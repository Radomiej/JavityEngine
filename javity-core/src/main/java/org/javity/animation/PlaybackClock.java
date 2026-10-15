package org.javity.animation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Shared scene-time bookkeeping and bounded event dispatch for animation handles. */
final class PlaybackClock {
  private static final int MAX_EVENTS_PER_STEP = 1024;
  final String name;
  final double duration;
  final PlayMode mode;
  private final List<AnimationMarker> markers = new ArrayList<>();
  private final Map<String, Runnable> markerCallbacks = new LinkedHashMap<>();
  private double elapsed;
  private double speed = 1;
  private boolean paused;
  private boolean active = true;
  private boolean completed;
  private boolean startPending = true;
  private boolean startMarkerPending = true;
  private long generation;
  private Runnable startCallback;
  private Runnable loopCallback;
  private Runnable completeCallback;

  PlaybackClock(String name, double duration, PlayMode mode) {
    this(name, duration, mode, 0, 1, List.of());
  }

  PlaybackClock(
      String name,
      double duration,
      PlayMode mode,
      double normalizedStart,
      double speed,
      List<AnimationMarker> markers) {
    this.name = Objects.requireNonNull(name);
    if (!Double.isFinite(duration) || duration <= 0)
      throw new IllegalArgumentException("Duration must be finite and positive");
    if (!Double.isFinite(normalizedStart) || normalizedStart < 0 || normalizedStart > 1)
      throw new IllegalArgumentException("Start time must be finite and between 0 and 1");
    this.duration = duration;
    this.mode = Objects.requireNonNull(mode);
    setSpeed(speed);
    this.elapsed = (mode == PlayMode.LOOP && normalizedStart == 1) ? 0 : normalizedStart * duration;
    this.completed = mode == PlayMode.ONCE && normalizedStart == 1;
    Set<String> names = new HashSet<>();
    for (AnimationMarker marker : markers) {
      if (!names.add(marker.name()))
        throw new IllegalArgumentException("Duplicate animation marker: " + marker.name());
      this.markers.add(marker);
    }
    this.markers.sort(Comparator.comparingDouble(AnimationMarker::normalizedTime));
  }

  void setSpeed(double speed) {
    if (!Double.isFinite(speed) || speed < 0)
      throw new IllegalArgumentException("Speed must be finite and non-negative");
    this.speed = speed;
  }

  double speed() {
    return speed;
  }

  double rawTime() {
    return elapsed;
  }

  double time() {
    return mode == PlayMode.LOOP ? elapsed : Math.min(elapsed, duration);
  }

  double normalizedTime() {
    return mode == PlayMode.LOOP ? elapsed / duration : Math.min(1, elapsed / duration);
  }

  boolean isPaused() {
    return paused;
  }

  boolean isCompleted() {
    return completed;
  }

  boolean isActive() {
    return active;
  }

  boolean isPlaying() {
    return active && !paused && !completed;
  }

  void pause() {
    if (active && !completed && !paused) {
      paused = true;
      generation++;
    }
  }

  void resume() {
    if (active && !completed && paused) {
      paused = false;
      generation++;
    }
  }

  void stop() {
    active = false;
    paused = false;
    generation++;
  }

  void restart() {
    elapsed = 0;
    paused = false;
    active = true;
    completed = false;
    startPending = true;
    startMarkerPending = true;
    generation++;
  }

  void seek(double seconds) {
    if (!Double.isFinite(seconds) || seconds < 0 || seconds > duration)
      throw new IllegalArgumentException("Seek time must be finite and within the clip duration");
    elapsed = mode == PlayMode.LOOP && seconds == duration ? 0 : seconds;
    completed = mode == PlayMode.ONCE && seconds == duration;
    active = true;
    generation++;
  }

  boolean fireStart(BooleanSupplier isCurrent) {
    if (!isPlaying() || !startPending) return isPlaying() && isCurrent.getAsBoolean();
    startPending = false;
    long firedGeneration = generation;
    Runnable callback = startCallback;
    try {
      if (callback != null) callback.run();
    } catch (RuntimeException | Error failure) {
      stop();
      throw failure;
    }
    return generation == firedGeneration && isPlaying() && isCurrent.getAsBoolean();
  }

  void onStart(Runnable callback) {
    startCallback = Objects.requireNonNull(callback);
  }

  void onLoop(Runnable callback) {
    loopCallback = Objects.requireNonNull(callback);
  }

  void onComplete(Runnable callback) {
    completeCallback = Objects.requireNonNull(callback);
  }

  void onMarker(String name, Runnable callback) {
    Objects.requireNonNull(callback);
    if (markers.stream().noneMatch(marker -> marker.name().equals(name)))
      throw new IllegalArgumentException("Unknown animation marker: " + name);
    markerCallbacks.put(name, callback);
  }

  void onMarker(String name, double normalizedTime, Runnable callback) {
    Objects.requireNonNull(callback);
    AnimationMarker marker = new AnimationMarker(name, normalizedTime);
    AnimationMarker existing =
        markers.stream().filter(value -> value.name().equals(name)).findFirst().orElse(null);
    if (existing != null && existing.normalizedTime() != normalizedTime)
      throw new IllegalArgumentException("Animation marker already has a different time: " + name);
    if (existing == null) {
      markers.add(marker);
      markers.sort(Comparator.comparingDouble(AnimationMarker::normalizedTime));
    }
    markerCallbacks.put(name, callback);
  }

