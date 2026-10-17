package org.javity.effects;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Common state and completion callback support for effect playback handles. */
public abstract class EffectHandle {
  interface CallbackDispatcher {
    boolean ownerValid();

    void dispatch(Runnable callback);

    void dispatchCleanup(Runnable callback);
  }

  public enum State {
    RUNNING,
    COMPLETED,
    CANCELLED
  }

  private State state = State.RUNNING;
  private final List<Runnable> callbacks = new ArrayList<>();
  private CallbackDispatcher dispatcher;

  public final State state() {
    return state;
  }

  public final boolean isRunning() {
    return state == State.RUNNING;
  }

  public final boolean isCompleted() {
    return state == State.COMPLETED;
  }

  public final boolean isCancelled() {
    return state == State.CANCELLED;
  }

  public abstract void cancel(CancelMode mode);

  public final void cancel() {
    cancel(CancelMode.KEEP_CURRENT);
  }

  public EffectHandle onComplete(Runnable callback) {
    Objects.requireNonNull(callback);
    if (state == State.COMPLETED) dispatch(callback);
    else if (state == State.RUNNING) callbacks.add(callback);
    return this;
  }

  final void setCallbackDispatcher(CallbackDispatcher dispatcher) {
    this.dispatcher = Objects.requireNonNull(dispatcher);
  }

  final void complete() {
    if (state != State.RUNNING) return;
    markCompleted();
    notifyCompleted();
  }

  final void markCompleted() {
    if (state == State.RUNNING) state = State.COMPLETED;
  }

  final void notifyCompleted() {
    if (state != State.COMPLETED) return;
    fireCallbacks();
  }

  final void cancelled() {
    if (state != State.RUNNING) return;
    state = State.CANCELLED;
    callbacks.clear();
  }

  final void fail() {
    if (state == State.CANCELLED) return;
    state = State.CANCELLED;
    callbacks.clear();
  }

  private void fireCallbacks() {
    List<Runnable> copy = List.copyOf(callbacks);
    callbacks.clear();
    for (Runnable callback : copy) {
      if (dispatcher != null && !dispatcher.ownerValid()) break;
      dispatch(callback);
      if (dispatcher != null && !dispatcher.ownerValid()) break;
    }
  }

  private void dispatch(Runnable callback) {
    if (dispatcher == null) {
      callback.run();
      return;
    }
    if (!dispatcher.ownerValid()) return;
    try {
      dispatcher.dispatch(callback);
    } catch (RuntimeException | Error failure) {
      fail();
      throw failure;
    }
  }

  final void dispatchCleanup(Runnable callback) {
    if (dispatcher == null) {
      callback.run();
      return;
    }
    dispatcher.dispatchCleanup(callback);
  }
}
