package org.javity.render.sky;

import static org.junit.Assert.*;

import com.badlogic.gdx.graphics.Color;
import org.javity.render.lighting.RenderEnvironment3D;
import org.junit.Test;

public class SkySettingsTest {
  @Test
  public void copiesColorsInAndOut() {
    Color source = new Color(.2f, .3f, .4f, 1);
    SkySettings settings = SkySettings.gradient(source, Color.WHITE, Color.BLACK);
    source.set(Color.RED);
    Color out = settings.zenith(new Color());
    assertEquals(.2f, out.r, 0);
    out.set(Color.GREEN);
    assertEquals(.3f, settings.zenith(out).g, 0);
    assertEquals(SkySettings.Mode.GRADIENT, settings.mode());
    assertNull(settings.cubemap());
  }

  @Test
  public void rejectsInvalidValuesAndNullSources() {
    for (float bad : new float[] {Float.NaN, Float.POSITIVE_INFINITY, -.1f}) {
      Color c = new Color();
      c.r = bad;
      assertThrows(
          IllegalArgumentException.class, () -> SkySettings.gradient(c, Color.WHITE, Color.BLACK));
    }
    assertThrows(NullPointerException.class, () -> SkySettings.cubemap(null));
    assertThrows(
        NullPointerException.class, () -> SkySettings.gradient(null, Color.WHITE, Color.BLACK));
  }

  @Test
  public void acceptsFiniteUnclampedRgb() {
    Color c = new Color();
    c.r = 3;
    assertEquals(3, SkySettings.gradient(c, c, c).zenith(new Color()).r, 0);
  }

  @Test
  public void sceneSelectsAndClearsImmutableSettings() {
    RenderEnvironment3D scene = new RenderEnvironment3D();
    assertNull(scene.sky());
    SkySettings settings = SkySettings.gradient(Color.BLUE, Color.WHITE, Color.BLACK);
    scene.setSky(settings);
    assertSame(settings, scene.sky());
    assertThrows(NullPointerException.class, () -> scene.setSky(null));
    assertSame(settings, scene.sky());
    scene.clearSky();
    assertNull(scene.sky());
  }
}
