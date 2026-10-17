package org.javity.physics3d;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;

/** Sphere radius in world units. */
public final class SphereCollider3D extends Collider3D {
  private final float radius;

  public SphereCollider3D(float radius) {
    Physics3D.positive(radius);
    this.radius = radius;
  }

  public float getRadius() {
    return radius;
  }

  @Override
  void addTo(ShapeBuilder b, Vector3 o, Quaternion q) {
    b.sphere(radius, o, q);
  }

  @Override
  String descriptor() {
    return "sphere:" + radius;
  }
}
