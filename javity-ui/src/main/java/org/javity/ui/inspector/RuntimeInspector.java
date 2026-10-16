package org.javity.ui.inspector;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;
import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.core.Transform3D;
import org.javity.serialization.SceneJson;
import org.javity.ui.flex.*;

/**
 * Optional desktop debug overlay. Borrows its Stage, theme and current scene; owns its Yoga tree.
 */
public final class RuntimeInspector implements AutoCloseable {
  private final Stage stage;
  private final UiTheme theme;
  private final Supplier<Game> currentScene;
  private final RuntimeInspectionSession session = new RuntimeInspectionSession();
  private final SceneInspection inspection = session.inspection;
  private final TextField search;
  private java.util.function.Consumer<Boolean> visibilityChanged = value -> {};
  private java.util.function.Function<GameObject, String> focusSelected = object -> "Debug camera unavailable";
  private final Flex root, panel, rows;
  private final Label status, detail;
  private final Label editStatus;
  private final SceneJson exportSchema;
  private final java.nio.file.Path exportDestination;
  private String exportReportText;
  private final TextButton transformField;
  private final TextField transformValue;
  private final TextButton gizmoModeButton, gizmoSpaceButton;
  private final TextField gizmoSnap;
  private static final List<String> TRANSFORM_FIELDS =
      List.of(
          "2D.POSITION_X",
          "2D.POSITION_Y",
          "2D.ROTATION",
          "2D.SCALE_X",
          "2D.SCALE_Y",
          "3D.POSITION_X",
          "3D.POSITION_Y",
          "3D.POSITION_Z",
          "3D.ROTATION_X",
          "3D.ROTATION_Y",
          "3D.ROTATION_Z",
          "3D.ROTATION_W",
          "3D.SCALE_X",
          "3D.SCALE_Y",
          "3D.SCALE_Z");
  private final ScrollPane listScroll, detailScroll;
  private List<SceneInspection.Row> previousRows = List.of();
  private Integer previousSelection;
  private GameObject editorObject;
  private Game displayedScene;
  private final Map<Integer, TextButton> buttons = new LinkedHashMap<>();
  private float elapsed;
  private boolean closed;
  private ShapeRenderer gizmoRenderer;
  private Camera gizmoCamera3D;
  private Camera gizmoCamera2D;
  private GizmoMode gizmoMode = GizmoMode.TRANSLATE;
  private boolean gizmoLocal = true;
  private Drag drag;
  private final Vector2 dragPointer = new Vector2();

  private enum GizmoMode {
    TRANSLATE,
    ROTATE,
    SCALE
  }

  private record Drag(
      int pointer,
      int axis,
      Vector2 start,
      Vector2 projectedAxis,
      SceneInspection.Transform3DState before3D,
      SceneInspection.Transform2DState before2D,
      SceneInspection.Transform3DEditSession session3D,
      SceneInspection.Transform2DEditSession session2D,
      Transform3D parent,
      float[] parentMatrix,
      Camera camera,
      CameraStamp cameraStamp) {}

  private static final class CameraStamp {
    final float[] combined, view, projection, position, direction, up;
    final float viewportWidth, viewportHeight;
    final int backbufferWidth, backbufferHeight, stageScreenX, stageScreenY;
    final int stageScreenWidth, stageScreenHeight;
    final float stageWorldWidth, stageWorldHeight;

    CameraStamp(Camera camera, Stage stage) {
      combined = Arrays.copyOf(camera.combined.val, 16);
      view = Arrays.copyOf(camera.view.val, 16);
      projection = Arrays.copyOf(camera.projection.val, 16);
      position = vector(camera.position);
      direction = vector(camera.direction);
      up = vector(camera.up);
      viewportWidth = camera.viewportWidth;
      viewportHeight = camera.viewportHeight;
      backbufferWidth = Gdx.graphics.getBackBufferWidth();
      backbufferHeight = Gdx.graphics.getBackBufferHeight();
      var viewport = stage.getViewport();
      stageScreenX = viewport.getScreenX();
      stageScreenY = viewport.getScreenY();
      stageScreenWidth = viewport.getScreenWidth();
      stageScreenHeight = viewport.getScreenHeight();
      stageWorldWidth = viewport.getWorldWidth();
      stageWorldHeight = viewport.getWorldHeight();
    }

    boolean matches(Camera camera, Stage stage) {
      var viewport = stage.getViewport();
      return cameraProjectionUnchanged(
              combined,
              backbufferWidth,
              backbufferHeight,
              camera.combined.val,
              Gdx.graphics.getBackBufferWidth(),
              Gdx.graphics.getBackBufferHeight())
          && Arrays.equals(view, camera.view.val)
          && Arrays.equals(projection, camera.projection.val)
          && Arrays.equals(position, vector(camera.position))
          && Arrays.equals(direction, vector(camera.direction))
          && Arrays.equals(up, vector(camera.up))
          && Float.compare(viewportWidth, camera.viewportWidth) == 0
          && Float.compare(viewportHeight, camera.viewportHeight) == 0
          && backbufferWidth == Gdx.graphics.getBackBufferWidth()
          && backbufferHeight == Gdx.graphics.getBackBufferHeight()
          && stageScreenX == viewport.getScreenX()
          && stageScreenY == viewport.getScreenY()
          && stageScreenWidth == viewport.getScreenWidth()
          && stageScreenHeight == viewport.getScreenHeight()
          && Float.compare(stageWorldWidth, viewport.getWorldWidth()) == 0
          && Float.compare(stageWorldHeight, viewport.getWorldHeight()) == 0;
    }

    private static float[] vector(Vector3 value) {
      return new float[] {value.x, value.y, value.z};
    }
  }

