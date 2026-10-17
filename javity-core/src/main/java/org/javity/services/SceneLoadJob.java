package org.javity.services;

import org.javity.core.Game;

/** Incremental, caller-thread scene preparation. A successful scene transfers exactly once. */
public interface SceneLoadJob extends AutoCloseable {
  void update(float unscaledDeltaSeconds);

  double progress();

  boolean isReady();

  Game takeScene();

  /** Cancels preparation and releases resources still owned by this job. */
  @Override
  void close();
}
