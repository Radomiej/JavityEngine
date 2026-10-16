package org.javity.physics3d;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.javity.core.Component;
import org.javity.core.TransformEditPolicy;

/** Typed primitive collision geometry in world units, independent of rendered model scale. */
public abstract class Collider3D extends Component implements TransformEditPolicy {
  private final Vector3 offset = new Vector3();
  private final Quaternion rotation = new Quaternion();
  long revision;
  private boolean trigger;
  private int group = 1, mask = 0xffff;
  private float friction = .5f, restitution;
  private final List<Consumer<Contact3D>> listeners = new ArrayList<>();

  @Override
  public final boolean allowsInspectorTransformEdit() {
    return false;
  }

  public Vector3 getOffset() {
    return offset.cpy();
  }

  public Quaternion getRotation() {
    return new Quaternion(rotation);
  }

  /** Marks this collider as a no-response overlap volume with its own native collision object. */
  public Collider3D setTrigger(boolean trigger) {
    if (this.trigger != trigger) {
      this.trigger = trigger;
      revision++;
    }
    return this;
  }

  public boolean isTrigger() {
    return trigger;
  }

  /** Sets the unsigned 16-bit Bullet broadphase group and mask for this collider. */
  public Collider3D setFilter(int group, int mask) {
    PhysicsFilter3D.validateBits(group, "group");
    PhysicsFilter3D.validateBits(mask, "mask");
    if (this.group != group || this.mask != mask) {
      this.group = group;
      this.mask = mask;
      revision++;
    }
    return this;
  }

  public int getCollisionGroup() {
    return group;
  }

  public int getCollisionMask() {
    return mask;
  }

  public Collider3D setMaterial(float friction, float restitution) {
    Physics3D.nonnegative(friction);
    if (!Float.isFinite(restitution) || restitution < 0 || restitution > 1)
      throw new IllegalArgumentException("Restitution must be in [0, 1]");
    if (this.friction != friction || this.restitution != restitution) {
      this.friction = friction;
      this.restitution = restitution;
      revision++;
    }
    return this;
  }

  public float getFriction() {
    return friction;
  }

  public float getRestitution() {
    return restitution;
  }

  public AutoCloseable onContact(Consumer<Contact3D> listener) {
    Objects.requireNonNull(listener);
    listeners.add(listener);
    return new AutoCloseable() {
      private boolean closed;

      @Override
      public void close() {
        if (!closed) {
          closed = true;
          listeners.remove(listener);
        }
      }
    };
  }

  List<Consumer<Contact3D>> listenersSnapshot() {
    return List.copyOf(listeners);
  }

  void deliver(Contact3D event) {
    for (Consumer<Contact3D> listener : listenersSnapshot()) listener.accept(event);
  }

  @Override
  protected void onRelease() {
    listeners.clear();
  }

  public Collider3D setLocalPose(Vector3 offset, Quaternion rotation) {
    Objects.requireNonNull(offset);
    Objects.requireNonNull(rotation);
    Physics3D.finite(offset);
    Physics3D.validateQuaternion(rotation);
    this.offset.set(offset);
    this.rotation.set(Physics3D.normalizedCopy(rotation));
    revision++;
    return this;
  }

  abstract void addTo(ShapeBuilder builder, Vector3 childOffset, Quaternion childRotation);

  abstract String descriptor();

  final void addTo(ShapeBuilder builder) {
    addTo(builder, offset, rotation);
  }

  final boolean hasCenteredIdentityPose() {
    return offset.isZero(1e-6f)
        && Math.abs(Math.abs(rotation.w) - 1f) < 1e-6f
        && Math.abs(rotation.x) < 1e-6f
        && Math.abs(rotation.y) < 1e-6f
        && Math.abs(rotation.z) < 1e-6f;
  }

  final boolean rotationIsIrrelevant() {
    return this instanceof SphereCollider3D;
  }
}
