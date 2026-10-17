package org.javity.presentation;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import java.util.Objects;
import org.javity.core.Component;
import org.javity.core.ComponentChange;
import org.javity.core.GameObject;
import org.javity.core.Transform3D;

/**
 * Additive local model pose contributions. The canonical Transform3D is borrowed and never edited.
 */
public final class Presentation3D extends Component {
  public static final int CAPACITY = 64;
  public static final int X = 0,
      Y = 1,
      Z = 2,
      ROTATION_X = 3,
      ROTATION_Y = 4,
      ROTATION_Z = 5,
      SCALE_X = 6,
      SCALE_Y = 7,
      SCALE_Z = 8;
  private static final double MAX_VALUE = Float.MAX_VALUE;
  private static final double MAX_CONTRIBUTION = Float.MAX_VALUE / (CAPACITY * 2d);
  private final Slot[] slots = new Slot[CAPACITY];
  private final double[] aggregateScratch = new double[9];
  private boolean released;
  private Runnable componentListener;
  private Transform3D canonicalTransform;
  private long observedActiveRevision, observedEnabledRevision;

  public Presentation3D() {
    for (int i = 0; i < slots.length; i++) slots[i] = new Slot();
  }

  @Override
  public void onCreate() {
    observedActiveRevision = getGameObject().activeRevision();
    observedEnabledRevision = enabledRevision();
    canonicalTransform = getGameObject().getComponent(Transform3D.class);
    if (canonicalTransform == null)
      throw new IllegalStateException("Presentation3D requires a Transform3D on its GameObject");
    componentListener =
        getGameObject()
            .onComponentChange(
                change -> {
                  if (change.type() == ComponentChange.Type.REMOVED && change.component() == this)
                    invalidateSlots();
                  if (change.type() == ComponentChange.Type.REMOVED
                      && change.component() == canonicalTransform) {
                    canonicalTransform = null;
                    invalidateSlots();
                  }
                });
  }

  /** Creates a persistent, caller-owned channel. Closing it removes only its held pose. */
  public Channel openChannel() {
    int index = allocate();
    Slot slot = slots[index];
    slot.generation++;
    slot.channel = true;
    Channel channel = new Channel(this, index, slot.generation);
    slot.owner = channel;
    return channel;
  }

  /** Creates one bounded transient additive contribution. */
  public Lease contribute() {
    int index = allocate();
    Slot slot = slots[index];
    slot.generation++;
    slot.channel = false;
    Lease lease = new Lease(this, index, slot.generation);
    slot.owner = lease;
    return lease;
  }

  public int activeCount() {
    isUsable();
    int count = 0;
    for (Slot slot : slots) if (slot.active) count++;
    return count;
  }

  /**
   * Copies the current aggregate into caller-owned storage. Disabled/inactive owners publish
   * identity.
   */
  public void snapshot(Pose out) {
    Objects.requireNonNull(out, "out");
    if (!isUsable()) {
      out.set(null);
      return;
    }
    aggregate(-1, null, aggregateScratch);
    out.set(aggregateScratch);
  }

  private boolean isUsable() {
    GameObject owner = getGameObject();
    if (released
        || owner == null
        || !owner.containsComponent(this)
        || !isInitialized()
        || canonicalTransform == null
        || !owner.containsComponent(canonicalTransform)
        || owner.getComponent(Transform3D.class) != canonicalTransform) return false;
    long activeRevision = owner.activeRevision();
    long enabledRevision = enabledRevision();
    if (activeRevision != observedActiveRevision || enabledRevision != observedEnabledRevision) {
      observedActiveRevision = activeRevision;
      observedEnabledRevision = enabledRevision;
      invalidateSlots();
    }
    if (!isEnabled() || !owner.isActive() || owner.isDestroyed()) {
      invalidateSlots();
      return false;
    }
    return true;
  }

  private int allocate() {
    if (released || getGameObject() != null && !isUsable())
      throw new IllegalStateException("Presentation3D provider is no longer attached and active");
    for (int i = 0; i < slots.length; i++)
      if (!slots[i].active) {
        slots[i].active = true;
        return i;
      }
    throw new IllegalStateException("Presentation3D capacity of 64 contributions exceeded");
  }

  private void write(int index, long generation, int property, float value) {
    requireLive(index, generation);
    finite(value);
    if (Math.abs((double) value) > MAX_CONTRIBUTION)
      throw new IllegalArgumentException("presentation contribution exceeds supported magnitude");
    Slot slot = slots[index];
    double[] candidate = slot.values.clone();
    candidate[property] = value;
    aggregate(index, candidate, aggregateScratch); // Validate the full publication before commit.
    slot.values[property] = value;
  }

