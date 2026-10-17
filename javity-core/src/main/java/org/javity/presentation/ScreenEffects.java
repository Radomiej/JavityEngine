package org.javity.presentation;

import com.badlogic.gdx.graphics.Color;
import java.util.Objects;
import org.javity.core.GameObject;
import org.javity.core.GameSystem;
import org.javity.render.ShapeRenderer2D;

/** UI-clock screen overlays rendered by the normal SCREEN shape pass. */
public final class ScreenEffects extends GameSystem {
  public static final int MAX_ACTIVE_FLASHES = 64;
  private static final double MAX_DURATION_SECONDS = 3600;

  private static final class FlashSlot {
    final Color color = new Color(Color.WHITE);
    GameObject object;
    ShapeRenderer2D shape;
    long generation, order, eligibleFrame;
    double age, duration;
    float peak;
    boolean active;
  }

  private final FlashSlot[] slots = new FlashSlot[MAX_ACTIVE_FLASHES];
  private int width, height;
  private long nextOrder;
  private boolean released;

  public ScreenEffects(int width, int height) {
    positiveViewport(width, height);
    this.width = width;
    this.height = height;
    for (int i = 0; i < slots.length; i++) slots[i] = new FlashSlot();
  }

  @Override
  public void onCreate() {
    ensureSlots();
  }

  /** Changes the logical SCREEN-space dimensions used by every pooled full-viewport shape. */
  public void resize(int width, int height) {
    positiveViewport(width, height);
    this.width = width;
    this.height = height;
    for (FlashSlot slot : slots) layout(slot);
  }

  /**
   * Draws a full logical-viewport color overlay. It starts at peak opacity, then fades linearly to
   * zero over {@code seconds}; a zero duration returns an inactive handle.
   */
  public ScreenFlash flash(Color color, float peak, double seconds) {
    live();
    validateColor(color);
    if (!Float.isFinite(peak) || peak < 0 || peak > 1)
      throw new IllegalArgumentException("Flash peak must be between 0 and 1");
    if (!Double.isFinite(seconds) || seconds < 0 || seconds > MAX_DURATION_SECONDS)
      throw new IllegalArgumentException("Flash duration must be between 0 and one hour");
    if (seconds == 0 || peak == 0) return new ScreenFlash(null, -1, 0);
    ensureSlots();
    int index = -1;
    for (int i = 0; i < slots.length; i++)
      if (!slots[i].active) {
        index = i;
        break;
      }
    if (index < 0) {
      index = oldestSlot();
      retire(index);
    }
    FlashSlot slot = slots[index];
    slot.generation++;
    slot.order = nextOrderForFlash();
    slot.eligibleFrame = getGame().getFrameIndex() + 1;
    slot.age = 0;
    slot.duration = seconds;
    slot.peak = peak;
    slot.color.set(color);
    slot.active = true;
    slot.shape.color.set(slot.color);
    slot.shape.color.a = slot.color.a * peak;
    slot.shape.setEnabled(true);
    slot.object.setActive(true);
    slot.shape.order = (int) slot.order;
    layout(slot);
    return new ScreenFlash(this, index, slot.generation);
  }

  public int getActiveFlashCount() {
    int count = 0;
    for (FlashSlot slot : slots) if (slot.active) count++;
    return count;
  }

  @Override
  public void onUiUpdate(float unscaledDelta) {
    if (released || unscaledDelta <= 0 || getGame() == null || getGame().isDisposed()) return;
    long frame = getGame().getFrameIndex();
    for (int i = 0; i < slots.length; i++) {
      FlashSlot slot = slots[i];
      if (!slot.active || frame < slot.eligibleFrame) continue;
      slot.age = Math.min(slot.duration, slot.age + unscaledDelta);
      float alpha = slot.peak * (float) (1 - slot.age / slot.duration);
      slot.shape.color.a = slot.color.a * alpha;
      if (slot.age >= slot.duration) retire(i);
    }
  }

  boolean isActive(int index, long generation) {
    return index >= 0
        && index < slots.length
        && slots[index].active
        && slots[index].generation == generation;
  }

  void cancel(int index, long generation) {
    if (isActive(index, generation)) retire(index);
  }

  @Override
  protected void onRelease() {
    released = true;
    for (int i = 0; i < slots.length; i++) {
      retire(i);
      FlashSlot slot = slots[i];
      if (slot.object != null && !slot.object.isDestroyed()) slot.object.destroy();
      slot.object = null;
      slot.shape = null;
    }
  }

  private void ensureSlots() {
    if (slots[0].object != null) return;
    live();
    for (int i = 0; i < slots.length; i++) {
      FlashSlot slot = slots[i];
      GameObject object = getGame().createObject("screen-flash-" + i);
      ShapeRenderer2D shape = object.addComponent(ShapeRenderer2D.rectangle(1, 1));
      shape.space = ShapeRenderer2D.Space.SCREEN;
      shape.layer = Integer.MAX_VALUE;
      shape.order = i;
      shape.setEnabled(false);
      object.setActive(false);
      slot.object = object;
      slot.shape = shape;
      layout(slot);
    }
  }

  private void layout(FlashSlot slot) {
    if (slot.object == null || slot.object.isDestroyed()) return;
    slot.object.transform.position.set(width / 2f, height / 2f);
    slot.object.transform.scale.set(width, height);
    slot.object.transform.rotation = 0;
    slot.object.transform.visualOffset.setZero();
  }

  private int oldestSlot() {
    int oldest = 0;
    for (int i = 1; i < slots.length; i++) if (slots[i].order < slots[oldest].order) oldest = i;
    return oldest;
  }

  private long nextOrderForFlash() {
    // Comparator uses ints; rebasing the at-most-64 active overlays prevents wraparound reordering.
    if (nextOrder < Integer.MAX_VALUE - 1L) return nextOrder++;
    long min = Long.MAX_VALUE;
    for (FlashSlot slot : slots) if (slot.active && slot.order < min) min = slot.order;
    if (min == Long.MAX_VALUE) min = nextOrder;
    for (FlashSlot slot : slots)
      if (slot.active) {
        slot.order -= min;
        slot.shape.order = (int) slot.order;
      }
    nextOrder -= min;
    return nextOrder++;
  }

  private void retire(int index) {
    FlashSlot slot = slots[index];
    if (!slot.active) return;
    slot.active = false;
    slot.age = slot.duration = 0;
    slot.peak = 0;
    slot.color.set(Color.WHITE);
    slot.shape.color.set(Color.WHITE);
    slot.shape.order = index;
    slot.shape.setEnabled(false);
    if (!slot.object.isDestroyed()) slot.object.setActive(false);
  }

  private void live() {
    if (released || getGame() == null || getGame().isDisposed() || isDisposed())
      throw new IllegalStateException("ScreenEffects is not active in a scene");
  }

  private static void validateColor(Color color) {
    Objects.requireNonNull(color, "color");
    if (!Float.isFinite(color.r)
        || !Float.isFinite(color.g)
        || !Float.isFinite(color.b)
        || !Float.isFinite(color.a)
        || color.r < 0
        || color.r > 1
        || color.g < 0
        || color.g > 1
        || color.b < 0
        || color.b > 1
        || color.a < 0
        || color.a > 1)
      throw new IllegalArgumentException("Flash color channels must be between 0 and 1");
  }

  private static void positiveViewport(int width, int height) {
    if (width <= 0 || height <= 0) throw new IllegalArgumentException("Positive viewport required");
  }
}
