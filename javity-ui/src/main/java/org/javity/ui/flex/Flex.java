package org.javity.ui.flex;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.utils.Layout;
import com.badlogic.gdx.utils.Disposable;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import org.javity.ui.yoga.YogaBackend;
import org.javity.ui.yoga.YogaBackends;
import org.javity.ui.yoga.YogaNode;
import org.javity.ui.yoga.YogaNode.Align;
import org.javity.ui.yoga.YogaNode.FlexDirection;

/** Scene2D container backed by a real Yoga tree. Close detached containers explicitly. */
public final class Flex extends WidgetGroup implements Disposable, AutoCloseable {
  private static final class Item {
    final YogaNode node;
    final boolean owned;
    String classes = "";
    YogaNode.MeasureFunction measure;
    Throwable measureFailure;

    Item(YogaNode node, boolean owned) {
      this.node = node;
      this.owned = owned;
    }
  }

  private final YogaNode node;
  private final IdentityHashMap<Actor, Item> items = new IdentityHashMap<>();
  private final UiTheme theme;
  private final YogaBackend backend;
  private UtilityStyles.Visual visual;
  private String classes;
  private String parentClasses = "";
  private boolean disposed;

  public Flex(String classes) {
    this(YogaBackends.createDefault(), null, classes);
  }

  public Flex(UiTheme theme, String classes) {
    this(YogaBackends.createDefault(), theme, classes);
  }

  public Flex(YogaBackend backend, UiTheme theme, String classes) {
    this.backend = java.util.Objects.requireNonNull(backend);
    this.theme = theme;
    setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.childrenOnly);
    node = backend.createNode();
    this.classes = classes == null ? "" : classes;
    try {
      visual = UtilityStyles.apply(node, this.classes);
    } catch (RuntimeException e) {
      node.freeNode();
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
    YogaNode probe = backend.createNode();
    try {
      if (actor instanceof Flex child) child.applyClassesToProbe(probe, child.classes, replacement);
      else UtilityStyles.apply(probe, replacement);
      if (!alreadyChild) super.addActor(actor);
      Item item = items.get(actor);
      item.classes = replacement;
      if (actor instanceof Flex child) child.setParentClasses(replacement);
      else item.node.copyStyleFrom(probe);
    } finally {
      probe.freeNode();
    }
    invalidateHierarchy();
    return actor;
  }

  /** Replace all Yoga and visual styles on this container. */
  public void setStyleClasses(String classes) {
    requireOpen();
    String replacement = classes == null ? "" : classes;
    YogaNode probe = backend.createNode();
    UtilityStyles.Visual nextVisual;
    try {
      nextVisual = applyClassesToProbe(probe, replacement, parentClasses);
      node.copyStyleFrom(probe);
    } finally {
      probe.freeNode();
    }
    this.classes = replacement;
    this.visual = nextVisual;
    invalidateHierarchy();
  }

  private void setParentClasses(String replacement) {
    YogaNode probe = backend.createNode();
    UtilityStyles.Visual nextVisual;
    try {
      nextVisual = applyClassesToProbe(probe, classes, replacement);
      node.copyStyleFrom(probe);
    } finally {
      probe.freeNode();
    }
    parentClasses = replacement;
    visual = nextVisual;
    invalidateHierarchy();
  }

