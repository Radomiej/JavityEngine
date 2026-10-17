package org.javity.ui.controls;

import java.util.*;

/** GL-free world-space minimap data. Coordinates use Y up, independently of screen input. */
public final class MinimapModel {
  public record Marker(String id, float x, float y, int rgba, float size) {}

  private final float x, y, width, height;
  private final Map<String, Marker> markers = new LinkedHashMap<>();

  public MinimapModel(float x, float y, float width, float height) {
    finite(x);
    finite(y);
    finite(width);
    finite(height);
    finite(x + width);
    finite(y + height);
    if (width <= 0 || height <= 0)
      throw new IllegalArgumentException("Positive world dimensions required");
    this.x = x;
    this.y = y;
    this.width = width;
    this.height = height;
  }

  public float width() {
    return width;
  }

  public float height() {
    return height;
  }

  public float normalizedX(float value) {
    finite(value);
    return Math.max(0, Math.min(1, (value - x) / width));
  }

  public float normalizedY(float value) {
    finite(value);
    return Math.max(0, Math.min(1, (value - y) / height));
  }

  public void put(String id, float x, float y, int rgba, float size) {
    Objects.requireNonNull(id, "id");
    finite(x);
    finite(y);
    finite(size);
    if (id.isBlank() || size <= 0)
      throw new IllegalArgumentException("Marker id and positive size required");
    markers.put(id, new Marker(id, x, y, rgba, size));
  }

  public void remove(String id) {
    markers.remove(id);
  }

  public List<Marker> markers() {
    return List.copyOf(markers.values());
  }

  private static void finite(float value) {
    if (!Float.isFinite(value)) throw new IllegalArgumentException("Finite coordinates required");
  }
}
