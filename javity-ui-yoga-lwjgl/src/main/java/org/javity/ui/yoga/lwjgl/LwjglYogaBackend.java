package org.javity.ui.yoga.lwjgl;

import static org.lwjgl.util.yoga.Yoga.*;

import java.util.ArrayList;
import java.util.List;
import org.javity.ui.yoga.YogaBackend;
import org.javity.ui.yoga.YogaNode;
import org.lwjgl.util.yoga.YGMeasureFunc;
import org.lwjgl.util.yoga.YGSize;

/** Desktop Yoga implementation. Native pointers and callbacks stay inside this module. */
public final class LwjglYogaBackend implements YogaBackend {
  @Override
  public YogaNode createNode() {
    long pointer = YGNodeNew();
    try {
      return new Node(pointer);
    } catch (RuntimeException | Error failure) {
      YGNodeFree(pointer);
      throw failure;
    }
  }

  private static final class Node implements YogaNode {
    final long pointer;
    final List<Node> children = new ArrayList<>();
    YGMeasureFunc measure;
    Throwable measureFailure;
    Node parent;
    boolean closed;

    Node(long pointer) {
      this.pointer = pointer;
    }

    private Node unwrap(YogaNode node) {
      if (!(node instanceof Node other))
        throw new IllegalArgumentException("Yoga node belongs to another backend");
      other.requireOpen();
      return other;
    }

    private void requireOpen() {
      if (closed) throw new IllegalStateException("Yoga node is closed");
    }

    @Override
    public void insertChild(YogaNode child, int index) {
      requireOpen();
      Node other = unwrap(child);
      if (index < 0 || index > children.size()) throw new IndexOutOfBoundsException(index);
      if (other == this) throw new IllegalArgumentException("A Yoga node cannot parent itself");
      if (other.parent != null)
        throw new IllegalStateException("Yoga child must be detached before insertion");
      for (Node ancestor = this; ancestor != null; ancestor = ancestor.parent) {
        if (ancestor == other) throw new IllegalArgumentException("Cyclic Yoga tree");
      }
      if (measure != null)
        throw new IllegalStateException("A measured Yoga node cannot have children");
      YGNodeInsertChild(pointer, other.pointer, index);
      children.add(index, other);
      other.parent = this;
    }

    @Override
    public void removeChild(YogaNode child) {
      requireOpen();
      Node other = unwrap(child);
      if (other.parent != this)
        throw new IllegalArgumentException("Yoga node is not a child of this parent");
      YGNodeRemoveChild(pointer, other.pointer);
      children.remove(other);
      other.parent = null;
    }

    @Override
    public void removeAllChildren() {
      requireOpen();
      YGNodeRemoveAllChildren(pointer);
      for (Node child : children) child.parent = null;
      children.clear();
    }

    @Override
    public void copyStyleFrom(YogaNode source) {
      requireOpen();
      YGNodeCopyStyle(pointer, unwrap(source).pointer);
    }

    @Override
    public void markDirty() {
      requireOpen();
      if (measure == null || !children.isEmpty())
        throw new IllegalStateException("Only measured Yoga leaves may be marked dirty");
      YGNodeMarkDirty(pointer);
    }

    @Override
    public void setMeasureFunction(MeasureFunction function) {
      requireOpen();
      if (function != null && !children.isEmpty())
        throw new IllegalStateException("A Yoga node with children cannot be measured");
      measureFailure = null;
      if (measure != null) {
        YGNodeSetMeasureFunc(pointer, null);
        measure.free();
        measure = null;
      }
      if (function != null) {
        measure =
            YGMeasureFunc.create(
                (long n, float w, int wm, float h, int hm, YGSize out) -> {
                  try {
                    Size size = function.measure(w, wm, h, hm);
                    out.set(size.width(), size.height());
                  } catch (Throwable failure) {
                    if (measureFailure == null) measureFailure = failure;
                    out.set(0, 0);
                  }
                });
        YGNodeSetMeasureFunc(pointer, measure);
      }
    }

