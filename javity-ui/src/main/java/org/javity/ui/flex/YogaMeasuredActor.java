package org.javity.ui.flex;

import java.util.List;

/** A leaf actor whose content size is measured directly by Yoga. */
public interface YogaMeasuredActor {
  record Size(float width, float height, List<String> lines) {
    public Size(float width, float height) {
      this(width, height, List.of());
    }

    public Size {
      lines = List.copyOf(lines);
    }
  }

  Size measureForYoga(float width, int widthMode);

  default Size measureForContentWidth(float width) {
    return measureForYoga(width, 2);
  }
}
