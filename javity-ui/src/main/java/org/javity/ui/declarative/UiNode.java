package org.javity.ui.declarative;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable description of one native UI node. */
public record UiNode(
    String type, String key, String classes, Map<String, Object> props, List<UiNode> children) {
  public UiNode {
    Objects.requireNonNull(type, "type");
    if (key == null || key.isEmpty())
      throw new IllegalArgumentException("UI node key must be nonempty");
    classes = classes == null ? "" : classes;
    props = props == null ? Map.of() : Map.copyOf(props);
    children = children == null ? List.of() : List.copyOf(children);
  }

  public static UiNode element(
      String type, String key, String classes, Map<String, Object> props, List<UiNode> children) {
    return new UiNode(type, key, classes, props, children);
  }
}
