package org.javity.sequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.javity.core.Component;
import org.javity.effects.Clock;

/** Component that advances beat/cue sequences using scene WORLD or unscaled UI time. */
public final class Sequencer extends Component {
  private static final int MAX_STEP_WORK = 1024;

  private final Clock clock;
  private SequencePlayback current;
  private long generation;

  public Sequencer(Clock clock) {
    this.clock = Objects.requireNonNull(clock, "clock");
    updateMode = clock == Clock.WORLD ? UpdateMode.WORLD : UpdateMode.UI;
  }

  public Clock clock() {
    return clock;
  }

  /** Replaces the current run. The returned run first becomes eligible on the next game frame. */
  public SequencePlayback play(Sequence sequence, double bpm, boolean loop) {
    Objects.requireNonNull(sequence, "sequence");
    if (!Double.isFinite(bpm) || bpm <= 0)
      throw new IllegalArgumentException("BPM must be finite and positive");
    if (!componentIsAlive())
      throw new IllegalStateException("Sequencer must belong to a live game object before play");
    if (generation == Long.MAX_VALUE)
      throw new IllegalStateException("Sequence generation exhausted");
    if (current != null) current.stopInternal();
    current =
        new SequencePlayback(this, sequence, bpm, loop, ++generation, getGame().getFrameIndex());
    return current;
  }

  /** The most recently selected run, including a completed run; null before the first play. */
  public SequencePlayback current() {
    return current;
  }

  boolean isCurrent(SequencePlayback playback) {
    return current == playback && playback.generation == generation && componentIsAlive();
  }

  void stop(SequencePlayback playback) {
    if (current == playback && playback.generation == generation) playback.stopInternal();
  }

  void setBpm(SequencePlayback playback, double bpm) {
    if (!Double.isFinite(bpm) || bpm <= 0)
      throw new IllegalArgumentException("BPM must be finite and positive");
    if (current != playback || playback.generation != generation) return;
    if (playback.bpm != bpm) {
      playback.bpm = bpm;
      playback.tempoRevision++;
    }
  }

  @Override
  public void onUpdate(float delta) {
    SequencePlayback playback = current;
    if (playback == null || playback.state != SequencePlayback.State.PLAYING) return;
    if (!componentIsAlive()) {
      playback.stopInternal();
      return;
    }
    if (getGame().getFrameIndex() <= playback.generationFrame) return;

    double next = playback.elapsedBeats + delta * playback.bpm / 60.0;
    if (!Double.isFinite(next)) throw new IllegalArgumentException("Sequence step is too large");
    long tempoRevision = playback.tempoRevision;
    long controlRevision = playback.controlRevision;
    List<Dispatch> dispatches = preflight(playback, next);
    for (Dispatch dispatch : dispatches) {
      if (!isDispatchCurrent(playback)) return;
      if (playback.state != SequencePlayback.State.PLAYING || !canContinueDispatch()) return;
      if (playback.tempoRevision != tempoRevision || playback.controlRevision != controlRevision)
        return;
      playback.elapsedBeats = dispatch.beat;
      if (dispatch.kind == DispatchKind.CUE) {
        playback.cueIndex++;
        invoke(playback, dispatch.cue.action());
      } else if (dispatch.kind == DispatchKind.LOOP) {
        playback.cycle++;
        playback.cueIndex = 0;
        invoke(playback, playback.onLoop);
      } else {
        playback.state = SequencePlayback.State.COMPLETED;
        invokeCompletion(playback);
      }
      if (!isDispatchCurrent(playback)) return;
      if (playback.state != SequencePlayback.State.PLAYING || !canContinueDispatch()) return;
      if (playback.tempoRevision != tempoRevision || playback.controlRevision != controlRevision)
        return;
    }
    if (isDispatchCurrent(playback)
        && playback.state == SequencePlayback.State.PLAYING
        && canContinueDispatch()
        && playback.tempoRevision == tempoRevision
        && playback.controlRevision == controlRevision) playback.elapsedBeats = next;
  }

