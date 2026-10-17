package org.javity.android;

import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.javity.ui.yoga.android.AndroidYogaBackend;

/** Maintained Android entry point for the portable core/voxel/UI/QuickJS fixture. */
public final class JavityGameActivity extends AndroidApplication {
  private AndroidGameFixture fixture;
  private volatile boolean explicitRecreationRequested;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    AndroidYogaBackend.initialize(getApplicationContext());

    AndroidApplicationConfiguration configuration = new AndroidApplicationConfiguration();
    configuration.useGL30 = true;
    configuration.useImmersiveMode = true;
    configuration.depth = 24;
    configuration.numSamples = 0;
    configuration.useAccelerometer = false;
    configuration.useCompass = false;
    configuration.useGyroscope = false;
    configuration.useRotationVectorSensor = false;
    fixture = new AndroidGameFixture();
    initialize(fixture, configuration);
    // Exercise managed-resource recovery on every actual surface pause/resume.
    glSurfaceViewForInstrumentation().setPreserveEGLContextOnPause(false);
  }

  @Override
  protected com.badlogic.gdx.backends.android.AndroidGraphics createGraphics(
      AndroidApplicationConfiguration config) {
    return new JavityAndroidGraphics(
        this,
        config,
        config.resolutionStrategy == null
            ? new com.badlogic.gdx.backends.android.surfaceview.FillResolutionStrategy()
            : config.resolutionStrategy);
  }

  @Override
  public void recreate() {
    AndroidGameFixture current = fixture;
    if (current == null) {
      super.recreate();
      return;
    }
    explicitRecreationRequested = true;
    current.requestTerminalDisposal();
    if (current.isLifecycleSuspended()) {
      // Listener.pause already released this fixture's GL resources. Drain only its retained CPU
      // Game on the original GL thread and acknowledge completion before the old view detaches.
      disposePausedFixtureBeforeDetach(current);
    }
    super.recreate();
  }

  @Override
  protected void onPause() {
    AndroidGameFixture current = fixture;
    if (current != null && (explicitRecreationRequested || isChangingConfigurations()))
      current.requestTerminalDisposal();
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    AndroidGameFixture current = fixture;
    if (isChangingConfigurations()
        && current != null
        && current.isLifecycleSuspended()
        && !current.isTerminalDisposalComplete()) {
      current.requestTerminalDisposal();
      disposePausedFixtureBeforeDetach(current);
    }
    super.onDestroy();
  }

  private void disposePausedFixtureBeforeDetach(AndroidGameFixture current) {
    CountDownLatch complete = new CountDownLatch(1);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    glSurfaceViewForInstrumentation()
        .queueEvent(
            () -> {
              try {
                current.disposeRetainedCpuAfterPause();
              } catch (Throwable thrown) {
                failure.set(thrown);
              } finally {
                complete.countDown();
              }
            });
    try {
      if (!complete.await(10, TimeUnit.SECONDS))
        throw new IllegalStateException("Timed out waiting for paused fixture CPU cleanup");
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Interrupted waiting for paused fixture CPU cleanup", interrupted);
    }
    Throwable cleanupFailure = failure.get();
    if (cleanupFailure != null)
      throw new IllegalStateException("Paused fixture CPU cleanup failed", cleanupFailure);
    if (!current.isTerminalDisposalComplete())
      throw new IllegalStateException("Paused fixture did not publish terminal disposal");
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    AndroidGameFixture current = fixture;
    if (current != null) {
      if (com.badlogic.gdx.Gdx.app != null)
        com.badlogic.gdx.Gdx.app.postRunnable(() -> current.setWindowFocused(hasFocus));
      else current.setWindowFocused(hasFocus);
    }
  }

  /** Read-only diagnostics used by Android instrumentation and the root integration gate. */
  public AndroidGameFixture fixture() {
    return fixture;
  }

  /** Returns the actual GL SurfaceView origin in display coordinates, including insets/layout. */
  public int[] glSurfaceBoundsOnScreen() {
    SurfaceView surface = findSurfaceView(getWindow().getDecorView());
    if (surface == null) throw new IllegalStateException("libGDX SurfaceView is not attached");
    int[] location = new int[2];
    surface.getLocationOnScreen(location);
    return new int[] {location[0], location[1], surface.getWidth(), surface.getHeight()};
  }

  public GLSurfaceView glSurfaceViewForInstrumentation() {
    SurfaceView found = findSurfaceView(getWindow().getDecorView());
    if (!(found instanceof GLSurfaceView surface))
      throw new IllegalStateException("libGDX GLSurfaceView is not attached");
    return surface;
  }

  private static SurfaceView findSurfaceView(View view) {
    if (view instanceof SurfaceView surface) return surface;
    if (!(view instanceof ViewGroup group)) return null;
    for (int i = 0; i < group.getChildCount(); i++) {
      SurfaceView found = findSurfaceView(group.getChildAt(i));
      if (found != null) return found;
    }
    return null;
  }
}
