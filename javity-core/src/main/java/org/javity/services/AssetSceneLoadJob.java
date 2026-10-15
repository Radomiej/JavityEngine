package org.javity.services;

import com.badlogic.gdx.assets.AssetManager;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.javity.core.Game;

/** Uses a scene-owned AssetManager to prepare a Game incrementally before it starts. */
public final class AssetSceneLoadJob implements SceneLoadJob {
  private final Game scene;
  private final SceneAssets assets;
  private final Consumer<Game> configure;
  private final int updateBudgetMillis;
  private boolean ready;
  private boolean transferred;
  private boolean closed;

  public AssetSceneLoadJob(List<AssetSpec<?>> specs, Consumer<Game> configure) {
    this(specs, configure, 4);
  }

  public AssetSceneLoadJob(
      List<AssetSpec<?>> specs, Consumer<Game> configure, int updateBudgetMillis) {
    this(specs, configure, updateBudgetMillis, new AssetManager());
  }

  /** Injection overload for custom libGDX resolvers and deterministic local asset tests. */
  public AssetSceneLoadJob(
      List<AssetSpec<?>> specs,
      Consumer<Game> configure,
      int updateBudgetMillis,
      AssetManager assetManager) {
    Objects.requireNonNull(specs);
    this.configure = Objects.requireNonNull(configure);
    Objects.requireNonNull(assetManager);
    if (updateBudgetMillis < 0) throw new IllegalArgumentException("Negative update budget");
    this.updateBudgetMillis = updateBudgetMillis;
    Game newScene = new Game();
    SceneAssets newAssets = newScene.addSystem(new SceneAssets(assetManager));
    try {
      newAssets.queueSceneAssets(specs.toArray(AssetSpec<?>[]::new));
    } catch (RuntimeException | Error failure) {
      try {
        newScene.dispose();
      } catch (RuntimeException | Error cleanup) {
        failure.addSuppressed(cleanup);
      }
      throw failure;
    }
    scene = newScene;
    assets = newAssets;
  }

  @Override
  public void update(float unscaledDeltaSeconds) {
    ensureOpen();
    if (!Float.isFinite(unscaledDeltaSeconds) || unscaledDeltaSeconds < 0)
      throw new IllegalArgumentException("Invalid load delta");
    if (!ready) ready = assets.update(updateBudgetMillis);
  }

  @Override
  public double progress() {
    return closed || transferred ? 1 : assets.progress();
  }

  @Override
  public boolean isReady() {
    return ready && !closed && !transferred;
  }

  @Override
  public Game takeScene() {
    ensureOpen();
    if (!ready) throw new IllegalStateException("Scene assets are not ready");
    if (transferred) throw new IllegalStateException("Scene already transferred");
    configure.accept(scene);
    transferred = true;
    return scene;
  }

  public SceneAssets assets() {
    ensureOpen();
    return assets;
  }

  @Override
  public void close() {
    if (closed || transferred) return;
    closed = true;
    scene.dispose();
  }

  private void ensureOpen() {
    if (closed || transferred) throw new IllegalStateException("Load job is closed");
  }
}
