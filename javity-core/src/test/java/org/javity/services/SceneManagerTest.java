package org.javity.services;

import static org.junit.Assert.*;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader;
import com.badlogic.gdx.files.FileHandle;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.javity.core.Game;
import org.junit.Test;

public class SceneManagerTest {
  @Test
  public void directStartFailureLeavesOldSceneAliveAndDisposesPartialNext() {
    SceneManager manager = new SceneManager();
    TrackingGame old = new TrackingGame();
    manager.request(() -> old);
    manager.step(0);
    TrackingGame failed = new TrackingGame();
    failed.failCreate = true;
    manager.request(() -> failed);

    assertThrows(IllegalStateException.class, () -> manager.step(0));
    assertSame(old, manager.current());
    assertFalse(old.isDisposed());
    assertTrue(failed.isDisposed());
    manager.close();
  }

  @Test
  public void directFactoryReentrantReplacementCannotAdoptStaleGame() {
    SceneManager manager = new SceneManager();
    TrackingGame old = new TrackingGame();
    manager.request(() -> old);
    manager.step(0);
    TrackingGame stale = new TrackingGame();
    TrackingGame newest = new TrackingGame();
    manager.request(
        () -> {
          manager.request(() -> newest);
          return stale;
        });

    manager.step(0);
    assertTrue(stale.isDisposed());
    assertSame(newest, manager.current());
    assertTrue(old.isDisposed());
    manager.close();
  }

  @Test
  public void loadedTransitionKeepsOldSceneUntilReadyAndFadesOnCappedClock() {
    SceneManager manager = new SceneManager();
    TrackingGame old = new TrackingGame();
    manager.request(() -> old);
    manager.step(0);
    TrackingGame next = new TrackingGame();
    ManualJob job = new ManualJob(next);
    LoadHandle handle = manager.requestLoading(job, new SceneTransition(.2, .2));

    manager.step(.5f);
    assertSame(old, manager.current());
    assertFalse(old.isDisposed());
    assertEquals(LoadState.FADING_OUT, handle.state());
    assertEquals(.5, manager.overlayAlpha(), .0001);
    manager.step(.5f);
    assertSame(next, manager.current());
    assertTrue(old.isDisposed());
    assertEquals(LoadState.FADING_IN, handle.state());
    assertEquals(1, manager.overlayAlpha(), .0001);
    handle.cancel();
    assertEquals(LoadState.FADING_IN, handle.state());
    manager.step(.5f);
    assertEquals(.5, manager.overlayAlpha(), .0001);
    manager.step(.5f);
    assertEquals(LoadState.COMPLETED, handle.state());
    assertTrue(handle.isDone());
    assertEquals(0, manager.overlayAlpha(), 0);
    manager.close();
  }

  @Test
  public void cancellationAndLoadFailureKeepOldAndReleaseJobOnce() {
    SceneManager manager = new SceneManager();
    TrackingGame old = new TrackingGame();
    manager.request(() -> old);
    manager.step(0);
    ManualJob cancelledJob = new ManualJob(new TrackingGame());
    LoadHandle cancelled = manager.requestLoading(cancelledJob, SceneTransition.none());
    cancelled.cancel();
    assertEquals(LoadState.CANCELLED, cancelled.state());
    assertEquals(1, cancelledJob.closeCount.get());
    assertSame(old, manager.current());

    TrackingGame failedScene = new TrackingGame();
    failedScene.failCreate = true;
    ManualJob failedJob = new ManualJob(failedScene);
    AtomicInteger failures = new AtomicInteger();
    LoadHandle failed =
        manager
            .requestLoading(failedJob, SceneTransition.none())
            .onFailure(error -> failures.incrementAndGet());
    manager.step(.1f);
    assertEquals(LoadState.FAILED, failed.state());
    assertNotNull(failed.error());
    assertEquals(1, failures.get());
    assertSame(old, manager.current());
    assertFalse(old.isDisposed());
    assertTrue(failedScene.isDisposed());
    assertEquals(1, failedJob.closeCount.get());
    assertEquals(0, manager.overlayAlpha(), 0);
    manager.close();
  }

