package org.javity.animation;

import java.util.Objects;

/** One independently clocked sparse TRS override layer. */
public final class ModelAnimationLayer implements ModelPosePlayback.Host {
  final ModelPoseAnimator owner;
  final String name;
  final NodeMask mask;
  boolean[] resolvedMask;
  double weight;
  ModelPosePlayback current;
  private double fadeFrom, fadeTo, fadeElapsed, fadeDuration;
  boolean fading;
  private long fadeGeneration;
  private long fadeEligibleFrame;

  ModelAnimationLayer(ModelPoseAnimator owner, String name, NodeMask mask) {
    this.owner = owner;
    this.name = name;
    this.mask = mask;
  }

  public String name() {
    return name;
  }

  public NodeMask mask() {
    return mask;
  }

  public double weight() {
    return weight;
  }

  public ModelPosePlayback current() {
    return current;
  }

  public ModelAnimationLayer setWeight(double value) {
    requireUsable();
    checkWeight(value);
    fading = false;
    fadeGeneration++;
    weight = value;
    owner.composeNow();
    return this;
  }

  /** Linearly fades this layer's override weight on the owner's WORLD clock. */
  public ModelAnimationLayer fadeTo(double targetWeight, double seconds) {
    requireUsable();
    checkWeight(targetWeight);
    if (!Double.isFinite(seconds) || seconds < 0)
      throw new IllegalArgumentException("Fade duration must be finite and non-negative");
    fadeGeneration++;
    if (seconds == 0) {
      fading = false;
      weight = targetWeight;
      owner.composeNow();
      return this;
    }
    fadeFrom = weight;
    fadeTo = targetWeight;
    fadeElapsed = 0;
    fadeDuration = seconds;
    fading = true;
    fadeEligibleFrame = owner.frameIndex() + 1;
    owner.composeNow();
    return this;
  }

  public ModelAnimationLayer stop() {
    requireUsable();
    fadeGeneration++;
    fading = false;
    if (current != null) current.clock.stop();
    current = null;
    owner.composeNow();
    return this;
  }

  public ModelPosePlayback play(String animationName, PlayMode mode) {
    return play(AnimationTransition.clip(animationName).mode(mode));
  }

  public ModelPosePlayback play(AnimationTransition transition) {
    Objects.requireNonNull(transition);
    requireUsable();
    if (transition.fadeSeconds() > 0)
      throw new IllegalArgumentException(
          "Clip crossfade is unsupported; fade the layer weight instead");
    ModelPosePlayback next = owner.createLayerPlayback(this, transition);
    if (current != null) current.clock.stop();
    current = next;
    owner.composeNow();
    return next;
  }

  void updateFade(float delta) {
    if (!fading || owner.frameIndex() < fadeEligibleFrame) return;
    fadeElapsed = Math.min(fadeDuration, fadeElapsed + delta);
    double progress = fadeElapsed / fadeDuration;
    weight = fadeFrom + (fadeTo - fadeFrom) * progress;
    if (fadeElapsed >= fadeDuration) fading = false;
  }

  boolean isFading() {
    return fading;
  }

  private static void checkWeight(double value) {
    if (!Double.isFinite(value) || value < 0 || value > 1)
      throw new IllegalArgumentException("Layer weight must be between 0 and 1");
  }

  private void requireUsable() {
    owner.requireLayerUsable(this);
  }

  @Override
  public void requireCurrent(ModelPosePlayback playback) {
    requireUsable();
    if (current != playback || !playback.clock.isActive())
      throw new IllegalStateException("Playback is no longer current");
  }

  @Override
  public ModelPosePlayback restart(ModelPosePlayback playback) {
    requireCurrent(playback);
    playback.clock.restart();
    owner.composeNow();
    return playback;
  }

  @Override
  public ModelPosePlayback seek(ModelPosePlayback playback, double seconds) {
    requireCurrent(playback);
    if (!Double.isFinite(seconds) || seconds < 0 || seconds > playback.duration())
      throw new IllegalArgumentException("Seek time must be finite and within the clip duration");
    playback.clock.seek(seconds);
    owner.composeNow();
    return playback;
  }

  @Override
  public ModelPosePlayback stopped(ModelPosePlayback playback) {
    requireCurrent(playback);
    playback.clock.stop();
    current = null;
    owner.composeNow();
    return playback;
  }

  @Override
  public void parameter(ModelPosePlayback playback, double value) {
    throw new IllegalStateException("A layer playback has no mixer parameter");
  }
}
