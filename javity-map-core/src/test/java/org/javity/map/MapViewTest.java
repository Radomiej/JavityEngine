package org.javity.map;

import static org.junit.Assert.*;

import org.junit.Test;

public class MapViewTest {
  @Test
  public void mercatorRoundTripAndBounds() {
    for (GeoCoordinate p :
        new GeoCoordinate[] {
          new GeoCoordinate(0, 0), new GeoCoordinate(51.4, 21.1), new GeoCoordinate(-80, -179.9)
        }) {
      var q = WebMercator.coordinate(WebMercator.x(p, 15), WebMercator.y(p, 15), 15);
      assertEquals(p.latitude(), q.latitude(), 1e-9);
      assertEquals(p.longitude(), q.longitude(), 1e-9);
    }
    assertEquals(-180, new GeoCoordinate(0, 180).longitude(), 0);
    assertEquals(WebMercator.MAX_LATITUDE, new GeoCoordinate(90, 0).latitude(), 0);
    assertThrows(IllegalArgumentException.class, () -> new GeoCoordinate(Double.NaN, 0));
    assertThrows(IllegalArgumentException.class, () -> new TileKey(2, 4, 0));
  }

  @Test
  public void visibleTilesWrapDatelineAndStopAtPoles() {
    MapView v = new MapView(new GeoCoordinate(0, 179.9), 2);
    v.resize(600, 400);
    assertTrue(v.visibleTiles().stream().anyMatch(t -> t.key().x() == 0));
    assertTrue(v.visibleTiles().stream().anyMatch(t -> t.key().x() == 3));
    for (var t : v.visibleTiles()) {
      assertTrue(t.key().y() >= 0 && t.key().y() < 4);
      assertTrue(t.screenX() < 600 && t.screenX() + 256 > 0);
    }
    v.setCenter(new GeoCoordinate(90, 0));
    assertTrue(v.visibleTiles().stream().allMatch(t -> t.key().y() >= 0));
    v.setZoom(0);
    v.resize(1100, 720);
    assertTrue(v.visibleTiles().size() > 1);
    assertTrue(v.visibleTiles().stream().allMatch(t -> t.key().equals(new TileKey(0, 0, 0))));
  }

  @Test
  public void zoomKeepsCursorCoordinateAndPanUsesPixels() {
    MapView v = new MapView(new GeoCoordinate(51.4, 21.1), 5);
    v.resize(800, 600);
    GeoCoordinate anchor = v.coordinateAt(670, 250);
    v.zoomAt(8, 670, 250);
    var after = v.coordinateAt(670, 250);
    assertEquals(anchor.latitude(), after.latitude(), 1e-9);
    assertEquals(anchor.longitude(), after.longitude(), 1e-9);
    var screen = v.screenPosition(anchor);
    assertEquals(670, screen.x, 1e-3);
    assertEquals(250, screen.y, 1e-3);
    v.pan(100, -50);
    screen = v.screenPosition(anchor);
    assertEquals(770, screen.x, 1e-3);
    assertEquals(200, screen.y, 1e-3);
    assertThrows(IllegalArgumentException.class, () -> v.setZoom(23));
    assertThrows(IllegalArgumentException.class, () -> v.resize(0, 10));
  }

  @Test
  public void datelineMarkerTakesNearestCopy() {
    MapView v = new MapView(new GeoCoordinate(0, 179.9), 5);
    v.resize(800, 600);
    assertTrue(Math.abs(v.screenPosition(new GeoCoordinate(0, -179.9)).x - 400) < 5);
  }

  @Test
  public void routeCopiesCoordinatesAndMissingFilesDoNotLoadNativePixmap() {
    var points = new java.util.ArrayList<GeoCoordinate>();
    points.add(new GeoCoordinate(0, 0));
    var route = new MapRoute(points);
    points.clear();
    assertEquals(1, route.getCoordinates().size());
    assertThrows(UnsupportedOperationException.class, () -> route.getCoordinates().clear());
    var source =
        new FileTileSource(new com.badlogic.gdx.files.FileHandle("build/nonexistent-tiles"));
    assertNull(source.load(new TileKey(0, 0, 0)));
    assertThrows(IllegalArgumentException.class, () -> route.setWidth(0));
    assertThrows(
        IllegalArgumentException.class,
        () -> new MapMarker(new GeoCoordinate(0, 0)).setSize(Float.NaN));
  }

  @Test
  public void geographicCodecPreservesDoublePrecision() {
    var schema = MapSceneCodecs.install(org.javity.serialization.SceneJson.standard());
    try (var a = new org.javity.core.Game();
        var b = new org.javity.core.Game()) {
      var view =
          a.createObject("map")
              .addComponent(new MapView(new GeoCoordinate(51.40271234567, 21.14711234567), 16));
      var copied =
          schema.read(b, schema.write(a.getObjects())).getFirst().getComponent(MapView.class);
      assertEquals(view.getCenter().latitude(), copied.getCenter().latitude(), 1e-12);
      assertEquals(view.getCenter().longitude(), copied.getCenter().longitude(), 1e-12);
    }
  }
}
