package org.javity.physics3d;

import com.badlogic.gdx.math.Vector3;
import java.util.Objects;
import java.util.function.Consumer;
import org.javity.core.Component;
import org.javity.core.TransformEditPolicy;

/** Optional Y-up Bullet capsule movement authority. Transform3D.position is the capsule feet. */
public final class BulletCharacterController3D extends Component implements TransformEditPolicy {
  public record State(
      Vector3 feet,
      Vector3 velocity,
      boolean boundToPhysics,
      boolean grounded,
      boolean jumped,
      boolean landed) {
    public State {
      feet = feet.cpy();
      velocity = velocity.cpy();
    }

    @Override
    public Vector3 feet() {
      return feet.cpy();
    }

    @Override
    public Vector3 velocity() {
      return velocity.cpy();
    }
  }

  private final float radius, totalHeight, stepHeight;
  private float maxSlopeRadians = (float) Math.toRadians(45), speed = 4f, sprintMultiplier = 1.7f;
  private float jumpSpeed = 6f, maxFallSpeed = 55f;
  private int jumpBufferTicks = 4;
  private int collisionGroup = 1, collisionMask = 0xffff;
  private float moveX, moveZ, yaw, pitch;
  private boolean sprint, jumpHeld;
  private boolean jumpRequested;
  private long jumpRequestRevision;
  private int bufferedJumpTicks;
  private CharacterReadiness3D readiness;
  private final Vector3 feet = new Vector3(), velocity = new Vector3();
  private boolean grounded, jumped, landed;
  private boolean boundToPhysics;
  private Consumer<State> stateListener = ignored -> {};
  private Consumer<Contact3D> triggerListener = ignored -> {};
  private Vector3 warpFeet;

  public BulletCharacterController3D(float radius, float totalHeight, float stepHeight) {
    Physics3D.positive(radius);
    Physics3D.positive(totalHeight);
    Physics3D.nonnegative(stepHeight);
    if (totalHeight < 2 * radius || totalHeight > Float.MAX_VALUE / 8f)
      throw new IllegalArgumentException(
          "Capsule totalHeight must be at least its diameter and finite");
    if (stepHeight > totalHeight)
      throw new IllegalArgumentException("Step height exceeds capsule height");
    this.radius = radius;
    this.totalHeight = totalHeight;
    this.stepHeight = stepHeight;
  }

  @Override
  public boolean allowsInspectorTransformEdit() {
    return false;
  }

  public float radius() {
    return radius;
  }

  public float totalHeight() {
    return totalHeight;
  }

  public float stepHeight() {
    return stepHeight;
  }

  public float maxSlopeRadians() {
    return maxSlopeRadians;
  }

  public Vector3 feet() {
    return feet.cpy();
  }

  public Vector3 velocity() {
    return velocity.cpy();
  }

  public boolean isGrounded() {
    return grounded;
  }

  /** Whether a native ghost and action are currently registered in the scene's Bullet world. */
  public boolean isBoundToPhysics() {
    return boundToPhysics;
  }

  public float yawRadians() {
    return yaw;
  }

  public float pitchRadians() {
    return pitch;
  }

  public BulletCharacterController3D setMoveAxes(float right, float forward) {
    if (!Float.isFinite(right) || !Float.isFinite(forward))
      throw new IllegalArgumentException("Move axes must be finite");
    double length = Math.sqrt((double) right * right + (double) forward * forward);
    if (length > 1) {
      right = (float) (right / length);
      forward = (float) (forward / length);
    }
    moveX = right;
    moveZ = forward;
    return this;
  }

  public BulletCharacterController3D setSprint(boolean sprint) {
    this.sprint = sprint;
    return this;
  }

  /** Jump is edge-triggered; repeated true calls while held do not queue repeated jumps. */
  public BulletCharacterController3D setJumpHeld(boolean held) {
    if (held && !jumpHeld) {
      jumpRequested = true;
      jumpRequestRevision++;
    }
    jumpHeld = held;
    return this;
  }

  public BulletCharacterController3D setLook(float yawRadians, float pitchRadians) {
    if (!Float.isFinite(yawRadians) || !Float.isFinite(pitchRadians))
      throw new IllegalArgumentException("Look angles must be finite");
    yaw = yawRadians;
    pitch = Math.max((float) -Math.PI / 2f, Math.min((float) Math.PI / 2f, pitchRadians));
    return this;
  }

  /** Clears held input and pending jump after host focus loss. */
  public void clearInput() {
    moveX = moveZ = 0;
    sprint = false;
    jumpHeld = false;
    jumpRequested = false;
    bufferedJumpTicks = 0;
  }

  public BulletCharacterController3D setSpeed(float speed) {
    Physics3D.nonnegative(speed);
    this.speed = speed;
    return this;
  }

