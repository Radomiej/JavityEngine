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
  private final FeelPreviewPanel feelPreview;
  private final LightAuthoringPanel lights;
  private com.badlogic.gdx.graphics.glutils.ShapeRenderer socketRenderer;
  private org.javity.avatar.AvatarBinding socketBinding;
  private String socketName;
  private Supplier<com.badlogic.gdx.math.Matrix4> socketBase;
  private Supplier<Camera> socketCamera;

  private Supplier<Camera> debugCamera = () -> null;
  private Runnable interactionChanged = () -> {};
  private boolean focused = true, restoreCapture, closed;

  public RuntimeToolsOverlay(Stage stage, UiTheme theme, Supplier<Game> scene) {
    inspector = new RuntimeInspector(stage, theme, scene);
    inspector.setVisible(false);
    try {
      feelPreview = new FeelPreviewPanel(stage, theme, inspector::selectedObject);
    } catch (RuntimeException | Error failure) {
      inspector.close();
      throw failure;
    }
    try {
      lights = new LightAuthoringPanel(stage, theme, inspector);
    } catch (RuntimeException | Error failure) {
      feelPreview.close();
      inspector.close();
      throw failure;
    }
    feelPreview.setVisible(false);
    inspector.setVisibilityListener(this::visibilityChanged);
    inspector.setFocusSelectedHandler(
        object -> {
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

  public RuntimeInspector inspector() {
    return inspector;
  }

  public FeelPreviewPanel feelPreview() {
    return feelPreview;
  }

  /** Return a camera only after the host explicitly entered its debug/free-fly mode. */
  public void setDebugCameraProvider(Supplier<Camera> provider) {
    debugCamera = Objects.requireNonNull(provider);
  }

  /** Clear gameplay latches and discard the next mouse delta on each input handoff. */
  public void setInteractionChanged(Runnable callback) {
    interactionChanged = Objects.requireNonNull(callback);
  }

  private void visibilityChanged(boolean visible) {
    feelPreview.setVisible(visible);
    if (!visible) {
      feelPreview.stop();
      lights.setVisible(false);
    }
    if (visible) {
      restoreCapture = focused && Gdx.input.isCursorCatched();
      Gdx.input.setCursorCatched(false);
    } else {
      Gdx.input.setCursorCatched(focused && restoreCapture);
      restoreCapture = false;
    }
    interactionChanged.run();
  }

  public void update(float unscaledDelta) {
    inspector.update(unscaledDelta);
    lights.update(inspector.isVisible());
    feelPreview.setVisible(inspector.isVisible() && !lights.hasLight());
    feelPreview.update(unscaledDelta);
  }

  public LightAuthoringPanel lightAuthoring() {
    return lights;
  }

  /** Optional socket diagnostic: host borrows binding/base/camera until clear or close. */
  public void setSocketGizmo(
      org.javity.avatar.AvatarBinding binding,
      String name,
      Supplier<com.badlogic.gdx.math.Matrix4> base,
      Supplier<Camera> camera) {
    socketBinding = Objects.requireNonNull(binding);
    socketName = Objects.requireNonNull(name);
    socketBase = Objects.requireNonNull(base);
    socketCamera = Objects.requireNonNull(camera);
  }

  public void clearSocketGizmo() {
    socketBinding = null;
    socketBase = null;
    socketCamera = null;
  }

  public void drawGizmos() {
    inspector.drawGizmos();
    if (closed || !inspector.isVisible() || socketBinding == null) return;
    Camera camera = socketCamera.get();
    if (camera == null) return;
    if (socketRenderer == null)
      socketRenderer = new com.badlogic.gdx.graphics.glutils.ShapeRenderer();
    socketRenderer.setProjectionMatrix(camera.combined);
    socketRenderer.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line);
    try {
      SocketGizmoAdapter.draw(socketRenderer, camera, socketBinding, socketName, socketBase.get());
    } finally {
      socketRenderer.end();
    }
  }

  public void onFocusLost() {
    focused = false;
    restoreCapture = false;
    inspector.onFocusLost();
    Gdx.input.setCursorCatched(false);
    interactionChanged.run();
  }

  public void onFocusGained() {
    focused = true;
  }

  @Override
  public void close() {
    if (closed) return;
    if (inspector.isVisible()) inspector.setVisible(false);
    closed = true;
    clearSocketGizmo();
    if (socketRenderer != null) socketRenderer.dispose();
    lights.close();
    feelPreview.close();
    inspector.close();
  }
}
