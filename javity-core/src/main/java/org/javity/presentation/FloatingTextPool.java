package org.javity.presentation;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import java.util.*;
import org.javity.core.GameObject;
import org.javity.core.GameSystem;
import org.javity.effects.Clock;
import org.javity.render.TextRenderer2D;

/** Bounded, scene-owned pool of labels; the caller retains ownership of the font. */
public final class FloatingTextPool extends GameSystem {
  public static final int DEFAULT_CAPACITY = 32;
  private static final float DEFAULT_RISE_DISTANCE = 24;

  static final class Slot {
    final GameObject object;
    final TextRenderer2D renderer;
    long generation;
    long order;
    long eligibleFrame;
    float originX, originY, duration, age, startAlpha;
    boolean active;

    Slot(GameObject object, TextRenderer2D renderer) {
      this.object = object;
      this.renderer = renderer;
    }
  }

  private final BitmapFont font;
  private final Clock clock;
  private final int capacity;
  private final float riseDistance;
  private final List<Slot> slots = new ArrayList<>();
  private long nextOrder;
  private boolean released;

  public FloatingTextPool(BitmapFont font) {
    this(font, Clock.WORLD);
  }

  public FloatingTextPool(BitmapFont font, Clock clock) {
    this(font, clock, DEFAULT_CAPACITY, DEFAULT_RISE_DISTANCE);
  }

  public FloatingTextPool(BitmapFont font, Clock clock, int capacity, float riseDistance) {
    this.font = Objects.requireNonNull(font, "font");
    this.clock = Objects.requireNonNull(clock, "clock");
    if (capacity < 1) throw new IllegalArgumentException("Capacity must be positive");
    if (!Float.isFinite(riseDistance) || riseDistance < 0)
      throw new IllegalArgumentException("Rise distance must be finite and nonnegative");
    this.capacity = capacity;
    this.riseDistance = riseDistance;
  }

  @Override
  public void onCreate() {
    for (int index = 0; index < capacity; index++) {
      GameObject object = getGame().createObject("floating-text-" + index);
      TextRenderer2D renderer = object.addComponent(new TextRenderer2D(font, ""));
      renderer.setEnabled(false);
      object.setActive(false);
      slots.add(new Slot(object, renderer));
    }
  }

  public FloatingTextHandle spawn(String text, float x, float y, float duration, Color color) {
    live();
    if (text == null || text.isBlank())
      throw new IllegalArgumentException("Text must not be empty");
    if (!Float.isFinite(x) || !Float.isFinite(y))
      throw new IllegalArgumentException("Coordinates must be finite");
    if (!Float.isFinite(duration) || duration <= 0)
      throw new IllegalArgumentException("Duration must be finite and positive");
    validateColor(color);
    Slot slot = null;
    for (Slot candidate : slots) {
      if (!candidate.active) {
        slot = candidate;
        break;
      }
    }
    if (slot == null) {
      slot = slots.getFirst();
      for (Slot candidate : slots) if (candidate.order < slot.order) slot = candidate;
      retire(slot);
    }
    resetRendererAndTransform(slot);
    slot.generation++;
    slot.order = nextOrder++;
    slot.eligibleFrame = getGame().getFrameIndex() + 1;
    slot.active = true;
    slot.originX = x;
    slot.originY = y;
    slot.duration = duration;
    slot.age = 0;
    slot.startAlpha = color.a;
    slot.renderer.text = text;
    slot.renderer.color.set(color);
    slot.renderer.setEnabled(true);
    slot.object.transform.position.set(x, y);
    slot.object.setActive(true);
    return new FloatingTextHandle(this, slot, slot.generation);
  }

  public int getCapacity() {
    return capacity;
  }

  public int getActiveCount() {
    int active = 0;
    for (Slot slot : slots) if (slot.active) active++;
    return active;
  }

  @Override
  public void onAfterUpdate(float delta) {
    if (clock == Clock.WORLD) advance(delta);
  }

  @Override
  public void onUiUpdate(float unscaledDelta) {
    if (clock == Clock.UI) advance(unscaledDelta);
  }

  boolean isActive(Slot slot, long generation) {
    return !released && slot != null && slot.active && slot.generation == generation;
  }

  void cancel(Slot slot, long generation) {
    if (isActive(slot, generation)) retire(slot);
  }

  @Override
  protected void onRelease() {
    released = true;
    for (Slot slot : slots) {
      retire(slot);
      if (!slot.object.isDestroyed()) slot.object.destroy();
    }
    slots.clear();
  }

  private void advance(float delta) {
    if (released || delta <= 0 || getGame().isDisposed()) return;
    for (Slot slot : slots) {
      if (!slot.active || getGame().getFrameIndex() < slot.eligibleFrame) continue;
      slot.age = Math.min(slot.duration, slot.age + delta);
      float progress = slot.age / slot.duration;
      slot.object.transform.position.set(slot.originX, slot.originY + riseDistance * progress);
      slot.renderer.color.a = slot.startAlpha * (1 - progress);
      if (slot.age >= slot.duration) retire(slot);
    }
  }

  private void retire(Slot slot) {
    if (slot == null) return;
    slot.active = false;
    slot.age = 0;
    slot.duration = 0;
    slot.originX = 0;
    slot.originY = 0;
    slot.startAlpha = 1;
    slot.eligibleFrame = 0;
    resetRendererAndTransform(slot);
    slot.renderer.setEnabled(false);
    if (!slot.object.isDestroyed()) slot.object.setActive(false);
  }

  private void resetRendererAndTransform(Slot slot) {
    slot.renderer.text = "";
    slot.renderer.color.set(Color.WHITE);
    slot.renderer.layer = 0;
    slot.renderer.order = 0;
    slot.renderer.updateMode = org.javity.core.Component.UpdateMode.WORLD;
    slot.object.transform.position.setZero();
    slot.object.transform.scale.set(1, 1);
    slot.object.transform.rotation = 0;
    slot.object.transform.visualOffset.setZero();
  }

  private void live() {
    if (released || isDisposed()) throw new IllegalStateException("Floating text pool disposed");
    if (slots.size() != capacity)
      throw new IllegalStateException("Game must be started before spawning text");
  }

  private static void validateColor(Color color) {
    if (color == null
        || !Float.isFinite(color.r)
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
      throw new IllegalArgumentException("Color channels must be finite and in [0, 1]");
  }
}
