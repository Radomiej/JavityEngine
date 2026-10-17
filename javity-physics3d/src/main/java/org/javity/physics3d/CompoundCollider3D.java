package org.javity.physics3d;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import java.util.List;
import java.util.Objects;

/** Immutable bounded primitive-child compound description. */
public final class CompoundCollider3D extends Collider3D {
  public sealed interface Child permits Box, Sphere, Capsule {
    Vector3 offset();

    Quaternion rotation();
  }

  public record Box(Vector3 dimensions, Vector3 offset, Quaternion rotation) implements Child {
    public Box {
      dimensions = positiveCopy(dimensions);
      offset = finiteCopy(offset);
      rotation = rotationCopy(rotation);
    }

    @Override
    public Vector3 dimensions() {
      return dimensions.cpy();
    }

    @Override
    public Vector3 offset() {
      return offset.cpy();
    }

    @Override
    public Quaternion rotation() {
      return new Quaternion(rotation);
    }
  }

  public record Sphere(float radius, Vector3 offset, Quaternion rotation) implements Child {
    public Sphere {
      Physics3D.positive(radius);
      offset = finiteCopy(offset);
      rotation = rotationCopy(rotation);
    }

    @Override
    public Vector3 offset() {
      return offset.cpy();
    }

    @Override
    public Quaternion rotation() {
      return new Quaternion(rotation);
    }
  }

  public record Capsule(float radius, float totalHeight, Vector3 offset, Quaternion rotation)
      implements Child {
    public Capsule {
      Physics3D.positive(radius);
      Physics3D.positive(totalHeight);
      if (totalHeight < 2 * radius)
        throw new IllegalArgumentException("Capsule height must be at least its diameter");
      offset = finiteCopy(offset);
      rotation = rotationCopy(rotation);
    }

    @Override
    public Vector3 offset() {
      return offset.cpy();
    }

    @Override
    public Quaternion rotation() {
      return new Quaternion(rotation);
    }
  }

  private final List<Child> children;

  public CompoundCollider3D(List<? extends Child> children) {
    Objects.requireNonNull(children);
    if (children.isEmpty() || children.size() > 64)
      throw new IllegalArgumentException("Compound requires 1..64 children");
    this.children = List.copyOf(children);
  }

  public List<Child> getChildren() {
    return children;
  }

  @Override
  void addTo(ShapeBuilder b, Vector3 offset, Quaternion rotation) {
    Matrix4 parentRotation = new Matrix4().set(rotation);
    for (Child c : children) {
      Vector3 childOffset = c.offset();
      float[] m = parentRotation.val;
      double x =
          (double) m[Matrix4.M00] * childOffset.x
              + (double) m[Matrix4.M01] * childOffset.y
              + (double) m[Matrix4.M02] * childOffset.z
              + offset.x;
      double y =
          (double) m[Matrix4.M10] * childOffset.x
              + (double) m[Matrix4.M11] * childOffset.y
              + (double) m[Matrix4.M12] * childOffset.z
              + offset.y;
      double z =
          (double) m[Matrix4.M20] * childOffset.x
              + (double) m[Matrix4.M21] * childOffset.y
              + (double) m[Matrix4.M22] * childOffset.z
              + offset.z;
      double limit = Float.MAX_VALUE / 4d;
      if (!Double.isFinite(x)
          || !Double.isFinite(y)
          || !Double.isFinite(z)
          || Math.abs(x) > limit
          || Math.abs(y) > limit
          || Math.abs(z) > limit)
        throw new IllegalArgumentException(
            "Composed collider offset exceeds the supported float range");
      Vector3 o = new Vector3((float) x, (float) y, (float) z);
      Quaternion q = new Quaternion(rotation).mul(c.rotation()).nor();
      switch (c) {
        case Box box -> b.box(box.dimensions(), o, q);
        case Sphere sphere -> b.sphere(sphere.radius(), o, q);
        case Capsule capsule -> b.capsule(capsule.radius(), capsule.totalHeight(), o, q);
      }
    }
  }

  @Override
  String descriptor() {
    return "compound:" + children;
  }

  private static Vector3 positiveCopy(Vector3 v) {
    Objects.requireNonNull(v);
    Physics3D.positive(v.x);
    Physics3D.positive(v.y);
    Physics3D.positive(v.z);
    return v.cpy();
  }

  private static Vector3 finiteCopy(Vector3 v) {
    Objects.requireNonNull(v);
    Physics3D.finite(v);
    return v.cpy();
  }

  private static Quaternion rotationCopy(Quaternion q) {
    Objects.requireNonNull(q);
    return Physics3D.normalizedCopy(q);
  }
}
