package org.javity.services;

import java.util.ArrayList;
import java.util.List;
import org.javity.core.GameSystem;

/** Scene-local stacked time scales, applied once at the start of each game step. */
public final class TimeManager extends GameSystem {
  private final List<TimeLease> leases = new ArrayList<>();
  private double baseScale = 1;
  private double appliedScale = 1;
  private long nextSequence;

  public void setBaseScale(double scale) {
    validateScale(scale);
    baseScale = scale;
  }

  public double getBaseScale() {
    return baseScale;
  }

  /** Scale captured for the most recently resolved step (or 1 before the first step). */
  public double getScale() {
    return appliedScale;
  }

  /** Adds a newest-wins override which ages on accepted, unscaled game time. */
  public TimeLease push(double scale, double unscaledDurationSeconds) {
    if (isDisposed()) throw new IllegalStateException("Time manager disposed");
    validateScale(scale);
    if (!Double.isFinite(unscaledDurationSeconds) || unscaledDurationSeconds < 0)
      throw new IllegalArgumentException("Invalid time lease duration");
    long frame = getGame().getFrameIndex();
    TimeLease lease =
        new TimeLease(this, nextSequence++, frame + 1, scale, unscaledDurationSeconds);
    if (lease.isActive()) leases.add(lease);
    return lease;
  }

  public TimeLease freeze(double unscaledDurationSeconds) {
    return push(0, unscaledDurationSeconds);
  }

  /**
   * Resolves one frame snapshot. The winning lease is chosen before time is deducted, so a lease
   * that reaches zero during this call still governs this frame.
   */
  public void resolveScaleForStep(double unscaledDelta) {
    if (!Double.isFinite(unscaledDelta) || unscaledDelta < 0)
      throw new IllegalArgumentException("Invalid unscaled delta");
    long frame = getGame().getFrameIndex();
    TimeLease winner = null;
    for (TimeLease lease : leases) {
      if (lease.isActive()
          && lease.eligibleFrame <= frame
          && (winner == null || lease.sequence > winner.sequence)) winner = lease;
    }
    appliedScale = winner == null ? baseScale : winner.scale;
    for (TimeLease lease : List.copyOf(leases)) {
      if (!lease.isActive() || lease.eligibleFrame > frame) continue;
      lease.remaining -= unscaledDelta;
      if (lease.remaining <= 0) lease.deactivate();
    }
    leases.removeIf(lease -> !lease.isActive());
  }

  void cancel(TimeLease lease) {
    if (lease == null || !lease.isActive()) return;
    lease.deactivate();
    leases.remove(lease);
  }

  @Override
  protected void onRelease() {
    for (TimeLease lease : leases) lease.deactivate();
    leases.clear();
  }

  private static void validateScale(double scale) {
    if (!Double.isFinite(scale) || scale < 0 || scale > 4)
      throw new IllegalArgumentException("Time scale must be finite and in [0, 4]");
  }
}
