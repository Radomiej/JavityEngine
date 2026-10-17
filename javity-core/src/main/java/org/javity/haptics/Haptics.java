package org.javity.haptics;

/** Scene or service scoped haptic capability and playback API. */
public interface Haptics extends AutoCloseable {
  HapticsCapabilities capabilities();

  HapticHandle play(HapticPulse pulse);

  HapticHandle play(HapticPattern pattern);

  @Override
  default void close() {}
}
