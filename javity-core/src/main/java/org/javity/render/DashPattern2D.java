package org.javity.render;

import java.util.Arrays;

/** Immutable alternating ON/OFF lengths for an open {@link ShapeRenderer2D} polyline. */
public final class DashPattern2D {
  private final float[] lengths;
  private final double period;

  /** Creates a pattern of 2, 4, 6, or 8 positive finite local path lengths. */
  public DashPattern2D(float... lengths) {
    if (lengths == null || lengths.length < 2 || lengths.length > 8 || (lengths.length & 1) != 0)
      throw new IllegalArgumentException("Dash pattern requires 2, 4, 6, or 8 lengths");
    this.lengths = lengths.clone();
    double total = 0;
    for (float length : this.lengths) {
      if (!Float.isFinite(length) || length <= 0)
        throw new IllegalArgumentException("Dash lengths must be finite and positive");
      total += (double) length;
      if (!Double.isFinite(total) || !Float.isFinite((float) total))
        throw new IllegalArgumentException("Dash period must be representable as a float");
    }
    if (total <= 0) throw new IllegalArgumentException("Dash period must be positive");
    period = total;
  }

  public int size() {
    return lengths.length;
  }

  public float length(int index) {
    return lengths[index];
  }

  public float[] lengths() {
    return lengths.clone();
  }

  /** Pattern period, summed in double precision. */
  public double period() {
    return period;
  }

  /** Period packed for the renderer's GLSL float uniforms. */
  float periodFloat() {
    return (float) period;
  }

  double periodDouble() {
    return period;
  }

  /** Returns the alternating ink interval containing the supplied normalized cycle position. */
  boolean contains(double position) {
    double cursor = 0;
    for (int i = 0; i < lengths.length; i++) {
      double end = cursor + lengths[i];
      if (position >= cursor && position < end) return (i & 1) == 0;
      cursor = end;
    }
    return false;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof DashPattern2D pattern && Arrays.equals(lengths, pattern.lengths);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(lengths);
  }
}
