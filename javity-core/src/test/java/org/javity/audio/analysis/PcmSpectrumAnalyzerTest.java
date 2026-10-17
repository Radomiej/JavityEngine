package org.javity.audio.analysis;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;

public class PcmSpectrumAnalyzerTest {
  private static final int FS = 48_000;
  private static final int N = 1024;

  @Test
  public void coherentToneHasExpectedBinPeakRmsAndMetadata() {
    PcmSpectrumAnalyzer analyzer = analyzer(FS, N, PcmSpectrumAnalyzer.WindowType.RECTANGULAR);
    PcmSpectrumAnalyzer.SpectrumFrame frame = analyzer.push(tone(N, 32, .5), 0, N).get(0);
    assertEquals(1500, frame.frequencyHz(32), 0);
    assertEquals(.5, frame.amplitude(32), 1e-7);
    assertEquals(.5 / Math.sqrt(2), frame.rawRms(), 1e-7);
    assertEquals(.5, frame.peak(), 1e-7);
    assertEquals(0, frame.startSampleIndex());
    assertEquals(N, frame.endSampleIndex());
    assertEquals(N / 2 + 1, frame.binCount());
    assertEquals(FS / (double) N, frame.binWidthHz(), 0);
    assertEquals(-6.020599913, frame.amplitudeDbfs(32), 1e-6);
  }

  @Test
  public void sampleRateDcNyquistAndSilenceUseCorrectOneSidedFactors() {
    PcmSpectrumAnalyzer.SpectrumFrame mapped =
        analyzer(44_100, N, PcmSpectrumAnalyzer.WindowType.RECTANGULAR)
            .push(tone(N, 32, .5), 0, N)
            .get(0);
    assertEquals(1378.125, mapped.frequencyHz(32), 1e-9);

    PcmSpectrumAnalyzer analyzer = analyzer(FS, N, PcmSpectrumAnalyzer.WindowType.RECTANGULAR);
    float[] dc = new float[N];
    java.util.Arrays.fill(dc, .25f);
    PcmSpectrumAnalyzer.SpectrumFrame dcFrame = analyzer.push(dc, 0, N).get(0);
    assertEquals(.25, dcFrame.amplitude(0), 1e-12);
    assertEquals(0, dcFrame.amplitude(N / 2), 1e-12);
    analyzer.reset();
    float[] nyquist = new float[N];
    for (int i = 0; i < N; i++) nyquist[i] = (i & 1) == 0 ? .6f : -.6f;
    PcmSpectrumAnalyzer.SpectrumFrame nyquistFrame = analyzer.push(nyquist, 0, N).get(0);
    assertEquals(.6, nyquistFrame.amplitude(N / 2), 1e-7);
    assertEquals(0, nyquistFrame.amplitude(0), 1e-12);

    PcmSpectrumAnalyzer.SpectrumFrame silence =
        analyzer(48_000, N, PcmSpectrumAnalyzer.WindowType.HANN).push(new float[N], 0, N).get(0);
    for (double value : silence.amplitudes()) assertEquals(0, value, 0);
    assertEquals(-120, silence.amplitudeDbfs(0), 0);
    assertTrue(Double.isFinite(silence.rawRms()));
  }

  @Test
  public void twoTonesHaveSeparatePeakAndPsdBandRms() {
    float[] samples = new float[N];
    for (int i = 0; i < N; i++) {
      samples[i] =
          (float)
              (.4 * Math.sin(2 * Math.PI * 20 * i / N) + .2 * Math.sin(2 * Math.PI * 40 * i / N));
    }
    PcmSpectrumAnalyzer.SpectrumFrame frame =
        analyzer(FS, N, PcmSpectrumAnalyzer.WindowType.RECTANGULAR).push(samples, 0, N).get(0);
    assertEquals(.4, frame.amplitude(20), 1e-7);
    assertEquals(.2, frame.amplitude(40), 1e-7);
    assertEquals(.4 / Math.sqrt(2), frame.bandRms(20 * 46.875, 20 * 46.875), 1e-7);
    assertEquals(.2 / Math.sqrt(2), frame.bandRms(40 * 46.875, 40 * 46.875), 1e-7);
    assertEquals(Math.sqrt((.4 * .4 + .2 * .2) / 2), frame.rawRms(), 1e-7);
  }