  public BulletCharacterController3D setSprintMultiplier(float value) {
    Physics3D.positive(value);
    sprintMultiplier = value;
    return this;
  }

  public BulletCharacterController3D setJumpSpeed(float value) {
    ensureNativeConfigurationMutable();
    Physics3D.positive(value);
    jumpSpeed = value;
    return this;
  }

  public BulletCharacterController3D setMaxFallSpeed(float value) {
    ensureNativeConfigurationMutable();
    Physics3D.positive(value);
    maxFallSpeed = value;
    return this;
  }

  public BulletCharacterController3D setJumpBufferTicks(int ticks) {
    ensureNativeConfigurationMutable();
    if (ticks < 0 || ticks > 120)
      throw new IllegalArgumentException("Jump buffer must be in [0,120]");
    jumpBufferTicks = ticks;
    return this;
  }

  /** Slope is in radians. */
  public BulletCharacterController3D setMaxSlopeRadians(float radians) {
    ensureNativeConfigurationMutable();
    if (!Float.isFinite(radians) || radians < 0 || radians >= Math.PI / 2)
      throw new IllegalArgumentException("Slope must be in [0, pi/2)");
    maxSlopeRadians = radians;
    return this;
  }

  public BulletCharacterController3D setFilter(int group, int mask) {
    ensureNativeConfigurationMutable();
    PhysicsFilter3D.validateBits(group, "group");
    PhysicsFilter3D.validateBits(mask, "mask");
    collisionGroup = group;
    collisionMask = mask;
    return this;
  }

  private void ensureNativeConfigurationMutable() {
    if (isInitialized())
      throw new IllegalStateException(
          "Bullet character native settings are immutable after initialization");
  }

  public BulletCharacterController3D setReadinessPolicy(CharacterReadiness3D policy) {
    readiness = policy;
    return this;
  }

  public BulletCharacterController3D onState(Consumer<State> listener) {
    stateListener = Objects.requireNonNull(listener);
    return this;
  }

  /** Receives copied trigger enter/stay/exit events after the native step. */
  public BulletCharacterController3D onTrigger(Consumer<Contact3D> listener) {
    triggerListener = Objects.requireNonNull(listener);
    return this;
  }

  /** Queues an explicit feet-position warp for the next unlocked Physics3D boundary. */
  public BulletCharacterController3D warp(Vector3 worldFeet) {
    warpFeet = Physics3D.copyFinite(worldFeet);
    return this;
  }

  long jumpRequestRevision() {
    return jumpRequestRevision;
  }

  boolean consumeJumpRequest(long expectedRevision) {
    if (jumpRequestRevision != expectedRevision) return false;
    boolean v = jumpRequested;
    jumpRequested = false;
    return v;
  }

  boolean jumpHeld() {
    return jumpHeld;
  }

  float moveX() {
    return moveX;
  }

  float moveZ() {
    return moveZ;
  }

  boolean sprinting() {
    return sprint;
  }

  float speed() {
    return speed;
  }

  float sprintMultiplier() {
    return sprintMultiplier;
  }

  float jumpSpeed() {
    return jumpSpeed;
  }

  float maxFallSpeed() {
    return maxFallSpeed;
  }

  int jumpBufferTicks() {
    return jumpBufferTicks;
  }

  int bufferedJumpTicks() {
    return bufferedJumpTicks;
  }

  void bufferedJumpTicks(int ticks) {
    bufferedJumpTicks = ticks;
  }

  float maxSlopeRadiansValue() {
    return maxSlopeRadians;
  }

  int collisionGroup() {
    return collisionGroup;
  }

  int collisionMask() {
    return collisionMask;
  }

  CharacterReadiness3D readiness() {
    return readiness;
  }

  Vector3 takeWarpFeet() {
    Vector3 v = warpFeet;
    warpFeet = null;
    return v;
  }

  Vector3 pendingWarpFeet() {
    return warpFeet == null ? null : warpFeet.cpy();
  }

  State updateState(
      Vector3 position,
      Vector3 measuredVelocity,
      boolean bound,
      boolean onGround,
      boolean didJump,
      boolean didLand) {
    feet.set(position);
    velocity.set(measuredVelocity);
    boundToPhysics = bound;
    grounded = onGround;
    jumped = didJump;
    landed = didLand;
    return snapshot();
  }

  State snapshot() {
    return new State(feet, velocity, boundToPhysics, grounded, jumped, landed);
  }

  void setBoundToPhysics(boolean value) {
    boundToPhysics = value;
  }

  void notifyState(State value) {
    stateListener.accept(value);
  }

  void publishTrigger(Contact3D event) {
    triggerListener.accept(event);
  }

  @Override
  protected void onRelease() {
    clearInput();
    warpFeet = null;
    readiness = null;
    stateListener = ignored -> {};
    triggerListener = ignored -> {};
  }
}
