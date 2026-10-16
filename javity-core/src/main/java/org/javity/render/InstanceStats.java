package org.javity.render;

/** Immutable counters for one completed world view; uploadBytes counts actual GPU uploads. */
public record InstanceStats(
    long submitted,
    long visible,
    long culled,
    long instanced,
    long fallback,
    long groups,
    long draws,
    long uploadBytes) {
  public static final InstanceStats EMPTY = new InstanceStats(0, 0, 0, 0, 0, 0, 0, 0);

  public InstanceStats {
    if (submitted < 0
        || visible < 0
        || culled < 0
        || instanced < 0
        || fallback < 0
        || groups < 0
        || draws < 0
        || uploadBytes < 0) throw new IllegalArgumentException("Negative instance counter");
  }
}
