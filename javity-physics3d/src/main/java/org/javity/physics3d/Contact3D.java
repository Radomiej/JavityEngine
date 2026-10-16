package org.javity.physics3d;

import com.badlogic.gdx.math.Vector3;
import java.util.Objects;
import org.javity.core.GameObject;

/** Immutable copied body-level contact snapshot. The normal points from B toward A. */
public final class Contact3D {
  public enum Phase {
    ENTER,
    STAY,
    EXIT
  }

  private final GameObject objectA, objectB;
  private final Collider3D colliderA, colliderB;
  private final Phase phase;
  private final long fixedTick;
  private final long bodyLifetimeA, bodyLifetimeB;
  private final boolean trigger;
  private final Vector3 pointA, pointB, normalOnB;

  Contact3D(
      GameObject a,
      GameObject b,
      Collider3D ca,
      Collider3D cb,
      Phase phase,
      long tick,
      boolean trigger,
      long bodyLifetimeA,
      long bodyLifetimeB,
      Vector3 pointA,
      Vector3 pointB,
      Vector3 normalOnB) {
    objectA = Objects.requireNonNull(a);
    objectB = Objects.requireNonNull(b);
    colliderA = ca;
    colliderB = cb;
    this.phase = phase;
    fixedTick = tick;
    this.trigger = trigger;
    this.bodyLifetimeA = bodyLifetimeA;
    this.bodyLifetimeB = bodyLifetimeB;
    this.pointA = pointA.cpy();
    this.pointB = pointB.cpy();
    this.normalOnB = normalOnB.cpy();
  }

  public GameObject objectA() {
    return objectA;
  }

  public GameObject objectB() {
    return objectB;
  }

  public int objectAId() {
    return objectA.id;
  }

  public int objectBId() {
    return objectB.id;
  }

  public Collider3D colliderA() {
    return colliderA;
  }

  public Collider3D colliderB() {
    return colliderB;
  }

  public Phase phase() {
    return phase;
  }

  public long fixedTick() {
    return fixedTick;
  }

  public long bodyLifetimeA() {
    return bodyLifetimeA;
  }

  public long bodyLifetimeB() {
    return bodyLifetimeB;
  }

  public boolean isTrigger() {
    return trigger;
  }

  public Vector3 pointA() {
    return pointA.cpy();
  }

  public Vector3 pointB() {
    return pointB.cpy();
  }

  public Vector3 normalOnB() {
    return normalOnB.cpy();
  }

  Contact3D reversed() {
    return new Contact3D(
        objectB,
        objectA,
        colliderB,
        colliderA,
        phase,
        fixedTick,
        trigger,
        bodyLifetimeB,
        bodyLifetimeA,
        pointB,
        pointA,
        normalOnB.cpy().scl(-1));
  }

  Contact3D withPhase(Phase p, long tick) {
    return new Contact3D(
        objectA,
        objectB,
        colliderA,
        colliderB,
        p,
        tick,
        trigger,
        bodyLifetimeA,
        bodyLifetimeB,
        pointA,
        pointB,
        normalOnB);
  }
}
