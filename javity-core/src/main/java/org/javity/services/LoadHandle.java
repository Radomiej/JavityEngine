package org.javity.services;

import java.util.Objects;
import java.util.function.Consumer;

/** Request state and cancellation handle. Late callback registration does not replay events. */
public final class LoadHandle {
  private final Runnable cancelAction;
  private LoadState state = LoadState.LOADING;
  private double progress;
  private Throwable error;
  private Runnable completeCallback;
  private Consumer<Throwable> failureCallback;

  LoadHandle(Runnable cancelAction) {
    this.cancelAction = Objects.requireNonNull(cancelAction);
  }

  public LoadState state() {
    return state;
  }

  /** Monotonic request progress in the inclusive range 0..1. */
  public double progress() {
    return progress;
  }

  public Throwable error() {
    return error;
  }

  public boolean isDone() {
    return state.isDone();
  }

  public void cancel() {
    if (!isDone()) cancelAction.run();
  }

  /** Registers one callback for successful completion; terminal events are not replayed. */
  public LoadHandle onComplete(Runnable callback) {
    completeCallback = Objects.requireNonNull(callback);
    return this;
  }

  /** Registers one callback for failure; terminal events are not replayed. */
  public LoadHandle onFailure(Consumer<Throwable> callback) {
    failureCallback = Objects.requireNonNull(callback);
    return this;
  }

  void setState(LoadState state) {
    if (!isDone()) this.state = state;
  }

  void advance(double progress) {
    if (Double.isFinite(progress))
      this.progress = Math.max(this.progress, Math.max(0, Math.min(1, progress)));
  }

  void complete() {
    if (isDone()) return;
    state = LoadState.COMPLETED;
    advance(1);
    if (completeCallback != null) completeCallback.run();
  }

  void fail(Throwable error) {
    if (isDone()) return;
    this.error = Objects.requireNonNull(error);
    state = LoadState.FAILED;
    if (failureCallback != null) failureCallback.accept(error);
  }

  void cancelTerminal() {
    if (isDone()) return;
    state = LoadState.CANCELLED;
  }
}
