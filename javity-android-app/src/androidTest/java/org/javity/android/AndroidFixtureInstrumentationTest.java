package org.javity.android;

import android.content.pm.ActivityInfo;
import android.os.SystemClock;
import android.test.ActivityInstrumentationTestCase2;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector2;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** App-level runtime/render/input assertions for density-160 and density-320 API35 runs. */
@SuppressWarnings("deprecation")
public final class AndroidFixtureInstrumentationTest
    extends ActivityInstrumentationTestCase2<JavityGameActivity> {
  public AndroidFixtureInstrumentationTest() {
    super(JavityGameActivity.class);
  }

  public void testPackagedRuntimeReactTouchAndResourceLifecycle() throws Exception {
    JavityGameActivity activity = getActivity();
    AndroidGameFixture fixture = activity.fixture();
    assertNotNull(fixture);
    waitUntilReady(fixture);

    AndroidGameFixture.FixtureSnapshot before = snapshot(fixture);
    assertTrue("fixture should be ready", before.ready);
    assertTrue("React score text should commit", before.committedScore.contains("Java signal: 0"));
    assertTrue("QuickJS host should remain open", before.runtimeOpen);
    assertTrue("Expected owned non-empty chunk models", before.ownedVoxelChunkModels > 0);
    assertEquals(
        "Fixture should own one attached sample ModelRenderer", 1, before.modelRendererComponents);
    assertTrue(
        "Expected a physical framebuffer",
        before.framebufferWidth > 0 && before.framebufferHeight > 0);
    AtomicReference<AndroidGameFixture.ModelPixelProof> modelProof = new AtomicReference<>();
    runOnOwnerThread(() -> modelProof.set(fixture.initialModelPixelProofForInstrumentation()));
    AndroidGameFixture.ModelPixelProof model = modelProof.get();
    assertTrue(
        "Actual world-only sample box must produce positive cyan pixels before input",
        model.cyanPixelsWithModel >= model.width * model.height / 2);
    assertEquals(
        "Disabling only the sample renderer must remove those world-only pixels",
        0,
        model.cyanPixelsWithoutModel);
    Gdx.app.log(
        "JavityModelPixelProof",
        "roi="
            + model.x
            + ","
            + model.y
            + ","
            + model.width
            + ","
            + model.height
            + " on="
            + model.cyanPixelsWithModel
            + " off="
            + model.cyanPixelsWithoutModel);

    assertGlesRegion(before.roundedSdfRegion, before, "rounded material");
    assertGlesRegion(before.circleSdfRegion, before, "anti-aliased circle");
    assertGlesRegion(before.dashedSdfRegion, before, "dashed line");

    int[] surface = surfaceBounds(activity);
    float[] hudBounds = actorBounds(fixture, "android-hud");
    float[] controlsBounds = actorBounds(fixture, "touch-movement-controls");
    assertGlesRegionBetweenActors(
        before.roundedSdfRegion, before, surface, hudBounds, controlsBounds, "rounded material");
    assertGlesRegionBetweenActors(
        before.circleSdfRegion, before, surface, hudBounds, controlsBounds, "anti-aliased circle");
    assertGlesRegionBetweenActors(
        before.dashedSdfRegion, before, surface, hudBounds, controlsBounds, "dashed line");
    float[] button = actorBounds(fixture, "android-increment");
    Vector2 center =
        new Vector2(
            (button[0] + button[2]) * .5f + surface[0], (button[1] + button[3]) * .5f + surface[1]);

    // Keep the gesture in the HUD's Stage-owned pointer stream, then release vertically below
    // the measured button and beyond ClickListener's tap square. Never fall back across actors.
    float outsideMarginPhysical = 16f / before.unitsPerPixel;
    float outsideY = button[3] + outsideMarginPhysical;
    assertTrue(
        "HUD button down must lie inside the GL surface",
        center.x - surface[0] >= 0
            && center.x - surface[0] < surface[2]
            && center.y - surface[1] >= 0
            && center.y - surface[1] < surface[3]);
    assertTrue(
        "Vertical HUD drag release must remain inside the GL surface",
        center.x - surface[0] >= 0
            && center.x - surface[0] < surface[2]
            && outsideY >= 0
            && outsideY < surface[3]);
    assertTrue(
        "HUD drag release must exceed the measured button and tap square",
        (outsideY - button[3]) * before.unitsPerPixel > 14f);
    float outsideDisplayY = outsideY + surface[1];
    AndroidGameFixture.FixtureSnapshot hudStart = snapshot(fixture);
    sendTouch(center.x, center.y, center.x, outsideDisplayY);
    AndroidGameFixture.FixtureSnapshot afterHudDrag = awaitInputAndFrame(fixture, hudStart, 3);
    assertEquals(
        "Measured HUD drag must not activate Increment",
        "Java signal: 0",
        afterHudDrag.committedScore);
    assertEquals(
        "HUD drag must not leak to the look actor", hudStart.lookEvents, afterHudDrag.lookEvents);

    // The real click is the independent oracle: packaged React -> Java action -> Signal -> commit.
    AndroidGameFixture.FixtureSnapshot tapStart = snapshot(fixture);
    tap(center.x, center.y);
    awaitInputAndFrame(fixture, tapStart, 2);
    AndroidGameFixture.FixtureSnapshot afterTap = waitForScore(fixture, "Java signal: 1");
    assertEquals(
        "Exactly one tap commits exactly one Java action",
        "Java signal: 1",
        afterTap.committedScore);

    AndroidGameFixture.FixtureSnapshot density = snapshot(fixture);
    assertTrue("Density scale must be positive", density.density > 0f);
    assertEquals(1f / density.density, density.unitsPerPixel, .0001f);
    assertTrue(
        "Logical viewport should be available",
        density.logicalWidth > 0 && density.logicalHeight > 0);

    // Seed actual held movement, jump and look with measured controls before focus cancellation.
    float[] forward = actorBounds(fixture, "move-" + Input.Keys.W);
    float[] jump = actorBounds(fixture, "jump-control");
    float[] look = actorBounds(fixture, "right-side-look-input");
    float[] p0 = displayCenter(forward, surface);
    float[] p1 = displayCenter(jump, surface);
    float[] p2 = displayCenter(look, surface);
    long downTime = SystemClock.uptimeMillis();

    AndroidGameFixture.FixtureSnapshot inputStart = snapshot(fixture);
    sendPointerEvent(downTime, MotionEvent.ACTION_DOWN, new float[][] {p0});
    AndroidGameFixture.FixtureSnapshot oneFinger = awaitInputAndFrame(fixture, inputStart, 1);
    assertTrue("Measured W touch should hold movement", oneFinger.moving);

    AndroidGameFixture.FixtureSnapshot jumpStart = oneFinger;
    sendPointerEvent(
        downTime,
        MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
        new float[][] {p0, p1});
    AndroidGameFixture.FixtureSnapshot twoFingers = awaitInputAndFrame(fixture, jumpStart, 1);
    assertTrue("Measured jump touch should hold jump", twoFingers.jumping);

    AndroidGameFixture.FixtureSnapshot lookStart = twoFingers;
    sendPointerEvent(
        downTime,
        MotionEvent.ACTION_POINTER_DOWN | (2 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
        new float[][] {p0, p1, p2});
    AndroidGameFixture.FixtureSnapshot threeFingers = awaitInputAndFrame(fixture, lookStart, 1);
    assertTrue("Third touch in right look region should claim look", threeFingers.lookPointerOwned);

    float[] movedLook = {p2[0] + 32f, p2[1]};
    AndroidGameFixture.FixtureSnapshot dragStart = threeFingers;
    sendPointerEvent(downTime, MotionEvent.ACTION_MOVE, new float[][] {p0, p1, movedLook});
    AndroidGameFixture.FixtureSnapshot activeLook = awaitInputAndFrame(fixture, dragStart, 1);
    assertTrue(
        "Owned look pointer should change camera look",
        activeLook.lookEvents > threeFingers.lookEvents);

    runOnOwnerThread(() -> fixture.setWindowFocused(false));
    AndroidGameFixture.FixtureSnapshot unfocused = snapshot(fixture);
    assertFalse("movement must clear on focus loss", unfocused.moving);
    assertFalse("jump must clear on focus loss", unfocused.jumping);
    assertFalse("look pointer must clear on focus loss", unfocused.lookPointerOwned);
    runOnOwnerThread(() -> fixture.setWindowFocused(true));

    // Release the injected Android pointers after the fixture has cancelled Scene2D touch focus.
    AndroidGameFixture.FixtureSnapshot initialReleaseStart = snapshot(fixture);
    sendPointerEvent(
        downTime,
        MotionEvent.ACTION_POINTER_UP | (2 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
        new float[][] {p0, p1, movedLook});
    sendPointerEvent(
        downTime,
        MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
        new float[][] {p0, p1});
    sendPointerEvent(downTime, MotionEvent.ACTION_UP, new float[][] {p0});
    awaitInputAndFrame(fixture, initialReleaseStart, 3);

    // Reverse the input order with actual MotionEvents: claim look first, then press movement.
    long reverseDownTime = SystemClock.uptimeMillis();
    AndroidGameFixture.FixtureSnapshot reverseStart = snapshot(fixture);
    sendPointerEvent(reverseDownTime, MotionEvent.ACTION_DOWN, new float[][] {p2});
    AndroidGameFixture.FixtureSnapshot lookFirst = awaitInputAndFrame(fixture, reverseStart, 1);
    assertTrue("Look-first touch should claim look", lookFirst.lookPointerOwned);
    sendPointerEvent(
        reverseDownTime,
        MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
        new float[][] {p2, p0});
    AndroidGameFixture.FixtureSnapshot movementSecond = awaitInputAndFrame(fixture, lookFirst, 1);
    assertTrue("Movement-second touch should hold W", movementSecond.moving);
    assertTrue(
        "Movement-second touch must preserve the look claim", movementSecond.lookPointerOwned);
    float[] reverseMovedLook = {p2[0] + 32f, p2[1]};
    sendPointerEvent(
        reverseDownTime, MotionEvent.ACTION_MOVE, new float[][] {reverseMovedLook, p0});
    AndroidGameFixture.FixtureSnapshot reverseLook = awaitInputAndFrame(fixture, movementSecond, 1);
    assertTrue(
        "Look-first owner should keep receiving drag with movement held",
        reverseLook.lookEvents > movementSecond.lookEvents && reverseLook.moving);
    runOnOwnerThread(() -> fixture.setWindowFocused(false));
    AndroidGameFixture.FixtureSnapshot reverseUnfocused = snapshot(fixture);
    assertFalse("Reverse-order movement must clear on focus loss", reverseUnfocused.moving);
    assertFalse(
        "Reverse-order look claim must clear on focus loss", reverseUnfocused.lookPointerOwned);
    runOnOwnerThread(() -> fixture.setWindowFocused(true));
    AndroidGameFixture.FixtureSnapshot reverseReleaseStart = snapshot(fixture);
    sendPointerEvent(
        reverseDownTime,
        MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
        new float[][] {reverseMovedLook, p0});
    sendPointerEvent(reverseDownTime, MotionEvent.ACTION_UP, new float[][] {reverseMovedLook});
    awaitInputAndFrame(fixture, reverseReleaseStart, 2);

    long previousGeneration = snapshot(fixture).resourceGeneration;
    // Full resource recreation includes cold shader compilation on the TCG emulator.
    runOnOwnerThread(fixture::recreateResourcesForInstrumentation, 60);
    waitUntilReady(fixture);
    AndroidGameFixture.FixtureSnapshot recreated = snapshot(fixture);
    assertTrue(
        "Render/UI resources should be rebuilt", recreated.resourceGeneration > previousGeneration);
    assertEquals(
        "Resource rebuild must not attach duplicate ModelRenderers",
        1,
        recreated.modelRendererComponents);
    assertTrue("QuickJS runtime should be recreated", recreated.runtimeOpen);
    assertEquals(
        "Java signal should survive the resource recreation",
        "Java signal: 1",
        recreated.committedScore);
  }

  public void testShortViewportUsesMeasuredCompactLayoutAndRestoresPortraitControls()
      throws Exception {
    JavityGameActivity activity = getActivity();
    AndroidGameFixture fixture = activity.fixture();
    assertNotNull(fixture);
    waitUntilReady(fixture);

    int originalOrientation = activity.getRequestedOrientation();
    AndroidGameFixture.FixtureSnapshot portrait = snapshot(fixture);
    float[] portraitMove = actorBounds(fixture, "move-" + Input.Keys.W);
    assertTrue("initial layout should be portrait-sized", portrait.logicalHeight >= 360);
    assertEquals(
        "portrait movement cell remains 54 logical units",
        54f,
        (portraitMove[3] - portraitMove[1]) * portrait.unitsPerPixel,
        .5f);
    try {
      getInstrumentation()
          .runOnMainSync(
              () -> activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
      AndroidGameFixture.FixtureSnapshot compact =
          waitForLogicalHeightBelow(fixture, activity, 360, portrait.completedFrames);
      assertSame(
          "manifest configChanges must keep the same Activity during resize",
          activity,
          getActivity());
      assertSame(
          "orientation resize must retain the existing fixture", fixture, activity.fixture());
      assertTrue(
          "compact React score actor remains present",
          actorBounds(fixture, "android-score-value").length == 4);
      assertTrue(
          "compact React Java action actor remains present",
          actorBounds(fixture, "android-increment").length == 4);

      float[] hud = actorBounds(fixture, "android-hud");
      float[] controls = actorBounds(fixture, "touch-movement-controls");
      float freeLogicalGap = (controls[1] - hud[3]) * compact.unitsPerPixel;
      assertTrue(
          "measured compact HUD and controls must leave an SDF strip: " + freeLogicalGap,
          freeLogicalGap >= 8f);
      float[] compactMove = actorBounds(fixture, "move-" + Input.Keys.W);
      assertTrue(
          "compact native touch target stays at least about 60 physical pixels",
          compactMove[3] - compactMove[1] >= 60f);
      assertGlesRegion(compact.roundedSdfRegion, compact, "compact rounded material");
      assertGlesRegion(compact.circleSdfRegion, compact, "compact circle");
      assertGlesRegion(compact.dashedSdfRegion, compact, "compact dashed line");
      int[] surface = surfaceBounds(activity);
      float[] hudBounds = actorBounds(fixture, "android-hud");
      float[] controlsBounds = actorBounds(fixture, "touch-movement-controls");
      assertGlesRegionBetweenActors(
          compact.roundedSdfRegion,
          compact,
          surface,
          hudBounds,
          controlsBounds,
          "compact rounded material");
      assertGlesRegionBetweenActors(
          compact.circleSdfRegion, compact, surface, hudBounds, controlsBounds, "compact circle");
      assertGlesRegionBetweenActors(
          compact.dashedSdfRegion,
          compact,
          surface,
          hudBounds,
          controlsBounds,
          "compact dashed line");
    } finally {
      getInstrumentation()
          .runOnMainSync(() -> activity.setRequestedOrientation(originalOrientation));
    }

    AndroidGameFixture.FixtureSnapshot restored =
        waitForLogicalHeightAtLeast(fixture, activity, 360, compactFrameCount(fixture));
    assertSame("portrait restoration keeps Activity alive", activity, getActivity());
    assertSame("portrait restoration keeps fixture alive", fixture, activity.fixture());
    float[] restoredMove = actorBounds(fixture, "move-" + Input.Keys.W);
    assertEquals(
        "restored portrait movement cell returns to 54 logical units",
        54f,
        (restoredMove[3] - restoredMove[1]) * restored.unitsPerPixel,
        .5f);
    assertTrue(
        "fixture remains rendered after orientation restoration", restored.completedFrames > 0);
  }

  private AndroidGameFixture.FixtureSnapshot waitForLogicalHeightBelow(
      AndroidGameFixture fixture, JavityGameActivity activity, int maximum, long previousFrames)
      throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
    while (System.nanoTime() < deadline) {
      AndroidGameFixture.FixtureSnapshot current = snapshot(fixture);
      int[] surface = surfaceBounds(activity);
      if (current.logicalHeight > 0
          && current.logicalHeight < maximum
          && current.completedFrames > previousFrames
          && current.framebufferWidth == surface[2]
          && current.framebufferHeight == surface[3]
          && Math.abs(current.logicalHeight - surface[3] * current.unitsPerPixel) <= 1f)
        return current;
      Thread.sleep(30);
    }
    fail("Activity did not render the resized short viewport");
    return snapshot(fixture);
  }

  private AndroidGameFixture.FixtureSnapshot waitForLogicalHeightAtLeast(
      AndroidGameFixture fixture, JavityGameActivity activity, int minimum, long previousFrames)
      throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
    while (System.nanoTime() < deadline) {
      AndroidGameFixture.FixtureSnapshot current = snapshot(fixture);
      int[] surface = surfaceBounds(activity);
      if (current.logicalHeight >= minimum
          && current.completedFrames > previousFrames
          && current.framebufferWidth == surface[2]
          && current.framebufferHeight == surface[3]
          && Math.abs(current.logicalHeight - surface[3] * current.unitsPerPixel) <= 1f)
        return current;
      Thread.sleep(30);
    }
    fail("Activity did not render the restored tall viewport");
    return snapshot(fixture);
  }

  private long compactFrameCount(AndroidGameFixture fixture) throws Exception {
    return snapshot(fixture).completedFrames;
  }

  private void runOnOwnerThread(Runnable action) throws Exception {
    runOnOwnerThread(action, 10);
  }

  private void runOnOwnerThread(Runnable action, long timeoutSeconds) throws Exception {
    CountDownLatch done = new CountDownLatch(1);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Gdx.app.postRunnable(
        () -> {
          try {
            action.run();
          } catch (Throwable thrown) {
            failure.set(thrown);
          } finally {
            done.countDown();
          }
        });
    assertTrue(
        "GDX owner-thread operation timed out", done.await(timeoutSeconds, TimeUnit.SECONDS));
    Throwable thrown = failure.get();
    if (thrown != null) throw new AssertionError("GDX owner-thread operation failed", thrown);
  }

  private AndroidGameFixture.FixtureSnapshot snapshot(AndroidGameFixture fixture) throws Exception {
    AtomicReference<AndroidGameFixture.FixtureSnapshot> result = new AtomicReference<>();
    runOnOwnerThread(() -> result.set(fixture.diagnosticsSnapshot()));
    return result.get();
  }

  private float[] actorBounds(AndroidGameFixture fixture, String name) throws Exception {
    AtomicReference<float[]> result = new AtomicReference<>();
    runOnOwnerThread(() -> result.set(fixture.actorSurfaceBounds(name)));
    return result.get();
  }

  private int[] surfaceBounds(JavityGameActivity activity) throws Exception {
    int[][] result = new int[1][];
    getInstrumentation().runOnMainSync(() -> result[0] = activity.glSurfaceBoundsOnScreen());
    return result[0];
  }

  private static float[] displayCenter(float[] bounds, int[] surface) {
    return new float[] {
      (bounds[0] + bounds[2]) * .5f + surface[0], (bounds[1] + bounds[3]) * .5f + surface[1]
    };
  }

  private void assertGlesRegion(
      AndroidGameFixture.GlesRegion region,
      AndroidGameFixture.FixtureSnapshot snapshot,
      String label) {
    assertNotNull(label + " region should be exposed for pixel checks", region);
    assertTrue(
        label + " should be within physical framebuffer",
        region.x >= 0
            && region.y >= 0
            && region.x + region.width <= snapshot.framebufferWidth
            && region.y + region.height <= snapshot.framebufferHeight);
  }

  private void assertGlesRegionBetweenActors(
      AndroidGameFixture.GlesRegion region,
      AndroidGameFixture.FixtureSnapshot snapshot,
      int[] surface,
      float[] hud,
      float[] controls,
      String label) {
    float pixelsY = (float) snapshot.framebufferHeight / surface[3];
    float clearBottom = snapshot.framebufferHeight - controls[1] * pixelsY;
    float clearTop = snapshot.framebufferHeight - hud[3] * pixelsY;
    assertTrue(
        label + " must be below measured opaque HUD and above measured controls",
        region.y >= clearBottom && region.y + region.height <= clearTop);
  }

  private void tap(float x, float y) {
    long downTime = SystemClock.uptimeMillis();
    MotionEvent down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0);
    MotionEvent up = MotionEvent.obtain(downTime, downTime + 80, MotionEvent.ACTION_UP, x, y, 0);
    try {
      injectPointerEvent(down);
      injectPointerEvent(up);
    } finally {
      down.recycle();
      up.recycle();
    }
  }

  private void sendTouch(float x, float y, float moveX, float moveY) {
    long downTime = SystemClock.uptimeMillis();
    MotionEvent down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0);
    MotionEvent move =
        MotionEvent.obtain(downTime, downTime + 40, MotionEvent.ACTION_MOVE, moveX, moveY, 0);
    MotionEvent up =
        MotionEvent.obtain(downTime, downTime + 80, MotionEvent.ACTION_UP, moveX, moveY, 0);
    try {
      injectPointerEvent(down);
      injectPointerEvent(move);
      injectPointerEvent(up);
    } finally {
      down.recycle();
      move.recycle();
      up.recycle();
    }
  }

  private void sendPointerEvent(long downTime, int action, float[][] points) {
    MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[points.length];
    MotionEvent.PointerCoords[] coordinates = new MotionEvent.PointerCoords[points.length];
    for (int i = 0; i < points.length; i++) {
      properties[i] = new MotionEvent.PointerProperties();
      properties[i].id = i;
      properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
      coordinates[i] = new MotionEvent.PointerCoords();
      coordinates[i].x = points[i][0];
      coordinates[i].y = points[i][1];
      coordinates[i].pressure = 1f;
      coordinates[i].size = 1f;
    }
    MotionEvent event =
        MotionEvent.obtain(
            downTime,
            SystemClock.uptimeMillis(),
            action,
            points.length,
            properties,
            coordinates,
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            0);
    try {
      injectPointerEvent(event);
    } finally {
      event.recycle();
    }
  }

  private void injectPointerEvent(MotionEvent event) {
    event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
    JavityGameActivity activity = getActivity();
    int[][] window = new int[1][];
    boolean[] focused = new boolean[1];
    boolean[] visible = new boolean[1];
    boolean[] finishing = new boolean[1];
    getInstrumentation()
        .runOnMainSync(
            () -> {
              View decor = activity.getWindow().getDecorView();
              int[] origin = new int[2];
              decor.getLocationOnScreen(origin);
              window[0] =
                  new int[] {origin[0], origin[1], decor.getWidth(), decor.getHeight()};
              focused[0] = activity.hasWindowFocus() && decor.hasWindowFocus();
              visible[0] = decor.isAttachedToWindow() && decor.isShown();
              finishing[0] = activity.isFinishing() || activity.isDestroyed();
            });
    int[] bounds = window[0];
    assertNotNull("Activity window bounds should be available before touch injection", bounds);
    assertTrue(
        "Activity window must own focus before injecting touch; focused="
            + focused[0]
            + " visible="
            + visible[0]
            + " finishingOrDestroyed="
            + finishing[0]
            + " bounds="
            + java.util.Arrays.toString(bounds),
        focused[0] && visible[0] && !finishing[0]);
    for (int pointer = 0; pointer < event.getPointerCount(); pointer++) {
      float x = event.getX(pointer);
      float y = event.getY(pointer);
      assertTrue(
          "Touch pointer "
              + pointer
              + " at "
              + x
              + ","
              + y
              + " must be inside the focused Activity window "
              + java.util.Arrays.toString(bounds),
          x >= bounds[0]
              && x < bounds[0] + bounds[2]
              && y >= bounds[1]
              && y < bounds[1] + bounds[3]);
    }
    boolean injected = getInstrumentation().getUiAutomation().injectInputEvent(event, true);
    assertTrue("UiAutomation must inject the touchscreen event into the focused app", injected);
  }

  private AndroidGameFixture.FixtureSnapshot awaitInputAndFrame(
      AndroidGameFixture fixture,
      AndroidGameFixture.FixtureSnapshot before,
      int expectedAdditionalEvents)
      throws Exception {
    long target = before.inputEventCount + expectedAdditionalEvents;
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline) {
      AndroidGameFixture.FixtureSnapshot current = snapshot(fixture);
      if (current.inputEventCount >= target && current.completedFrames > before.completedFrames)
        return current;
      Thread.sleep(30);
    }
    fail("Injected touch events were not processed in a completed render cycle");
    return snapshot(fixture);
  }

  private static void waitUntilReady(AndroidGameFixture fixture) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
    while ((!fixture.isReady()
            || fixture.renderedVoxelChunks() == 0
            || fixture.framebufferWidth() == 0)
        && System.nanoTime() < deadline) Thread.sleep(30);
    if (!fixture.isReady() || fixture.renderedVoxelChunks() == 0 || fixture.framebufferWidth() == 0)
      throw new AssertionError("fixture did not render within the bounded startup window");
  }

  private AndroidGameFixture.FixtureSnapshot waitForScore(
      AndroidGameFixture fixture, String expected) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline) {
      AndroidGameFixture.FixtureSnapshot current = snapshot(fixture);
      if (expected.equals(current.committedScore)) return current;
      Thread.sleep(30);
    }
    fail(
        "Expected committed score "
            + expected
            + " but observed "
            + snapshot(fixture).committedScore);
    return snapshot(fixture);
  }
}
