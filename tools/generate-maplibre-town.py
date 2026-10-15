#!/usr/bin/env python3
"""Generate the deterministic, original MapLibre MBTiles demo package.

The fixture is one authored Web Mercator tile at z14, centered at a fictional
Polish town. MapLibre can overscale that tile above its maxzoom without changing
the town's geographic placement. Runtime dependencies: Python standard library
only.
"""

import argparse
import binascii
import gzip
import hashlib
import json
import math
import pathlib
import shutil
import sqlite3
import zlib


EXTENT = 4096
BASE_ZOOM = 14
MIN_ZOOM = BASE_ZOOM
MAX_ZOOM = BASE_ZOOM
CENTER_LON = 21.0
CENTER_LAT = 52.0
PACKAGE_NAME = "maplibre-town"


def varint(value):
    if value < 0:
        raise ValueError("protobuf varints here must be unsigned")
    result = bytearray()
    while value > 0x7F:
        result.append((value & 0x7F) | 0x80)
        value >>= 7
    result.append(value)
    return bytes(result)


def field_varint(number, value):
    return varint((number << 3) | 0) + varint(value)


def field_bytes(number, data):
    return varint((number << 3) | 2) + varint(len(data)) + data


def zigzag(value):
    return (value << 1) ^ (value >> 31)


def packed(values):
    return b"".join(varint(value) for value in values)


def encode_geometry(geometry_type, paths):
    """Encode MVT command streams; points are one-vertex paths."""
    cursor_x = cursor_y = 0
    commands = []
    for path in paths:
        if not path:
            continue
        if geometry_type == 1:
            commands.append((len(path) << 3) | 1)
        else:
            commands.append(9)
        first = path[0]
        commands.extend((zigzag(first[0] - cursor_x), zigzag(first[1] - cursor_y)))
        cursor_x, cursor_y = first
        if geometry_type != 1:
            if len(path) > 1:
                commands.append(((len(path) - 1) << 3) | 2)
                for point in path[1:]:
                    commands.extend((zigzag(point[0] - cursor_x), zigzag(point[1] - cursor_y)))
                    cursor_x, cursor_y = point
            if geometry_type == 3:
                commands.append(15)
    return packed(commands)


def polygon_area(ring):
    return sum(
        ring[index][0] * ring[(index + 1) % len(ring)][1]
        - ring[(index + 1) % len(ring)][0] * ring[index][1]
        for index in range(len(ring))
    )


def oriented_ring(points, exterior):
    ring = list(points)
    if polygon_area(ring) == 0:
        raise ValueError("fixture polygon ring has zero area")
    # Positive signed area is clockwise in screen/tile coordinates (y down).
    wants_positive = exterior
    if (polygon_area(ring) > 0) != wants_positive:
        ring.reverse()
    return ring


def px(cx, cy, dx, dy):
    return round(cx + dx), round(cy + dy)


def rectangle(cx, cy, left, top, right, bottom, reverse=False):
    ring = [px(cx, cy, left, top), px(cx, cy, right, top), px(cx, cy, right, bottom), px(cx, cy, left, bottom)]
    return list(reversed(ring)) if reverse else ring


