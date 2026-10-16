package org.javity.android;

/** Small failure-aggregation primitive shared by lifecycle cleanup and CPU failure tests. */
final class FixtureLifecycle {
  private FixtureLifecycle() {}

  static Throwable attempt(Throwable primary, Runnable operation) {
    try {
      operation.run();
    } catch (RuntimeException | Error failure) {
      if (primary == null) return failure;
      if (failure != primary) primary.addSuppressed(failure);
    }
    return primary;
  }

  static Throwable rollback(Throwable primary, Runnable cleanup) {
    return attempt(primary, cleanup);
  }

  static void rethrow(Throwable failure) {
    if (failure instanceof RuntimeException runtimeException) throw runtimeException;
    if (failure instanceof Error error) throw error;
    if (failure != null) throw new IllegalStateException("Fixture lifecycle failed", failure);
  }
}
