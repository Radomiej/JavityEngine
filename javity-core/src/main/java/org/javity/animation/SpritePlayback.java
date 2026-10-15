package org.javity.animation;

import java.util.Objects;

/** Mutable playback state returned by {@link SpriteAnimationPlayer}. */
public final class SpritePlayback {
  final PlaybackClock clock;
  private final SpriteAnimationPlayer owner;

  SpritePlayback(PlaybackClock clock, SpriteAnimationPlayer owner) {
    this.clock = clock;
    this.owner = owner;
  }

  public String clipName() {
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

  public SpritePlayback setSpeed(double speed) {
    clock.setSpeed(speed);
    return this;
  }

  public SpritePlayback pause() {
    clock.pause();
    return this;
  }

  public SpritePlayback resume() {
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

  public SpritePlayback onLoop(Runnable callback) {
    clock.onLoop(Objects.requireNonNull(callback));
    return this;
  }

  /** Runs once on the first eligible active, unpaused component update. */
  public SpritePlayback onStart(Runnable callback) {
    clock.onStart(Objects.requireNonNull(callback));
    return this;
  }

  public SpritePlayback onComplete(Runnable callback) {
    clock.onComplete(Objects.requireNonNull(callback));
    return this;
  }

  /** Seeks silently and synchronously applies the corresponding pose or frame. */
  public SpritePlayback seek(double seconds) {
    return owner.seek(this, seconds);
  }

  /** Seeks silently to a finite normalized time in the inclusive range [0, 1]. */
  public SpritePlayback seekNormalized(double normalizedTime) {
    if (!Double.isFinite(normalizedTime) || normalizedTime < 0 || normalizedTime > 1)
      throw new IllegalArgumentException("Normalized seek must be finite and between 0 and 1");
    return seek(normalizedTime * clock.duration);
  }

  /** Binds a callback to marker metadata declared by this playback's transition. */
  public SpritePlayback onMarker(String name, Runnable callback) {
    clock.onMarker(name, Objects.requireNonNull(callback));
    return this;
  }

  /** Adds or binds a marker that belongs only to this playback. */
  public SpritePlayback onMarker(String name, double normalizedTime, Runnable callback) {
    clock.onMarker(name, normalizedTime, Objects.requireNonNull(callback));
    return this;
  }

  public SpritePlayback restart() {
    return owner.restart(this);
  }
}
