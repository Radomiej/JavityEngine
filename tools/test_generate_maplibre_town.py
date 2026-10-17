#!/usr/bin/env python3
"""CPU-only acceptance tests for the authored MapLibre town fixture."""

import binascii
import gzip
import hashlib
import json
import pathlib
import sqlite3
import shutil
import struct
import subprocess
import tempfile
import unittest
import zlib
import math


ROOT = pathlib.Path(__file__).resolve().parents[1]
GENERATOR = ROOT / "tools" / "generate-maplibre-town.py"


def fields(data):
    """Decode the small protobuf wire subset used by MVT (0 and 2)."""
    pos = 0

    def varint():
        nonlocal pos
        value = 0
        shift = 0
        while True:
            byte = data[pos]
            pos += 1
            value |= (byte & 0x7F) << shift
            if byte < 0x80:
                return value
            shift += 7
            if shift >= 70:
                raise AssertionError("overlong protobuf varint")

    while pos < len(data):
        tag = varint()
        number, wire = tag >> 3, tag & 7
        if wire == 0:
            yield number, wire, varint()
        elif wire == 2:
            length = varint()
            value = data[pos : pos + length]
            pos += length
            yield number, wire, value
        elif wire == 1:
            value = data[pos : pos + 8]
            pos += 8
            yield number, wire, value
        elif wire == 5:
            value = data[pos : pos + 4]
            pos += 4
            yield number, wire, value
        else:
            raise AssertionError(f"unexpected MVT wire type {wire}")


def decode_varints(data):
    values = []
    pos = 0
    while pos < len(data):
        value = 0
        shift = 0
        while True:
            byte = data[pos]
            pos += 1
            value |= (byte & 127) << shift
            if byte < 128:
                break
            shift += 7
        values.append(value)
    return values


def layer_features(tile_bytes):
    result = {}
    for number, wire, layer_data in fields(tile_bytes):
        if number != 3 or wire != 2:
            continue
        layer = list(fields(layer_data))
        name = next(v.decode("utf-8") for n, w, v in layer if n == 1 and w == 2)
        extent = next(v for n, w, v in layer if n == 5 and w == 0)
        version = next(v for n, w, v in layer if n == 15 and w == 0)
        keys = [v.decode("utf-8") for n, w, v in layer if n == 3 and w == 2]
        values = [value_from_fields(v) for n, w, v in layer if n == 4 and w == 2]
        features = []
        for n, w, feature_data in layer:
            if n == 2 and w == 2:
                feature_fields = list(fields(feature_data))
                props = {}
                feature_id = next((v for n, w, v in feature_fields if n == 1 and w == 0), None)
                geom_type = next(v for n, w, v in feature_fields if n == 3 and w == 0)
                tags = next((v for n, w, v in feature_fields if n == 2 and w == 2), b"")
                tag_values = decode_varints(tags)
                for i in range(0, len(tag_values), 2):
                    props[keys[tag_values[i]]] = values[tag_values[i + 1]]
                geometry = next(v for n, w, v in feature_fields if n == 4 and w == 2)
                features.append({
                    "id": feature_id,
                    "type": geom_type,
                    "properties": props,
                    "geometry": decode_varints(geometry),
                })
        result[name] = {"extent": extent, "version": version, "features": features}
    return result


def value_from_fields(value_data):
    for number, wire, value in fields(value_data):
        if number == 1 and wire == 2:
            return value.decode("utf-8")
        if number == 2 and wire == 5:
            return struct.unpack("<f", value)[0]
        if number == 3 and wire == 1:
            return struct.unpack("<d", value)[0]
        if number == 4 and wire == 0:
            return value
        if number == 5 and wire == 0:
            return value
        if number == 6 and wire == 0:
            return bool(value)
    raise AssertionError("MVT value has no recognized scalar")


def signed_delta(value):
    return (value >> 1) ^ -(value & 1)


def decode_polygon_rings(commands):
    x = y = 0
    pos = 0
    rings = []
    while pos < len(commands):
        command = commands[pos]
        pos += 1
        command_id, count = command & 7, command >> 3
        if command_id == 1:
            for _ in range(count):
                x += signed_delta(commands[pos])
                y += signed_delta(commands[pos + 1])
                pos += 2
                rings.append([(x, y)])
        elif command_id == 2:
            for _ in range(count):
                x += signed_delta(commands[pos])
                y += signed_delta(commands[pos + 1])
                pos += 2
                rings[-1].append((x, y))
        elif command_id == 7:
            rings[-1].append(rings[-1][0])
        else:
            raise AssertionError(f"invalid polygon geometry command {command_id}")
    return rings


