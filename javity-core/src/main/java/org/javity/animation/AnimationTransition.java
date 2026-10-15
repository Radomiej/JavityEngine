package org.javity.animation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable reusable defaults for creating a fresh animation playback. */
public final class AnimationTransition {
  private final String clipName;
  private final PlayMode mode;
  private final double fadeSeconds;
  private final double speed;
  private final double normalizedStart;
  private final List<AnimationMarker> markers;

  public AnimationTransition(
      String clipName,
      PlayMode mode,
      double fadeSeconds,
      double speed,
      double normalizedStart,
      List<AnimationMarker> markers) {
    if (clipName == null || clipName.isBlank())
      throw new IllegalArgumentException("Clip name is required");
    this.clipName = clipName;
    this.mode = Objects.requireNonNull(mode);
    if (!Double.isFinite(fadeSeconds) || fadeSeconds < 0 || fadeSeconds > Float.MAX_VALUE)
      throw new IllegalArgumentException("Fade duration must be finite and non-negative");
    if (!Double.isFinite(speed) || speed < 0)
      throw new IllegalArgumentException("Speed must be finite and non-negative");
    if (!Double.isFinite(normalizedStart) || normalizedStart < 0 || normalizedStart > 1)
      throw new IllegalArgumentException("Start time must be finite and between 0 and 1");
    this.fadeSeconds = fadeSeconds;
    this.speed = speed;
    this.normalizedStart = normalizedStart;
    this.markers = List.copyOf(Objects.requireNonNull(markers));
    Set<String> names = new HashSet<>();
    for (AnimationMarker marker : this.markers) {
      Objects.requireNonNull(marker, "Marker cannot be null");
      if (!names.add(marker.name()))
        throw new IllegalArgumentException("Duplicate animation marker: " + marker.name());
    }
  }

  public static AnimationTransition clip(String name) {
    return new AnimationTransition(name, PlayMode.ONCE, 0, 1, 0, List.of());
  }

  public String clipName() {
    return clipName;
  }

  public PlayMode mode() {
    return mode;
  }

  public double fadeSeconds() {
    return fadeSeconds;
  }

  public double speed() {
    return speed;
  }

  public double normalizedStart() {
    return normalizedStart;
  }

  public List<AnimationMarker> markers() {
    return markers;
  }

  public AnimationTransition loop() {
    return copy(PlayMode.LOOP, fadeSeconds, speed, normalizedStart, markers);
  }

  public AnimationTransition mode(PlayMode value) {
    return copy(Objects.requireNonNull(value), fadeSeconds, speed, normalizedStart, markers);
  }

  public AnimationTransition fade(double seconds) {
    return copy(mode, seconds, speed, normalizedStart, markers);
  }

  public AnimationTransition speed(double value) {
    return copy(mode, fadeSeconds, value, normalizedStart, markers);
  }

  public AnimationTransition startAt(double normalized) {
    return copy(mode, fadeSeconds, speed, normalized, markers);
  }

  public AnimationTransition markers(AnimationMarker... values) {
    Objects.requireNonNull(values);
    List<AnimationMarker> result = new ArrayList<>(List.of(values));
    return copy(mode, fadeSeconds, speed, normalizedStart, result);
  }

  private AnimationTransition copy(
      PlayMode nextMode,
      double nextFade,
      double nextSpeed,
      double nextStart,
      List<AnimationMarker> nextMarkers) {
    return new AnimationTransition(clipName, nextMode, nextFade, nextSpeed, nextStart, nextMarkers);
  }
}