  private void writeAll(int index, long generation, double[] candidate) {
    requireLive(index, generation);
    if (slots[index].paused)
      throw new IllegalStateException("paused presentation lease cannot be changed");
    double[] copy = candidate.clone();
    for (double value : copy) {
      if (!Double.isFinite(value) || Math.abs(value) > MAX_CONTRIBUTION)
        throw new IllegalArgumentException("presentation contribution must be finite and bounded");
    }
    aggregate(index, copy, aggregateScratch);
    System.arraycopy(copy, 0, slots[index].values, 0, copy.length);
  }

  private float read(int index, long generation, int property) {
    requireLive(index, generation);
    return (float) slots[index].values[property];
  }

  private void aggregate(int replacing, double[] replacement, double[] sum) {
    java.util.Arrays.fill(sum, 0d);
    for (int i = 0; i < slots.length; i++) {
      Slot slot = slots[i];
      if (!slot.active) continue;
      double[] values = i == replacing ? replacement : slot.values;
      for (int p = 0; p < sum.length; p++) sum[p] += values[p];
    }
    for (double value : sum)
      if (!Double.isFinite(value) || Math.abs(value) > Float.MAX_VALUE)
        throw new IllegalArgumentException("aggregate presentation pose is not representable");
    double sx = 1d + sum[6], sy = 1d + sum[7], sz = 1d + sum[8];
    if (!Double.isFinite(sx)
        || !Double.isFinite(sy)
        || !Double.isFinite(sz)
        || Math.abs(sx) > Float.MAX_VALUE
        || Math.abs(sy) > Float.MAX_VALUE
        || Math.abs(sz) > Float.MAX_VALUE)
      throw new IllegalArgumentException("aggregate presentation scale is not representable");
    sum[6] = sx - 1d;
    sum[7] = sy - 1d;
    sum[8] = sz - 1d;
  }

  private void setPaused(int index, long generation, boolean paused) {
    requireLive(index, generation);
    slots[index].paused = paused;
  }

  private void release(int index, long generation) {
    if (!matches(index, generation)) return;
    Slot slot = slots[index];
    slot.active = false;
    slot.paused = false;
    slot.channel = false;
    slot.owner = null;
    java.util.Arrays.fill(slot.values, 0d);
  }

  private boolean matches(int index, long generation) {
    if (index < 0 || index >= slots.length || !isUsable()) return false;
    return slots[index].active && slots[index].generation == generation;
  }

  private void requireLive(int index, long generation) {
    if (!matches(index, generation))
      throw new IllegalStateException("Presentation3D lease is stale");
  }

  @Override
  protected void onRelease() {
    released = true;
    invalidateSlots();
    if (componentListener != null) componentListener.run();
    componentListener = null;
    canonicalTransform = null;
  }

  private void invalidateSlots() {
    for (Slot slot : slots) {
      Object owner = slot.owner;
      if (owner instanceof Channel channel) channel.invalidate();
      if (owner instanceof Lease lease) lease.invalidate();
      slot.active = false;
      slot.channel = false;
      slot.owner = null;
      slot.paused = false;
      java.util.Arrays.fill(slot.values, 0d);
    }
  }

  private static void finite(float value) {
    if (!Float.isFinite(value))
      throw new IllegalArgumentException("presentation value must be finite");
  }

  /** Caller-owned immutable-by-provider copy of the aggregate model pose. */
  public static final class Pose {
    private double x, y, z, rx, ry, rz, sx = 1, sy = 1, sz = 1;
    private final Quaternion rotation = new Quaternion();
    private final Vector3 translation = new Vector3();
    private final Vector3 scale = new Vector3(1, 1, 1);

    private void set(double[] a) {
      if (a == null) {
        x = y = z = rx = ry = rz = 0;
        sx = sy = sz = 1;
        return;
      }
      x = a[0];
      y = a[1];
      z = a[2];
      rx = a[3];
      ry = a[4];
      rz = a[5];
      sx = a[6] + 1d;
      sy = a[7] + 1d;
      sz = a[8] + 1d;
    }

    public float translationX() {
      return (float) x;
    }

    public float translationY() {
      return (float) y;
    }

    public float translationZ() {
      return (float) z;
    }

    public float rotationX() {
      return (float) rx;
    }

    public float rotationY() {
      return (float) ry;
    }

    public float rotationZ() {
      return (float) rz;
    }