  @Test
  public void closeFromCandidateStartPreventsStaleAdoption() {
    SceneManager manager = new SceneManager();
    TrackingGame old = new TrackingGame();
    manager.request(() -> old);
    manager.step(0);
    TrackingGame candidate = new TrackingGame();
    candidate.onCreateAction = manager::close;
    manager.request(() -> candidate);

    manager.step(0);
    assertNull(manager.current());
    assertTrue(old.isDisposed());
    assertTrue(candidate.isDisposed());
  }

  @Test
  public void callbacksThatReplaceLoadCannotAdoptOrAdvanceStaleJob() {
    SceneManager manager = new SceneManager();
    TrackingGame old = new TrackingGame();
    manager.request(() -> old);
    manager.step(0);
    ManualJob replacement = new ManualJob(new TrackingGame());
    ManualJob stale = new ManualJob(new TrackingGame());
    LoadHandle[] replacementHandle = new LoadHandle[1];
    AtomicInteger staleProgressCalls = new AtomicInteger();
    stale.updateAction =
        () -> replacementHandle[0] = manager.requestLoading(replacement, SceneTransition.none());
    stale.progressAction = staleProgressCalls::incrementAndGet;
    LoadHandle original = manager.requestLoading(stale, SceneTransition.none());

    manager.step(.1f);

    assertEquals(LoadState.CANCELLED, original.state());
    assertEquals(0, staleProgressCalls.get());
    assertSame(old, manager.current());
    assertEquals(LoadState.LOADING, replacementHandle[0].state());
    manager.close();
  }

  @Test
  public void progressAndReadyCallbacksCannotReviveCancelledHandles() {
    for (boolean cancelFromReady : new boolean[] {false, true}) {
      SceneManager manager = new SceneManager();
      TrackingGame old = new TrackingGame();
      manager.request(() -> old);
      manager.step(0);
      ManualJob job = new ManualJob(new TrackingGame());
      LoadHandle[] handle = new LoadHandle[1];
      if (cancelFromReady) job.readyAction = () -> handle[0].cancel();
      else job.progressAction = () -> handle[0].cancel();
      handle[0] = manager.requestLoading(job, SceneTransition.none());

      manager.step(.1f);

      assertEquals(LoadState.CANCELLED, handle[0].state());
      assertSame(old, manager.current());
      assertEquals(cancelFromReady ? 1 : 0, job.readyCalls);
      manager.close();
    }
  }

  @Test
  public void reentrantRequestDuringOldJobCloseWinsAndOuterJobIsReleased() {
    SceneManager manager = new SceneManager();
    TrackingGame old = new TrackingGame();
    manager.request(() -> old);
    manager.step(0);
    ManualJob innermost = new ManualJob(new TrackingGame());
    ManualJob existing = new ManualJob(new TrackingGame());
    existing.closeAction = () -> manager.requestLoading(innermost, SceneTransition.none());
    manager.requestLoading(existing, SceneTransition.none());
    ManualJob outer = new ManualJob(new TrackingGame());

    LoadHandle stale = manager.requestLoading(outer, SceneTransition.none());

    assertEquals(LoadState.CANCELLED, stale.state());
    assertTrue(outer.scene.isDisposed());
    manager.step(.1f);
    assertSame(innermost.scene, manager.current());
    manager.close();
  }

  @Test
  public void invalidProgressFailsWithoutReplacingOldScene() {
    SceneManager manager = new SceneManager();
    TrackingGame old = new TrackingGame();
    manager.request(() -> old);
    manager.step(0);
    ManualJob badProgress = new ManualJob(new TrackingGame());
    badProgress.reportedProgress = Double.NaN;
    LoadHandle handle = manager.requestLoading(badProgress, SceneTransition.none());

    manager.step(.1f);

    assertEquals(LoadState.FAILED, handle.state());
    assertSame(old, manager.current());
    assertFalse(old.isDisposed());
    manager.close();
  }

