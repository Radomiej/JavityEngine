package org.javity.effects;

public final class TweenHandle extends EffectHandle {
  private final java.util.function.Consumer<CancelMode> cancelAction;

  TweenHandle(java.util.function.Consumer<CancelMode> cancelAction) {
    this.cancelAction = cancelAction;
  }

  @Override
  public void cancel(CancelMode mode) {
    if (isRunning()) cancelAction.accept(java.util.Objects.requireNonNull(mode));
  }

  @Override
  public TweenHandle onComplete(Runnable callback) {
    super.onComplete(callback);
    return this;
  }
}
