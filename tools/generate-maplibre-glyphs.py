#!/usr/bin/env python3
"""Generate offline MapLibre Native glyph PBF ranges from Javity's bundled Open Sans font.

The output follows misc/proto/glyphs.proto from maplibre-native. Glyph bitmaps are
single-channel signed-distance fields rendered at 24 px, with radius 8 and a
3 px bitmap border. Runtime builds need neither Pillow nor SciPy; they consume
the generated PBFs as ordinary local style glyph resources.
"""
from __future__ import annotations

import argparse
import struct
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont
from scipy.ndimage import distance_transform_edt

REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
FONT_PATH = REPOSITORY_ROOT / "javity-ui/src/main/resources/org/javity/ui/fonts/OpenSans-Regular.ttf"
DEFAULT_OUTPUT = REPOSITORY_ROOT / "javity-maplibre/src/main/resources/org/javity/maplibre/glyphs/Open Sans Regular"
FONT_STACK = "Open Sans Regular"
FONT_SIZE = 24
SDF_RADIUS = 8
SDF_CUTOFF = 0.25
BITMAP_BORDER = 3
RANGES = ((0, 255), (256, 511), (8192, 8447))
OFFICIAL_GLYPH_PROTO = "https://github.com/maplibre/maplibre-native/blob/main/misc/proto/glyphs.proto"


def _u16(data: bytes, offset: int) -> int:
    return struct.unpack_from(">H", data, offset)[0]


def _u32(data: bytes, offset: int) -> int:
    return struct.unpack_from(">I", data, offset)[0]


def _font_cmap(font_bytes: bytes) -> tuple[tuple[int, int], ...]:
    """Read Unicode cmap subtables without adding a fontTools runtime dependency."""
    table_count = _u16(font_bytes, 4)
    cmap_offset = None
    cmap_length = None
    for index in range(table_count):
        record = 12 + index * 16
        if font_bytes[record:record + 4] == b"cmap":
            cmap_offset = _u32(font_bytes, record + 8)
            cmap_length = _u32(font_bytes, record + 12)
            break
    if cmap_offset is None or cmap_length is None:
        raise ValueError("The supplied font has no cmap table")

    cmap = font_bytes[cmap_offset:cmap_offset + cmap_length]
    records = _u16(cmap, 2)
    subtables: list[tuple[int, int]] = []
    for index in range(records):
        record = 4 + index * 8
        platform = _u16(cmap, record)
        encoding = _u16(cmap, record + 2)
        offset = _u32(cmap, record + 4)
        fmt = _u16(cmap, offset)
        if platform == 0 or (platform == 3 and encoding in (1, 10)):
            if fmt in (4, 12):
                subtables.append((offset, fmt))
    if not subtables:
        raise ValueError("The supplied font has no supported Unicode cmap table")
    return tuple(subtables)


def _has_glyph(font_bytes: bytes, subtables: tuple[tuple[int, int], ...], codepoint: int) -> bool:
    cmap_offset = _u32(next(
        font_bytes[12 + index * 16 + 8:12 + index * 16 + 12]
        for index in range(_u16(font_bytes, 4))
        if font_bytes[12 + index * 16:16 + index * 16] == b"cmap"
    ), 0)
    cmap = font_bytes[cmap_offset:]
    for offset, fmt in subtables:
        if fmt == 12:
            group_count = _u32(cmap, offset + 12)
            low, high = 0, group_count
            while low < high:
                mid = (low + high) // 2
                group = offset + 16 + mid * 12
                start, end = _u32(cmap, group), _u32(cmap, group + 4)
                if codepoint < start:
                    high = mid
                elif codepoint > end:
                    low = mid + 1
                else:
                    return _u32(cmap, group + 8) + codepoint - start != 0
        elif codepoint <= 0xFFFF:
            segment_count = _u16(cmap, offset + 6) // 2
            end_array = offset + 14
            start_array = end_array + segment_count * 2 + 2
            delta_array = start_array + segment_count * 2
            range_array = delta_array + segment_count * 2
            for index in range(segment_count):
                end = _u16(cmap, end_array + index * 2)
                start = _u16(cmap, start_array + index * 2)
                if start <= codepoint <= end:
                    delta = _u16(cmap, delta_array + index * 2)
                    range_offset_position = range_array + index * 2
                    range_offset = _u16(cmap, range_offset_position)
                    if range_offset == 0:
                        glyph_id = (codepoint + delta) & 0xFFFF
                    else:
                        glyph_position = range_offset_position + range_offset + 2 * (codepoint - start)
                        glyph_id = _u16(cmap, glyph_position)
                        if glyph_id:
                            glyph_id = (glyph_id + delta) & 0xFFFF
                    if glyph_id:
                        return True
                    break
    return False


