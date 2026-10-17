package org.javity.wasm;

/** Releases owners before their borrowed GPU resources and preserves the first failure. */
final class SceneCleanup {
  private SceneCleanup() {}

  static void close(Throwable failure, Runnable... actions) {
    for (Runnable action : actions) {
      if (action == null) continue;
      try {
        action.run();
      } catch (RuntimeException | Error cleanupFailure) {
        if (failure == null) failure = cleanupFailure;
        else if (failure != cleanupFailure) failure.addSuppressed(cleanupFailure);
      }
    }
    if (failure instanceof RuntimeException exception) throw exception;
    if (failure instanceof Error error) throw error;
  }
}
