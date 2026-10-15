#!/usr/bin/env python3
"""Import a small, reproducible Gorzów Wielkopolski OpenFreeMap package.

The importer preserves upstream MVT tile payloads. It fetches only the bounded
z12-z14 footprint used by the demo, with hard byte/concurrency limits. Python
standard library only; use --offline to assemble solely from the local cache.
"""

import argparse
import concurrent.futures
import gzip
import hashlib
import json
import math
import pathlib
import shutil
import sqlite3
import struct
import tempfile
import time
import urllib.error
import urllib.request


CENTER_LON = 15.2385
CENTER_LAT = 52.7319
BASE_ZOOM = 14
MIN_ZOOM = 12
MAX_ZOOM = 14
TILEJSON_URL = "https://tiles.openfreemap.org/planet"
TILEJSON_SNAPSHOT = pathlib.Path(__file__).resolve().parent / "map-data/openfreemap-20261011_080001_pt.json"
TILEJSON_SHA256 = "6da5604000f11b01be50b7767c6a781fe7b880a1e6c8d0500fb153eb6d7cc51d"
TILE_TEMPLATE = "https://tiles.openfreemap.org/planet/20261011_080001_pt/{z}/{x}/{y}.pbf"
USER_AGENT = "JavityEngine-demo/0.7 (local evaluation)"
MAX_TILES = 50
MAX_TILE_BYTES = 2 * 1024 * 1024
MAX_TOTAL_BYTES = 32 * 1024 * 1024
MAX_WORKERS = 2
TIMEOUT_SECONDS = 20
RETRIES = 2
PACKAGE_NAME = "maplibre-gorzow"
ATTRIBUTION = "© OpenStreetMap contributors · OpenMapTiles · OpenFreeMap"
SOURCE_LAYERS = (
    "transportation", "transportation_name", "water", "waterway", "building",
    "landcover", "landuse", "park", "place", "poi", "water_name", "boundary",
)


def world_xy(lon, lat):
    lat = max(-85.05112878, min(85.05112878, lat))
    return (lon + 180.0) / 360.0, 0.5 - math.asinh(math.tan(math.radians(lat))) / (2 * math.pi)


def center_tile(z):
    n = 1 << z
    wx, wy = world_xy(CENTER_LON, CENTER_LAT)
    return min(n - 1, int(wx * n)), min(n - 1, int(wy * n))


def tile_footprint():
    """Return z14 5x5 footprint and the fully covering z13/z12 tiles."""
    x, y = center_tile(BASE_ZOOM)
    coords = set()
    for z in (BASE_ZOOM, BASE_ZOOM - 1, BASE_ZOOM - 2):
        factor = 1 << (BASE_ZOOM - z)
        x0, x1 = (x - 2) // factor, (x + 2) // factor
        y0, y1 = (y - 2) // factor, (y + 2) // factor
        n = 1 << z
        coords.update((z, tx, ty) for tx in range(max(0, x0), min(n - 1, x1) + 1)
                      for ty in range(max(0, y0), min(n - 1, y1) + 1))
    return sorted(coords)


def cache_path(cache, coord):
    z, x, y = coord
    return pathlib.Path(cache) / str(z) / str(x) / f"{y}.pbf"


def get_tile(coord, cache, download=True, opener=None):
    path = cache_path(cache, coord)
    if path.is_file():
        data = path.read_bytes()
        if not data or len(data) > MAX_TILE_BYTES:
            raise ValueError(f"cached tile outside byte bounds: {coord}")
        return data
    if not download:
        raise FileNotFoundError(f"tile {coord} is absent from cache {cache}")
    opener = opener or urllib.request.urlopen
    z, x, y = coord
    url = TILE_TEMPLATE.format(z=z, x=x, y=y)
    last_error = None
    for attempt in range(RETRIES + 1):
        request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "application/x-protobuf"})
        try:
            with opener(request, timeout=TIMEOUT_SECONDS) as response:
                data = response.read(MAX_TILE_BYTES + 1)
            if not data or len(data) > MAX_TILE_BYTES:
                raise ValueError(f"tile {coord} exceeds {MAX_TILE_BYTES} bytes or is empty")
            # Reject HTML/error bodies while preserving binary MVT as supplied.
            if data.lstrip().startswith((b"<", b"{")):
                raise ValueError(f"tile endpoint returned a non-MVT response for {coord}")
            path.parent.mkdir(parents=True, exist_ok=True)
            temporary = path.with_suffix(".pbf.tmp")
            temporary.write_bytes(data)
            temporary.replace(path)
            return data
        except (urllib.error.URLError, TimeoutError, OSError) as error:
            last_error = error
            if attempt < RETRIES:
                time.sleep(0.2 * (attempt + 1))
    raise RuntimeError(f"could not fetch {coord} after {RETRIES + 1} attempts: {last_error}")


