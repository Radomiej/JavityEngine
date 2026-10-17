package org.javity.haptics;

/** Shared request bounds used by every backend, including the unsupported fallback. */
public final class HapticsLimits {
  public static final int MAX_PATTERN_PULSES = 32;
  public static final double MAX_TOTAL_DURATION_SECONDS = 10;

  private HapticsLimits() {}

  static void validateDuration(double durationSeconds, String label) {
    if (!Double.isFinite(durationSeconds)
        || durationSeconds <= 0
        || durationSeconds > MAX_TOTAL_DURATION_SECONDS)
      throw new IllegalArgumentException(
          label + " must be finite and in (0, " + MAX_TOTAL_DURATION_SECONDS + "] seconds");
  }
}