  @Test
  public void hannCorrectsCoherentGainAndReducesCoherentToneLeakage() {
    PcmSpectrumAnalyzer.SpectrumFrame hann =
        analyzer(FS, N, PcmSpectrumAnalyzer.WindowType.HANN).push(tone(N, 32, .5), 0, N).get(0);
    assertEquals(.5, hann.amplitude(32), 1e-7);
    assertEquals(.25, hann.amplitude(31), 1e-7);
    assertEquals(.25, hann.amplitude(33), 1e-7);
    assertEquals(
        .5 / Math.sqrt(2), hann.bandRms(31 * hann.binWidthHz(), 33 * hann.binWidthHz()), 1e-7);

    PcmSpectrumAnalyzer.SpectrumFrame rectangular =
        analyzer(FS, N, PcmSpectrumAnalyzer.WindowType.RECTANGULAR)
            .push(tone(N, 32.5, .5), 0, N)
            .get(0);
    assertTrue(rectangular.amplitude(32) > rectangular.amplitude(40));
    assertTrue(rectangular.amplitude(32) > 0 && rectangular.amplitude(33) > 0);
    assertTrue(Math.max(rectangular.amplitude(32), rectangular.amplitude(33)) < .5);
  }

  @Test
  public void splitPushesPreserveHopContinuityAndSnapshotsOwnTheirArrays() {
    float[] input = tone(3 * N, 32, .5);
    PcmSpectrumAnalyzer oneShot = analyzer(FS, N, PcmSpectrumAnalyzer.WindowType.HANN);
    List<PcmSpectrumAnalyzer.SpectrumFrame> expected = oneShot.push(input, 0, input.length);
    PcmSpectrumAnalyzer split = analyzer(FS, N, PcmSpectrumAnalyzer.WindowType.HANN);
    List<PcmSpectrumAnalyzer.SpectrumFrame> frames = new java.util.ArrayList<>();
    for (int offset = 0; offset < input.length; offset += 700) {
      frames.addAll(split.push(input, offset, Math.min(700, input.length - offset)));
    }
    assertEquals(5, frames.size());
    for (int i = 0; i < frames.size(); i++) {
      assertEquals(expected.get(i).endSampleIndex(), frames.get(i).endSampleIndex());
      assertEquals(i * (N / 2), frames.get(i).startSampleIndex());
      assertEquals(expected.get(i).amplitude(32), frames.get(i).amplitude(32), 0);
    }
    double[] copied = frames.get(0).amplitudes();
    copied[32] = 99;
    assertEquals(.5, frames.get(0).amplitude(32), 1e-7);
    input[0] = 1;
    assertEquals(.5, frames.get(0).amplitude(32), 1e-7);
  }

  @Test
  public void smoothingAdvancesPerHopAndUsesAttackAndReleaseConstants() {
    double tau = (N / 2.0 / FS) / Math.log(2);
    PcmSpectrumAnalyzer analyzer = analyzer(FS, N, PcmSpectrumAnalyzer.WindowType.RECTANGULAR);
    analyzer.configureSmoothing(tau, tau);
    int toneSamples = N + N / 2;
    List<PcmSpectrumAnalyzer.SpectrumFrame> rising =
        analyzer.push(tone(toneSamples, 32, .5), 0, toneSamples);
    assertEquals(.25, rising.get(0).smoothedAmplitude(32), 1e-7);
    assertEquals(.375, rising.get(1).smoothedAmplitude(32), 1e-7);
    float[] zeros = new float[N / 2];
    PcmSpectrumAnalyzer.SpectrumFrame falling = analyzer.push(zeros, 0, zeros.length).get(0);
    assertEquals(.3125, falling.smoothedAmplitude(32), 1e-7);
    assertEquals(.375, rising.get(1).smoothedAmplitude(32), 1e-7);
  }

  @Test
  public void stereoAverageIsExplicitAndCancelsOppositePhaseChannels() {
    float[] stereo = new float[2 * N];
    for (int i = 0; i < N; i++) {
      float sample = (float) (.5 * Math.sin(2 * Math.PI * 32 * i / N));
      stereo[2 * i] = sample;
      stereo[2 * i + 1] = -sample;
    }
    float[] mono = PcmStereoDownmixer.average(stereo, 0, N);
    PcmSpectrumAnalyzer.SpectrumFrame frame =
        analyzer(FS, N, PcmSpectrumAnalyzer.WindowType.RECTANGULAR)
            .push(mono, 0, mono.length)
            .get(0);
    assertEquals(0, frame.amplitude(32), 0);
  }

