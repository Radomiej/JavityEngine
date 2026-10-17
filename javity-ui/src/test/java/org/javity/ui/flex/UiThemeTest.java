package org.javity.ui.flex;

import static org.junit.Assert.*;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import org.junit.Test;

public class UiThemeTest {
  @Test
  public void scaledBorrowedFontKeepsUnicodePagesKerningAndOriginalScale() {
    BitmapFont.BitmapFontData data = new BitmapFont.BitmapFontData();
    data.glyphs[0] = new BitmapFont.Glyph[512];
    data.glyphs[1] = null; // Some BMFont files have unallocated pages.
    data.glyphs[39] = new BitmapFont.Glyph[512];
    data.lineHeight = 20;
    data.capHeight = 12;
    data.ascent = 9;
    data.descent = -3;
    data.down = -20;
    data.spaceXadvance = 4;
    data.setScale(1.25f);

    BitmapFont.Glyph question = glyph('?', 7);
    BitmapFont.Glyph latin = glyph('A', 8);
    BitmapFont.Glyph polish = glyph('Ż', 9);
    BitmapFont.Glyph distantUnicode = glyph('\u4e00', 10);
    latin.setKerning('Ż', -2);
    data.setGlyph('?', question);
    data.setGlyph('A', latin);
    data.setGlyph('Ż', polish);
    data.setGlyph('\u4e00', distantUnicode);
    data.missingGlyph = question;
    float sourceScale = data.scaleX;
    float sourceLineHeight = data.lineHeight;
    int sourceAdvance = data.getGlyph('Ż').xadvance;
    int sourceKerning = data.getGlyph('A').getKerning('Ż');
    BitmapFont.BitmapFontData scaled = UiTheme.copyScaledFontData(data, 2f);

    assertNotSame(data, scaled);
    assertEquals('Ż', scaled.getGlyph('Ż').id);
    assertEquals('\u4e00', scaled.getGlyph('\u4e00').id);
    assertEquals(-2, scaled.getGlyph('A').getKerning('Ż'));
    assertNotNull(scaled.getGlyph('?'));
    assertEquals(2.5f, scaled.scaleX, .0001f);
    assertEquals(sourceLineHeight * 2, scaled.lineHeight, .001f);
    assertEquals(sourceAdvance, scaled.getGlyph('Ż').xadvance);
    assertEquals(sourceKerning, scaled.getGlyph('A').getKerning('Ż'));
    assertEquals(sourceScale, data.scaleX, 0);
    assertEquals(sourceLineHeight, data.lineHeight, 0);
    assertEquals(sourceAdvance, data.getGlyph('Ż').xadvance);
    assertEquals(sourceKerning, data.getGlyph('A').getKerning('Ż'));
  }

  private static BitmapFont.Glyph glyph(char id, int advance) {
    BitmapFont.Glyph glyph = new BitmapFont.Glyph();
    glyph.id = id;
    glyph.width = advance - 1;
    glyph.height = 10;
    glyph.xadvance = advance;
    glyph.kerning = new byte[128][];
    return glyph;
  }
}
