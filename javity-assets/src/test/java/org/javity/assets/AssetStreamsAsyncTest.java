package org.javity.assets;

import static org.junit.Assert.*;

import com.badlogic.gdx.assets.*;
import com.badlogic.gdx.assets.loaders.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.utils.Array;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.javity.core.Game;
import org.junit.Test;

public class AssetStreamsAsyncTest {
  static final class AsyncLoader extends AsynchronousAssetLoader<AssetStreamsOwnershipTest.Item, AssetLoaderParameters<AssetStreamsOwnershipTest.Item>> {
    final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
    final List<AssetStreamsOwnershipTest.Item> made = new ArrayList<>();
    volatile Thread preparationThread;
    Thread syncThread;
    int preparations;
    boolean returnDependencies;
    AsyncLoader() { super(FileHandle::new); }
    public Array<AssetDescriptor> getDependencies(String name, FileHandle file, AssetLoaderParameters<AssetStreamsOwnershipTest.Item> p) {
      if (returnDependencies) { entered.countDown(); await(release); return new Array<>(); }
      return null;
    }
    public void loadAsync(AssetManager manager, String name, FileHandle file, AssetLoaderParameters<AssetStreamsOwnershipTest.Item> p) {
      preparations++; preparationThread = Thread.currentThread(); entered.countDown(); await(release);
    }
    public AssetStreamsOwnershipTest.Item loadSync(AssetManager manager, String name, FileHandle file, AssetLoaderParameters<AssetStreamsOwnershipTest.Item> p) {
      syncThread = Thread.currentThread(); var item = new AssetStreamsOwnershipTest.Item(name); made.add(item); return item;
    }
  }
  static void await(CountDownLatch latch) {
    try { if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("latch timed out"); }
    catch (InterruptedException ex) { throw new AssertionError(ex); }
  }
  static void settle(AssetStreams streams, AssetLease<?> lease) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while ((lease.state() == AssetLease.State.QUEUED || lease.state() == AssetLease.State.LOADING) && System.nanoTime() < deadline) {
      streams.onUiUpdate(0); streams.finishFrame(); Thread.yield();
    }
  }
  @Test public void cancellationNeverPublishesOldGenerationAndFreshRequestLoadsSeparately() {
    AssetManager manager = new AssetManager(FileHandle::new, false); AsyncLoader loader = new AsyncLoader();
    manager.setLoader(AssetStreamsOwnershipTest.Item.class, loader);
    AssetStreams streams = new AssetStreams(manager, AssetStreamsOwnershipTest.BUDGET);
    try {
      var key = new AssetKey<>("item", AssetStreamsOwnershipTest.Item.class);
      var old = streams.request(key, 0, 10); streams.onUiUpdate(0); await(loader.entered); old.close();
      var fresh = streams.request(key, 0, 10); streams.onUiUpdate(0);
      assertEquals(AssetLease.State.QUEUED, fresh.state()); loader.release.countDown(); settle(streams, fresh);
      assertEquals(AssetLease.State.CLOSED, old.state()); assertEquals(AssetLease.State.READY, fresh.state());
      assertEquals(2, loader.preparations); assertNotSame(loader.preparationThread, loader.syncThread);
      assertSame(Thread.currentThread(), loader.syncThread);
      assertEquals(1, loader.made.getFirst().disposals); assertSame(Thread.currentThread(), loader.made.getFirst().disposedOn);
      assertSame(loader.made.getLast(), fresh.get());
    } finally { loader.release.countDown(); streams.dispose(); }
    assertEquals(1, loader.made.getLast().disposals);
  }
  @Test public void sceneCloseDrainsDependencyInjectionWithoutHoldingManagerMonitor() throws Exception {
    AssetManager manager = new AssetManager(FileHandle::new, false); AsyncLoader loader = new AsyncLoader(); loader.returnDependencies = true;
    manager.setLoader(AssetStreamsOwnershipTest.Item.class, loader);
    AssetStreams streams = new AssetStreams(manager, AssetStreamsOwnershipTest.BUDGET);
    Game game = new Game(); game.addSystem(streams);
    var lease = streams.request(new AssetKey<>("item", AssetStreamsOwnershipTest.Item.class), 0, 10);
    streams.onUiUpdate(0); await(loader.entered);
    AtomicReference<Throwable> helperFailure = new AtomicReference<>();
    Thread releaser = new Thread(() -> {
      try {
        // Owner closes publications before draining. Wait for the owner to enter that drain.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!streams.isDisposed() && System.nanoTime() < deadline) Thread.yield();
        synchronized (manager) { loader.release.countDown(); }
      } catch (Throwable ex) { helperFailure.set(ex); loader.release.countDown(); }
    });
    releaser.start(); game.dispose(); releaser.join(5000);
    assertNull(helperFailure.get()); assertFalse(releaser.isAlive()); assertEquals(AssetLease.State.CLOSED, lease.state());
    assertEquals(1, loader.made.size()); assertEquals(1, loader.made.getFirst().disposals);
    assertSame(Thread.currentThread(), loader.syncThread); assertSame(Thread.currentThread(), loader.made.getFirst().disposedOn);
    assertEquals(0, manager.getLoadedAssets());
  }
  @Test public void defaultG3djLoaderDropsEveryAbandonedModelDataRecord() throws Exception {
    var directory = Files.createTempDirectory("javity-model-failure");
    var modelPath = directory.resolve("model.g3dj");
    Files.writeString(modelPath, """
        {"version":[0,1],"id":"cpu-fixture","meshes":[],
         "materials":[{"id":"mat","textures":[{"id":"missing","filename":"missing.png","type":"DIFFUSE"}]}],
         "nodes":[],"animations":[]}
        """);
    AssetManager manager = new AssetManager(FileHandle::new);
    ModelLoader<?> loader = (ModelLoader<?>) manager.getLoader(Model.class, modelPath.toString());
    AssetStreams streams = new AssetStreams(manager, AssetStreamsOwnershipTest.BUDGET);
    try {
      for (int i = 0; i < 5; i++) {
        var lease = streams.request(new AssetKey<>(modelPath.toString(), Model.class), 0, 10);
        settle(streams, lease); assertEquals(String.valueOf(lease.failure()), AssetLease.State.FAILED, lease.state());
        assertEquals(0, ModelPreparationProbe.count(loader)); assertEquals(0, AssetMetadataProbe.dependencyKeys(manager));
        assertEquals(0, manager.getLoadedAssets()); lease.close();
      }
    } finally { streams.dispose(); Files.delete(modelPath); Files.delete(directory); }
  }
  @Test public void pendingDefaultModelDataRejectsTransferEvenAfterManagerClear() throws Exception {
    var path = Files.createTempFile("javity-pending-model", ".g3dj");
    Files.writeString(path, """
        {"version":[0,1],"id":"fixture","meshes":[],"materials":[],"nodes":[]}
        """);
    AssetManager manager = new AssetManager(FileHandle::new);
    ModelLoader<?> loader = (ModelLoader<?>) manager.getLoader(Model.class, path.toString());
    try {
      loader.getDependencies(path.toString(), new FileHandle(path.toFile()), null);
      manager.clear();
      assertEquals(0, manager.getLoadedAssets()); assertTrue(manager.isFinished());
      assertEquals(1, ModelPreparationProbe.count(loader));
      assertThrows(IllegalArgumentException.class, () -> new AssetStreams(manager, AssetStreamsOwnershipTest.BUDGET));
      assertEquals(1, ModelPreparationProbe.count(loader)); // failed validation does not take ownership
    } finally {
      JavityModelPreparation142.discardUnloaded(loader, manager::isLoaded);
      manager.dispose(); Files.delete(path);
    }
  }

}
