package org.javity.presentation;

import java.util.Objects;
import org.javity.core.Component;
import org.javity.core.ComponentChange;
import org.javity.effects.Clock;

/** Bounded scene-object shake and analytical damped-impulse presentation effects. */
public final class PresentationEffects extends Component {
  public static final int MAX_ACTIVE_EFFECTS = 64;
  private static final double MAX_DURATION_SECONDS = 3600;
  private static final double MAX_AMPLITUDE = 1_000_000;
  private static final double MAX_FREQUENCY_HZ = 1_000;
  private static final double MIN_FREQUENCY_HZ = .001;
  private static final double MAX_DAMPING_RATIO = 100;

  private enum Kind {
    SHAKE,
    POSITION_PUNCH,
    SCALE_PUNCH
  }

  private static final class Effect {
    long generation;
    long order;
    long eligibleFrame;
    double age, duration;
    double x, y, rotation, frequency, damping;
    long seed;
    Kind kind;
    boolean active, paused;
    PresentationHandle handle;
    int poseIndex = -1;
  }

  private final Effect[] effects = new Effect[MAX_ACTIVE_EFFECTS];
  private final Clock clock;
  private Presentation2D pose;
  private long nextOrder;
  private boolean released;

  public PresentationEffects(Clock clock) {
    this.clock = Objects.requireNonNull(clock, "clock");
    updateMode = clock == Clock.UI ? UpdateMode.UI : UpdateMode.WORLD;
    for (int i = 0; i < effects.length; i++) effects[i] = new Effect();
  }

  /**
   * Adds deterministic smooth random translation/rotation, decaying exactly to zero at duration.
   */
  public PresentationHandle shake(
      float amplitudeX,
      float amplitudeY,
      float rotationAmplitude,
      double duration,
      double frequency,
      long seed) {
    finiteMagnitude(amplitudeX, "amplitudeX");
    finiteMagnitude(amplitudeY, "amplitudeY");
    finiteMagnitude(rotationAmplitude, "rotationAmplitude");
    positiveDuration(duration);
    positiveFrequency(frequency);
    return start(
        Kind.SHAKE, amplitudeX, amplitudeY, rotationAmplitude, duration, frequency, 0, seed);
  }

  /**
   * Applies an exact unit-mass damped-oscillator response to an initial velocity impulse. x and y
   * are impulse velocities in world units per second, not peak offsets.
   */
  public PresentationHandle punchPosition(
      float x, float y, double frequencyHz, double dampingRatio, double duration) {
    finiteMagnitude(x, "x impulse");
    finiteMagnitude(y, "y impulse");
    positiveFrequency(frequencyHz);
    damping(dampingRatio);
    positiveDuration(duration);
    return start(Kind.POSITION_PUNCH, x, y, 0, duration, frequencyHz, dampingRatio, 0);
  }

  /** x and y are initial scale velocities per second; contributions are additive scale deltas. */
  public PresentationHandle punchScale(
      float x, float y, double frequencyHz, double dampingRatio, double duration) {
    finiteMagnitude(x, "x impulse");
    finiteMagnitude(y, "y impulse");
    positiveFrequency(frequencyHz);
    damping(dampingRatio);
    positiveDuration(duration);
    return start(Kind.SCALE_PUNCH, x, y, 0, duration, frequencyHz, dampingRatio, 0);
  }

  public int getActiveCount() {
    reconcilePose();
    int count = 0;
    for (Effect effect : effects) if (effect.active) count++;
    return count;
  }

  @Override
  public void onCreate() {
    ensurePose();
  }

  @Override
  public void onComponentChange(ComponentChange change) {
    if (change.type() == ComponentChange.Type.REMOVED && change.component() == pose)
      poseProviderRemoved(pose);
  }

