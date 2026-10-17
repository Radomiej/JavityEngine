package org.javity.map;

import com.badlogic.gdx.math.Vector2;
import java.util.*;
import org.javity.core.Component;

/** Full-viewport map camera. Screen pixels have their origin at bottom left. */
public final class MapView extends Component {
  public record VisibleTile(TileKey key, float screenX, float screenY) {}

  private GeoCoordinate center;
  private int zoom, width = 1, height = 1;

  public MapView(GeoCoordinate center, int zoom) {
    setCenter(center);
    setZoom(zoom);
  }

  public GeoCoordinate getCenter() {
    return center;
  }

  public int getZoom() {
    return zoom;
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  public void setCenter(GeoCoordinate center) {
    this.center = Objects.requireNonNull(center);
  }

  public void setZoom(int zoom) {
    WebMercator.validateZoom(zoom);
    this.zoom = zoom;
  }

  public void resize(int width, int height) {
    if (width <= 0 || height <= 0) throw new IllegalArgumentException("Positive viewport required");
    this.width = width;
    this.height = height;
  }

  public GeoCoordinate coordinateAt(double screenX, double screenY) {
    return WebMercator.coordinate(
        WebMercator.x(center, zoom) + screenX - width / 2.0,
        WebMercator.y(center, zoom) - screenY + height / 2.0,
        zoom);
  }

  public Vector2 screenPosition(GeoCoordinate coordinate) {
    double size = WebMercator.worldSize(zoom);
    double dx =
        WebMercator.wrap(
                WebMercator.x(coordinate, zoom) - WebMercator.x(center, zoom) + size / 2, size)
            - size / 2;
    return new Vector2(
        (float) (width / 2.0 + dx),
        (float) (height / 2.0 - WebMercator.y(coordinate, zoom) + WebMercator.y(center, zoom)));
  }

  /** Move map content by screen pixels, e.g. pointer drag delta. */
  public void pan(double dx, double dy) {
    if (!Double.isFinite(dx) || !Double.isFinite(dy))
      throw new IllegalArgumentException("Invalid pan");
    center =
        WebMercator.coordinate(
            WebMercator.x(center, zoom) - dx, WebMercator.y(center, zoom) + dy, zoom);
  }

  /** Preserve geographic position under cursor, except where pole bounds prevent it. */
  public void zoomAt(int zoom, double screenX, double screenY) {
    GeoCoordinate anchor = coordinateAt(screenX, screenY);
    setZoom(zoom);
    center =
        WebMercator.coordinate(
            WebMercator.x(anchor, zoom) - screenX + width / 2.0,
            WebMercator.y(anchor, zoom) + screenY - height / 2.0,
            zoom);
  }

  public List<VisibleTile> visibleTiles() {
    double left = WebMercator.x(center, zoom) - width / 2.0,
        top = WebMercator.y(center, zoom) - height / 2.0;
    int n = 1 << zoom;
    long firstX = (long) Math.floor(left / 256), lastX = (long) Math.ceil((left + width) / 256) - 1;
    int firstY = Math.max(0, (int) Math.floor(top / 256)),
        lastY = Math.min(n - 1, (int) Math.ceil((top + height) / 256) - 1);
    List<VisibleTile> tiles = new ArrayList<>();
    for (int y = firstY; y <= lastY; y++)
      for (long x = firstX; x <= lastX; x++)
        tiles.add(
            new VisibleTile(
                new TileKey(zoom, (int) Math.floorMod(x, n), y),
                (float) (x * 256 - left),
                (float) (height - ((y + 1) * 256 - top))));
    return List.copyOf(tiles);
  }
}
