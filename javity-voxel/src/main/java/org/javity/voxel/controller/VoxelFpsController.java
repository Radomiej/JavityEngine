package org.javity.voxel.controller;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import java.util.Objects;
import org.javity.core.Component;
import org.javity.core.Transform3D;
import org.javity.voxel.BlockPos;
import org.javity.voxel.ChunkPos;
import org.javity.voxel.VoxelWorld;

/** CPU-only, axis-aligned first-person movement for a finite, explicitly loaded voxel world. */
public final class VoxelFpsController extends Component {
  public static final float BODY_RADIUS = 0.30f;
  public static final float BODY_HEIGHT = 1.80f;
  public static final float EYE_HEIGHT = 1.62f;
  public static final float WALK_SPEED = 4.3f;
  public static final float SPRINT_SPEED = 7.0f;
  public static final float JUMP_SPEED = 8.0f;
  public static final float GRAVITY = 24.0f;
  public static final float TERMINAL_VELOCITY = 50.0f;
  public static final float MAX_FRAME_DELTA = 0.25f;
  public static final int MAX_MOVEMENT_STEPS = 64;
  private static final float MAX_STEP_DISTANCE = 0.25f;
  private static final float COLLISION_EPSILON = 1e-5f;
  private static final float MAX_PITCH = 89.9f;
  private static final float JUMP_BUFFER_SECONDS = 0.12f;

  private final VoxelWorld world;
  private final Quaternion yawRotation = new Quaternion();
  private final Quaternion pitchRotation = new Quaternion();
  private Transform3D transform;
  private float forwardInput;
  private float rightInput;
  private float yawDegrees;
  private float pitchDegrees;
  private float verticalVelocity;
  private float jumpBuffer;
  private boolean sprinting;
  private boolean jumpHeld;
  private boolean jumpQueued;
  private boolean grounded;

  public VoxelFpsController(VoxelWorld world) {
    this.world = Objects.requireNonNull(world, "world");
  }

  @Override
  public void onCreate() {
    transform = getComponent(Transform3D.class);
    if (transform == null)
      throw new IllegalStateException("VoxelFpsController requires a same-object Transform3D");
    validateFeetPosition();
    applyLookRotation();
    grounded =
        collidesAt(
            transform.position.x, transform.position.y - COLLISION_EPSILON, transform.position.z);
  }

  @Override
  public void onUpdate(float delta) {
    advance(delta);
  }

  /**
   * Sets local movement axes. Forward is positive and right is positive; each is clamped to [-1,1].
   */
  public void setMoveInput(float forward, float right) {
    requireFinite(forward, "forward");
    requireFinite(right, "right");
    forwardInput = clamp(forward, -1f, 1f);
    rightInput = clamp(right, -1f, 1f);
  }

  public void setSprint(boolean sprinting) {
    this.sprinting = sprinting;
  }

  /** Queues a jump on the rising edge; holding the key does not repeatedly jump. */
  public void setJump(boolean held) {
    if (held && !jumpHeld) {
      jumpQueued = true;
      jumpBuffer = JUMP_BUFFER_SECONDS;
    }
    jumpHeld = held;
  }

  /** Applies relative mouse motion in degrees. Positive pitch looks upward. */
  public void addLookInput(float yawDeltaDegrees, float pitchDeltaDegrees) {
    requireFinite(yawDeltaDegrees, "yawDeltaDegrees");
    requireFinite(pitchDeltaDegrees, "pitchDeltaDegrees");
    yawDegrees = wrapDegrees((double) yawDegrees + yawDeltaDegrees);
    pitchDegrees = clamp(pitchDegrees + pitchDeltaDegrees, -MAX_PITCH, MAX_PITCH);
    applyLookRotation();
  }

