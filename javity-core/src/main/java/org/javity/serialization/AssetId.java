package org.javity.serialization;

import java.util.Objects;

/** Stable, typed identifier for an application-owned asset. */
public record AssetId<T>(String value) {
  public AssetId {
    Objects.requireNonNull(value, "value");
    if (value.isBlank() || !value.equals(value.trim()))
      throw new IllegalArgumentException("Asset ID must be non-blank and trimmed");
  }
}
