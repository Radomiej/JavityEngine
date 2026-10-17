package org.javity.particles;

import java.util.Objects;
import org.javity.effects.Feedback;

/** Feedback adapter whose per-play resource owns exactly one burst lease. */
public final class ParticleBurstFeedback3D {
  private ParticleBurstFeedback3D() {}

  public static Feedback burst(ParticleBurstPool3D pool, ParticleBurstSpec3D spec) {
    Objects.requireNonNull(pool, "pool");
    Objects.requireNonNull(spec, "spec");
    return Feedback.resource(
        spec.lifetimeSeconds(),
        () -> {
          ParticleBurstHandle3D handle = pool.burst(spec);
          return handle == null ? () -> {} : handle;
        });
  }
}
