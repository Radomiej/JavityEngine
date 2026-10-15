package org.javity.core;

import com.badlogic.gdx.utils.Disposable;

/** Optional scene service, registered before Game.start and owned by that game. */
public abstract class GameSystem implements Disposable {
  private Game game;
  private boolean initialized, disposed;

  public final Game getGame() {
    return game;
  }

  public final boolean isDisposed() {
    return disposed;
  }

  public void onCreate() {}

  public void onBeforeUpdate(float delta) {}

  /** Unscaled service phase, called even while world time is paused or frozen. */
  public void onUiUpdate(float unscaledDelta) {}

  public void onAfterUpdate(float delta) {}

  public void onDestroy() {}

  protected void onRelease() {}

  final void attach(Game game) {
    if (this.game != null || disposed)
      throw new IllegalArgumentException("System already attached or disposed");
    this.game = game;
  }

  final void initialize() {
    if (!initialized) {
      initialized = true;
      onCreate();
    }
  }

  @Override
  public final void dispose() {
    if (disposed) return;
    disposed = true;
    Throwable failure = null;
    try {
      if (initialized) onDestroy();
    } catch (RuntimeException | Error e) {
      failure = e;
    }
    try {
      onRelease();
    } catch (RuntimeException | Error e) {
      if (failure == null) failure = e;
      else failure.addSuppressed(e);
    }
    if (failure instanceof RuntimeException e) throw e;
    if (failure instanceof Error e) throw e;
  }
}