  private List<Dispatch> preflight(SequencePlayback playback, double target) {
    List<Dispatch> result = new ArrayList<>();
    long cycle = playback.cycle;
    int cueIndex = playback.cueIndex;
    List<Cue> cues = playback.sequence.orderedCues();
    int work = 0;
    while (true) {
      if (playback.loop) {
        if (cueIndex < cues.size()) {
          double cueBeat = cycle * playback.sequence.durationBeats() + cues.get(cueIndex).beat();
          if (!Double.isFinite(cueBeat))
            throw new IllegalArgumentException("Sequence time overflow");
          if (cueBeat <= target) {
            if (++work > MAX_STEP_WORK) throw tooMuchWork();
            result.add(new Dispatch(DispatchKind.CUE, cueBeat, cues.get(cueIndex)));
            cueIndex++;
            continue;
          }
        }
        double boundary = (cycle + 1.0) * playback.sequence.durationBeats();
        if (!Double.isFinite(boundary))
          throw new IllegalArgumentException("Sequence time overflow");
        if (boundary <= target) {
          if (++work > MAX_STEP_WORK) throw tooMuchWork();
          result.add(new Dispatch(DispatchKind.LOOP, boundary, null));
          if (cycle == Long.MAX_VALUE)
            throw new IllegalArgumentException("Sequence loop count overflow");
          cycle++;
          cueIndex = 0;
          continue;
        }
        break;
      }

      if (cueIndex < cues.size()) {
        double cueBeat = cues.get(cueIndex).beat();
        if (cueBeat <= target) {
          if (++work > MAX_STEP_WORK) throw tooMuchWork();
          result.add(new Dispatch(DispatchKind.CUE, cueBeat, cues.get(cueIndex)));
          cueIndex++;
          continue;
        }
      }
      if (target >= playback.sequence.durationBeats()) {
        if (++work > MAX_STEP_WORK) throw tooMuchWork();
        result.add(new Dispatch(DispatchKind.COMPLETE, playback.sequence.durationBeats(), null));
      }
      break;
    }
    return result;
  }

  private static IllegalArgumentException tooMuchWork() {
    return new IllegalArgumentException("Sequence update exceeds the 1024 cue/cycle work limit");
  }

  private void invoke(SequencePlayback playback, Runnable callback) {
    if (callback == null) return;
    try {
      callback.run();
    } catch (RuntimeException | Error failure) {
      playback.stopInternal();
      throw failure;
    }
  }

  private void invokeCompletion(SequencePlayback playback) {
    if (playback.onComplete == null) return;
    try {
      playback.onComplete.run();
    } catch (RuntimeException | Error failure) {
      playback.state = SequencePlayback.State.STOPPED;
      throw failure;
    }
  }

  private boolean isDispatchCurrent(SequencePlayback playback) {
    if (current != playback || playback.generation != generation) return false;
    if (!componentIsAlive()) {
      playback.stopInternal();
      return false;
    }
    return true;
  }

  private boolean componentIsAlive() {
    return getGame() != null
        && !getGame().isDisposed()
        && getGameObject() != null
        && !getGameObject().isDestroyed()
        && (isInitialized() || getGameObject().getComponents().contains(this));
  }

  private boolean canContinueDispatch() {
    return componentIsAlive()
        && isEnabled()
        && getGameObject().isActive()
        && (clock == Clock.UI || (!getGame().isPaused() && !getGame().isWorldFrozen()));
  }

  @Override
  public void onDestroy() {
    if (current != null) current.stopInternal();
  }

  @Override
  protected void onRelease() {
    if (current != null) current.stopInternal();
  }

  private enum DispatchKind {
    CUE,
    LOOP,
    COMPLETE
  }

  private record Dispatch(DispatchKind kind, double beat, Cue cue) {}
}
