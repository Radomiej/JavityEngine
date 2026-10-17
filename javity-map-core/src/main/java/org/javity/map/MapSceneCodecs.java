package org.javity.map;

import static org.javity.serialization.SceneJson.*;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.JsonValue;
import java.util.*;
import org.javity.serialization.SceneJson;

/** Geographic components only, no tile textures/provider credentials. */
public final class MapSceneCodecs {
  private MapSceneCodecs() {}

  private static JsonValue coordinate(GeoCoordinate c) {
    var d = object();
    d.addChild("lat", new JsonValue(c.latitude()));
    d.addChild("lon", new JsonValue(c.longitude()));
    return d;
  }

  private static GeoCoordinate coordinate(JsonValue d) {
    return new GeoCoordinate(d.getDouble("lat"), d.getDouble("lon"));
  }

  public static SceneJson install(SceneJson schema) {
    schema.register(
        "mapView",
        MapView.class,
        c -> {
          var d = coordinate(c.getCenter());
          d.addChild("zoom", new JsonValue(c.getZoom()));
          return d;
        },
        d -> new MapView(coordinate(d), d.getInt("zoom")));
    schema.register(
        "mapMarker",
        MapMarker.class,
        c -> {
          var d = coordinate(c.getCoordinate());
          number(d, "size", c.getSize());
          text(d, "color", c.color.toString());
          return d;
        },
        d -> {
          var c = new MapMarker(coordinate(d));
          c.setSize(finite(d, "size", 12));
          c.color.set(Color.valueOf(d.getString("color")));
          return c;
        });
    schema.register(
        "mapRoute",
        MapRoute.class,
        c -> {
          var d = object();
          number(d, "width", c.getWidth());
          text(d, "color", c.color.toString());
          var pts = array();
          for (var p : c.getCoordinates()) pts.addChild(coordinate(p));
          d.addChild("points", pts);
          return d;
        },
        d -> {
          List<GeoCoordinate> pts = new ArrayList<>();
          for (JsonValue p : d.require("points")) pts.add(coordinate(p));
          var c = new MapRoute(pts);
          c.setWidth(finite(d, "width", 3));
          c.color.set(Color.valueOf(d.getString("color")));
          return c;
        });
    return schema;
  }
}
