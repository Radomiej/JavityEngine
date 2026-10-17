package org.javity.services;

import com.badlogic.gdx.assets.AssetManager;
import java.util.Objects;

/** Typed declaration of one scene asset to queue. */
public record AssetSpec<T>(String path, Class<T> type) {
  public AssetSpec {
    Objects.requireNonNull(path);
    Objects.requireNonNull(type);
    if (path.isBlank()) throw new IllegalArgumentException("Asset path cannot be blank");
  }

  void queue(AssetManager assets) {
    if (!assets.isLoaded(path, type) && !assets.isLoaded(path)) assets.load(path, type);
  }
}