def decode_point(commands):
    if len(commands) != 3 or commands[0] != 9:
        raise AssertionError("expected one MVT MoveTo point")
    return signed_delta(commands[1]), signed_delta(commands[2])


def decode_geometry_paths(commands, geometry_type):
    x = y = pos = 0
    paths = []
    while pos < len(commands):
        command = commands[pos]
        pos += 1
        command_id, count = command & 7, command >> 3
        if count == 0:
            raise AssertionError("MVT geometry command has zero count")
        if command_id in (1, 2):
            if command_id == 1:
                if geometry_type != 1:
                    paths.append([])
                for _ in range(count):
                    x += signed_delta(commands[pos])
                    y += signed_delta(commands[pos + 1])
                    pos += 2
                    if geometry_type == 1:
                        paths.append([(x, y)])
                    else:
                        paths[-1].append((x, y))
            else:
                if geometry_type == 1 or not paths:
                    raise AssertionError("invalid MVT LineTo command")
                for _ in range(count):
                    x += signed_delta(commands[pos])
                    y += signed_delta(commands[pos + 1])
                    pos += 2
                    paths[-1].append((x, y))
        elif command_id == 7:
            if geometry_type != 3 or not paths:
                raise AssertionError("invalid MVT ClosePath command")
            paths[-1].append(paths[-1][0])
        else:
            raise AssertionError(f"unexpected MVT geometry command {command_id}")
    return paths


def package_files(root):
    return {path.relative_to(root).as_posix() for path in root.rglob("*") if path.is_file()}


def mbtiles_snapshot(path):
    """Read logical MBTiles content, independent of SQLite file-layout details."""
    with sqlite3.connect(path) as db:
        integrity = db.execute("PRAGMA integrity_check").fetchone()[0]
        if integrity != "ok":
            raise AssertionError(f"{path} failed SQLite integrity_check: {integrity}")
        schema = db.execute(
            "SELECT type, name, tbl_name, sql FROM sqlite_master "
            "WHERE name NOT LIKE 'sqlite_%' ORDER BY type, name"
        ).fetchall()
        metadata = db.execute(
            "SELECT name, value FROM metadata ORDER BY name, value"
        ).fetchall()
        tiles = db.execute(
            "SELECT zoom_level, tile_column, tile_row, tile_data FROM tiles "
            "ORDER BY zoom_level, tile_column, tile_row"
        ).fetchall()
    return schema, metadata, tiles


def validated_package_manifest(test_case, package):
    manifest = json.loads((package / "fixture-source.json").read_text(encoding="utf-8"))
    hashes = manifest["sha256"]
    expected_files = package_files(package) - {"fixture-source.json"}
    test_case.assertEqual(set(hashes), expected_files, "manifest covers every package file")
    for relative, digest in hashes.items():
        actual = hashlib.sha256((package / relative).read_bytes()).hexdigest()
        test_case.assertEqual(actual, digest, relative)
    return manifest


def assert_packages_match(test_case, generated, checked_in):
    test_case.assertEqual(package_files(generated), package_files(checked_in))
    generated_manifest = validated_package_manifest(test_case, generated)
    checked_in_manifest = validated_package_manifest(test_case, checked_in)
    for relative in package_files(generated) - {"town.mbtiles", "fixture-source.json"}:
        test_case.assertEqual(
            (generated / relative).read_bytes(),
            (checked_in / relative).read_bytes(),
            relative,
        )
    test_case.assertEqual(
        mbtiles_snapshot(generated / "town.mbtiles"),
        mbtiles_snapshot(checked_in / "town.mbtiles"),
        "town.mbtiles",
    )
    generated_manifest["sha256"].pop("town.mbtiles")
    checked_in_manifest["sha256"].pop("town.mbtiles")
    test_case.assertEqual(generated_manifest, checked_in_manifest, "fixture-source.json")