def _varint(value: int) -> bytes:
    if value < 0:
        raise ValueError("protobuf varints must be non-negative")
    encoded = bytearray()
    while value > 0x7F:
        encoded.append((value & 0x7F) | 0x80)
        value >>= 7
    encoded.append(value)
    return bytes(encoded)


def _field_varint(number: int, value: int) -> bytes:
    return _varint(number << 3) + _varint(value)


def _field_bytes(number: int, payload: bytes) -> bytes:
    return _varint((number << 3) | 2) + _varint(len(payload)) + payload


def _signed_zigzag(value: int) -> int:
    return (value << 1) ^ (value >> 31)


def _encode_signed_distance(distance: np.ndarray) -> bytes:
    """Encode positive-outside pixel distances using MapLibre's 0.25 cutoff convention."""
    # Native and GL JS symbol_sdf shaders use inner_edge=(256-64)/256 = 0.75.
    # With a 0.25 cutoff, a zero-distance contour encodes to 192 after byte quantization.
    encoded = 255.0 * (1.0 - (distance / SDF_RADIUS + SDF_CUTOFF))
    return np.ceil(np.clip(encoded, 0, 255)).astype(np.uint8).tobytes()


def _sdf_bitmap(mask: Image.Image) -> bytes:
    """Convert an antialiased mask to an 8 px SDF with a 3 px glyph bitmap border."""
    alpha = np.asarray(mask, dtype=np.uint8)
    padded = np.pad(alpha, BITMAP_BORDER, mode="constant", constant_values=0)
    inside = padded >= 128
    # Positive means outside, negative means inside. The binary EDTs are adjusted by
    # a half pixel because EDT measures center-to-center distances: the rasterized
    # contour lies halfway between opposite-color pixel centers. Fractional source
    # coverage then moves that contour within the antialiased edge pixels.
    signed_distance = distance_transform_edt(~inside) - distance_transform_edt(inside)
    inside_float = inside.astype(np.float32)
    signed_distance += inside_float - 0.5
    signed_distance += inside_float - padded.astype(np.float32) / 255.0
    return _encode_signed_distance(signed_distance)


def _glyph_message(font: ImageFont.FreeTypeFont, codepoint: int) -> bytes:
    character = chr(codepoint)
    left, top, right, bottom = font.getbbox(character, anchor="ls")
    width, height = right - left, bottom - top
    if min(width, height) < 0 or max(width, height) >= 256:
        raise ValueError(f"Glyph U+{codepoint:04X} has unsupported bitmap dimensions {width}x{height}")
    advance = int(round(font.getlength(character)))
    if not 0 <= advance < 256 or not -128 <= left < 128 or not -128 <= -top < 128:
        raise ValueError(f"Glyph U+{codepoint:04X} has metrics outside the MapLibre PBF limits")

    payload = bytearray()
    payload += _field_varint(1, codepoint)
    if width > 0 and height > 0:
        mask = Image.new("L", (width, height), 0)
        ImageDraw.Draw(mask).text((-left, -top), character, font=font, fill=255, anchor="ls")
        payload += _field_bytes(2, _sdf_bitmap(mask))
    payload += _field_varint(3, width)
    payload += _field_varint(4, height)
    payload += _field_varint(5, _signed_zigzag(left))
    payload += _field_varint(6, _signed_zigzag(-top))
    payload += _field_varint(7, advance)
    return bytes(payload)


def generate_range(font_path: Path, output_path: Path, start: int, end: int) -> bytes:
    """Generate and write one official MapLibre Glyphs PBF range."""
    font_bytes = font_path.read_bytes()
    subtables = _font_cmap(font_bytes)
    font = ImageFont.truetype(str(font_path), FONT_SIZE)
    fontstack = bytearray()
    fontstack += _field_bytes(1, FONT_STACK.encode("utf-8"))
    fontstack += _field_bytes(2, f"{start}-{end}".encode("ascii"))
    for codepoint in range(start, end + 1):
        if _has_glyph(font_bytes, subtables, codepoint):
            fontstack += _field_bytes(3, _glyph_message(font, codepoint))
    output = _field_bytes(1, bytes(fontstack))
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_bytes(output)
    return output


def generate(output_dir: Path = DEFAULT_OUTPUT, font_path: Path = FONT_PATH) -> tuple[Path, ...]:
    outputs = []
    for start, end in RANGES:
        destination = output_dir / f"{start}-{end}.pbf"
        generate_range(font_path, destination, start, end)
        outputs.append(destination)
    return tuple(outputs)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT,
                        help="output folder (default: the javity-maplibre glyph resource path)")
    parser.add_argument("--font", type=Path, default=FONT_PATH,
                        help="source TTF (default: bundled Open Sans Regular)")
    args = parser.parse_args()
    for path in generate(args.output_dir, args.font):
        print(path)


if __name__ == "__main__":
    main()
