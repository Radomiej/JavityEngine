package org.javity.services;

/** Unscaled fade durations for a transactional scene handoff. */
public record SceneTransition(double fadeOutSeconds, double fadeInSeconds) {
  public SceneTransition {
    if (!Double.isFinite(fadeOutSeconds)
        || !Double.isFinite(fadeInSeconds)
        || fadeOutSeconds < 0
        || fadeInSeconds < 0)
      throw new IllegalArgumentException("Transition durations must be finite and nonnegative");
  }

  public static SceneTransition none() {
    return new SceneTransition(0, 0);
  }
}
