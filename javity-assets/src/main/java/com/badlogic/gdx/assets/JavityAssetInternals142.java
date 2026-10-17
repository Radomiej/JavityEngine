package com.badlogic.gdx.assets;

import com.badlogic.gdx.Version;
import com.badlogic.gdx.assets.loaders.JavityModelPreparation142;
import com.badlogic.gdx.assets.loaders.ModelLoader;
import java.util.*;

/** Internal libGDX 1.14.2 bridge; classpath only. No reflection or replacement loading algorithms.
 * See THIRD_PARTY.md and docs/asset-streaming.md for the pinned source audit and limitations. */
public final class JavityAssetInternals142 {
  private JavityAssetInternals142() {}

  public static void checkVersion() {
    if (!"1.14.2".equals(Version.VERSION))
      throw new IllegalStateException("AssetStreams cleanup requires libGDX 1.14.2, got " + Version.VERSION);
  }

  /** Reject residual failed-load state as well as loaded assets/tasks, without taking ownership. */
  public static void requireEmpty(AssetManager manager) {
    checkVersion();
    if (manager.getLoadedAssets() != 0 || !manager.isFinished()
        || manager.assetDependencies.notEmpty() || manager.injected.notEmpty())
      throw new IllegalArgumentException("Transfer a configured empty AssetManager without abandoned metadata");
    for (var bySuffix : manager.loaders.values())
      for (var loader : bySuffix.values())
        if (loader instanceof ModelLoader<?> model && JavityModelPreparation142.hasPending(model))
          throw new IllegalArgumentException("Transfer loaders without pending ModelData");
  }

  /** Snapshot before a sole root admission. The caller must exclude manager mutations. */
  public static Map<String, SavedAsset> snapshot(AssetManager manager) {
    Map<String, SavedAsset> result = new LinkedHashMap<>();
    for (String name : manager.getAssetNames()) {
      var deps = manager.getDependencies(name);
      List<String> edges = new ArrayList<>();
      if (deps != null) for (String dep : deps) edges.add(dep);
      result.put(name, new SavedAsset(manager.get(name), manager.getReferenceCount(name), List.copyOf(edges)));
    }
    return result;
  }

  public record SavedAsset(Object object, int references, List<String> dependencies) {}

  /** Wait outside the manager monitor: an outstanding worker may still inject dependencies.
   * A barrier on 1.14.2's single worker also covers ancestors discarded by handleTaskError. */
  public static void abandonFailedTasks(AssetManager manager) {
    manager.executor.submit(() -> null).get();
    synchronized (manager) {
      manager.tasks.clear();
      manager.loadQueue.clear();
      manager.injected.clear();
    }
  }

  /** Exact rollback of registered assets. Throwing Disposables are attempted once, forgotten,
   * and reported; their own incomplete resource release cannot be repaired by the manager. */
  public static Throwable restore(AssetManager manager, Map<String, SavedAsset> baseline) {
    Throwable failure = null;
    Map<String, SavedAsset> current = snapshot(manager);
    List<String> order = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (String name : current.keySet()) visit(name, current, baseline.keySet(), seen, order);
    Collections.reverse(order); // parents before dependencies, including shared descendants
    for (String name : order) manager.assetDependencies.remove(name);
    for (String name : order) {
      try {
        // A previous AssetManager.unload may already have invoked a throwing dispose.
        if (manager.getReferenceCount(name) > 0) {
          manager.setReferenceCount(name, 1);
          manager.unload(name);
        }
      } catch (RuntimeException | Error ex) {
        failure = append(failure, ex);
      } finally {
        var type = manager.assetTypes.remove(name);
        if (type != null) manager.assets.get(type).remove(name);
        manager.assetDependencies.remove(name);
      }
    }
    for (var saved : baseline.entrySet()) {
      String name = saved.getKey();
      if (!manager.isLoaded(name) || manager.get(name) != saved.getValue().object())
        failure = append(failure, new IllegalStateException("Baseline asset lost during load: " + name));
      else manager.setReferenceCount(name, saved.getValue().references());
    }
    for (String name : manager.assetDependencies.keys().toArray())
      if (!manager.isLoaded(name)) manager.assetDependencies.remove(name);
    manager.injected.clear();
    for (var bySuffix : manager.loaders.values())
      for (var loader : bySuffix.values())
        if (loader instanceof ModelLoader<?> model)
          JavityModelPreparation142.discardUnloaded(model, manager::isLoaded);
    return failure;
  }

  private static void visit(String name, Map<String, SavedAsset> assets, Set<String> baseline,
                            Set<String> seen, List<String> order) {
    if (baseline.contains(name) || !assets.containsKey(name) || !seen.add(name)) return;
    for (String dependency : assets.get(name).dependencies()) visit(dependency, assets, baseline, seen, order);
    order.add(name);
  }

  /** Use the ordinary unload path; retain a recovery target if a user Disposable throws. */
  public static Throwable releaseRoot(AssetManager manager, String root) {
    Map<String, SavedAsset> target = snapshot(manager);
    decrement(root, target);
    try {
      manager.unload(root);
      return null;
    } catch (RuntimeException | Error ex) {
      return append(ex, restore(manager, target));
    }
  }

  private static void decrement(String name, Map<String, SavedAsset> target) {
    SavedAsset saved = target.get(name);
    if (saved == null) return;
    if (saved.references() == 1) target.remove(name);
    else target.put(name, new SavedAsset(saved.object(), saved.references() - 1, saved.dependencies()));
    for (String dep : saved.dependencies()) decrement(dep, target);
  }

  public static Throwable append(Throwable first, Throwable next) {
    if (next == null) return first;
    if (first == null) return next;
    if (first != next) first.addSuppressed(next);
    return first;
  }
}
