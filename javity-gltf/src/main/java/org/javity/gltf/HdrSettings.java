package org.javity.gltf;

import java.util.Objects;

/** Immutable world exposure and output operator. Exposure is a positive linear multiplier. */
public record HdrSettings(float exposure, ToneMapper toneMapper) {
  public enum ToneMapper {
    ACES,
    REINHARD
  }

  public static final HdrSettings DEFAULT = new HdrSettings(1, ToneMapper.ACES);

  public HdrSettings {
    if (!Float.isFinite(exposure) || exposure <= 0 || exposure > 64)
      throw new IllegalArgumentException("HDR exposure must be finite and in (0,64]");
    Objects.requireNonNull(toneMapper, "toneMapper");
  }
}
