package org.javity.animation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable one-dimensional, phase-synchronous model animation mixer. */
public final class LinearModelMixer {
  private final double cycleDuration;
  private final List<Entry> entries;

  private LinearModelMixer(double cycleDuration, List<Entry> entries) {
    this.cycleDuration = cycleDuration;
    this.entries = List.copyOf(entries);
  }

  public static Builder builder(double cycleDuration) {
    return new Builder(cycleDuration);
  }

  public double cycleDuration() {
    return cycleDuration;
  }

  public List<Entry> entries() {
    return entries;
  }

  public static final class Entry {
    private final String animationName;
    private final double threshold;

    private Entry(String animationName, double threshold) {
      this.animationName = animationName;
      this.threshold = threshold;
    }

    public String animationName() {
      return animationName;
    }

    public double threshold() {
      return threshold;
    }
  }

  public static final class Builder {
    private final double cycleDuration;
    private final List<Entry> entries = new ArrayList<>();

    private Builder(double cycleDuration) {
      if (!Double.isFinite(cycleDuration) || cycleDuration <= 0)
        throw new IllegalArgumentException("Cycle duration must be finite and positive");
      this.cycleDuration = cycleDuration;
    }

    public Builder add(String animationName, double threshold) {
      if (animationName == null || animationName.isBlank())
        throw new IllegalArgumentException("Animation name is required");
      if (!Double.isFinite(threshold))
        throw new IllegalArgumentException("Mixer threshold must be finite");
      if (!entries.isEmpty() && threshold <= entries.get(entries.size() - 1).threshold)
        throw new IllegalArgumentException("Mixer thresholds must be strictly ascending");
      for (Entry entry : entries)
        if (entry.animationName.equals(animationName))
          throw new IllegalArgumentException("Duplicate mixer animation: " + animationName);
      entries.add(new Entry(animationName, threshold));
      return this;
    }

    public LinearModelMixer build() {
      if (entries.size() < 2)
        throw new IllegalStateException("A linear model mixer needs at least two entries");
      return new LinearModelMixer(cycleDuration, entries);
    }
  }
}
