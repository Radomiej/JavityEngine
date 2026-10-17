package org.javity.map;

import com.badlogic.gdx.graphics.Color;
import java.util.Objects;
import org.javity.core.Component;

/** Square geographic marker, size in screen pixels. */
public final class MapMarker extends Component {
  private GeoCoordinate coordinate;
  private float size = 12;
  public final Color color = new Color(Color.CYAN);

  public MapMarker(GeoCoordinate coordinate) {
    setCoordinate(coordinate);
  }

  public GeoCoordinate getCoordinate() {
    return coordinate;
  }

  public void setCoordinate(GeoCoordinate coordinate) {
    this.coordinate = Objects.requireNonNull(coordinate);
  }

  public float getSize() {
    return size;
  }

  public void setSize(float size) {
    if (!Float.isFinite(size) || size <= 0)
      throw new IllegalArgumentException("Invalid marker size");
    this.size = size;
  }
}
