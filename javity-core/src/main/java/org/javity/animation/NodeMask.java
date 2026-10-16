package org.javity.animation;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Immutable node selection by exact paths/ids, descendant subtree, or set composition. */
public final class NodeMask {
  enum Kind {
    EXACT,
    SUBTREE,
    UNION,
    DIFFERENCE
  }

  private final Kind kind;
  private final String[] values;
  private final boolean paths;
  private final NodeMask[] operands;

  private NodeMask(Kind kind, String[] values, boolean paths, NodeMask[] operands) {
    this.kind = kind;
    this.values = values;
    this.paths = paths;
    this.operands = operands;
  }

  private static NodeMask selectors(Kind kind, String[] raw, boolean paths) {
    if (raw.length == 0) throw new IllegalArgumentException("A node mask cannot be empty");
    Set<String> unique = new LinkedHashSet<>();
    for (String value : raw) {
      if (value == null || value.isBlank())
        throw new IllegalArgumentException("Mask value is required");
      String normalized = value.trim();
      if (paths
          && (normalized.startsWith("/") || normalized.endsWith("/") || normalized.contains("//")))
        throw new IllegalArgumentException("Node paths must be relative full paths: " + normalized);
      if (!unique.add(normalized))
        throw new IllegalArgumentException("Duplicate node mask value: " + normalized);
    }
    return new NodeMask(kind, unique.toArray(String[]::new), paths, null);
  }

  /** Selects only the exact full paths, preserving the original NodeMask.paths contract. */
  public static NodeMask paths(String... paths) {
    return selectors(Kind.EXACT, Objects.requireNonNull(paths).clone(), true);
  }

  /** Selects only nodes whose ids are unique in the target model. */
  public static NodeMask ids(String... ids) {
    return selectors(Kind.EXACT, Objects.requireNonNull(ids).clone(), false);
  }

  /** Selects each named node and all descendants reached by Node child identity. */
  public static NodeMask subtreePaths(String... paths) {
    return selectors(Kind.SUBTREE, Objects.requireNonNull(paths).clone(), true);
  }

  /** Selects each uniquely identified node and all descendants reached by Node child identity. */
  public static NodeMask subtreeIds(String... ids) {
    return selectors(Kind.SUBTREE, Objects.requireNonNull(ids).clone(), false);
  }

  /** Selects the union of all supplied immutable masks. */
  public static NodeMask union(NodeMask... masks) {
    return expression(Kind.UNION, masks);
  }

  /** Selects the included mask minus the excluded mask. An empty result is valid. */
  public static NodeMask difference(NodeMask include, NodeMask exclude) {
    return new NodeMask(
        Kind.DIFFERENCE,
        null,
        false,
        new NodeMask[] {Objects.requireNonNull(include), Objects.requireNonNull(exclude)});
  }

  private static NodeMask expression(Kind kind, NodeMask[] masks) {
    Objects.requireNonNull(masks);
    if (masks.length == 0) throw new IllegalArgumentException("At least one node mask is required");
    NodeMask[] copy = masks.clone();
    for (NodeMask mask : copy) Objects.requireNonNull(mask, "mask");
    return new NodeMask(kind, null, false, copy);
  }

  Kind kind() {
    return kind;
  }

  String[] values() {
    return values == null ? null : values.clone();
  }

  boolean usesPaths() {
    return paths;
  }

  NodeMask[] operands() {
    return operands == null ? null : operands.clone();
  }

  @Override
  public String toString() {
    return switch (kind) {
      case EXACT -> (paths ? "NodeMask.paths" : "NodeMask.ids") + Arrays.toString(values);
      case SUBTREE ->
          (paths ? "NodeMask.subtreePaths" : "NodeMask.subtreeIds") + Arrays.toString(values);
      case UNION -> "NodeMask.union" + Arrays.toString(operands);
      case DIFFERENCE -> "NodeMask.difference(" + operands[0] + ", " + operands[1] + ")";
    };
  }
}
