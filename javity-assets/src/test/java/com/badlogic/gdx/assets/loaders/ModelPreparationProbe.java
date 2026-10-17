package com.badlogic.gdx.assets.loaders;

/** Test-only visibility into the exact retained ModelData array. */
public final class ModelPreparationProbe {
  public static int count(ModelLoader<?> loader) {
    synchronized (loader.items) { return loader.items.size; }
  }
}