  private UtilityStyles.Visual applyClassesToProbe(YogaNode probe, String own, String parent) {
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
        node.removeChild(item.node);
        if (actor instanceof Flex child) child.setParentClasses("");
        if (item.owned) {
          releaseMeasure(item);
          item.node.freeNode();
        }
      }
    }
    // Reinsert in Scene2D order, including addActorAt/before/after operations.
    node.removeAllChildren();
    int index = 0;
    for (Actor actor : getChildren()) {
      Item item = items.get(actor);
      if (item == null) {
        if (actor instanceof Flex child) child.requireOpen();
        item =
            new Item(
                actor instanceof Flex child ? child.node : backend.createNode(),
                !(actor instanceof Flex));
        if (actor instanceof YogaMeasuredActor measurable) installMeasure(item, measurable);
        items.put(actor, item);
      }
      node.insertChild(item.node, index++);
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
          if (item.measure != null) item.node.markDirty();
          continue;
        }
        boolean verticalStack = node.styleFlexDirection() == FlexDirection.COLUMN;
        boolean stretchCrossAxis = node.styleAlignItems() == Align.STRETCH;
        if (!hasDimension(item.classes, "w-") && !(verticalStack && stretchCrossAxis))
          item.node.setWidth(actor instanceof Layout l ? l.getPrefWidth() : actor.getWidth());
        if (!hasDimension(item.classes, "h-") && !(!verticalStack && stretchCrossAxis)) {
          float height = actor instanceof Layout l ? l.getPrefHeight() : actor.getHeight();
          if (height > 0) item.node.setHeight(height);
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
    YogaNode copy = copyYogaTree(callbacks, failure);
    try {
      copy.calculateLayout(availableWidth, Float.NaN);
      failure.rethrow();
      return new IntrinsicSize(copy.layoutWidth(), copy.layoutHeight());
    } finally {
      releaseTemporaryMeasures(callbacks);
      copy.freeSubtree();
    }
  }

  private record TemporaryMeasure(YogaNode node, YogaNode.MeasureFunction callback) {}

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

  private YogaNode copyYogaTree(List<TemporaryMeasure> callbacks, MeasureFailure failure) {
    int firstCallback = callbacks.size();
    YogaNode copy = backend.createNode();
    YogaNode pendingChildCopy = null;
    try {
      copy.copyStyleFrom(node);
      int index = 0;
      for (Actor actor : getChildren()) {
        Item item = items.get(actor);
        YogaNode childCopy;
        if (actor instanceof Flex child) {
          childCopy = child.copyYogaTree(callbacks, failure);
          pendingChildCopy = childCopy;
        } else {
          childCopy = backend.createNode();
          pendingChildCopy = childCopy;
          childCopy.copyStyleFrom(item.node);
          if (actor instanceof YogaMeasuredActor measurable) {
            YogaNode.MeasureFunction callback = measureFunction(measurable, failure::capture);
            try {
              callbacks.add(new TemporaryMeasure(childCopy, callback));
              childCopy.setMeasureFunction(callback);
            } catch (RuntimeException | Error e) {
              if (!callbacks.isEmpty() && callbacks.getLast().callback() == callback)
                callbacks.removeLast();
              childCopy.setMeasureFunction(null);
              throw e;
            }
          }
          if (!(actor instanceof YogaMeasuredActor)) {
            boolean verticalStack = node.styleFlexDirection() == FlexDirection.COLUMN;
            boolean stretchCrossAxis = node.styleAlignItems() == Align.STRETCH;
            if (!hasDimension(item.classes, "w-") && !(verticalStack && stretchCrossAxis))
              childCopy.setWidth(
                  actor instanceof Layout layout ? layout.getPrefWidth() : actor.getWidth());
            if (!hasDimension(item.classes, "h-") && !(!verticalStack && stretchCrossAxis)) {
              float preferredHeight =
                  actor instanceof Layout layout ? layout.getPrefHeight() : actor.getHeight();
              if (preferredHeight > 0) childCopy.setHeight(preferredHeight);
              else childCopy.setHeightAuto();
            } else if (!hasDimension(item.classes, "h-")) {
              childCopy.setHeightAuto();
            }
          }
        }
        copy.insertChild(childCopy, index++);
        pendingChildCopy = null;
      }
      return copy;
    } catch (RuntimeException | Error e) {
      releaseTemporaryMeasuresFrom(callbacks, firstCallback);
      if (pendingChildCopy != null) pendingChildCopy.freeSubtree();
      copy.freeSubtree();
      throw e;
    }
  }

  @Override
  public void layout() {
    requireOpen();
    if (!(getParent() instanceof Flex)) {
      measureLeaves();
      node.calculateLayout(getWidth(), getHeight());
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
    item.node.setMeasureFunction(item.measure);
  }

  static YogaNode.MeasureFunction measureFunction(
      YogaMeasuredActor measurable, java.util.function.Consumer<Throwable> failures) {
    return (float width, int widthMode, float height, int heightMode) -> {
      try {
        float constraint = Float.isFinite(width) ? Math.max(0, width) : Float.NaN;
        YogaMeasuredActor.Size size = measurable.measureForYoga(constraint, widthMode);
        float measuredWidth = finiteNonnegative(size.width());
        float measuredHeight = finiteNonnegative(size.height());
        float resultWidth = measuredWidth;
        if (Float.isFinite(width)) {
          if (widthMode == YogaNode.MEASURE_EXACTLY) resultWidth = Math.max(0, width);
          else if (widthMode == YogaNode.MEASURE_AT_MOST)
            resultWidth = Math.min(measuredWidth, Math.max(0, width));
        }
        float resultHeight = measuredHeight;
        if (Float.isFinite(height)) {
          if (heightMode == YogaNode.MEASURE_EXACTLY) resultHeight = Math.max(0, height);
          else if (heightMode == YogaNode.MEASURE_AT_MOST)
            resultHeight = Math.min(measuredHeight, Math.max(0, height));
        }
        return new YogaNode.Size(resultWidth, resultHeight);
      } catch (Throwable failure) {
        failures.accept(failure);
        return new YogaNode.Size(0, 0);
      }
    };
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
    item.node.setMeasureFunction(null);
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
      temporary.node().setMeasureFunction(null);
    }
  }

  private void applyBounds() {
    for (Actor actor : getChildren()) {
      YogaNode childNode = items.get(actor).node;
      float height = childNode.layoutHeight();
      actor.setBounds(
          childNode.layoutLeft(),
          getHeight() - childNode.layoutTop() - height,
          childNode.layoutWidth(),
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
    node.freeNode();
  }
}