  @Test
  public void jobReturningCurrentSceneDoesNotDisposeIt() {
    SceneManager manager = new SceneManager();
    TrackingGame old = new TrackingGame();
    manager.request(() -> old);
    manager.step(0);
    ManualJob invalid = new ManualJob(old);
    LoadHandle handle = manager.requestLoading(invalid, SceneTransition.none());

    manager.step(.1f);

    assertEquals(LoadState.FAILED, handle.state());
    assertSame(old, manager.current());
    assertFalse(old.isDisposed());
    manager.close();
  }

  @Test
  public void replacingDuringFadeInDoesNotCloseTransferredJobTwice() {
    SceneManager manager = new SceneManager();
    TrackingGame old = new TrackingGame();
    manager.request(() -> old);
    manager.step(0);
    CountingCloseJob job = new CountingCloseJob(new TrackingGame());
    manager.requestLoading(job, new SceneTransition(0, 1));
    manager.step(.1f);
    assertEquals(1, job.closeCalls.get());

    manager.request(() -> new TrackingGame());
    manager.step(.1f);

    assertEquals(1, job.closeCalls.get());
    manager.close();
  }

  @Test
  public void oldDisposeFailureRemainsPrimaryWhenCompletionCallbackThrows() {
    SceneManager manager = new SceneManager();
    RuntimeException oldDisposeFailure = new RuntimeException("old dispose");
    RuntimeException callbackFailure = new RuntimeException("completion callback");
    TrackingGame old = new TrackingGame();
    old.onDestroyAction =
        () -> {
          throw oldDisposeFailure;
        };
    manager.request(() -> old);
    manager.step(0);
    TrackingGame next = new TrackingGame();
    ManualJob job = new ManualJob(next);
    LoadHandle handle =
        manager
            .requestLoading(job, SceneTransition.none())
            .onComplete(
                () -> {
                  throw callbackFailure;
                });

    RuntimeException thrown = assertThrows(RuntimeException.class, () -> manager.step(.1f));

    assertSame(oldDisposeFailure, thrown);
    assertArrayEquals(new Throwable[] {callbackFailure}, thrown.getSuppressed());
    assertEquals(LoadState.COMPLETED, handle.state());
    assertSame(next, manager.current());
    assertTrue(old.isDisposed());
    assertFalse(next.isDisposed());
    assertEquals(1, job.closeCount.get());
    manager.close();
  }

  @Test
  public void positiveFadeReportsHandoffAndCompletionFailuresInTheirOwnSteps() {
    SceneManager manager = new SceneManager();
    RuntimeException oldDisposeFailure = new RuntimeException("old dispose");
    RuntimeException callbackFailure = new RuntimeException("completion callback");
    AtomicInteger oldDestroyCalls = new AtomicInteger();
    AtomicInteger callbackCalls = new AtomicInteger();
    AtomicInteger nextDestroyCalls = new AtomicInteger();
    TrackingGame old = new TrackingGame();
    old.onDestroyAction =
        () -> {
          oldDestroyCalls.incrementAndGet();
          throw oldDisposeFailure;
        };
    manager.request(() -> old);
    manager.step(0);
    TrackingGame next = new TrackingGame();
    next.onDestroyAction = nextDestroyCalls::incrementAndGet;
    ManualJob job = new ManualJob(next);
    LoadHandle handle =
        manager
            .requestLoading(job, new SceneTransition(0, .2))
            .onComplete(
                () -> {
                  callbackCalls.incrementAndGet();
                  throw callbackFailure;
                });

    RuntimeException handoffFailure = assertThrows(RuntimeException.class, () -> manager.step(.1f));

    assertSame(oldDisposeFailure, handoffFailure);
    assertEquals(LoadState.FADING_IN, handle.state());
    assertSame(next, manager.current());
    assertTrue(old.isDisposed());
    assertFalse(next.isDisposed());
    assertEquals(1, oldDestroyCalls.get());
    assertEquals(1, job.closeCount.get());

    manager.step(.1f);
    assertEquals(LoadState.FADING_IN, handle.state());
    assertEquals(.5, manager.overlayAlpha(), .0001);
    RuntimeException completionFailure =
        assertThrows(RuntimeException.class, () -> manager.step(.1f));

    assertSame(callbackFailure, completionFailure);
    assertEquals(0, completionFailure.getSuppressed().length);
    assertEquals(LoadState.COMPLETED, handle.state());
    assertSame(next, manager.current());
    assertEquals(1, oldDestroyCalls.get());
    assertEquals(1, callbackCalls.get());
    assertEquals(0, nextDestroyCalls.get());
    manager.step(.1f);
    assertEquals(1, callbackCalls.get());
    manager.close();
    assertEquals(1, nextDestroyCalls.get());
    assertTrue(next.isDisposed());
    assertNull(manager.current());
  }