  /** Direct CPU simulation entry point; the game loop normally calls this through onUpdate. */
  public void advance(float delta) {
    if (!Float.isFinite(delta) || delta < 0f)
      throw new IllegalArgumentException("delta must be finite and non-negative");
    if (transform == null
        || !isInitialized()
        || getGameObject() == null
        || getGameObject().isDestroyed())
      throw new IllegalStateException("VoxelFpsController is not attached to a live GameObject");
    if (!isEnabled() || !getGameObject().isActive()) return;
    validateFeetPosition();
    if (delta == 0f) return;

    float dt = Math.min(delta, MAX_FRAME_DELTA);
    if (jumpQueued) {
      if (grounded) {
        verticalVelocity = JUMP_SPEED;
        grounded = false;
        jumpQueued = false;
        jumpBuffer = 0f;
      }
    }

    float speed = sprinting ? SPRINT_SPEED : WALK_SPEED;
    float f = forwardInput;
    float r = rightInput;
    float magnitude = (float) Math.sqrt(f * f + r * r);
    if (magnitude > 1f) {
      f /= magnitude;
      r /= magnitude;
    }
    double radians = Math.toRadians(yawDegrees);
    float dx = (float) ((-Math.sin(radians) * f + Math.cos(radians) * r) * speed * dt);
    float dz = (float) ((-Math.cos(radians) * f - Math.sin(radians) * r) * speed * dt);

    verticalVelocity = Math.max(-TERMINAL_VELOCITY, verticalVelocity - GRAVITY * dt);
    float dy = verticalVelocity * dt;
    float maxDistance = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
    int steps =
        Math.max(
            1,
            Math.max(
                (int) Math.ceil(maxDistance / MAX_STEP_DISTANCE), (int) Math.ceil(dt / 0.01f)));
    if (steps > MAX_MOVEMENT_STEPS)
      throw new IllegalStateException("Movement exceeded the bounded collision step budget");
    float stepDelta = dt / steps;
    float stepX = dx / steps, stepY = dy / steps, stepZ = dz / steps;
    for (int i = 0; i < steps; i++) {
      grounded = false;
      float movedY = moveAxis(1, stepY);
      if (movedY != stepY) {
        if (stepY < 0f) grounded = true;
        verticalVelocity = 0f;
      }
      moveAxis(0, stepX);
      moveAxis(2, stepZ);
      if (!grounded && verticalVelocity == 0f) {
        float probe = moveAxis(1, -COLLISION_EPSILON * 2f);
        if (probe < 0f) {
          grounded = true;
          transform.position.y -= probe;
        }
      }
      if (jumpQueued && !grounded) {
        jumpBuffer = Math.max(0f, jumpBuffer - stepDelta);
        if (jumpBuffer == 0f) jumpQueued = false;
      }
    }
    validateFeetPosition();
  }

  public boolean isGrounded() {
    return grounded;
  }

  public float getVerticalVelocity() {
    return verticalVelocity;
  }

  public float getYawDegrees() {
    return yawDegrees;
  }

  public float getPitchDegrees() {
    return pitchDegrees;
  }

  public Transform3D getTransform3D() {
    return transform;
  }

  /** Writes the camera eye position. The returned vector is the caller-provided output. */
  public Vector3 getEyePosition(Vector3 out) {
    Objects.requireNonNull(out, "out");
    if (transform == null) throw new IllegalStateException("Controller is not initialized");
    return out.set(transform.position.x, transform.position.y + EYE_HEIGHT, transform.position.z);
  }

  /** Writes the unit look direction (yaw zero faces world -Z). */
  public Vector3 getLookDirection(Vector3 out) {
    Objects.requireNonNull(out, "out");
    double yaw = Math.toRadians(yawDegrees), pitch = Math.toRadians(pitchDegrees);
    float cp = (float) Math.cos(pitch);
    return out.set(
            (float) (-Math.sin(yaw) * cp), (float) Math.sin(pitch), (float) (-Math.cos(yaw) * cp))
        .nor();
  }

  private void applyLookRotation() {
    if (transform == null) return;
    yawRotation.setFromAxis(Vector3.Y, yawDegrees);
    pitchRotation.setFromAxis(Vector3.X, pitchDegrees);
    transform.rotation.set(yawRotation).mul(pitchRotation).nor();
  }

  private float moveAxis(int axis, float amount) {
    if (amount == 0f) return 0f;
    Vector3 p = transform.position;
    float minX = p.x - BODY_RADIUS, maxX = p.x + BODY_RADIUS;
    float minY = p.y, maxY = p.y + BODY_HEIGHT;
    float minZ = p.z - BODY_RADIUS, maxZ = p.z + BODY_RADIUS;
    float min = axis == 0 ? minX : axis == 1 ? minY : minZ;
    float max = axis == 0 ? maxX : axis == 1 ? maxY : maxZ;
    float allowed = amount;
    if (amount > 0f) {
      int first = floorToInt(max - COLLISION_EPSILON);
      int last = floorToInt(max + amount - COLLISION_EPSILON);
      for (int lead = first; lead <= last; lead++) {
        float distance = lead - max;
        if (distance < -COLLISION_EPSILON) continue;
        if (distance > allowed) break;
        if (overlapsSolid(axis, lead, minX, maxX, minY, maxY, minZ, maxZ)) {
          allowed = Math.max(0f, distance);
          break;
        }
      }
    } else {
      int first = floorToInt(min + COLLISION_EPSILON);
      int last = floorToInt(min + amount + COLLISION_EPSILON);
      for (int lead = first; lead >= last; lead--) {
        float distance = (lead + 1f) - min;
        if (distance > COLLISION_EPSILON) continue;
        if (distance < allowed) break;
        if (overlapsSolid(axis, lead, minX, maxX, minY, maxY, minZ, maxZ)) {
          allowed = Math.min(0f, distance);
          break;
        }
      }
    }
    if (axis == 0) p.x += allowed;
    else if (axis == 1) p.y += allowed;
    else p.z += allowed;
    return allowed;
  }

