package org.javity.map;

/** Latitude/longitude in degrees. Immutable, normalized to Web Mercator bounds. */
public record GeoCoordinate(double latitude, double longitude) {
  public GeoCoordinate {
    if (!Double.isFinite(latitude) || !Double.isFinite(longitude))
      throw new IllegalArgumentException("Non-finite coordinate");
    latitude = Math.max(-WebMercator.MAX_LATITUDE, Math.min(WebMercator.MAX_LATITUDE, latitude));
    longitude = WebMercator.wrap(longitude + 180, 360) - 180;
  }
}
