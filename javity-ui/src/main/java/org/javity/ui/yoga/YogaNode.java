package org.javity.ui.yoga;

/** Portable, opaque handle to one backend-owned Yoga node. */
public interface YogaNode {
  int MEASURE_UNDEFINED = 0;
  int MEASURE_EXACTLY = 1;
  int MEASURE_AT_MOST = 2;

  enum FlexDirection {
    ROW,
    COLUMN
  }

  enum Align {
    START,
    CENTER,
    END,
    STRETCH
  }

  enum Wrap {
    NO_WRAP,
    WRAP
  }

  enum Justify {
    START,
    CENTER,
    END,
    SPACE_BETWEEN
  }

  enum Edge {
    ALL,
    HORIZONTAL,
    VERTICAL,
    TOP,
    RIGHT,
    BOTTOM,
    LEFT
  }

  enum Gutter {
    ALL,
    COLUMN,
    ROW
  }

  /** Layout-result getters accept individual physical edges, not Yoga's style shorthands. */
  static void requirePhysicalLayoutEdge(Edge edge) {
    if (edge != Edge.TOP && edge != Edge.RIGHT && edge != Edge.BOTTOM && edge != Edge.LEFT)
      throw new IllegalArgumentException("Layout getters require a physical edge: " + edge);
  }

  @FunctionalInterface
  interface MeasureFunction {
    Size measure(float width, int widthMode, float height, int heightMode);
  }

  record Size(float width, float height) {}

  void insertChild(YogaNode child, int index);

  void removeChild(YogaNode child);

  void removeAllChildren();

  void copyStyleFrom(YogaNode source);

  void markDirty();

  /** Callback failures are rethrown by calculateLayout after Yoga returns from native code. */
  void setMeasureFunction(MeasureFunction function);

  void calculateLayout(float width, float height);

  float layoutLeft();

  float layoutTop();

  float layoutWidth();

  float layoutHeight();

  float layoutBorder(Edge edge);

  float layoutPadding(Edge edge);

  float layoutMargin(Edge edge);

  float styleGap(Gutter gutter);

  FlexDirection styleFlexDirection();

  Align styleAlignItems();

  void setFlexDirection(FlexDirection value);

  void setFlexWrap(Wrap value);

  void setFlexGrow(float value);

  void setFlexShrink(float value);

  void setFlexBasis(float value);

  void setAlignItems(Align value);

  void setAlignSelf(Align value);

  void setJustifyContent(Justify value);

  void setGap(Gutter gutter, float value);

  void setBorder(Edge edge, float value);

  void setWidth(float value);

  void setWidthPercent(float value);

  void setWidthAuto();

  void setHeight(float value);

  void setHeightPercent(float value);

  void setHeightAuto();

  void setMinWidth(float value);

  void setMinHeight(float value);

  void setMaxWidth(float value);

  void setMaxHeight(float value);

  void setMargin(Edge edge, float value);

  void setPadding(Edge edge, float value);

  void freeSubtree();

  void freeNode();
}
