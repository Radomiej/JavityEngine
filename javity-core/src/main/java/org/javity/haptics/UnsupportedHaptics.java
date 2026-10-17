package org.javity.haptics;

import java.util.Objects;

/** Honest desktop/default backend: requests validate, then report unsupported and do nothing. */
public final class UnsupportedHaptics implements Haptics {
  private static final HapticsCapabilities CAPABILITIES = HapticsCapabilities.unsupported();

  @Override
  public HapticsCapabilities capabilities() {
    return CAPABILITIES;
  }

  @Override
  public HapticHandle play(HapticPulse pulse) {
    Objects.requireNonNull(pulse, "pulse");
    return UnsupportedHandle.INSTANCE;
  }

  @Override
  public HapticHandle play(HapticPattern pattern) {
    Objects.requireNonNull(pattern, "pattern");
    return UnsupportedHandle.INSTANCE;
  }

  private enum UnsupportedHandle implements HapticHandle {
    INSTANCE;

    @Override
    public HapticStatus status() {
      return HapticStatus.UNSUPPORTED;
    }

    @Override
    public boolean isActive() {
      return false;
    }

    @Override
    public void cancel() {}
  }
}