  @Test
  public void loadFailureRemainsPrimaryWithCleanupAndFailureCallbackErrors() {
    SceneManager manager = new SceneManager();
    TrackingGame old = new TrackingGame();
    manager.request(() -> old);
    manager.step(0);
    RuntimeException loadFailure = new RuntimeException("load failed");
    RuntimeException closeFailure = new RuntimeException("job close failed");
    RuntimeException callbackFailure = new RuntimeException("failure callback");
    ManualJob job = new ManualJob(new TrackingGame());
    job.updateFailure = loadFailure;
    job.closeFailure = closeFailure;
    LoadHandle handle =
        manager
            .requestLoading(job, SceneTransition.none())
            .onFailure(
                error -> {
                  throw callbackFailure;
                });

    RuntimeException thrown = assertThrows(RuntimeException.class, () -> manager.step(.1f));

    assertSame(loadFailure, thrown);
    assertSame(loadFailure, handle.error());
    assertArrayEquals(new Throwable[] {closeFailure, callbackFailure}, thrown.getSuppressed());
    assertEquals(LoadState.FAILED, handle.state());
    assertSame(old, manager.current());
    assertFalse(old.isDisposed());
    assertTrue(job.scene.isDisposed());
    assertEquals(1, job.closeCount.get());
    manager.close();
  }

  @Test
  public void failureAggregationSkipsSelfSuppression() {
    SceneManager manager = new SceneManager();
    TrackingGame old = new TrackingGame();
    manager.request(() -> old);
    manager.step(0);
    RuntimeException sameFailure = new RuntimeException("same instance");
    ManualJob job = new ManualJob(new TrackingGame());
    job.updateFailure = sameFailure;
    job.closeFailure = sameFailure;
    LoadHandle handle =
        manager
            .requestLoading(job, SceneTransition.none())
            .onFailure(
                error -> {
                  throw sameFailure;
                });

    RuntimeException thrown = assertThrows(RuntimeException.class, () -> manager.step(.1f));

    assertSame(sameFailure, thrown);
    assertSame(sameFailure, handle.error());
    assertEquals(0, thrown.getSuppressed().length);
    assertEquals(LoadState.FAILED, handle.state());
    assertSame(old, manager.current());
    assertEquals(1, job.closeCount.get());
    manager.close();
  }

