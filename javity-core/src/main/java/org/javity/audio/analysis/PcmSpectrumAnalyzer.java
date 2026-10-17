package org.javity.audio.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Bounded, single-threaded FFT analysis of caller-supplied mono PCM. */
public final class PcmSpectrumAnalyzer {
  public static final int MIN_FFT_SIZE = 256;
  public static final int MAX_FFT_SIZE = 4096;
  public static final int MAX_WINDOWS_PER_PUSH = 8;

  public enum WindowType {
    RECTANGULAR,
    /** Periodic Hann: {@code 0.5 - 0.5*cos(2*pi*n/N)}. */
    HANN
  }

  private int sampleRate;
  private int fftSize;
  private int hopSize;
  private WindowType windowType;
  private double[] window;
  private double[] twiddleReal;
  private double[] twiddleImaginary;
  private double windowSum;
  private double windowPowerSum;
  private float[] ring;
  private double[] fftReal;
  private double[] fftImaginary;
  private double[] smoothing;
  private boolean smoothingConfigured;
  private double attackSeconds;
  private double releaseSeconds;
  private long samplesAccepted;
  private long nextFrameEnd;
  private long frameCount;

  public PcmSpectrumAnalyzer(int sampleRate, int fftSize, WindowType windowType) {
    configure(sampleRate, fftSize, windowType);
  }

