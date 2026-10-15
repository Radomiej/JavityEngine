package org.javity.animation;

import java.util.Objects;

/** Per-instance mixer, clip, or layer playback handle. */
public final class ModelPosePlayback {
  interface Host {
    void requireCurrent(ModelPosePlayback playback);
    ModelPosePlayback restart(ModelPosePlayback playback);
    ModelPosePlayback seek(ModelPosePlayback playback, double seconds);
    void parameter(ModelPosePlayback playback, double value);
    ModelPosePlayback stopped(ModelPosePlayback playback);
  }

  final PlaybackClock clock;
  final Host host;
  final LinearModelMixer mixer;
  double parameter;

  ModelPosePlayback(PlaybackClock clock, Host host, LinearModelMixer mixer, double parameter) {
    this.clock = clock;
    this.host = host;
    this.mixer = mixer;
    this.parameter = parameter;
  }

  public String animationName() { return clock.name; }
  public PlayMode mode() { return clock.mode; }
  public double time() { return clock.time(); }
  public double duration() { return clock.duration; }
  public double normalizedTime() { return clock.normalizedTime(); }
  public double speed() { return clock.speed(); }
  public double parameter() { return parameter; }
  public boolean isPaused() { return clock.isPaused(); }
  public boolean isPlaying() { return clock.isPlaying(); }
  public boolean isCompleted() { return clock.isCompleted(); }
  public boolean isActive() { return clock.isActive(); }

  public ModelPosePlayback setParameter(double value) {
    host.requireCurrent(this);
    if (mixer == null) throw new IllegalStateException("This playback has no mixer parameter");
    if (!Double.isFinite(value)) throw new IllegalArgumentException("Mixer parameter must be finite");
    host.parameter(this, value);
    return this;
  }

  public ModelPosePlayback setSpeed(double value) {
    host.requireCurrent(this);
    clock.setSpeed(value);
    return this;
  }

  public ModelPosePlayback pause() {
    host.requireCurrent(this);
    clock.pause();
    return this;
  }

  public ModelPosePlayback resume() {
    host.requireCurrent(this);
    clock.resume();
    return this;
  }

  public ModelPosePlayback onStart(Runnable callback) {
    host.requireCurrent(this);
    clock.onStart(Objects.requireNonNull(callback));
    return this;
  }

  public ModelPosePlayback onLoop(Runnable callback) {
    host.requireCurrent(this);
    clock.onLoop(Objects.requireNonNull(callback));
    return this;
  }

  public ModelPosePlayback onComplete(Runnable callback) {
    host.requireCurrent(this);
    clock.onComplete(Objects.requireNonNull(callback));
    return this;
  }

  public ModelPosePlayback onMarker(String name, Runnable callback) {
    host.requireCurrent(this);
    clock.onMarker(name, Objects.requireNonNull(callback));
    return this;
  }

  public ModelPosePlayback onMarker(String name, double normalizedTime, Runnable callback) {
    host.requireCurrent(this);
    clock.onMarker(name, normalizedTime, Objects.requireNonNull(callback));
    return this;
  }

  public ModelPosePlayback seek(double seconds) {
    return host.seek(this, seconds);
  }

  public ModelPosePlayback seekNormalized(double normalized) {
    if (!Double.isFinite(normalized) || normalized < 0 || normalized > 1)
      throw new IllegalArgumentException("Normalized seek must be finite and between 0 and 1");
    return seek(normalized * clock.duration);
  }

  public ModelPosePlayback restart() {
    return host.restart(this);
  }

  public ModelPosePlayback stop() {
    return host.stopped(this);
  }
}
