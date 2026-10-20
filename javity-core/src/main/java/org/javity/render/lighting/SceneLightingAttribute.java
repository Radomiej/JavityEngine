package org.javity.render.lighting;

import com.badlogic.gdx.graphics.g3d.Attribute;
import java.util.Arrays;

/**
 * Scene shader marker and copied finite-range/fog parameters. Forces ordinary instance fallback.
 */
public final class SceneLightingAttribute extends Attribute {
  public static final long Type = register("javitySceneLighting");
  private float[] pointRanges, spotRanges;
  private int pointCount, spotCount;
  private float near, far;

  SceneLightingAttribute() {
    this(new float[0], new float[0], 0, 1);
  }

  SceneLightingAttribute(float[] points, float[] spots, float near, float far) {
    super(Type);
    pointRanges = points.clone();
    spotRanges = spots.clone();
    finish(points.length, spots.length, near, far);
  }

  void prepare(int points, int spots) {
    if (pointRanges.length < points) pointRanges = Arrays.copyOf(pointRanges, points);
    if (spotRanges.length < spots) spotRanges = Arrays.copyOf(spotRanges, spots);
  }

  void setPointRange(int index, float range) {
    pointRanges[index] = range;
  }

  void setSpotRange(int index, float range) {
    spotRanges[index] = range;
  }

  void finish(int points, int spots, float near, float far) {
    pointCount = points;
    spotCount = spots;
    this.near = near;
    this.far = far;
  }

  public float pointRange(int index) {
    return index < pointCount ? pointRanges[index] : 1;
  }

  public float spotRange(int index) {
    return index < spotCount ? spotRanges[index] : 1;
  }

  public float fogNear() {
    return near;
  }

  public float fogFar() {
    return far;
  }

  // TeaVM 0.15 lacks Arrays.compare(float[], float[]). Retain the JDK's
  // lexicographic Float.compare ordering, including signed zero and NaN.
  private static int compareRanges(float[] a, int aCount, float[] b, int bCount) {
    for (int i = 0, n = Math.min(aCount, bCount); i < n; i++) {
      int result = Float.compare(a[i], b[i]);
      if (result != 0) return result;
    }
    return Integer.compare(aCount, bCount);
  }

  @Override
  public Attribute copy() {
    SceneLightingAttribute copy = new SceneLightingAttribute();
    copy.pointRanges = Arrays.copyOf(pointRanges, pointCount);
    copy.spotRanges = Arrays.copyOf(spotRanges, spotCount);
    copy.finish(pointCount, spotCount, near, far);
    return copy;
  }

  // Attribute.equals(Object) checks type and dispatches here. Its default implementation
  // compares only hashes, which cannot represent equality of the copied lighting values.
  @Override
  protected boolean equals(Attribute other) {
    return other instanceof SceneLightingAttribute && compareTo(other) == 0;
  }

  @Override
  public int hashCode() {
    int result = 31 * super.hashCode() + pointCount;
    for (int i = 0; i < pointCount; i++)
      result = 31 * result + Float.floatToIntBits(pointRanges[i]);
    result = 31 * result + spotCount;
    for (int i = 0; i < spotCount; i++) result = 31 * result + Float.floatToIntBits(spotRanges[i]);
    result = 31 * result + Float.floatToIntBits(near);
    return 31 * result + Float.floatToIntBits(far);
  }

  @Override
  public int compareTo(Attribute other) {
    if (type != other.type) return Long.compare(type, other.type);
    SceneLightingAttribute value = (SceneLightingAttribute) other;
    int result = compareRanges(pointRanges, pointCount, value.pointRanges, value.pointCount);
    if (result == 0)
      result = compareRanges(spotRanges, spotCount, value.spotRanges, value.spotCount);
    if (result == 0) result = Float.compare(near, value.near);
    return result == 0 ? Float.compare(far, value.far) : result;
  }
}