def verify_rgba_png(path, expected_size):
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise AssertionError(f"{path.name} is not PNG")
    pos = 8
    idat = bytearray()
    width = height = None
    while pos < len(data):
        length = int.from_bytes(data[pos : pos + 4], "big")
        kind = data[pos + 4 : pos + 8]
        payload = data[pos + 8 : pos + 8 + length]
        crc = int.from_bytes(data[pos + 8 + length : pos + 12 + length], "big")
        if (binascii.crc32(kind + payload) & 0xFFFFFFFF) != crc:
            raise AssertionError(f"{path.name} has invalid PNG chunk CRC")
        if kind == b"IHDR":
            width, height = struct.unpack(">II", payload[:8])
            if payload[8:] != bytes((8, 6, 0, 0, 0)):
                raise AssertionError("sprite atlas must be 8-bit RGBA PNG")
        elif kind == b"IDAT":
            idat.extend(payload)
        elif kind == b"IEND":
            break
        pos += 12 + length
    if (width, height) != (expected_size, expected_size):
        raise AssertionError(f"unexpected PNG size {(width, height)}")
    pixels = zlib.decompress(bytes(idat))
    stride = width * 4 + 1
    if len(pixels) != stride * height or any(pixels[row * stride] != 0 for row in range(height)):
        raise AssertionError("expected deterministic filter-0 RGBA scanlines")
    if not any(pixels[row * stride + 1 + pixel * 4 + 3] for row in range(height) for pixel in range(width)):
        raise AssertionError("sprite atlas is fully transparent")


