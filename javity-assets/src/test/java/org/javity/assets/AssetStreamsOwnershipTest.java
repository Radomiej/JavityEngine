package org.javity.assets;

import static org.junit.Assert.*;

import com.badlogic.gdx.assets.*;
import com.badlogic.gdx.assets.loaders.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class AssetStreamsOwnershipTest {
  static final StreamBudget BUDGET = new StreamBudget(100, 4, 10000, 100, 0);
  static final class Item implements Disposable {
    final String name;
    int disposals;
    Thread disposedOn;
    boolean throwOnDispose;
    Item(String name) { this.name = name; }
    public void dispose() {
      disposals++;
      disposedOn = Thread.currentThread();
      if (throwOnDispose) throw new IllegalStateException("dispose " + name);
    }
  }
  static final class Loader extends SynchronousAssetLoader<Item, AssetLoaderParameters<Item>> {
    final Map<String, List<String>> edges = new HashMap<>();
    final Set<String> failures = new HashSet<>();
    final Set<String> throwingDisposers = new HashSet<>();
    final Map<String, List<Item>> made = new HashMap<>();
    Loader() { super(FileHandle::new); }
    public Array<AssetDescriptor> getDependencies(String name, FileHandle file, AssetLoaderParameters<Item> p) {
      Array<AssetDescriptor> result = new Array<>();
      for (String dep : edges.getOrDefault(name, List.of())) result.add(new AssetDescriptor<>(dep, Item.class));
      return result;
    }
    public Item load(AssetManager manager, String name, FileHandle file, AssetLoaderParameters<Item> p) {
      if (failures.contains(name)) throw new IllegalStateException("failed " + name);
      Item item = new Item(name);
      item.throwOnDispose = throwingDisposers.contains(name);
      made.computeIfAbsent(name, n -> new ArrayList<>()).add(item);
      return item;
    }
  }
  static final class TrackingManager extends AssetManager {
    int disposals;
    TrackingManager() { super(FileHandle::new, false); }
    @Override public void dispose() { disposals++; super.dispose(); }
  }
  static final class Fixture implements AutoCloseable {
    final TrackingManager manager = new TrackingManager();
    final Loader loader = new Loader();
    final AssetStreams streams;
    Fixture() { manager.setLoader(Item.class, loader); streams = new AssetStreams(manager, BUDGET); }
    AssetLease<Item> request(String name) { return streams.request(new AssetKey<>(name, Item.class), 0, 10); }
    // Force actual admission pressure while retaining the stated number of pinned roots.
    void evictClosed(int pinnedRoots) {
      var pressure = streams.request(new AssetKey<>("pressure", Item.class), 0, 10000 - pinnedRoots * 10L);
      try { streams.finishFrame(); } finally { pressure.close(); }
    }
    void ready(AssetLease<?> lease) {
      for (int i = 0; i < 10000 && lease.state() != AssetLease.State.READY && lease.state() != AssetLease.State.FAILED; i++) {
        streams.onUiUpdate(0); streams.finishFrame(); Thread.yield();
      }
      assertEquals(String.valueOf(lease.failure()), AssetLease.State.READY, lease.state());
    }
    void failed(AssetLease<?> lease) {
      for (int i = 0; i < 10000 && lease.state() != AssetLease.State.FAILED; i++) {
        streams.onUiUpdate(0); streams.finishFrame(); Thread.yield();
      }
      assertEquals(AssetLease.State.FAILED, lease.state());
      streams.finishFrame();
    }
    public void close() { streams.dispose(); }
  }
  @Test public void sameKeyLoadsOnce() {
    try (Fixture f = new Fixture()) {
      var a = f.request("a"); var b = f.request("a"); f.ready(a);
      assertSame(a.get(), b.get()); assertEquals(1, f.loader.made.get("a").size());
      assertEquals(1, f.manager.getReferenceCount("a"));
    }
  }
  @Test public void closingOneLeaseKeepsSecondReady() {
    try (Fixture f = new Fixture()) {
      var a = f.request("a"); var b = f.request("a"); f.ready(a); Item item = a.get();
      a.close(); f.streams.finishFrame(); assertSame(item, b.get()); assertEquals(0, item.disposals);
      b.close(); assertEquals(0, item.disposals); f.evictClosed(0); assertEquals(1, item.disposals);
    }
  }
  @Test public void sharedDependencySurvivesFirstRootUnload() {
    try (Fixture f = new Fixture()) {
      f.loader.edges.put("a", List.of("good")); f.loader.edges.put("b", List.of("good"));
      var a = f.request("a"); var b = f.request("b"); f.ready(a); f.ready(b);
      Item good = f.manager.get("good", Item.class); assertEquals(2, f.manager.getReferenceCount("good"));
      a.close(); f.evictClosed(1); assertEquals(0, good.disposals); assertEquals(1, f.manager.getReferenceCount("good"));
      b.close(); f.evictClosed(0); assertEquals(1, good.disposals); assertEquals(0, f.manager.getLoadedAssets());
    }
  }
  @Test public void conflictingTypeOrEstimateRefused() {
    try (Fixture f = new Fixture()) {
      f.request("a");
      assertThrows(IllegalArgumentException.class, () -> f.streams.request(new AssetKey<>("a", String.class), 0, 10));
      assertThrows(IllegalArgumentException.class, () -> f.streams.request(new AssetKey<>("a", Item.class), 0, 11));
      assertThrows(IllegalArgumentException.class, () -> f.streams.request(new AssetKey<>("huge", Item.class), 0, 10001));
    }
  }
  @Test public void closeIdempotentAndGetAfterCloseFails() {
    try (Fixture f = new Fixture()) {
      var a = f.request("a"); f.ready(a); Item item = a.get(); a.close(); a.close();
      assertThrows(IllegalStateException.class, a::get); f.evictClosed(0); assertEquals(1, item.disposals);
    }
  }
  @Test public void wrongThreadRefusedBeforeDisposeMutation() throws Exception {
    Fixture f = new Fixture(); var a = f.request("a"); f.ready(a); Item item = a.get();
    for (Runnable operation : List.<Runnable>of(() -> f.request("b"), a::state, a::get, a::failure, a::close,
        () -> f.streams.onUiUpdate(0), f.streams::finishFrame, f.streams::dispose)) wrongThread(operation);
    assertFalse(f.streams.isDisposed()); assertEquals(0, item.disposals); assertSame(item, a.get());
    f.close(); f.close(); assertEquals(1, item.disposals); assertEquals(1, f.manager.disposals); wrongThread(f.streams::dispose);
  }
  static void wrongThread(Runnable action) throws Exception {
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread thread = new Thread(() -> { try { action.run(); } catch (Throwable t) { failure.set(t); } });
    thread.start(); thread.join(3000); assertFalse(thread.isAlive()); assertTrue(String.valueOf(failure.get()), failure.get() instanceof IllegalStateException);
  }
  @Test public void failedDependencyRestoresPinnedTransitiveBaselineAndRetriesExactly() {
    try (Fixture f = new Fixture()) {
      f.loader.edges.put("b", List.of("good")); f.loader.edges.put("good", List.of("child"));
      var b = f.request("b"); f.ready(b); Item good = f.manager.get("good", Item.class); Item child = f.manager.get("child", Item.class);
      f.loader.edges.put("a", List.of("good", "bad")); f.loader.failures.add("bad");
      var a = f.request("a"); var unrelated = f.request("later"); f.failed(a);
      assertEquals(AssetLease.State.QUEUED, unrelated.state());
      assertTrue(a.failure().getMessage().contains("a")); assertTrue(a.failure().getMessage().contains("bad"));
      assertSame(good, f.manager.get("good")); assertSame(child, f.manager.get("child")); assertEquals(1, f.manager.getReferenceCount("good")); assertEquals(1, f.manager.getReferenceCount("child"));
      assertNull(f.manager.getDependencies("a")); assertNull(f.manager.getDependencies("bad"));
      f.loader.failures.clear(); var retry = f.request("a"); f.ready(unrelated); f.ready(retry);
      assertEquals(AssetLease.State.FAILED, a.state()); assertEquals(2, f.manager.getReferenceCount("good"));
      assertEquals(2, f.manager.getDependencies("a").size);
      retry.close(); f.evictClosed(2); assertEquals(1, f.manager.getReferenceCount("good"));
      b.close(); unrelated.close(); f.evictClosed(0); assertEquals(1, good.disposals); assertEquals(1, child.disposals);
    }
  }
  @Test public void earlierCompletedSiblingAndNestedFailedMetadataAreRemoved() {
    try (Fixture f = new Fixture()) {
      f.loader.edges.put("nested", List.of("bad")); f.loader.failures.add("bad");
      for (int i = 0; i < 20; i++) {
        String root = "failure" + i; f.loader.edges.put(root, List.of("nested", "good"));
        var lease = f.request(root); f.failed(lease);
        assertEquals(0, f.manager.getLoadedAssets()); assertEquals(0, AssetMetadataProbe.dependencyKeys(f.manager)); assertNull(f.manager.getDependencies(root));
        assertNull(f.manager.getDependencies("nested")); assertNull(f.manager.getDependencies("bad"));
        assertEquals(1, f.loader.made.get("good").get(i).disposals);
      }
    }
  }
  @Test public void rootFailureDoesNotDoubleDecrementBaseline() {
    try (Fixture f = new Fixture()) {
      f.loader.edges.put("b", List.of("good")); var b = f.request("b"); f.ready(b);
      f.loader.edges.put("a", List.of("good")); f.loader.failures.add("a"); f.failed(f.request("a"));
      assertEquals(1, f.manager.getReferenceCount("good")); assertEquals(0, f.manager.get("good", Item.class).disposals);
    }
  }
  @Test public void populatedOrQueuedManagerTransferRejected() {
    TrackingManager manager = new TrackingManager(); manager.setLoader(Item.class, new Loader());
    manager.load("a", Item.class);
    assertThrows(IllegalArgumentException.class, () -> new AssetStreams(manager, BUDGET));
    manager.finishLoading(); assertThrows(IllegalArgumentException.class, () -> new AssetStreams(manager, BUDGET));
    assertEquals(0, manager.disposals); assertEquals(1, manager.getLoadedAssets()); manager.dispose();
  }
  @Test public void secondaryDisposalFailureClearsStrandedAncestorsAndContinuesCleanup() {
    try (Fixture f = new Fixture()) {
      f.loader.edges.put("root", List.of("nested", "sibling"));
      f.loader.edges.put("nested", List.of("throwing"));
      f.loader.failures.add("nested"); f.loader.throwingDisposers.add("throwing");
      var root = f.request("root");
      for (int i = 0; i < 100 && root.state() != AssetLease.State.FAILED; i++) f.streams.onUiUpdate(0);
      assertEquals(AssetLease.State.FAILED, root.state());
      assertEquals(1, AssetMetadataProbe.tasks(f.manager)); // actual libGDX secondary-error hole
      f.streams.finishFrame();
      assertEquals(0, AssetMetadataProbe.tasks(f.manager)); assertEquals(0, AssetMetadataProbe.dependencyKeys(f.manager));
      assertEquals(0, f.manager.getLoadedAssets());
      assertEquals(1, f.loader.made.get("throwing").getFirst().disposals);
      assertEquals(1, f.loader.made.get("sibling").getFirst().disposals);
      assertNotNull(root.failure()); f.ready(f.request("later"));
    }
  }
  @Test public void rollbackDisposalFailureIsReportedAndOtherResourcesStillRelease() {
    try (Fixture f = new Fixture()) {
      f.loader.edges.put("root", List.of("bad", "one", "two"));
      f.loader.failures.add("bad"); f.loader.throwingDisposers.add("one");
      var root = f.request("root");
      for (int i = 0; i < 100 && root.state() != AssetLease.State.FAILED; i++) f.streams.onUiUpdate(0);
      assertThrows(IllegalStateException.class, f.streams::finishFrame);
      assertEquals(1, root.failure().getSuppressed().length);
      assertEquals(0, f.manager.getLoadedAssets()); assertEquals(0, AssetMetadataProbe.dependencyKeys(f.manager));
      assertEquals(1, f.loader.made.get("one").getFirst().disposals);
      assertEquals(1, f.loader.made.get("two").getFirst().disposals);
    }
  }
  @Test public void finalReleaseContinuesAfterThrowingParentWithoutDoubleDisposal() {
    Fixture f = new Fixture();
    f.loader.edges.put("root", List.of("child")); f.loader.throwingDisposers.add("root");
    var root = f.request("root"); f.ready(root); root.close();
    assertThrows(IllegalStateException.class, () -> f.evictClosed(0));
    assertEquals(1, f.loader.made.get("root").getFirst().disposals);
    assertEquals(1, f.loader.made.get("child").getFirst().disposals);
    assertEquals(0, AssetMetadataProbe.dependencyKeys(f.manager)); f.close();
  }
  @Test public void keyAndBudgetValidation() {
    assertThrows(IllegalArgumentException.class, () -> new AssetKey<>(" ", Item.class));
    assertThrows(NullPointerException.class, () -> new AssetKey<>("a", null));
    assertEquals(new AssetKey<>("folder/a", Item.class), new AssetKey<>("folder\\a", Item.class));
    assertThrows(IllegalArgumentException.class, () -> new StreamBudget(0, 1, 1, 1, 0));
    assertThrows(IllegalArgumentException.class, () -> new StreamBudget(1, 0, 1, 1, 0));
    assertThrows(IllegalArgumentException.class, () -> new StreamBudget(1, 1, 0, 1, 0));
    assertThrows(IllegalArgumentException.class, () -> new StreamBudget(1, 1, 1, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> new StreamBudget(1, 1, 1, 1, -1));
  }

  @Test public void managerWithAbandonedMetadataIsNotAnEmptyTransfer() {
    TrackingManager manager = new TrackingManager(); Loader loader = new Loader();
    loader.edges.put("root", List.of("bad")); loader.failures.add("bad");
    manager.setLoader(Item.class, loader); manager.setErrorListener((asset, cause) -> {});
    try {
      manager.load("root", Item.class); manager.finishLoading();
      assertEquals(0, manager.getLoadedAssets()); assertTrue(manager.isFinished());
      assertTrue(AssetMetadataProbe.dependencyKeys(manager) > 0);
      assertThrows(IllegalArgumentException.class, () -> new AssetStreams(manager, BUDGET));
      assertEquals(0, manager.disposals); assertTrue(AssetMetadataProbe.dependencyKeys(manager) > 0);
    } finally { manager.dispose(); }
  }

}
