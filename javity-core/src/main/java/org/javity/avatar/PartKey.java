package org.javity.avatar;

import java.util.Objects;

/** Stable selector for one NodePart: full node path plus ordinal within that node. */
public record PartKey(String nodePath, int partIndex) {
  public PartKey {
    if (nodePath == null
        || nodePath.isBlank()
        || nodePath.startsWith("/")
        || nodePath.endsWith("/")
        || nodePath.contains("//"))
      throw new IllegalArgumentException("Part nodePath must be a relative full path");
    nodePath = Objects.requireNonNull(nodePath).trim();
    if (partIndex < 0) throw new IllegalArgumentException("partIndex must be non-negative");
  }
}
