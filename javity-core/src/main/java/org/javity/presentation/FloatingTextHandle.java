package org.javity.presentation;

/** Generation-safe lease on one pooled floating label. */
public final class FloatingTextHandle implements AutoCloseable {
  private final FloatingTextPool pool;
  private final FloatingTextPool.Slot slot;
  private final long generation;

  FloatingTextHandle(FloatingTextPool pool, FloatingTextPool.Slot slot, long generation) {
    this.pool = pool;
    this.slot = slot;
    this.generation = generation;
  }

  public boolean isActive() {
    return pool.isActive(slot, generation);
  }

  public void cancel() {
    pool.cancel(slot, generation);
  }

  @Override
  public void close() {
    cancel();
  }
}
