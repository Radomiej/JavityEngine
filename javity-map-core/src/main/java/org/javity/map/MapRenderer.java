package org.javity.map;

import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.utils.Disposable;
import java.util.Objects;
import org.javity.core.*;

/**
 * Independent 2D map pass. Caller begins Batch with a viewport-sized pixel projection. Owns GPU
 * cache; dispose on the GL thread. No per-tile GameObjects.
 */
public final class MapRenderer implements Disposable, AutoCloseable {
  private record TileTexture(Texture texture) implements AutoCloseable {
    public void close() {
      texture.dispose();
    }
  }

  private final TileSource source;
  private final ResourceCache<TileKey, TileTexture> cache;
  private TextureRegion white;
  private boolean closed;

  public MapRenderer(TileSource source, int capacity) {
    this.source = Objects.requireNonNull(source);
    cache = new ResourceCache<>(capacity);
  }

  public int getCachedTileCount() {
    return cache.size();
  }

  private TileTexture load(TileKey key) {
    Pixmap pixels = source.load(key);
    if (pixels == null) return null;
    try {
      if (pixels.getWidth() != 256 || pixels.getHeight() != 256)
        throw new IllegalArgumentException("Expected 256x256 XYZ tile: " + key);
      Texture texture = new Texture(pixels);
      texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
      return new TileTexture(texture);
    } finally {
      pixels.dispose();
    }
  }

  private TextureRegion white() {
    if (white == null) {
      Pixmap pixels = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
      try {
        pixels.setColor(Color.WHITE);
        pixels.fill();
        white = new TextureRegion(new Texture(pixels));
      } finally {
        pixels.dispose();
      }
    }
    return white;
  }

  private static boolean visible(Component c) {
    return c.isInitialized()
        && c.isEnabled()
        && c.getGameObject().isActive()
        && !c.getGameObject().isDestroyed();
  }

  public void render(Game game, MapView view, Batch batch) {
    if (closed) throw new IllegalStateException("Renderer closed");
    if (view.getGame() != game)
      throw new IllegalArgumentException("MapView belongs to another Game");
    if (!visible(view) || game.isDisposed()) return;
    float original = batch.getPackedColor();
    try {
      batch.setColor(Color.WHITE);
      for (var placement : view.visibleTiles()) {
        // SpriteBatch may still reference a texture evicted by this load.
        if (!cache.contains(placement.key())) batch.flush();
        TileTexture tile = cache.get(placement.key(), () -> load(placement.key()));
        if (tile != null)
          batch.draw(tile.texture(), placement.screenX(), placement.screenY(), 256, 256);
      }
      for (GameObject object : game.getObjectsWith(MapRoute.class))
        for (MapRoute route : object.getComponents(MapRoute.class))
          if (visible(route)) {
            batch.setColor(route.color);
            var points = route.getCoordinates();
            for (int i = 1; i < points.size(); i++) {
              Vector2 a = view.screenPosition(points.get(i - 1)),
                  b = view.screenPosition(points.get(i));
              // Shortest dateline segment rather than a line across the entire world.
              double size = WebMercator.worldSize(view.getZoom());
              b.x = (float) (a.x + WebMercator.wrap(b.x - a.x + size / 2, size) - size / 2);
              float dx = b.x - a.x, dy = b.y - a.y, length = (float) Math.hypot(dx, dy);
              if (length > 0)
                batch.draw(
                    white(),
                    a.x,
                    a.y - route.getWidth() / 2,
                    0,
                    route.getWidth() / 2,
                    length,
                    route.getWidth(),
                    1,
                    1,
                    MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees);
            }
          }
      for (GameObject object : game.getObjectsWith(MapMarker.class))
        for (MapMarker marker : object.getComponents(MapMarker.class))
          if (visible(marker)) {
            Vector2 p = view.screenPosition(marker.getCoordinate());
            float size = marker.getSize();
            batch.setColor(marker.color);
            batch.draw(white(), p.x - size / 2, p.y - size / 2, size, size);
          }
    } finally {
      batch.setPackedColor(original);
    }
  }

  @Override
  public void close() {
    dispose();
  }

  @Override
  public void dispose() {
    if (closed) return;
    closed = true;
    try {
      cache.close();
    } finally {
      if (white != null) white.getTexture().dispose();
    }
  }
}