  private boolean overlapsSolid(
      int axis, int lead, float minX, float maxX, float minY, float maxY, float minZ, float maxZ) {
    int x0 = axis == 0 ? lead : floorToInt(minX + COLLISION_EPSILON);
    int x1 = axis == 0 ? lead : floorToInt(maxX - COLLISION_EPSILON);
    int y0 = axis == 1 ? lead : floorToInt(minY + COLLISION_EPSILON);
    int y1 = axis == 1 ? lead : floorToInt(maxY - COLLISION_EPSILON);
    int z0 = axis == 2 ? lead : floorToInt(minZ + COLLISION_EPSILON);
    int z1 = axis == 2 ? lead : floorToInt(maxZ - COLLISION_EPSILON);
    for (int y = y0; y <= y1; y++)
      for (int z = z0; z <= z1; z++)
        for (int x = x0; x <= x1; x++) if (solidOrUnloaded(x, y, z)) return true;
    return false;
  }

  private boolean collidesAt(float x, float y, float z) {
    float minX = x - BODY_RADIUS, maxX = x + BODY_RADIUS;
    float minY = y, maxY = y + BODY_HEIGHT;
    float minZ = z - BODY_RADIUS, maxZ = z + BODY_RADIUS;
    for (int by = floorToInt(minY + COLLISION_EPSILON);
        by <= floorToInt(maxY - COLLISION_EPSILON);
        by++)
      for (int bz = floorToInt(minZ + COLLISION_EPSILON);
          bz <= floorToInt(maxZ - COLLISION_EPSILON);
          bz++)
        for (int bx = floorToInt(minX + COLLISION_EPSILON);
            bx <= floorToInt(maxX - COLLISION_EPSILON);
            bx++) if (solidOrUnloaded(bx, by, bz)) return true;
    return false;
  }

  private boolean solidOrUnloaded(int x, int y, int z) {
    if (!inWorldRange(x) || !inWorldRange(y) || !inWorldRange(z)) return true;
    if (!world.isLoaded(ChunkPos.fromBlock(x, y, z))) return true;
    return world.getBlock(x, y, z) != 0;
  }

  private void validateFeetPosition() {
    Vector3 p = transform.position;
    if (!Float.isFinite(p.x)
        || !Float.isFinite(p.y)
        || !Float.isFinite(p.z)
        || p.x - BODY_RADIUS < BlockPos.MIN_COORDINATE
        || p.x + BODY_RADIUS > BlockPos.MAX_COORDINATE + 1f
        || p.y < BlockPos.MIN_COORDINATE
        || p.y + BODY_HEIGHT > BlockPos.MAX_COORDINATE + 1f
        || p.z - BODY_RADIUS < BlockPos.MIN_COORDINATE
        || p.z + BODY_RADIUS > BlockPos.MAX_COORDINATE + 1f)
      throw new IllegalStateException(
          "Player feet position is non-finite or outside voxel world bounds");
  }

  private static int floorToInt(float value) {
    if (!Float.isFinite(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
      throw new IllegalStateException("Collision coordinate is outside integer range");
    return (int) Math.floor(value);
  }

  private static boolean inWorldRange(int coordinate) {
    return coordinate >= BlockPos.MIN_COORDINATE && coordinate <= BlockPos.MAX_COORDINATE;
  }

  private static void requireFinite(float value, String label) {
    if (!Float.isFinite(value)) throw new IllegalArgumentException(label + " must be finite");
  }

  private static float clamp(float value, float min, float max) {
    return Math.max(min, Math.min(max, value));
  }

  private static float wrapDegrees(double degrees) {
    degrees %= 360d;
    if (degrees >= 180d) degrees -= 360d;
    if (degrees < -180d) degrees += 360d;
    return (float) degrees;
  }
}
