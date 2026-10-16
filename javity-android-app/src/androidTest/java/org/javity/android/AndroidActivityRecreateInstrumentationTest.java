package org.javity.android;

import android.app.Activity;
import android.app.Instrumentation;
import android.test.ActivityInstrumentationTestCase2;
import com.badlogic.gdx.backends.android.AndroidApplication;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Separately proves Android Activity recreation creates a fresh, working application fixture. */
@SuppressWarnings("deprecation")
public final class AndroidActivityRecreateInstrumentationTest
    extends ActivityInstrumentationTestCase2<JavityGameActivity> {
  private static final long STARTUP_TIMEOUT_SECONDS = 60;

  public AndroidActivityRecreateInstrumentationTest() {
    super(JavityGameActivity.class);
  }

  public void testActivityRecreateBuildsFreshReadyGameFixture() throws Exception {
    Instrumentation instrumentation = getInstrumentation();
    JavityGameActivity oldActivity = getActivity();
    AndroidGameFixture oldFixture = oldActivity.fixture();
    assertNotNull("initial Activity should own its fixture", oldFixture);
    waitUntilReady(oldFixture, STARTUP_TIMEOUT_SECONDS);
    AndroidGameFixture.FixtureSnapshot before = snapshotOnOwnerThread(oldActivity, oldFixture);
    assertTrue("initial fixture should be ready before recreation", before.ready);
    assertTrue("initial Activity should have rendered frames", before.completedFrames > 0);

    Instrumentation.ActivityMonitor monitor =
        instrumentation.addMonitor(JavityGameActivity.class.getName(), null, false);
    JavityGameActivity recreatedActivity = null;
    try {
      instrumentation.runOnMainSync(oldActivity::recreate);
      Activity observed =
          monitor.waitForActivityWithTimeout(TimeUnit.SECONDS.toMillis(STARTUP_TIMEOUT_SECONDS));
      assertNotNull("Activity.recreate should launch a monitored replacement Activity", observed);
      assertTrue("replacement must be the app Activity", observed instanceof JavityGameActivity);
      recreatedActivity = (JavityGameActivity) observed;
      assertNotSame(
          "Activity.recreate must create a new Activity instance", oldActivity, recreatedActivity);

      AndroidGameFixture recreatedFixture = recreatedActivity.fixture();
      assertNotNull("replacement Activity should own a fixture", recreatedFixture);
      assertNotSame(
          "replacement Activity should construct a fresh fixture", oldFixture, recreatedFixture);
      waitUntilReady(recreatedFixture, STARTUP_TIMEOUT_SECONDS);

      // diagnosticsSnapshot enforces fixture owner-thread access; post through this new app
      // instance
      // so a stale global Gdx.app reference cannot accidentally sample the old GL thread.
      AndroidGameFixture.FixtureSnapshot after =
          snapshotOnOwnerThread(recreatedActivity, recreatedFixture);
      assertTrue("replacement fixture should be ready", after.ready);
      assertTrue("replacement app should complete render frames", after.completedFrames > 0);
      assertTrue("replacement app should reopen its QuickJS runtime", after.runtimeOpen);
      assertEquals(
          "fresh React UI should render the initial score", "Java signal: 0", after.committedScore);
      assertTrue(
          "replacement fixture should own non-empty voxel chunk models",
          after.ownedVoxelChunkModels > 0);
      assertEquals(
          "replacement fixture should attach one sample ModelRenderer",
          1,
          after.modelRendererComponents);
      assertTrue(
          "replacement fixture should have a physical framebuffer",
          after.framebufferWidth > 0 && after.framebufferHeight > 0);
      waitUntilStopped(oldFixture);
    } finally {
      instrumentation.removeMonitor(monitor);
      if (recreatedActivity != null) {
        JavityGameActivity activityToFinish = recreatedActivity;
        instrumentation.runOnMainSync(
            () -> {
              if (!activityToFinish.isFinishing()) activityToFinish.finish();
            });
        instrumentation.waitForIdleSync();
      }
    }
  }

  public void testActivityRecreateAfterAlreadyPausedClosesOldCpuGame() throws Exception {
    Instrumentation instrumentation = getInstrumentation();
    JavityGameActivity oldActivity = getActivity();
    AndroidGameFixture oldFixture = oldActivity.fixture();
    assertNotNull("initial Activity should own its fixture", oldFixture);
    waitUntilReady(oldFixture, STARTUP_TIMEOUT_SECONDS);

    // Deliver the real Activity lifecycle callback while the fixture is running. libGDX waits
    // for its GL-thread pause callback, which releases every fixture GL owner but retains Game.
    instrumentation.runOnMainSync(() -> instrumentation.callActivityOnPause(oldActivity));
    waitUntilLifecycleSuspended(oldFixture, STARTUP_TIMEOUT_SECONDS);
    assertFalse(
        "ordinary Activity pause must retain Game until recreation",
        oldFixture.isDisposedForInstrumentation());
    assertFalse(
        "ordinary Activity pause is not terminal disposal",
        oldFixture.isTerminalDisposalComplete());
    assertFalse(
        "ordinary Activity pause must release its live GL generation", oldFixture.isReady());

    Instrumentation.ActivityMonitor monitor =
        instrumentation.addMonitor(JavityGameActivity.class.getName(), null, false);
    JavityGameActivity recreatedActivity = null;
    try {
      instrumentation.runOnMainSync(oldActivity::recreate);
      Activity observed =
          monitor.waitForActivityWithTimeout(TimeUnit.SECONDS.toMillis(STARTUP_TIMEOUT_SECONDS));
      assertNotNull("paused Activity recreation should launch a replacement", observed);
      assertTrue(observed instanceof JavityGameActivity);
      recreatedActivity = (JavityGameActivity) observed;
      assertNotSame(oldActivity, recreatedActivity);
      AndroidGameFixture newFixture = recreatedActivity.fixture();
      assertNotNull(newFixture);
      assertNotSame("replacement should use an independent fixture", oldFixture, newFixture);
      waitUntilReady(newFixture, STARTUP_TIMEOUT_SECONDS);
      waitUntilTerminal(oldFixture);
      assertFalse("old paused fixture must be stopped", oldFixture.isReady());
    } finally {
      instrumentation.removeMonitor(monitor);
      if (recreatedActivity != null) {
        JavityGameActivity activityToFinish = recreatedActivity;
        instrumentation.runOnMainSync(
            () -> {
              if (!activityToFinish.isFinishing()) activityToFinish.finish();
            });
        instrumentation.waitForIdleSync();
      }
    }
  }

  private void waitUntilReady(AndroidGameFixture fixture, long timeoutSeconds)
      throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
    while (System.nanoTime() < deadline) {
      if (fixture.isReady()
          && fixture.completedFramesForInstrumentation() > 0
          && fixture.renderedVoxelChunks() > 0
          && fixture.framebufferWidth() > 0
          && fixture.framebufferHeight() > 0) return;
      Thread.sleep(30);
    }
    fail("Fixture did not become ready and render within " + timeoutSeconds + " seconds");
  }

  private void waitUntilStopped(AndroidGameFixture fixture) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (!fixture.isTerminalDisposalComplete() && System.nanoTime() < deadline) Thread.sleep(30);
    assertTrue("old fixture must reach terminal disposal", fixture.isTerminalDisposalComplete());
    assertFalse(
        "old fixture must release its resources during Activity recreation", fixture.isReady());
  }

  private void waitUntilTerminal(AndroidGameFixture fixture) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (!fixture.isTerminalDisposalComplete() && System.nanoTime() < deadline) Thread.sleep(30);
    assertTrue(
        "old paused fixture must close Game and retained owners",
        fixture.isTerminalDisposalComplete());
    assertTrue(
        "old paused fixture should be marked disposed", fixture.isDisposedForInstrumentation());
  }

  private void waitUntilLifecycleSuspended(AndroidGameFixture fixture, long timeoutSeconds)
      throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
    while (!fixture.isLifecycleSuspended() && System.nanoTime() < deadline) Thread.sleep(30);
    assertTrue(
        "Activity pause must release the fixture GL generation", fixture.isLifecycleSuspended());
  }

  private AndroidGameFixture.FixtureSnapshot snapshotOnOwnerThread(
      AndroidApplication application, AndroidGameFixture fixture) throws Exception {
    CountDownLatch done = new CountDownLatch(1);
    AtomicReference<AndroidGameFixture.FixtureSnapshot> result = new AtomicReference<>();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    application.postRunnable(
        () -> {
          try {
            result.set(fixture.diagnosticsSnapshot());
          } catch (Throwable thrown) {
            failure.set(thrown);
          } finally {
            done.countDown();
          }
        });
    assertTrue("GDX owner-thread snapshot timed out", done.await(10, TimeUnit.SECONDS));
    if (failure.get() != null)
      throw new AssertionError("Could not sample fixture on its owner thread", failure.get());
    assertNotNull("owner-thread callback should produce a snapshot", result.get());
    return result.get();
  }
}