  private final InputAdapter keys =
      new InputAdapter() {
        @Override
        public boolean keyDown(int keycode) {
          if (!closed && keycode == Input.Keys.F1) {
            setVisible(!isVisible());
            return true;
          }
          return false;
        }
      };

  private final InputAdapter gizmoInput =
      new InputAdapter() {
        @Override
        public boolean touchDown(int x, int y, int pointer, int button) {
          try {
            boolean handled = beginDrag(x, y, pointer, button);
            if (drag != null) dragPointer.set(x, y);
            return handled;
          } catch (IllegalArgumentException | IllegalStateException failure) {
            editStatus.setText(
                failure.getMessage() == null ? "Gizmo input rejected" : failure.getMessage());
            drag = null;
            return true;
          }
        }

        @Override
        public boolean touchDragged(int x, int y, int pointer) {
          if (drag == null || drag.pointer() != pointer) return false;
          dragPointer.set(x, y);
          if (!dragProjectionIsCurrent()) cancelDrag("Camera or viewport changed; drag cancelled");
          return true;
        }

        @Override
        public boolean touchUp(int x, int y, int pointer, int button) {
          if (drag == null || drag.pointer() != pointer) return false;
          finishDrag(x, y);
          return true;
        }

        @Override
        public boolean touchCancelled(int x, int y, int pointer, int button) {
          if (drag == null || drag.pointer() != pointer) return false;
          drag = null;
          return true;
        }
      };

  public RuntimeInspector(Stage stage, UiTheme theme, Supplier<Game> currentScene) {
    this(stage, theme, currentScene, null, null);
  }

