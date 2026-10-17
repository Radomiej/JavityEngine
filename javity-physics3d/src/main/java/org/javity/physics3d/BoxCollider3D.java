package org.javity.physics3d;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;

/** Box dimensions are full edge lengths, in world units. */
public final class BoxCollider3D extends Collider3D {
  private final Vector3 dimensions;

  public BoxCollider3D(float width, float height, float depth) {
    Physics3D.positive(width);
    Physics3D.positive(height);
    Physics3D.positive(depth);
    dimensions = new Vector3(width, height, depth);
  }

  public Vector3 getDimensions() {
    return dimensions.cpy();
  }

  @Override
  void addTo(ShapeBuilder b, Vector3 o, Quaternion q) {
    b.box(dimensions, o, q);
  }

  @Override
  String descriptor() {
    return "box:" + dimensions;
  }
}
