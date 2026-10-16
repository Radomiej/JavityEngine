package org.javity.presentation;

import java.util.Arrays;
import java.util.Objects;
import org.javity.core.Component;
import org.javity.core.ComponentChange;
import org.javity.core.GameObject;

/** Per-view additive camera-local translation, rotation-vector and FOV delta contributions. */
public final class CameraPresentation3D extends Component {
  public enum Space {
    WORLD,
    VIEWMODEL
  }

  public static final int CAPACITY = 64;
  public static final int X = 0,
      Y = 1,
      Z = 2,
      ROTATION_X = 3,
      ROTATION_Y = 4,
      ROTATION_Z = 5,
      FOV_DELTA = 6;
  private static final double MAX_VALUE = Float.MAX_VALUE;
  private static final double MAX_CONTRIBUTION = Float.MAX_VALUE / (CAPACITY * 2d);
  private final Space space;
  private final Slot[] slots = new Slot[CAPACITY];
  private final double[] aggregateScratch = new double[7];
  private boolean released;
  private Runnable componentListener;
  private long observedActiveRevision, observedEnabledRevision;

  public CameraPresentation3D(Space space) {
    this.space = Objects.requireNonNull(space, "space");
    for (int i = 0; i < slots.length; i++) slots[i] = new Slot();
  }

  public Space space() {
    return space;
  }

  @Override
  public void onCreate() {
    observedActiveRevision = getGameObject().activeRevision();
    observedEnabledRevision = enabledRevision();
    componentListener =
        getGameObject()
            .onComponentChange(
                change -> {
                  if (change.type() == ComponentChange.Type.REMOVED && change.component() == this)
                    invalidateSlots();
                });
  }

  public Channel openChannel() {
    int i = allocate();
    Slot s = slots[i];
    s.generation++;
    s.active = true;
    s.channel = true;
    Channel c = new Channel(this, i, s.generation);
    s.owner = c;
    return c;
  }

  public Lease contribute() {
    int i = allocate();
    Slot s = slots[i];
    s.generation++;
    s.active = true;
    s.channel = false;
    Lease lease = new Lease(this, i, s.generation);
    s.owner = lease;
    return lease;
  }

  public int activeCount() {
    isUsable();
    int n = 0;
    for (Slot s : slots) if (s.active) n++;
    return n;
  }

  public void snapshot(Snapshot out) {
    Objects.requireNonNull(out, "out");
    if (!isUsable()) {
      Arrays.fill(aggregateScratch, 0d);
      out.set(aggregateScratch);
      return;
    }
    aggregate(-1, null, aggregateScratch);
    out.set(aggregateScratch);
  }

  private int allocate() {
    if (released || getGameObject() != null && !isUsable())
      throw new IllegalStateException("Camera presentation provider is detached or inactive");
    for (int i = 0; i < slots.length; i++) if (!slots[i].active) return i;
    throw new IllegalStateException("Camera presentation capacity of 64 contributions exceeded");
  }

  private boolean isUsable() {
    GameObject o = getGameObject();
    if (released || o == null || !o.containsComponent(this) || !isInitialized()) return false;
    long activeRevision = o.activeRevision();
    long enabledRevision = enabledRevision();
    if (activeRevision != observedActiveRevision || enabledRevision != observedEnabledRevision) {
      observedActiveRevision = activeRevision;
      observedEnabledRevision = enabledRevision;
      invalidateSlots();
    }
    if (!isEnabled() || !o.isActive() || o.isDestroyed()) {
      invalidateSlots();
      return false;
    }
    return true;
  }

  private void aggregate(int replace, double[] values, double[] sum) {
    Arrays.fill(sum, 0d);
    for (int i = 0; i < slots.length; i++) {
      Slot s = slots[i];
      if (!s.active) continue;
      double[] v = i == replace ? values : s.values;
      for (int p = 0; p < sum.length; p++) sum[p] += v[p];
    }
    for (double v : sum)
      if (!Double.isFinite(v) || Math.abs(v) > Float.MAX_VALUE)
        throw new IllegalArgumentException("aggregate camera presentation is not representable");
  }

  private void write(int i, long g, int p, float value) {
    require(i, g);
    if (!Float.isFinite(value) || Math.abs((double) value) > MAX_CONTRIBUTION)
      throw new IllegalArgumentException("camera presentation value must be finite and bounded");
    double[] next = slots[i].values.clone();
    next[p] = value;
    aggregate(i, next, aggregateScratch);
    slots[i].values[p] = value;
  }