def fetch_tiles(coords, cache, download=True):
    if len(coords) > MAX_TILES:
        raise ValueError(f"tile request has {len(coords)} tiles; limit is {MAX_TILES}")
    # Check existing cache before requests to avoid beginning an over-budget build.
    cached_bytes = sum(cache_path(cache, c).stat().st_size for c in coords if cache_path(cache, c).is_file())
    if cached_bytes > MAX_TOTAL_BYTES:
        raise ValueError("cached tile set exceeds 32 MiB")
    tiles = {}
    total = 0
    with concurrent.futures.ThreadPoolExecutor(max_workers=MAX_WORKERS) as pool:
        futures = {pool.submit(get_tile, coord, cache, download): coord for coord in coords}
        for future in concurrent.futures.as_completed(futures):
            coord = futures[future]
            data = future.result()
            total += len(data)
            if total > MAX_TOTAL_BYTES:
                raise ValueError("downloaded tile set exceeds 32 MiB")
            tiles[coord] = data
    return dict(sorted(tiles.items()))


def _varint(data, pos):
    value = shift = 0
    while pos < len(data):
        byte = data[pos]
        pos += 1
        value |= (byte & 0x7f) << shift
        if byte < 0x80:
            return value, pos
        shift += 7
        if shift >= 70:
            raise ValueError("overlong protobuf varint")
    raise ValueError("truncated protobuf varint")


def protobuf_fields(data):
    pos = 0
    while pos < len(data):
        tag, pos = _varint(data, pos)
        number, wire = tag >> 3, tag & 7
        if number == 0:
            raise ValueError("invalid protobuf field number")
        if wire == 0:
            value, pos = _varint(data, pos)
        elif wire == 1:
            value, pos = data[pos:pos + 8], pos + 8
        elif wire == 2:
            length, pos = _varint(data, pos)
            value, pos = data[pos:pos + length], pos + length
            if len(value) != length:
                raise ValueError("truncated protobuf bytes field")
        elif wire == 5:
            value, pos = data[pos:pos + 4], pos + 4
        else:
            raise ValueError(f"unsupported protobuf wire type {wire}")
        yield number, wire, value


def decode_value(data):
    for number, wire, value in protobuf_fields(data):
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
            return (value >> 1) ^ -(value & 1)
        if number == 7 and wire == 0:
            return bool(value)
    return None


def tile_statistics(tile):
    """Read actual MVT layer names, feature totals, and a small set of names."""
    if tile.startswith(b"\x1f\x8b"):
        tile = gzip.decompress(tile)
    result = {}
    for number, wire, raw_layer in protobuf_fields(tile):
        if number != 3 or wire != 2:
            continue
        layer = list(protobuf_fields(raw_layer))
        name = next((v.decode("utf-8") for n, w, v in layer if n == 1 and w == 2), None)
        if not name:
            continue
        keys = [v.decode("utf-8") for n, w, v in layer if n == 3 and w == 2]
        values = [decode_value(v) for n, w, v in layer if n == 4 and w == 2]
        count = 0
        names = set()
        for n, w, raw_feature in layer:
            if n != 2 or w != 2:
                continue
            count += 1
            for fn, fw, raw in protobuf_fields(raw_feature):
                if fn != 2 or fw != 2:
                    continue
                tags, pos = [], 0
                while pos < len(raw):
                    value, pos = _varint(raw, pos)
                    tags.append(value)
                for i in range(0, len(tags) - 1, 2):
                    key_index, value_index = tags[i], tags[i + 1]
                    if key_index < len(keys) and value_index < len(values) and keys[key_index] in ("name", "name:pl"):
                        value = values[value_index]
                        if isinstance(value, str) and value:
                            names.add(value)
        result[name] = {"featureCount": count, "names": sorted(names)[:20]}
    return result


