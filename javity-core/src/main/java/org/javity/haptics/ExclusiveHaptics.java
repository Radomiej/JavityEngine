package org.javity.haptics;

import java.util.Objects;

/**
 * Adapts a backend with one global native cancel operation. At most one request is current;
 * cancelling a stale handle cannot stop a newer request.
 *
 * <p>Calls are confined to one owner thread. Synchronous backend callbacks may close this service,
 * but may not start another playback while a native play or cancel operation is in flight.
 */
public final class ExclusiveHaptics implements Haptics {
  /** Backend contract for APIs whose cancel operation stops all current native vibration. */
  public interface Backend {
    HapticsCapabilities capabilities();

    /** Starts or replaces the current native request; false means it did not start. */
    boolean play(HapticRequest request);

    void cancel();
  }

  public sealed interface HapticRequest permits PulseRequest, PatternRequest {}

  public record PulseRequest(HapticPulse pulse) implements HapticRequest {
    public PulseRequest {
      Objects.requireNonNull(pulse, "pulse");
    }
  }

  public record PatternRequest(HapticPattern pattern) implements HapticRequest {
    public PatternRequest {
      Objects.requireNonNull(pattern, "pattern");
    }
  }

  private final Backend backend;
  private final HapticsCapabilities capabilities;
  private Playback current;
  private long nextGeneration;
  private boolean backendOperationInFlight;
  private boolean closed;

  public ExclusiveHaptics(Backend backend) {
    this.backend = Objects.requireNonNull(backend, "backend");
    capabilities = Objects.requireNonNull(backend.capabilities(), "backend capabilities");
  }

  @Override
  public HapticsCapabilities capabilities() {
    return capabilities;
  }

  @Override
  public HapticHandle play(HapticPulse pulse) {
    return playRequest(new PulseRequest(pulse));
  }

  @Override
  public HapticHandle play(HapticPattern pattern) {
    return playRequest(new PatternRequest(pattern));
  }

  private HapticHandle playRequest(HapticRequest request) {
    Objects.requireNonNull(request, "request");
    if (backendOperationInFlight)
      throw new IllegalStateException("Cannot play while a backend operation is in flight");
    if (closed) throw new IllegalStateException("Haptics service is closed");
    if (!capabilities.available()
        || !capabilities.basicPulse()
        || (request instanceof PatternRequest && !capabilities.patterns()))
      return new InactiveHandle(HapticStatus.UNSUPPORTED);

    invalidateCurrent();
    Playback playback = new Playback(++nextGeneration);
    backendOperationInFlight = true;
    boolean started;
    try {
      started = backend.play(request);
    } catch (RuntimeException | Error startFailure) {
      playback.status = HapticStatus.FAILED;
      try {
        backend.cancel();
      } catch (RuntimeException | Error cleanupFailure) {
        if (startFailure != cleanupFailure) startFailure.addSuppressed(cleanupFailure);
      } finally {
        backendOperationInFlight = false;
      }
      throw startFailure;
    }

    if (started && !closed) {
      playback.active = true;
      current = playback;
      backendOperationInFlight = false;
      return playback;
    }

    if (!started) playback.status = HapticStatus.FAILED;
    try {
      // A false or closed-during-start result can follow partial native work. Cancel before
      // releasing the in-flight guard so callbacks cannot create a successor under global cancel.
      backend.cancel();
    } finally {
      backendOperationInFlight = false;
    }
    return playback;
  }

  private void invalidateCurrent() {
    Playback previous = current;
    current = null;
    if (previous != null) previous.active = false;
  }

  private void cancelIfCurrent(Playback playback) {
    if (current != playback) return;
    current = null;
    playback.active = false;
    cancelBackend();
  }

  private void cancelBackend() {
    if (backendOperationInFlight)
      throw new IllegalStateException("Backend operation is already in flight");
    backendOperationInFlight = true;
    try {
      backend.cancel();
    } finally {
      backendOperationInFlight = false;
    }
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    Playback previous = current;
    boolean cancelCurrent = previous != null && previous.active;
    current = null;
    if (previous != null) previous.active = false;
    if (backendOperationInFlight) return;
    if (cancelCurrent) cancelBackend();
  }

  private final class Playback implements HapticHandle {
    private final long generation;
    private HapticStatus status = HapticStatus.STARTED;
    private boolean active;

    private Playback(long generation) {
      this.generation = generation;
    }

    @Override
    public HapticStatus status() {
      return status;
    }

    @Override
    public boolean isActive() {
      return active && current == this && generation == nextGeneration && !closed;
    }

    @Override
    public void cancel() {
      cancelIfCurrent(this);
    }
  }

  private static final class InactiveHandle implements HapticHandle {
    private final HapticStatus status;

    private InactiveHandle(HapticStatus status) {
      this.status = status;
    }

    @Override
    public HapticStatus status() {
      return status;
    }

    @Override
    public boolean isActive() {
      return false;
    }

    @Override
    public void cancel() {}
  }
}
