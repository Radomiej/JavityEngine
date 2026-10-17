package org.javity.ui.yoga.android;

import android.content.Context;
import com.facebook.soloader.SoLoader;
import com.facebook.yoga.*;
import java.util.ArrayList;
import java.util.List;
import org.javity.ui.yoga.YogaBackend;
import org.javity.ui.yoga.YogaNode;

/** Real Yoga Android JNI adapter. Call initialize before constructing any Flex or document. */
public final class AndroidYogaBackend implements YogaBackend {
  private static volatile boolean initialized;

  public static synchronized void initialize(Context context) {
    if (initialized) return;
    if (context == null) throw new IllegalArgumentException("Android Context is required");
    SoLoader.init(context.getApplicationContext(), false);
    initialized = true;
  }

  public static boolean isInitialized() {
    return initialized;
  }

  @Override
  public YogaNode createNode() {
    if (!initialized)
      throw new IllegalStateException("Initialize Android Yoga with Context before creating UI");
    return new Node();
  }

  private static final class Node implements YogaNode {
    final YogaConfigJNIFinalizer config;
    final YogaNodeJNIFinalizer node;
    final List<Node> children = new ArrayList<>();
    MeasureFunction measure;
    Throwable measureFailure;
    Node parent;
    boolean closed;

    Node() {
      YogaConfigJNIFinalizer allocatedConfig = new YogaConfigJNIFinalizer();
      try {
        allocatedConfig.setUseWebDefaults(false);
        allocatedConfig.setPointScaleFactor(1f);
        allocatedConfig.setErrata(YogaErrata.NONE);
        YogaNodeJNIFinalizer allocatedNode = new YogaNodeJNIFinalizer(allocatedConfig);
        config = allocatedConfig;
        node = allocatedNode;
      } catch (RuntimeException | Error failure) {
        try {
          allocatedConfig.freeNatives();
        } catch (Throwable cleanupFailure) {
          failure.addSuppressed(cleanupFailure);
        }
        throw failure;
      }
    }

    private Node unwrap(YogaNode other) {
      if (!(other instanceof Node result))
        throw new IllegalArgumentException("Yoga node belongs to another backend");
      result.requireOpen();
      return result;
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
      node.addChildAt(other.node, index);
      children.add(index, other);
      other.parent = this;
    }

    @Override
    public void removeChild(YogaNode child) {
      requireOpen();
      Node other = unwrap(child);
      if (other.parent != this)
        throw new IllegalArgumentException("Yoga node is not a child of this parent");
      int index = children.indexOf(other);
      node.removeChildAt(index);
      children.remove(other);
      other.parent = null;
    }

    @Override
    public void removeAllChildren() {
      requireOpen();
      while (!children.isEmpty()) {
        Node child = children.getLast();
        node.removeChildAt(children.size() - 1);
        children.removeLast();
        child.parent = null;
      }
    }

    @Override
    public void copyStyleFrom(YogaNode source) {
      requireOpen();
      node.copyStyle(unwrap(source).node);
    }

    @Override
    public void markDirty() {
      requireOpen();
      if (measure == null || !children.isEmpty())
        throw new IllegalStateException("Only measured Yoga leaves may be marked dirty");
      node.dirty();
    }

    @Override
    public void setMeasureFunction(MeasureFunction function) {
      requireOpen();
      if (function != null && !children.isEmpty())
        throw new IllegalStateException("A Yoga node with children cannot be measured");
      measure = function;
      measureFailure = null;
      node.setMeasureFunction(
          function == null
              ? null
              : (n, width, widthMode, height, heightMode) -> {
                try {
                  Size size =
                      function.measure(width, widthMode.intValue(), height, heightMode.intValue());
                  return YogaMeasureOutput.make(size.width(), size.height());
                } catch (Throwable failure) {
                  if (measureFailure == null) measureFailure = failure;
                  return YogaMeasureOutput.make(0, 0);
                }
              });
    }

    @Override
    public void calculateLayout(float width, float height) {
      requireOpen();
      clearMeasureFailures();
      node.setDirection(YogaDirection.LTR);
      node.calculateLayout(width, height);
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
      return node.getLayoutX();
    }

    @Override
    public float layoutTop() {
      requireOpen();
      return node.getLayoutY();
    }

    @Override
    public float layoutWidth() {
      requireOpen();
      return node.getLayoutWidth();
    }

    @Override
    public float layoutHeight() {
      requireOpen();
      return node.getLayoutHeight();
    }

    @Override
    public float layoutBorder(Edge e) {
      requireOpen();
      YogaNode.requirePhysicalLayoutEdge(e);
      return node.getLayoutBorder(edge(e));
    }

    @Override
    public float layoutPadding(Edge e) {
      requireOpen();
      YogaNode.requirePhysicalLayoutEdge(e);
      return node.getLayoutPadding(edge(e));
    }

    @Override
    public float layoutMargin(Edge e) {
      requireOpen();
      YogaNode.requirePhysicalLayoutEdge(e);
      return node.getLayoutMargin(edge(e));
    }