def _source_metadata(tilejson_path=None):
    # The exact release metadata is a checked-in input, independent of /tmp/network.
    source_path = pathlib.Path(tilejson_path) if tilejson_path is not None else TILEJSON_SNAPSHOT
    with source_path.open("rb") as stream:
        contents = stream.read(2 * 1024 * 1024 + 1)
    if len(contents) > 2 * 1024 * 1024:
        raise ValueError("TileJSON metadata exceeds 2 MiB")
    if tilejson_path is None and hashlib.sha256(contents).hexdigest() != TILEJSON_SHA256:
        raise ValueError("Pinned OpenFreeMap TileJSON checksum mismatch")
    raw = json.loads(contents)
    if TILE_TEMPLATE not in raw.get("tiles", []):
        raise ValueError("TileJSON does not describe the pinned tile release")
    layers = {item["id"]: item for item in raw.get("vector_layers", [])}
    if not all(layer in layers for layer in SOURCE_LAYERS):
        raise ValueError("TileJSON is missing required OpenMapTiles layers")
    return [layers[layer] for layer in SOURCE_LAYERS]


def canonical_json(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=False, separators=(",", ":")) + "\n"


def map_style(mode):
    night = mode == "night"
    p = {
        "background": "#14212b" if night else "#eef1ed",
        "water": "#244c67" if night else "#87bfd1",
        "land": "#e5e5dc" if night else "#f2f0e8",
        "wood": "#263f37" if night else "#d6e4cf",
        "park": "#2e513f" if night else "#cfe1c7",
        "building": "#4c5661" if night else "#d8d1c4",
        "road-casing": "#1e2a31" if night else "#b2aa9c",
        "road": "#788a94" if night else "#fffdf8",
        "major": "#d6a34e" if night else "#f4d087",
        "text": "#e8edf0" if night else "#37434a",
        "halo": "#192831" if night else "#fffefa",
    }
    layers = [
        {"id":"background","type":"background","paint":{"background-color":p["background"]}},
        {"id":"landcover","type":"fill","source":"gorzow","source-layer":"landcover","paint":{"fill-color":["match",["get","class"],"wood",p["wood"],"grass",p["park"],"farmland",p["land"],p["land"]],"fill-opacity":0.9}},
        {"id":"landuse","type":"fill","source":"gorzow","source-layer":"landuse","paint":{"fill-color":["match",["get","class"],"residential",p["land"],"industrial",p["land"],"commercial",p["land"],p["park"]],"fill-opacity":0.6}},
        {"id":"park","type":"fill","source":"gorzow","source-layer":"park","paint":{"fill-color":p["park"],"fill-opacity":0.85}},
        {"id":"water","type":"fill","source":"gorzow","source-layer":"water","paint":{"fill-color":p["water"]}},
        {"id":"waterway","type":"line","source":"gorzow","source-layer":"waterway","paint":{"line-color":p["water"],"line-width":["interpolate",["linear"],["zoom"],8,0.5,15,2.5]}},
        {"id":"building","type":"fill","source":"gorzow","source-layer":"building","minzoom":13,"paint":{"fill-color":p["building"],"fill-outline-color":p["road-casing"],"fill-opacity":0.82}},
        {"id":"boundary","type":"line","source":"gorzow","source-layer":"boundary","paint":{"line-color":p["road-casing"],"line-width":1,"line-dasharray":[2,2],"line-opacity":0.7}},
        {"id":"road-names","type":"symbol","source":"gorzow","source-layer":"transportation_name","minzoom":13,"filter":["any",["has","name:pl"],["has","name"]],"layout":{"symbol-placement":"line","text-field":["coalesce",["get","name:pl"],["get","name"]],"text-font":["Open Sans Regular"],"text-size":["interpolate",["linear"],["zoom"],13,10,17,13],"text-max-angle":30,"text-padding":2},"paint":{"text-color":p["text"],"text-halo-color":p["halo"],"text-halo-width":1.2}},
        {"id":"water-names","type":"symbol","source":"gorzow","source-layer":"water_name","minzoom":10,"filter":["any",["has","name:pl"],["has","name"]],"layout":{"symbol-placement":"line","text-field":["coalesce",["get","name:pl"],["get","name"]],"text-font":["Open Sans Regular"],"text-size":11},"paint":{"text-color":p["water"],"text-halo-color":p["halo"],"text-halo-width":1}},
        {"id":"place-labels","type":"symbol","source":"gorzow","source-layer":"place","minzoom":8,"filter":["all",["any",["has","name:pl"],["has","name"]],["in",["get","class"],["literal",["city","town","village","suburb","neighbourhood"]]]],"layout":{"text-field":["coalesce",["get","name:pl"],["get","name"]],"text-font":["Open Sans Regular"],"text-size":["match",["get","class"],"city",17,"town",15,"village",12,10],"text-max-width":10,"text-allow-overlap":False},"paint":{"text-color":p["text"],"text-halo-color":p["halo"],"text-halo-width":1.5}},
        {"id":"poi-labels","type":"symbol","source":"gorzow","source-layer":"poi","minzoom":15,"filter":["all",["any",["has","name:pl"],["has","name"]],["in",["get","class"],["literal",["hospital","school","university","place_of_worship","museum","park","bus_station","railway_station"]]]],"layout":{"text-field":["coalesce",["get","name:pl"],["get","name"]],"text-font":["Open Sans Regular"],"text-size":10,"text-max-width":8,"text-optional":True},"paint":{"text-color":p["text"],"text-halo-color":p["halo"],"text-halo-width":1.2}},
    ]
    # Physical grade takes precedence over road class. Never redraw a bridge as a
    # ground path or a tunnel as an ordinary road. Casings/fills are paired per
    # class so a main street covers minor crossings without fragmented outlines.
    geometry = layers[:8]
    labels = {layer["id"]: layer for layer in layers[8:]}
    road_classes = ["path", "track", "pedestrian", "service", "minor", "tertiary", "secondary", "primary", "trunk", "motorway"]
    widths = [1.2, 1.5, 2.2, 2.4, 3.2, 4.0, 4.8, 5.4, 6.0, 6.8]
    transport = []
    grade = ["to-number", ["get", "layer"], 0]
    for structure in ("tunnel", "ground", "bridge"):
        # OpenMapTiles layer is normally -5..5. Outer buckets retain unusual
        # values instead of silently dropping infrastructure from custom maps.
        levels = range(-5, 6) if structure != "ground" else (0,)
        for level in levels:
            structure_filter = (["!", ["in", ["get", "brunnel"], ["literal", ["bridge", "tunnel"]]]]
                                if structure == "ground" else ["==", ["get", "brunnel"], structure])
            level_filter = (["<=", grade, -5] if level == -5 else [">=", grade, 5] if level == 5 else ["==", grade, level])
            if structure == "ground": level_filter = True
            for rank, road_class in enumerate(["rail"] + road_classes):
                rail = road_class == "rail"
                width = 1.4 if rail else widths[rank - 1]
                color = p["major"] if road_class in ("secondary", "primary", "trunk", "motorway") else p["road"]
                road_filter = ["all", structure_filter, level_filter, ["==", ["get", "class"], road_class]]
                layout = {"line-cap": "butt" if structure != "ground" else "round", "line-join": "round"}
                for casing in (True, False):
                    paint = {"line-color": p["road-casing"] if casing or rail else color,
                             "line-width": ["interpolate", ["linear"], ["zoom"], 8, 0.3, 14, width + (1.6 if casing else 0), 17, width * 2 + (2 if casing else 0)]}
                    if structure == "tunnel":
                        paint["line-opacity"] = 0.45 if casing else 0.65
                        if casing: paint["line-dasharray"] = [2, 2]
                    if rail or road_class in ("path", "track"):
                        if casing: continue
                        paint["line-dasharray"] = [2, 1.5]
                    transport.append({"id": f"{structure}-{level}-{road_class}-{'casing' if casing else 'fill'}",
                                      "type": "line", "source": "gorzow", "source-layer": "transportation",
                                      "filter": road_filter, "layout": layout.copy(), "paint": paint})
    # Tunnels precede buildings and all surface/bridge traffic. River crossings
    # retain the class-specific deck colour and square bridge abutments.
    tunnels = [layer for layer in transport if layer["id"].startswith("tunnel-")]
    surface = [layer for layer in transport if not layer["id"].startswith("tunnel-")]
    layers = geometry[:6] + tunnels + geometry[6:] + surface
    for layer in labels.values():
        layout = layer["layout"]
        layout.update({"text-allow-overlap": False, "text-ignore-placement": False,
                       "text-padding": 5, "text-keep-upright": True})
    roads = labels["road-names"]["layout"]
    roads.update({"symbol-spacing": 380, "text-max-angle": 20,
                  "text-size": ["interpolate", ["linear"], ["zoom"], 13, 11, 17, 14],
                  "symbol-sort-key": ["match", ["get", "class"], "motorway", 0, "trunk", 1, "primary", 2, "secondary", 3, "tertiary", 4, 5]})
    poi = labels["poi-labels"]["layout"]
    poi.update({"text-size": 11, "text-max-width": 10, "text-line-height": 1.25,
                "text-variable-anchor": ["top", "bottom", "left", "right"], "text-radial-offset": 0.6})
    labels["poi-labels"]["minzoom"] = 15
    labels["water-names"]["paint"]["text-color"] = "#83b4d1" if night else "#37667e"
    # Placement runs from topmost style layers: streets and places reserve room
    # before optional POIs. Shared source keeps collision detection tile-aware.
    layers += [labels[name] for name in ("poi-labels", "water-names", "road-names", "place-labels")]
    return {"version":8,"name":"Gorzów Wielkopolski — "+mode,"sources":{"gorzow":{"type":"vector","url":"mbtiles://@gorzow"}},"glyphs":"glyphs/{fontstack}/{range}.pbf","layers":layers}