def authored_layers(cx, cy):
    """The same original tile-space sketch, shifted to the actual tile center."""
    return [
        ("water", [
            feature(101, 3, {"kind": "lake", "name": "Błękitny Staw"}, [
                [px(cx, cy, -690, -80), px(cx, cy, -620, -250), px(cx, cy, -440, -325),
                 px(cx, cy, -260, -260), px(cx, cy, -220, -115), px(cx, cy, -315, 5),
                 px(cx, cy, -520, 35)],
            ]),
        ]),
        ("landuse", [
            feature(201, 3, {"kind": "park", "name": "Park Lipowy", "access": "public"}, [
                rectangle(cx, cy, -165, -240, 195, 48),
            ]),
        ]),
        ("buildings", [
            feature(301, 3, {"kind": "town-hall", "name": "Ratusz", "height": 24.0}, [
                rectangle(cx, cy, 15, -45, 145, 85),
                oriented_ring(rectangle(cx, cy, 52, -8, 108, 48), exterior=False),
            ]),
            feature(302, 3, {"kind": "house", "name": "Dom przy Rynku", "height": 9.0}, [
                rectangle(cx, cy, 185, -28, 255, 38),
            ]),
            feature(303, 3, {"kind": "house", "name": "Kamienica", "height": 14.0}, [
                rectangle(cx, cy, -252, -60, -188, 18),
            ]),
        ]),
        ("roads", [
            feature(401, 2, {"class": "primary", "name": "Ulica Główna", "surface": "asphalt"}, [
                [px(cx, cy, -850, 130), px(cx, cy, -510, 118), px(cx, cy, -235, 102),
                 px(cx, cy, 10, 105), px(cx, cy, 340, 88), px(cx, cy, 770, 72)],
            ]),
            feature(402, 2, {"class": "residential", "name": "Rynek"}, [
                [px(cx, cy, -60, -420), px(cx, cy, -42, -180), px(cx, cy, -25, 0),
                 px(cx, cy, -5, 210), px(cx, cy, 30, 440)],
            ]),
            feature(403, 2, {"class": "footway", "name": "Ścieżka nad stawem", "surface": "gravel"}, [
                [px(cx, cy, -620, 0), px(cx, cy, -520, -28), px(cx, cy, -418, -8),
                 px(cx, cy, -318, 25), px(cx, cy, -250, 76)],
            ]),
        ]),
        ("poi", [
            feature(501, 1, {"kind": "town-hall", "name": "Ratusz"}, [[px(cx, cy, 80, 20)]]),
            feature(502, 1, {"kind": "fountain", "name": "Fontanna Żółtego Mostu"}, [[px(cx, cy, -10, 165)]]),
        ]),
        ("label-positions", [
            feature(601, 1, {"label": "Łódź", "kind": "town"}, [[px(cx, cy, -28, -65)]]),
            feature(602, 1, {"label": "Żółty Most", "kind": "bridge"}, [[px(cx, cy, -515, 2)]]),
            feature(603, 1, {"label": "Północny Rynek", "kind": "square"}, [[px(cx, cy, 110, 150)]]),
        ]),
    ]


def feature(feature_id, geometry_type, properties, paths):
    return {"id": feature_id, "type": geometry_type, "properties": properties, "paths": paths}


def encode_value(value):
    if isinstance(value, bool):
        return field_varint(7, int(value))
    if isinstance(value, int):
        return field_varint(5, value)
    if isinstance(value, float):
        import struct
        return varint((2 << 3) | 5) + struct.pack("<f", value)
    return field_bytes(1, str(value).encode("utf-8"))


def encode_layer(name, features):
    keys = sorted({key for item in features for key in item["properties"]})
    value_pairs = sorted(
        {(type(value).__name__, value) for item in features for value in item["properties"].values()},
        key=lambda pair: (pair[0], str(pair[1])),
    )
    key_indexes = {key: index for index, key in enumerate(keys)}
    value_indexes = {pair: index for index, pair in enumerate(value_pairs)}
    encoded = [field_bytes(1, name.encode("utf-8"))]
    for item in features:
        tags = []
        for key, value in sorted(item["properties"].items()):
            tags.extend((key_indexes[key], value_indexes[(type(value).__name__, value)]))
        geom = field_bytes(4, encode_geometry(item["type"], item["paths"]))
        feature_bytes = b"".join((
            field_varint(1, item["id"]),
            field_bytes(2, packed(tags)),
            field_varint(3, item["type"]),
            geom,
        ))
        encoded.append(field_bytes(2, feature_bytes))
    for key in keys:
        encoded.append(field_bytes(3, key.encode("utf-8")))
    for type_name, value in value_pairs:
        encoded.append(field_bytes(4, encode_value(value)))
    encoded.extend((field_varint(5, EXTENT), field_varint(15, 2)))
    return b"".join(encoded)


