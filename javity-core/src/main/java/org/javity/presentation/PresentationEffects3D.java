package org.javity.presentation;

import java.util.Objects;
import org.javity.core.Component;
import org.javity.core.ComponentChange;
import org.javity.effects.Clock;
import org.javity.effects.EffectSystem;
import org.javity.effects.Feedback;

/**
 * Bounded, render-only 3D presentation presets. Position offsets use local world units, scale
 * offsets are multiplier deltas, rotations and bob phases use radians, time and duration use
 * seconds, frequency uses hertz, and FOV deltas use degrees. Punch/recoil arguments are initial
 * velocities (units/second, scale-delta/second or radians/second); spring arguments are target
 * offsets. Every playback owns one additive provider slot; gameplay transforms and camera authored
 * state are never written. Attach a manager beside the matching presentation providers and use the
 * same WORLD/UI clock on its FeedbackPlayer. Completion callbacks use the scene EffectSystem;
 * register that system before Game.start, as required by FeedbackPlayer. Disabling this manager
 * holds its last provider sample; re-enabling resumes its clock. Disabling a provider or owner
 * invalidates the corresponding playback.
 */
public final class PresentationEffects3D extends Component {
  public static final int MAX_ACTIVE_EFFECTS = 64;
  private static final double MAX_DURATION = 3600;
  private static final double MAX_MAGNITUDE = 1_000_000;
  private static final double MIN_HZ = .001;
  private static final double MAX_HZ = 1000;
  private static final double MAX_DAMPING = 100;

  public enum Surface {
    MODEL,
    WORLD_CAMERA,
    VIEWMODEL_CAMERA
  }

  public enum Kind {
    PUNCH_POSITION,
    PUNCH_SCALE,
    PUNCH_ANGULAR,
    SPRING_POSITION,
    SHAKE,
    RECOIL,
    TIME_BOB,
    DISTANCE_BOB,
    FOV_KICK,
    FOV_SPRING
  }

  /** Immutable parameters for one bounded playback. Values use world units, radians and seconds. */
  public static final class Preset {
    private final Kind kind;
    private final Surface surface;
    private final double[] vector;
    private final double duration, frequency, damping, phase;
    private final long seed;

    private Preset(
        Kind kind,
        Surface surface,
        double[] vector,
        double duration,
        double frequency,
        double damping,
        double phase,
        long seed) {
      this.kind = kind;
      this.surface = surface;
      this.vector = vector.clone();
      this.duration = duration;
      this.frequency = frequency;
      this.damping = damping;
      this.phase = phase;
      this.seed = seed;
    }

    public Kind kind() {
      return kind;
    }

    public Surface surface() {
      return surface;
    }

    public double durationSeconds() {
      return duration;
    }
  }

  private static final class Slot {
    long generation, eligibleFrame;
    boolean active, paused;
    Playback playback;
  }

  private final Clock clock;
  private final Slot[] slots = new Slot[MAX_ACTIVE_EFFECTS];
  private long nextGeneration;
  private boolean released;
  private Presentation3D model;
  private CameraPresentation3D worldCamera, viewmodelCamera;

  public PresentationEffects3D(Clock clock) {
    this.clock = Objects.requireNonNull(clock, "clock");
    updateMode = clock == Clock.UI ? UpdateMode.UI : UpdateMode.WORLD;
    for (int i = 0; i < slots.length; i++) slots[i] = new Slot();
  }

  public PresentationEffects3D() {
    this(Clock.WORLD);
  }

  public Clock clock() {
    return clock;
  }

  public static Preset punchPosition(
      double x, double y, double z, double hz, double damping, double duration) {
    return punch(Kind.PUNCH_POSITION, Surface.MODEL, new double[] {x, y, z}, hz, damping, duration);
  }

  public static Preset punchScale(
      double x, double y, double z, double hz, double damping, double duration) {
    return punch(Kind.PUNCH_SCALE, Surface.MODEL, new double[] {x, y, z}, hz, damping, duration);
  }

  public static Preset punchAngular(
      double rx, double ry, double rz, double hz, double damping, double duration) {
    return punch(
        Kind.PUNCH_ANGULAR, Surface.MODEL, new double[] {rx, ry, rz}, hz, damping, duration);
  }

