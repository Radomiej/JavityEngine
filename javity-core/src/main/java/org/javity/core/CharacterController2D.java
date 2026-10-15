package org.javity.core;

import com.badlogic.gdx.math.Vector2;

/**
 * Moves its owner's transform from a normalized velocity command during WORLD updates. This is
 * transform movement only; it does not perform collision detection or response.
 */
public class CharacterController2D extends Component {
  public final Vector2 velocity = new Vector2();
  public boolean constrainToBounds = true;

  private float speed = 120;
  private boolean hasWorldBounds;
  private float boundsX, boundsY, boundsWidth, boundsHeight;
  private float halfWidth, halfHeight;

  /** Set the configured speed used by {@link #move(float, float)} in world units per second. */
  public CharacterController2D setSpeed(float speed) {
    requireFinite(speed, "speed");
    if (speed < 0) throw new IllegalArgumentException("Speed must be non-negative");
    this.speed = speed;
    return this;
  }

  public float getSpeed() {
    return speed;
  }

  /** Set normalized movement intent using the configured speed. */
  public void move(float x, float y) {
    setVelocity(x, y, speed);
  }

  /** Set normalized movement intent using a per-command speed in world units per second. */
  public void move(float x, float y, float speed) {
    requireFinite(speed, "speed");
    if (speed < 0) throw new IllegalArgumentException("Speed must be non-negative");
    setVelocity(x, y, speed);
  }

  /** Set movement intent without changing the supplied vector. */
  public void move(Vector2 direction) {
    if (direction == null) throw new IllegalArgumentException("Direction must not be null");
    move(direction.x, direction.y);
  }

  /** Set movement intent without changing the supplied vector. */
  public void move(Vector2 direction, float speed) {
    if (direction == null) throw new IllegalArgumentException("Direction must not be null");
    move(direction.x, direction.y, speed);
  }

  /** Constrain the owner's center to this world-space rectangle when enabled. */
  public CharacterController2D setWorldBounds(float x, float y, float width, float height) {
    requireFinite(x, "bounds x");
    requireFinite(y, "bounds y");
    requireFinite(width, "bounds width");
    requireFinite(height, "bounds height");
    if (width <= 0 || height <= 0)
      throw new IllegalArgumentException("World bounds dimensions must be positive");
    requireFinite(x + width, "bounds right");
    requireFinite(y + height, "bounds top");
    boundsX = x;
    boundsY = y;
    boundsWidth = width;
    boundsHeight = height;
    hasWorldBounds = true;
    return this;
  }

  public CharacterController2D clearWorldBounds() {
    hasWorldBounds = false;
    return this;
  }

  /** Half-size of the controlled object's footprint used when constraining bounds. */
  public CharacterController2D setHalfExtents(float halfWidth, float halfHeight) {
    requireFinite(halfWidth, "half width");
    requireFinite(halfHeight, "half height");
    if (halfWidth < 0 || halfHeight < 0)
      throw new IllegalArgumentException("Half extents must be non-negative");
    this.halfWidth = halfWidth;
    this.halfHeight = halfHeight;
    return this;
  }

  /** Called after transform movement for this WORLD update. No collision phase is implied. */
  public void onAfterMove(float delta) {}

  @Override
  public void onUpdate(float delta) {
    requireFinite(delta, "delta");
    if (delta < 0) throw new IllegalArgumentException("Delta must be non-negative");

    if (delta > 0 && (velocity.x != 0 || velocity.y != 0)) {
      Transform2D transform = getTransform();
      float x = finiteFloat(transform.position.x + (double) velocity.x * delta, "x position");
      float y = finiteFloat(transform.position.y + (double) velocity.y * delta, "y position");
      if (constrainToBounds && hasWorldBounds) {
        x =
            clamp(
                x,
                boundsX + (double) halfWidth,
                boundsX + (double) boundsWidth - halfWidth,
                boundsX + (double) boundsWidth * .5);
        y =
            clamp(
                y,
                boundsY + (double) halfHeight,
                boundsY + (double) boundsHeight - halfHeight,
                boundsY + (double) boundsHeight * .5);
      }
      transform.position.set(x, y);
    }
    onAfterMove(delta);
  }

  private void setVelocity(float x, float y, float speed) {
    requireFinite(x, "direction x");
    requireFinite(y, "direction y");
    double length = Math.hypot((double) x, (double) y);
    if (length == 0 || speed == 0) {
      velocity.setZero();
      return;
    }
    velocity.set(
        finiteFloat(x / length * speed, "velocity x"),
        finiteFloat(y / length * speed, "velocity y"));
  }

  private static float clamp(float value, double min, double max, double center) {
    // An object wider than the bounds stays centered on that axis.
    if (min > max) return finiteFloat(center, "bounded position");
    return finiteFloat(Math.max(min, Math.min(max, value)), "bounded position");
  }

  private static float finiteFloat(double value, String label) {
    if (!Double.isFinite(value) || value > Float.MAX_VALUE || value < -Float.MAX_VALUE)
      throw new IllegalArgumentException("Invalid " + label);
    return (float) value;
  }

  private static void requireFinite(float value, String label) {
    if (!Float.isFinite(value)) throw new IllegalArgumentException("Invalid " + label);
  }
}