def create_mbtiles(path, tiles, vector_layers):
    if path.exists():
        path.unlink()
    db = sqlite3.connect(path)
    try:
        db.execute("PRAGMA page_size=4096")
        db.execute("PRAGMA journal_mode=OFF")
        db.execute("PRAGMA synchronous=OFF")
        db.execute("CREATE TABLE metadata (name TEXT, value TEXT)")
        db.execute("CREATE TABLE tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB)")
        db.execute("CREATE UNIQUE INDEX tile_index ON tiles (zoom_level, tile_column, tile_row)")
        n = 1 << BASE_ZOOM
        # Bounds cover the z14 5x5 footprint (coordinates from XYZ edges).
        wx0 = (min(x for z, x, y in tiles if z == BASE_ZOOM)) / n
        wx1 = (max(x for z, x, y in tiles if z == BASE_ZOOM) + 1) / n
        wy0 = (min(y for z, x, y in tiles if z == BASE_ZOOM)) / n
        wy1 = (max(y for z, x, y in tiles if z == BASE_ZOOM) + 1) / n
        def lon(wx): return wx * 360.0 - 180.0
        def lat(wy): return math.degrees(math.atan(math.sinh(math.pi * (1 - 2 * wy))))
        metadata = {
            "name":"Gorzów Wielkopolski · OpenFreeMap offline demo", "type":"overlay", "version":"1",
            "description":"Bounded OpenFreeMap OpenMapTiles vector data centered on Gorzów Wielkopolski. Original MVT payloads retained.",
            "format":"pbf", "bounds":f"{lon(wx0):.8f},{lat(wy1):.8f},{lon(wx1):.8f},{lat(wy0):.8f}",
            "center":f"{CENTER_LON},{CENTER_LAT},{BASE_ZOOM}", "minzoom":str(MIN_ZOOM), "maxzoom":str(MAX_ZOOM),
            "json":json.dumps({"vector_layers":vector_layers},ensure_ascii=False,sort_keys=True,separators=(",",":")),
        }
        db.execute("BEGIN")
        db.executemany("INSERT INTO metadata(name,value) VALUES (?,?)", metadata.items())
        for (z, x, y), payload in tiles.items():
            tms_y = (1 << z) - 1 - y
            # Deterministic gzip wrapper only; uncompressed bytes are upstream MVT.
            if not payload.startswith(b"\x1f\x8b"):
                payload = gzip.compress(payload, compresslevel=9, mtime=0)
            else:
                payload = gzip.compress(gzip.decompress(payload), compresslevel=9, mtime=0)
            db.execute("INSERT INTO tiles VALUES (?,?,?,?)", (z,x,tms_y,payload))
        db.commit()
        db.execute("VACUUM")
    finally:
        db.close()


