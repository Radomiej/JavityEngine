package org.javity.map;

import com.badlogic.gdx.graphics.Color;
import java.util.*;
import org.javity.core.Component;

/** Polyline through immutable geographic coordinates, width in screen pixels. */
public final class MapRoute extends Component {
  private List<GeoCoordinate> coordinates;
  private float width = 3;
  public final Color color = new Color(Color.GOLD);

  public MapRoute(List<GeoCoordinate> coordinates) {
    setCoordinates(coordinates);
  }

  public List<GeoCoordinate> getCoordinates() {
    return coordinates;
  }

  public void setCoordinates(List<GeoCoordinate> coordinates) {
    this.coordinates = List.copyOf(coordinates);
  }

  public float getWidth() {
    return width;
  }

  public void setWidth(float width) {
    if (!Float.isFinite(width) || width <= 0)
      throw new IllegalArgumentException("Invalid route width");
    this.width = width;
  }
}
