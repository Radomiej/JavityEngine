package org.javity.haptics;

/** One independently owned playback request. Closing a handle is equivalent to cancel. */
public interface HapticHandle extends AutoCloseable {
  HapticStatus status();

  /** True only while this handle owns a current active native request. */
  boolean isActive();

  void cancel();

  @Override
  default void close() {
    cancel();
  }
}