  /** Configure an explicit runtime snapshot schema and destination supplied by the host. */
  public RuntimeInspector(
      Stage stage,
      UiTheme theme,
      Supplier<Game> currentScene,
      SceneJson exportSchema,
      java.nio.file.Path exportDestination) {
    this.stage = Objects.requireNonNull(stage);
    this.theme = Objects.requireNonNull(theme);
    this.currentScene = Objects.requireNonNull(currentScene);
    if ((exportSchema == null) != (exportDestination == null))
      throw new IllegalArgumentException("SceneJson and export destination must be configured together");
    this.exportSchema = exportSchema;
    this.exportDestination = exportDestination;
    root = new Flex(theme, "flex-row p-4");
    rows = new Flex(theme, "flex-col gap-1");
    try {
      root.setName("runtime-inspector");
      root.setFillParent(true);
      root.add(new Flex("flex-col"), "flex-1");
      panel =
          root.add(
              new Flex(theme, "flex-col p-4 gap-1 bg-slate-900 rounded-lg border border-slate-700"),
              "w-96 h-full");
      // Only the panel blocks world input. Empty overlay space remains childrenOnly.
      panel.setName("runtime-inspector-panel");
      panel.setTouchable(Touchable.enabled);
      panel.addListener(
          new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
              return true;
            }
          });
      Flex heading = panel.add(new Flex(theme, "flex-row gap-2"), "h-8");
      heading.add(theme.label("RUNTIME SNAPSHOT", "text-cyan-400"), "flex-1");
      heading.add(
          theme.button("X", "bg-slate-700 text-slate-200 rounded", () -> setVisible(false)), "w-8");
      if (exportSchema != null) {
        TextButton exportButton = theme.button(
            "EXPORT",
            "text-sm text-white bg-blue-600 rounded",
            this::exportRuntimeSnapshot);
        exportButton.setName("runtime-export-button");
        heading.add(exportButton, "w-16");
        TextButton selectionButton = theme.button(
            "PICK",
            "text-sm text-white bg-slate-700 rounded",
            this::exportSelectedSnapshot);
        selectionButton.setName("runtime-export-selection-button");
        heading.add(selectionButton, "w-12");
      }
      status = panel.add(theme.label("No live scene", "text-sm text-slate-400", true), "h-8");
      Flex searchRow = panel.add(new Flex(theme, "flex-row gap-1"), "h-8");
      search = theme.textField("", "text-sm text-slate-200 bg-slate-800 rounded");
      search.setMessageText("Search name / ID / tag");
      search.setName("runtime-object-search");
      search.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener() {
        @Override public void changed(ChangeEvent event, Actor actor) {
          session.query = search.getText();
          refresh();
        }
      });
      searchRow.add(search, "flex-1");
      TextButton focus = theme.button("Focus", "text-sm text-white bg-slate-700 rounded", this::focusSelected);
      focus.setName("runtime-focus-selected");
      searchRow.add(focus, "w-14");
      listScroll = new ScrollPane(rows);
      listScroll.setName("runtime-object-list");
      listScroll.setScrollingDisabled(true, false);
      listScroll.setFadeScrollBars(false);
      installScrollFocus(listScroll);
      panel.add(listScroll, "h-24");
      panel.add(theme.label("INSPECTOR", "text-cyan-400"), "h-6");
      detail = theme.label("Select a GameObject", "text-sm text-slate-200", true);
      detail.setName("runtime-inspector-details");
      detailScroll = new ScrollPane(detail);
      detailScroll.setName("runtime-inspector-details-viewport");
      detailScroll.setScrollingDisabled(true, false);
      detailScroll.setFadeScrollBars(false);
      installScrollFocus(detailScroll);
      panel.add(detailScroll, "flex-1");
      panel.add(
          theme.label("TRANSFORM FIELD  tap to choose 2D / 3D property", "text-sm text-cyan-400"),
          "h-5");
      Flex transformRow = panel.add(new Flex(theme, "flex-row gap-1"), "h-8");
      transformField =
          theme.button(
              "2D.POSITION_X",
              "text-sm text-slate-200 bg-slate-800 rounded",
              this::nextTransformField);
      transformField.setName("runtime-transform-field");
      transformValue = theme.textField("0", "text-sm text-slate-200 bg-slate-800 rounded");
      transformValue.setName("runtime-transform-value");
      transformRow.add(transformField, "flex-1");
      transformRow.add(transformValue, "flex-1");
      TextButton applyButton =
          theme.button("Apply", "text-sm text-white bg-blue-600 rounded", this::applyTransform);
      applyButton.setName("runtime-transform-apply");
      transformRow.add(applyButton, "w-14");
      Flex gizmoRow = panel.add(new Flex(theme, "flex-row gap-1"), "h-8");
      gizmoModeButton =
          theme.button(
              "GIZMO: MOVE", "text-sm text-white bg-blue-600 rounded", this::cycleGizmoMode);
      gizmoModeButton.setName("runtime-gizmo-mode");
      gizmoSpaceButton =
          theme.button(
              "LOCAL", "text-sm text-slate-200 bg-slate-700 rounded", this::toggleGizmoSpace);
      gizmoSpaceButton.setName("runtime-gizmo-space");
      gizmoSnap = theme.textField("0.5", "text-sm text-slate-200 bg-slate-800 rounded");
      gizmoSnap.setName("runtime-gizmo-snap");
      gizmoRow.add(gizmoModeButton, "flex-1");
      gizmoRow.add(gizmoSpaceButton, "w-16");
      gizmoRow.add(gizmoSnap, "w-16");
      Flex historyRow = panel.add(new Flex(theme, "flex-row gap-1"), "h-8");
      TextButton undoButton =
          theme.button("Undo", "text-sm text-slate-200 bg-slate-700 rounded", this::undo);
      undoButton.setName("runtime-transform-undo");
      TextButton redoButton =
          theme.button("Redo", "text-sm text-slate-200 bg-slate-700 rounded", this::redo);
      redoButton.setName("runtime-transform-redo");
      historyRow.add(undoButton, "flex-1");
      historyRow.add(redoButton, "flex-1");
      editStatus = panel.add(theme.label("Edit status", "text-sm text-slate-400"), "h-6");
      editStatus.setName("runtime-edit-status");
      gizmoRenderer = new ShapeRenderer();
      stage.addActor(root);
      refresh();
    } catch (RuntimeException | Error failure) {
      if (gizmoRenderer != null) gizmoRenderer.dispose();
      rows.close();
      root.close();
      throw failure;
    }
  }

  /** Prepare once, display every preflight issue, then atomically save that exact candidate. */
  public boolean exportRuntimeSnapshot() {
    return exportSnapshot(false);
  }

  /** Export the currently selected GameObject through the same configured schema and destination. */
  public boolean exportSelectedSnapshot() {
    return exportSnapshot(true);
  }

  private boolean exportSnapshot(boolean selectedOnly) {
    if (closed || exportSchema == null) return false;
    Game scene = currentScene.get();
    if (scene == null) {
      exportReportText = "Runtime snapshot export failed: no live scene";
      detail.setText(exportReportText);
      detailScroll.invalidateHierarchy();
      return false;
    }
    SceneJson.ExportReport report;
    if (selectedOnly) {
      GameObject selected = editorObject;
      if (scene != displayedScene || selected == null || selected.game != scene
          || selected.isDestroyed() || !scene.getObjects().contains(selected)) {
        exportReportText = "Runtime snapshot blocked: select a live object in the current scene";
        editStatus.setText("Runtime snapshot blocked");
        detail.setText(exportReportText);
        detailScroll.invalidateHierarchy();
        return false;
      }
      report = exportSchema.prepareSelection(List.of(selected));
    } else {
      report = exportSchema.prepare(scene);
    }
    if (!report.isValid()) {
      exportReportText = "Runtime snapshot blocked:\n" +
          String.join("\n", report.issues().stream().map(SceneJson.ExportIssue::toString).toList());
      editStatus.setText("Runtime snapshot blocked (" + report.issues().size() + " issues)");
      detail.setText(exportReportText);
      detailScroll.invalidateHierarchy();
      return false;
    }
    try {
      exportSchema.save(report, exportDestination);
      exportReportText = "Runtime snapshot saved:\n" + exportDestination.toAbsolutePath();
      editStatus.setText("Runtime snapshot saved");
      detail.setText(exportReportText);
      detailScroll.invalidateHierarchy();
      return true;
    } catch (java.io.IOException | RuntimeException failure) {
      exportReportText = "Runtime snapshot failed: " +
          (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
      editStatus.setText("Runtime snapshot failed");
      detail.setText(exportReportText);
      detailScroll.invalidateHierarchy();
      return false;
    }
  }

  /** Put this before the Stage processor to reserve F1. No global input is replaced. */
  public InputAdapter inputProcessor() {
    return keys;
  }

  /** Put this after Stage in the host InputMultiplexer so handled UI events keep priority. */
  public InputAdapter gizmoInputProcessor() {
    return gizmoInput;
  }

  /** Supply live cameras for screen-pickable world handles; cameras remain caller-owned. */
  public void setGizmoCameras(Camera camera3D, Camera camera2D) {
    requireOpen();
    gizmoCamera3D = camera3D;
    gizmoCamera2D = camera2D;
    cancelDrag("Gizmo camera changed; drag cancelled");
  }

  /** Call from the host window's focus-loss callback. */
  public void onFocusLost() {
    cancelDrag("Window lost focus; gizmo drag cancelled");
  }

  /** Call before applying a host Stage/world viewport resize. */
  public void onViewportChanged() {
    cancelDrag("Viewport resized; gizmo drag cancelled");
  }

  private void cancelDrag(String message) {
    if (drag != null && editStatus != null) editStatus.setText(message);
    drag = null;
  }

  private boolean dragProjectionIsCurrent() {
    return drag != null && drag.cameraStamp().matches(drag.camera(), stage);
  }

  static boolean cameraProjectionUnchanged(
      float[] before, int beforeWidth, int beforeHeight, float[] current, int width, int height) {
    return Arrays.equals(before, current) && beforeWidth == width && beforeHeight == height;
  }

  /** Current height available for selected-object details, exposed for host layout diagnostics. */
  public float detailsViewportHeight() {
    return detailScroll.getHeight();
  }

  public float detailsContentHeight() {
    detailScroll.validate();
    return detail.getPrefHeight();
  }

  public boolean canUndo() {
    return inspection.canUndo();
  }

  public boolean canRedo() {
    return inspection.canRedo();
  }

  /** Draw world handles after the game renderer and before Stage draws the inspector panel. */
  public void drawGizmos() {
    if (closed || !isVisible() || editorObject == null || !editorObject.isActive()) return;
    Transform3D spatial = editorObject.getComponent(Transform3D.class);
    boolean is3D = spatial != null && gizmoCamera3D != null;
    Camera camera = is3D ? gizmoCamera3D : gizmoCamera2D;
    if (camera == null) return;
    Vector3 origin =
        is3D
            ? worldPosition(spatial)
            : new Vector3(editorObject.transform.position.x, editorObject.transform.position.y, 0);
    if (drag != null && gizmoMode == GizmoMode.TRANSLATE) {
      if (!dragProjectionIsCurrent()) {
        cancelDrag("Camera or viewport changed; drag cancelled");
        return;
      }
      Vector2 delta = dragPointer.cpy().sub(drag.start());
      float length = drag.projectedAxis().len();
      if (length > 1) {
        float amount = delta.dot(drag.projectedAxis()) / (length * length) * handleLength(is3D);
        float step = parseSnapStepOrZero();
        try {
          if (is3D && drag.before3D() != null) {
            Vector3 basis =
                new Vector3(
                        drag.axis() == 0 ? 1 : 0,
                        drag.axis() == 1 ? 1 : 0,
                        drag.axis() == 2 ? 1 : 0)
                    .scl(amount);
            SceneInspection.Transform3DState preview =
                SceneInspection.translatedTransform3D(
                    drag.before3D(), drag.parent(), basis, gizmoLocal, step);
            origin.set(preview.x(), preview.y(), preview.z());
            if (drag.parent() != null) origin.mul(drag.parent().worldMatrix());
          } else if (!is3D && drag.before2D() != null) {
            Vector2 localDelta =
                new Vector2(drag.axis() == 0 ? amount : 0, drag.axis() == 1 ? amount : 0);
            localDelta.x = snap(localDelta.x, step);
            localDelta.y = snap(localDelta.y, step);
            if (gizmoLocal) localDelta.rotateRad(drag.before2D().rotation());
            origin.x = drag.before2D().x() + localDelta.x;
            origin.y = drag.before2D().y() + localDelta.y;
          }
        } catch (IllegalArgumentException | IllegalStateException failure) {
          cancelDrag(
              failure.getMessage() == null ? "Gizmo preview rejected" : failure.getMessage());
          origin =
              is3D
                  ? worldPosition(spatial)
                  : new Vector3(
                      editorObject.transform.position.x, editorObject.transform.position.y, 0);
        }
      }
    }
    if (gizmoRenderer == null) return;
    gizmoRenderer.setProjectionMatrix(camera.combined);
    boolean depthWasEnabled = Gdx.gl.glIsEnabled(GL20.GL_DEPTH_TEST);
    Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    gizmoRenderer.begin(ShapeRenderer.ShapeType.Line);
    try {
      int count = is3D ? 3 : 2;
      for (int axis = 0; axis < count; axis++) {
        gizmoRenderer.setColor(
            inspection.selectedTransformEditable() ? axisColor(axis) : Color.GRAY);
        Vector3 endpoint = handleEndpoint(editorObject, axis, is3D, gizmoLocal);
        Vector3 actualOrigin =
            is3D
                ? worldPosition(editorObject.getComponent(Transform3D.class))
                : new Vector3(
                    editorObject.transform.position.x, editorObject.transform.position.y, 0);
        endpoint.add(origin.cpy().sub(actualOrigin));
        gizmoRenderer.line(origin, endpoint);
      }
    } finally {
      gizmoRenderer.end();
      if (depthWasEnabled) Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    }
  }

  private void cycleGizmoMode() {
    if (closed) return;
    drag = null;
    gizmoMode = GizmoMode.values()[(gizmoMode.ordinal() + 1) % GizmoMode.values().length];
    gizmoModeButton.setText(
        switch (gizmoMode) {
          case TRANSLATE -> "GIZMO: MOVE";
          case ROTATE -> "GIZMO: ROTATE";
          case SCALE -> "GIZMO: SCALE";
        });
  }

  private void toggleGizmoSpace() {
    if (closed) return;
    drag = null;
    gizmoLocal = !gizmoLocal;
    gizmoSpaceButton.setText(gizmoLocal ? "LOCAL" : "WORLD");
  }

  private boolean beginDrag(int x, int y, int pointer, int button) {
    if (closed || !isVisible() || button != Input.Buttons.LEFT || editorObject == null)
      return false;
    if (currentScene.get() != displayedScene) {
      drag = null;
      refresh();
      return false;
    }
    Transform3D t = editorObject.getComponent(Transform3D.class);
    boolean is3D = t != null;
    Camera camera = is3D ? gizmoCamera3D : gizmoCamera2D;
    if (camera == null) return false;
    Vector3 origin =
        is3D
            ? worldPosition(t)
            : new Vector3(editorObject.transform.position.x, editorObject.transform.position.y, 0);
    Vector2 pointerPoint = new Vector2(x, y), bestAxis = null;
    float bestDistance = 11f;
    int selectedAxis = -1;
    for (int axis = 0; axis < (is3D ? 3 : 2); axis++) {
      Vector2 a = projectToScreen(camera, origin);
      Vector2 b = projectToScreen(camera, handleEndpoint(editorObject, axis, is3D, gizmoLocal));
      Vector2 segment = b.cpy().sub(a);
      float length2 = segment.len2();
      if (length2 < 4) continue;
      float along = MathUtils.clamp(pointerPoint.cpy().sub(a).dot(segment) / length2, 0, 1);
      float distance = pointerPoint.dst(a.mulAdd(segment, along));
      if (distance < bestDistance) {
        bestDistance = distance;
        selectedAxis = axis;
        bestAxis = segment;
      }
    }
    if (selectedAxis < 0) return false;
    if (!inspection.selectedTransformEditable()) {
      editStatus.setText("A component owns this object's transform; gizmo edit rejected");
      return true;
    }
    try {
      Transform3D parent = is3D ? t.getParent() : null;
      SceneInspection.Transform3DState before3D = is3D ? inspection.selectedTransform3D() : null;
      SceneInspection.Transform2DState before2D = is3D ? null : inspection.selectedTransform2D();
      if (gizmoMode == GizmoMode.TRANSLATE && is3D && !gizmoLocal)
        SceneInspection.translatedTransform3D(
            before3D, parent, Vector3.Zero, false, parseSnapStepOrZero());
      if (gizmoMode == GizmoMode.ROTATE && is3D && !gizmoLocal && parent != null)
        throw new IllegalStateException(
            "World rotation below a parent is unsupported; choose LOCAL");
      if (gizmoMode == GizmoMode.SCALE && is3D && !gizmoLocal)
        requireWorldScale3DRepresentable(before3D, parent);
      if (gizmoMode == GizmoMode.SCALE && !is3D && !gizmoLocal)
        SceneInspection.scaledTransform2D(before2D, selectedAxis, 1.1f, false, 0);
      float[] parentMatrix = parent == null ? null : Arrays.copyOf(parent.worldMatrix().val, 16);
      drag =
          new Drag(
              pointer,
              selectedAxis,
              pointerPoint,
              bestAxis,
              before3D,
              before2D,
              is3D ? inspection.beginTransform3DEdit() : null,
              is3D ? null : inspection.beginTransform2DEdit(),
              parent,
              parentMatrix,
              camera,
              new CameraStamp(camera, stage));
      return true;
    } catch (IllegalArgumentException | IllegalStateException failure) {
      editStatus.setText(
          failure.getMessage() == null ? "Gizmo edit rejected" : failure.getMessage());
      return true;
    }
  }

  private static void requireWorldScale3DRepresentable(
      SceneInspection.Transform3DState before, Transform3D parent) {
    if (parent != null
        || Math.abs(before.qx()) > 1e-5f
        || Math.abs(before.qy()) > 1e-5f
        || Math.abs(before.qz()) > 1e-5f)
      throw new IllegalStateException(
          "World scale under a parent or rotated object may introduce shear; choose LOCAL");
  }

  private void finishDrag(int x, int y) {
    Drag current = drag;
    drag = null;
    if (current == null || editorObject == null || currentScene.get() != displayedScene) return;
    if (!current.cameraStamp().matches(current.camera(), stage)) {
      editStatus.setText("Camera or viewport changed; gizmo drag cancelled");
      return;
    }
    Transform3D transform = editorObject.getComponent(Transform3D.class);
    if (current.before3D() != null && transform == null) {
      editStatus.setText("Transform3D removed during drag");
      return;
    }
    if (current.parent() != null) {
      if (transform.getParent() != current.parent()
          || !Arrays.equals(current.parentMatrix(), current.parent().worldMatrix().val)) {
        editStatus.setText("Parent changed during drag; gizmo edit cancelled");
        return;
      }
    }
    try {
      float step = Float.parseFloat(gizmoSnap.getText().trim());
      if (!Float.isFinite(step) || step < 0)
        throw new IllegalArgumentException(
            "Snap step must be nonnegative and finite; zero disables snapping");
      float length = current.projectedAxis().len();
      if (length < 2) return;
      Vector2 delta = new Vector2(x - current.start().x, y - current.start().y);
      float fraction = delta.dot(current.projectedAxis()) / (length * length);
      float amount = fraction * handleLength(current.before3D() != null);
      int axis = current.axis();
      if (current.before3D() == null) {
        SceneInspection.Transform2DState b = current.before2D();
        float xPos = b.x(), yPos = b.y(), rotation = b.rotation(), sx = b.scaleX(), sy = b.scaleY();
        if (gizmoMode == GizmoMode.TRANSLATE) {
          Vector3 d = new Vector3(axis == 0 ? 1 : 0, axis == 1 ? 1 : 0, 0).scl(amount);
          d.x = snap(d.x, step);
          d.y = snap(d.y, step);
          if (gizmoLocal) d.rotateRad(Vector3.Z, b.rotation());
          xPos += d.x;
          yPos += d.y;
        } else if (gizmoMode == GizmoMode.ROTATE) {
          rotation += snap(fraction * MathUtils.PI, step);
        } else {
          SceneInspection.Transform2DState scaled =
              SceneInspection.scaledTransform2D(
                  b, axis, Math.max(.001f, 1 + amount / handleLength(false)), gizmoLocal, step);
          sx = scaled.scaleX();
          sy = scaled.scaleY();
        }
        inspection.commitTransform2D(
            current.session2D(),
            new SceneInspection.Transform2DState(xPos, yPos, rotation, sx, sy));
      } else {
        SceneInspection.Transform3DState b = current.before3D();
        Vector3 direction = new Vector3(axis == 0 ? 1 : 0, axis == 1 ? 1 : 0, axis == 2 ? 1 : 0);
        float[] next = stateValues(b);
        if (gizmoMode == GizmoMode.TRANSLATE) {
          SceneInspection.Transform3DState moved =
              SceneInspection.translatedTransform3D(
                  b, transform.getParent(), direction.scl(amount), gizmoLocal, step);
          inspection.commitTransform3D(current.session3D(), moved);
          editStatus.setText("Gizmo edit applied as one undo step");
          refresh();
          return;
        } else if (gizmoMode == GizmoMode.ROTATE) {
          if (!gizmoLocal && transform.getParent() != null)
            throw new IllegalStateException(
                "World rotation below a parent is unsupported; choose LOCAL");
          Quaternion q = new Quaternion(b.qx(), b.qy(), b.qz(), b.qw());
          Quaternion d =
              new Quaternion(
                  new Vector3(axis == 0 ? 1 : 0, axis == 1 ? 1 : 0, axis == 2 ? 1 : 0),
                  snap(fraction * MathUtils.PI, step) * MathUtils.radiansToDegrees);
          if (gizmoLocal) q.mul(d);
          else q.mulLeft(d);
          next[3] = q.x;
          next[4] = q.y;
          next[5] = q.z;
          next[6] = q.w;
        } else {
          if (!gizmoLocal) requireWorldScale3DRepresentable(b, transform.getParent());
          next[7 + axis] =
              next[7 + axis] * Math.max(.001f, 1 + snap(amount / handleLength(true), step));
        }
        inspection.commitTransform3D(
            current.session3D(),
            new SceneInspection.Transform3DState(
                next[0], next[1], next[2], next[3], next[4], next[5], next[6], next[7], next[8],
                next[9]));
      }
      editStatus.setText("Gizmo edit applied as one undo step");
      refresh();
    } catch (IllegalArgumentException | IllegalStateException failure) {
      editStatus.setText(
          failure.getMessage() == null ? "Gizmo edit rejected" : failure.getMessage());
    }
  }

  private static float[] stateValues(SceneInspection.Transform3DState s) {
    return new float[] {
      s.x(), s.y(), s.z(), s.qx(), s.qy(), s.qz(), s.qw(), s.scaleX(), s.scaleY(), s.scaleZ()
    };
  }

  private static float snap(float value, float step) {
    return step == 0 ? value : (float) Math.floor(value / step + .5f) * step;
  }

  private float parseSnapStepOrZero() {
    try {
      float value = Float.parseFloat(gizmoSnap.getText().trim());
      return Float.isFinite(value) && value >= 0 ? value : 0;
    } catch (RuntimeException ignored) {
      return 0;
    }
  }

  private static float handleLength(boolean is3D) {
    return is3D ? 1.25f : 55f;
  }

  private static Color axisColor(int axis) {
    return switch (axis) {
      case 0 -> Color.RED;
      case 1 -> Color.GREEN;
      default -> Color.BLUE;
    };
  }

  private static Vector3 worldPosition(Transform3D transform) {
    return transform.worldMatrix().getTranslation(new Vector3());
  }

  private static Vector3 handleEndpoint(GameObject object, int axis, boolean is3D, boolean local) {
    float length = handleLength(is3D);
    Vector3 basis = new Vector3(axis == 0 ? 1 : 0, axis == 1 ? 1 : 0, axis == 2 ? 1 : 0);
    if (is3D) {
      Transform3D t = object.getComponent(Transform3D.class);
      Vector3 origin = worldPosition(t);
      if (local) {
        t.rotation.transform(basis);
        if (t.getParent() != null) basis = transformVector(t.getParent().worldMatrix(), basis);
        return origin.mulAdd(basis, length);
      }
      return origin.mulAdd(basis, length);
    }
    Vector3 origin = new Vector3(object.transform.position.x, object.transform.position.y, 0);
    if (local) basis.rotateRad(Vector3.Z, object.transform.rotation);
    return origin.mulAdd(basis, length);
  }

  private static Vector3 transformVector(Matrix4 matrix, Vector3 vector) {
    float[] m = matrix.val;
    return new Vector3(
        m[0] * vector.x + m[4] * vector.y + m[8] * vector.z,
        m[1] * vector.x + m[5] * vector.y + m[9] * vector.z,
        m[2] * vector.x + m[6] * vector.y + m[10] * vector.z);
  }

  private static Vector2 projectToScreen(Camera camera, Vector3 world) {
    Vector3 projected = camera.project(world.cpy());
    return new Vector2(projected.x, Gdx.graphics.getHeight() - projected.y);
  }

  public boolean isVisible() {
    return !closed && root.isVisible();
  }

  public void setVisible(boolean value) {
    requireOpen();
    if (root.isVisible() == value) return;
    root.setVisible(value);
    if (!value) {
      clearFocus();
      drag = null;
    } else refresh();
    visibilityChanged.accept(value);
  }

  public void update(float unscaledDelta) {
    requireOpen();
    if (!Float.isFinite(unscaledDelta) || unscaledDelta < 0)
      throw new IllegalArgumentException("Invalid UI delta");
    if (drag != null && !dragProjectionIsCurrent())
      cancelDrag("Camera or viewport changed; drag cancelled");
    if (!isVisible()) return;
    elapsed += unscaledDelta;
    if (elapsed >= .2f) {
      elapsed = 0;
      refresh();
    }
  }

  public void refresh() {
    requireOpen();
    Game source = currentScene.get();
    boolean sceneChanged = session.bind(source);
    displayedScene = source;
    if (sceneChanged) search.setText("");
    SceneInspection.Snapshot snapshot = inspection.capture();
    status.setText(snapshot.status());
    List<SceneInspection.Row> visible = inspection.visibleRows(session.query, session.collapsed);
    if (sceneChanged || !visible.equals(previousRows)) {
      float scroll = listScroll.getScrollY();
      for (Actor actor : rows.getChildren().toArray(Actor.class))
        if (actor instanceof Flex child) child.close();
      rows.clearChildren();
      buttons.clear();
      List<SceneInspection.Row> all = snapshot.objects();
      for (SceneInspection.Row row : visible) {
        int rowIndex = all.indexOf(row);
        boolean hasChildren = rowIndex + 1 < all.size() && all.get(rowIndex + 1).depth() > row.depth();
        Flex treeRow = rows.add(new Flex(theme, "flex-row gap-1"), "h-8 w-full");
        if (hasChildren) {
          TextButton collapse = theme.button(session.collapsed.contains(row.id()) ? "+" : "-",
              "text-sm text-white bg-slate-700 rounded", () -> {
                if (!closed && currentScene.get() == source)
                  setCollapsed(row.id(), !session.collapsed.contains(row.id()));
              });
          collapse.setName("runtime-collapse-" + row.id());
          treeRow.add(collapse, "w-6");
        }
        String indent = "  ".repeat(Math.min(row.depth(), 12));
        String text =
            indent
                + "#"
                + row.id()
                + " "
                + row.name()
                + (row.active() ? "" : " [off]")
                + " ("
                + row.components()
                + ")";
        TextButton button =
            treeRow.add(
                theme.button(
                    text,
                    "text-sm text-slate-200 bg-slate-800 rounded",
                    () -> {
                      // Rebind before selecting: ids can repeat in a replacement scene.
                      if (closed) return;
                      if (currentScene.get() != source) {
                        refresh();
                        return;
                      }
                      inspection.select(row.id());
                      refresh();
                    }),
                "flex-1");
        button.setName("runtime-object-" + row.id());
        button.getLabel().setEllipsis(true);
        button.getLabel().setAlignment(com.badlogic.gdx.utils.Align.left);
        buttons.put(row.id(), button);
      }
      listScroll.invalidateHierarchy();
      listScroll.validate();
      listScroll.setScrollY(scroll);
      previousRows = visible;
      previousSelection = null;
    }
    for (var entry : buttons.entrySet()) {
      boolean selected = Objects.equals(entry.getKey(), snapshot.selectedId());
      if (!Objects.equals(previousSelection, snapshot.selectedId()) || selected)
        theme.updateButton(
            entry.getValue(),
            selected
                ? "text-sm text-white bg-blue-600 rounded"
                : "text-sm text-slate-200 bg-slate-800 rounded");
    }
    previousSelection = snapshot.selectedId();
    String detailText = String.join("\n", snapshot.details());
    GameObject selected = selectedObject(source, snapshot.selectedId());
    if (sceneChanged || selected != editorObject) {
      drag = null;
      editorObject = selected;
      exportReportText = null;
      transformField.setText(TRANSFORM_FIELDS.getFirst());
      loadCurrentTransformValue();
    }
    detail.setText(exportReportText == null ? detailText : exportReportText);
    detailScroll.invalidateHierarchy();
    boolean editable = snapshot.selectedId() != null && source != null && !source.isDisposed();
    transformField.setDisabled(!editable);
    transformValue.setDisabled(!editable);
    boolean has3D = selected != null && selected.getComponent(Transform3D.class) != null;
    String currentField = transformField.getText().toString();
    if (!has3D && currentField.startsWith("3D.")) {
      transformField.setText("2D.POSITION_X");
      loadCurrentTransformValue();
    }
    transformField.setDisabled(
        !editable || (!has3D && transformField.getText().toString().startsWith("3D.")));
    transformValue.setDisabled(
        !editable || (!has3D && transformField.getText().toString().startsWith("3D.")));
  }

  /** Select this exact live object instance in the current supplier scene. */
  public boolean selectObject(GameObject object) {
    requireOpen();
    Game scene = currentScene.get();
    if (scene != displayedScene || object == null || object.game != scene || object.isDestroyed()
        || scene == null || scene.isDisposed() || !scene.getObjects().contains(object)) {
      refresh();
      return false;
    }
    inspection.select(object.id);
    refresh();
    return true;
  }

  /** Live current selection, never an object from a replaced/disposed supplier scene. */
  public GameObject selectedObject() {
    if (closed || currentScene.get() != displayedScene) return null;
    return selectedObject(displayedScene, inspection.capture().selectedId());
  }

  public void setSearchQuery(String query) {
    requireOpen();
    refresh();
    search.setText(query == null ? "" : query);
  }

  public String searchQuery() { return session.query; }

  public void setCollapsed(int id, boolean value) {
    requireOpen();
    if (currentScene.get() != displayedScene) { refresh(); return; }
    if (value) session.collapsed.add(id); else session.collapsed.remove(id);
    previousRows = List.of();
    refresh();
  }

  public boolean isCollapsed(int id) { return session.collapsed.contains(id); }

  /** Host callback for cursor/input ownership on every visibility transition, including X. */
  public void setVisibilityListener(java.util.function.Consumer<Boolean> listener) {
    visibilityChanged = Objects.requireNonNull(listener);
  }

  public void setFocusSelectedHandler(java.util.function.Function<GameObject, String> handler) {
    focusSelected = Objects.requireNonNull(handler);
  }

  /** Null handler result means success; a refusal is shown in the panel. */
  public boolean focusSelected() {
    requireOpen();
    GameObject object = selectedObject();
    String refusal = object == null ? "Select a live object first" : focusSelected.apply(object);
    editStatus.setText(refusal == null ? "Debug camera focused" : refusal);
    return refusal == null;
  }

  private static GameObject selectedObject(Game scene, Integer id) {
    if (scene == null || id == null || scene.isDisposed()) return null;
    for (GameObject object : scene.getObjects()) if (object.id == id && !object.isDestroyed()) return object;
    return null;
  }

  private void applyTransform() {
    if (closed) return;
    if (currentScene.get() != displayedScene) {
      refresh();
      editStatus.setText("Scene changed; select the object again");
      return;
    }
    try {
      String[] selection =
          transformField.getText().toString().toUpperCase(Locale.ROOT).split("\\.", 2);
      float value = Float.parseFloat(transformValue.getText().trim());
      if (!Float.isFinite(value))
        throw new NumberFormatException("Transform values must be finite");
      if (selection.length != 2) throw new IllegalArgumentException("Use 2D.FIELD or 3D.FIELD");
      switch (selection[0]) {
        case "2D" -> inspection.edit(SceneInspection.Transform2DField.valueOf(selection[1]), value);
        case "3D" -> inspection.edit(SceneInspection.Transform3DField.valueOf(selection[1]), value);
        default -> throw new IllegalArgumentException("Use 2D.FIELD or 3D.FIELD");
      }
      editStatus.setText("Transform applied");
      loadCurrentTransformValue();
      refresh();
    } catch (IllegalArgumentException | IllegalStateException failure) {
      editStatus.setText(
          failure.getMessage() == null ? "Invalid Transform3D edit" : failure.getMessage());
    }
  }

  private void nextTransformField() {
    if (closed) return;
    int index = TRANSFORM_FIELDS.indexOf(transformField.getText().toString());
    boolean has3D = editorObject != null && editorObject.getComponent(Transform3D.class) != null;
    do {
      index = (index + 1) % TRANSFORM_FIELDS.size();
    } while (!has3D && TRANSFORM_FIELDS.get(index).startsWith("3D."));
    transformField.setText(TRANSFORM_FIELDS.get(index));
    loadCurrentTransformValue();
  }

  private void loadCurrentTransformValue() {
    GameObject object = editorObject;
    if (object == null) {
      transformValue.setText("0");
      return;
    }
    String[] selection = transformField.getText().toString().split("\\.", 2);
    float value = 0;
    try {
      if (selection.length == 2 && selection[0].equals("2D")) {
        value =
            switch (SceneInspection.Transform2DField.valueOf(selection[1])) {
              case POSITION_X -> object.transform.position.x;
              case POSITION_Y -> object.transform.position.y;
              case ROTATION -> object.transform.rotation;
              case SCALE_X -> object.transform.scale.x;
              case SCALE_Y -> object.transform.scale.y;
            };
      } else if (selection.length == 2 && selection[0].equals("3D")) {
        Transform3D transform = object.getComponent(Transform3D.class);
        if (transform == null) return;
        value =
            switch (SceneInspection.Transform3DField.valueOf(selection[1])) {
              case POSITION_X -> transform.position.x;
              case POSITION_Y -> transform.position.y;
              case POSITION_Z -> transform.position.z;
              case ROTATION_X -> transform.rotation.x;
              case ROTATION_Y -> transform.rotation.y;
              case ROTATION_Z -> transform.rotation.z;
              case ROTATION_W -> transform.rotation.w;
              case SCALE_X -> transform.scale.x;
              case SCALE_Y -> transform.scale.y;
              case SCALE_Z -> transform.scale.z;
            };
      }
      transformValue.setText(Float.toString(value));
    } catch (IllegalArgumentException ignored) {
      transformValue.setText("0");
    }
  }

  private void undo() {
    if (closed) return;
    if (currentScene.get() != displayedScene) {
      refresh();
      editStatus.setText("Scene changed; edit history cleared");
      return;
    }
    editStatus.setText(inspection.undo() ? "Undo applied" : "Nothing to undo / edit became stale");
    loadCurrentTransformValue();
    refresh();
  }

  private void redo() {
    if (closed) return;
    if (currentScene.get() != displayedScene) {
      refresh();
      editStatus.setText("Scene changed; edit history cleared");
      return;
    }
    editStatus.setText(inspection.redo() ? "Redo applied" : "Nothing to redo / edit became stale");
    loadCurrentTransformValue();
    refresh();
  }

  private void installScrollFocus(ScrollPane pane) {
    pane.addListener(
        new InputListener() {
          @Override
          public void enter(InputEvent event, float x, float y, int pointer, Actor from) {
            if (pointer == -1) stage.setScrollFocus(pane);
          }

          @Override
          public void exit(InputEvent event, float x, float y, int pointer, Actor to) {
            if (pointer == -1
                && (to == null || !to.isDescendantOf(pane))
                && stage.getScrollFocus() == pane) stage.setScrollFocus(null);
          }
        });
  }

  private void clearFocus() {
    Actor keyboard = stage.getKeyboardFocus(), scroll = stage.getScrollFocus();
    if (keyboard != null && keyboard.isDescendantOf(root)) stage.setKeyboardFocus(null);
    if (scroll != null && scroll.isDescendantOf(root)) stage.setScrollFocus(null);
  }

  private void requireOpen() {
    if (closed) throw new IllegalStateException("Inspector closed");
  }

  @Override
  public void close() {
    if (closed) return;
    clearFocus();
    closed = true;
    drag = null;
    gizmoRenderer.dispose();
    root.remove();
    rows.close();
    root.close();
    buttons.clear();
    inspection.setScene(null);
    displayedScene = null;
    editorObject = null;
    previousRows = List.of();
  }
}
