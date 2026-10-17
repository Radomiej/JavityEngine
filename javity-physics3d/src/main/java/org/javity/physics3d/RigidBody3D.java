package org.javity.physics3d;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import java.util.ArrayDeque;
import java.util.Objects;
import org.javity.core.Component;
import org.javity.core.TransformEditPolicy;

/** One movement authority per object. Commands and values are copied at the API boundary. */
public final class RigidBody3D extends Component implements TransformEditPolicy {
  public enum Type {
    STATIC,
    DYNAMIC,
    KINEMATIC
  }

  record Action(boolean impulse, Vector3 value) {}

  record Pose(Vector3 position, Quaternion rotation) {}

  private Type type;
  private float mass = 1, linearDamping, angularDamping, ccdThreshold, ccdRadius;
  private long revision;
  private final ArrayDeque<Action> actions = new ArrayDeque<>();
  private Vector3 requestedLinearVelocity, requestedAngularVelocity;
  private Pose kinematicTarget, teleportTarget;
  private boolean clearVelocityOnTeleport;
  private final Vector3 linearVelocity = new Vector3(), angularVelocity = new Vector3();

  public RigidBody3D(Type type) {
    this.type = Objects.requireNonNull(type);
  }

  public Type getType() {
    return type;
  }

  public float getMass() {
    return mass;
  }

  public Vector3 getLinearVelocity() {
    return linearVelocity.cpy();
  }

  public Vector3 getAngularVelocity() {
    return angularVelocity.cpy();
  }

  @Override
  public boolean allowsInspectorTransformEdit() {
    return false;
  }

  public RigidBody3D setType(Type type) {
    Type next = Objects.requireNonNull(type);
    if (next == Type.DYNAMIC && mass <= 0)
      throw new IllegalStateException("Dynamic body requires positive mass");
    if (this.type != next) {
      this.type = next;
      revision++;
    }
    return this;
  }

  public RigidBody3D setMass(float mass) {
    Physics3D.nonnegative(mass);
    if (type == Type.DYNAMIC && mass == 0)
      throw new IllegalArgumentException("Dynamic body requires positive mass");
    if (this.mass != mass) {
      this.mass = mass;
      revision++;
    }
    return this;
  }

  public RigidBody3D setLinearVelocity(Vector3 value) {
    Vector3 v = Physics3D.copyFinite(value);
    requestedLinearVelocity = v;
    linearVelocity.set(v);
    return this;
  }

  public RigidBody3D setAngularVelocity(Vector3 value) {
    Vector3 v = Physics3D.copyFinite(value);
    requestedAngularVelocity = v;
    angularVelocity.set(v);
    return this;
  }

  public RigidBody3D applyForce(Vector3 value) {
    actions.add(new Action(false, Physics3D.copyFinite(value)));
    return this;
  }

  public RigidBody3D applyImpulse(Vector3 value) {
    actions.add(new Action(true, Physics3D.copyFinite(value)));
    return this;
  }

  public RigidBody3D setDamping(float linear, float angular) {
    validateDamping(linear);
    validateDamping(angular);
    linearDamping = linear;
    angularDamping = angular;
    revision++;
    return this;
  }

  public RigidBody3D setCcd(float motionThreshold, float sweptSphereRadius) {
    Physics3D.nonnegative(motionThreshold);
    Physics3D.nonnegative(sweptSphereRadius);
    if ((motionThreshold == 0) != (sweptSphereRadius == 0))
      throw new IllegalArgumentException("CCD threshold and radius must both be zero or positive");
    ccdThreshold = motionThreshold;
    ccdRadius = sweptSphereRadius;
    revision++;
    return this;
  }

  public RigidBody3D moveKinematic(Vector3 position, Quaternion rotation) {
    if (type != Type.KINEMATIC)
      throw new IllegalStateException("Only kinematic bodies accept moveKinematic");
    kinematicTarget = pose(position, rotation);
    return this;
  }

  public RigidBody3D teleport(Vector3 position, Quaternion rotation, boolean clearVelocity) {
    teleportTarget = pose(position, rotation);
    clearVelocityOnTeleport = clearVelocity;
    return this;
  }

  private static Pose pose(Vector3 position, Quaternion rotation) {
    return new Pose(Physics3D.copyFinite(position), Physics3D.normalizedCopy(rotation));
  }

  @Override
  protected void onRelease() {
    actions.clear();
    kinematicTarget = null;
    teleportTarget = null;
  }

  long revision() {
    return revision;
  }

  float massForNative() {
    return type == Type.DYNAMIC ? mass : 0;
  }

  ArrayDeque<Action> actions() {
    return actions;
  }

  Vector3 takeLinearVelocity() {
    Vector3 v = requestedLinearVelocity;
    requestedLinearVelocity = null;
    return v;
  }

  Vector3 takeAngularVelocity() {
    Vector3 v = requestedAngularVelocity;
    requestedAngularVelocity = null;
    return v;
  }

  Pose takeKinematicTarget() {
    Pose p = kinematicTarget;
    kinematicTarget = null;
    return p;
  }

  Pose takeTeleportTarget() {
    Pose p = teleportTarget;
    teleportTarget = null;
    return p;
  }

  boolean clearVelocityOnTeleport() {
    return clearVelocityOnTeleport;
  }

  float linearDamping() {
    return linearDamping;
  }

  float angularDamping() {
    return angularDamping;
  }

  float ccdThreshold() {
    return ccdThreshold;
  }

  float ccdRadius() {
    return ccdRadius;
  }

  void publish(Vector3 linear, Vector3 angular) {
    linearVelocity.set(linear);
    angularVelocity.set(angular);
  }

  private static void validateDamping(float value) {
    if (!Float.isFinite(value) || value < 0 || value > 1)
      throw new IllegalArgumentException("Damping must be between 0 and 1");
  }
}
