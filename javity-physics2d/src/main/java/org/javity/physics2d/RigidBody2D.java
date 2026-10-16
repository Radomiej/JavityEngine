package org.javity.physics2d;

import com.badlogic.gdx.math.Vector2;
import java.util.Objects;
import org.javity.core.Component;
import org.javity.core.TransformEditPolicy;

/** One per object. Positions and velocity use world units; angular velocity uses radians/s. */
public final class RigidBody2D extends Component implements TransformEditPolicy {
  public enum Type {
    STATIC,
    DYNAMIC,
    KINEMATIC
  }

  private Type type;
  private boolean fixedRotation, bullet;
  private float gravityScale = 1, linearDamping, angularDamping;

  record Action(boolean impulse, float x, float y) {}

  final java.util.ArrayDeque<Action> actions = new java.util.ArrayDeque<>();

  /** Applied once at the next fixed physics step, at center of mass. kg * world units/s². */
  public RigidBody2D applyForce(float x, float y) {
    finite(x);
    finite(y);
    actions.add(new Action(false, x, y));
    return this;
  }

  /** kg * world units/s. Queued safely, including from contact callbacks. */
  public RigidBody2D applyLinearImpulse(float x, float y) {
    finite(x);
    finite(y);
    actions.add(new Action(true, x, y));
    return this;
  }

  @Override
  public void onDestroy() {
    actions.clear();
  }

  long revision;
  final Vector2 velocity = new Vector2();
  float angularVelocity, mass;
  boolean velocityRequested, angularVelocityRequested;

  public RigidBody2D() {
    this(Type.DYNAMIC);
  }

  @Override
  public boolean allowsInspectorTransformEdit() {
    return false;
  }

  public RigidBody2D(Type type) {
    this.type = Objects.requireNonNull(type);
  }

  public Type getType() {
    return type;
  }

  public boolean isFixedRotation() {
    return fixedRotation;
  }

  public boolean isBullet() {
    return bullet;
  }

  public float getGravityScale() {
    return gravityScale;
  }

  public float getLinearDamping() {
    return linearDamping;
  }

  public float getAngularDamping() {
    return angularDamping;
  }

  public Vector2 getLinearVelocity() {
    return velocity.cpy();
  }

  public float getAngularVelocity() {
    return angularVelocity;
  }

  public float getMass() {
    return mass;
  }

  public RigidBody2D setType(Type type) {
    if (this.type != Objects.requireNonNull(type)) {
      this.type = type;
      revision++;
    }
    return this;
  }

  public RigidBody2D setFixedRotation(boolean value) {
    if (fixedRotation != value) {
      fixedRotation = value;
      revision++;
    }
    return this;
  }

  public RigidBody2D setBullet(boolean value) {
    if (bullet != value) {
      bullet = value;
      revision++;
    }
    return this;
  }

  public RigidBody2D setGravityScale(float value) {
    finite(value);
    if (gravityScale != value) {
      gravityScale = value;
      revision++;
    }
    return this;
  }

  public RigidBody2D setLinearDamping(float value) {
    positiveOrZero(value);
    if (linearDamping != value) {
      linearDamping = value;
      revision++;
    }
    return this;
  }

  public RigidBody2D setAngularDamping(float value) {
    positiveOrZero(value);
    if (angularDamping != value) {
      angularDamping = value;
      revision++;
    }
    return this;
  }

  public RigidBody2D setLinearVelocity(float x, float y) {
    finite(x);
    finite(y);
    velocity.set(x, y);
    velocityRequested = true;
    return this;
  }

  public RigidBody2D setAngularVelocity(float value) {
    finite(value);
    angularVelocity = value;
    angularVelocityRequested = true;
    return this;
  }

  static void finite(float value) {
    if (!Float.isFinite(value)) throw new IllegalArgumentException("Expected finite physics value");
  }

  static void positiveOrZero(float value) {
    finite(value);
    if (value < 0) throw new IllegalArgumentException("Expected nonnegative physics value");
  }
}
