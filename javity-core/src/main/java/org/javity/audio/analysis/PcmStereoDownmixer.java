package org.javity.audio.analysis;

/** Explicit adapter from interleaved stereo float PCM to mono using (L + R) / 2. */
public final class PcmStereoDownmixer {
  private PcmStereoDownmixer() {}

  /** Validates and copies {@code frameCount} interleaved frames into a new mono array. */
  public static float[] average(float[] interleavedStereo, int sampleOffset, int frameCount) {
    if (interleavedStereo == null) throw new NullPointerException("interleavedStereo");
    if (sampleOffset < 0
        || sampleOffset > interleavedStereo.length
        || frameCount < 0
        || frameCount > (interleavedStereo.length - sampleOffset) / 2) {
      throw new IndexOutOfBoundsException("Invalid interleaved stereo range");
    }
    int sampleEnd = sampleOffset + frameCount * 2;
    for (int i = sampleOffset; i < sampleEnd; i++) {
      float sample = interleavedStereo[i];
      if (!Float.isFinite(sample) || sample < -1f || sample > 1f) {
        throw new IllegalArgumentException("PCM samples must be finite and in [-1, 1]");
      }
    }
    float[] mono = new float[frameCount];
    for (int frame = 0; frame < frameCount; frame++) {
      mono[frame] =
          (interleavedStereo[sampleOffset + 2 * frame]
                  + interleavedStereo[sampleOffset + 2 * frame + 1])
              * 0.5f;
    }
    return mono;
  }
}