  private static Preset punch(Kind k, Surface s, double[] v, double hz, double d, double t) {
    vector(v);
    frequency(hz);
    damping(d);
    duration(t);
    return new Preset(k, s, v, t, hz, d, 0, 0);
  }

  /** Position spring starts at zero position and velocity. setGoal changes only its equilibrium. */
  public static Preset springToOffset(
      double x, double y, double z, double hz, double damping, double duration) {
    vector(new double[] {x, y, z});
    frequency(hz);
    damping(damping);
    duration(duration);
    return new Preset(
        Kind.SPRING_POSITION, Surface.MODEL, new double[] {x, y, z}, duration, hz, damping, 0, 0);
  }

  /** Smooth deterministic translation and angular noise, all amplitudes in local units/radians. */
  public static Preset shake(
      Surface surface,
      double x,
      double y,
      double z,
      double rx,
      double ry,
      double rz,
      double duration,
      double frequency,
      long seed) {
    Objects.requireNonNull(surface, "surface");
    double[] v = {x, y, z, rx, ry, rz};
    vector(v);
    duration(duration);
    frequency(frequency);
    return new Preset(Kind.SHAKE, surface, v, duration, frequency, 0, 0, seed);
  }

  /** Independent shot contribution; rapid shots overlap until each finite recoil expires. */
  public static Preset recoil(
      Surface surface,
      double x,
      double y,
      double z,
      double rx,
      double ry,
      double rz,
      double hz,
      double damping,
      double duration) {
    Objects.requireNonNull(surface, "surface");
    double[] v = {x, y, z, rx, ry, rz};
    vector(v);
    frequency(hz);
    damping(damping);
    duration(duration);
    return new Preset(Kind.RECOIL, surface, v, duration, hz, damping, 0, 0);
  }

  /** Time-clock sinusoid. Intensity is normalized [0,1], supplied live with setBobInput. */
  public static Preset timeBob(
      double x, double y, double z, double frequency, double phaseRadians, double duration) {
    return timeBob(Surface.MODEL, x, y, z, frequency, phaseRadians, duration);
  }

  public static Preset timeBob(
      Surface surface,
      double x,
      double y,
      double z,
      double frequency,
      double phaseRadians,
      double duration) {
    Objects.requireNonNull(surface, "surface");
    double[] v = {x, y, z};
    vector(v);
    frequency(frequency);
    finite(phaseRadians, "phase");
    duration(duration);
    return new Preset(Kind.TIME_BOB, surface, v, duration, frequency, 0, phaseRadians, 0);
  }

  /** Distance phase is supplied in radians from the caller's movement abstraction. */
  public static Preset distanceBob(double x, double y, double z, double duration) {
    return distanceBob(Surface.MODEL, x, y, z, duration);
  }

  public static Preset distanceBob(Surface surface, double x, double y, double z, double duration) {
    Objects.requireNonNull(surface, "surface");
    double[] v = {x, y, z};
    vector(v);
    duration(duration);
    return new Preset(Kind.DISTANCE_BOB, surface, v, duration, 0, 0, 0, 0);
  }

  /** Camera FOV delta impulse in degrees, applied around the live authored base FOV. */
  public static Preset fovKick(
      Surface camera, double deltaDegrees, double hz, double damping, double duration) {
    Objects.requireNonNull(camera, "camera");
    if (camera == Surface.MODEL)
      throw new IllegalArgumentException("FOV requires a camera surface");
    magnitude(deltaDegrees, "FOV impulse");
    frequency(hz);
    damping(damping);
    duration(duration);
    return new Preset(
        Kind.FOV_KICK, camera, new double[] {deltaDegrees}, duration, hz, damping, 0, 0);
  }

  /** Springs the presentation FOV delta in degrees around the camera's live authored FOV. */
  public static Preset fovSpring(
      Surface camera, double targetDeltaDegrees, double hz, double damping, double duration) {
    Objects.requireNonNull(camera, "camera");
    if (camera == Surface.MODEL)
      throw new IllegalArgumentException("FOV requires a camera surface");
    magnitude(targetDeltaDegrees, "FOV target");
    frequency(hz);
    damping(damping);
    duration(duration);
    return new Preset(
        Kind.FOV_SPRING, camera, new double[] {targetDeltaDegrees}, duration, hz, damping, 0, 0);
  }

