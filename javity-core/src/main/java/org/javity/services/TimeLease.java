package org.javity.services;

/** A cancellable, scene-owned temporary time-scale override. */
public final class TimeLease implements AutoCloseable {
  private final TimeManager manager;
  final long sequence;
  final long eligibleFrame;
  final double scale;
  double remaining;
  private boolean active;

  TimeLease(TimeManager manager, long sequence, long eligibleFrame, double scale, double duration) {
    this.manager = manager;
    this.sequence = sequence;
    this.eligibleFrame = eligibleFrame;
    this.scale = scale;
    this.remaining = duration;
    this.active = duration > 0;
  }

  /** Cancels this lease. Repeated calls have no effect. */
  public void cancel() {
    manager.cancel(this);
  }

  /** Returns whether this lease can still affect a future frame. */
  public boolean isActive() {
    return active;
  }

  @Override
  public void close() {
    cancel();
  }

  void deactivate() {
    active = false;
  }
}
