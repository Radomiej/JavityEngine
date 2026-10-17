package org.javity.demo;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import org.javity.core.*;
import org.javity.core.Game;
import org.javity.physics2d.*;
import org.javity.render.*;
import org.javity.ui.flex.*;
import org.javity.ui.flex.Rectangle;
import org.javity.ui.inspector.RuntimeInspector;
import org.javity.ui.inspector.SceneInspection;
import org.javity.serialization.SceneJson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** New component runtime alongside the imported legacy backend (--legacy). */
final class ModernDemo extends ApplicationAdapter {
  private final boolean smoke;
  private final boolean inspectAtStartup;
  private final SceneJson exportSchema;
  private final Path exportDestination;
  private Path inspectorSmokeExportDirectory;
  private Path inspectorSmokeExportTarget;
  private RuntimeInspector inspector;
  private InputMultiplexer input;
  private GameObject inspectorTransient;
  private GameObject gizmoParent, gizmoChild;
  private GameObject rejectionParent, rejectionChild, scaleProbe;
  private Transform3D gizmoOriginalParent;
  private SceneInspection.Transform3DState gizmoOriginalState;
  private ScreenViewport inspectorScreenViewport;
  private Vector2 stagePriorityClick;
  private Component inspectorTransientComponent;
  private Game game;
  private Camera3D runtimeCamera3D;
  private GdxRenderer renderer;
  private Stage stage;
  private UiTheme theme;
  private Flex root;
  private TextButton pause;
  private Texture texture;
  private Model model;
  private Physics2D physics;
  private GameObject ball, floor, trigger, smokeCamera;
  private Texture solidTexture;
  private Rectangle rectangle;
  private Label contactStatus;
  private Vector2 pausedPosition;
  private Vector2 gizmoStart, gizmoAxis;
  private SceneInspection.Transform3DState gizmoBaselineState, gizmoAfterState;
  private int groundContacts, triggerContacts;
  private int frames, worldUpdates, uiUpdates;

  ModernDemo(boolean smoke) {
    this(smoke, false);
  }

  ModernDemo(boolean smoke, boolean inspectAtStartup) {
    this(smoke, inspectAtStartup, null, null);
  }

  ModernDemo(
      boolean smoke, boolean inspectAtStartup, SceneJson exportSchema, Path exportDestination) {
    this.smoke = smoke;
    this.inspectAtStartup = inspectAtStartup;
    this.exportSchema = exportSchema;
    this.exportDestination = exportDestination;
  }

  @Override
  public void create() {
    renderer = new GdxRenderer();
    stage = new Stage(new ScreenViewport());
    inspectorScreenViewport = (ScreenViewport) stage.getViewport();
    Gdx.input.setInputProcessor(stage);
    game = new Game();
    physics = game.addSystem(new Physics2D(new Vector2(0, -980), 100));
    runtimeCamera3D = game.createObject("camera").addComponent(new Camera3D(60));
    var camera = runtimeCamera3D;
    camera.camera.position.set(0, 1, 5);
    camera.camera.lookAt(0, 0, 0);
    model = renderer.loadModel("models/cube.obj");
    model.materials.get(0).set(ColorAttribute.createDiffuse(Color.valueOf("10b981")));
    GameObject cube = game.createObject("cube").addTag("model");
    Transform3D transform = cube.addComponent(new Transform3D());
    transform.position.set(1.4f, 0, 0);
    cube.addComponent(new ModelRenderer(model));
    cube.addComponent(
        new Component() {
          public void onUpdate(float delta) {
            if (!(inspectAtStartup && frames >= 20 && frames <= 25))
              transform.rotation.mulLeft(new Quaternion(Vector3.Y, delta * 35));
            worldUpdates++;
          }
        });
    Pixmap image = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
    image.setColor(Color.valueOf("22d3ee"));
    image.fillCircle(32, 32, 28);
    image.setColor(Color.WHITE);
    image.fillCircle(25, 40, 5);
    texture = new Texture(image);
    image.dispose();
    ball = game.createObject("ball").setPosition(Gdx.graphics.getWidth() * .7f, 290);
    ball.addComponent(new SpriteRenderer2D(new TextureRegion(texture)));
    ball.addComponent(new RigidBody2D().setFixedRotation(true));
    ball.addComponent(new CircleCollider2D(28).setRestitution(.2f));
    ball.addComponent(new ContactMonitor());
    Pixmap pixel = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
    pixel.setColor(Color.WHITE);
    pixel.fill();
    solidTexture = new Texture(pixel);
    pixel.dispose();
    floor = game.createObject("floor").addTag("floor");
    floor.addComponent(new BoxCollider2D(360, 18));
    SpriteRenderer2D floorSprite =
        floor.addComponent(new SpriteRenderer2D(new TextureRegion(solidTexture)));
    floorSprite.width = 360;
    floorSprite.height = 18;
    floorSprite.tint.set(UtilityStyles.color("slate-700"));
    trigger = game.createObject("trigger");
    trigger.addComponent(new BoxCollider2D(360, 40).setTrigger(true));
    Component ui =
        game.createObject("ui-clock")
            .addComponent(
                new Component() {
                  public void onUpdate(float delta) {
                    uiUpdates++;
                  }
                });
    ui.updateMode = Component.UpdateMode.UI;
    createUi();
    SceneJson inspectorSchema = exportSchema;
    Path inspectorDestination = exportDestination;
    if (smoke && inspectAtStartup && inspectorDestination == null) {
      try {
        inspectorSmokeExportDirectory = Files.createTempDirectory("javity-runtime-export-smoke");
        inspectorSmokeExportTarget = inspectorSmokeExportDirectory.resolve("runtime-scene.json");
        inspectorSchema = SceneJson.standard();
        inspectorDestination = inspectorSmokeExportTarget;
      } catch (IOException failure) {
        throw new IllegalStateException("Could not prepare runtime export smoke destination", failure);
      }
    }
    inspector = new RuntimeInspector(stage, theme, () -> game, inspectorSchema, inspectorDestination);
    inspector.setGizmoCameras(runtimeCamera3D.camera, renderer.camera2D);
    inspector.setVisible(inspectAtStartup);
    input =
        new InputMultiplexer(inspector.inputProcessor(), stage, inspector.gizmoInputProcessor());
    Gdx.input.setInputProcessor(input);
    game.start();
    resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
  }