  @Override
  public void onUpdate(float delta) {
    if (released || delta <= 0 || getGame() == null || getGame().isDisposed()) return;
    long frame = getGame().getFrameIndex();
    for (int i = 0; i < effects.length; i++) {
      Effect effect = effects[i];
      if (!effect.active || effect.paused || frame < effect.eligibleFrame) continue;
      effect.age = Math.min(effect.duration, effect.age + delta);
      if (effect.age >= effect.duration) {
        finish(i, effect, true);
      } else {
        apply(effect, i);
      }
    }
  }

  @Override
  protected void onRelease() {
    released = true;
    for (int i = 0; i < effects.length; i++) finish(i, effects[i], false);
    pose = null;
  }

  boolean isActive(int index, long generation) {
    reconcilePose();
    return index >= 0
        && index < effects.length
        && effects[index].active
        && effects[index].generation == generation;
  }

  void cancel(int index, long generation) {
    if (isActive(index, generation)) finish(index, effects[index], false);
  }

  boolean isPaused(int index, long generation) {
    return isActive(index, generation) && effects[index].paused;
  }

  void setPaused(int index, long generation, boolean paused) {
    if (isActive(index, generation)) effects[index].paused = paused;
  }

  private PresentationHandle start(
      Kind kind,
      double x,
      double y,
      double rotation,
      double duration,
      double frequency,
      double damping,
      long seed) {
    live();
    ensurePose();
    Effect selected = null;
    int index = -1;
    for (int i = 0; i < effects.length; i++) {
      if (!effects[i].active) {
        selected = effects[i];
        index = i;
        break;
      }
    }
    if (selected == null) {
      index = 0;
      for (int i = 1; i < effects.length; i++)
        if (effects[i].order < effects[index].order) index = i;
      selected = effects[index];
      finish(index, selected, false);
    }
    int poseIndex;
    try {
      poseIndex = pose.acquire(this);
    } catch (IllegalStateException full) {
      int oldest = oldestActiveIndex();
      if (oldest < 0) throw full;
      finish(oldest, effects[oldest], false);
      poseIndex = pose.acquire(this);
    }
    selected.generation++;
    selected.order = nextOrder++;
    selected.eligibleFrame = getGame().getFrameIndex() + 1;
    selected.age = 0;
    selected.duration = duration;
    selected.x = x;
    selected.y = y;
    selected.rotation = rotation;
    selected.frequency = frequency;
    selected.damping = damping;
    selected.seed = seed;
    selected.kind = kind;
    selected.active = true;
    selected.paused = false;
    selected.poseIndex = poseIndex;
    PresentationHandle handle = new PresentationHandle(this, index, selected.generation);
    selected.handle = handle;
    apply(selected, index);
    return handle;
  }

  private void ensurePose() {
    reconcilePose();
    if (pose == null) pose = getGameObject().addComponent(new Presentation2D());
  }

  private void reconcilePose() {
    if (getGameObject() == null) return;
    Presentation2D current = getGameObject().getComponent(Presentation2D.class);
    if (current != pose) {
      Presentation2D previous = pose;
      if (previous != null) poseProviderRemoved(previous);
      pose = current;
    }
  }

  void poseProviderRemoved(Presentation2D provider) {
    if (provider == null || pose != provider) return;
    // Invalidate all handles and callbacks tied to the detached provider. In particular, do not
    // deliver natural-expiry callbacks while component removal/scene cleanup is in progress.
    for (int i = 0; i < effects.length; i++) finish(i, effects[i], false, provider);
    pose = null;
  }

  private void live() {
    if (released
        || getGameObject() == null
        || getGameObject().isDestroyed()
        || getGame() == null
        || getGame().isDisposed()
        || !getGameObject().getComponents().contains(this))
      throw new IllegalStateException("PresentationEffects owner is no longer active");
  }

