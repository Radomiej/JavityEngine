package org.javity.effects;

import java.util.*;

/** Shared private playback engine used by TweenPlayer and FeedbackPlayer. */
final class TimelineRunner {
  interface Node {
    Result advance(double delta, Execution execution);

    default void close(Execution execution) {}
  }

  record Result(double consumed, boolean done) {}

  private final EffectSystem system;
  private final java.util.function.BooleanSupplier ownerValid;
  private final List<Execution> executions = new ArrayList<>();
  private boolean released;

  TimelineRunner(EffectSystem system, java.util.function.BooleanSupplier ownerValid) {
    this.system = system;
    this.ownerValid = ownerValid;
  }

  EffectSystem system() {
    return system;
  }

  Execution add(Node node, EffectHandle handle, long eligibleFrame) {
    if (released) throw new IllegalStateException("Effect player has been released");
    Execution execution = new Execution(node, handle, eligibleFrame);
    executions.add(execution);
    return execution;
  }

  void update(float delta) {
    Throwable failure = null;
    for (Execution execution : List.copyOf(executions)) {
      if (!execution.handle.isRunning() || system.frame() < execution.eligibleFrame) continue;
      try {
        if (!ownerValid.getAsBoolean()) execution.cancel(CancelMode.KEEP_CURRENT);
        else {
          Result result = execution.node.advance(delta, execution);
          if (!ownerValid.getAsBoolean()) execution.cancel(CancelMode.KEEP_CURRENT);
          else if (result.done() && execution.handle.isRunning()) execution.finish();
        }
      } catch (RuntimeException | Error next) {
        failure = appendFailure(failure, next);
        try {
          execution.cancel(CancelMode.KEEP_CURRENT);
        } catch (RuntimeException | Error cleanupFailure) {
          failure = appendFailure(failure, cleanupFailure);
        }
      }
    }
    executions.removeIf(execution -> !execution.handle.isRunning());
    rethrow(failure);
  }

  void cancelAll() {
    Throwable failure = null;
    for (Execution execution : List.copyOf(executions)) {
      try {
        execution.cancel(CancelMode.KEEP_CURRENT);
      } catch (RuntimeException | Error next) {
        failure = appendFailure(failure, next);
      }
    }
    executions.clear();
    rethrow(failure);
  }

  void release() {
    released = true;
    cancelAll();
  }

  static Node tween(FloatTarget target, float to, double duration, Ease ease) {
    return new TweenNode(target, to, duration, ease);
  }

  static void validateDuration(double duration) {
    if (!Double.isFinite(duration) || duration < 0)
      throw new IllegalArgumentException("Duration must be finite and non-negative");
  }

  final class Execution implements EffectSystem.Playback {
    private final Node node;
    private final EffectHandle handle;
    private final long eligibleFrame;
    private final List<ActiveTween> tweens = new ArrayList<>();
    private boolean cancelling;
    private boolean finishing;

    Execution(Node node, EffectHandle handle, long eligibleFrame) {
      this.node = node;
      this.handle = handle;
      this.eligibleFrame = eligibleFrame;
      handle.setCallbackDispatcher(
          new EffectHandle.CallbackDispatcher() {
            @Override
            public boolean ownerValid() {
              return TimelineRunner.this.ownerValid.getAsBoolean();
            }

            @Override
            public void dispatch(Runnable callback) {
              Execution.this.dispatch(callback);
            }

            @Override
            public void dispatchCleanup(Runnable callback) {
              Execution.this.cleanup(callback::run);
            }
          });
    }

    EffectSystem system() {
      return TimelineRunner.this.system;
    }

    boolean isRunning() {
      return handle.isRunning();
    }

    @Override
    public void superseded() {
      cancel(CancelMode.KEEP_CURRENT);
    }

    void cancel(CancelMode mode) {
      if (!handle.isRunning() || cancelling || finishing) return;
      Objects.requireNonNull(mode, "mode");
      cancelling = true;
      // Terminal state is visible before any user-owned resource cleanup can re-enter us.
      handle.cancelled();
      Throwable failure = null;
      try {
        for (ActiveTween tween : List.copyOf(tweens)) {
          try {
            tween.cancel(mode);
          } catch (RuntimeException | Error next) {
            failure = appendFailure(failure, next);
          }
        }
        tweens.clear();
        try {
          closeNode(node);
        } catch (RuntimeException | Error next) {
          failure = appendFailure(failure, next);
        }
      } finally {
        cancelling = false;
      }
      rethrow(failure);
    }

    void finish() {
      if (!handle.isRunning() || finishing) return;
      finishing = true;
      Throwable failure = null;
      for (ActiveTween tween : List.copyOf(tweens)) {
        try {
          tween.finish();
        } catch (RuntimeException | Error next) {
          failure = appendFailure(failure, next);
        }
      }
      tweens.clear();
      handle.markCompleted();
      try {
        closeNode(node);
      } catch (RuntimeException | Error next) {
        failure = appendFailure(failure, next);
      }
      if (failure == null) handle.notifyCompleted();
      else handle.fail();
      finishing = false;
      rethrow(failure);
    }