  /** Replaces the configuration and clears input history and smoothing. */
  public void configure(int sampleRate, int fftSize, WindowType windowType) {
    validateConfiguration(sampleRate, fftSize, windowType);
    this.sampleRate = sampleRate;
    this.fftSize = fftSize;
    this.hopSize = fftSize / 2;
    this.windowType = windowType;
    this.window = new double[fftSize];
    this.twiddleReal = new double[fftSize / 2];
    this.twiddleImaginary = new double[fftSize / 2];
    this.ring = new float[fftSize];
    this.fftReal = new double[fftSize];
    this.fftImaginary = new double[fftSize];
    this.smoothing = new double[fftSize / 2 + 1];
    this.windowSum = 0;
    this.windowPowerSum = 0;
    for (int i = 0; i < fftSize; i++) {
      double value =
          windowType == WindowType.RECTANGULAR
              ? 1.0
              : 0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / fftSize);
      window[i] = value;
      windowSum += value;
      windowPowerSum += value * value;
    }
    for (int i = 0; i < fftSize / 2; i++) {
      double angle = -2.0 * Math.PI * i / fftSize;
      twiddleReal[i] = Math.cos(angle);
      twiddleImaginary[i] = Math.sin(angle);
    }
    if (smoothingConfigured) ArraysZero.fill(smoothing);
    samplesAccepted = 0;
    nextFrameEnd = fftSize;
    frameCount = 0;
  }

  /**
   * Enables attack/release smoothing on peak-amplitude bins. Each newly published window advances
   * the smoother once using the configured hop duration. A zero time constant means immediate
   * response in that direction.
   */
  public void configureSmoothing(double attackSeconds, double releaseSeconds) {
    if (!Double.isFinite(attackSeconds)
        || attackSeconds < 0
        || !Double.isFinite(releaseSeconds)
        || releaseSeconds < 0) {
      throw new IllegalArgumentException("Smoothing times must be finite and non-negative");
    }
    this.attackSeconds = attackSeconds;
    this.releaseSeconds = releaseSeconds;
    this.smoothingConfigured = true;
    ArraysZero.fill(smoothing);
  }

  /** Disables smoothing and clears its state. */
  public void disableSmoothing() {
    smoothingConfigured = false;
    ArraysZero.fill(smoothing);
  }

  /**
   * Accepts a bounded mono PCM range and returns immutable snapshots for all windows completed by
   * this call. The complete range is validated before the analyzer's ring buffer or frame sequence
   * changes.
   */
  public List<SpectrumFrame> push(float[] pcm, int offset, int count) {
    Objects.requireNonNull(pcm, "pcm");
    if (offset < 0 || count < 0 || offset > pcm.length - count) {
      throw new IndexOutOfBoundsException("Invalid PCM offset/count");
    }
    if (count > 4 * fftSize) {
      throw new IllegalArgumentException("A push may contain at most 4N samples");
    }
    long endAfter = samplesAccepted + count;
    long windows = endAfter < nextFrameEnd ? 0 : 1 + (endAfter - nextFrameEnd) / hopSize;
    if (windows > MAX_WINDOWS_PER_PUSH) {
      throw new IllegalArgumentException("A push may generate at most 8 windows");
    }
    for (int i = offset; i < offset + count; i++) {
      float sample = pcm[i];
      if (!Float.isFinite(sample) || sample < -1f || sample > 1f) {
        throw new IllegalArgumentException("PCM samples must be finite and in [-1, 1]");
      }
    }

    List<SpectrumFrame> frames = new ArrayList<>((int) windows);
    for (int i = offset; i < offset + count; i++) {
      ring[(int) (samplesAccepted % fftSize)] = pcm[i];
      samplesAccepted++;
      if (samplesAccepted == nextFrameEnd) {
        frames.add(analyzeWindow(nextFrameEnd));
        nextFrameEnd += hopSize;
      }
    }
    return List.copyOf(frames);
  }

  /** Clears buffered samples, frame numbering, and smoothing state. */
  public void reset() {
    ArraysZero.fill(ring);
    ArraysZero.fill(smoothing);
    samplesAccepted = 0;
    nextFrameEnd = fftSize;
    frameCount = 0;
  }

  public int sampleRate() {
    return sampleRate;
  }

  public int fftSize() {
    return fftSize;
  }

  public int hopSize() {
    return hopSize;
  }

  public WindowType windowType() {
    return windowType;
  }

  public long samplesAccepted() {
    return samplesAccepted;
  }

  public long frameCount() {
    return frameCount;
  }

  private SpectrumFrame analyzeWindow(long endSampleIndex) {
    double sumSquares = 0;
    double peak = 0;
    int oldest = (int) (endSampleIndex % fftSize);
    for (int i = 0; i < fftSize; i++) {
      double sample = ring[(oldest + i) % fftSize];
      sumSquares += sample * sample;
      peak = Math.max(peak, Math.abs(sample));
      fftReal[i] = sample * window[i];
      fftImaginary[i] = 0;
    }
    fftInPlace();
    int bins = fftSize / 2 + 1;
    double[] amplitudes = new double[bins];
    double[] psd = new double[bins];
    double[] smoothed = smoothingConfigured ? smoothing : null;
    double df = (double) sampleRate / fftSize;
    double smoothAlpha = smoothingConfigured ? smoothingAlpha() : 1;
    for (int k = 0; k < bins; k++) {
      double magnitudeSquared = fftReal[k] * fftReal[k] + fftImaginary[k] * fftImaginary[k];
      double magnitude = Math.sqrt(magnitudeSquared);
      boolean edge = k == 0 || k == fftSize / 2;
      double amplitude = (edge ? 1 : 2) * magnitude / windowSum;
      amplitudes[k] = amplitude;
      double density = magnitudeSquared / (sampleRate * windowPowerSum);
      psd[k] = edge ? density : density * 2;
      if (smoothed != null) {
        double alpha = amplitude >= smoothed[k] ? smoothAlpha : smoothingAlphaForRelease();
        smoothed[k] += alpha * (amplitude - smoothed[k]);
      }
    }
    frameCount++;
    return new SpectrumFrame(
        sampleRate,
        fftSize,
        hopSize,
        windowType,
        endSampleIndex - fftSize,
        endSampleIndex,
        frameCount,
        Math.sqrt(sumSquares / fftSize),
        peak,
        amplitudes,
        psd,
        smoothingConfigured ? smoothing.clone() : null,
        df);
  }

  private double smoothingAlpha() {
    return alpha(attackSeconds);
  }

  private double smoothingAlphaForRelease() {
    return alpha(releaseSeconds);
  }

  private double alpha(double tau) {
    if (tau == 0) return 1;
    return 1 - Math.exp(-((double) hopSize / sampleRate) / tau);
  }

  private void fftInPlace() {
    for (int i = 1, j = 0; i < fftSize; i++) {
      int bit = fftSize >>> 1;
      for (; (j & bit) != 0; bit >>>= 1) j ^= bit;
      j ^= bit;
      if (i < j) {
        double temporary = fftReal[i];
        fftReal[i] = fftReal[j];
        fftReal[j] = temporary;
        temporary = fftImaginary[i];
        fftImaginary[i] = fftImaginary[j];
        fftImaginary[j] = temporary;
      }
    }
    for (int size = 2; size <= fftSize; size <<= 1) {
      int half = size >>> 1;
      int twiddleStep = fftSize / size;
      for (int start = 0; start < fftSize; start += size) {
        for (int j = 0; j < half; j++) {
          int twiddle = j * twiddleStep;
          double tr =
              twiddleReal[twiddle] * fftReal[start + j + half]
                  - twiddleImaginary[twiddle] * fftImaginary[start + j + half];
          double ti =
              twiddleReal[twiddle] * fftImaginary[start + j + half]
                  + twiddleImaginary[twiddle] * fftReal[start + j + half];
          double ur = fftReal[start + j];
          double ui = fftImaginary[start + j];
          fftReal[start + j] = ur + tr;
          fftImaginary[start + j] = ui + ti;
          fftReal[start + j + half] = ur - tr;
          fftImaginary[start + j + half] = ui - ti;
        }
      }
    }
  }

  private static void validateConfiguration(int sampleRate, int fftSize, WindowType windowType) {
    if (sampleRate <= 0) throw new IllegalArgumentException("Sample rate must be positive");
    if (fftSize < MIN_FFT_SIZE || fftSize > MAX_FFT_SIZE || (fftSize & (fftSize - 1)) != 0) {
      throw new IllegalArgumentException("FFT size must be a power of two from 256 through 4096");
    }
    Objects.requireNonNull(windowType, "windowType");
  }

  private static final class ArraysZero {
    private static void fill(double[] values) {
      java.util.Arrays.fill(values, 0);
    }

    private static void fill(float[] values) {
      java.util.Arrays.fill(values, 0);
    }
  }

  /** Immutable spectrum snapshot. Bin arrays are never exposed directly. */
  public static final class SpectrumFrame {
    private final int sampleRate;
    private final int fftSize;
    private final int hopSize;
    private final WindowType windowType;
    private final long startSampleIndex;
    private final long endSampleIndex;
    private final long sequence;
    private final double rawRms;
    private final double peak;
    private final double[] amplitudes;
    private final double[] psd;
    private final double[] smoothed;
    private final double binWidthHz;

    private SpectrumFrame(
        int sampleRate,
        int fftSize,
        int hopSize,
        WindowType windowType,
        long startSampleIndex,
        long endSampleIndex,
        long sequence,
        double rawRms,
        double peak,
        double[] amplitudes,
        double[] psd,
        double[] smoothed,
        double binWidthHz) {
      this.sampleRate = sampleRate;
      this.fftSize = fftSize;
      this.hopSize = hopSize;
      this.windowType = windowType;
      this.startSampleIndex = startSampleIndex;
      this.endSampleIndex = endSampleIndex;
      this.sequence = sequence;
      this.rawRms = rawRms;
      this.peak = peak;
      this.amplitudes = amplitudes;
      this.psd = psd;
      this.smoothed = smoothed;
      this.binWidthHz = binWidthHz;
    }

    public int sampleRate() {
      return sampleRate;
    }

    public int fftSize() {
      return fftSize;
    }

    public int hopSize() {
      return hopSize;
    }

    public WindowType windowType() {
      return windowType;
    }

    public long startSampleIndex() {
      return startSampleIndex;
    }

    /** Exclusive sample index of the last sample included in this frame. */
    public long endSampleIndex() {
      return endSampleIndex;
    }

    public long sequence() {
      return sequence;
    }

    public double rawRms() {
      return rawRms;
    }

    public double peak() {
      return peak;
    }

    public double binWidthHz() {
      return binWidthHz;
    }

    public int binCount() {
      return amplitudes.length;
    }

    public double frequencyHz(int bin) {
      checkBin(bin);
      return bin * binWidthHz;
    }

    public double amplitude(int bin) {
      checkBin(bin);
      return amplitudes[bin];
    }

    public double smoothedAmplitude(int bin) {
      checkBin(bin);
      if (smoothed == null) throw new IllegalStateException("Smoothing is not enabled");
      return smoothed[bin];
    }

    public double[] amplitudes() {
      return amplitudes.clone();
    }

    public double[] smoothedAmplitudes() {
      if (smoothed == null) throw new IllegalStateException("Smoothing is not enabled");
      return smoothed.clone();
    }

    /** Amplitude referenced to 1, floored at -120 dBFS. */
    public double amplitudeDbfs(int bin) {
      double value = amplitude(bin);
      return value == 0 ? -120 : Math.max(-120, 20 * Math.log10(value));
    }

    /** Integrates one-sided PSD bins whose center frequencies lie in [lowHz, highHz]. */
    public double bandRms(double lowHz, double highHz) {
      double nyquist = sampleRate / 2.0;
      if (!Double.isFinite(lowHz)
          || !Double.isFinite(highHz)
          || lowHz < 0
          || highHz < lowHz
          || highHz > nyquist) {
        throw new IllegalArgumentException("Band must be finite and within [0, Nyquist]");
      }
      double energy = 0;
      for (int bin = 0; bin < psd.length; bin++) {
        double frequency = bin * binWidthHz;
        if (frequency >= lowHz && frequency <= highHz) energy += psd[bin] * binWidthHz;
      }
      return Math.sqrt(energy);
    }

    private void checkBin(int bin) {
      if (bin < 0 || bin >= amplitudes.length) throw new IndexOutOfBoundsException("bin " + bin);
    }
  }
}