def world_xy(lon, lat):
    lat = max(-85.05112878, min(85.05112878, lat))
    x = (lon + 180.0) / 360.0
    sin_lat = math.sin(math.radians(lat))
    y = 0.5 - math.log((1 + sin_lat) / (1 - sin_lat)) / (4 * math.pi)
    return x, y


def tile_center(zoom):
    n = 1 << zoom
    world_x, world_y_value = world_xy(CENTER_LON, CENTER_LAT)
    tile_x = min(n - 1, max(0, int(world_x * n)))
    tile_y = min(n - 1, max(0, int(world_y_value * n)))
    center_x = (world_x * n - tile_x) * EXTENT
    center_y = (world_y_value * n - tile_y) * EXTENT
    return tile_x, tile_y, center_x, center_y


def tile_message(zoom):
    _, _, cx, cy = tile_center(zoom)
    layers = authored_layers(cx, cy)
    return b"".join(field_bytes(3, encode_layer(name, features)) for name, features in layers)


def lon_from_world_x(x):
    return x * 360.0 - 180.0


def lat_from_world_y(y):
    return math.degrees(math.atan(math.sinh(math.pi * (1.0 - 2.0 * y))))


def package_bounds():
    tile_x, tile_y, center_x, center_y = tile_center(BASE_ZOOM)
    layers = authored_layers(center_x, center_y)
    coordinates = [point for _, features in layers for item in features for path in item["paths"] for point in path]
    min_x = min(point[0] for point in coordinates)
    max_x = max(point[0] for point in coordinates)
    min_y = min(point[1] for point in coordinates)
    max_y = max(point[1] for point in coordinates)
    n = 1 << BASE_ZOOM
    west = lon_from_world_x((tile_x + min_x / EXTENT) / n)
    east = lon_from_world_x((tile_x + max_x / EXTENT) / n)
    north = lat_from_world_y((tile_y + min_y / EXTENT) / n)
    south = lat_from_world_y((tile_y + max_y / EXTENT) / n)
    return west, south, east, north


def vector_layers_json():
    schema = {
        "water": {"kind": "String", "name": "String"},
        "landuse": {"access": "String", "kind": "String", "name": "String"},
        "buildings": {"height": "Number", "kind": "String", "name": "String"},
        "roads": {"class": "String", "name": "String", "surface": "String"},
        "poi": {"kind": "String", "name": "String"},
        "label-positions": {"kind": "String", "label": "String"},
    }
    return {"vector_layers": [
        {"id": name, "fields": schema[name], "minzoom": MIN_ZOOM, "maxzoom": MAX_ZOOM}
        for name in schema
    ]}


