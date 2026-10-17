package org.javity.particles;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import java.util.Objects;

/** Immutable description of one deterministic world-space burst. Distances use world units. */
public final class ParticleBurstSpec3D {
  /** Conservative component-wise world-space limit, including the billboard half extent. */
  static final double MAX_ABSOLUTE_WORLD_COORDINATE = (double) Float.MAX_VALUE / 4.0;

  private final Vector3 origin;
  private final Vector3 minVelocity;
  private final Vector3 maxVelocity;
  private final Vector3 acceleration;
  private final Color color;
  private final float lifetimeSeconds;
  private final float size;
  private final int count;
  private final long seed;

  public ParticleBurstSpec3D(
      Vector3 origin,
      Vector3 minVelocity,
      Vector3 maxVelocity,
      Vector3 acceleration,
      float lifetimeSeconds,
      float size,
      Color color,
      int count,
      long seed) {
    this.origin = finiteCopy(origin, "origin");
    this.minVelocity = finiteCopy(minVelocity, "minVelocity");
    this.maxVelocity = finiteCopy(maxVelocity, "maxVelocity");
    this.acceleration = finiteCopy(acceleration, "acceleration");
    if (minVelocity.x > maxVelocity.x
        || minVelocity.y > maxVelocity.y
        || minVelocity.z > maxVelocity.z)
      throw new IllegalArgumentException("Minimum velocity must not exceed maximum velocity");
    if (!Float.isFinite(lifetimeSeconds) || lifetimeSeconds <= 0)
      throw new IllegalArgumentException("Lifetime must be finite and positive");
    if (!Float.isFinite(size) || size <= 0)
      throw new IllegalArgumentException("Size must be finite and positive");
    if (count < 1) throw new IllegalArgumentException("Burst count must be positive");
    this.color = new Color(Objects.requireNonNull(color, "color"));
    if (!finite(this.color.r)
        || !finite(this.color.g)
        || !finite(this.color.b)
        || !finite(this.color.a)
        || this.color.r < 0
        || this.color.r > 1
        || this.color.g < 0
        || this.color.g > 1
        || this.color.b < 0
        || this.color.b > 1
        || this.color.a < 0
        || this.color.a > 1)
      throw new IllegalArgumentException("Color channels must be finite and in [0,1]");
    this.lifetimeSeconds = lifetimeSeconds;
    this.size = size;
    this.count = count;
    this.seed = seed;
    validateWorldEnvelope(
        this.origin, this.minVelocity, this.maxVelocity, this.acceleration, lifetimeSeconds, size);
  }

  public Vector3 origin() {
    return new Vector3(origin);
  }

  public Vector3 minVelocity() {
    return new Vector3(minVelocity);
  }

  public Vector3 maxVelocity() {
    return new Vector3(maxVelocity);
  }

  public Vector3 acceleration() {
    return new Vector3(acceleration);
  }

  public Color color() {
    return new Color(color);
  }

  public float lifetimeSeconds() {
    return lifetimeSeconds;
  }

  public float size() {
    return size;
  }

  public int count() {
    return count;
  }

  public long seed() {
    return seed;
  }

  private static Vector3 finiteCopy(Vector3 value, String name) {
    Objects.requireNonNull(value, name);
    if (!finite(value.x) || !finite(value.y) || !finite(value.z))
      throw new IllegalArgumentException(name + " must be finite");
    return new Vector3(value);
  }

  private static boolean finite(float value) {
    return Float.isFinite(value);
  }

  private static void validateWorldEnvelope(
      Vector3 origin,
      Vector3 minVelocity,
      Vector3 maxVelocity,
      Vector3 acceleration,
      float lifetimeSeconds,
      float size) {
    double life = lifetimeSeconds;
    double billboardExtent = size;
    for (int axis = 0; axis < 3; axis++) {
      double minimum = component(minVelocity, axis);
      double maximum = component(maxVelocity, axis);
      double speed = Math.max(Math.abs(minimum), Math.abs(maximum));
      double accelerationMagnitude = Math.abs(component(acceleration, axis));
      double maximumSpeed = speed + accelerationMagnitude * life;
      double maximumTravel = speed * life + 0.5 * accelerationMagnitude * life * life;
      double maximumCoordinate =
          Math.abs(component(origin, axis)) + maximumTravel + billboardExtent;
      if (!Double.isFinite(maximumSpeed)
          || maximumSpeed > Float.MAX_VALUE
          || !Double.isFinite(maximumCoordinate)
          || maximumCoordinate > MAX_ABSOLUTE_WORLD_COORDINATE)
        throw new IllegalArgumentException(
            "Particle trajectory and billboard must stay within the conservative world-space "
                + "limit (Float.MAX_VALUE / 4)");
    }
  }

  private static float component(Vector3 vector, int axis) {
    return switch (axis) {
      case 0 -> vector.x;
      case 1 -> vector.y;
      default -> vector.z;
    };
  }
}
