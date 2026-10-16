package org.javity.assets;

import static org.junit.Assert.*;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.files.FileHandle;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.javity.core.Game;
import org.javity.services.TimeManager;
import org.junit.Test;

/** Real AssetManager transactions with controlled CPU-only Disposable loaders. */
public class AssetStreamsSchedulingTest {
  static final class Fixture implements AutoCloseable {
    final AssetManager manager = new AssetManager(FileHandle::new, false);
    final AssetStreamsOwnershipTest.Loader loader = new AssetStreamsOwnershipTest.Loader();
    final AssetStreams streams;
    Fixture(int queued, long bytes, int entries) {
      manager.setLoader(AssetStreamsOwnershipTest.Item.class, loader);
      streams = new AssetStreams(manager, new StreamBudget(queued, 8, bytes, entries, 0));
    }
    AssetLease<AssetStreamsOwnershipTest.Item> request(String name, int priority, long bytes) {
      return streams.request(new AssetKey<>(name, AssetStreamsOwnershipTest.Item.class), priority, bytes);
    }
    void ready(AssetLease<?> lease) {
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while (lease.state() != AssetLease.State.READY && lease.state() != AssetLease.State.FAILED && System.nanoTime() < deadline) {
        streams.onUiUpdate(0); streams.finishFrame(); Thread.yield();
      }
      assertEquals(String.valueOf(lease.failure()), AssetLease.State.READY, lease.state());
    }
    public void close() { streams.dispose(); }
  }

  @Test public void priorityThenFifo() {
    try (Fixture f = new Fixture(5, 100, 5)) {
      var low = f.request("low", 1, 10);
      var first = f.request("first", 3, 10);
      var second = f.request("second", 3, 10);
      f.ready(first); assertEquals(AssetLease.State.QUEUED, second.state()); assertEquals(AssetLease.State.QUEUED, low.state());
      f.ready(second); assertEquals(AssetLease.State.QUEUED, low.state()); f.ready(low);
    }
  }

  @Test public void queueAndInflightLimits() {
    AssetManager manager = new AssetManager(FileHandle::new, false);
    var loader = new AssetStreamsAsyncTest.AsyncLoader(); manager.setLoader(AssetStreamsOwnershipTest.Item.class, loader);
    AssetStreams streams = new AssetStreams(manager, new StreamBudget(1, 8, 100, 5, 0));
    try {
      var key = new AssetKey<>("active", AssetStreamsOwnershipTest.Item.class);
      var active = streams.request(key, 0, 10);
      assertThrows(IllegalStateException.class, () -> streams.request(new AssetKey<>("overflow", AssetStreamsOwnershipTest.Item.class), 0, 10));
      var duplicate = streams.request(key, 0, 10); // same root consumes no extra queue capacity
      streams.onUiUpdate(0); AssetStreamsAsyncTest.await(loader.entered);
      var queued = streams.request(new AssetKey<>("queued", AssetStreamsOwnershipTest.Item.class), 9, 20);
      StreamStats snapshot = streams.stats();
      assertEquals(new StreamStats(1, 1, 0, 0, 10, false), snapshot);
      assertThrows(IllegalStateException.class, () -> streams.request(new AssetKey<>("overflow", AssetStreamsOwnershipTest.Item.class), 0, 10));
      for (int i = 0; i < 10; i++) streams.onUiUpdate(0);
      assertEquals(1, streams.stats().inflight()); assertEquals(AssetLease.State.QUEUED, queued.state());
      queued.close(); var replacement = streams.request(new AssetKey<>("replacement", AssetStreamsOwnershipTest.Item.class), 0, 20);
      assertEquals(1, streams.stats().queued()); assertEquals(1, snapshot.queued());
      loader.release.countDown(); AssetStreamsAsyncTest.settle(streams, active); AssetStreamsAsyncTest.settle(streams, replacement);
      assertSame(active.get(), duplicate.get()); assertEquals(0, streams.stats().inflight());
    } finally { loader.release.countDown(); streams.dispose(); }
    for (var item : loader.made) assertEquals(1, item.disposals);
  }