  Step preflight(float delta) {
    if (!Float.isFinite(delta) || delta < 0)
      throw new IllegalArgumentException("Playback delta must be finite and non-negative");
    if (!isPlaying() || speed == 0) return new Step(generation, elapsed, false, List.of(), false);
    double scaled = delta * speed;
    if (!Double.isFinite(scaled)) throw new IllegalArgumentException("Playback step is too large");
    double next = elapsed + scaled;
    if (!Double.isFinite(next)) throw new IllegalArgumentException("Playback time overflow");
    boolean finishing = mode == PlayMode.ONCE && next >= duration;
    double crossingEnd = next;
    if (mode == PlayMode.ONCE) {
      next = Math.min(next, duration);
      crossingEnd = next;
    }
    if (mode == PlayMode.LOOP) {
      double cycles = Math.floor(next / duration);
      if (!Double.isFinite(cycles) || cycles > MAX_EVENTS_PER_STEP)
        throw new IllegalArgumentException("Playback step crosses too many loops");
      next %= duration;
    }
    int lifecycleCallbacks =
        (startPending && startCallback != null ? 1 : 0)
            + (finishing && completeCallback != null ? 1 : 0);
    int eventBudget = MAX_EVENTS_PER_STEP - lifecycleCallbacks;
    if (eventBudget < 0)
      throw new IllegalArgumentException("Playback step crosses too many animation events");
    List<Dispatch> events = crossings(elapsed, crossingEnd, eventBudget);
    return new Step(generation, next, startMarkerPending, events, finishing);
  }

  void advance(Step step, BooleanSupplier isCurrent) {
    if (step.generation != generation || !isPlaying() || !isCurrent.getAsBoolean()) return;
    elapsed = step.nextElapsed;
    if (step.includesInitialStartMarker) startMarkerPending = false;
    for (Dispatch dispatch : step.events) {
      if (!dispatchStillCurrent(step, isCurrent)) return;
      try {
        dispatch.callback.run();
      } catch (RuntimeException | Error failure) {
        stop();
        throw failure;
      }
    }
    if (!dispatchStillCurrent(step, isCurrent)) return;
    if (step.finishing) {
      completed = true;
      Runnable callback = completeCallback;
      if (callback != null) {
        try {
          callback.run();
        } catch (RuntimeException | Error failure) {
          stop();
          throw failure;
        }
      }
    }
  }

  private boolean dispatchStillCurrent(Step step, BooleanSupplier isCurrent) {
    return generation == step.generation && isPlaying() && isCurrent.getAsBoolean();
  }

  private List<Dispatch> crossings(double before, double after, int eventBudget) {
    List<Dispatch> result = new ArrayList<>();
    int count = 0;
    List<AnimationMarker> ordered = orderedMarkers();
    if (startMarkerPending) {
      for (AnimationMarker marker : markers) {
        if (marker.normalizedTime() == 0) {
          count = countCrossing(count, eventBudget);
          addCallback(result, marker.name());
        }
      }
    }
    if (after <= before) return result;
    if (mode == PlayMode.ONCE) {
      for (AnimationMarker marker : ordered) {
        double eventTime = marker.normalizedTime() * duration;
        if (eventTime > before && eventTime <= after) {
          count = countCrossing(count, eventBudget);
          addCallback(result, marker.name());
        }
      }
      return result;
    }

    long firstCycle = (long) Math.floor(before / duration);
    long lastCycle = (long) Math.floor(after / duration);
    List<TimedDispatch> timed = new ArrayList<>();
    for (long cycle = firstCycle; cycle <= lastCycle; cycle++) {
      double cycleStart = cycle * duration;
      for (AnimationMarker marker : ordered) {
        double eventTime = cycleStart + marker.normalizedTime() * duration;
        if (!(eventTime > before && eventTime <= after)) continue;
        count = countCrossing(count, eventBudget);
        int rank = marker.normalizedTime() == 1 ? 0 : marker.normalizedTime() == 0 ? 2 : 1;
        Runnable callback = markerCallbacks.get(marker.name());
        if (callback != null) timed.add(new TimedDispatch(eventTime, rank, timed.size(), callback));
      }
      double boundary = (cycle + 1d) * duration;
      if (boundary > before && boundary <= after && loopCallback != null) {
        count = countCrossing(count, eventBudget);
        timed.add(new TimedDispatch(boundary, 1, timed.size(), loopCallback));
      }
    }
    timed.sort(
        Comparator.comparingDouble(TimedDispatch::time)
            .thenComparingInt(TimedDispatch::rank)
            .thenComparingInt(TimedDispatch::order));
    for (TimedDispatch item : timed) result.add(new Dispatch(item.callback));
    return result;
  }

  private int countCrossing(int count, int eventBudget) {
    if (++count > eventBudget)
      throw new IllegalArgumentException("Playback step crosses too many animation events");
    return count;
  }

  private List<AnimationMarker> orderedMarkers() {
    return markers;
  }

  private void addCallback(List<Dispatch> result, String name) {
    Runnable callback = markerCallbacks.get(name);
    if (callback != null) result.add(new Dispatch(callback));
  }

  record Step(
      long generation,
      double nextElapsed,
      boolean includesInitialStartMarker,
      List<Dispatch> events,
      boolean finishing) {}

  private record Dispatch(Runnable callback) {}

  private record TimedDispatch(double time, int rank, int order, Runnable callback) {}

  @FunctionalInterface
  interface BooleanSupplier {
    boolean getAsBoolean();
  }
}
