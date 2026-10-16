package org.javity.render.lighting;

/** Point falloff multiplied by cosine^exponent inside a cone around local -Z. */
public final class SpotLight3D extends PointLight3D {
  private float cutoffDegrees = 30, exponent = 1;

  public float cutoffDegrees() {
    return cutoffDegrees;
  }

  public float exponent() {
    return exponent;
  }

  public void setCutoffDegrees(float value) {
    if (!Float.isFinite(value) || value <= 0 || value >= 90)
      throw new IllegalArgumentException("cutoff must be finite and between 0 and 90 degrees");
    cutoffDegrees = value;
  }

  public void setExponent(float value) {
    nonnegative(value, "exponent");
    exponent = value;
  }
}
