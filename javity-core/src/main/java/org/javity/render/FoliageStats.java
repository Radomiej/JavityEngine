package org.javity.render;

/** Live generated geometry, latest-view submission counts, and cumulative rebuild diagnostics. */
public record FoliageStats(
    int patches,
    int meshes,
    long vertices,
    long indices,
    long geometryBytes,
    int submitted,
    int visible,
    int culled,
    int draws,
    long builds,
    long buildFailures) {
  public static final FoliageStats EMPTY = new FoliageStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
}
