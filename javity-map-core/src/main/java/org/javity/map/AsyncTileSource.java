package org.javity.map;

import com.badlogic.gdx.graphics.Pixmap;

/**
 * Download/cache bytes in workers; decode/upload on GL thread via MapRenderer. Caller owns source.
 */
public final class AsyncTileSource implements TileSource, AutoCloseable {
  private final TileByteCache bytes;

  public AsyncTileSource(TileByteCache bytes) {
    this.bytes = java.util.Objects.requireNonNull(bytes);
  }

  public String attribution() {
    return bytes.attribution();
  }

  public Pixmap load(TileKey key) {
    byte[] data = bytes.poll(key);
    if (data == null) return null;
    try {
      Pixmap pixels = new Pixmap(data, 0, data.length);
      if (pixels.getWidth() != 256 || pixels.getHeight() != 256) {
        pixels.dispose();
        throw new IllegalArgumentException("Expected 256x256 tile");
      }
      return pixels;
    } catch (RuntimeException e) {
      bytes.invalidate(key, "Invalid tile image: " + e.getMessage());
      return null;
    }
  }

  public void close() {
    bytes.close();
  }
}
