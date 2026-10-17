package org.javity.physics3d;

import com.badlogic.gdx.math.Vector3;
import java.util.Objects;
import org.javity.core.GameObject;

/** Closest copied ray/sweep result; it contains no borrowed Bullet values. */
public final class PhysicsHit3D {
  private final GameObject object;
  private final Collider3D collider;
  private final Vector3 point, normal;
  private final float fraction, distance;

  PhysicsHit3D(
      GameObject object,
      Collider3D collider,
      Vector3 point,
      Vector3 normal,
      float fraction,
      float distance) {
    this.object = Objects.requireNonNull(object);
    this.collider = collider;
    this.point = point.cpy();
    this.normal = normal.cpy();
    this.fraction = fraction;
    this.distance = distance;
  }

  public GameObject object() {
    return object;
  }

  public int objectId() {
    return object.id;
  }

  public Collider3D collider() {
    return collider;
  }

  public Vector3 point() {
    return point.cpy();
  }

  public Vector3 normal() {
    return normal.cpy();
  }

  public float fraction() {
    return fraction;
  }

  public float distance() {
    return distance;
  }
}