    public float scaleX() {
      return (float) sx;
    }

    public float scaleY() {
      return (float) sy;
    }

    public float scaleZ() {
      return (float) sz;
    }

    /**
     * Writes local translation × normalized rotation-vector quaternion × scale to caller storage.
     */
    public Matrix4 localMatrix(Matrix4 out) {
      Objects.requireNonNull(out, "out");
      double angle = Math.sqrt(rx * rx + ry * ry + rz * rz);
      double qx, qy, qz, qw;
      if (angle < 1e-8) {
        double factor = .5d - angle * angle / 48d;
        qx = rx * factor;
        qy = ry * factor;
        qz = rz * factor;
        qw = 1d - angle * angle / 8d;
      } else {
        double half = angle * .5d, factor = Math.sin(half) / angle;
        qx = rx * factor;
        qy = ry * factor;
        qz = rz * factor;
        qw = Math.cos(half);
      }
      rotation.set((float) qx, (float) qy, (float) qz, (float) qw).nor();
      translation.set((float) x, (float) y, (float) z);
      scale.set((float) sx, (float) sy, (float) sz);
      return out.set(translation, rotation, scale);
    }
  }

  public static final class Channel implements AutoCloseable {
    private Presentation3D provider;
    private final int index;
    private final long generation;

    private Channel(Presentation3D p, int i, long g) {
      provider = p;
      index = i;
      generation = g;
    }

    public GameObject gameObject() {
      return require().getGameObject();
    }

    public boolean isValid() {
      Presentation3D current = provider;
      return current != null && current.matches(index, generation) && current.slots[index].channel;
    }

    public void close() {
      if (provider != null) {
        provider.release(index, generation);
        provider = null;
      }
    }

    private void invalidate() {
      provider = null;
    }

    public float get(int property) {
      return require().read(index, generation, property);
    }

    public void set(int property, float value) {
      require().write(index, generation, property, value);
    }

    public float getX() {
      return get(X);
    }

    public void setX(float v) {
      set(X, v);
    }

    public float getY() {
      return get(Y);
    }

    public void setY(float v) {
      set(Y, v);
    }

    public float getZ() {
      return get(Z);
    }

    public void setZ(float v) {
      set(Z, v);
    }

    public float getRotationX() {
      return get(ROTATION_X);
    }

    public void setRotationX(float v) {
      set(ROTATION_X, v);
    }

    public float getRotationY() {
      return get(ROTATION_Y);
    }

    public void setRotationY(float v) {
      set(ROTATION_Y, v);
    }

    public float getRotationZ() {
      return get(ROTATION_Z);
    }

    public void setRotationZ(float v) {
      set(ROTATION_Z, v);
    }

    public float getScaleX() {
      return get(SCALE_X);
    }

    public void setScaleX(float v) {
      set(SCALE_X, v);
    }

    public float getScaleY() {
      return get(SCALE_Y);
    }

    public void setScaleY(float v) {
      set(SCALE_Y, v);
    }

    public float getScaleZ() {
      return get(SCALE_Z);
    }

    public void setScaleZ(float v) {
      set(SCALE_Z, v);
    }

    private Presentation3D require() {
      if (!isValid()) throw new IllegalStateException("Presentation3D channel is stale");
      return provider;
    }
  }

  public static final class Lease implements AutoCloseable {
    private Presentation3D provider;
    private final int index;
    private final long generation;

    private Lease(Presentation3D p, int i, long g) {
      provider = p;
      index = i;
      generation = g;
    }

    public boolean isActive() {
      return provider != null && provider.matches(index, generation);
    }

    public boolean isPaused() {
      return isActive() && provider.slots[index].paused;
    }

    public Lease pause() {
      require().setPaused(index, generation, true);
      return this;
    }

    public Lease resume() {
      require().setPaused(index, generation, false);
      return this;
    }

    public void set(
        float x, float y, float z, float rx, float ry, float rz, float sx, float sy, float sz) {
      require().writeAll(index, generation, new double[] {x, y, z, rx, ry, rz, sx, sy, sz});
    }

    public void close() {
      if (provider != null) {
        provider.release(index, generation);
        provider = null;
      }
    }

    private void invalidate() {
      provider = null;
    }

    private Presentation3D require() {
      if (!isActive()) throw new IllegalStateException("Presentation3D lease is stale");
      return provider;
    }
  }

  private static final class Slot {
    boolean active, paused, channel;
    long generation;
    Object owner;
    final double[] values = new double[9];
  }
}
