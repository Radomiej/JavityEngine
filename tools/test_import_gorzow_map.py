"""CPU-only tests for the bounded OpenFreeMap Gorzów package importer."""

import gzip
import hashlib
import importlib.util
import json
import pathlib
import sqlite3
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("import_gorzow_map", ROOT / "tools/import-gorzow-map.py")
IMPORTER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(IMPORTER)


def varint(value):
    out = bytearray()
    while value > 0x7f:
        out.append((value & 0x7f) | 0x80)
        value >>= 7
    out.append(value)
    return bytes(out)


def field(number, wire, value):
    tag = varint(number << 3 | wire)
    if wire == 2:
        return tag + varint(len(value)) + value
    if wire == 0:
        return tag + varint(value)
    return tag + value


def named_place_tile(name="Gorzów Wielkopolski"):
    feature = field(1, 0, 7) + field(2, 2, varint(0) + varint(0)) + field(3, 0, 1) + field(4, 2, varint(9) + varint(20) + varint(30))
    value = field(1, 2, name.encode("utf-8"))
    layer = (field(1, 2, b"place") + field(2, 2, feature) + field(3, 2, b"name:pl") + field(4, 2, value)
             + field(5, 0, 4096) + field(15, 0, 2))
    return field(3, 2, layer)


def eval_style_expression(expression, feature):
    """Evaluate the small MapLibre expression subset used by transport/label styles."""
    if not isinstance(expression, list):
        return expression
    if not expression:
        return expression
    op, *args = expression
    if op == "literal":
        return args[0]
    if op == "get":
        return feature.get(eval_style_expression(args[0], feature))
    if op == "has":
        return eval_style_expression(args[0], feature) in feature
    if op == "to-number":
        value = eval_style_expression(args[0], feature)
        try:
            if value is None:
                raise ValueError
            return float(value)
        except (TypeError, ValueError):
            return eval_style_expression(args[1], feature) if len(args) > 1 else 0
    if op == "all":
        return all(eval_style_expression(arg, feature) for arg in args)
    if op == "any":
        return any(eval_style_expression(arg, feature) for arg in args)
    if op == "!":
        return not eval_style_expression(args[0], feature)
    if op == "==":
        return eval_style_expression(args[0], feature) == eval_style_expression(args[1], feature)
    if op == "<=":
        return eval_style_expression(args[0], feature) <= eval_style_expression(args[1], feature)
    if op == ">=":
        return eval_style_expression(args[0], feature) >= eval_style_expression(args[1], feature)
    if op == "in":
        value = eval_style_expression(args[0], feature)
        choices = eval_style_expression(args[1], feature)
        return value in choices
    if op == "match":
        value = eval_style_expression(args[0], feature)
        for index in range(1, len(args) - 1, 2):
            labels = args[index]
            if value in (labels if isinstance(labels, list) else [labels]):
                return eval_style_expression(args[index + 1], feature)
        return eval_style_expression(args[-1], feature)
    if op == "coalesce":
        for arg in args:
            value = eval_style_expression(arg, feature)
            if value is not None:
                return value
        return None
    raise AssertionError(f"unsupported expression in test evaluator: {op}")


