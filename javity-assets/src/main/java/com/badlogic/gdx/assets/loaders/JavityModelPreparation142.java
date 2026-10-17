package com.badlogic.gdx.assets.loaders;

import java.util.function.Predicate;

/** Internal libGDX 1.14.2 bridge; see THIRD_PARTY.md and docs/asset-streaming.md. */
public final class JavityModelPreparation142 {
  private JavityModelPreparation142() {}

  public static boolean hasPending(ModelLoader<?> loader) {
    synchronized (loader.items) { return loader.items.notEmpty(); }
  }

  /** Only after the exclusive manager's worker is quiescent. No loader algorithms replaced. */
  public static void discardUnloaded(ModelLoader<?> loader, Predicate<String> loaded) {
    synchronized (loader.items) {
      for (int i = loader.items.size - 1; i >= 0; i--)
        if (!loaded.test(loader.items.get(i).key)) loader.items.removeIndex(i);
    }
  }
}
