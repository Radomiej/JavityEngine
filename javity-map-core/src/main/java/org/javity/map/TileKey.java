package org.javity.map;

/** Canonical XYZ tile key; Y starts at the north pole (not TMS). */
public record TileKey(int zoom, int x, int y) {
  public TileKey {
    WebMercator.validateZoom(zoom);
    int n = 1 << zoom;
    if (x < 0 || x >= n || y < 0 || y >= n)
      throw new IllegalArgumentException("Tile outside world");
  }
}
