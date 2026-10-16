package org.javity.android;

import android.opengl.GLSurfaceView;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;
import com.badlogic.gdx.Gdx;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Isolated revised test source: keep all engine owners alive across an actual GLSurfaceView EGL
 * loss.
 */
@SuppressWarnings("deprecation")
public final class AndroidManagedContextLossInstrumentationTest
    extends ActivityInstrumentationTestCase2<JavityGameActivity> {
  private static final String TAG = "ManagedContextTest";
  private static final long RESUME_FRAME_TIMEOUT_SECONDS = 60;
  private static final long GL_CALLBACK_TIMEOUT_SECONDS = 30;

  public AndroidManagedContextLossInstrumentationTest() {
    super(JavityGameActivity.class);
  }

  public void testManagedOwnersRecoverAfterActualSurfaceContextLoss() throws Exception {
    JavityGameActivity activity = getActivity();
    AndroidGameFixture fixture = activity.fixture();
    assertNotNull(fixture);
    waitUntilReady(fixture);

    AndroidManagedContextProbe.Snapshot before = probeSnapshot(fixture);
    Log.i(TAG, "pre-loss pixels and owners: " + before);
    AndroidGameFixture.FixtureSnapshot fixtureBefore = fixtureSnapshot(fixture);
    assertTrue("probe TextureData should have its initial upload", before.managedUploadCount() > 0);
    assertHealthyPixels(before, "before context loss");
    assertWorldModel(fixture, "before context loss");

    GLSurfaceView surface = surfaceView(activity);
    assertNotNull(surface);
    assertFalse("test requires EGL preservation disabled", surface.getPreserveEGLContextOnPause());

    long frameBeforePause = fixture.completedFramesForInstrumentation();
    logPhase("pausing retained GLSurfaceView", frameBeforePause);
    getInstrumentation().runOnMainSync(surface::onPause);
    long pausedFrame = fixture.completedFramesForInstrumentation();
    logPhase("resuming retained GLSurfaceView", pausedFrame);
    getInstrumentation().runOnMainSync(surface::onResume);

    // Read the fixture's volatile render heartbeat from the test thread. This distinguishes a
    // resumed GL render loop from a GL-thread diagnostic callback that is merely still queued.
    waitForCompletedFrame(fixture, pausedFrame);
    logPhase("first post-resume render observed", fixture.completedFramesForInstrumentation());

    // One GL-thread diagnostic after render resumes; avoid flooding a stalled GL runnable queue.
    AndroidManagedContextProbe.Snapshot after = probeSnapshot(fixture);
    Log.i(TAG, "post-loss pixels and owners: " + after);
    AndroidGameFixture.FixtureSnapshot fixtureAfter = fixtureSnapshot(fixture);

    assertSame("same Activity owns the retained fixture", fixture, activity.fixture());
    assertSame("same GLSurfaceView was resumed", surface, surfaceView(activity));
    assertEquals(
        "ordinary fixture resources were not rebuilt",
        fixtureBefore.resourceGeneration,
        fixtureAfter.resourceGeneration);
    assertEquals("probe helper was retained", before.helperIdentity(), after.helperIdentity());
    assertEquals(
        "SmoothShapeRenderer was retained",
        before.shapeRendererIdentity(),
        after.shapeRendererIdentity());
    assertEquals("UiTheme was retained", before.themeIdentity(), after.themeIdentity());
    assertEquals(
        "managed control Texture was retained",
        before.controlTextureIdentity(),
        after.controlTextureIdentity());
    assertTrue(
        "libGDX must consume the retained managed Pixmap after EGL loss",
        after.managedUploadCount() > before.managedUploadCount());
    assertTrue(
        "ordinary fixture rendering resumed",
        fixtureAfter.completedFrames > fixtureBefore.completedFrames);
    assertHealthyPixels(after, "after context loss");
    assertWorldModel(fixture, "after context loss");
    // Diagnostic only: EGL native handles may be reused after context destruction (ABA).
    assertTrue("snapshot exposes the current EGL handle", after.eglContextNativeHandle() != 0);
  }

  private void assertWorldModel(AndroidGameFixture fixture, String phase) throws Exception {
    AndroidGameFixture.ModelPixelProof proof =
        onGdxThread(fixture::initialModelPixelProofForInstrumentation);
    Log.i(
        TAG,
        phase
            + " model ROI="
            + proof.x
            + ","
            + proof.y
            + " enabled="
            + proof.cyanPixelsWithModel
            + " disabled="
            + proof.cyanPixelsWithoutModel);
    assertTrue("world model should render " + phase, proof.cyanPixelsWithModel >= 32);
    assertEquals("world-only negative control " + phase, 0, proof.cyanPixelsWithoutModel);
  }

  private void assertHealthyPixels(AndroidManagedContextProbe.Snapshot snapshot, String phase) {
    assertObserved(
        "material SDF " + phase,
        snapshot.expectedMaterialBounds(),
        snapshot.observedMaterialBounds());
    assertObserved(
        "dashed SDF " + phase, snapshot.expectedDashedBounds(), snapshot.observedDashedBounds());
    assertObserved(
        "rounded UI " + phase, snapshot.expectedRoundedBounds(), snapshot.observedRoundedBounds());
    assertTrue("font glyphs should render " + phase, snapshot.nonEmptyGlyphPixels() > 0);
  }

  private void assertObserved(
      String label,
      AndroidManagedContextProbe.Bounds expected,
      AndroidManagedContextProbe.Bounds observed) {
    assertTrue(
        label + " should contain enough colored pixels", observed.pixels() >= expected.pixels());
    assertTrue(
        label + " ink should stay inside its expected enclosing region",
        observed.minX() >= expected.minX()
            && observed.minY() >= expected.minY()
            && observed.maxX() <= expected.maxX()
            && observed.maxY() <= expected.maxY());
  }

  private void waitForCompletedFrame(AndroidGameFixture fixture, long previous) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(RESUME_FRAME_TIMEOUT_SECONDS);
    while (System.nanoTime() < deadline) {
      if (fixture.completedFramesForInstrumentation() > previous) return;
      Thread.sleep(100);
    }
    fail(
        "GLSurfaceView resumed but no ordinary fixture render completed within "
            + RESUME_FRAME_TIMEOUT_SECONDS
            + " seconds");
  }

  private void waitUntilReady(AndroidGameFixture fixture) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
    while (System.nanoTime() < deadline) {
      if (fixture.isReady()) {
        AndroidGameFixture.FixtureSnapshot current = fixtureSnapshot(fixture);
        if (current.ready && current.completedFrames > 0 && current.framebufferWidth > 0) return;
      }
      Thread.sleep(50);
    }
    fail("Android fixture did not become ready before context-loss test");
  }

  private void logPhase(String phase, long frame) {
    Log.i(TAG, phase + " at " + System.nanoTime() + " ns; completedFrames=" + frame);
  }

  private GLSurfaceView surfaceView(JavityGameActivity activity) throws Exception {
    AtomicReference<GLSurfaceView> result = new AtomicReference<>();
    getInstrumentation()
        .runOnMainSync(() -> result.set(activity.glSurfaceViewForInstrumentation()));
    return result.get();
  }

  private AndroidManagedContextProbe.Snapshot probeSnapshot(AndroidGameFixture fixture)
      throws Exception {
    return onGdxThread(fixture::contextProbeSnapshotForInstrumentation);
  }

  private AndroidGameFixture.FixtureSnapshot fixtureSnapshot(AndroidGameFixture fixture)
      throws Exception {
    return onGdxThread(fixture::diagnosticsSnapshot);
  }

  private <T> T onGdxThread(ThrowingSupplier<T> operation) throws Exception {
    CountDownLatch done = new CountDownLatch(1);
    AtomicReference<T> result = new AtomicReference<>();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Gdx.app.postRunnable(
        () -> {
          try {
            result.set(operation.get());
          } catch (Throwable thrown) {
            failure.set(thrown);
          } finally {
            done.countDown();
          }
        });
    assertTrue(
        "GL-thread diagnostic runnable was not drained within "
            + GL_CALLBACK_TIMEOUT_SECONDS
            + " seconds",
        done.await(GL_CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS));
    if (failure.get() != null)
      throw new AssertionError("GL-thread diagnostic failed", failure.get());
    return result.get();
  }

  @FunctionalInterface
  private interface ThrowingSupplier<T> {
    T get() throws Exception;
  }
}
