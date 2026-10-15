package org.javity.sequence;

import java.util.List;
import java.util.Objects;

/** An immutable declaration-ordered set of cues. */
public record Track(List<Cue> cues) {
  public Track {
    cues = List.copyOf(Objects.requireNonNull(cues, "cues"));
  }
}
