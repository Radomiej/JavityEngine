"""CPU-only validation for the checked-in MapLibre glyph PBF resources."""
from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools/generate-maplibre-glyphs.py"
RESOURCE_DIR = ROOT / "javity-maplibre/src/main/resources/org/javity/maplibre/glyphs/Open Sans Regular"

spec = importlib.util.spec_from_file_location("generate_maplibre_glyphs", SCRIPT)
assert spec and spec.loader
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def read_varint(data: bytes, offset: int) -> tuple[int, int]:
    value = 0
    shift = 0
    while True:
        byte = data[offset]
        offset += 1
        value |= (byte & 0x7F) << shift
        if byte < 0x80:
            return value, offset
        shift += 7
        if shift > 63:
            raise AssertionError("invalid varint")


def fields(data: bytes):
    offset = 0
    while offset < len(data):
        key, offset = read_varint(data, offset)
        number, wire_type = key >> 3, key & 7
        if wire_type == 0:
            value, offset = read_varint(data, offset)
        elif wire_type == 2:
            size, offset = read_varint(data, offset)
            value = data[offset:offset + size]
            if len(value) != size:
                raise AssertionError("truncated length-delimited field")
            offset += size
        else:
            raise AssertionError(f"unexpected protobuf wire type {wire_type}")
        yield number, value


def parse_range(data: bytes) -> tuple[str, str, dict[int, dict[int, object]]]:
    top = list(fields(data))
    assert all(number == 1 for number, _ in top)
    assert len(top) == 1
    stack_fields = list(fields(top[0][1]))
    names = [value.decode("utf-8") for number, value in stack_fields if number == 1]
    ranges = [value.decode("ascii") for number, value in stack_fields if number == 2]
    glyph_messages = [value for number, value in stack_fields if number == 3]
    glyphs: dict[int, dict[int, object]] = {}
    for message in glyph_messages:
        glyph_fields = dict(fields(message))
        codepoint = glyph_fields[1]
        glyphs[codepoint] = glyph_fields
    assert len(names) == 1 and len(ranges) == 1
    return names[0], ranges[0], glyphs


class MapLibreGlyphGeneratorTest(unittest.TestCase):
    def test_offline_range_covers_typographic_punctuation_in_real_osm_labels(self):
        self.assertIn((8192, 8447), module.RANGES)
        path = RESOURCE_DIR / "8192-8447.pbf"
        self.assertTrue(path.is_file())
        _, _, glyphs = parse_range(path.read_bytes())
        for character in "–—‘’“”…":
            self.assertIn(ord(character), glyphs, character)

    def test_zero_distance_edge_uses_maplibre_shader_cutoff_byte(self):
        # MapLibre Native and GL JS use an SDF fill threshold of (256 - 64) / 256.
        self.assertEqual(bytes([192]), module._encode_signed_distance(module.np.array([0.0])))
        self.assertEqual(bytes([255]), module._encode_signed_distance(module.np.array([-8.0])))
        self.assertEqual(bytes([0]), module._encode_signed_distance(module.np.array([6.0])))

    def test_checked_in_ranges_are_valid_and_cover_polish_text(self):
        cases = ((0, 255), (256, 511))
        all_glyphs = {}
        for start, end in cases:
            path = RESOURCE_DIR / f"{start}-{end}.pbf"
            self.assertTrue(path.is_file(), path)
            name, range_name, glyphs = parse_range(path.read_bytes())
            self.assertEqual(module.FONT_STACK, name)
            self.assertEqual(f"{start}-{end}", range_name)
            self.assertGreater(len(glyphs), 100)
            self.assertTrue(all(start <= codepoint <= end for codepoint in glyphs))

            advances = []
            saw_bitmap = False
            for codepoint, glyph in glyphs.items():
                self.assertTrue({1, 3, 4, 5, 6, 7}.issubset(glyph), f"U+{codepoint:04X} lacks required metrics")
                width, height = glyph[3], glyph[4]
                self.assertGreaterEqual(width, 0)
                self.assertGreaterEqual(height, 0)
                self.assertLess(width, 256)
                self.assertLess(height, 256)
                self.assertGreaterEqual(glyph[7], 0)
                self.assertLess(glyph[7], 256)
                advances.append(glyph[7])
                bitmap = glyph.get(2)
                if width and height:
                    self.assertIsNotNone(bitmap, f"U+{codepoint:04X} has dimensions but no SDF")
                    self.assertEqual((width + 6) * (height + 6), len(bitmap))
                    self.assertLess(min(bitmap), 128)
                    self.assertGreater(max(bitmap), 128)
                    saw_bitmap = True
                else:
                    self.assertIsNone(bitmap, f"U+{codepoint:04X} should have no bitmap")
            self.assertTrue(saw_bitmap)
            self.assertGreater(max(advances), min(advances))
            all_glyphs.update(glyphs)

        polish = "ĄąĆćĘęŁłŃńÓóŚśŹźŻż"
        for character in polish:
            self.assertIn(ord(character), all_glyphs, f"missing Polish glyph {character}")

    def test_generator_is_deterministic_and_matches_checked_in_resources(self):
        with tempfile.TemporaryDirectory() as first, tempfile.TemporaryDirectory() as second:
            generated_one = module.generate(Path(first))
            generated_two = module.generate(Path(second))
            for path_one, path_two in zip(generated_one, generated_two, strict=True):
                self.assertEqual(path_one.read_bytes(), path_two.read_bytes())
                self.assertEqual((RESOURCE_DIR / path_one.name).read_bytes(), path_one.read_bytes())


if __name__ == "__main__":
    unittest.main()