  private void createUi() {
    theme = new UiTheme();
    root = new Flex(theme, "flex-col p-8 gap-6");
    root.setFillParent(true);
    stage.addActor(root);
    Flex header = root.add(new Flex(theme, "flex-row items-center justify-between"), "h-16");
    header.add(theme.label("JAVITY", "text-2xl text-cyan-400"), "");
    header.add(theme.label("Java 25  /  2D + 3D  /  Yoga", "text-slate-400"), "");
    Flex body = root.add(new Flex(theme, "flex-row gap-6"), "flex-1");
    Flex panel =
        body.add(
            new Flex(theme, "flex-col p-6 gap-4 bg-slate-800 rounded-xl border-2 border-slate-700"),
            "w-80");
    panel.add(theme.label("A simpler engine", "text-xl text-white"), "");
    panel.add(theme.label("Scene-local component indexes", "text-slate-400"), "");
    panel.add(theme.label("2D sprites + 3D models", "text-emerald-500"), "");
    panel.add(theme.label("Real Yoga / Flexbox layout", "text-cyan-400"), "");
    panel.add(theme.label("Fixed-step physics + rounded UI", "text-slate-400"), "");
    Label status = panel.add(theme.label("World: running", "text-emerald-500"), "");
    contactStatus = panel.add(theme.label("Contacts: 0 / triggers: 0", "text-slate-400"), "");
    rectangle =
        panel.add(
            theme.rectangle("bg-slate-900 rounded-lg border-2 border-cyan-400"), "w-full h-8");
    pause =
        panel.add(
            theme.button(
                "Pause / resume",
                "text-white bg-blue-600 rounded-lg border border-cyan-400",
                () -> {
                  if (game.isPaused()) game.resume();
                  else game.pause();
                  status.setText(game.isPaused() ? "World: paused / UI active" : "World: running");
                }),
            "h-12 w-full");
    panel.add(
        theme.button(
            "Reset ball",
            "text-white bg-slate-700 rounded-lg border border-slate-400",
            () -> {
              ball.setPosition(Gdx.graphics.getWidth() * .7f, 290);
              ball.getComponent(RigidBody2D.class).setLinearVelocity(0, 0);
            }),
        "h-10 w-full");
    body.add(new Flex("flex-col"), "flex-1");
    root.add(
        theme.label(
            "Resize the window. F1 opens the runtime scene inspector.", "text-sm text-slate-400"),
        "h-8");
  }

