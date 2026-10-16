package org.javity.haptics;

/** Reports only haptic features that the current backend can actually invoke. */
public record HapticsCapabilities(
    boolean available, boolean basicPulse, boolean amplitudeControl, boolean patterns) {
  public HapticsCapabilities {
    if (!available && (basicPulse || amplitudeControl || patterns))
      throw new IllegalArgumentException("Unavailable haptics cannot advertise features");
    if ((amplitudeControl || patterns) && !basicPulse)
      throw new IllegalArgumentException("Amplitude and pattern support require basic pulses");
  }

  public static HapticsCapabilities unsupported() {
    return new HapticsCapabilities(false, false, false, false);
  }
}