  /**
   * Feedback adapter that lazily acquires a handle on the owner's manager. Configure that manager
   * and the FeedbackPlayer with the same Clock so the resource lifetime and samples advance
   * together.
   */
  public static Feedback feedback(org.javity.core.GameObject owner, Preset preset) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(preset, "preset");
    return Feedback.resource(
        preset.duration,
        () -> {
          PresentationEffects3D manager = owner.getComponent(PresentationEffects3D.class);
          if (manager == null)
            throw new IllegalStateException("Feedback owner needs PresentationEffects3D");
          return manager.play(preset);
        });
  }

  public int activeCount() {
    reconcile();
    int n = 0;
    for (Slot s : slots) if (s.active) n++;
    return n;
  }

  public Playback play(Preset preset) {
    Objects.requireNonNull(preset, "preset");
    live();
    reconcile();
    int index = -1;
    for (int i = 0; i < slots.length; i++)
      if (!slots[i].active) {
        index = i;
        break;
      }
    if (index < 0) throw new IllegalStateException("PresentationEffects3D capacity of 64 reached");
    Slot s = slots[index];
    s.generation = ++nextGeneration;
    s.active = true;
    s.paused = false;
    s.eligibleFrame = getGame().getFrameIndex() + 1;
    Playback p = new Playback(this, index, s.generation, preset);
    s.playback = p;
    try {
      p.acquire();
      return p;
    } catch (RuntimeException | Error failure) {
      finish(index, s.generation, p, false);
      throw failure;
    }
  }

  @Override
  public void onCreate() {
    reconcile();
  }

  @Override
  public void onComponentChange(ComponentChange change) {
    if (change.type() == ComponentChange.Type.REMOVED
        && (change.component() == model
            || change.component() == worldCamera
            || change.component() == viewmodelCamera)) {
      invalidateAll();
      model = null;
      worldCamera = null;
      viewmodelCamera = null;
    }
  }

  @Override
  public void onUpdate(float delta) {
    if (released || !(delta > 0) || getGame() == null || getGame().isDisposed()) return;
    long frame = getGame().getFrameIndex();
    Throwable failure = null;
    for (int i = 0; i < slots.length; i++) {
      Slot s = slots[i];
      Playback p = s.playback;
      if (!s.active || p == null || s.paused || frame < s.eligibleFrame) continue;
      try {
        p.advance(delta);
      } catch (RuntimeException | Error thrown) {
        try {
          finish(i, s.generation, p, false);
        } catch (RuntimeException | Error cleanup) {
          thrown.addSuppressed(cleanup);
        }
        if (failure == null) failure = thrown;
        else failure.addSuppressed(thrown);
      }
    }
    if (failure instanceof RuntimeException runtime) throw runtime;
    if (failure instanceof Error error) throw error;
  }

  @Override
  protected void onRelease() {
    released = true;
    invalidateAll();
    model = null;
    worldCamera = null;
    viewmodelCamera = null;
  }

  private void reconcile() {
    var owner = getGameObject();
    if (owner == null
        || released
        || !owner.containsComponent(this)
        || owner.isDestroyed()
        || !owner.isActive()) {
      invalidateAll();
      model = null;
      worldCamera = null;
      viewmodelCamera = null;
      return;
    }
    Presentation3D m = owner.getComponent(Presentation3D.class);
    CameraPresentation3D w = findCamera(CameraPresentation3D.Space.WORLD);
    CameraPresentation3D v = findCamera(CameraPresentation3D.Space.VIEWMODEL);
    if (m != model || w != worldCamera || v != viewmodelCamera) {
      invalidateAll();
      model = m;
      worldCamera = w;
      viewmodelCamera = v;
    }
    for (int i = 0; i < slots.length; i++) {
      Slot slot = slots[i];
      Playback playback = slot.playback;
      if (slot.active && playback != null && !playback.hasLiveLease())
        finish(i, slot.generation, playback, false);
    }
  }

  private CameraPresentation3D findCamera(CameraPresentation3D.Space space) {
    for (CameraPresentation3D c : getGameObject().getComponents(CameraPresentation3D.class))
      if (c.space() == space) return c;
    return null;
  }

  private void invalidateAll() {
    for (int i = 0; i < slots.length; i++) {
      Slot slot = slots[i];
      if (slot.active) finish(i, slot.generation, slot.playback, false);
    }
  }

  private boolean callbackOwnerValid() {
    var owner = getGameObject();
    var game = getGame();
    return !released
        && owner != null
        && game != null
        && !game.isDisposed()
        && !owner.isDestroyed()
        && owner.isActive()
        && owner.containsComponent(this);
  }

  private void dispatchCompletion(Runnable callback) {
    if (!callbackOwnerValid()) return;
    EffectSystem system = getGame().getSystem(EffectSystem.class);
    if (system == null)
      throw new IllegalStateException(
          "PresentationEffects3D callbacks require EffectSystem registered before Game.start");
    system.dispatchCallback(this::callbackOwnerValid, callback);
  }

  private void finish(int i, long generation, Playback expected, boolean natural) {
    Slot s = slots[i];
    if (!s.active || s.generation != generation || s.playback != expected) return;
    Playback p = s.playback;
    s.active = false;
    s.paused = false;
    s.playback = null;
    if (p != null) p.releaseLease();
    if (p != null) p.ended(natural);
  }

  private boolean active(int i, long g) {
    reconcile();
    return i >= 0 && i < slots.length && slots[i].active && slots[i].generation == g;
  }

  private void pause(int i, long g, boolean p) {
    if (active(i, g)) slots[i].paused = p;
  }

  private void cancel(int i, long g) {
    if (active(i, g)) {
      Slot slot = slots[i];
      finish(i, g, slot.playback, false);
    }
  }

  private void live() {
    if (released
        || getGameObject() == null
        || getGame() == null
        || getGame().isDisposed()
        || getGameObject().isDestroyed()
        || !getGameObject().getComponents().contains(this))
      throw new IllegalStateException("PresentationEffects3D owner is unavailable");
  }

  /** Generation-safe playback handle. Spring goal and bob inputs can change while it is active. */
  public static final class Playback implements AutoCloseable {
    private PresentationEffects3D manager;
    private final PresentationEffects3D callbackManager;
    private final int index;
    private final long generation;
    private final Preset preset;
    private Presentation3D.Lease modelLease;
    private CameraPresentation3D.Lease cameraLease;
    private final double[] springScratch = new double[6];
    private double age, x, y, z, vx, vy, vz, intensity = 1, distancePhase;
    private double targetIntensity = 1;
    private boolean completed;
    private Runnable onComplete;

    private Playback(PresentationEffects3D m, int i, long g, Preset p) {
      manager = m;
      callbackManager = m;
      index = i;
      generation = g;
      preset = p;
    }

    private void acquire() {
      if (preset.surface == Surface.MODEL) {
        if (manager.model == null)
          throw new IllegalStateException("PresentationEffects3D requires Presentation3D on owner");
        modelLease = manager.model.contribute();
      } else {
        CameraPresentation3D c =
            preset.surface == Surface.WORLD_CAMERA ? manager.worldCamera : manager.viewmodelCamera;
        if (c == null)
          throw new IllegalStateException(
              "PresentationEffects3D requires matching CameraPresentation3D on owner");
        cameraLease = c.contribute();
      }
      if (preset.kind == Kind.SPRING_POSITION) {
        presetGoal[0] = preset.vector[0];
        presetGoal[1] = preset.vector[1];
        presetGoal[2] = preset.vector[2];
      }
      if (preset.kind == Kind.FOV_SPRING) presetGoal[0] = preset.vector[0];
    }

    public boolean isActive() {
      return manager != null && manager.active(index, generation);
    }

    public boolean isPaused() {
      return isActive() && manager.slots[index].paused;
    }

    public Playback pause() {
      require();
      manager.pause(index, generation, true);
      return this;
    }

    public Playback resume() {
      require();
      manager.pause(index, generation, false);
      return this;
    }

    public void cancel() {
      if (manager != null) manager.cancel(index, generation);
    }

    public Playback onComplete(Runnable callback) {
      Objects.requireNonNull(callback, "callback");
      if (completed) callbackManager.dispatchCompletion(callback);
      else if (isActive()) onComplete = callback;
      return this;
    }

    /** Updates the live intensity of a time bob; it eases to the new value over 100 ms. */
    public Playback setTimeBobIntensity(double normalizedIntensity) {
      require();
      if (preset.kind != Kind.TIME_BOB && preset.kind != Kind.DISTANCE_BOB)
        throw new IllegalStateException("not a bob playback");
      if (!Double.isFinite(normalizedIntensity)
          || normalizedIntensity < 0
          || normalizedIntensity > 1)
        throw new IllegalArgumentException("intensity must be in [0,1]");
      if (preset.kind != Kind.TIME_BOB)
        throw new IllegalStateException("distance bob requires setDistanceBobInput");
      targetIntensity = normalizedIntensity;
      return this;
    }

    /** Supplies normalized motion intensity and distance phase in radians from the caller. */
    public Playback setDistanceBobInput(double normalizedIntensity, double phaseRadians) {
      require();
      if (preset.kind != Kind.DISTANCE_BOB)
        throw new IllegalStateException("not a distance bob playback");
      if (!Double.isFinite(normalizedIntensity)
          || normalizedIntensity < 0
          || normalizedIntensity > 1)
        throw new IllegalArgumentException("intensity must be in [0,1]");
      finite(phaseRadians, "distance phase");
      targetIntensity = normalizedIntensity;
      distancePhase = phaseRadians;
      return this;
    }

    /** Changes the spring equilibrium without changing the sampled position or velocity. */
    public Playback setGoal(double x, double y, double z) {
      require();
      if (preset.kind != Kind.SPRING_POSITION)
        throw new IllegalStateException("not a spring playback");
      magnitude(x, "goal x");
      magnitude(y, "goal y");
      magnitude(z, "goal z");
      presetGoal[0] = x;
      presetGoal[1] = y;
      presetGoal[2] = z;
      return this;
    }

    /** Changes a camera FOV spring's target delta in degrees without resetting its state. */
    public Playback setFovGoal(double deltaDegrees) {
      require();
      if (preset.kind != Kind.FOV_SPRING)
        throw new IllegalStateException("not an FOV spring playback");
      magnitude(deltaDegrees, "FOV goal");
      presetGoal[0] = deltaDegrees;
      return this;
    }

    private final double[] presetGoal = new double[3];

    private boolean hasLiveLease() {
      return preset.surface == Surface.MODEL
          ? modelLease != null && modelLease.isActive()
          : cameraLease != null && cameraLease.isActive();
    }

    private void advance(double dt) {
      if (!isActive()) return;
      age = Math.min(preset.duration, age + dt);
      if (age >= preset.duration) {
        setNeutral();
        manager.finish(index, generation, this, true);
        return;
      }
      double[] v = preset.vector;
      double a = 0, b = 0, c = 0, d = 0, e = 0, f = 0, fov = 0;
      switch (preset.kind) {
        case PUNCH_POSITION -> {
          double r = impulse(age, preset.frequency, preset.damping);
          a = v[0] * r;
          b = v[1] * r;
          c = v[2] * r;
        }
        case PUNCH_SCALE -> {
          double r = impulse(age, preset.frequency, preset.damping);
          d = v[0] * r;
          e = v[1] * r;
          f = v[2] * r;
        }
        case PUNCH_ANGULAR -> {
          double r = impulse(age, preset.frequency, preset.damping);
          a = v[0] * r;
          b = v[1] * r;
          c = v[2] * r;
        }
        case RECOIL -> {
          double r = impulse(age, preset.frequency, preset.damping);
          a = v[0] * r;
          b = v[1] * r;
          c = v[2] * r;
          d = v[3] * r;
          e = v[4] * r;
          f = v[5] * r;
        }
        case FOV_KICK -> fov = v[0] * impulse(age, preset.frequency, preset.damping);
        case FOV_SPRING -> {
          springStep(
              dt, preset.frequency, preset.damping, presetGoal, x, 0, 0, vx, 0, 0, springScratch);
          x = springScratch[0];
          vx = springScratch[3];
          fov = x;
        }
        case SHAKE -> {
          double env = envelope(age / preset.duration), cell = age * preset.frequency;
          long lo = (long) Math.floor(cell);
          double t = cell - lo;
          a = v[0] * noise(preset.seed, lo, 0, t) * env;
          b = v[1] * noise(preset.seed, lo, 1, t) * env;
          c = v[2] * noise(preset.seed, lo, 2, t) * env;
          d = v[3] * noise(preset.seed, lo, 3, t) * env;
          e = v[4] * noise(preset.seed, lo, 4, t) * env;
          f = v[5] * noise(preset.seed, lo, 5, t) * env;
        }
        case SPRING_POSITION -> {
          springStep(
              dt, preset.frequency, preset.damping, presetGoal, x, y, z, vx, vy, vz, springScratch);
          x = springScratch[0];
          y = springScratch[1];
          z = springScratch[2];
          vx = springScratch[3];
          vy = springScratch[4];
          vz = springScratch[5];
          a = x;
          b = y;
          c = z;
        }
        case TIME_BOB -> {
          intensity += (targetIntensity - intensity) * (-Math.expm1(-dt / .1));
          double q = Math.sin(2 * Math.PI * preset.frequency * age + preset.phase) * intensity;
          a = v[0] * q;
          b = v[1] * q;
          c = v[2] * q;
        }
        case DISTANCE_BOB -> {
          intensity += (targetIntensity - intensity) * (-Math.expm1(-dt / .1));
          double q = Math.sin(distancePhase) * intensity;
          a = v[0] * q;
          b = v[1] * q;
          c = v[2] * q;
        }
      }
      set(a, b, c, d, e, f, fov);
    }

    private void set(double x, double y, double z, double rx, double ry, double rz, double fov) {
      if (modelLease != null) {
        float sx = (float) x, sy = (float) y, sz = (float) z;
        if (preset.kind == Kind.PUNCH_SCALE) {
          sx = 0;
          sy = 0;
          sz = 0;
          modelLease.set(0, 0, 0, 0, 0, 0, (float) rx, (float) ry, (float) rz);
        } else if (preset.kind == Kind.PUNCH_ANGULAR) {
          modelLease.set(0, 0, 0, (float) x, (float) y, (float) z, 0, 0, 0);
        } else if (preset.kind == Kind.SHAKE || preset.kind == Kind.RECOIL) {
          modelLease.set(
              (float) x, (float) y, (float) z, (float) rx, (float) ry, (float) rz, 0, 0, 0);
        } else modelLease.set((float) x, (float) y, (float) z, 0, 0, 0, 0, 0, 0);
      } else if (cameraLease != null)
        cameraLease.set(
            (float) x, (float) y, (float) z, (float) rx, (float) ry, (float) rz, (float) fov);
    }

    private void setNeutral() {
      if (modelLease != null) modelLease.set(0, 0, 0, 0, 0, 0, 0, 0, 0);
      else if (cameraLease != null) cameraLease.set(0, 0, 0, 0, 0, 0, 0);
    }

    private void releaseLease() {
      if (modelLease != null) {
        modelLease.close();
        modelLease = null;
      }
      if (cameraLease != null) {
        cameraLease.close();
        cameraLease = null;
      }
    }

    private void ended(boolean natural) {
      manager = null;
      if (natural) {
        completed = true;
        Runnable r = onComplete;
        onComplete = null;
        if (r != null) callbackManager.dispatchCompletion(r);
      } else onComplete = null;
    }

    private void require() {
      if (!isActive()) throw new IllegalStateException("PresentationEffects3D playback is stale");
    }

    @Override
    public void close() {
      cancel();
    }
  }

  private static double impulse(double t, double hz, double zeta) {
    if (t <= 0) return 0;
    double wn = 2 * Math.PI * hz;
    if (zeta < 1) {
      double wd = wn * Math.sqrt(1 - zeta * zeta);
      return Math.exp(-zeta * wn * t) * Math.sin(wd * t) / wd;
    }
    if (zeta == 1) return t * Math.exp(-wn * t);
    double root = Math.sqrt(zeta * zeta - 1), r1 = -wn * (zeta - root), gap = 2 * wn * root;
    return Math.exp(r1 * t) * -Math.expm1(-gap * t) / gap;
  }

  /** Exact constant-equilibrium damped spring update, independently applied to each axis. */
  private static void springStep(
      double dt,
      double hz,
      double zeta,
      double[] goal,
      double px,
      double py,
      double pz,
      double vx,
      double vy,
      double vz,
      double[] out) {
    for (int i = 0; i < 3; i++) {
      double p = i == 0 ? px : i == 1 ? py : pz, vel = i == 0 ? vx : i == 1 ? vy : vz;
      double y = p - goal[i], wn = 2 * Math.PI * hz, nextY, nextV;
      if (zeta < 1) {
        double wd = wn * Math.sqrt(1 - zeta * zeta),
            decay = Math.exp(-zeta * wn * dt),
            sin = Math.sin(wd * dt),
            cos = Math.cos(wd * dt);
        nextY = decay * (y * cos + (vel + zeta * wn * y) * sin / wd);
        nextV = decay * (vel * cos - (zeta * wn * vel + wn * wn * y) * sin / wd);
      } else if (zeta == 1) {
        double c = vel + wn * y, decay = Math.exp(-wn * dt);
        nextY = decay * (y + c * dt);
        nextV = decay * (vel - wn * c * dt);
      } else {
        double root = Math.sqrt(zeta * zeta - 1),
            r1 = -wn * (zeta - root),
            r2 = -wn * (zeta + root);
        double c1 = (vel - r2 * y) / (r1 - r2),
            c2 = y - c1,
            e1 = Math.exp(r1 * dt),
            e2 = Math.exp(r2 * dt);
        nextY = c1 * e1 + c2 * e2;
        nextV = c1 * r1 * e1 + c2 * r2 * e2;
      }
      out[i] = goal[i] + nextY;
      out[i + 3] = nextV;
    }
    for (double n : out)
      if (!Double.isFinite(n)) throw new IllegalArgumentException("spring state became non-finite");
  }

  private static double noise(long seed, long cell, int axis, double t) {
    return lerp(noiseValue(seed, cell, axis), noiseValue(seed, cell + 1, axis), t);
  }

  private static double noiseValue(long seed, long cell, int axis) {
    long v = seed ^ (cell * 0x9E3779B97F4A7C15L) ^ (axis * 0xD1B54A32D192ED03L);
    v = (v ^ (v >>> 30)) * 0xBF58476D1CE4E5B9L;
    v = (v ^ (v >>> 27)) * 0x94D049BB133111EBL;
    v ^= v >>> 31;
    return ((v >>> 11) * 0x1.0p-52) - 1;
  }

  private static double lerp(double a, double b, double t) {
    return a + (b - a) * t;
  }

  private static double envelope(double p) {
    return 1 - p * p * (3 - 2 * p);
  }

  private static void duration(double x) {
    if (!Double.isFinite(x) || x <= 0 || x > MAX_DURATION)
      throw new IllegalArgumentException("duration must be in (0, 3600] seconds");
  }

  private static void frequency(double x) {
    if (!Double.isFinite(x) || x < MIN_HZ || x > MAX_HZ)
      throw new IllegalArgumentException("frequency must be in [.001, 1000] Hz");
  }

  private static void damping(double x) {
    if (!Double.isFinite(x) || x < 0 || x > MAX_DAMPING)
      throw new IllegalArgumentException("damping must be in [0, 100]");
  }

  private static void vector(double[] x) {
    for (int i = 0; i < x.length; i++) magnitude(x[i], "amplitude");
  }

  private static void magnitude(double x, String n) {
    if (!Double.isFinite(x) || Math.abs(x) > MAX_MAGNITUDE)
      throw new IllegalArgumentException(n + " must be finite and <= 1,000,000 in magnitude");
  }

  private static void finite(double x, String n) {
    if (!Double.isFinite(x)) throw new IllegalArgumentException(n + " must be finite");
  }
}
