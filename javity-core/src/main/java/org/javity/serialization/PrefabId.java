package org.javity.serialization;

import java.util.Objects;

/** Stable catalog key for a prefab definition. */
public record PrefabId(String value) {
  public PrefabId {
    Objects.requireNonNull(value, "value");
    if (value.isBlank() || !value.equals(value.trim()))
      throw new IllegalArgumentException("Prefab ID must be non-blank and trimmed");
  }
}