  @Test public void reservationIncludesResidentBaselineAndRollbackWaitsForFrameEnd() {
    AssetManager manager = new AssetManager(FileHandle::new, false);
    var sync = new AssetStreamsOwnershipTest.Loader(); var async = new AssetStreamsAsyncTest.AsyncLoader();
    manager.setLoader(AssetStreamsOwnershipTest.Item.class, sync);
    manager.setLoader(AssetStreamsOwnershipTest.Item.class, ".async", async);
    var streams = new AssetStreams(manager, new StreamBudget(4, 8, 30, 3, 0));
    try {
      var pinned = streams.request(new AssetKey<>("pinned", AssetStreamsOwnershipTest.Item.class), 0, 10);
      AssetStreamsAsyncTest.settle(streams, pinned); assertEquals(AssetLease.State.READY, pinned.state());
      var loading = streams.request(new AssetKey<>("loading.async", AssetStreamsOwnershipTest.Item.class), 0, 10);
      streams.onUiUpdate(0); AssetStreamsAsyncTest.await(async.entered);
      var waiting = streams.request(new AssetKey<>("waiting", AssetStreamsOwnershipTest.Item.class), 0, 20);
      assertEquals(new StreamStats(1, 1, 1, 1, 20, true), streams.stats());
      var item = pinned.get(); pinned.close(); streams.finishFrame();
      assertEquals(0, item.disposals); // active rollback baseline remains frozen
      waiting.close(); async.release.countDown(); AssetStreamsAsyncTest.settle(streams, loading);
      assertEquals(new StreamStats(0, 0, 2, 1, 20, false), streams.stats());
    } finally { async.release.countDown(); streams.dispose(); }
    for (var items : sync.made.values()) for (var item : items) assertEquals(1, item.disposals);
    for (var item : async.made) assertEquals(1, item.disposals);
  }

  @Test public void failedReservationRemainsUntilRollbackAndDisposeAfterFailureIsIdempotent() {
    Fixture f = new Fixture(4, 30, 3);
    f.loader.edges.put("root", List.of("bad", "good")); f.loader.failures.add("bad");
    var root = f.request("root", 0, 20);
    for (int i = 0; i < 100 && root.state() != AssetLease.State.FAILED; i++) f.streams.onUiUpdate(0);
    assertEquals(AssetLease.State.FAILED, root.state());
    assertEquals(new StreamStats(0, 1, 0, 0, 20, false), f.streams.stats());
    assertEquals(0, f.loader.made.get("good").getFirst().disposals);
    f.streams.dispose(); f.streams.dispose();
    assertEquals(AssetLease.State.CLOSED, root.state()); assertEquals(0, f.manager.getLoadedAssets());
    assertEquals(1, f.loader.made.get("good").getFirst().disposals);
    assertEquals(new StreamStats(0, 0, 0, 0, 0, false), f.streams.stats());
  }

  @Test public void pinnedAssetsNeverEvicted() {
    try (Fixture f = new Fixture(4, 10, 1)) {
      var pinned = f.request("pinned", 0, 10); f.ready(pinned); var item = pinned.get();
      var waiting = f.request("waiting", 0, 10);
      for (int i = 0; i < 10; i++) { f.streams.onUiUpdate(0); f.streams.finishFrame(); }
      assertEquals(AssetLease.State.QUEUED, waiting.state()); assertSame(item, pinned.get()); assertEquals(0, item.disposals);
      assertEquals(new StreamStats(1, 0, 1, 1, 10, true), f.streams.stats());
      pinned.close(); assertTrue(f.streams.stats().overBudget()); f.streams.finishFrame();
      assertEquals(1, item.disposals); assertFalse(f.streams.stats().overBudget()); f.ready(waiting);
    }
  }

  @Test public void onlyFinishFrameEvicts() {
    try (Fixture f = new Fixture(4, 20, 2)) {
      var a = f.request("a", 0, 10); f.ready(a); var item = a.get(); a.close();
      f.streams.finishFrame(); assertEquals(0, item.disposals); // no pressure: cache retained
      var b = f.request("b", 0, 20);
      // Simulated open batch: requests, closes, updates, and diagnostics must leave a valid.
      for (int i = 0; i < 10; i++) { f.streams.onUiUpdate(0); f.streams.stats(); }
      assertEquals(0, item.disposals); assertEquals(AssetLease.State.QUEUED, b.state());
      f.streams.finishFrame(); assertEquals(1, item.disposals); f.ready(b);
    }
  }

  @Test public void reRequestBeforeFinishFrameKeepsResource() {
    try (Fixture f = new Fixture(4, 10, 1)) {
      var a = f.request("a", 0, 10); f.ready(a); var item = a.get(); a.close();
      var waiting = f.request("waiting", 0, 10);
      var again = f.request("a", 0, 10); f.streams.finishFrame();
      assertEquals(AssetLease.State.READY, again.state()); assertSame(item, again.get()); assertEquals(0, item.disposals);
      assertEquals(1, f.loader.made.get("a").size()); assertEquals(AssetLease.State.QUEUED, waiting.state());
      again.close(); f.streams.finishFrame(); assertEquals(1, item.disposals);
    }
  }

