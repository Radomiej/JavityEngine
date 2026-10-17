package org.javity.physics3d;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;

/** Y-up capsule. Height is total end-to-end height, including both hemispheres. */
public final class CapsuleCollider3D extends Collider3D {
  private final float radius, totalHeight;

  public CapsuleCollider3D(float radius, float totalHeight) {
    Physics3D.positive(radius);
    Physics3D.positive(totalHeight);
    if (totalHeight < 2 * radius)
      throw new IllegalArgumentException("Capsule height must be at least its diameter");
    this.radius = radius;
    this.totalHeight = totalHeight;
  }

  public float getRadius() {
    return radius;
  }

  public float getTotalHeight() {
    return totalHeight;
  }

  @Override
  void addTo(ShapeBuilder b, Vector3 o, Quaternion q) {
    b.capsule(radius, totalHeight, o, q);
  }

  @Override
  String descriptor() {
    return "capsule:" + radius + ':' + totalHeight;
  }
}