  private void apply(Effect effect, int index) {
    double progress = Math.min(1, effect.age / effect.duration);
    double envelope = 1 - progress * progress * (3 - 2 * progress);
    double px = 0, py = 0, angle = 0, sx = 0, sy = 0;
    if (effect.kind == Kind.SHAKE) {
      double cell = effect.age * effect.frequency;
      long lower = (long) Math.floor(cell);
      double fraction = cell - lower;
      px =
          effect.x * lerp(noise(effect.seed, lower, 0), noise(effect.seed, lower + 1, 0), fraction);
      py =
          effect.y * lerp(noise(effect.seed, lower, 1), noise(effect.seed, lower + 1, 1), fraction);
      angle =
          effect.rotation
              * lerp(noise(effect.seed, lower, 2), noise(effect.seed, lower + 1, 2), fraction);
      px *= envelope;
      py *= envelope;
      angle *= envelope;
    } else {
      double response = impulseResponse(effect.age, effect.frequency, effect.damping);
      if (effect.kind == Kind.POSITION_PUNCH) {
        px = effect.x * response;
        py = effect.y * response;
      } else {
        sx = effect.x * response;
        sy = effect.y * response;
      }
    }
    pose.set(effect.poseIndex, px, py, angle, sx, sy);
  }

  private void finish(int index, Effect effect, boolean natural) {
    finish(index, effect, natural, pose);
  }

  private void finish(int index, Effect effect, boolean natural, Presentation2D provider) {
    if (!effect.active) return;
    PresentationHandle handle = effect.handle;
    effect.active = false;
    effect.age = effect.duration = 0;
    effect.x = effect.y = effect.rotation = effect.frequency = effect.damping = 0;
    effect.seed = 0;
    effect.kind = null;
    effect.paused = false;
    effect.handle = null;
    if (provider != null) provider.release(effect.poseIndex);
    effect.poseIndex = -1;
    if (natural && handle != null) handle.completed();
    else if (handle != null) handle.cancelled();
  }

  private static double impulseResponse(double time, double frequency, double zeta) {
    if (time <= 0) return 0;
    double wn = 2 * Math.PI * frequency;
    if (zeta < 1) {
      double wd = wn * Math.sqrt(1 - zeta * zeta);
      return Math.exp(-zeta * wn * time) * Math.sin(wd * time) / wd;
    }
    if (zeta == 1) return time * Math.exp(-wn * time);
    double root = Math.sqrt(zeta * zeta - 1);
    double r1 = -wn * (zeta - root);
    double gap = 2 * wn * root;
    return Math.exp(r1 * time) * -Math.expm1(-gap * time) / gap;
  }

  private static double noise(long seed, long cell, int axis) {
    long value = seed ^ (cell * 0x9E3779B97F4A7C15L) ^ (axis * 0xD1B54A32D192ED03L);
    value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
    value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
    value ^= value >>> 31;
    return ((value >>> 11) * 0x1.0p-52) - 1.0;
  }

  private static double lerp(double a, double b, double t) {
    return a + (b - a) * t;
  }

  private static void finiteMagnitude(double value, String name) {
    if (!Double.isFinite(value) || Math.abs(value) > MAX_AMPLITUDE)
      throw new IllegalArgumentException(name + " must be finite and bounded");
  }

  private static void positiveFrequency(double value) {
    if (!Double.isFinite(value) || value < MIN_FREQUENCY_HZ || value > MAX_FREQUENCY_HZ)
      throw new IllegalArgumentException("Frequency must be between 0.001 and 1000 Hz");
  }

  private int oldestActiveIndex() {
    int oldest = -1;
    for (int i = 0; i < effects.length; i++)
      if (effects[i].active && (oldest < 0 || effects[i].order < effects[oldest].order)) oldest = i;
    return oldest;
  }

  private static void positiveDuration(double value) {
    if (!Double.isFinite(value) || value <= 0 || value > MAX_DURATION_SECONDS)
      throw new IllegalArgumentException("Duration must be positive and at most one hour");
  }

  private static void damping(double value) {
    if (!Double.isFinite(value) || value < 0 || value > MAX_DAMPING_RATIO)
      throw new IllegalArgumentException("Damping ratio must be between 0 and 100");
  }
}