  @Test public void reRequestCachedResourceAndLruUsesLastAcquireOrRelease() {
    try (Fixture f = new Fixture(5, 30, 3)) {
      var a = f.request("a", 0, 10); var b = f.request("b", 0, 10); var c = f.request("c", 0, 10);
      f.ready(a); f.ready(b); f.ready(c);
      var ai = a.get(); var bi = b.get(); var ci = c.get();
      a.close(); b.close(); c.close(); f.streams.finishFrame();
      var cached = f.request("a", 0, 10); assertSame(ai, cached.get()); cached.close();
      var d = f.request("d", 0, 10); f.streams.finishFrame();
      assertEquals(0, ai.disposals); assertEquals(1, bi.disposals); assertEquals(0, ci.disposals); f.ready(d);
      assertEquals(3, f.streams.stats().ready()); assertEquals(1, f.streams.stats().pinned());
    }
  }

  @Test public void residentEntryLimitAndReservationsApplyBeforeAdmission() {
    try (Fixture f = new Fixture(5, Long.MAX_VALUE, 3)) {
      var a = f.request("a", 0, Long.MAX_VALUE); f.ready(a);
      var b = f.request("b", 0, Long.MAX_VALUE); f.streams.onUiUpdate(0);
      assertEquals(AssetLease.State.QUEUED, b.state()); assertTrue(f.streams.stats().overBudget());
      assertEquals(Long.MAX_VALUE, f.streams.stats().estimatedResidentBytes());
      a.close(); f.streams.finishFrame(); f.ready(b);
    }
    try (Fixture f = new Fixture(5, 100, 1)) {
      var a = f.request("a", 0, 0); f.ready(a); var b = f.request("b", 0, 0);
      f.streams.onUiUpdate(0); assertEquals(AssetLease.State.QUEUED, b.state()); assertTrue(f.streams.stats().overBudget());
    }
  }

  @Test public void blockedPriorityHeadDoesNotBypassOrEvictCachePointlessly() {
    try (Fixture f = new Fixture(5, 30, 3)) {
      var pinned = f.request("pinned", 0, 20); f.ready(pinned);
      var cached = f.request("cached", 0, 10); f.ready(cached); var item = cached.get(); cached.close();
      var head = f.request("head", 9, 20); var low = f.request("low", 1, 0);
      f.streams.finishFrame(); f.streams.onUiUpdate(0);
      assertEquals(0, item.disposals); assertEquals(AssetLease.State.QUEUED, head.state());
      assertEquals(AssetLease.State.QUEUED, low.state()); assertTrue(f.streams.stats().overBudget());
      pinned.close(); f.streams.finishFrame(); f.ready(head);
    }
  }

  @Test public void lateOldGenerationNotPublished() {
    new AssetStreamsAsyncTest().cancellationNeverPublishesOldGenerationAndFreshRequestLoadsSeparately();
  }
  @Test public void sceneCloseDuringLoadCleansDependencies() throws Exception {
    new AssetStreamsAsyncTest().sceneCloseDrainsDependencyInjectionWithoutHoldingManagerMonitor();
  }
  @Test public void failedLoadAllowsExplicitNewRequest() {
    try (Fixture f = new Fixture(3, 20, 2)) {
      f.loader.failures.add("a"); var failed = f.request("a", 0, 10);
      AssetStreamsAsyncTest.settle(f.streams, failed); assertEquals(AssetLease.State.FAILED, failed.state());
      f.loader.failures.clear(); var retry = f.request("a", 0, 10); f.ready(retry);
      assertEquals(AssetLease.State.FAILED, failed.state()); assertNotNull(failed.failure());
      assertEquals(new StreamStats(0, 0, 1, 1, 10, false), f.streams.stats());
    }
  }

  @Test public void statsAndAllLeaseReadsRequireOwnerThread() throws Exception {
    try (Fixture f = new Fixture(2, 20, 2)) {
      AssetStreamsOwnershipTest.wrongThread(f.streams::stats);
      f.streams.dispose(); assertEquals(new StreamStats(0, 0, 0, 0, 0, false), f.streams.stats());
      AssetStreamsOwnershipTest.wrongThread(f.streams::stats);
    }
  }

  @Test public void worldPauseStillAdvancesStreamingOnUiUpdate() {
    try (Fixture f = new Fixture(2, 20, 2)) {
      Game game = new Game(); var time = game.addSystem(new TimeManager()); game.addSystem(f.streams);
      var lease = f.request("paused", 0, 10);
      time.setBaseScale(0); game.start();
      for (int i = 0; i < 20 && lease.state() != AssetLease.State.READY; i++) { game.step(.016f); f.streams.finishFrame(); }
      assertEquals(AssetLease.State.READY, lease.state()); game.dispose();
    }
  }
}