  @Test
  public void stereoDownmixerChecksEmptyRangeOffsetAtBothBoundaries() {
    assertArrayEquals(new float[0], PcmStereoDownmixer.average(new float[2], 2, 0), 0);
    assertThrows(
        IndexOutOfBoundsException.class, () -> PcmStereoDownmixer.average(new float[2], 3, 0));
  }

  @Test
  public void invalidInputAndConfigurationRejectAtomically() {
    PcmSpectrumAnalyzer a = analyzer(FS, N, PcmSpectrumAnalyzer.WindowType.RECTANGULAR);
    PcmSpectrumAnalyzer b = analyzer(FS, N, PcmSpectrumAnalyzer.WindowType.RECTANGULAR);
    float[] prefix = tone(N / 2, 32, .5);
    a.push(prefix, 0, prefix.length);
    b.push(prefix, 0, prefix.length);
    float[] invalid = {.25f, Float.NaN, .25f};
    assertThrows(IllegalArgumentException.class, () -> a.push(invalid, 0, invalid.length));
    assertThrows(
        IllegalArgumentException.class,
        () -> a.push(new float[] {.25f, Float.POSITIVE_INFINITY}, 0, 2));
    assertThrows(IllegalArgumentException.class, () -> a.push(new float[] {1.01f}, 0, 1));
    assertThrows(IndexOutOfBoundsException.class, () -> a.push(prefix, prefix.length, 1));
    assertThrows(IllegalArgumentException.class, () -> a.push(new float[4 * N + 1], 0, 4 * N + 1));
    assertEquals(a.samplesAccepted(), b.samplesAccepted());
    float[] suffix = tone(N / 2, 32, .5);
    assertEquals(
        b.push(suffix, 0, suffix.length).get(0).amplitude(32),
        a.push(suffix, 0, suffix.length).get(0).amplitude(32),
        0);
    PcmSpectrumAnalyzer.SpectrumFrame prior = a.push(tone(N / 2, 32, .5), 0, N / 2).get(0);
    assertThrows(
        IllegalArgumentException.class,
        () -> a.configure(FS, 1000, PcmSpectrumAnalyzer.WindowType.HANN));
    assertThrows(
        IllegalArgumentException.class,
        () -> a.configure(0, N, PcmSpectrumAnalyzer.WindowType.HANN));
    assertThrows(
        IllegalArgumentException.class,
        () -> a.configure(FS, 300, PcmSpectrumAnalyzer.WindowType.HANN));
    assertEquals(.5, prior.amplitude(32), 1e-7);
    assertEquals(N, a.fftSize());
  }

  @Test
  public void resetAndReconfigureClearBufferedSamplesAndSmoothing() {
    PcmSpectrumAnalyzer analyzer = analyzer(FS, N, PcmSpectrumAnalyzer.WindowType.HANN);
    analyzer.configureSmoothing(.01, .02);
    analyzer.push(tone(N / 2, 32, .5), 0, N / 2);
    analyzer.reset();
    assertEquals(0, analyzer.samplesAccepted());
    PcmSpectrumAnalyzer.SpectrumFrame silence = analyzer.push(new float[N], 0, N).get(0);
    assertEquals(0, silence.amplitude(32), 0);
    assertEquals(0, silence.smoothedAmplitude(32), 0);
    analyzer.configure(44_100, 512, PcmSpectrumAnalyzer.WindowType.RECTANGULAR);
    assertEquals(44_100, analyzer.sampleRate());
    assertEquals(512, analyzer.fftSize());
    assertEquals(0, analyzer.samplesAccepted());
    assertEquals(0, analyzer.frameCount());
  }

  private static PcmSpectrumAnalyzer analyzer(
      int sampleRate, int size, PcmSpectrumAnalyzer.WindowType window) {
    return new PcmSpectrumAnalyzer(sampleRate, size, window);
  }

  private static float[] tone(int length, double bin, double amplitude) {
    float[] samples = new float[length];
    for (int i = 0; i < length; i++) {
      samples[i] = (float) (amplitude * Math.sin(2 * Math.PI * bin * i / N));
    }
    return samples;
  }
}
