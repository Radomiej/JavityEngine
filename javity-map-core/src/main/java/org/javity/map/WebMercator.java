package org.javity.map;

/** Global pixel coordinates: origin northwest; X east, Y south. */
public final class WebMercator {
  public static final int TILE_SIZE = 256, MAX_ZOOM = 22;
  public static final double MAX_LATITUDE = 85.0511287798066;

  private WebMercator() {}

  public static double worldSize(int zoom) {
    validateZoom(zoom);
    return (double) TILE_SIZE * (1L << zoom);
  }

  public static void validateZoom(int zoom) {
    if (zoom < 0 || zoom > MAX_ZOOM) throw new IllegalArgumentException("Zoom must be 0..22");
  }

  static double wrap(double value, double size) {
    return ((value % size) + size) % size;
  }

  public static double x(GeoCoordinate p, int zoom) {
    return (p.longitude() + 180) / 360 * worldSize(zoom);
  }

  public static double y(GeoCoordinate p, int zoom) {
    double sin = Math.sin(Math.toRadians(p.latitude()));
    return Math.max(
        0,
        Math.min(
            worldSize(zoom),
            (.5 - Math.log((1 + sin) / (1 - sin)) / (4 * Math.PI)) * worldSize(zoom)));
  }

  public static GeoCoordinate coordinate(double x, double y, int zoom) {
    if (!Double.isFinite(x) || !Double.isFinite(y))
      throw new IllegalArgumentException("Non-finite pixel coordinate");
    double size = worldSize(zoom);
    y = Math.max(0, Math.min(size, y));
    return new GeoCoordinate(
        Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1 - 2 * y / size)))),
        wrap(x, size) / size * 360 - 180);
  }
}