    @Override
    public void calculateLayout(float width, float height) {
      requireOpen();
      clearMeasureFailures();
      YGNodeCalculateLayout(pointer, width, height, YGDirectionLTR);
      rethrowMeasureFailures();
    }

    private void clearMeasureFailures() {
      measureFailure = null;
      for (Node child : children) child.clearMeasureFailures();
    }

    private void rethrowMeasureFailures() {
      if (measureFailure instanceof RuntimeException runtime) throw runtime;
      if (measureFailure instanceof Error error) throw error;
      if (measureFailure != null)
        throw new IllegalStateException("Yoga measure callback failed", measureFailure);
      for (Node child : children) child.rethrowMeasureFailures();
    }

    @Override
    public float layoutLeft() {
      requireOpen();
      return YGNodeLayoutGetLeft(pointer);
    }

    @Override
    public float layoutTop() {
      requireOpen();
      return YGNodeLayoutGetTop(pointer);
    }

    @Override
    public float layoutWidth() {
      requireOpen();
      return YGNodeLayoutGetWidth(pointer);
    }

    @Override
    public float layoutHeight() {
      requireOpen();
      return YGNodeLayoutGetHeight(pointer);
    }

    @Override
    public float layoutBorder(Edge e) {
      requireOpen();
      YogaNode.requirePhysicalLayoutEdge(e);
      return YGNodeLayoutGetBorder(pointer, edge(e));
    }

    @Override
    public float layoutPadding(Edge e) {
      requireOpen();
      YogaNode.requirePhysicalLayoutEdge(e);
      return YGNodeLayoutGetPadding(pointer, edge(e));
    }

    @Override
    public float layoutMargin(Edge e) {
      requireOpen();
      YogaNode.requirePhysicalLayoutEdge(e);
      return YGNodeLayoutGetMargin(pointer, edge(e));
    }

    @Override
    public float styleGap(Gutter g) {
      requireOpen();
      return YGNodeStyleGetGap(
          pointer,
          switch (g) {
            case ALL -> YGGutterAll;
            case COLUMN -> YGGutterColumn;
            case ROW -> YGGutterRow;
          });
    }

    @Override
    public FlexDirection styleFlexDirection() {
      requireOpen();
      return YGNodeStyleGetFlexDirection(pointer) == YGFlexDirectionColumn
          ? FlexDirection.COLUMN
          : FlexDirection.ROW;
    }

    @Override
    public Align styleAlignItems() {
      requireOpen();
      return switch (YGNodeStyleGetAlignItems(pointer)) {
        case YGAlignFlexStart -> Align.START;
        case YGAlignCenter -> Align.CENTER;
        case YGAlignFlexEnd -> Align.END;
        default -> Align.STRETCH;
      };
    }

    @Override
    public void setFlexDirection(FlexDirection v) {
      requireOpen();
      YGNodeStyleSetFlexDirection(
          pointer, v == FlexDirection.COLUMN ? YGFlexDirectionColumn : YGFlexDirectionRow);
    }

    @Override
    public void setFlexWrap(Wrap v) {
      requireOpen();
      YGNodeStyleSetFlexWrap(pointer, v == Wrap.WRAP ? YGWrapWrap : YGWrapNoWrap);
    }

    @Override
    public void setFlexGrow(float v) {
      requireOpen();
      YGNodeStyleSetFlexGrow(pointer, v);
    }

    @Override
    public void setFlexShrink(float v) {
      requireOpen();
      YGNodeStyleSetFlexShrink(pointer, v);
    }

    @Override
    public void setFlexBasis(float v) {
      requireOpen();
      YGNodeStyleSetFlexBasis(pointer, v);
    }

    @Override
    public void setAlignItems(Align v) {
      requireOpen();
      YGNodeStyleSetAlignItems(pointer, align(v));
    }

    @Override
    public void setAlignSelf(Align v) {
      requireOpen();
      YGNodeStyleSetAlignSelf(pointer, align(v));
    }