def style_document(mode):
    night = mode == "night"
    palette = {
        "background": "#101a24" if night else "#e8eee6",
        "water": "#183d5a" if night else "#73b9cf",
        "park": "#214b3b" if night else "#8ab978",
        "building": "#647080" if night else "#d9c8a5",
        "primary": "#f0a34a" if night else "#f5c85d",
        "residential": "#9aa9ba" if night else "#fff4d7",
        "footway": "#55c6bc" if night else "#7a795e",
        "road-casing": "#15212e" if night else "#9d927d",
        "point": "#ffda72" if night else "#bf4c3f",
    }
    return {
        "version": 8,
        "name": "Authored Town — Night" if night else "Authored Town — Day",
        "sources": {"town": {"type": "vector", "url": "mbtiles://@town"}},
        "glyphs": "glyphs/{fontstack}/{range}.pbf",
        "sprite": "sprites/town",
        "layers": [
            {"id": "background", "type": "background", "paint": {"background-color": palette["background"]}},
            {"id": "water", "type": "fill", "source": "town", "source-layer": "water",
             "filter": ["==", ["get", "kind"], "lake"], "paint": {"fill-color": palette["water"]}},
            {"id": "park", "type": "fill", "source": "town", "source-layer": "landuse",
             "filter": ["==", ["get", "kind"], "park"], "paint": {"fill-color": palette["park"], "fill-opacity": 0.9}},
            {"id": "building", "type": "fill", "source": "town", "source-layer": "buildings",
             "filter": ["in", ["get", "kind"], ["literal", ["house", "town-hall"]]],
             "paint": {"fill-color": ["match", ["get", "kind"], "town-hall", "#c98c55" if not night else "#a87767", palette["building"]],
                       "fill-opacity": ["interpolate", ["linear"], ["zoom"], 14, 0.7, 20, 1]}},
            {"id": "road-casing", "type": "line", "source": "town", "source-layer": "roads",
             "filter": ["in", ["get", "class"], ["literal", ["primary", "residential"]]],
             "paint": {"line-color": palette["road-casing"],
                       "line-width": ["interpolate", ["linear"], ["zoom"], 14, 3, 20, 8],
                       },
             "layout": {"line-cap": "round", "line-join": "round"}},
            {"id": "road-primary", "type": "line", "source": "town", "source-layer": "roads",
             "filter": ["==", ["get", "class"], "primary"],
             "paint": {"line-color": palette["primary"],
                       "line-width": ["interpolate", ["linear"], ["zoom"], 14, 1, 20, 4],
                       },
             "layout": {"line-cap": "round", "line-join": "round"}},
            {"id": "road-residential", "type": "line", "source": "town", "source-layer": "roads",
             "filter": ["==", ["get", "class"], "residential"],
             "paint": {"line-color": palette["residential"], "line-width": 2},
             "layout": {"line-cap": "round", "line-join": "round"}},
            {"id": "road-footway", "type": "line", "source": "town", "source-layer": "roads",
             "filter": ["==", ["get", "class"], "footway"],
             "paint": {"line-color": palette["footway"], "line-width": 2,
                       "line-dasharray": [2, 1]},
             "layout": {"line-cap": "round", "line-join": "round"}},
            {"id": "poi", "type": "circle", "source": "town", "source-layer": "poi",
             "paint": {"circle-color": palette["point"], "circle-radius": 6,
                       "circle-stroke-color": palette["background"], "circle-stroke-width": 1}},
            {"id": "poi-icon", "type": "symbol", "source": "town", "source-layer": "poi",
             "layout": {"icon-image": "town-hall", "icon-size": 1,
                        "icon-anchor": "center", "icon-allow-overlap": True}},
            {"id": "label-positions", "type": "circle", "source": "town", "source-layer": "label-positions",
             "paint": {"circle-color": "#ffffff", "circle-radius": 2, "circle-opacity": 0.8}},
            {"id": "labels", "type": "symbol", "source": "town", "source-layer": "label-positions",
             "layout": {"text-field": ["get", "label"], "text-font": ["Open Sans Regular"],
                        "text-size": ["interpolate", ["linear"], ["zoom"], 14, 12, 20, 16],
                        "text-anchor": "top", "text-offset": [0, 0.8]},
             "paint": {"text-color": "#25323a" if not night else "#f2efe5",
                       "text-halo-color": palette["background"], "text-halo-width": 1.25}},
        ],
    }


def canonical_json(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=False, separators=(",", ":")) + "\n"


def png_chunk(kind, payload):
    crc = binascii.crc32(kind + payload) & 0xFFFFFFFF
    return len(payload).to_bytes(4, "big") + kind + payload + crc.to_bytes(4, "big")


def encode_rgba_png(pixels, width, height):
    scanlines = bytearray()
    for y in range(height):
        scanlines.append(0)
        for x in range(width):
            scanlines.extend(pixels[y][x])
    header = width.to_bytes(4, "big") + height.to_bytes(4, "big") + bytes((8, 6, 0, 0, 0))
    return b"\x89PNG\r\n\x1a\n" + b"".join((
        png_chunk(b"IHDR", header),
        png_chunk(b"IDAT", zlib.compress(bytes(scanlines), 9)),
        png_chunk(b"IEND", b""),
    ))


