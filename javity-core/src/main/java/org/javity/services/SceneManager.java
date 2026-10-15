package org.javity.services;

import java.util.Objects;
import java.util.function.Supplier;
import org.javity.core.Game;

/** Owns Game lifetimes and performs scene switches only outside {@link Game#step(float)}. */
public final class SceneManager implements AutoCloseable {
  private Game current;
  private Supplier<? extends Game> pending;
  private ActiveLoad loading;
  private boolean closed;
  private boolean stepping;
  private long generation;
  private double overlayAlpha;

  public Game current() {
    return current;
  }

  /**
   * Backwards-compatible deferred request. The old scene remains alive until the new one starts.
   */
  public void request(Supplier<? extends Game> factory) {
    ensureOpen();
    pending = Objects.requireNonNull(factory);
    generation++;
    cancelActiveLoad();
  }

  /** Queues an incremental load and returns a handle for state, progress and cancellation. */
  public LoadHandle requestLoading(SceneLoadJob job, SceneTransition transition) {
    ensureOpen();
    Objects.requireNonNull(job);
    Objects.requireNonNull(transition);
    pending = null;
    long token = ++generation;
    LoadHandle handle = new LoadHandle(() -> cancel(token));
    try {
      cancelActiveLoad();
    } catch (RuntimeException | Error failure) {
      try {
        job.close();
      } catch (RuntimeException | Error cleanup) {
        addSuppressedIfDistinct(failure, cleanup);
      }
      throw failure;
    }
    if (closed || token != generation) {
      handle.cancelTerminal();
      job.close();
      return handle;
    }
    loading = new ActiveLoad(token, job, transition, handle);
    overlayAlpha = 0;
    return handle;
  }

  /** Alpha for a root-owned full-screen fade overlay, always between zero and one. */
  public double overlayAlpha() {
    return overlayAlpha;
  }

  public void step(float delta) {
    if (closed) return;
    if (!Float.isFinite(delta) || delta < 0) throw new IllegalArgumentException("Invalid delta");
    if (stepping) throw new IllegalStateException("Recursive scene step");
    stepping = true;
    float unscaledDelta = Math.min(delta, .1f);
    try {
      commitPending();
      updateLoading(unscaledDelta);
      Game scene = current;
      if (!closed && scene != null) scene.step(delta);
      advanceTransition(unscaledDelta);
      commitPending();
    } finally {
      stepping = false;
    }
  }

  private void commitPending() {
    if (closed || pending == null) return;
    Supplier<? extends Game> factory = pending;
    pending = null;
    long token = generation;
    Game next = Objects.requireNonNull(factory.get(), "Scene factory returned null");
    if (next == current) throw new IllegalArgumentException("Fresh Game required");
    if (closed || token != generation) {
      next.dispose();
      return;
    }
    try {
      next.start();
    } catch (RuntimeException | Error failure) {
      disposeAfterFailure(next, failure);
      throw failure;
    }
    if (closed || token != generation) {
      next.dispose();
      return;
    }
    Game old = current;
    current = next;
    if (old != null) old.dispose();
  }

  private void updateLoading(float delta) {
    ActiveLoad active = loading;
    if (closed || active == null || active.token != generation) return;
    if (active.handle.state() == LoadState.LOADING) {
      try {
        active.job.update(delta);
        if (!isActive(active)) return;
        double progress = active.job.progress();
        if (!isActive(active)) return;
        if (!Double.isFinite(progress) || progress < 0 || progress > 1)
          throw new IllegalStateException("Scene load progress must be finite and between 0 and 1");
        active.handle.advance(progress * .7);
        boolean ready = active.job.isReady();
        if (!isActive(active)) return;
        if (ready) {
          active.handle.setState(LoadState.FADING_OUT);
          active.fadeElapsed = 0;
        }
      } catch (RuntimeException | Error failure) {
        if (!isActive(active)) return;
        failLoad(active, failure);
        return;
      }
    }
    if (active.handle.state() == LoadState.FADING_OUT) {
      double duration = active.transition.fadeOutSeconds();
      active.fadeElapsed = Math.min(duration, active.fadeElapsed + delta);
      overlayAlpha = duration == 0 ? 1 : clamp(active.fadeElapsed / duration);
      active.handle.advance(.7 + .15 * overlayAlpha);
    }
  }

  private void advanceTransition(float delta) {
    ActiveLoad active = loading;
    if (closed || active == null || active.token != generation) return;
    if (active.handle.state() == LoadState.FADING_OUT
        && active.fadeElapsed >= active.transition.fadeOutSeconds()) {
      handoff(active);
      return;
    }
    if (active.handle.state() == LoadState.FADING_IN) {
      active.fadeElapsed = Math.min(active.transition.fadeInSeconds(), active.fadeElapsed + delta);
      double duration = active.transition.fadeInSeconds();
      overlayAlpha = duration == 0 ? 0 : clamp(1 - active.fadeElapsed / duration);
      active.handle.advance(.85 + .15 * (1 - overlayAlpha));
      if (active.fadeElapsed >= duration) finishLoad(active);
    }
  }

