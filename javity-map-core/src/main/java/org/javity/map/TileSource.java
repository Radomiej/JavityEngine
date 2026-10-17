package org.javity.map;

import com.badlogic.gdx.graphics.Pixmap;

/**
 * Called on GL thread on a cache miss. Return owned Pixmap or null if absent. Renderer disposes
 * each returned Pixmap after upload. Source lifetime belongs to caller.
 */
@FunctionalInterface
public interface TileSource {
  Pixmap load(TileKey key);

  default String attribution() {
    return "";
  }
}
