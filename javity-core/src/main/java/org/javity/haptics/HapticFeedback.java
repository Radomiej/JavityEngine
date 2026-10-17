package org.javity.haptics;

import java.util.Objects;
import org.javity.effects.Feedback;

/** Feedback descriptions that own and close one haptic playback handle per eligible play. */
public final class HapticFeedback {
  private HapticFeedback() {}

  public static Feedback pulse(Haptics haptics, HapticPulse pulse) {
    Objects.requireNonNull(haptics, "haptics");
    Objects.requireNonNull(pulse, "pulse");
    return Feedback.resource(
        pulse.durationSeconds(),
        () -> Objects.requireNonNull(haptics.play(pulse), "haptic handle"));
  }

  public static Feedback pattern(Haptics haptics, HapticPattern pattern) {
    Objects.requireNonNull(haptics, "haptics");
    Objects.requireNonNull(pattern, "pattern");
    return Feedback.resource(
        pattern.totalDurationSeconds(),
        () -> Objects.requireNonNull(haptics.play(pattern), "haptic handle"));
  }
}
