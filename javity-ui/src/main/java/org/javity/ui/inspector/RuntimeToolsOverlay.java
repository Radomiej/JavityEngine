package org.javity.ui.inspector;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Stage;
import java.util.Objects;
import java.util.function.Supplier;
import org.javity.core.Game;
import org.javity.core.Transform3D;
import org.javity.ui.flex.UiTheme;

/** Optional scene tools. Borrows Stage/theme/Game/cameras; host owns processor installation. */
public final class RuntimeToolsOverlay implements AutoCloseable {
  private final RuntimeInspector inspector;
  private Supplier<Camera> debugCamera = () -> null;
  private Runnable interactionChanged = () -> {};
  private boolean focused = true, restoreCapture, closed;

  public RuntimeToolsOverlay(Stage stage, UiTheme theme, Supplier<Game> scene) {
    inspector = new RuntimeInspector(stage, theme, scene);
    inspector.setVisible(false);
    inspector.setVisibilityListener(this::visibilityChanged);
    inspector.setFocusSelectedHandler(object -> {
      Camera camera = debugCamera.get();
      if (camera == null) return "Focus refused: enter free-fly debug mode first";
      Transform3D transform = object.getComponent(Transform3D.class);
      if (transform == null) return "Focus refused: selected object has no Transform3D";
      Vector3 target = transform.worldMatrix().getTranslation(new Vector3());
      camera.position.set(target).mulAdd(camera.direction, -5f);
      camera.lookAt(target);
      camera.update();
      return null;
    });
  }

  public RuntimeInspector inspector() { return inspector; }

  /** Return a camera only after the host explicitly entered its debug/free-fly mode. */
  public void setDebugCameraProvider(Supplier<Camera> provider) {
    debugCamera = Objects.requireNonNull(provider);
  }

  /** Clear gameplay latches and discard the next mouse delta on each input handoff. */
  public void setInteractionChanged(Runnable callback) {
    interactionChanged = Objects.requireNonNull(callback);
  }

  private void visibilityChanged(boolean visible) {
    if (visible) {
      restoreCapture = focused && Gdx.input.isCursorCatched();
      Gdx.input.setCursorCatched(false);
    } else {
      Gdx.input.setCursorCatched(focused && restoreCapture);
      restoreCapture = false;
    }
    interactionChanged.run();
  }

  public void update(float unscaledDelta) { inspector.update(unscaledDelta); }
  public void drawGizmos() { inspector.drawGizmos(); }

  public void onFocusLost() {
    focused = false;
    restoreCapture = false;
    inspector.onFocusLost();
    Gdx.input.setCursorCatched(false);
    interactionChanged.run();
  }

  public void onFocusGained() { focused = true; }

  @Override public void close() {
    if (closed) return;
    if (inspector.isVisible()) inspector.setVisible(false);
    closed = true;
    inspector.close();
  }
}
