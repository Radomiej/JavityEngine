package org.javity.signals;

/** An idempotently closeable registration with an event source. */
public interface Subscription extends AutoCloseable {
  @Override
  void close();

  boolean isClosed();
}
