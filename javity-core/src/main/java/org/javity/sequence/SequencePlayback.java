package org.javity.sequence;

import java.util.Objects;

/** Mutable handle for one sequence run. */
public final class SequencePlayback implements AutoCloseable {
  enum State {
    PLAYING,
    PAUSED,
    COMPLETED,
    STOPPED
  }

  final Sequencer owner;
  final Sequence sequence;
  double bpm;
  final boolean loop;
  final long generation;
  final long generationFrame;
  State state = State.PLAYING;
  double elapsedBeats;
  long cycle;
  int cueIndex;
  long tempoRevision;
  long controlRevision;
  Runnable onComplete;
  Runnable onLoop;

  SequencePlayback(
      Sequencer owner,
      Sequence sequence,
      double bpm,
      boolean loop,
      long generation,
      long generationFrame) {
    this.owner = owner;
    this.sequence = sequence;
    this.bpm = bpm;
    this.loop = loop;
    this.generation = generation;
    this.generationFrame = generationFrame;
  }

  public Sequence sequence() {
    return sequence;
  }

  public double bpm() {
    return bpm;
  }

  /** Changes tempo while preserving the current beat position. */
  public SequencePlayback setBpm(double bpm) {
    owner.setBpm(this, bpm);
    return this;
  }

  public boolean loops() {
    return loop;
  }

  /** Current cycle-local position. A looping playback reports zero at an exact loop boundary. */
  public double playheadBeats() {
    if (!loop) return Math.min(elapsedBeats, sequence.durationBeats());
    double position = elapsedBeats % sequence.durationBeats();
    return position == 0 ? 0 : position;
  }

  public boolean isPlaying() {
    return state == State.PLAYING && owner.isCurrent(this);
  }

  public boolean isPaused() {
    return state == State.PAUSED && owner.isCurrent(this);
  }

  public boolean isCompleted() {
    return state == State.COMPLETED;
  }

  public boolean isActive() {
    return (state == State.PLAYING || state == State.PAUSED) && owner.isCurrent(this);
  }

  public SequencePlayback pause() {
    if (state == State.PLAYING && owner.isCurrent(this)) {
      state = State.PAUSED;
      controlRevision++;
    }
    return this;
  }

  public SequencePlayback resume() {
    if (state == State.PAUSED && owner.isCurrent(this)) {
      state = State.PLAYING;
      controlRevision++;
    }
    return this;
  }

  public SequencePlayback stop() {
    owner.stop(this);
    return this;
  }

  public SequencePlayback onComplete(Runnable callback) {
    onComplete = Objects.requireNonNull(callback, "callback");
    return this;
  }

  public SequencePlayback onLoop(Runnable callback) {
    onLoop = Objects.requireNonNull(callback, "callback");
    return this;
  }

  @Override
  public void close() {
    stop();
  }

  void stopInternal() {
    if (state == State.PLAYING || state == State.PAUSED) {
      state = State.STOPPED;
      controlRevision++;
    }
  }
}
