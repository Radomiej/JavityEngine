package org.javity.sequence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Immutable beat-based cue data. Cue ties retain track declaration and cue declaration order. */
public final class Sequence {
  private final double durationBeats;
  private final List<Track> tracks;
  private final List<Cue> orderedCues;

  public Sequence(double durationBeats, List<Track> tracks) {
    if (!Double.isFinite(durationBeats) || durationBeats <= 0)
      throw new IllegalArgumentException("Sequence duration must be finite and positive");
    this.durationBeats = durationBeats;
    this.tracks = List.copyOf(Objects.requireNonNull(tracks, "tracks"));

    List<IndexedCue> indexed = new ArrayList<>();
    for (int trackIndex = 0; trackIndex < this.tracks.size(); trackIndex++) {
      List<Cue> cues = this.tracks.get(trackIndex).cues();
      for (int cueIndex = 0; cueIndex < cues.size(); cueIndex++) {
        Cue cue = cues.get(cueIndex);
        if (cue.beat() >= durationBeats)
          throw new IllegalArgumentException("Cue beat must be less than sequence duration");
        indexed.add(new IndexedCue(trackIndex, cueIndex, cue));
      }
    }
    indexed.sort(
        Comparator.comparingDouble((IndexedCue entry) -> entry.cue().beat())
            .thenComparingInt(IndexedCue::trackIndex)
            .thenComparingInt(IndexedCue::cueIndex));
    this.orderedCues = indexed.stream().map(IndexedCue::cue).toList();
  }

  public double durationBeats() {
    return durationBeats;
  }

  public List<Track> tracks() {
    return tracks;
  }

  List<Cue> orderedCues() {
    return orderedCues;
  }

  /** Starts a builder whose direct cues form the first track. */
  public static Builder builder(double durationBeats) {
    return new Builder(durationBeats);
  }

  public static final class Builder {
    private final double durationBeats;
    private final List<Cue> directCues = new ArrayList<>();
    private final List<Track> tracks = new ArrayList<>();

    private Builder(double durationBeats) {
      if (!Double.isFinite(durationBeats) || durationBeats <= 0)
        throw new IllegalArgumentException("Sequence duration must be finite and positive");
      this.durationBeats = durationBeats;
    }

    public Builder cue(double beat, Runnable action) {
      directCues.add(new Cue(beat, action));
      return this;
    }

    public Builder track(Track track) {
      flushDirectCues();
      tracks.add(Objects.requireNonNull(track, "track"));
      return this;
    }

    public Sequence build() {
      List<Track> result = new ArrayList<>(tracks);
      if (!directCues.isEmpty()) result.add(new Track(directCues));
      return new Sequence(durationBeats, result);
    }

    private void flushDirectCues() {
      if (directCues.isEmpty()) return;
      tracks.add(new Track(directCues));
      directCues.clear();
    }
  }

  private record IndexedCue(int trackIndex, int cueIndex, Cue cue) {}
}
