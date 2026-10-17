package org.javity.sequence;

import java.util.Objects;

/** An action scheduled at a beat within a sequence's duration. */
public record Cue(double beat, Runnable action) {
  public Cue {
    if (!Double.isFinite(beat) || beat < 0)
      throw new IllegalArgumentException("Cue beat must be finite and non-negative");
    Objects.requireNonNull(action, "action");
  }
}
