package org.javity.render.sky;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Cubemap;
import java.util.Objects;

/** Immutable scene background. Colors are copied; the cubemap is borrowed and must outlive use. */
public final class SkySettings {
  public enum Mode {
    GRADIENT,
    CUBEMAP
  }

  private final Cubemap cubemap;
  private final Color zenith, horizon, nadir;

  private SkySettings(Cubemap cubemap, Color zenith, Color horizon, Color nadir) {
    this.cubemap = cubemap;
    this.zenith = zenith;
    this.horizon = horizon;
    this.nadir = nadir;
  }

  public static SkySettings cubemap(Cubemap borrowed) {
    return new SkySettings(Objects.requireNonNull(borrowed, "borrowed"), null, null, null);
  }

  public static SkySettings gradient(Color zenith, Color horizon, Color nadir) {
    return new SkySettings(null, copy(zenith), copy(horizon), copy(nadir));
  }

  public Mode mode() {
    return cubemap == null ? Mode.GRADIENT : Mode.CUBEMAP;
  }

  public Cubemap cubemap() {
    return cubemap;
  }

  public Color zenith(Color out) {
    return gradientColor(out, zenith);
  }

  public Color horizon(Color out) {
    return gradientColor(out, horizon);
  }

  public Color nadir(Color out) {
    return gradientColor(out, nadir);
  }

  private Color gradientColor(Color out, Color value) {
    if (value == null) throw new IllegalStateException("Cubemap has no gradient colors");
    return Objects.requireNonNull(out, "out").set(value);
  }

  private static Color copy(Color value) {
    Objects.requireNonNull(value, "color");
    if (!Float.isFinite(value.r)
        || !Float.isFinite(value.g)
        || !Float.isFinite(value.b)
        || value.r < 0
        || value.g < 0
        || value.b < 0)
      throw new IllegalArgumentException("Sky RGB must be finite and nonnegative");
    // RGB is intentionally unclamped, matching scene lighting. Background output is opaque.
    Color copy = new Color(value);
    copy.a = 1;
    return copy;
  }
}