def authored_icon():
    transparent = (0, 0, 0, 0)
    pixels = [[transparent for _ in range(16)] for _ in range(16)]

    def rect(x0, y0, x1, y1, color):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                pixels[y][x] = color

    def polygon(points, color):
        min_y = min(y for _, y in points)
        max_y = max(y for _, y in points)
        for y in range(min_y, max_y + 1):
            for x in range(16):
                inside = False
                previous = points[-1]
                for current in points:
                    x1, y1 = current
                    x2, y2 = previous
                    if ((y1 > y) != (y2 > y)) and x < (x2 - x1) * (y - y1) / (y2 - y1) + x1:
                        inside = not inside
                    previous = current
                if inside:
                    pixels[y][x] = color

    outline = (73, 48, 39, 255)
    roof = (177, 79, 58, 255)
    wall = (235, 199, 137, 255)
    shadow = (160, 115, 72, 255)
    glass = (78, 142, 166, 255)
    door = (93, 62, 45, 255)
    polygon([(8, 1), (15, 6), (13, 8), (3, 8), (1, 6)], outline)
    polygon([(8, 2), (13, 6), (12, 7), (4, 7), (3, 6)], roof)
    rect(3, 7, 12, 13, outline)
    rect(4, 8, 11, 12, wall)
    rect(4, 8, 11, 8, shadow)
    rect(5, 9, 6, 10, glass)
    rect(9, 9, 10, 10, glass)
    rect(7, 10, 8, 12, door)
    rect(2, 13, 13, 14, outline)
    rect(1, 15, 14, 15, (93, 72, 55, 255))
    return pixels


