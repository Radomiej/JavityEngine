package com.badlogic.gdx.assets;

/** Test-only assertion against orphan keys, not merely public dependency contents. */
public final class AssetMetadataProbe {
  public static int dependencyKeys(AssetManager manager) { return manager.assetDependencies.size; }
  public static int tasks(AssetManager manager) { return manager.tasks.size; }
}
