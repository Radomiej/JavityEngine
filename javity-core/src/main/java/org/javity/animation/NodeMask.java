package org.javity.animation;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Immutable set of full slash-separated model node paths, or unique node ids. */
public final class NodeMask {
  private final String[] values;
  private final boolean paths;

  private NodeMask(String[] values, boolean paths) {
    if (values.length == 0) throw new IllegalArgumentException("A node mask cannot be empty");
    Set<String> unique = new LinkedHashSet<>();
    for (String value : values) {
      if (value == null || value.isBlank()) throw new IllegalArgumentException("Mask value is required");
      String normalized = value.trim();
      if (paths && (normalized.startsWith("/") || normalized.endsWith("/") || normalized.contains("//")))
        throw new IllegalArgumentException("Node paths must be relative full paths: " + normalized);
      if (!unique.add(normalized)) throw new IllegalArgumentException("Duplicate node mask value: " + normalized);
    }
    this.values = unique.toArray(String[]::new);
    this.paths = paths;
  }

  public static NodeMask paths(String... paths) {
    return new NodeMask(Objects.requireNonNull(paths).clone(), true);
  }

  public static NodeMask ids(String... ids) {
    return new NodeMask(Objects.requireNonNull(ids).clone(), false);
  }

  String[] values() {
    return values;
  }

  boolean usesPaths() {
    return paths;
  }

  @Override
  public String toString() {
    return (paths ? "NodeMask.paths" : "NodeMask.ids") + Arrays.toString(values);
  }
}
