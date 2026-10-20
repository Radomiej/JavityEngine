package org.javity.animation;

import com.badlogic.gdx.graphics.g3d.model.NodeKeyframe;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;

/** Internal extraction policy. No retained baseline or pending movement survives a clock change. */
final class ModelRootMotion {
  final int nodeIndex;
  final Consumer<RootMotionDelta> sink;
  private final TranslationSnapshot reusableSnapshot = new TranslationSnapshot();

  ModelRootMotion(int nodeIndex, Consumer<RootMotionDelta> sink) {
    this.nodeIndex = nodeIndex;
    this.sink = sink;
  }

  RootMotionDelta plan(
      ModelPosePlayback playback,
      PlaybackClock.Step step,
      float delta,
      DoubleFunction<Vector3> sample) {
    PlaybackClock clock = playback.clock;
    if (!clock.isPlaying() || clock.speed() == 0 || delta == 0) return null;
    Vector3 before = sample.apply(clock.time());
    Vector3 after = sample.apply(step.nextElapsed());
    double cycles =
        clock.mode == PlayMode.LOOP
            ? Math.floor((clock.time() + delta * clock.speed()) / clock.duration)
            : 0;
    Vector3 start = cycles == 0 ? Vector3.Zero : sample.apply(0);
    Vector3 end = cycles == 0 ? Vector3.Zero : sample.apply(clock.duration);
    RootMotionDelta result =
        new RootMotionDelta(
            (float) ((double) after.x - before.x + cycles * ((double) end.x - start.x)),
            (float) ((double) after.y - before.y + cycles * ((double) end.y - start.y)),
            (float) ((double) after.z - before.z + cycles * ((double) end.z - start.z)));
    return result.x() == 0 && result.y() == 0 && result.z() == 0 ? null : result;
  }

  TranslationSnapshot captureTranslation(Array<?> source) {
    if (source == null) return null;
    TranslationSnapshot snapshot =
        reusableSnapshot.inUse ? new TranslationSnapshot() : reusableSnapshot;
    try {
      snapshot.inUse = true;
      snapshot.source = source;
      snapshot.keyCount = source.size;
      int required = Math.multiplyExact(source.size, 4);
      if (snapshot.bits.length < required) snapshot.bits = new int[required];
      // Preserve lazy sampler behavior for malformed, unused live keys: cancel motion rather
      // than introducing a new eager validation failure ahead of ordinary pose composition.
      if (snapshot.read(true)) return snapshot;
      snapshot.close();
      return null;
    } catch (RuntimeException | Error failure) {
      snapshot.close();
      throw failure;
    }
  }

  /**
   * Exact leased channel snapshot; ordinary ticks reuse this configuration's high-water storage.
   */
  static final class TranslationSnapshot implements AutoCloseable {
    private Array<?> source;
    private int[] bits = new int[0];
    private int keyCount;
    private boolean inUse;

    @Override
    public void close() {
      source = null;
      keyCount = 0;
      inUse = false;
    }

    boolean unchanged() {
      return inUse && source.size == keyCount && read(false);
    }

    private boolean read(boolean capture) {
      for (int i = 0; i < source.size; i++) {
        Object item = source.get(i);
        if (!(item instanceof NodeKeyframe<?> key) || !(key.value instanceof Vector3 value))
          return false;
        int offset = i * 4;
        if (!component(offset, key.keytime, capture)
            || !component(offset + 1, value.x, capture)
            || !component(offset + 2, value.y, capture)
            || !component(offset + 3, value.z, capture)) return false;
      }
      return true;
    }

    private boolean component(int index, float value, boolean capture) {
      int exact = Float.floatToRawIntBits(value);
      if (capture) bits[index] = exact;
      return bits[index] == exact;
    }
  }
}
