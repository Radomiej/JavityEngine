package org.javity.animation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.javity.core.GameSystem;

/** Opt-in, bounded scene-owned exact local-pose sharing. Playback remains per animator. */
public final class PoseBatchSystem extends GameSystem {
  public record SourceStats(int retainedSnapshots, long snapshotsCreated, long comparisons) {}

  private final ExactPoseCache cache;
  private final int capacity;
  private final List<SharedPoseData> sources = new ArrayList<>();
  private long snapshotsCreated, comparisons;

  public PoseBatchSystem(int maxEntries) {
    cache = new ExactPoseCache(maxEntries);
    capacity = maxEntries;
  }

  SharedPoseData source(
      SharedPoseData previous,
      Predicate<SharedPoseData> matches,
      Supplier<SharedPoseData> capture) {
    if (previous != null) {
      comparisons++;
      if (matches.test(previous)) return previous;
    }
    for (SharedPoseData candidate : sources) {
      if (candidate == previous) continue;
      comparisons++;
      if (matches.test(candidate)) return candidate;
    }
    SharedPoseData result = capture.get();
    snapshotsCreated++;
    if (sources.size() < capacity) sources.add(result);
    return result;
  }

  ExactPoseCache.Pose resolve(ExactPoseKey key, Supplier<float[]> sampler) {
    // Immediate compose calls can occur during init/UI or after WORLD, including paused frames.
    cache.beginFrame(getGame().getFrameIndex());
    return cache.resolvePose(key, sampler);
  }

  @Override
  public void onUiUpdate(float delta) {
    cache.beginFrame(getGame().getFrameIndex());
  }

  public ExactPoseCache.Stats stats() {
    return cache.stats();
  }

  public SourceStats sourceStats() {
    return new SourceStats(sources.size(), snapshotsCreated, comparisons);
  }

  @Override
  protected void onRelease() {
    cache.clear();
    sources.clear();
  }
}