    void dispatch(Runnable callback) {
      system.enterCallback();
      try {
        callback.run();
        if (!ownerValid.getAsBoolean()) cancel(CancelMode.KEEP_CURRENT);
      } catch (RuntimeException | Error failure) {
        try {
          cancel(CancelMode.KEEP_CURRENT);
        } catch (RuntimeException | Error cleanupFailure) {
          appendFailure(failure, cleanupFailure);
        }
        throw failure;
      } finally {
        system.leaveCallback();
      }
    }

    <T> T invoke(java.util.concurrent.Callable<T> callback) {
      final Object[] value = new Object[1];
      dispatch(
          () -> {
            try {
              value[0] = callback.call();
            } catch (RuntimeException | Error failure) {
              throw failure;
            } catch (Exception failure) {
              throw new IllegalStateException("Feedback callback failed", failure);
            }
          });
      @SuppressWarnings("unchecked")
      T result = (T) value[0];
      return result;
    }

    void invoke(Runnable callback) {
      dispatch(callback);
    }

    void cleanup(ThrowingRunnable callback) {
      system.enterCleanupCallback();
      try {
        callback.run();
      } catch (RuntimeException | Error failure) {
        throw failure;
      } catch (Exception failure) {
        throw new IllegalStateException("Feedback cleanup failed", failure);
      } finally {
        system.leaveCallback();
      }
    }

    void closeNode(Node node) {
      node.close(this);
    }

    ActiveTween beginTween(FloatTarget target, float destination) {
      if (!handle.isRunning()) return null;
      if (!target.isValid()) throw new IllegalStateException("Effect target is no longer valid");
      EffectSystem.Lease lease = system.acquire(target, this);
      if (!handle.isRunning()) return null;
      float start;
      try {
        start = target.get();
      } catch (RuntimeException | Error e) {
        system.release(target, lease);
        throw e;
      }
      ActiveTween tween = new ActiveTween(this, target, start, destination, lease);
      tweens.add(tween);
      return tween;
    }

    void callback(Runnable callback) {
      dispatch(callback);
    }
  }

  @FunctionalInterface
  interface ThrowingRunnable {
    void run() throws Exception;
  }

  static Throwable appendFailure(Throwable primary, Throwable next) {
    if (primary == null) return next;
    if (primary != next) primary.addSuppressed(next);
    return primary;
  }

  static void rethrow(Throwable failure) {
    if (failure instanceof RuntimeException runtime) throw runtime;
    if (failure instanceof Error error) throw error;
  }

  private static final class ActiveTween {
    private final Execution execution;
    private final FloatTarget target;
    private final float start, destination;
    private final EffectSystem.Lease lease;
    private boolean active = true;

    ActiveTween(
        Execution execution,
        FloatTarget target,
        float start,
        float destination,
        EffectSystem.Lease lease) {
      this.execution = execution;
      this.target = target;
      this.start = start;
      this.destination = destination;
      this.lease = lease;
    }

    boolean write(float value) {
      if (!active || !execution.system().isCurrent(target, lease) || !target.isValid()) {
        finish();
        return false;
      }
      target.set(value);
      return true;
    }

    void cancel(CancelMode mode) {
      if (!active) return;
      Throwable failure = null;
      if (mode == CancelMode.RESTORE_START
          && target.isValid()
          && execution.system().isCurrent(target, lease)) {
        try {
          target.set(start);
        } catch (RuntimeException | Error next) {
          failure = next;
        }
      }
      try {
        finish();
      } catch (RuntimeException | Error next) {
        failure = appendFailure(failure, next);
      }
      rethrow(failure);
    }

    void finish() {
      if (!active) return;
      active = false;
      execution.system().release(target, lease);
      execution.tweens.remove(this);
    }
  }

  private static final class TweenNode implements Node {
    private final FloatTarget target;
    private final float destination;
    private final double duration;
    private final Ease ease;
    private ActiveTween active;
    private double elapsed;

    TweenNode(FloatTarget target, float destination, double duration, Ease ease) {
      this.target = target;
      this.destination = destination;
      this.duration = duration;
      this.ease = ease;
    }

    @Override
    public Result advance(double delta, Execution execution) {
      if (!target.isValid()) {
        execution.cancel(CancelMode.KEEP_CURRENT);
        return new Result(0, true);
      }
      if (active == null) {
        active = execution.beginTween(target, destination);
        if (active == null) return new Result(0, true);
      }
      if (duration == 0) {
        active.write(destination);
        active.finish();
        return new Result(0, true);
      }
      double consumed = Math.min(delta, duration - elapsed);
      elapsed += consumed;
      if (elapsed >= duration) active.write(destination);
      else {
        double amount = ease.apply(elapsed / duration);
        double value = (double) active.start + ((double) destination - active.start) * amount;
        if (!Double.isFinite(value) || !Float.isFinite((float) value))
          throw new IllegalArgumentException("Interpolated value is not finite");
        active.write((float) value);
      }
      if (elapsed >= duration || !active.active) {
        active.finish();
        return new Result(consumed, true);
      }
      return new Result(consumed, false);
    }
  }
}