  @Test
  public void assetJobLoadsLocalPlainDataAndConfiguresBeforeSceneStart() throws Exception {
    Path file = Files.createTempFile("javity-scene-asset", ".txt");
    Files.writeString(file, "scene asset ready");
    com.badlogic.gdx.assets.loaders.FileHandleResolver resolver = FileHandle::new;
    AssetManager assetManager = new AssetManager(resolver, false);
    Application priorApplication = Gdx.app;
    Gdx.app =
        (Application)
            Proxy.newProxyInstance(
                Application.class.getClassLoader(),
                new Class<?>[] {Application.class},
                (proxy, method, args) ->
                    method.getName().equals("getType")
                        ? Application.ApplicationType.Desktop
                        : null);
    assetManager.setLoader(
        String.class,
        new AsynchronousAssetLoader<String, AssetLoaderParameters<String>>(resolver) {
          private String loadedText;

          @Override
          public void loadAsync(
              AssetManager manager,
              String fileName,
              FileHandle file,
              AssetLoaderParameters<String> params) {
            loadedText = file.readString();
          }

          @Override
          public String loadSync(
              AssetManager manager,
              String fileName,
              FileHandle file,
              AssetLoaderParameters<String> params) {
            return loadedText;
          }

          @Override
          public com.badlogic.gdx.utils.Array<AssetDescriptor> getDependencies(
              String fileName, FileHandle file, AssetLoaderParameters<String> params) {
            return null;
          }
        });
    AtomicInteger configured = new AtomicInteger();
    AssetSceneLoadJob job =
        new AssetSceneLoadJob(
            List.of(new AssetSpec<>(file.toAbsolutePath().toString(), String.class)),
            game -> {
              assertEquals(
                  "scene asset ready",
                  game.getSystem(SceneAssets.class)
                      .get(file.toAbsolutePath().toString(), String.class));
              configured.incrementAndGet();
            },
            1,
            assetManager);
    try {
      double previous = 0;
      for (int i = 0; i < 1000 && !job.isReady(); i++) {
        job.update(.016f);
        assertTrue(job.progress() >= previous);
        previous = job.progress();
      }
      assertTrue("local AssetManager job did not complete", job.isReady());
      Game scene = job.takeScene();
      assertEquals(1, configured.get());
      assertTrue(scene.getSystem(SceneAssets.class).isDisposed() == false);
      scene.dispose();
      job.close();
      assertTrue(scene.getSystem(SceneAssets.class) == null);
    } finally {
      job.close();
      Gdx.app = priorApplication;
      Files.deleteIfExists(file);
    }
  }

  private static class ManualJob implements SceneLoadJob {
    private final Game scene;
    private final AtomicInteger closeCount = new AtomicInteger();
    private boolean ready;
    private boolean transferred;
    private boolean closed;
    private double reportedProgress = 1;
    private Runnable updateAction = () -> {};
    private Runnable progressAction = () -> {};
    private Runnable readyAction = () -> {};
    private int readyCalls;
    private Runnable closeAction = () -> {};
    private RuntimeException updateFailure;
    private RuntimeException closeFailure;

    private ManualJob(Game scene) {
      this.scene = scene;
    }

    @Override
    public void update(float delta) {
      updateAction.run();
      if (updateFailure != null) throw updateFailure;
      ready = true;
    }

    @Override
    public double progress() {
      progressAction.run();
      return ready ? reportedProgress : 0;
    }

    @Override
    public boolean isReady() {
      readyCalls++;
      readyAction.run();
      return ready;
    }

    @Override
    public Game takeScene() {
      if (!ready || transferred) throw new IllegalStateException();
      transferred = true;
      return scene;
    }

    @Override
    public void close() {
      if (closed) return;
      closed = true;
      closeCount.incrementAndGet();
      closeAction.run();
      if (!transferred) scene.dispose();
      if (closeFailure != null) throw closeFailure;
    }
  }

  private static final class CountingCloseJob extends ManualJob {
    private final AtomicInteger closeCalls = new AtomicInteger();

    private CountingCloseJob(Game scene) {
      super(scene);
    }

    @Override
    public void close() {
      closeCalls.incrementAndGet();
      super.close();
    }
  }

  private static final class TrackingGame extends Game {
    private boolean failCreate;
    private Runnable onCreateAction = () -> {};
    private Runnable onDestroyAction = () -> {};

    @Override
    public void onCreate() {
      onCreateAction.run();
      if (failCreate) throw new IllegalStateException("expected start failure");
    }

    @Override
    public void onDestroy() {
      onDestroyAction.run();
    }
  }
}