    @Override
    public float styleGap(Gutter g) {
      requireOpen();
      return node.getGap(
          switch (g) {
            case ALL -> YogaGutter.ALL;
            case COLUMN -> YogaGutter.COLUMN;
            case ROW -> YogaGutter.ROW;
          });
    }

    @Override
    public FlexDirection styleFlexDirection() {
      requireOpen();
      return node.getFlexDirection() == YogaFlexDirection.COLUMN
          ? FlexDirection.COLUMN
          : FlexDirection.ROW;
    }

    @Override
    public Align styleAlignItems() {
      requireOpen();
      return align(node.getAlignItems());
    }

    private static Align align(YogaAlign value) {
      return switch (value) {
        case FLEX_START -> Align.START;
        case CENTER -> Align.CENTER;
        case FLEX_END -> Align.END;
        default -> Align.STRETCH;
      };
    }

    private static YogaAlign yoga(Align value) {
      return switch (value) {
        case START -> YogaAlign.FLEX_START;
        case CENTER -> YogaAlign.CENTER;
        case END -> YogaAlign.FLEX_END;
        case STRETCH -> YogaAlign.STRETCH;
      };
    }

    @Override
    public void setFlexDirection(FlexDirection v) {
      requireOpen();
      node.setFlexDirection(
          v == FlexDirection.COLUMN ? YogaFlexDirection.COLUMN : YogaFlexDirection.ROW);
    }

    @Override
    public void setFlexWrap(Wrap v) {
      requireOpen();
      node.setWrap(v == Wrap.WRAP ? YogaWrap.WRAP : YogaWrap.NO_WRAP);
    }

    @Override
    public void setFlexGrow(float v) {
      requireOpen();
      node.setFlexGrow(v);
    }

    @Override
    public void setFlexShrink(float v) {
      requireOpen();
      node.setFlexShrink(v);
    }

    @Override
    public void setFlexBasis(float v) {
      requireOpen();
      node.setFlexBasis(v);
    }

    @Override
    public void setAlignItems(Align v) {
      requireOpen();
      node.setAlignItems(yoga(v));
    }

    @Override
    public void setAlignSelf(Align v) {
      requireOpen();
      node.setAlignSelf(yoga(v));
    }

    @Override
    public void setJustifyContent(Justify v) {
      requireOpen();
      node.setJustifyContent(
          switch (v) {
            case START -> YogaJustify.FLEX_START;
            case CENTER -> YogaJustify.CENTER;
            case END -> YogaJustify.FLEX_END;
            case SPACE_BETWEEN -> YogaJustify.SPACE_BETWEEN;
          });
    }

    @Override
    public void setGap(Gutter g, float v) {
      requireOpen();
      node.setGap(
          switch (g) {
            case ALL -> YogaGutter.ALL;
            case COLUMN -> YogaGutter.COLUMN;
            case ROW -> YogaGutter.ROW;
          },
          v);
    }

    @Override
    public void setBorder(Edge e, float v) {
      requireOpen();
      node.setBorder(edge(e), v);
    }

    @Override
    public void setWidth(float v) {
      requireOpen();
      node.setWidth(v);
    }

    @Override
    public void setWidthPercent(float v) {
      requireOpen();
      node.setWidthPercent(v);
    }

    @Override
    public void setWidthAuto() {
      requireOpen();
      node.setWidthAuto();
    }

    @Override
    public void setHeight(float v) {
      requireOpen();
      node.setHeight(v);
    }

    @Override
    public void setHeightPercent(float v) {
      requireOpen();
      node.setHeightPercent(v);
    }

    @Override
    public void setHeightAuto() {
      requireOpen();
      node.setHeightAuto();
    }

    @Override
    public void setMinWidth(float v) {
      requireOpen();
      node.setMinWidth(v);
    }

    @Override
    public void setMinHeight(float v) {
      requireOpen();
      node.setMinHeight(v);
    }

    @Override
    public void setMaxWidth(float v) {
      requireOpen();
      node.setMaxWidth(v);
    }

    @Override
    public void setMaxHeight(float v) {
      requireOpen();
      node.setMaxHeight(v);
    }

    @Override
    public void setMargin(Edge e, float v) {
      requireOpen();
      node.setMargin(edge(e), v);
    }

    @Override
    public void setPadding(Edge e, float v) {
      requireOpen();
      node.setPadding(edge(e), v);
    }

    private static YogaEdge edge(Edge e) {
      return switch (e) {
        case ALL -> YogaEdge.ALL;
        case HORIZONTAL -> YogaEdge.HORIZONTAL;
        case VERTICAL -> YogaEdge.VERTICAL;
        case TOP -> YogaEdge.TOP;
        case RIGHT -> YogaEdge.RIGHT;
        case BOTTOM -> YogaEdge.BOTTOM;
        case LEFT -> YogaEdge.LEFT;
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
        throw new IllegalStateException("Remove/free children before Yoga node");
      try {
        node.setMeasureFunction(null);
        node.setData(null);
      } finally {
        measure = null;
        try {
          node.freeNatives();
        } finally {
          try {
            config.freeNatives();
          } finally {
            closed = true;
          }
        }
      }
    }
  }
}
