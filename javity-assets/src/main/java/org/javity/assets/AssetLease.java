package org.javity.assets;

/** Owner-thread lease. Borrowed objects must no longer be used after close. */
public interface AssetLease<T> extends AutoCloseable {
  enum State { QUEUED, LOADING, READY, FAILED, CLOSED }
  State state();
  T get();
  Throwable failure();
  @Override void close();
}
