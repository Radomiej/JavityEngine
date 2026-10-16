package org.javity.haptics;

import java.util.List;
import java.util.Objects;

/** Immutable bounded sequence of pulses and optional zero-intensity gaps. */
public record HapticPattern(List<HapticPulse> pulses) {
  public HapticPattern {
    Objects.requireNonNull(pulses, "pulses");
    if (pulses.isEmpty() || pulses.size() > HapticsLimits.MAX_PATTERN_PULSES)
      throw new IllegalArgumentException(
          "Pattern must contain 1.." + HapticsLimits.MAX_PATTERN_PULSES + " pulses");
    pulses = List.copyOf(pulses);
    double total = 0;
    for (HapticPulse pulse : pulses) {
      total += Objects.requireNonNull(pulse, "pulse").durationSeconds();
      if (!Double.isFinite(total) || total > HapticsLimits.MAX_TOTAL_DURATION_SECONDS)
        throw new IllegalArgumentException("Pattern duration exceeds the supported limit");
    }
  }

  public double totalDurationSeconds() {
    return pulses.stream().mapToDouble(HapticPulse::durationSeconds).sum();
  }
}
