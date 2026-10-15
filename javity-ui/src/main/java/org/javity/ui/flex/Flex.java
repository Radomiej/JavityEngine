package org.javity.ui.flex;

import static org.lwjgl.util.yoga.Yoga.*;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.utils.Layout;
import com.badlogic.gdx.utils.Disposable;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import org.lwjgl.util.yoga.YGMeasureFunc;
import org.lwjgl.util.yoga.YGSize;

/** Scene2D container backed by a real Yoga tree. Close detached containers explicitly. */
public final class Flex extends WidgetGroup implements Disposable, AutoCloseable {
  private static final class Item {
    final long node;
    final boolean owned;
    String classes = "";
    YGMeasureFunc measure;
    Throwable measureFailure;

    Item(long node, boolean owned) {
      this.node = node;
      this.owned = owned;
    }
  }

  private final long node;
  private final IdentityHashMap<Actor, Item> items = new IdentityHashMap<>();
  private final UiTheme theme;
  private UtilityStyles.Visual visual;
  private String classes;
  private String parentClasses = "";
  private boolean disposed;

  public Flex(String classes) {
    this(null, classes);
  }

  public Flex(UiTheme theme, String classes) {
    this.theme = theme;
    setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.childrenOnly);
    node = YGNodeNew();
    this.classes = classes == null ? "" : classes;
    try {
      visual = UtilityStyles.apply(node, this.classes);
    } catch (RuntimeException e) {
      YGNodeFree(node);
      throw e;
    }
  }

  public <T extends Actor> T add(T actor, String classes) {
    requireOpen();
    if (actor == this || isDescendantOf(actor))
      throw new IllegalArgumentException("Cyclic UI tree");
    boolean alreadyChild = actor.getParent() == this;
    // Validate before changing the existing parent or allocating its persistent node.
    String replacement = classes == null ? "" : classes;
    long probe = YGNodeNew();
    try {
      if (actor instanceof Flex child) child.applyClassesToProbe(probe, child.classes, replacement);
      else UtilityStyles.apply(probe, replacement);
      if (!alreadyChild) super.addActor(actor);
      Item item = items.get(actor);
      item.classes = replacement;
      if (actor instanceof Flex child) child.setParentClasses(replacement);
      else YGNodeCopyStyle(item.node, probe);
    } finally {
      YGNodeFree(probe);
    }
    invalidateHierarchy();
    return actor;
  }

  /** Replace all Yoga and visual styles on this container. */
  public void setStyleClasses(String classes) {
    requireOpen();
    String replacement = classes == null ? "" : classes;
    long probe = YGNodeNew();
    UtilityStyles.Visual nextVisual;
    try {
      nextVisual = applyClassesToProbe(probe, replacement, parentClasses);
      YGNodeCopyStyle(node, probe);
    } finally {
      YGNodeFree(probe);
    }
    this.classes = replacement;
    this.visual = nextVisual;
    invalidateHierarchy();
  }

  private void setParentClasses(String replacement) {
    long probe = YGNodeNew();
    UtilityStyles.Visual nextVisual;
    try {
      nextVisual = applyClassesToProbe(probe, classes, replacement);
      YGNodeCopyStyle(node, probe);
    } finally {
      YGNodeFree(probe);
    }
    parentClasses = replacement;
    visual = nextVisual;
    invalidateHierarchy();
  }

  private UtilityStyles.Visual applyClassesToProbe(long probe, String own, String parent) {
    UtilityStyles.Visual ownVisual = UtilityStyles.apply(probe, own);
    if (parent != null && !parent.isBlank()) UtilityStyles.apply(probe, parent);
    return ownVisual;
  }

  public String getStyleClasses() {
    return classes;
  }

  @Override
  protected void childrenChanged() {
    super.childrenChanged();
    if (items == null || disposed) return;
    for (Actor actor : items.keySet().toArray(Actor[]::new)) {
      if (actor.getParent() != this) {
        Item item = items.remove(actor);
        YGNodeRemoveChild(node, item.node);
        if (actor instanceof Flex child) child.setParentClasses("");
        if (item.owned) {
          releaseMeasure(item);
          YGNodeFree(item.node);
        }
      }
    }
    // Reinsert in Scene2D order, including addActorAt/before/after operations.
    YGNodeRemoveAllChildren(node);
    int index = 0;
    for (Actor actor : getChildren()) {
      Item item = items.get(actor);
      if (item == null) {
        if (actor instanceof Flex child) child.requireOpen();
        item =
            new Item(
                actor instanceof Flex child ? child.node : YGNodeNew(), !(actor instanceof Flex));
        if (actor instanceof YogaMeasuredActor measurable) installMeasure(item, measurable);
        items.put(actor, item);
      }
      YGNodeInsertChild(node, item.node, index++);
    }
  }

  private void measureLeaves() {
    for (Actor actor : getChildren()) {
      Item item = items.get(actor);
      if (actor instanceof Flex child) child.measureLeaves();
      else {
        if (actor instanceof YogaMeasuredActor) {
          // Scene2D invalidation is independent from Yoga's measure cache. A changed Label text,
          // inherited font class, or wrap mode can arrive under identical root constraints, so
          // dirty its native leaf at the layout boundary before Yoga reuses a cached measurement.
          // Never do this inside the measure callback: Yoga must not be mutated reentrantly.
          if (item.measure != null) YGNodeMarkDirty(item.node);
          continue;
        }
        boolean verticalStack = YGNodeStyleGetFlexDirection(node) == YGFlexDirectionColumn;
        boolean stretchCrossAxis = YGNodeStyleGetAlignItems(node) == YGAlignStretch;
        if (!hasDimension(item.classes, "w-") && !(verticalStack && stretchCrossAxis))
          YGNodeStyleSetWidth(
              item.node, actor instanceof Layout l ? l.getPrefWidth() : actor.getWidth());
        if (!hasDimension(item.classes, "h-") && !(!verticalStack && stretchCrossAxis)) {
          float height = actor instanceof Layout l ? l.getPrefHeight() : actor.getHeight();
          if (height > 0) YGNodeStyleSetHeight(item.node, height);
        }
      }
    }
  }

  private static boolean hasDimension(String classes, String prefix) {
    for (String token : classes.split("\\s+")) if (token.startsWith(prefix)) return true;
    return false;
  }

  @Override
  public float getPrefWidth() {
    return measureIntrinsic(Float.NaN).width();
  }

  @Override
  public float getPrefHeight() {
    float width = Float.NaN;
    if (getParent() instanceof ScrollPane pane) {
      if (pane.getWidth() <= 0) return measureIntrinsic(Float.NaN).height();
      width = pane.getWidth();
      ScrollPane.ScrollPaneStyle style = pane.getStyle();
      if (style.background != null)
        width -= style.background.getLeftWidth() + style.background.getRightWidth();
      if (style.vScroll != null) width -= pane.getScrollBarWidth();
      width = Math.max(0, width);
    }
    return measureIntrinsic(width).height();
  }

  private record IntrinsicSize(float width, float height) {}

  /**
   * Measure a detached copy so preferred-size queries never rewrite this Flex's active Yoga tree.
   */
  private IntrinsicSize measureIntrinsic(float availableWidth) {
    requireOpen();
    List<TemporaryMeasure> callbacks = new ArrayList<>();
    MeasureFailure failure = new MeasureFailure();
    long copy = copyYogaTree(callbacks, failure);
    try {
      YGNodeCalculateLayout(copy, availableWidth, Float.NaN, YGDirectionLTR);
      failure.rethrow();
      return new IntrinsicSize(YGNodeLayoutGetWidth(copy), YGNodeLayoutGetHeight(copy));
    } finally {
      releaseTemporaryMeasures(callbacks);
      YGNodeFreeRecursive(copy);
    }
  }

  private record TemporaryMeasure(long node, YGMeasureFunc callback) {}

  private static final class MeasureFailure {
    Throwable failure;

    void capture(Throwable thrown) {
      if (failure == null) failure = thrown;
    }

    void rethrow() {
      if (failure instanceof RuntimeException runtime) throw runtime;
      if (failure instanceof Error error) throw error;
      if (failure != null) throw new IllegalStateException("Yoga text measurement failed", failure);
    }
  }

  private long copyYogaTree(List<TemporaryMeasure> callbacks, MeasureFailure failure) {
    int firstCallback = callbacks.size();
    long copy = YGNodeNew();
    long pendingChildCopy = 0;
    try {
      YGNodeCopyStyle(copy, node);
      int index = 0;
      for (Actor actor : getChildren()) {
        Item item = items.get(actor);
        long childCopy;
        if (actor instanceof Flex child) {
          childCopy = child.copyYogaTree(callbacks, failure);
          pendingChildCopy = childCopy;
        } else {
          childCopy = YGNodeNew();
          pendingChildCopy = childCopy;
          YGNodeCopyStyle(childCopy, item.node);
          if (actor instanceof YogaMeasuredActor measurable) {
            YGMeasureFunc callback = measureFunction(measurable, failure::capture);
            try {
              callbacks.add(new TemporaryMeasure(childCopy, callback));
              YGNodeSetMeasureFunc(childCopy, callback);
            } catch (RuntimeException | Error e) {
              if (!callbacks.isEmpty() && callbacks.getLast().callback() == callback)
                callbacks.removeLast();
              callback.free();
              throw e;
            }
          }
          if (!(actor instanceof YogaMeasuredActor)) {
            boolean verticalStack = YGNodeStyleGetFlexDirection(node) == YGFlexDirectionColumn;
            boolean stretchCrossAxis = YGNodeStyleGetAlignItems(node) == YGAlignStretch;
            if (!hasDimension(item.classes, "w-") && !(verticalStack && stretchCrossAxis))
              YGNodeStyleSetWidth(
                  childCopy,
                  actor instanceof Layout layout ? layout.getPrefWidth() : actor.getWidth());
            if (!hasDimension(item.classes, "h-") && !(!verticalStack && stretchCrossAxis)) {
              float preferredHeight =
                  actor instanceof Layout layout ? layout.getPrefHeight() : actor.getHeight();
              if (preferredHeight > 0) YGNodeStyleSetHeight(childCopy, preferredHeight);
              else YGNodeStyleSetHeightAuto(childCopy);
            } else if (!hasDimension(item.classes, "h-")) {
              YGNodeStyleSetHeightAuto(childCopy);
            }
          }
        }
        YGNodeInsertChild(copy, childCopy, index++);
        pendingChildCopy = 0;
      }
      return copy;
    } catch (RuntimeException | Error e) {
      releaseTemporaryMeasuresFrom(callbacks, firstCallback);
      if (pendingChildCopy != 0) YGNodeFreeRecursive(pendingChildCopy);
      YGNodeFreeRecursive(copy);
      throw e;
    }
  }

  @Override
  public void layout() {
    requireOpen();
    if (!(getParent() instanceof Flex)) {
      measureLeaves();
      YGNodeCalculateLayout(node, getWidth(), getHeight(), YGDirectionLTR);
      checkMeasureFailures();
    }
    applyBounds();
  }

  private void installMeasure(Item item, YogaMeasuredActor measurable) {
    item.measure =
        measureFunction(
            measurable,
            thrown -> {
              if (item.measureFailure == null) item.measureFailure = thrown;
            });
    YGNodeSetMeasureFunc(item.node, item.measure);
  }

  static YGMeasureFunc measureFunction(
      YogaMeasuredActor measurable, java.util.function.Consumer<Throwable> failures) {
    return YGMeasureFunc.create(
        (long node, float width, int widthMode, float height, int heightMode, YGSize out) -> {
          try {
            float constraint = Float.isFinite(width) ? Math.max(0, width) : Float.NaN;
            YogaMeasuredActor.Size size = measurable.measureForYoga(constraint, widthMode);
            float measuredWidth = finiteNonnegative(size.width());
            float measuredHeight = finiteNonnegative(size.height());
            float resultWidth = measuredWidth;
            if (Float.isFinite(width)) {
              if (widthMode == YGMeasureModeExactly) resultWidth = Math.max(0, width);
              else if (widthMode == YGMeasureModeAtMost)
                resultWidth = Math.min(measuredWidth, Math.max(0, width));
            }
            float resultHeight = measuredHeight;
            if (Float.isFinite(height)) {
              if (heightMode == YGMeasureModeExactly) resultHeight = Math.max(0, height);
              else if (heightMode == YGMeasureModeAtMost)
                resultHeight = Math.min(measuredHeight, Math.max(0, height));
            }
            out.set(resultWidth, resultHeight);
          } catch (Throwable failure) {
            failures.accept(failure);
            out.set(0, 0);
          }
        });
  }

  private static float finiteNonnegative(float value) {
    if (!Float.isFinite(value)) throw new IllegalArgumentException("Yoga text size must be finite");
    return Math.max(0, value);
  }

  private void checkMeasureFailures() {
    for (Actor actor : getChildren()) {
      Item item = items.get(actor);
      if (item != null && item.measureFailure != null) {
        Throwable failure = item.measureFailure;
        item.measureFailure = null;
        if (failure instanceof RuntimeException runtime) throw runtime;
        if (failure instanceof Error error) throw error;
        throw new IllegalStateException("Yoga text measurement failed", failure);
      }
      if (actor instanceof Flex child) child.checkMeasureFailures();
    }
  }

  private static void releaseMeasure(Item item) {
    if (item.measure == null) return;
    YGNodeSetMeasureFunc(item.node, null);
    item.measure.free();
    item.measure = null;
  }

  private static void releaseTemporaryMeasures(List<TemporaryMeasure> callbacks) {
    releaseTemporaryMeasuresFrom(callbacks, 0);
  }

  private static void releaseTemporaryMeasuresFrom(
      List<TemporaryMeasure> callbacks, int firstCallback) {
    for (int i = callbacks.size() - 1; i >= firstCallback; i--) {
      TemporaryMeasure temporary = callbacks.get(i);
      callbacks.remove(i);
      YGNodeSetMeasureFunc(temporary.node(), null);
      temporary.callback().free();
    }
  }

  private void applyBounds() {
    for (Actor actor : getChildren()) {
      long childNode = items.get(actor).node;
      float height = YGNodeLayoutGetHeight(childNode);
      actor.setBounds(
          YGNodeLayoutGetLeft(childNode),
          getHeight() - YGNodeLayoutGetTop(childNode) - height,
          YGNodeLayoutGetWidth(childNode),
          height);
      if (actor instanceof Flex child) child.applyBounds();
      else if (actor instanceof Layout layout) layout.validate();
    }
  }

  @Override
  public void draw(Batch batch, float parentAlpha) {
    validate();
    if ((visual.background() != null || visual.borderWidth() > 0) && theme != null) {
      float saved = batch.getPackedColor();
      batch.setColor(getColor().r, getColor().g, getColor().b, getColor().a * parentAlpha);
      try {
        theme.drawRectangle(batch, getX(), getY(), getWidth(), getHeight(), visual);
      } finally {
        batch.setPackedColor(saved);
      }
    }
    super.draw(batch, parentAlpha);
  }

  private void requireOpen() {
    if (disposed) throw new IllegalStateException("Flex has been disposed");
  }

  @Override
  public void close() {
    dispose();
  }

  @Override
  public void dispose() {
    if (disposed) return;
    remove();
    Actor[] children = getChildren().toArray(Actor.class);
    clearChildren();
    for (Actor actor : children) if (actor instanceof Flex child) child.dispose();
    disposed = true;
    YGNodeFree(node);
  }
}
