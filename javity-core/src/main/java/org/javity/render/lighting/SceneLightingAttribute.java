package org.javity.render.lighting;

import com.badlogic.gdx.graphics.g3d.Attribute;
import java.util.Arrays;

/**
 * Scene shader marker and copied finite-range/fog parameters. Forces ordinary instance fallback.
 */
public final class SceneLightingAttribute extends Attribute {
  public static final long Type = register("javitySceneLighting");
  private final float[] pointRanges, spotRanges;
  private final float near, far;

  SceneLightingAttribute(float[] points, float[] spots, float near, float far) {
    super(Type);
    pointRanges = points.clone();
    spotRanges = spots.clone();
    this.near = near;
    this.far = far;
  }

  public float pointRange(int index) {
    return index < pointRanges.length ? pointRanges[index] : 1;
  }

  public float spotRange(int index) {
    return index < spotRanges.length ? spotRanges[index] : 1;
  }

  public float fogNear() {
    return near;
  }

  public float fogFar() {
    return far;
  }

  @Override
  public Attribute copy() {
    return new SceneLightingAttribute(pointRanges, spotRanges, near, far);
  }

  @Override
  public int compareTo(Attribute other) {
    if (type != other.type) return Long.compare(type, other.type);
    SceneLightingAttribute value = (SceneLightingAttribute) other;
    int result = Arrays.compare(pointRanges, value.pointRanges);
    if (result == 0) result = Arrays.compare(spotRanges, value.spotRanges);
    if (result == 0) result = Float.compare(near, value.near);
    return result == 0 ? Float.compare(far, value.far) : result;
  }
}