class ImportGorzowMapTests(unittest.TestCase):
    @staticmethod
    def style_layers(mode="day"):
        return IMPORTER.map_style(mode)["layers"]

    @staticmethod
    def layer_by_id(layers, layer_id):
        return next(layer for layer in layers if layer["id"] == layer_id)

    def test_bounded_footprint_and_xyz_tms(self):
        coords = IMPORTER.tile_footprint()
        self.assertEqual(38, len(coords))
        self.assertLessEqual(len(coords), IMPORTER.MAX_TILES)
        self.assertEqual(25, sum(z == 14 for z, _, _ in coords))
        self.assertEqual(9, sum(z == 13 for z, _, _ in coords))
        self.assertEqual(4, sum(z == 12 for z, _, _ in coords))
        x, y = IMPORTER.center_tile(14)
        self.assertEqual((8885, 5357), (x, y))
        self.assertEqual(11026, (1 << 14) - 1 - 5357)

    def test_download_policy_and_cache(self):
        with tempfile.TemporaryDirectory() as temp:
            coord = (14, 8885, 5357)
            payload = named_place_tile()
            seen = []

            class Response:
                def __enter__(self): return self
                def __exit__(self, *args): pass
                def read(self, limit): return payload

            def opener(request, timeout):
                seen.append((request.full_url, request.get_header("User-agent"), timeout))
                return Response()

            first = IMPORTER.get_tile(coord, temp, opener=opener)
            second = IMPORTER.get_tile(coord, temp, opener=lambda *args: self.fail("cache should be used"))
            self.assertEqual(payload, first)
            self.assertEqual(payload, second)
            self.assertEqual(1, len(seen))
            self.assertEqual(IMPORTER.USER_AGENT, seen[0][1])
            self.assertEqual(IMPORTER.TIMEOUT_SECONDS, seen[0][2])
            self.assertIn("20261011_080001_pt/14/8885/5357.pbf", seen[0][0])
            with self.assertRaises(ValueError):
                IMPORTER.get_tile((14, 1, 1), temp, opener=lambda *args, **kwargs: type("R", (), {"__enter__": lambda s:s,"__exit__":lambda *a:None,"read":lambda s,n:b"x"*(IMPORTER.MAX_TILE_BYTES+1)})())
            with self.assertRaises(ValueError):
                IMPORTER.fetch_tiles([(14, i, 1) for i in range(IMPORTER.MAX_TILES + 1)], temp, download=False)

    def test_real_mvt_names_and_unsupported_wire_type_handling(self):
        stats = IMPORTER.tile_statistics(named_place_tile())
        self.assertEqual(1, stats["place"]["featureCount"])
        self.assertIn("Gorzów Wielkopolski", stats["place"]["names"])

    def test_mbtiles_gzip_tms_metadata_and_package_are_reproducible(self):
        tile = named_place_tile()
        vector_layers = [{"id": "place", "fields": {"name": "String"}, "minzoom": 0, "maxzoom": 14}]
        tiles = {(14, 8885, 5357): tile}
        with tempfile.TemporaryDirectory() as temp:
            vector_layers = [{"id": layer, "fields": {"test-field": "String"}, "minzoom": 0, "maxzoom": 14} for layer in IMPORTER.SOURCE_LAYERS]
            schema = pathlib.Path(temp) / "tilejson.json"
            schema.write_text(json.dumps({"tiles": [IMPORTER.TILE_TEMPLATE], "vector_layers": vector_layers}))
            first = IMPORTER.build_package(pathlib.Path(temp) / "a", tiles, tilejson_path=schema)
            first_bytes = {p.relative_to(first).as_posix(): p.read_bytes() for p in first.rglob("*") if p.is_file()}
            second = IMPORTER.build_package(pathlib.Path(temp) / "b", tiles, tilejson_path=schema)
            second_bytes = {p.relative_to(second).as_posix(): p.read_bytes() for p in second.rglob("*") if p.is_file()}
            self.assertEqual(first_bytes, second_bytes)
            with sqlite3.connect(first / "gorzow.mbtiles") as db:
                metadata = dict(db.execute("SELECT name,value FROM metadata"))
                self.assertEqual("pbf", metadata["format"])
                self.assertEqual(14, int(metadata["maxzoom"]))
                self.assertEqual(json.loads(metadata["json"])["vector_layers"], vector_layers)
                row = db.execute("SELECT zoom_level,tile_column,tile_row,tile_data FROM tiles").fetchone()
            self.assertEqual((14, 8885, 11026), row[:3])
            self.assertEqual(tile, gzip.decompress(row[3]))
            manifest = json.loads((first / "map.json").read_text())
            self.assertEqual("mbtiles://@gorzow", json.loads((first / "style-day.json").read_text())["sources"]["gorzow"]["url"])
            self.assertEqual((IMPORTER.CENTER_LON, IMPORTER.CENTER_LAT, 15), tuple(manifest["initialCamera"][key] for key in ("longitude", "latitude", "zoom")))
            self.assertEqual(IMPORTER.ATTRIBUTION, manifest["attribution"])
            self.assertEqual("Open Sans Regular", " ".join(json.loads((first / "style-day.json").read_text())["layers"][-1]["layout"]["text-font"]))
            for mode in ("day", "night"):
                doc = json.loads((first / f"style-{mode}.json").read_text())
                self.assertEqual(8, doc["version"])
                self.assertTrue(all("source-layer" not in layer or layer["source-layer"] in IMPORTER.SOURCE_LAYERS for layer in doc["layers"]))
            provenance = json.loads((first / "fixture-source.json").read_text())
            actual_hashes = {p.relative_to(first).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in first.rglob("*") if p.is_file() and p.name != "fixture-source.json"}
            self.assertEqual(actual_hashes, provenance["sha256"])
            self.assertEqual(hashlib.sha256(tile).hexdigest(), provenance["sourceTileSha256"]["14/8885/5357"])
            self.assertTrue((first / "licenses/ODbL-1.0.txt").is_file())
            self.assertTrue((first / "glyphs/Open Sans Regular/8192-8447.pbf").is_file())

    def test_transport_buckets_are_disjoint_for_brunnel_and_extreme_layers(self):
        layers = self.style_layers()
        transport = [layer for layer in layers if layer.get("source-layer") == "transportation"]
        road_classes = ["rail", "path", "track", "pedestrian", "service", "minor",
                        "tertiary", "secondary", "primary", "trunk", "motorway"]
        values = [-100, -6, -5, -4, -1, 0, 1, 4, 5, 6, 100, "4", None]
        for structure, brunnel in (("ground", None), ("ground", "ford"),
                                   ("tunnel", "tunnel"), ("bridge", "bridge")):
            for layer_value in values:
                for road_class in road_classes:
                    feature = {"brunnel": brunnel, "layer": layer_value, "class": road_class}
                    hits = {layer["id"].rsplit("-", 1)[0] for layer in transport
                            if eval_style_expression(layer["filter"], feature)}
                    self.assertEqual(1, len(hits), (feature, hits))

        for brunnel in ("bridge", "tunnel"):
            feature = {"brunnel": brunnel, "layer": 0, "class": "minor"}
            ground = {layer["id"].rsplit("-", 1)[0] for layer in transport
                      if layer["id"].startswith("ground-")
                      and eval_style_expression(layer["filter"], feature)}
            self.assertEqual(set(), ground, (feature, ground))

    def test_transport_draw_order_keeps_tunnel_below_ground_and_bridge(self):
        layers = self.style_layers()
        ids = [layer["id"] for layer in layers]
        positions = {layer_id: ids.index(layer_id) for layer_id in
                     ("tunnel-0-minor-fill", "building", "ground-0-minor-fill",
                      "bridge-0-minor-fill")}
        self.assertLess(positions["tunnel-0-minor-fill"], positions["building"])
        self.assertLess(positions["building"], positions["ground-0-minor-fill"])
        self.assertLess(positions["ground-0-minor-fill"], positions["bridge-0-minor-fill"])

    def test_important_road_names_sort_before_minor_streets(self):
        roads = self.layer_by_id(self.style_layers(), "road-names")["layout"]
        rank = roads["symbol-sort-key"]
        self.assertEqual(0, eval_style_expression(rank, {"class": "motorway"}))
        self.assertEqual(1, eval_style_expression(rank, {"class": "trunk"}))
        self.assertEqual(2, eval_style_expression(rank, {"class": "primary"}))
        self.assertEqual(3, eval_style_expression(rank, {"class": "secondary"}))
        self.assertEqual(4, eval_style_expression(rank, {"class": "tertiary"}))
        self.assertEqual(5, eval_style_expression(rank, {"class": "minor"}))

    def test_label_collision_policy_and_polish_name_fallback(self):
        layers = self.style_layers()
        labels = {layer["id"]: layer for layer in layers
                  if layer.get("type") == "symbol"}
        expected = ["poi-labels", "water-names", "road-names", "place-labels"]
        self.assertEqual(expected, [layer["id"] for layer in layers
                                   if layer.get("type") == "symbol"])
        for layer in labels.values():
            layout = layer["layout"]
            self.assertFalse(layout["text-allow-overlap"], layer["id"])
            self.assertFalse(layout["text-ignore-placement"], layer["id"])
            self.assertEqual(5, layout["text-padding"], layer["id"])
            self.assertEqual("Open Sans Regular", layout["text-font"][0])
            self.assertEqual("ul. Żeromskiego", eval_style_expression(
                layout["text-field"], {"name:pl": "ul. Żeromskiego", "name": "Zeromski Street"}))
            self.assertEqual("Rondo Santockie", eval_style_expression(
                layout["text-field"], {"name": "Rondo Santockie"}))

    def test_polish_only_names_are_eligible_for_symbol_placement(self):
        layers = self.style_layers()
        class_by_id = {"place-labels": "town", "poi-labels": "museum"}
        excluded = []
        for layer in (item for item in layers if item.get("type") == "symbol"):
            feature = {"name:pl": "Ulica Żwirowa"}
            if layer["id"] in class_by_id:
                feature["class"] = class_by_id[layer["id"]]
            if not eval_style_expression(layer["filter"], feature):
                excluded.append(layer["id"])
        self.assertEqual([], excluded)


if __name__ == "__main__":
    unittest.main()