class MapLibreTownFixtureTest(unittest.TestCase):
    def generate(self, out_dir):
        subprocess.run(
            ["python3", str(GENERATOR), "--output", str(out_dir)],
            check=True,
            cwd=ROOT,
            capture_output=True,
            text=True,
        )

    def test_generator_is_byte_deterministic(self):
        with tempfile.TemporaryDirectory() as first, tempfile.TemporaryDirectory() as second:
            self.generate(pathlib.Path(first))
            self.generate(pathlib.Path(second))
            first_package = pathlib.Path(first) / "maplibre-town"
            second_package = pathlib.Path(second) / "maplibre-town"
            for relative in package_files(first_package):
                self.assertEqual((first_package / relative).read_bytes(), (second_package / relative).read_bytes(), relative)

    def test_checked_in_package_matches_generator_output(self):
        with tempfile.TemporaryDirectory() as temp:
            self.generate(pathlib.Path(temp))
            generated = pathlib.Path(temp) / "maplibre-town"
            checked_in = ROOT / "javity-desktop/src/main/resources/org/javity/demo/maplibre-town"
            self.assertEqual((generated / "LICENSE.txt").read_bytes(), (ROOT / "LICENSE").read_bytes())
            assert_packages_match(self, generated, checked_in)

    def test_package_comparison_ignores_sqlite_version_header_only(self):
        with tempfile.TemporaryDirectory() as temp:
            self.generate(pathlib.Path(temp))
            generated = pathlib.Path(temp) / "maplibre-town"
            alternate = pathlib.Path(temp) / "alternate-sqlite-build"
            shutil.copytree(generated, alternate)
            database = alternate / "town.mbtiles"
            with database.open("r+b") as stream:
                stream.seek(96)
                stream.write(b"\x00\x00\x00\x00")
            manifest_path = alternate / "fixture-source.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["sha256"]["town.mbtiles"] = hashlib.sha256(database.read_bytes()).hexdigest()
            manifest_path.write_text(json.dumps(manifest, ensure_ascii=False) + "\n", encoding="utf-8")

            assert_packages_match(self, generated, alternate)

            with sqlite3.connect(database) as db:
                stored = db.execute("SELECT tile_data FROM tiles").fetchone()[0]
                changed = stored[:-1] + bytes((stored[-1] ^ 1,))
                db.execute("UPDATE tiles SET tile_data = ?", (changed,))
            manifest["sha256"]["town.mbtiles"] = hashlib.sha256(database.read_bytes()).hexdigest()
            manifest_path.write_text(json.dumps(manifest, ensure_ascii=False) + "\n", encoding="utf-8")
            with self.assertRaises(AssertionError):
                assert_packages_match(self, generated, alternate)

            shutil.copyfile(generated / "town.mbtiles", database)
            with sqlite3.connect(database) as db:
                db.execute("UPDATE metadata SET value = 'wrong' WHERE name = 'format'")
            manifest["sha256"]["town.mbtiles"] = hashlib.sha256(database.read_bytes()).hexdigest()
            manifest_path.write_text(json.dumps(manifest, ensure_ascii=False) + "\n", encoding="utf-8")
            with self.assertRaises(AssertionError):
                assert_packages_match(self, generated, alternate)

    def test_mbtiles_has_tms_rows_gzip_mvt_and_courtyard(self):
        with tempfile.TemporaryDirectory() as temp:
            self.generate(pathlib.Path(temp))
            package = pathlib.Path(temp) / "maplibre-town"
            db = sqlite3.connect(package / "town.mbtiles")
            self.assertEqual(db.execute("PRAGMA integrity_check").fetchone()[0], "ok")
            self.assertEqual(db.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name").fetchall(), [("metadata",), ("tiles",)])
            metadata = dict(db.execute("SELECT name, value FROM metadata"))
            self.assertEqual(metadata["format"], "pbf")
            self.assertEqual(metadata["type"], "overlay")
            self.assertEqual((int(metadata["minzoom"]), int(metadata["maxzoom"])), (14, 14))
            vector_layers = json.loads(metadata["json"])["vector_layers"]
            self.assertEqual({entry["id"] for entry in vector_layers}, {"water", "landuse", "buildings", "roads", "poi", "label-positions"})
            center_lon, center_lat, metadata_center_zoom = (float(value) for value in metadata["center"].split(","))
            self.assertEqual(metadata_center_zoom, 14)
            tiles = db.execute("SELECT zoom_level, tile_column, tile_row, tile_data FROM tiles ORDER BY zoom_level, tile_column, tile_row").fetchall()
            self.assertEqual((int(metadata["minzoom"]), int(metadata["maxzoom"])), (14, 14))
            self.assertEqual({row[0] for row in tiles}, {14})
            self.assertEqual(len(tiles), 1, "the same authored geometry must not be duplicated at other zooms")
            town_hall = False
            courtyard_found = False
            polish_labels = set()
            for zoom, x, tms_y, stored in tiles:
                n = 1 << zoom
                expected_x = int((center_lon + 180.0) / 360.0 * n)
                sin_lat = math.sin(math.radians(center_lat))
                xyz_y = int((0.5 - math.log((1 + sin_lat) / (1 - sin_lat)) / (4 * math.pi)) * n)
                self.assertEqual((x, tms_y), (expected_x, n - 1 - xyz_y), "stored tile must use TMS row for the manifest center")
                self.assertTrue(stored.startswith(b"\x1f\x8b"), "PBF tile is gzip wrapped")
                decoded = layer_features(gzip.decompress(stored))
                self.assertEqual(set(decoded), {"water", "landuse", "buildings", "roads", "poi", "label-positions"})
                self.assertTrue(all(layer["version"] == 2 and layer["extent"] == 4096 for layer in decoded.values()))
                for layer in decoded.values():
                    for item in layer["features"]:
                        paths = decode_geometry_paths(item["geometry"], item["type"])
                        self.assertTrue(paths)
                        self.assertTrue(all(path for path in paths))
                        self.assertTrue(all(0 <= x <= 4096 and 0 <= y <= 4096 for path in paths for x, y in path))
                town_hall |= any(f["properties"].get("kind") == "town-hall" for f in decoded["poi"]["features"])
                polish_labels.update(f["properties"].get("label") for f in decoded["label-positions"]["features"])
                for feature in decoded["buildings"]["features"]:
                    rings = decode_polygon_rings(feature["geometry"])
                    self.assertGreaterEqual(len(rings), 1)
                    self.assertTrue(all(len(ring) >= 4 and ring[0] == ring[-1] for ring in rings))
                    self.assertTrue(all(0 <= x <= 4096 and 0 <= y <= 4096 for ring in rings for x, y in ring))
                    outer_area = sum(rings[0][i][0] * rings[0][i + 1][1] - rings[0][i + 1][0] * rings[0][i][1] for i in range(len(rings[0]) - 1))
                    self.assertGreater(outer_area, 0, "MVT exterior rings are clockwise in y-down tile coordinates")
                    courtyard_found |= len(rings) == 2
                    if len(rings) == 2:
                        inner_area = sum(rings[1][i][0] * rings[1][i + 1][1] - rings[1][i + 1][0] * rings[1][i][1] for i in range(len(rings[1]) - 1))
                        self.assertLess(inner_area, 0, "MVT interior rings are counter-clockwise in y-down tile coordinates")
            self.assertTrue(town_hall)
            self.assertTrue(courtyard_found)
            self.assertTrue({"Łódź", "Żółty Most", "Północny Rynek"}.issubset(polish_labels))
            bounds = [float(value) for value in metadata["bounds"].split(",")]
            self.assertLessEqual(bounds[0], center_lon)
            self.assertGreaterEqual(bounds[2], center_lon)
            self.assertLessEqual(bounds[1], center_lat)
            self.assertGreaterEqual(bounds[3], center_lat)
            zoom, tile_x, tms_y, stored = tiles[0]
            n = 1 << zoom
            world_x = (center_lon + 180.0) / 360.0
            sin_lat = math.sin(math.radians(center_lat))
            world_y = 0.5 - math.log((1 + sin_lat) / (1 - sin_lat)) / (4 * math.pi)
            local_x = (world_x * n - tile_x) * 4096
            xyz_y = n - 1 - tms_y
            local_y = (world_y * n - xyz_y) * 4096
            decoded = layer_features(gzip.decompress(stored))
            town_hall_point = next(f for f in decoded["poi"]["features"] if f["id"] == 501)
            self.assertEqual(decode_point(town_hall_point["geometry"]), (round(local_x + 80), round(local_y + 20)))
            db.close()

    def test_day_and_night_styles_share_local_source_and_exercise_style_spec(self):
        with tempfile.TemporaryDirectory() as temp:
            self.generate(pathlib.Path(temp))
            package = pathlib.Path(temp) / "maplibre-town"
            manifest = json.loads((package / "map.json").read_text())
            self.assertEqual(manifest["version"], 1)
            self.assertEqual(manifest["defaultStyle"], "day")
            self.assertEqual(manifest["initialCamera"]["zoom"], 16)
            self.assertEqual(manifest["fixture"]["kind"], "web-mercator-basezoom")
            self.assertEqual(manifest["sources"], {"town": "town.mbtiles"})
            self.assertEqual(manifest["styles"], {"day": "style-day.json", "night": "style-night.json"})
            styles = [json.loads((package / name).read_text()) for name in ("style-day.json", "style-night.json")]
            for style in styles:
                self.assertEqual(style["version"], 8)
                self.assertEqual(style["sources"]["town"]["type"], "vector")
                self.assertEqual(style["sources"]["town"]["url"], "mbtiles://@town")
                self.assertEqual(style["glyphs"], "glyphs/{fontstack}/{range}.pbf")
                self.assertFalse(any(key in json.dumps(style) for key in ("http://", "https://")))
                self.assertTrue(any(layer.get("source-layer") == "water" for layer in style["layers"]))
                self.assertTrue(any(layer.get("source-layer") == "buildings" and "filter" in layer for layer in style["layers"]))
                self.assertTrue(any(layer.get("source-layer") == "roads" and layer.get("paint", {}).get("line-dasharray") for layer in style["layers"]))
                self.assertTrue(any("interpolate" in json.dumps(layer.get("paint", {})) for layer in style["layers"]))
                road = next(layer for layer in style["layers"] if layer["id"] == "road-primary")
                self.assertEqual(road["paint"]["line-width"], ["interpolate", ["linear"], ["zoom"], 14, 1, 20, 4])
                for layer in style["layers"]:
                    if layer["type"] == "line":
                        self.assertNotIn("line-cap", layer.get("paint", {}))
                        self.assertNotIn("line-join", layer.get("paint", {}))
                        self.assertIn("line-cap", layer.get("layout", {}))
                        self.assertIn("line-join", layer.get("layout", {}))
                self.assertEqual(style["sprite"], "sprites/town")
                icons = json.loads((package / "sprites/town.json").read_text())
                self.assertEqual(icons["town-hall"], {"x": 0, "y": 0, "width": 16, "height": 16, "pixelRatio": 1, "sdf": False})
                icons2 = json.loads((package / "sprites/town@2x.json").read_text())
                self.assertEqual(icons2["town-hall"], {"x": 0, "y": 0, "width": 32, "height": 32, "pixelRatio": 2, "sdf": False})
                verify_rgba_png(package / "sprites/town.png", 16)
                verify_rgba_png(package / "sprites/town@2x.png", 32)
                self.assertTrue(any(layer["id"] == "poi-icon" and layer["type"] == "symbol" and layer["layout"]["icon-image"] == "town-hall" for layer in style["layers"]))
                labels = next(layer for layer in style["layers"] if layer["id"] == "labels")
                self.assertEqual(labels["layout"]["text-field"], ["get", "label"])
                self.assertEqual(labels["layout"]["text-font"], ["Open Sans Regular"])
                for range_name in ("0-255.pbf", "256-511.pbf"):
                    glyph_path = package / "glyphs/Open Sans Regular" / range_name
                    self.assertTrue(glyph_path.is_file(), f"offline glyph missing: {range_name}")
                    self.assertGreater(glyph_path.stat().st_size, 0)
                licenses = package / "licenses"
                self.assertTrue((licenses / "OpenSans-LICENSE.txt").is_file())
                self.assertTrue((licenses / "OpenSans-NOTICE.txt").is_file())
            self.assertNotEqual(styles[0]["layers"][0]["paint"]["background-color"], styles[1]["layers"][0]["paint"]["background-color"])


if __name__ == "__main__":
    unittest.main()
