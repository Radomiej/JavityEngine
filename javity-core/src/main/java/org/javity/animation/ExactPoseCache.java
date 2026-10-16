package org.javity.animation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Scene-owned frame-local cache. Capacity overflow samples without retaining another entry. */
public final class ExactPoseCache {
  public record Stats(
      int retainedEntries,
      long frameHits,
      long frameMisses,
      long frameSamplerExecutions,
      long totalHits,
      long totalMisses,
      long totalSamplerExecutions) {}

  private final int maxEntries;
  private final Map<ExactPoseKey, float[]> entries = new LinkedHashMap<>();
  private long frameIndex;
  private boolean hasFrame;
  private long hits, misses, executions, totalHits, totalMisses, totalExecutions;

  public ExactPoseCache(int maxEntries) {
    if (maxEntries < 0) throw new IllegalArgumentException("Negative cache capacity");
    this.maxEntries = maxEntries;
  }

  public void beginFrame(long frameIndex) {
    if (hasFrame && this.frameIndex == frameIndex) return;
    entries.clear();
    hits = misses = executions = 0;
    this.frameIndex = frameIndex;
    hasFrame = true;
  }

  public float[] resolve(ExactPoseKey key, Supplier<float[]> sampler) {
    Objects.requireNonNull(key);
    Objects.requireNonNull(sampler);
    float[] value = entries.get(key);
    if (value != null) {
      hits++;
      totalHits++;
      return value.clone();
    }
    misses++;
    totalMisses++;
    executions++;
    totalExecutions++;
    value = Objects.requireNonNull(sampler.get(), "Sampler returned null");
    if (entries.size() < maxEntries) entries.put(key, value.clone());
    return value.clone();
  }

  /** Releases all retained pose/source references; cumulative metrics remain available. */
  public void clear() {
    entries.clear();
    hits = misses = executions = 0;
    hasFrame = false;
  }

  public Stats stats() {
    return new Stats(
        entries.size(), hits, misses, executions, totalHits, totalMisses, totalExecutions);
  }
}