    private static int align(Align v) {
      return switch (v) {
        case START -> YGAlignFlexStart;
        case CENTER -> YGAlignCenter;
        case END -> YGAlignFlexEnd;
        case STRETCH -> YGAlignStretch;
      };
    }

    @Override
    public void setJustifyContent(Justify v) {
      requireOpen();
      YGNodeStyleSetJustifyContent(
          pointer,
          switch (v) {
            case START -> YGJustifyFlexStart;
            case CENTER -> YGJustifyCenter;
            case END -> YGJustifyFlexEnd;
            case SPACE_BETWEEN -> YGJustifySpaceBetween;
          });
    }

    @Override
    public void setGap(Gutter g, float v) {
      requireOpen();
      YGNodeStyleSetGap(
          pointer,
          switch (g) {
            case ALL -> YGGutterAll;
            case COLUMN -> YGGutterColumn;
            case ROW -> YGGutterRow;
          },
          v);
    }

    @Override
    public void setBorder(Edge e, float v) {
      requireOpen();
      YGNodeStyleSetBorder(pointer, edge(e), v);
    }

    @Override
    public void setWidth(float v) {
      requireOpen();
      YGNodeStyleSetWidth(pointer, v);
    }

    @Override
    public void setWidthPercent(float v) {
      requireOpen();
      YGNodeStyleSetWidthPercent(pointer, v);
    }

    @Override
    public void setWidthAuto() {
      requireOpen();
      YGNodeStyleSetWidthAuto(pointer);
    }

    @Override
    public void setHeight(float v) {
      requireOpen();
      YGNodeStyleSetHeight(pointer, v);
    }

    @Override
    public void setHeightPercent(float v) {
      requireOpen();
      YGNodeStyleSetHeightPercent(pointer, v);
    }

    @Override
    public void setHeightAuto() {
      requireOpen();
      YGNodeStyleSetHeightAuto(pointer);
    }

    @Override
    public void setMinWidth(float v) {
      requireOpen();
      YGNodeStyleSetMinWidth(pointer, v);
    }

    @Override
    public void setMinHeight(float v) {
      requireOpen();
      YGNodeStyleSetMinHeight(pointer, v);
    }

    @Override
    public void setMaxWidth(float v) {
      requireOpen();
      YGNodeStyleSetMaxWidth(pointer, v);
    }

    @Override
    public void setMaxHeight(float v) {
      requireOpen();
      YGNodeStyleSetMaxHeight(pointer, v);
    }

    @Override
    public void setMargin(Edge e, float v) {
      requireOpen();
      YGNodeStyleSetMargin(pointer, edge(e), v);
    }

    @Override
    public void setPadding(Edge e, float v) {
      requireOpen();
      YGNodeStyleSetPadding(pointer, edge(e), v);
    }

    private static int edge(Edge e) {
      return switch (e) {
        case ALL -> YGEdgeAll;
        case HORIZONTAL -> YGEdgeHorizontal;
        case VERTICAL -> YGEdgeVertical;
        case TOP -> YGEdgeTop;
        case RIGHT -> YGEdgeRight;
        case BOTTOM -> YGEdgeBottom;
        case LEFT -> YGEdgeLeft;
      };
    }

    @Override
    public void freeSubtree() {
      if (closed) return;
      if (parent != null) throw new IllegalStateException("Detach Yoga subtree before freeing it");
      for (Node child : List.copyOf(children)) {
        removeChild(child);
        child.freeSubtree();
      }
      freeNode();
    }

    @Override
    public void freeNode() {
      if (closed) return;
      if (parent != null) throw new IllegalStateException("Detach Yoga node before freeing it");
      if (!children.isEmpty())
        throw new IllegalStateException("Free Yoga children before their parent");
      try {
        setMeasureFunction(null);
      } finally {
        YGNodeFree(pointer);
        closed = true;
      }
    }
  }
}
