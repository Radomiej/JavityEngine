package org.javity.physics3d;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Package-private, candidate-local native shape owner. */
final class ShapeBuilder {
  final btCompoundShape compound;
  final List<btCollisionShape> children = new ArrayList<>();
  private final Consumer<String> allocationProbe;
  private final boolean direct;
  private final Vector3 worldPosition;
  private btCollisionShape directShape;
  private int childNumber;

  ShapeBuilder(Consumer<String> allocationProbe, boolean direct, Vector3 worldPosition) {
    this.allocationProbe = allocationProbe;
    this.direct = direct;
    this.worldPosition = worldPosition.cpy();
    this.compound = direct ? null : new btCompoundShape();
  }

  btCollisionShape root() {
    return compound == null ? directShape : compound;
  }

  void box(Vector3 dimensions, Vector3 offset, Quaternion rotation) {
    Physics3D.finite(dimensions);
    double radius =
        Math.sqrt(
                (double) dimensions.x * dimensions.x
                    + (double) dimensions.y * dimensions.y
                    + (double) dimensions.z * dimensions.z)
            * .5;
    validateEnvelope(offset, radius);
    add(new btBoxShape(dimensions.cpy().scl(.5f)), offset, rotation);
  }

  void sphere(float radius, Vector3 offset, Quaternion rotation) {
    Physics3D.positive(radius);
    validateEnvelope(offset, radius);
    add(new btSphereShape(radius), offset, rotation);
  }

  void capsule(float radius, float totalHeight, Vector3 offset, Quaternion rotation) {
    Physics3D.positive(radius);
    Physics3D.positive(totalHeight);
    if (totalHeight < 2 * radius)
      throw new IllegalArgumentException("Capsule height must be at least its diameter");
    validateEnvelope(offset, totalHeight * .5d);
    add(new btCapsuleShape(radius, totalHeight - 2 * radius), offset, rotation);
  }

  private void validateEnvelope(Vector3 offset, double radius) {
    Physics3D.finite(offset);
    double limit = Float.MAX_VALUE / 4d;
    if (!Double.isFinite(radius)
        || radius <= 0
        || Math.abs((double) worldPosition.x) + Math.abs((double) offset.x) + radius > limit
        || Math.abs((double) worldPosition.y) + Math.abs((double) offset.y) + radius > limit
        || Math.abs((double) worldPosition.z) + Math.abs((double) offset.z) + radius > limit)
      throw new IllegalArgumentException(
          "Collider pose and extent exceed the supported float range");
  }

  private void add(btCollisionShape shape, Vector3 offset, Quaternion rotation) {
    if (direct) {
      if (directShape != null) {
        shape.dispose();
        throw new IllegalStateException("Direct convex body accepts one primitive");
      }
      directShape = shape;
    } else {
      children.add(shape);
      compound.addChildShape(new Matrix4(offset, rotation, Vector3.One), shape);
    }
    allocationProbe.accept("shape-child-" + (++childNumber));
  }
}