  private void handoff(ActiveLoad active) {
    if (!isActive(active)) return;
    Game next = null;
    try {
      next = Objects.requireNonNull(active.job.takeScene(), "Load job returned null scene");
      if (next == current) {
        next = null;
        throw new IllegalArgumentException("Fresh Game required");
      }
      closeJobOnce(active);
      if (!isActive(active)) {
        next.dispose();
        return;
      }
      next.start();
      if (!isActive(active)) {
        next.dispose();
        return;
      }
    } catch (RuntimeException | Error failure) {
      if (next != null && next != current) disposeAfterFailure(next, failure);
      failLoad(active, failure);
      return;
    }

    Game old = current;
    current = next;
    active.handle.setState(LoadState.FADING_IN);
    active.fadeElapsed = 0;
    overlayAlpha = 1;
    Throwable cleanupFailure = null;
    if (old != null) {
      try {
        old.dispose();
      } catch (RuntimeException | Error failure) {
        cleanupFailure = failure;
      }
    }
    if (active.transition.fadeInSeconds() == 0) overlayAlpha = 0;
    if (active.transition.fadeInSeconds() == 0) {
      try {
        finishLoad(active);
      } catch (RuntimeException | Error callbackFailure) {
        if (cleanupFailure == null) cleanupFailure = callbackFailure;
        else addSuppressedIfDistinct(cleanupFailure, callbackFailure);
      }
    }
    if (cleanupFailure != null) throwAny(cleanupFailure);
  }

  private void finishLoad(ActiveLoad active) {
    if (!isActive(active)) return;
    loading = null;
    overlayAlpha = 0;
    active.handle.complete();
  }

  private void failLoad(ActiveLoad active, Throwable failure) {
    if (loading != active) return;
    loading = null;
    overlayAlpha = 0;
    try {
      closeJobOnce(active);
    } catch (RuntimeException | Error cleanup) {
      addSuppressedIfDistinct(failure, cleanup);
    }
    try {
      active.handle.fail(failure);
    } catch (RuntimeException | Error callbackFailure) {
      addSuppressedIfDistinct(failure, callbackFailure);
      throwAny(failure);
    }
  }

  private boolean isActive(ActiveLoad active) {
    return !closed && loading == active && active.token == generation;
  }

  private void cancel(long token) {
    ActiveLoad active = loading;
    if (closed || active == null || active.token != token || active.handle.isDone()) return;
    // Once the opaque handoff commits, the old scene is already disposed and cannot be restored.
    if (active.handle.state() == LoadState.FADING_IN) return;
    generation++;
    loading = null;
    overlayAlpha = 0;
    active.handle.cancelTerminal();
    closeJobOnce(active);
  }

  private void cancelActiveLoad() {
    ActiveLoad active = loading;
    if (active == null) return;
    loading = null;
    overlayAlpha = 0;
    active.handle.cancelTerminal();
    closeJobOnce(active);
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    generation++;
    pending = null;
    overlayAlpha = 0;
    ActiveLoad active = loading;
    loading = null;
    Throwable failure = null;
    if (active != null) {
      active.handle.cancelTerminal();
      try {
        closeJobOnce(active);
      } catch (RuntimeException | Error e) {
        failure = e;
      }
    }
    Game old = current;
    current = null;
    if (old != null) {
      try {
        old.dispose();
      } catch (RuntimeException | Error e) {
        failure = combine(failure, e);
      }
    }
    if (failure != null) throwAny(failure);
  }

  private static void disposeAfterFailure(Game scene, Throwable failure) {
    try {
      scene.dispose();
    } catch (RuntimeException | Error cleanup) {
      addSuppressedIfDistinct(failure, cleanup);
    }
  }

  private static void closeJobOnce(ActiveLoad active) {
    if (active.jobClosed) return;
    active.jobClosed = true;
    active.job.close();
  }

  private static Throwable combine(Throwable first, Throwable next) {
    if (first == null) return next;
    addSuppressedIfDistinct(first, next);
    return first;
  }

  private static void addSuppressedIfDistinct(Throwable primary, Throwable secondary) {
    if (primary != secondary) primary.addSuppressed(secondary);
  }

  private static double clamp(double value) {
    return Math.max(0, Math.min(1, value));
  }

  private static void throwAny(Throwable failure) {
    if (failure instanceof RuntimeException e) throw e;
    if (failure instanceof Error e) throw e;
  }

  private void ensureOpen() {
    if (closed) throw new IllegalStateException("Manager closed");
  }

  private static final class ActiveLoad {
    private final long token;
    private final SceneLoadJob job;
    private final SceneTransition transition;
    private final LoadHandle handle;
    private double fadeElapsed;
    private boolean jobClosed;

    private ActiveLoad(
        long token, SceneLoadJob job, SceneTransition transition, LoadHandle handle) {
      this.token = token;
      this.job = job;
      this.transition = transition;
      this.handle = handle;
    }
  }
}
