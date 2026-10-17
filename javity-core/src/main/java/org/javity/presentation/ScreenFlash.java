package org.javity.presentation;

/** Generation-safe cancellation handle for one rendered screen-space flash. */
public final class ScreenFlash implements AutoCloseable {
  private final ScreenEffects owner;
  private final int slot;
  private final long generation;

  ScreenFlash(ScreenEffects owner, int slot, long generation) {
    this.owner = owner;
    this.slot = slot;
    this.generation = generation;
  }

  public boolean isActive() {
    return owner != null && owner.isActive(slot, generation);
  }

  public void cancel() {
    if (owner != null) owner.cancel(slot, generation);
  }

  @Override
  public void close() {
    cancel();
  }
}