  private void writeAll(int i, long g, double[] next) {
    require(i, g);
    if (slots[i].paused) throw new IllegalStateException("paused camera lease cannot be changed");
    double[] copy = next.clone();
    for (double v : copy)
      if (!Double.isFinite(v) || Math.abs(v) > MAX_CONTRIBUTION)
        throw new IllegalArgumentException("camera presentation value must be finite and bounded");
    aggregate(i, copy, aggregateScratch);
    System.arraycopy(copy, 0, slots[i].values, 0, copy.length);
  }

  private float read(int i, long g, int p) {
    require(i, g);
    return (float) slots[i].values[p];
  }

  private boolean matches(int i, long g) {
    if (i < 0 || i >= slots.length || !isUsable()) return false;
    return slots[i].active && slots[i].generation == g;
  }

  private void require(int i, long g) {
    if (!matches(i, g)) throw new IllegalStateException("Camera presentation lease is stale");
  }

  private void pause(int i, long g, boolean value) {
    require(i, g);
    slots[i].paused = value;
  }

  private void release(int i, long g) {
    if (i < 0 || i >= slots.length || slots[i].generation != g || !slots[i].active) return;
    Slot s = slots[i];
    s.active = false;
    s.paused = false;
    s.owner = null;
    Arrays.fill(s.values, 0d);
  }

  @Override
  protected void onRelease() {
    released = true;
    invalidateSlots();
    if (componentListener != null) componentListener.run();
    componentListener = null;
  }

  private void invalidateSlots() {
    for (Slot s : slots) {
      if (s.owner instanceof Channel c) c.invalidate();
      if (s.owner instanceof Lease l) l.invalidate();
      s.active = false;
      s.channel = false;
      s.paused = false;
      s.owner = null;
      Arrays.fill(s.values, 0d);
    }
  }

  /** Caller-owned copied values. Rotation is a local rotation vector in radians. */
  public static final class Snapshot {
    private double x, y, z, rx, ry, rz, fov;

    private void set(double[] a) {
      x = a[0];
      y = a[1];
      z = a[2];
      rx = a[3];
      ry = a[4];
      rz = a[5];
      fov = a[6];
    }

    public void clear() {
      x = y = z = rx = ry = rz = fov = 0d;
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

    public float fovDelta() {
      return (float) fov;
    }
  }

  public static final class Channel implements AutoCloseable {
    private CameraPresentation3D p;
    private final int i;
    private final long g;

    private Channel(CameraPresentation3D p, int i, long g) {
      this.p = p;
      this.i = i;
      this.g = g;
    }

    public GameObject gameObject() {
      return require().getGameObject();
    }

    public boolean isValid() {
      CameraPresentation3D current = p;
      return current != null && current.matches(i, g) && current.slots[i].channel;
    }

    private CameraPresentation3D require() {
      if (!isValid()) throw new IllegalStateException("Camera channel is stale");
      return p;
    }

    public float get(int property) {
      return require().read(i, g, property);
    }

    public void set(int property, float v) {
      require().write(i, g, property, v);
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

    public float getFovDelta() {
      return get(FOV_DELTA);
    }

    public void setFovDelta(float v) {
      set(FOV_DELTA, v);
    }

    public void setPose(float x, float y, float z, float rx, float ry, float rz, float fov) {
      require().writeAll(i, g, new double[] {x, y, z, rx, ry, rz, fov});
    }

    public void close() {
      if (p != null) {
        p.release(i, g);
        p = null;
      }
    }

    private void invalidate() {
      p = null;
    }
  }

  public static final class Lease implements AutoCloseable {
    private CameraPresentation3D p;
    private final int i;
    private final long g;

    private Lease(CameraPresentation3D p, int i, long g) {
      this.p = p;
      this.i = i;
      this.g = g;
    }

    public boolean isActive() {
      return p != null && p.matches(i, g);
    }

    public Lease pause() {
      require().pause(i, g, true);
      return this;
    }

    public Lease resume() {
      require().pause(i, g, false);
      return this;
    }

    public void set(float x, float y, float z, float rx, float ry, float rz, float fov) {
      require().writeAll(i, g, new double[] {x, y, z, rx, ry, rz, fov});
    }

    public void close() {
      if (p != null) {
        p.release(i, g);
        p = null;
      }
    }

    private void invalidate() {
      p = null;
    }

    private CameraPresentation3D require() {
      if (!isActive()) throw new IllegalStateException("Camera lease is stale");
      return p;
    }
  }

  private static final class Slot {
    boolean active, paused, channel;
    long generation;
    Object owner;
    final double[] values = new double[7];
  }
}
