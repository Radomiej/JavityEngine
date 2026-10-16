package org.javity.haptics;

/** One pulse. Intensity is normalized to [0, 1], duration is in seconds. */
public record HapticPulse(float intensity, double durationSeconds) {
  public HapticPulse {
    if (!Float.isFinite(intensity) || intensity < 0 || intensity > 1)
      throw new IllegalArgumentException("Haptic intensity must be finite and in [0, 1]");
    HapticsLimits.validateDuration(durationSeconds, "Pulse duration");
    if (durationSeconds == 0) throw new IllegalArgumentException("Pulse duration must be positive");
  }
}