  @Override
  public void render() {
    Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    Gdx.gl.glClearColor(.043f, .071f, .125f, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    game.step(Gdx.graphics.getDeltaTime());
    renderer.render(game);
    physics.drawDebug(renderer.camera2D);
    inspector.drawGizmos();
    inspector.update(game.unscaledDeltaTime);
    stage.act(game.unscaledDeltaTime);
    stage.draw();
    frames++;
    if (smoke) verifySmoke();
  }

  private void clickPause() {
    Vector2 screen =
        stage.stageToScreenCoordinates(
            pause.localToStageCoordinates(
                new Vector2(pause.getWidth() / 2, pause.getHeight() / 2)));
    stage.touchDown(Math.round(screen.x), Math.round(screen.y), 0, Input.Buttons.LEFT);
    stage.touchUp(Math.round(screen.x), Math.round(screen.y), 0, Input.Buttons.LEFT);
  }

  private void verifySmoke() {
    if (inspectAtStartup) verifyInspectorSmoke();
    if (frames == 10) {
      smokeCamera = game.createObject("smoke camera").setPosition(470, 310);
      smokeCamera.transform.rotation = .2f;
      Camera2D camera = smokeCamera.addComponent(new Camera2D());
      camera.setZoom(1.2f);
      camera.setPixelsPerUnit(1.5f);
    }
    if (frames == 15) {
      Camera2D camera = smokeCamera.getComponent(Camera2D.class);
      Vector2 expected = camera.worldToScreen(700, 300);
      Vector3 actual =
          renderer.camera2D.project(
              new Vector3(700, 300, 0), 0, 0, camera.getWidth(), camera.getHeight());
      if (Math.abs(expected.x - actual.x) > .01f
          || Math.abs(expected.y - (camera.getHeight() - actual.y)) > .01f)
        throw new IllegalStateException("Camera2D native projection mismatch");
    }
    if (frames == 20) smokeCamera.destroy();
    if (frames == 30) {
      clickPause();
      pausedPosition = ball.transform.position.cpy();
      if (!game.isPaused()) throw new IllegalStateException("UI click did not pause game");
    }
    if (frames == 60) {
      if (!pausedPosition.equals(ball.transform.position))
        throw new IllegalStateException("Physics advanced during pause");
      clickPause();
      if (game.isPaused()) throw new IllegalStateException("UI click did not resume game");
    }
    if (frames == 90) Gdx.graphics.setWindowedMode(1000, 680);
    if (frames == 120) {
      if (worldUpdates < 80 || uiUpdates != 120 || root.getWidth() != stage.getWidth())
        throw new IllegalStateException("Lifecycle, pause or resize smoke failed");
      if (groundContacts == 0 || triggerContacts == 0 || physics.getBodyCount() != 3)
        throw new IllegalStateException("Physics contacts or bodies were not initialized");
      Pixmap screenshot =
          Pixmap.createFromFrameBuffer(
              0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
      verifyRectanglePixels(screenshot);
      PixmapIO.writePNG(Gdx.files.local("smoke.png"), screenshot, -1, true);
      if (inspectAtStartup) {
        PixmapIO.writePNG(Gdx.files.local("docs/demo-runtime-inspector.png"), screenshot, -1, true);
        Gdx.files
            .local("docs/runtime-inspector-verification.json")
            .writeString(
                "{\"status\":\"PASS\",\"frames\":120,\"selectionByStageClick\":true,\"liveObjectsComponents\":true,\"transform2DEditByStageClick\":true,\"transform3DEditByStageClick\":true,\"gizmoParentedLocalAxisDrag\":true,\"gizmoSingleUndoRedo\":true,\"supplierReplacementSameId\":true,\"physicsOwnedHandleRejected\":true,\"worldScale2DRotatedRepresentableAndRejected\":true,\"zoomed2DProjection\":true,\"singularParentPreviewCancelled\":true,\"scaledViewportStagePriority\":true,\"cameraResizeFocusDragCancellation\":true,\"detailsViewportAt1000x680And900x600\":true,\"fixtureRestoredBeforeScreenshot\":true,\"undoRedo\":true,\"invalidInputRejected\":true,\"F1AndFocus\":true,\"resize\":true,\"readOnly\":false}\n",
                false);
        Gdx.app.log(
            "RuntimeInspector",
            "PASS: native selection, gizmo drag/undo/redo, failure paths, supplier replacement,"
                + " transform edits, lifecycle, F1/focus, resize");
      }
      screenshot.dispose();
      Gdx.app.log(
          "JavitySmoke",
          "PASS: 120 frames, 2D + 3D + Camera2D, Yoga rounded/border/Rectangle, Box2D,"
              + " pause/resume, resize; WORLD="
              + worldUpdates
              + ", UI="
              + uiUpdates
              + ", contacts="
              + groundContacts
              + ", triggers="
              + triggerContacts);
      Gdx.app.exit();
    }
  }

  private void verifyInspectorSmoke() {
    GameObject cube = game.getObjectsWithTag("model").getFirst();
    if (frames == 4 && inspectorSmokeExportTarget != null) {
      try {
        Files.writeString(inspectorSmokeExportTarget, "preserve on blocked export");
      } catch (IOException failure) {
        throw new AssertionError("Could not prepare runtime export failure fixture", failure);
      }
      clickInspectorActor("runtime-export-button");
    }
    if (frames == 5 && inspectorSmokeExportTarget != null) {
      try {
        if (!Files.readString(inspectorSmokeExportTarget).equals("preserve on blocked export"))
          throw new AssertionError("Blocked full-scene export changed the existing target");
        Label details = stage.getRoot().findActor("runtime-inspector-details");
        if (!details.getText().toString().contains("Runtime snapshot blocked")
            || !details.getText().toString().contains("ModelRenderer"))
          throw new AssertionError("Inspector did not display aggregated export issues");
        GameObject selectedFixture = game.createObject("serializable selected fixture");
        selectedFixture.addComponent(new Transform3D());
        inspector.refresh();
        if (!inspector.selectObject(selectedFixture))
          throw new AssertionError("Inspector rejected an exact live runtime object for selection");
        clickInspectorActor("runtime-export-selection-button");
        if (!((Label) stage.getRoot().findActor("runtime-inspector-details"))
                .getText().toString().contains("Runtime snapshot saved")
            || !Files.readString(inspectorSmokeExportTarget).contains("serializable selected fixture"))
          throw new AssertionError(
              "Inspector selected-object export did not save a runtime snapshot; details="
                  + ((Label) stage.getRoot().findActor("runtime-inspector-details")).getText()
                  + ", target=" + Files.readString(inspectorSmokeExportTarget));
        try (Game restoredSelection = new Game()) {
          if (SceneJson.standard().read(restoredSelection,
                  Files.readString(inspectorSmokeExportTarget)).size() != 1)
            throw new AssertionError("Inspector selected-object export did not round-trip");
        }
        GameObject parent = game.createObject("export fixture parent");
        Transform3D parentTransform = parent.addComponent(new Transform3D());
        GameObject child = game.createObject("export fixture child");
        Transform3D childTransform = child.addComponent(new Transform3D());
        childTransform.setParent(parentTransform);
        SceneJson schema = SceneJson.standard();
        var report = schema.prepareSelection(java.util.List.of(parent, child));
        if (!report.isValid())
          throw new AssertionError("Serializable runtime selection was rejected: " + report.issues());
        schema.save(report, inspectorSmokeExportTarget);
        try (Game restored = new Game()) {
          var copies = schema.read(restored, Files.readString(inspectorSmokeExportTarget));
          if (copies.size() != 2
              || copies.get(1).getComponent(Transform3D.class).getParent()
                  != copies.get(0).getComponent(Transform3D.class))
            throw new AssertionError("Runtime export selection lost its hierarchy");
        }
        parent.destroy();
        child.destroy();
        selectedFixture.destroy();
      } catch (IOException failure) {
        throw new AssertionError("Runtime export smoke could not read/write its snapshot", failure);
      }
    }
    if (frames == 5) {
      inspector.refresh();
      com.badlogic.gdx.scenes.scene2d.Actor overlay =
          stage.getRoot().findActor("runtime-inspector");
      com.badlogic.gdx.scenes.scene2d.Actor worldHit =
          stage.hit(stage.getWidth() * .5f, stage.getHeight() * .5f, true);
      if (worldHit != null && worldHit.isDescendantOf(overlay))
        throw new AssertionError("Empty inspector overlay blocked world input");
      com.badlogic.gdx.scenes.scene2d.Actor button =
          stage.getRoot().findActor("runtime-object-" + cube.id);
      if (button == null) throw new AssertionError("Inspector object row missing");
      Vector2 screen =
          stage.stageToScreenCoordinates(
              button.localToStageCoordinates(
                  new Vector2(button.getWidth() / 2, button.getHeight() / 2)));
      stage.touchDown(Math.round(screen.x), Math.round(screen.y), 0, Input.Buttons.LEFT);
      stage.touchUp(Math.round(screen.x), Math.round(screen.y), 0, Input.Buttons.LEFT);
      Label details = stage.getRoot().findActor("runtime-inspector-details");
      if (!details.getText().toString().contains("ModelRenderer")
          || !details.getText().toString().contains("Transform3D"))
        throw new AssertionError("Inspector Stage click did not select model");
    }
    if (frames == 6) {
      inspectorTransient = game.createObject("inspector transient");
      inspectorTransient
          .addComponent(new Transform3D())
          .setParent(cube.getComponent(Transform3D.class));
      inspector.refresh();
      if (stage.getRoot().findActor("runtime-object-" + inspectorTransient.id) == null)
        throw new AssertionError("Inspector missed new object");
    }
    if (frames == 7) {
      inspectorTransient.destroy();
      inspector.refresh();
      if (stage.getRoot().findActor("runtime-object-" + inspectorTransient.id) != null)
        throw new AssertionError("Inspector retained destroyed object");
    }
    if (frames == 8) {
      stage.setKeyboardFocus(stage.getRoot().findActor("runtime-inspector-details"));
      stage.setScrollFocus(stage.getRoot().findActor("runtime-inspector-panel"));
      Gdx.input.getInputProcessor().keyDown(Input.Keys.F1);
      if (inspector.isVisible()
          || stage.getKeyboardFocus() != null
          || stage.getScrollFocus() != null)
        throw new AssertionError("Inspector hide retained UI focus");
    }
    if (frames == 9) {
      Gdx.input.getInputProcessor().keyDown(Input.Keys.F1);
      if (!inspector.isVisible()) throw new AssertionError("F1 did not restore inspector");
    }
    if (frames == 10) {
      TextField value = stage.getRoot().findActor("runtime-transform-value");
      TextButton property = stage.getRoot().findActor("runtime-transform-field");
      if (value == null
          || property == null
          || !property.getText().toString().equals("2D.POSITION_X"))
        throw new AssertionError(
            "Inspector transform controls missing or default property changed");
      value.setText("42.5");
      clickInspectorActor("runtime-transform-apply");
      if (Math.abs(cube.transform.position.x - 42.5f) > .001f)
        throw new AssertionError("Inspector did not apply transform values");
    }
    if (frames == 11) {
      clickInspectorActor("runtime-transform-undo");
      if (Math.abs(cube.transform.position.x) > .001f)
        throw new AssertionError("Inspector undo did not restore transform");
    }
    if (frames == 12) {
      clickInspectorActor("runtime-transform-redo");
      if (Math.abs(cube.transform.position.x - 42.5f) > .001f)
        throw new AssertionError("Inspector redo did not restore edit");
    }
    if (frames == 13) {
      TextField value = stage.getRoot().findActor("runtime-transform-value");
      value.setText("NaN");
      clickInspectorActor("runtime-transform-apply");
      if (Math.abs(cube.transform.position.x - 42.5f) > .001f)
        throw new AssertionError("Invalid inspector input changed transform");
      value.setText("42.5");
    }
    if (frames == 14) {
      TextButton property = stage.getRoot().findActor("runtime-transform-field");
      for (int i = 0; i < 15 && !property.getText().toString().equals("3D.POSITION_Z"); i++)
        clickInspectorActor("runtime-transform-field");
      if (!property.getText().toString().equals("3D.POSITION_Z"))
        throw new AssertionError("Inspector transform property selector did not cycle");
      TextField value = stage.getRoot().findActor("runtime-transform-value");
      value.setText("0.5");
      clickInspectorActor("runtime-transform-apply");
      if (Math.abs(cube.getComponent(Transform3D.class).position.z - .5f) > .001f)
        throw new AssertionError("Inspector did not apply Transform3D property");
    }
    if (frames == 15) {
      clickInspectorActor("runtime-transform-undo");
      if (Math.abs(cube.getComponent(Transform3D.class).position.z) > .001f)
        throw new AssertionError("Inspector did not undo Transform3D edit");
    }
    if (frames == 16) {
      clickInspectorActor("runtime-transform-redo");
      if (Math.abs(cube.getComponent(Transform3D.class).position.z - .5f) > .001f)
        throw new AssertionError("Inspector did not redo Transform3D edit");
    }
    if (frames == 20) {
      game.pause();
      root.setVisible(false);
      gizmoParent = game.createObject("gizmo parent");
      Transform3D parentTransform = gizmoParent.addComponent(new Transform3D());
      parentTransform.rotation.setFromAxis(Vector3.Z, 90);
      parentTransform.scale.set(2, 1, 1);
      gizmoChild = game.getObjectsWithTag("model").getFirst();
      Transform3D childTransform = gizmoChild.getComponent(Transform3D.class);
      gizmoOriginalParent = childTransform.getParent();
      gizmoOriginalState = transformState(childTransform);
      childTransform.setParent(parentTransform);
      inspector.refresh();
      ScrollPane objectList = stage.getRoot().findActor("runtime-object-list");
      objectList.setScrollY(objectList.getMaxY());
      objectList.updateVisualScroll();
    }
    if (frames == 21) {
      ScrollPane objectList = stage.getRoot().findActor("runtime-object-list");
      com.badlogic.gdx.scenes.scene2d.Actor lastRow =
          stage.getRoot().findActor("runtime-object-" + gizmoChild.id);
      Vector2 lastCenter =
          lastRow.localToStageCoordinates(
              new Vector2(lastRow.getWidth() / 2, lastRow.getHeight() / 2));
      com.badlogic.gdx.scenes.scene2d.Actor hit = stage.hit(lastCenter.x, lastCenter.y, true);
      if (hit == null || !hit.isDescendantOf(lastRow))
        throw new AssertionError(
            "Last runtime object row is clipped after scrolling to list end: " + hit);
      clickInspectorActor("runtime-object-" + gizmoChild.id);
      Label details = stage.getRoot().findActor("runtime-inspector-details");
      if (!details.getText().toString().contains("cube"))
        throw new AssertionError("Native Stage could not select the last runtime object row");
      objectList.setScrollY(0);
      objectList.updateVisualScroll();
    }
    if (frames == 22) beginNativeGizmoDrag(gizmoChild);
    if (frames == 23) finishNativeGizmoDrag(gizmoChild);
    if (frames == 24) {
      clickInspectorActor("runtime-transform-undo");
      if (!transformState(gizmoChild.getComponent(Transform3D.class)).equals(gizmoBaselineState))
        throw new AssertionError(
            "One undo did not restore the complete gizmo drag baseline: expected "
                + gizmoBaselineState
                + " got "
                + transformState(gizmoChild.getComponent(Transform3D.class))
                + " afterDrag="
                + gizmoAfterState
                + " canUndo="
                + inspector.canUndo()
                + " status="
                + ((Label) stage.getRoot().findActor("runtime-edit-status")).getText());
    }
    if (frames == 25) {
      clickInspectorActor("runtime-transform-redo");
      if (!transformState(gizmoChild.getComponent(Transform3D.class)).equals(gizmoAfterState))
        throw new AssertionError("One redo did not restore the complete gizmo drag");
    }
    if (frames == 26) {
      Game original = game;
      Game replacement = new Game();
      try {
        replacement.createObject("camera id placeholder");
        GameObject sameId = replacement.createObject("replacement with reused ID");
        if (sameId.id != cube.id)
          throw new AssertionError("Native scene fixture did not reuse the selected ID");
        game = replacement;
        inspector.refresh();
        Label details = stage.getRoot().findActor("runtime-inspector-details");
        if (!details.getText().toString().contains("Select a GameObject"))
          throw new AssertionError(
              "Runtime inspector leaked selection across supplier scene replacement");
      } finally {
        game = original;
        inspector.refresh();
        replacement.close();
      }
      ScrollPane objectList = stage.getRoot().findActor("runtime-object-list");
      objectList.setScrollY(objectList.getMaxY());
      objectList.updateVisualScroll();
    }
    if (frames == 27) {
      clickInspectorActor("runtime-object-" + cube.id);
      Label details = stage.getRoot().findActor("runtime-inspector-details");
      if (!details.getText().toString().contains("ModelRenderer")
          || !details.getText().toString().contains("Transform3D"))
        throw new AssertionError(
            "Native Stage could not reselect the last object after replacement");
    }
    if (frames == 28) {
      verifyDetailsViewport(1000, 680);
      // Preserve the panel's logical authoring size while exercising a 2:1 Stage viewport.
      FitViewport scaledViewport = new FitViewport(2000, 1360);
      scaledViewport.update(
          Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight(), true);
      stage.setViewport(scaledViewport);
      stage.getRoot().setSize(scaledViewport.getWorldWidth(), scaledViewport.getWorldHeight());
      Flex inspectorRoot = stage.getRoot().findActor("runtime-inspector");
      inspectorRoot.setSize(scaledViewport.getWorldWidth(), scaledViewport.getWorldHeight());
      inspectorRoot.invalidateHierarchy();
      inspectorRoot.validate();
      stage.draw();
      TextButton mode = stage.getRoot().findActor("runtime-gizmo-mode");
      stagePriorityClick =
          stage.stageToScreenCoordinates(
              mode.localToStageCoordinates(new Vector2(mode.getWidth() / 2, mode.getHeight() / 2)));
      com.badlogic.gdx.math.collision.Ray pickRay =
          runtimeCamera3D.camera.getPickRay(stagePriorityClick.x, stagePriorityClick.y);
      Vector3 target = pickRay.origin.cpy().mulAdd(pickRay.direction, 6f);
      Transform3D child = gizmoChild.getComponent(Transform3D.class);
      child.position.set(
          target.mul(gizmoParent.getComponent(Transform3D.class).worldMatrix().cpy().inv()));
      child.rotation.idt();
      child.scale.set(1, 1, 1);
    }
    if (frames == 29) {
      TextButton mode = stage.getRoot().findActor("runtime-gizmo-mode");
      Vector2 stageClick = stage.screenToStageCoordinates(stagePriorityClick.cpy());
      com.badlogic.gdx.scenes.scene2d.Actor stageHit = stage.hit(stageClick.x, stageClick.y, true);
      if (stageHit == null || !stageHit.isDescendantOf(mode))
        throw new AssertionError(
            "Scaled Stage coordinate roundtrip missed mode button: "
                + stageHit
                + " point="
                + stageClick
                + " screen="
                + stagePriorityClick
                + " button="
                + mode.getX()
                + ","
                + mode.getY()
                + " "
                + mode.getWidth()
                + "x"
                + mode.getHeight());
      int clickX = Math.round(stagePriorityClick.x), clickY = Math.round(stagePriorityClick.y);
      Vector2 roundedStagePoint = stage.screenToStageCoordinates(new Vector2(clickX, clickY));
      com.badlogic.gdx.scenes.scene2d.Actor roundedHit =
          stage.hit(roundedStagePoint.x, roundedStagePoint.y, true);
      if (roundedHit == null || !roundedHit.isDescendantOf(mode))
        throw new AssertionError(
            "Rounded input coordinate missed scaled Stage button: " + roundedHit);
      boolean stageDown = input.touchDown(clickX, clickY, 0, Input.Buttons.LEFT);
      boolean stageUp = input.touchUp(clickX, clickY, 0, Input.Buttons.LEFT);
      if (!mode.getText().toString().equals("GIZMO: ROTATE"))
        throw new AssertionError(
            "Scaled/letterboxed Stage did not receive click over gizmo handle: mode="
                + mode.getText()
                + " status="
                + ((Label) stage.getRoot().findActor("runtime-edit-status")).getText()
                + " stage="
                + stageDown
                + "/"
                + stageUp
                + " screen="
                + clickX
                + ","
                + clickY
                + " viewport="
                + stage.getViewport().getScreenX()
                + ","
                + stage.getViewport().getScreenY()
                + " "
                + stage.getViewport().getScreenWidth()
                + "x"
                + stage.getViewport().getScreenHeight());
      clickInputAt(stagePriorityClick);
      restoreTransform(
          gizmoChild.getComponent(Transform3D.class), gizmoOriginalState, gizmoOriginalParent);
      gizmoParent.destroy();
      gizmoParent = null;
      game.resume();
      stage.setViewport(inspectorScreenViewport);
      stage
          .getViewport()
          .update(Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight(), true);
      stage
          .getRoot()
          .setSize(stage.getViewport().getWorldWidth(), stage.getViewport().getWorldHeight());
      root.setVisible(true);
    }
    if (frames == 30) Gdx.graphics.setWindowedMode(900, 600);
    if (frames == 31) {
      verifyDetailsViewport(900, 600);
      Gdx.graphics.setWindowedMode(1000, 680);
    }
    if (frames == 33) {
      verifyDetailsViewport(1000, 680);
      verifyInspectorFailurePaths();
    }
    if (frames == 80) {
      inspectorTransientComponent = cube.addComponent(new Component() {});
      inspector.refresh();
      Label details = stage.getRoot().findActor("runtime-inspector-details");
      if (!details.getText().toString().lines().anyMatch(line -> line.startsWith("4. ")))
        throw new AssertionError("Inspector missed new component: " + details.getText());
    }
    if (frames == 81) {
      cube.removeComponent(inspectorTransientComponent);
      inspector.refresh();
      Label details = stage.getRoot().findActor("runtime-inspector-details");
      if (details.getText().toString().lines().anyMatch(line -> line.startsWith("4. ")))
        throw new AssertionError("Inspector retained removed component");
    }
  }

  private void verifyDetailsViewport(int width, int height) {
    if (Gdx.graphics.getBackBufferWidth() != width || Gdx.graphics.getBackBufferHeight() != height)
      throw new AssertionError("Native smoke did not reach viewport " + width + "x" + height);
    ((Flex) stage.getRoot().findActor("runtime-inspector")).validate();
    float visibleHeight = inspector.detailsViewportHeight();
    if (visibleHeight < 120)
      throw new AssertionError("Inspector details viewport is too short: " + visibleHeight);
    if (!((Label) stage.getRoot().findActor("runtime-inspector-details"))
        .getText()
        .toString()
        .contains("Transform3D"))
      throw new AssertionError("Selected object details/components are not populated");
    ScrollPane detailViewport = stage.getRoot().findActor("runtime-inspector-details-viewport");
    Flex panel = stage.getRoot().findActor("runtime-inspector-panel");
    if (detailViewport.getHeight() < 120
        || detailViewport.getY() < panel.getY()
        || detailViewport.getY() + detailViewport.getHeight() > panel.getY() + panel.getHeight())
      throw new AssertionError(
          "Inspector details are not visibly laid out: viewport="
              + detailViewport.getX()
              + ","
              + detailViewport.getY()
              + " "
              + detailViewport.getWidth()
              + "x"
              + detailViewport.getHeight()
              + " panel="
              + panel.getX()
              + ","
              + panel.getY()
              + " "
              + panel.getWidth()
              + "x"
              + panel.getHeight());
  }

  private void verifyInspectorFailurePaths() {
    selectObjectInInspector(ball);
    Vector2 physicsPositionOriginal = ball.transform.position.cpy();
    ball.transform.position.set(300, 320);
    Vector2 physicsBefore = ball.transform.position.cpy();
    Vector2 ballClick = null;
    boolean blocked = false;
    Vector3 ballOrigin = new Vector3(ball.transform.position, 0);
    StringBuilder ballPickTrace = new StringBuilder();
    for (int axis = 0; axis < 2 && !blocked; axis++) {
      Vector2 localAxis =
          new Vector2(axis == 0 ? 1 : 0, axis == 1 ? 1 : 0).rotateRad(ball.transform.rotation);
      Vector3 endpoint = ballOrigin.cpy().add(localAxis.x * 55, localAxis.y * 55, 0);
      Vector2 a = worldToScreen(renderer.camera2D, ballOrigin);
      Vector2 b = worldToScreen(renderer.camera2D, endpoint);
      for (float fraction : new float[] {.25f, .45f, .65f, .85f}) {
        ballClick = a.cpy().lerp(b, fraction);
        int x = Math.round(ballClick.x), y = Math.round(ballClick.y);
        ballPickTrace.append(axis).append('@').append(fraction).append(';');
        blocked = input.touchDown(x, y, 0, Input.Buttons.LEFT);
        if (blocked) break;
      }
    }
    if (!blocked || !ball.transform.position.equals(physicsBefore))
      throw new AssertionError(
          "Physics-owned handle was not rejected without mutating the body: consumed="
              + blocked
              + " click="
              + ballClick
              + " trace="
              + ballPickTrace
              + " camera="
              + renderer.camera2D.viewportWidth
              + "x"
              + renderer.camera2D.viewportHeight
              + " pos="
              + physicsBefore
              + " status="
              + ((Label) stage.getRoot().findActor("runtime-edit-status")).getText());
    if (!((Label) stage.getRoot().findActor("runtime-edit-status"))
        .getText()
        .toString()
        .contains("owns"))
      throw new AssertionError("Physics-owned handle rejection was not reported");
    input.touchUp(Math.round(ballClick.x), Math.round(ballClick.y), 0, Input.Buttons.LEFT);
    ball.transform.position.set(physicsPositionOriginal);

    scaleProbe = game.createObject("inspector scale probe");
    scaleProbe.transform.position.set(250, 320);
    scaleProbe.transform.rotation = 0;
    selectObjectInInspector(scaleProbe);
    setGizmoSpaceLocal();
    setGizmoMode("GIZMO: MOVE");
    Vector2 zoomBaseline = scaleProbe.transform.position.cpy();
    float cameraWorldWidth = renderer.camera2D.viewportWidth;
    float cameraWorldHeight = renderer.camera2D.viewportHeight;
    renderer.camera2D.viewportWidth *= 2;
    renderer.camera2D.viewportHeight *= 2;
    renderer.camera2D.update();
    drag2DWorldAxis(scaleProbe, 0);
    if (scaleProbe.transform.position.dst(zoomBaseline) < .1f)
      throw new AssertionError("Zoomed 2D full-frame camera did not project a pickable gizmo axis");
    scaleProbe.transform.position.set(zoomBaseline);
    renderer.camera2D.viewportWidth = cameraWorldWidth;
    renderer.camera2D.viewportHeight = cameraWorldHeight;
    renderer.camera2D.update();

    scaleProbe.transform.rotation = MathUtils.PI / 2;
    setGizmoSpaceWorld();
    setGizmoMode("GIZMO: SCALE");
    float beforeX = scaleProbe.transform.scale.x, beforeY = scaleProbe.transform.scale.y;
    drag2DWorldAxis(scaleProbe, 0);
    if (Math.abs(scaleProbe.transform.scale.x - beforeX) > .001f
        || scaleProbe.transform.scale.y <= beforeY)
      throw new AssertionError("World X scale at 90 degrees did not map to local Y");

    GameObject diagonalProbe = game.createObject("diagonal world-scale rejection probe");
    diagonalProbe.transform.position.set(250, 320);
    diagonalProbe.transform.rotation = MathUtils.PI / 4;
    selectObjectInInspector(diagonalProbe);
    setGizmoMode("GIZMO: SCALE");
    inspector.refresh();
    SceneInspection.Transform2DState diagonalBefore =
        new SceneInspection.Transform2DState(
            diagonalProbe.transform.position.x,
            diagonalProbe.transform.position.y,
            diagonalProbe.transform.rotation,
            diagonalProbe.transform.scale.x,
            diagonalProbe.transform.scale.y);
    boolean undoBefore = inspector.canUndo(), redoBefore = inspector.canRedo();
    Vector2 diagonalOrigin = new Vector2(diagonalProbe.transform.position);
    Vector2 diagonalStart =
        worldToScreen(renderer.camera2D, new Vector3(diagonalOrigin, 0).add(27.5f, 0, 0));
    Vector2 diagonalFinish =
        worldToScreen(renderer.camera2D, new Vector3(diagonalOrigin, 0).add(44, 0, 0));
    boolean rejected =
        input.touchDown(
            Math.round(diagonalStart.x), Math.round(diagonalStart.y), 0, Input.Buttons.LEFT);
    input.touchDragged(Math.round(diagonalFinish.x), Math.round(diagonalFinish.y), 0);
    input.touchUp(
        Math.round(diagonalFinish.x), Math.round(diagonalFinish.y), 0, Input.Buttons.LEFT);
    String diagonalStatus =
        ((Label) stage.getRoot().findActor("runtime-edit-status")).getText().toString();
    SceneInspection.Transform2DState diagonalAfter =
        new SceneInspection.Transform2DState(
            diagonalProbe.transform.position.x,
            diagonalProbe.transform.position.y,
            diagonalProbe.transform.rotation,
            diagonalProbe.transform.scale.x,
            diagonalProbe.transform.scale.y);
    if (!rejected
        || !diagonalAfter.equals(diagonalBefore)
        || !diagonalStatus.contains("shear")
        || inspector.canUndo() != undoBefore
        || inspector.canRedo() != redoBefore)
      throw new AssertionError("Unrepresentable 45-degree world scale was not rejected atomically");
    setGizmoMode("GIZMO: MOVE");
    setGizmoSpaceLocal();

    rejectionParent = game.createObject("singular-parent probe");
    Transform3D parentTransform = rejectionParent.addComponent(new Transform3D());
    rejectionChild = game.createObject("singular-child probe");
    Transform3D childTransform = rejectionChild.addComponent(new Transform3D());
    childTransform.position.set(-2, -1, 0);
    childTransform.setParent(parentTransform);
    selectObjectInInspector(rejectionChild);
    setGizmoSpaceWorld();
    SceneInspection.Transform3DState childBefore = transformState(childTransform);
    Vector2[] handle = handle3D(rejectionChild, 1);
    boolean started =
        input.touchDown(Math.round(handle[0].x), Math.round(handle[0].y), 0, Input.Buttons.LEFT);
    if (!started)
      throw new AssertionError(
          "Could not start WORLD translation preview fixture at "
              + handle[0]
              + " mode="
              + ((TextButton) stage.getRoot().findActor("runtime-gizmo-mode")).getText()
              + " space="
              + ((TextButton) stage.getRoot().findActor("runtime-gizmo-space")).getText()
              + " status="
              + ((Label) stage.getRoot().findActor("runtime-edit-status")).getText());
    parentTransform.scale.x = 0;
    inspector.drawGizmos();
    input.touchUp(Math.round(handle[0].x), Math.round(handle[0].y), 0, Input.Buttons.LEFT);
    if (!transformState(childTransform).equals(childBefore)
        || !((Label) stage.getRoot().findActor("runtime-edit-status"))
            .getText()
            .toString()
            .contains("singular"))
      throw new AssertionError("Singular WORLD parent preview escaped rejection/cancellation");

    parentTransform.scale.set(1, 1, 1);
    inspector.refresh();
    childBefore = transformState(childTransform);
    handle = handle3D(rejectionChild, 1);
    started =
        input.touchDown(Math.round(handle[0].x), Math.round(handle[0].y), 0, Input.Buttons.LEFT);
    if (!started) throw new AssertionError("Could not start cancellation fixture drag");
    boolean undoState = inspector.canUndo(), redoState = inspector.canRedo();
    inspector.onFocusLost();
    input.touchUp(Math.round(handle[1].x), Math.round(handle[1].y), 0, Input.Buttons.LEFT);
    if (!transformState(childTransform).equals(childBefore)
        || inspector.canUndo() != undoState
        || inspector.canRedo() != redoState)
      throw new AssertionError("Focus loss committed a stale drag or history entry");

    handle = handle3D(rejectionChild, 1);
    childBefore = transformState(childTransform);
    started =
        input.touchDown(Math.round(handle[0].x), Math.round(handle[0].y), 0, Input.Buttons.LEFT);
    if (!started) throw new AssertionError("Could not start camera-change cancellation fixture");
    undoState = inspector.canUndo();
    redoState = inspector.canRedo();
    Vector3 cameraPosition = runtimeCamera3D.camera.position.cpy();
    runtimeCamera3D.camera.position.add(.1f, 0, 0);
    runtimeCamera3D.camera.update();
    inspector.update(0);
    runtimeCamera3D.camera.position.set(cameraPosition);
    runtimeCamera3D.camera.update();
    input.touchUp(Math.round(handle[1].x), Math.round(handle[1].y), 0, Input.Buttons.LEFT);
    if (!transformState(childTransform).equals(childBefore)
        || inspector.canUndo() != undoState
        || inspector.canRedo() != redoState)
      throw new AssertionError("Camera motion committed a stale drag or history entry");

    handle = handle3D(rejectionChild, 1);
    childBefore = transformState(childTransform);
    started =
        input.touchDown(Math.round(handle[0].x), Math.round(handle[0].y), 0, Input.Buttons.LEFT);
    if (!started) throw new AssertionError("Could not start resize cancellation fixture");
    undoState = inspector.canUndo();
    redoState = inspector.canRedo();
    resize(900, 600);
    resize(1000, 680);
    input.touchUp(Math.round(handle[1].x), Math.round(handle[1].y), 0, Input.Buttons.LEFT);
    if (!transformState(childTransform).equals(childBefore)
        || inspector.canUndo() != undoState
        || inspector.canRedo() != redoState)
      throw new AssertionError("Viewport resize committed a stale drag or history entry");

    rejectionParent.destroy();
    rejectionChild.destroy();
    scaleProbe.destroy();
    diagonalProbe.destroy();
    inspector.refresh();
    selectObjectInInspector(game.getObjectsWithTag("model").getFirst());
  }

  private void selectObjectInInspector(GameObject object) {
    inspector.refresh();
    ScrollPane list = stage.getRoot().findActor("runtime-object-list");
    list.setScrollY(0);
    list.updateVisualScroll();
    stage.draw();
    com.badlogic.gdx.scenes.scene2d.Actor row =
        stage.getRoot().findActor("runtime-object-" + object.id);
    Vector2 point =
        row.localToStageCoordinates(new Vector2(row.getWidth() / 2, row.getHeight() / 2));
    com.badlogic.gdx.scenes.scene2d.Actor hit = stage.hit(point.x, point.y, true);
    if (hit == null || !hit.isDescendantOf(row)) {
      list.setScrollY(list.getMaxY());
      list.updateVisualScroll();
      stage.draw();
    }
    clickInspectorActor("runtime-object-" + object.id);
    stage.draw();
    Label details = stage.getRoot().findActor("runtime-inspector-details");
    if (!details.getText().toString().contains(object.name))
      throw new AssertionError("Native inspector selected the wrong object: " + details.getText());
  }

  private void setGizmoSpaceWorld() {
    TextButton button = stage.getRoot().findActor("runtime-gizmo-space");
    if (button.getText().toString().equals("LOCAL")) clickInspectorActor("runtime-gizmo-space");
  }

  private void setGizmoSpaceLocal() {
    TextButton button = stage.getRoot().findActor("runtime-gizmo-space");
    if (button.getText().toString().equals("WORLD")) clickInspectorActor("runtime-gizmo-space");
  }

  private void setGizmoMode(String mode) {
    TextButton button = stage.getRoot().findActor("runtime-gizmo-mode");
    for (int i = 0; i < 3 && !button.getText().toString().equals(mode); i++)
      clickInspectorActor("runtime-gizmo-mode");
    if (!button.getText().toString().equals(mode))
      throw new AssertionError("Could not select gizmo mode " + mode);
  }

  private void drag2DWorldAxis(GameObject object, int axis) {
    Vector2 origin = new Vector2(object.transform.position);
    Vector2 end = origin.cpy().add(axis == 0 ? 55 : 0, axis == 1 ? 55 : 0);
    Vector2 a = worldToScreen(renderer.camera2D, new Vector3(origin, 0));
    Vector2 b = worldToScreen(renderer.camera2D, new Vector3(end, 0));
    Vector2 start = a.cpy().lerp(b, .55f);
    Vector2 finish = start.cpy().mulAdd(b.cpy().sub(a), .2f);
    if (!input.touchDown(Math.round(start.x), Math.round(start.y), 0, Input.Buttons.LEFT))
      throw new AssertionError("Native 2D scale handle was not picked");
    input.touchDragged(Math.round(finish.x), Math.round(finish.y), 0);
    input.touchUp(Math.round(finish.x), Math.round(finish.y), 0, Input.Buttons.LEFT);
  }

  private Vector2[] handle3D(GameObject object, int axis) {
    Transform3D transform = object.getComponent(Transform3D.class);
    Vector3 origin = transform.worldMatrix().getTranslation(new Vector3());
    Vector3 end =
        origin.cpy().add(axis == 0 ? 1.25f : 0, axis == 1 ? 1.25f : 0, axis == 2 ? 1.25f : 0);
    Vector2 a = worldToScreen(runtimeCamera3D.camera, origin);
    Vector2 b = worldToScreen(runtimeCamera3D.camera, end);
    return new Vector2[] {a.cpy().lerp(b, .55f), a.cpy().mulAdd(b.cpy().sub(a), .75f)};
  }

  private static Vector2 worldToScreen(Camera camera, Vector3 world) {
    Vector3 screen = camera.project(world.cpy());
    return new Vector2(screen.x, Gdx.graphics.getHeight() - screen.y);
  }

  private void clickInputAt(Vector2 screen) {
    int x = Math.round(screen.x), y = Math.round(screen.y);
    Gdx.input.getInputProcessor().touchDown(x, y, 0, Input.Buttons.LEFT);
    Gdx.input.getInputProcessor().touchUp(x, y, 0, Input.Buttons.LEFT);
  }

  private void beginNativeGizmoDrag(GameObject cube) {
    Transform3D t = cube.getComponent(Transform3D.class);
    Matrix4 parentWorld = t.getParent() == null ? new Matrix4() : t.getParent().worldMatrix().cpy();
    Vector3 originWorld = new Vector3(t.position).mul(parentWorld);
    Vector3 axis = t.rotation.transform(Vector3.X.cpy());
    Vector3 worldAxis = transformVector(parentWorld, axis);
    Vector3 startWorld = originWorld.cpy().mulAdd(worldAxis, .5f);
    Vector3 endWorld = originWorld.cpy().mulAdd(worldAxis, 1.25f);
    Vector3 screenOrigin = runtimeCamera3D.camera.project(originWorld);
    Vector3 start = runtimeCamera3D.camera.project(startWorld);
    Vector3 end = runtimeCamera3D.camera.project(endWorld);
    gizmoStart = new Vector2(start.x, Gdx.graphics.getHeight() - start.y);
    Vector2 finish = new Vector2(end.x, Gdx.graphics.getHeight() - end.y);
    gizmoAxis =
        finish
            .sub(new Vector2(screenOrigin.x, Gdx.graphics.getHeight() - screenOrigin.y))
            .scl(1f / 1.25f);
    gizmoBaselineState = transformState(t);
    ((TextField) stage.getRoot().findActor("runtime-gizmo-snap")).setText("0");
    boolean began =
        Gdx.input
            .getInputProcessor()
            .touchDown(Math.round(gizmoStart.x), Math.round(gizmoStart.y), 0, Input.Buttons.LEFT);
    if (!began) throw new AssertionError("Native screen-space gizmo axis was not picked");
  }

  private static Vector3 transformVector(Matrix4 matrix, Vector3 vector) {
    float[] m = matrix.val;
    return new Vector3(
        m[0] * vector.x + m[4] * vector.y + m[8] * vector.z,
        m[1] * vector.x + m[5] * vector.y + m[9] * vector.z,
        m[2] * vector.x + m[6] * vector.y + m[10] * vector.z);
  }

  private void finishNativeGizmoDrag(GameObject cube) {
    Vector2 end = gizmoStart.cpy().mulAdd(gizmoAxis, .2f);
    Gdx.input.getInputProcessor().touchDragged(Math.round(end.x), Math.round(end.y), 0);
    Gdx.input
        .getInputProcessor()
        .touchUp(Math.round(end.x), Math.round(end.y), 0, Input.Buttons.LEFT);
    gizmoAfterState = transformState(cube.getComponent(Transform3D.class));
    Vector3 positionDelta =
        new Vector3(gizmoAfterState.x(), gizmoAfterState.y(), gizmoAfterState.z())
            .sub(gizmoBaselineState.x(), gizmoBaselineState.y(), gizmoBaselineState.z());
    if (positionDelta.len() < .1f)
      throw new AssertionError(
          "Native gizmo drag did not translate along the picked local axis: delta="
              + positionDelta
              + " status="
              + ((Label) stage.getRoot().findActor("runtime-edit-status")).getText());
  }

  private static SceneInspection.Transform3DState transformState(Transform3D transform) {
    return new SceneInspection.Transform3DState(
        transform.position.x,
        transform.position.y,
        transform.position.z,
        transform.rotation.x,
        transform.rotation.y,
        transform.rotation.z,
        transform.rotation.w,
        transform.scale.x,
        transform.scale.y,
        transform.scale.z);
  }

  private static void restoreTransform(
      Transform3D transform, SceneInspection.Transform3DState state, Transform3D parent) {
    transform.setParent(parent);
    transform.position.set(state.x(), state.y(), state.z());
    transform.rotation.set(state.qx(), state.qy(), state.qz(), state.qw());
    transform.scale.set(state.scaleX(), state.scaleY(), state.scaleZ());
  }

  void onFocusLost() {
    if (inspector != null) inspector.onFocusLost();
  }

  private void clickInspectorActor(String name) {
    com.badlogic.gdx.scenes.scene2d.Actor actor = stage.getRoot().findActor(name);
    if (actor == null) throw new AssertionError("Inspector control missing: " + name);
    Vector2 screen =
        stage.stageToScreenCoordinates(
            actor.localToStageCoordinates(
                new Vector2(actor.getWidth() / 2, actor.getHeight() / 2)));
    stage.touchDown(Math.round(screen.x), Math.round(screen.y), 0, Input.Buttons.LEFT);
    stage.touchUp(Math.round(screen.x), Math.round(screen.y), 0, Input.Buttons.LEFT);
  }

  @Override
  public void resize(int width, int height) {
    if (inspector != null) inspector.onViewportChanged();
    if (renderer != null) renderer.resize(width, height);
    if (stage != null) {
      stage.getViewport().update(width, height, true);
      stage
          .getRoot()
          .setSize(stage.getViewport().getWorldWidth(), stage.getViewport().getWorldHeight());
    }
    if (floor != null) floor.setPosition(width * .7f, 65);
    if (trigger != null) trigger.setPosition(width * .7f, 180);
  }

  @Override
  public void dispose() {
    if (input != null && inspector != null) {
      input.removeProcessor(inspector.inputProcessor());
      input.removeProcessor(inspector.gizmoInputProcessor());
    }
    if (Gdx.input.getInputProcessor() == input) Gdx.input.setInputProcessor(null);
    if (inspector != null) inspector.close();
    game.dispose();
    root.dispose();
    stage.dispose();
    theme.dispose();
    renderer.dispose();
    texture.dispose();
    solidTexture.dispose();
    if (inspectorSmokeExportDirectory != null) {
      try {
        if (inspectorSmokeExportTarget != null) Files.deleteIfExists(inspectorSmokeExportTarget);
        Files.deleteIfExists(inspectorSmokeExportDirectory);
      } catch (IOException failure) {
        Gdx.app.error("RuntimeInspector", "Could not clean runtime export smoke files", failure);
      }
    }
  }

  private final class ContactMonitor extends Component implements CollisionListener2D {
    public void onCollisionEnter(Contact2D contact) {
      if (contact.other().hasTag("floor")) groundContacts++;
    }

    public void onTriggerEnter(Contact2D contact) {
      triggerContacts++;
    }

    public void onLateUpdate(float delta) {
      contactStatus.setText("Contacts: " + groundContacts + " / triggers: " + triggerContacts);
    }
  }

  private void verifyRectanglePixels(Pixmap pixels) {
    Color corner = rectanglePixel(pixels, 1, 1);
    Color edge = rectanglePixel(pixels, 0, rectangle.getHeight() / 2);
    Color center = rectanglePixel(pixels, rectangle.getWidth() / 2, rectangle.getHeight() / 2);
    Color panel = UtilityStyles.color("slate-800"), fill = UtilityStyles.color("slate-900");
    if (Math.abs(corner.r - panel.r) > .03f
        || Math.abs(corner.g - panel.g) > .03f
        || Math.abs(center.b - fill.b) > .03f
        || edge.g < .5f
        || edge.b < .5f)
      throw new IllegalStateException(
          "Rounded corner, border or rectangle fill pixel check failed");
  }

  private Color rectanglePixel(Pixmap pixels, float x, float y) {
    Vector2 point = rectangle.localToStageCoordinates(new Vector2(x, y));
    int px = Math.round(point.x * pixels.getWidth() / stage.getWidth());
    int py = Math.round(point.y * pixels.getHeight() / stage.getHeight());
    return new Color(pixels.getPixel(px, py));
  }
}
