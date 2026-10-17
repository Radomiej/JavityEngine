package org.javity.ui.controls;

import java.util.Objects;
import org.javity.effects.Ease;

/** Headless value model for a foreground and its delayed trail. Times are in seconds. */
public final class ProgressModel {
  private float min;
  private float max = 1;
  private float value;
  private float displayed;
  private float delayed;
  private float fromDisplayed;
  private float fromDelayed;
  private float target;
  private float duration;
  private float delay;
  private double elapsed;
  private Ease ease = Ease.LINEAR;
  private Runnable onComplete;
  private long generation;
  private boolean animating;
  private boolean closed;
  private boolean increasing;

  public float getMin() {
    return min;
  }

  public float getMax() {
    return max;
  }

  public float getValue() {
    return value;
  }

  public float getDisplayedValue() {
    return displayed;
  }

  public float getDelayedIncrease() {
    return increasing ? delayed : displayed;
  }

  public float getDelayedDecrease() {
    return increasing ? displayed : delayed;
  }

  public boolean isAnimating() {
    return animating;
  }

  public boolean isIncreasing() {
    return increasing;
  }

  public void setRange(float min, float max) {
    requireOpen();
    finite(min, "min");
    finite(max, "max");
    if (!(min < max)) throw new IllegalArgumentException("Progress min must be less than max");
    this.min = min;
    this.max = max;
    value = clamp(value);
    displayed = clamp(displayed);
    delayed = clamp(delayed);
    target = clamp(target);
    fromDisplayed = clamp(fromDisplayed);
    fromDelayed = clamp(fromDelayed);
    if (animating && displayed == target && delayed == target) finish(generation);
  }

  public void setDuration(float seconds) {
    requireOpen();
    this.duration = nonnegative(seconds, "duration");
  }

  public void setDelay(float seconds) {
    requireOpen();
    this.delay = nonnegative(seconds, "delay");
  }

  public void setEase(Ease ease) {
    requireOpen();
    this.ease = Objects.requireNonNull(ease, "ease");
  }

  /** Starts the latest transition. Repeating the current target does not restart it. */
  public void setValue(float targetValue) {
    requireOpen();
    finite(targetValue, "value");
    float next = clamp(targetValue);
    if (next == value) return;
    long command = ++generation;
    value = next;
    target = next;
    increasing = next > displayed;
    fromDisplayed = displayed;
    fromDelayed = increasing ? displayed : Math.max(displayed, delayed);
    delayed = fromDelayed;
    elapsed = 0;
    animating = true;
    if (duration == 0 && delay == 0) {
      displayed = delayed = target;
      finish(command);
    } else {
      if (duration == 0) {
        if (increasing) delayed = target;
        else displayed = target;
      }
    }
  }

  public void setOnComplete(Runnable callback) {
    requireOpen();
    onComplete = callback;
  }

  /** Advances only by the caller supplied (UI/unscaled) time. */
  public void act(float deltaSeconds) {
    requireOpen();
    finite(deltaSeconds, "delta");
    if (deltaSeconds < 0) throw new IllegalArgumentException("delta must be nonnegative");
    if (!animating || deltaSeconds == 0) return;
    long command = generation;
    elapsed += deltaSeconds;
    if (increasing) {
      delayed =
          duration == 0
              ? target
              : interpolate(fromDelayed, target, (float) Math.min(1d, elapsed / duration));
      if (elapsed >= delay) {
        float progress = duration == 0 ? 1 : (float) Math.min(1d, (elapsed - delay) / duration);
        displayed = interpolate(fromDisplayed, target, progress);
      }
    } else {
      displayed =
          duration == 0
              ? target
              : interpolate(fromDisplayed, target, (float) Math.min(1d, elapsed / duration));
      if (elapsed >= delay) {
        float progress = duration == 0 ? 1 : (float) Math.min(1d, (elapsed - delay) / duration);
        delayed = interpolate(fromDelayed, target, progress);
      }
    }
    if (elapsed >= (double) delay + duration
        && (increasing ? displayed == target : delayed == target)) {
      displayed = delayed = target;
      finish(command);
    }
  }

  /** Jumps to the target and completes the current command once. */
  public void finishAnimation() {
    requireOpen();
    if (!animating) return;
    displayed = delayed = target;
    finish(generation);
  }

  /** Cancels callbacks and future updates; safe to call repeatedly. */
  public void close() {
    if (closed) return;
    ++generation;
    animating = false;
    onComplete = null;
    closed = true;
  }

  private float interpolate(float from, float to, float amount) {
    double t = Math.max(0d, Math.min(1d, amount));
    double curved = ease.apply(t);
    if (!Double.isFinite(curved)) curved = t;
    curved = Math.max(0d, Math.min(1d, curved));
    return clamp((float) ((double) from + ((double) to - from) * curved));
  }

  private void finish(long command) {
    if (command != generation || !animating) return;
    animating = false;
    Runnable callback = onComplete;
    if (callback != null && !closed && command == generation) callback.run();
  }

  private float clamp(float value) {
    return Math.max(min, Math.min(max, value));
  }

  private static float nonnegative(float value, String name) {
    finite(value, name);
    if (value < 0) throw new IllegalArgumentException(name + " must be nonnegative");
    return value;
  }

  private static void finite(float value, String name) {
    if (!Float.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
  }

  private void requireOpen() {
    if (closed) throw new IllegalStateException("ProgressModel is closed");
  }
}
