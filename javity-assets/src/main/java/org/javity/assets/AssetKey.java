package org.javity.assets;

import java.util.Objects;

/** Local/internal path and exact loader type. Slashes follow AssetDescriptor normalization. */
public record AssetKey<T>(String path, Class<T> type) {
  public AssetKey {
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(type, "type");
    if (path.isBlank()) throw new IllegalArgumentException("Empty asset path");
    path = path.replace('\\', '/');
  }
}
