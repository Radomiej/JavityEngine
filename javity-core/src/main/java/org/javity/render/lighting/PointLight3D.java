package org.javity.render.lighting;

/** Finite light with squared linear falloff: max(0, 1 - distance/range)^2. */
public class PointLight3D extends Light3D {
  private float range = 10;

  public final float range() {
    return range;
  }

  public final void setRange(float value) {
    if (!Float.isFinite(value) || value <= 0)
      throw new IllegalArgumentException("range must be finite and positive");
    range = value;
  }
}
