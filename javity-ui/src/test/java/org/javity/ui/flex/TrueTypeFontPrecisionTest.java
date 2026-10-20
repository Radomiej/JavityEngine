package org.javity.ui.flex;

import static org.junit.Assert.*;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import java.lang.reflect.Proxy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TrueTypeFontPrecisionTest {
  private Application previousApp;
  private Graphics previousGraphics;
  private GL20 previousGl, previousGl20;

  @Before
  public void installTextureUploadStub() {
    com.badlogic.gdx.utils.GdxNativesLoader.load();
    previousApp = Gdx.app;
    previousGraphics = Gdx.graphics;
    previousGl = Gdx.gl;
    previousGl20 = Gdx.gl20;
    Gdx.app = stub(Application.class);
    Gdx.graphics = stub(Graphics.class);
    Gdx.gl = Gdx.gl20 = stub(GL20.class);
  }

  @After
  public void restoreGlobals() {
    Gdx.app = previousApp;
    Gdx.graphics = previousGraphics;
    Gdx.gl = previousGl;
    Gdx.gl20 = previousGl20;
  }

  @Test
  public void bundledFontKeepsSubpixelTextWidthsAndCssLineHeights() {
    UiTheme theme = UiTheme.fromTrueType(fontFile(), 16);
    try {
      // FreeType rasterization is real; only GPU upload is stubbed in this metrics test.
      // Canvas advances come from the loaded bundled face in the real Chromium fixture.
      assertWidth(theme, "Navigation", "text-base", 79.87f, 24);
      assertWidth(theme, "Card title", "text-lg", 76.33f, 28);
      assertWidth(theme, "Card body copy.", "text-sm", 105.33f, 20);
      assertWidth(theme, "text-xl 20/28", "text-xl", 116.07f, 28);
      assertWidth(theme, "text-2xl 24/32", "text-2xl", 153.01f, 32);
    } finally {
      theme.dispose();
    }
  }

  @Test
  public void borrowedFractionalFontRetainsBothAxesThroughStyleUpdates() {
    UiTheme owner = UiTheme.fromTrueType(fontFile(), 16);
    var font = owner.getFont();
    font.getData().setScale(.25f, .5f);
    UiTheme borrower = new UiTheme(font, false);
    try {
      var label = borrower.label("Zażółć gęślą", "text-2xl");
      label.setSize(400, 40);
      label.validate();
      float baseWidth = new GlyphLayout(font, label.getText()).width;
      assertEquals(baseWidth * 1.5f, label.getGlyphLayout().width, .001f);
      assertEquals(.375f, label.getFontScaleX(), 0);
      assertEquals(.75f, label.getFontScaleY(), 0);
      borrower.updateLabel(label, "text-base");
      label.validate();
      assertEquals(baseWidth, label.getGlyphLayout().width, .001f);
      assertEquals(.25f, font.getScaleX(), 0);
      assertEquals(.5f, font.getScaleY(), 0);
      String repeated = "NNNNNNNNNNNNNNNN";
      var wrapped = borrower.label(repeated, "text-2xl", true);
      var measured = ((YogaMeasuredActor) wrapped).measureForContentWidth(400);
      assertEquals(new GlyphLayout(font, repeated).width * 1.5f, measured.width(), .001f);
    } finally {
      borrower.dispose();
      owner.dispose();
    }
  }

  @Test
  public void atlasKeepsItsOriginalNativeResolution() {
    UiTheme theme = UiTheme.fromTrueType(fontFile(), 16);
    try {
      assertEquals(1, theme.getFont().getScaleX(), 0);
      long bytes = 0;
      for (var region : theme.getFont().getRegions()) {
        var texture = region.getTexture();
        bytes += (long) texture.getWidth() * texture.getHeight() * 4;
      }
      assertTrue("The default 16px atlas stays within one MiB", bytes <= 1024 * 1024);
    } finally {
      theme.dispose();
    }
  }

  @Test
  public void advancesRetainSideBearingAndDoNotRoundEachCharacter() {
    UiTheme theme = UiTheme.fromTrueType(fontFile(), 16);
    try {
      var run = new GlyphLayout.GlyphRun();
      theme.getFont().getData().getGlyphs(run, "NNNNNNNN", 0, 8, null);
      assertEquals(
          "Keep the pen origin instead of stripping the first side bearing",
          0,
          run.xAdvances.first(),
          0);
      // Open Sans hmtx: 1544 units / 2048 units per em * 16px = 12.0625px.
      for (int i = 1; i < run.xAdvances.size; i++)
        assertEquals(12.0625f, run.xAdvances.get(i), 1f / 64);
    } finally {
      theme.dispose();
    }
  }

  @Test
  public void wrappedStyleRefreshesAfterBorrowedFontScaleChanges() {
    UiTheme owner = UiTheme.fromTrueType(fontFile(), 16);
    var font = owner.getFont();
    UiTheme borrower = new UiTheme(font, false);
    try {
      var wrapped = borrower.label("NNNNNNNNNNNNNNNN", "text-2xl", true);
      float originalWidth = ((YogaMeasuredActor) wrapped).measureForContentWidth(800).width();
      font.getData().setScale(1.5f, .75f);
      borrower.updateLabel(wrapped, "text-2xl", true);
      var size = ((YogaMeasuredActor) wrapped).measureForContentWidth(800);
      assertEquals(originalWidth * 1.5f, size.width(), .001f);
      assertEquals(font.getLineHeight() * 1.5f, wrapped.getStyle().font.getLineHeight(), .001f);
      assertEquals(1.5f, font.getScaleX(), 0);
      assertEquals(.75f, font.getScaleY(), 0);
    } finally {
      borrower.dispose();
      owner.dispose();
    }
  }

  @Test
  public void changingBorrowedSourceScaleKeepsOneCachedCopyPerStyleAndOlderLabelsValid()
      throws Exception {
    UiTheme owner = UiTheme.fromTrueType(fontFile(), 16);
    var font = owner.getFont();
    UiTheme borrower = new UiTheme(font, false);
    try {
      String text = "NNNNNNNN";
      var oldLarge = borrower.label(text, "text-2xl", true);
      var oldMedium = borrower.label(text, "text-xl", true);
      var oldLargeFont = oldLarge.getStyle().font;
      var oldMediumFont = oldMedium.getStyle().font;
      float oldLargeWidth = ((YogaMeasuredActor) oldLarge).measureForContentWidth(800).width();
      float oldMediumWidth = ((YogaMeasuredActor) oldMedium).measureForContentWidth(800).width();
      var currentLarge = borrower.label(text, "text-2xl", true);
      var currentMedium = borrower.label(text, "text-xl", true);
      for (int i = 1; i <= 100; i++) {
        font.getData().setScale(1 + i * .01f, 1 - i * .004f);
        borrower.updateLabel(currentLarge, "text-2xl", true);
        borrower.updateLabel(currentMedium, "text-xl", true);
      }
      var field = UiTheme.class.getDeclaredField("scaledBorrowedFonts");
      field.setAccessible(true);
      var cache = (java.util.Map<?, ?>) field.get(borrower);
      assertEquals(
          "Cache cardinality follows style count, not source-scale history", 2, cache.size());
      assertSame(oldLargeFont, oldLarge.getStyle().font);
      assertSame(oldMediumFont, oldMedium.getStyle().font);
      assertFalse(oldLargeFont.ownsTexture());
      assertFalse(oldMediumFont.ownsTexture());
      assertEquals(
          oldLargeWidth, ((YogaMeasuredActor) oldLarge).measureForContentWidth(800).width(), .001f);
      assertEquals(
          oldMediumWidth,
          ((YogaMeasuredActor) oldMedium).measureForContentWidth(800).width(),
          .001f);
      assertEquals(
          oldLargeWidth * 2,
          ((YogaMeasuredActor) currentLarge).measureForContentWidth(800).width(),
          .001f);
      assertEquals(
          oldMediumWidth * 2,
          ((YogaMeasuredActor) currentMedium).measureForContentWidth(800).width(),
          .001f);
    } finally {
      borrower.dispose();
      owner.dispose();
    }
  }

  private static void assertWidth(
      UiTheme theme, String text, String classes, float canvasAdvance, float lineHeight) {
    var label = theme.label(text, classes);
    var font = label.getStyle().font;
    assertEquals(text + " width", canvasAdvance, new GlyphLayout(font, text).width, 2.5f);
    assertEquals(lineHeight, font.getLineHeight(), .001f);
    label.setSize(400, lineHeight);
    label.validate();
    float before = label.getGlyphLayout().width;
    theme.updateLabel(label, classes);
    label.validate();
    assertEquals(
        "Updating a style preserves its raster scale", before, label.getGlyphLayout().width, .001f);
  }

  private static FileHandle fontFile() {
    return new FileHandle("src/main/resources/org/javity/ui/fonts/OpenSans-Regular.ttf");
  }

  private static <T> T stub(Class<T> type) {
    return type.cast(
        Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            (proxy, method, args) -> {
              if (method.getName().equals("getType")) return Application.ApplicationType.Desktop;
              Class<?> result = method.getReturnType();
              if (result == boolean.class) return false;
              if (result == int.class) return 0;
              if (result == float.class) return 0f;
              if (result == long.class) return 0L;
              if (result == double.class) return 0d;
              return null;
            }));
  }
}
