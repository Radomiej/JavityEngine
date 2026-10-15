package org.javity.presentation;

/** Generation-safe ownership handle for one additive presentation effect. */
public final class PresentationHandle implements AutoCloseable {
  private final PresentationEffects effects;
  private final int slot;
  private final long generation;
  private Runnable onComplete;
  private boolean completed;

  PresentationHandle(PresentationEffects effects, int slot, long generation) {
    this.effects = effects;
    this.slot = slot;
    this.generation = generation;
  }

  public boolean isActive() {
    return effects.isActive(slot, generation);
  }

  public void cancel() {
    effects.cancel(slot, generation);
  }

  public boolean isPaused() {
    return effects.isPaused(slot, generation);
  }

  public void pause() {
    effects.setPaused(slot, generation, true);
  }

  public void resume() {
    effects.setPaused(slot, generation, false);
  }

  /** Registers a callback for natural expiry. Cancellation and eviction do not call it. */
  public PresentationHandle onComplete(Runnable callback) {
    if (callback == null) throw new NullPointerException("callback");
    if (completed) callback.run();
    else if (isActive()) onComplete = callback;
    return this;
  }

  void completed() {
    if (completed) return;
    completed = true;
    Runnable callback = onComplete;
    onComplete = null;
    if (callback != null) callback.run();
  }

  void cancelled() {
    onComplete = null;
  }

  @Override
  public void close() {
    cancel();
  }
}
