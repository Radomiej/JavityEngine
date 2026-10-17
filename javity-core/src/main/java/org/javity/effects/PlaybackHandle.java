package org.javity.effects;

public final class PlaybackHandle extends EffectHandle {
  private final java.util.function.Consumer<CancelMode> cancelAction;

  PlaybackHandle(java.util.function.Consumer<CancelMode> cancelAction) {
    this.cancelAction = cancelAction;
  }

  @Override
  public void cancel(CancelMode mode) {
    if (isRunning()) cancelAction.accept(java.util.Objects.requireNonNull(mode));
  }

  @Override
  public PlaybackHandle onComplete(Runnable callback) {
    super.onComplete(callback);
    return this;
  }
}
