package org.javity.ui.flex;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.freetype.FreeType;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.utils.IntFloatMap;
import com.badlogic.gdx.utils.IntMap;

/** Keeps FreeType's 26.6 advances without enlarging the glyph atlas. */
final class SubpixelFontData extends FreeTypeFontGenerator.FreeTypeBitmapFontData {
  private final IntFloatMap advances;
  private final IntMap<IntFloatMap> kernings;

  private SubpixelFontData(IntFloatMap advances, IntMap<IntFloatMap> kernings) {
    this.advances = advances;
    this.kernings = kernings;
  }

  static SubpixelFontData read(FileHandle file, int size, String characters) {
    IntFloatMap advances = new IntFloatMap();
    IntMap<IntFloatMap> kernings = new IntMap<>();
    var library = FreeType.initFreeType();
    try {
      var face = library.newFace(file, 0);
      try {
        if (!face.setPixelSizes(0, size)) throw new IllegalArgumentException("Invalid font size");
        int[] codes = characters.chars().distinct().toArray();
        int[] indices = new int[codes.length];
        for (int i = 0; i < codes.length; i++) {
          indices[i] = face.getCharIndex(codes[i]);
          if (indices[i] != 0 && face.loadChar(codes[i], FreeType.FT_LOAD_NO_HINTING))
            advances.put(codes[i], face.getGlyph().getAdvanceX() / 64f);
        }
        if (face.hasKerning())
          for (int i = 0; i < codes.length; i++)
            for (int j = 0; j < codes.length; j++) {
              if (indices[i] == 0 || indices[j] == 0) continue;
              int value = face.getKerning(indices[i], indices[j], FreeType.FT_KERNING_UNFITTED);
              if (value != 0) {
                IntFloatMap row = kernings.get(codes[i]);
                if (row == null) {
                  row = new IntFloatMap();
                  kernings.put(codes[i], row);
                }
                row.put(codes[j], value / 64f);
              }
            }
      } finally {
        face.dispose();
      }
    } finally {
      library.dispose();
    }
    // These private tables are read-only after construction and shared by scaled copies.
    return new SubpixelFontData(advances, kernings);
  }

  SubpixelFontData copyMetrics() {
    return new SubpixelFontData(advances, kernings);
  }

  @Override
  public void getGlyphs(
      GlyphLayout.GlyphRun run, CharSequence text, int start, int end, BitmapFont.Glyph previous) {
    int firstGlyph = run.glyphs.size;
    int firstAdvance = run.xAdvances.size;
    super.getGlyphs(run, text, start, end, previous);
    for (int i = firstGlyph; i < run.glyphs.size; i++) {
      BitmapFont.Glyph current = run.glyphs.get(i);
      // Browser text starts at the pen position, including the first glyph's side bearing.
      float advance = previous == null ? 0 : advance(previous) + kerning(previous, current);
      run.xAdvances.set(firstAdvance + i - firstGlyph, advance * scaleX);
      previous = current;
    }
    if (run.glyphs.size > firstGlyph)
      run.xAdvances.set(run.xAdvances.size - 1, advance(run.glyphs.peek()) * scaleX);
  }

  private float advance(BitmapFont.Glyph glyph) {
    return glyph.fixedWidth ? glyph.xadvance : advances.get(glyph.id, glyph.xadvance);
  }

  private float kerning(BitmapFont.Glyph left, BitmapFont.Glyph right) {
    if (left.fixedWidth) return 0;
    IntFloatMap row = kernings.get(left.id);
    return row == null ? 0 : row.get(right.id, 0);
  }
}