def build_package(destination, tiles, tilejson_path=None):
    destination = pathlib.Path(destination)
    package = destination / PACKAGE_NAME
    if package.exists():
        shutil.rmtree(package)
    package.mkdir(parents=True)
    source_layers = _source_metadata(tilejson_path)
    # Require schema described by the pinned TileJSON and write only selected layers.
    map_doc = {"version":1,"styles":{"day":"style-day.json","night":"style-night.json"},"defaultStyle":"day",
        "sources":{"gorzow":"gorzow.mbtiles"},"initialCamera":{"longitude":CENTER_LON,"latitude":CENTER_LAT,"zoom":15,"bearing":0,"pitch":0},
        "attribution":ATTRIBUTION,"offline":True}
    (package / "map.json").write_text(canonical_json(map_doc),encoding="utf-8",newline="")
    project_root = pathlib.Path(__file__).resolve().parents[1]
    shutil.copyfile(project_root / "LICENSE", package / "LICENSE.txt")
    (package / "licenses").mkdir()
    source_font = project_root / "javity-maplibre/src/main/resources/org/javity/maplibre/glyphs/Open Sans Regular"
    (package / "glyphs/Open Sans Regular").mkdir(parents=True, exist_ok=True)
    for font_file in ("0-255.pbf","256-511.pbf","8192-8447.pbf"):
        shutil.copyfile(source_font / font_file, package / "glyphs/Open Sans Regular" / font_file)
    font_licenses = project_root / "javity-maplibre/src/main/resources/META-INF/licenses/OpenSans"
    shutil.copyfile(font_licenses / "LICENSE-OpenSans.txt",package / "licenses/OpenSans-LICENSE.txt")
    shutil.copyfile(font_licenses / "NOTICE-OpenSans.txt",package / "licenses/OpenSans-NOTICE.txt")
    shutil.copyfile(project_root / "tools/licenses/ODbL-1.0.txt", package / "licenses/ODbL-1.0.txt")
    (package / "licenses/ODbL-NOTICE.txt").write_text("OpenStreetMap data is licensed under ODbL 1.0.\nFull license source: https://opendatacommons.org/licenses/odbl/odbl-10.txt\nOpenStreetMap attribution: https://www.openstreetmap.org/copyright\n",encoding="utf-8")
    for mode in ("day","night"):
        (package / f"style-{mode}.json").write_text(canonical_json(map_style(mode)),encoding="utf-8",newline="")
    create_mbtiles(package / "gorzow.mbtiles",tiles,source_layers)
    hashes={path.relative_to(package).as_posix():hashlib.sha256(path.read_bytes()).hexdigest() for path in sorted(package.rglob("*")) if path.is_file() and path.name!="fixture-source.json"}
    stats={"/".join(map(str,coord)):tile_statistics(data) for coord,data in tiles.items()}
    record={"provenance":"OpenStreetMap-derived OpenMapTiles vector tiles served by OpenFreeMap; original MVT bytes are retained (MBTiles gzip wrapper is deterministic).",
        "source":"OpenFreeMap public free tile service; no API key, account, or quota configured.","tilejsonUrl":TILEJSON_URL,
        "tilejsonSha256":hashlib.sha256((pathlib.Path(tilejson_path) if tilejson_path is not None else TILEJSON_SNAPSHOT).read_bytes()).hexdigest(),
        "tileTemplate":TILE_TEMPLATE,"tilejsonVersion":"3.0.0","openMapTilesSchemaVersion":"3.16.0",
        "license":"Open Database License (ODbL) 1.0; see licenses/ODbL-1.0.txt","attribution":ATTRIBUTION,
        "zoomRange":[MIN_ZOOM,MAX_ZOOM],"tileCount":len(tiles),"sourceTileSha256":{"/".join(map(str,c)):hashlib.sha256(v).hexdigest() for c,v in tiles.items()},
        "tileStatistics":stats,"sha256":hashes}
    (package / "fixture-source.json").write_text(canonical_json(record),encoding="utf-8",newline="")
    return package


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output",type=pathlib.Path,default=pathlib.Path(__file__).resolve().parents[1]/"javity-desktop/src/main/resources/org/javity/demo")
    parser.add_argument("--cache",type=pathlib.Path,default=pathlib.Path(tempfile.gettempdir())/"javity-gorzow-probe/cache")
    parser.add_argument("--offline",action="store_true",help="use only the existing tile cache")
    args=parser.parse_args()
    coords=tile_footprint()
    tiles=fetch_tiles(coords,args.cache,download=not args.offline)
    package=build_package(args.output,tiles)
    print(f"Generated {package} from {len(tiles)} source tiles ({sum(map(len,tiles.values()))} source bytes)")
    names=sorted({name for stats in (tile_statistics(v) for v in tiles.values()) for layer in stats.values() for name in layer["names"]})
    print("Observed names: "+", ".join(names[:35]))


if __name__ == "__main__":
    main()
