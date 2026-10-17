package org.javity.services;

import com.badlogic.gdx.assets.AssetManager;
import java.util.Objects;
import org.javity.core.GameSystem;

/** Scene-owned libGDX asset manager. Construct after platform initialization. */
public final class SceneAssets extends GameSystem {
  private final AssetManager assets;
  private double progress;

  public SceneAssets() {
    this(new AssetManager());
  }

  public SceneAssets(AssetManager assets) {
    this.assets = Objects.requireNonNull(assets);
  }

  /** Queue an asset without blocking. The returned manager is updated on its calling thread. */
  public SceneAssets queueSceneAssets(AssetSpec<?>... specs) {
    ensureOpen();
    Objects.requireNonNull(specs);
    for (AssetSpec<?> spec : specs) Objects.requireNonNull(spec).queue(assets);
    progress = Math.max(progress, assets.getProgress());
    return this;
  }

  /** Advances asynchronous loading for at most the requested millisecond budget. */
  public boolean update(int budgetMillis) {
    ensureOpen();
    if (budgetMillis < 0) throw new IllegalArgumentException("Negative asset update budget");
    boolean done = assets.update(budgetMillis);
    progress = Math.max(progress, assets.getProgress());
    return done;
  }

  /** Monotonic loading progress in the inclusive range 0..1. */
  public double progress() {
    ensureOpen();
    progress = Math.max(progress, assets.getProgress());
    return Math.max(0, Math.min(1, progress));
  }

  /** Returns an already-loaded asset and fails clearly if it has not completed. */
  public <T> T get(String path, Class<T> type) {
    ensureOpen();
    if (!assets.isLoaded(path, type))
      throw new IllegalStateException("Asset is not loaded: " + path);
    return assets.get(path, type);
  }

  /** Legacy convenience API; unlike queueSceneAssets this method blocks until loaded. */
  public <T> T load(String path, Class<T> type) {
    ensureOpen();
    if (!assets.isLoaded(path, type)) {
      assets.load(path, type);
      assets.finishLoadingAsset(path);
      progress = Math.max(progress, assets.getProgress());
    }
    return assets.get(path, type);
  }

  public void unload(String path) {
    ensureOpen();
    if (assets.isLoaded(path)) assets.unload(path);
  }

  /** Exposes the backing manager for integrations that need libGDX-specific operations. */
  public AssetManager assetManager() {
    ensureOpen();
    return assets;
  }

  private void ensureOpen() {
    if (isDisposed()) throw new IllegalStateException("Assets closed");
  }

  @Override
  protected void onRelease() {
    assets.dispose();
  }
}