def scale_pixels(pixels, scale):
    return [
        [pixels[y // scale][x // scale] for x in range(len(pixels[0]) * scale)]
        for y in range(len(pixels) * scale)
    ]


def write_sprites(package):
    sprite_dir = package / "sprites"
    sprite_dir.mkdir()
    pixels = authored_icon()
    for suffix, scale in (("", 1), ("@2x", 2)):
        size = 16 * scale
        record = {"town-hall": {
            "x": 0, "y": 0, "width": size, "height": size,
            "pixelRatio": scale, "sdf": False,
        }}
        (sprite_dir / f"town{suffix}.json").write_text(canonical_json(record), encoding="utf-8", newline="")
        (sprite_dir / f"town{suffix}.png").write_bytes(encode_rgba_png(scale_pixels(pixels, scale), size, size))


def copy_glyph_assets(project_root, package):
    source = project_root / "javity-maplibre/src/main/resources/org/javity/maplibre/glyphs/Open Sans Regular"
    target = package / "glyphs/Open Sans Regular"
    target.mkdir(parents=True)
    for name in ("0-255.pbf", "256-511.pbf"):
        source_file = source / name
        if not source_file.is_file():
            raise FileNotFoundError(f"offline SDF glyph source is missing: {source_file}")
        shutil.copyfile(source_file, target / name)
    source_licenses = project_root / "javity-maplibre/src/main/resources/META-INF/licenses/OpenSans"
    license_target = package / "licenses"
    license_target.mkdir()
    shutil.copyfile(source_licenses / "LICENSE-OpenSans.txt", license_target / "OpenSans-LICENSE.txt")
    shutil.copyfile(source_licenses / "NOTICE-OpenSans.txt", license_target / "OpenSans-NOTICE.txt")


def create_mbtiles(path):
    if path.exists():
        path.unlink()
    db = sqlite3.connect(path)
    try:
        db.execute("PRAGMA page_size=4096")
        db.execute("PRAGMA journal_mode=OFF")
        db.execute("PRAGMA synchronous=OFF")
        db.execute("PRAGMA locking_mode=EXCLUSIVE")
        db.execute("CREATE TABLE metadata (name TEXT, value TEXT)")
        db.execute("CREATE TABLE tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB)")
        db.execute("CREATE UNIQUE INDEX tile_index ON tiles (zoom_level, tile_column, tile_row)")
        metadata = {
            "name": "Javity Authored Town",
            "type": "overlay",
            "version": "1",
            "description": "Original authored fictional town; not OSM data.",
            "format": "pbf",
            "bounds": ",".join(f"{value:.10f}" for value in package_bounds()),
            "center": f"{CENTER_LON},{CENTER_LAT},{BASE_ZOOM}",
            "minzoom": str(MIN_ZOOM),
            "maxzoom": str(MAX_ZOOM),
            "json": json.dumps(vector_layers_json(), ensure_ascii=False, sort_keys=True, separators=(",", ":")),
        }
        db.execute("BEGIN")
        db.executemany("INSERT INTO metadata(name, value) VALUES (?, ?)", list(metadata.items()))
        tile_x, tile_y, _, _ = tile_center(BASE_ZOOM)
        tms_y = (1 << BASE_ZOOM) - 1 - tile_y
        tile_data = gzip.compress(tile_message(BASE_ZOOM), compresslevel=9, mtime=0)
        db.execute(
            "INSERT INTO tiles(zoom_level, tile_column, tile_row, tile_data) VALUES (?, ?, ?, ?)",
            (BASE_ZOOM, tile_x, tms_y, tile_data),
        )
        db.commit()
        db.execute("VACUUM")
    finally:
        db.close()


def build_package(destination):
    destination.mkdir(parents=True, exist_ok=True)
    package = destination / PACKAGE_NAME
    if package.exists():
        shutil.rmtree(package)
    package.mkdir()
    map_doc = {
        "version": 1,
        "styles": {"day": "style-day.json", "night": "style-night.json"},
        "defaultStyle": "day",
        "sources": {"town": "town.mbtiles"},
        "initialCamera": {"longitude": CENTER_LON, "latitude": CENTER_LAT, "zoom": 16, "bearing": 0, "pitch": 0},
        "attribution": "Original synthetic data created for JavityEngine.",
        "offline": True,
        "fixture": {
            "kind": "web-mercator-basezoom",
            "description": "One z14 vector tile stores the town's canonical geometry. MapLibre may overscale it at higher camera zooms.",
        },
    }
    (package / "map.json").write_text(canonical_json(map_doc), encoding="utf-8", newline="")
    project_root = pathlib.Path(__file__).resolve().parents[1]
    shutil.copyfile(project_root / "LICENSE", package / "LICENSE.txt")
    copy_glyph_assets(project_root, package)
    for mode in ("day", "night"):
        (package / f"style-{mode}.json").write_text(canonical_json(style_document(mode)), encoding="utf-8", newline="")
    write_sprites(package)
    create_mbtiles(package / "town.mbtiles")
    hashes = {
        path.relative_to(package).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
        for path in sorted(package.rglob("*")) if path.is_file()
    }
    (package / "fixture-source.json").write_text(canonical_json({
        "provenance": "Original authored fixture. No OSM, Minecraft, or third-party geographic data.",
        "generator": "tools/generate-maplibre-town.py",
        "generatorVersion": 2,
        "coordinateModel": "The authored tile coordinates are relative to the projected camera center at z14 and therefore define one stable Web Mercator geometry. No alternate normalized copies are written at other zoom levels.",
        "zoomRange": [MIN_ZOOM, MAX_ZOOM],
        "extent": EXTENT,
        "sha256": hashes,
    }), encoding="utf-8", newline="")
    return package


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=pathlib.Path,
                        default=pathlib.Path(__file__).resolve().parents[1] / "javity-desktop/src/main/resources/org/javity/demo")
    args = parser.parse_args()
    package = build_package(args.output.resolve())
    print(f"Generated {package}")


if __name__ == "__main__":
    main()
