package org.javity.animation;

import java.util.Objects;

/** Mutable playback state returned by {@link ModelAnimator}. */
public final class ModelPlayback {
  final PlaybackClock clock;
  private final ModelAnimator owner;

  ModelPlayback(PlaybackClock clock, ModelAnimator owner) {
    this.clock = clock;
    this.owner = owner;
  }

  public String animationName() {
    return clock.name;
  }

  public PlayMode mode() {
    return clock.mode;
  }

  public double time() {
    return clock.time();
  }

  public double duration() {
    return clock.duration;
  }

  public double normalizedTime() {
    return clock.normalizedTime();
  }

  public double speed() {
    return clock.speed();
  }

  public ModelPlayback setSpeed(double speed) {
    clock.setSpeed(speed);
    return this;
  }

  public ModelPlayback pause() {
    clock.pause();
    return this;
  }

  public ModelPlayback resume() {
    clock.resume();
    return this;
  }

  public boolean isPaused() {
    return clock.isPaused();
  }

  public boolean isPlaying() {
    return clock.isPlaying();
  }

  public boolean isCompleted() {
    return clock.isCompleted();
  }

  public boolean isActive() {
    return clock.isActive();
  }

  public ModelPlayback onLoop(Runnable callback) {
    clock.onLoop(Objects.requireNonNull(callback));
    return this;
  }

  /** Runs once on the first eligible active, unpaused component update. */
  public ModelPlayback onStart(Runnable callback) {
    clock.onStart(Objects.requireNonNull(callback));
    return this;
  }

  public ModelPlayback onComplete(Runnable callback) {
    clock.onComplete(Objects.requireNonNull(callback));
    return this;
  }

  /** Rewinds this playback only if it is still the animator's current state. */
  /** Seeks silently and synchronously applies the corresponding pose or frame. */
  public ModelPlayback seek(double seconds) {
    return owner.seek(this, seconds);
  }

  /** Seeks silently to a finite normalized time in the inclusive range [0, 1]. */
  public ModelPlayback seekNormalized(double normalizedTime) {
    if (!Double.isFinite(normalizedTime) || normalizedTime < 0 || normalizedTime > 1)
      throw new IllegalArgumentException("Normalized seek must be finite and between 0 and 1");
    return seek(normalizedTime * clock.duration);
  }

  /** Binds a callback to marker metadata declared by this playback's transition. */
  public ModelPlayback onMarker(String name, Runnable callback) {
    clock.onMarker(name, Objects.requireNonNull(callback));
    return this;
  }

  /** Adds or binds a marker that belongs only to this playback. */
  public ModelPlayback onMarker(String name, double normalizedTime, Runnable callback) {
    clock.onMarker(name, normalizedTime, Objects.requireNonNull(callback));
    return this;
  }

  public ModelPlayback restart() {
    return owner.restart(this);
  }
}
