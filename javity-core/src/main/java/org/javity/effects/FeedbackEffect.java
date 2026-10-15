package org.javity.effects;

/**
 * A fresh, per-play effect used by {@link Feedback#custom(double, java.util.function.Supplier)}.
 */
public interface FeedbackEffect extends AutoCloseable {
  /** Called once when this effect first becomes eligible to run. */
  default void start() {}

  /** Called for each eligible update, with elapsed and frame delta in seconds. */
  default void update(double elapsed, double delta) {}

  /** Called once when the effect reaches its duration. */
  default void complete() {}

  /** Releases resources acquired by this playback. */
  @Override
  default void close() throws Exception {}
}
