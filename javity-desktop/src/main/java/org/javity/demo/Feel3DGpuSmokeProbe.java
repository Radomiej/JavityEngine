package org.javity.demo;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import org.javity.ui.flex.UiTheme;
import org.javity.ui.inspector.RuntimeToolsOverlay;
import org.javity.ui.inspector.FeelPreviewPanel;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Vector3;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.core.Transform3D;
import org.javity.effects.Clock;
import org.javity.particles.ParticleBurstPool3D;
import org.javity.particles.ParticleBurstRenderer3D;
import org.javity.particles.ParticleBurstSpec3D;
import org.javity.postprocessing.WorldPostProcess;
import org.javity.presentation.CameraPresentation3D;
import org.javity.presentation.Presentation3D;
import org.javity.presentation.PresentationEffects3D;
import org.javity.render.Camera3D;
import org.javity.render.CameraRig3D;
import org.javity.render.GdxRenderer;
import org.javity.render.ModelRenderer;

/** Integrated GL30 proof for Slice 2 presentation, particle and world-postprocess composition. */
final class Feel3DGpuSmokeProbe extends ApplicationAdapter {
  private static final int SIZE = 256;
  private Game game;
  private GdxRenderer renderer;
  private FrameBuffer output;
  private WorldPostProcess post;
  private ParticleBurstPool3D pool;
  private ParticleBurstRenderer3D particles;
  private Texture spark;
  private Model worldModel, viewModel;
  private ModelInstance viewInstance;
  private Camera3D worldCamera;
  private CameraRig3D rig;
  private Transform3D worldTransform;
  private Presentation3D worldPose;
  private PresentationEffects3D worldEffects, cameraEffects;
  private CameraPresentation3D worldCameraPose, viewCameraPose;
  private Throwable failure;

  @Override
  public void create() {
    try {
      run();
      System.out.println("feel3dGpuSmoke: PASS (3D presentation + particles + world postprocess)");
    } catch (Throwable t) {
      failure = t;
      t.printStackTrace();
    } finally {
      try {
        cleanupOwned();
      } catch (Throwable cleanup) {
        if (failure == null) failure = cleanup;
        else failure.addSuppressed(cleanup);
      }
      try {
        if (spark != null) spark.dispose();
      } catch (Throwable cleanup) {
        if (failure == null) failure = cleanup;
        else failure.addSuppressed(cleanup);
      } finally {
        spark = null;
      }
      try {
        if (output != null) output.dispose();
      } catch (Throwable cleanup) {
        if (failure == null) failure = cleanup;
        else failure.addSuppressed(cleanup);
      } finally {
        output = null;
      }
      Gdx.app.exit();
    }
  }

  @Override
  public void render() {
    if (failure != null) throw new IllegalStateException("Feel3D proof failed", failure);
  }

  Throwable failure() {
    return failure;
  }

  private void run() throws IOException {
    require(Gdx.gl30 != null, "GL30 context is required by ParticleBurstRenderer3D");
    game = new Game();
    renderer = new GdxRenderer();
    renderer.resize(SIZE, SIZE);
    output = new FrameBuffer(Pixmap.Format.RGBA8888, SIZE, SIZE, true);
    worldModel =
        new ModelBuilder()
            .createBox(
                .8f,
                .8f,
                .8f,
                new Material(ColorAttribute.createDiffuse(.08f, .9f, .12f, 1)),
                Usage.Position | Usage.Normal);
    viewModel =
        new ModelBuilder()
            .createBox(
                .32f,
                .24f,
                .3f,
                new Material(ColorAttribute.createDiffuse(.95f, .12f, .04f, 1)),
                Usage.Position | Usage.Normal);
    viewInstance = new ModelInstance(viewModel);
    viewInstance.transform.setToTranslation(.45f, -.3f, -1.5f);
    GameObject cameraObject = game.createObject("feel3d-view");
    worldCamera = cameraObject.addComponent(new Camera3D(50));
    worldCamera.camera.near = .1f;
    worldCamera.camera.far = 30;
    worldCameraPose =
        cameraObject.addComponent(new CameraPresentation3D(CameraPresentation3D.Space.WORLD));
    viewCameraPose =
        cameraObject.addComponent(new CameraPresentation3D(CameraPresentation3D.Space.VIEWMODEL));
    cameraEffects = cameraObject.addComponent(new PresentationEffects3D(Clock.WORLD));
    GameObject actor = game.createObject("feel3d-actor");
    worldTransform = actor.addComponent(new Transform3D());
    actor.addComponent(new ModelRenderer(worldModel));
    worldPose = actor.addComponent(new Presentation3D());
    worldEffects = actor.addComponent(new PresentationEffects3D(Clock.WORLD));
    pool = game.addSystem(new ParticleBurstPool3D(Clock.WORLD, 64, 8));
    game.start();
    game.step(0);
    particles = new ParticleBurstRenderer3D(64);
    post = new WorldPostProcess(SIZE, SIZE);
    spark = makeSpark();
    rig =
        new CameraRig3D(
            worldCamera, new PerspectiveCamera(72, 1, 1), (from, to, radius, owner) -> 1f);
    rig.setRadius(.01f);
    rig.resize(SIZE, SIZE);
    rig.setWorldProjection(50, .1f, 30);
    updateRig();

    // Canonical pose and baseline pixels.
    float[] canonicalPosition = {
      worldTransform.position.x, worldTransform.position.y, worldTransform.position.z
    };
    float[] canonicalScale = {
      worldTransform.scale.x, worldTransform.scale.y, worldTransform.scale.z
    };
    ImageStats base = render("base.png", false);
    require(
        base.greenCount > 80,
        "baseline 3D model did not produce enough green pixels: " + base.greenCount);
    require(
        base.redCount > 20,
        "baseline viewmodel did not produce enough red pixels: " + base.redCount);

    previewPanelProof(base, canonicalPosition, canonicalScale);

    // Real analytic preset output moves the mesh rendered by GdxRenderer.
    var punch = worldEffects.play(PresentationEffects3D.punchPosition(22, 0, 0, 2, .7, 1));
    game.step(.1f);
    updateRig();
    ImageStats moved = render("model-punch.png", false);
    require(
        moved.greenCount > 80 && moved.greenCount < SIZE * SIZE / 2,
        "moved world model foreground count is invalid: " + moved.greenCount);
    require(
        Math.abs(moved.greenCenterX - base.greenCenterX) > 5,
        "position punch failed to move actual model pixels: "
            + base.greenCenterX
            + " -> "
            + moved.greenCenterX);
    require(
        canonical(worldTransform, canonicalPosition, canonicalScale),
        "presentation changed canonical Transform3D");
    punch.cancel();
    game.step(0);
    updateRig();

    // FOV spring is a true projection delta, and follows the latest authored projection base.
    var fov =
        cameraEffects.play(
            PresentationEffects3D.fovSpring(
                PresentationEffects3D.Surface.WORLD_CAMERA, 12, 2, .8, 1));
    game.step(.1f);
    updateRig();
    float firstDelta = rig.presentedWorldFov() - 50;
    require(
        firstDelta > 0 && worldCamera.camera.fieldOfView > 50,
        "FOV spring did not change the actual camera projection");
    rig.setWorldProjection(54, .1f, 30);
    updateRig();
    require(
        Math.abs(rig.presentedWorldFov() - (54 + firstDelta)) < .03f,
        "FOV effect did not compose around the latest authored base");
    fov.cancel();
    updateRig();
    require(
        Math.abs(worldCamera.camera.fieldOfView - 54) < 1e-4,
        "FOV cancellation restored an obsolete base");

    // A shot adds independent viewmodel recoil plus explicit movement-distance bob.
    var recoil =
        cameraEffects.play(
            PresentationEffects3D.recoil(
                PresentationEffects3D.Surface.VIEWMODEL_CAMERA,
                .28,
                .02,
                0,
                .12,
                .03,
                0,
                3,
                .75,
                1));
    var bob =
        cameraEffects.play(
            PresentationEffects3D.distanceBob(
                PresentationEffects3D.Surface.VIEWMODEL_CAMERA, 0, .15, 0, 1));
    bob.setDistanceBobInput(1, Math.PI / 2);
    game.step(.08f);
    updateRig();
    ImageStats viewMoved = render("viewmodel-recoil-bob.png", false);
    require(
        viewMoved.redCount > 20 && viewMoved.redCount < SIZE * SIZE / 3,
        "moved viewmodel foreground count is invalid: " + viewMoved.redCount);
    require(
        Math.abs(viewMoved.redCenterX - base.redCenterX) > 2
            || Math.abs(viewMoved.redCenterY - base.redCenterY) > 2,
        "viewmodel recoil/bob did not move actual overlay pixels: center "
            + base.redCenterX
            + ","
            + base.redCenterY
            + " -> "
            + viewMoved.redCenterX
            + ","
            + viewMoved.redCenterY);
    require(
        canonical(worldTransform, canonicalPosition, canonicalScale),
        "camera recoil changed actor Transform3D");
    recoil.cancel();
    bob.cancel();
    updateRig();

    // The owned particle renderer draws an actual orange burst inside the postprocessed world pass.
    var burst =
        pool.burst(
            new ParticleBurstSpec3D(
                new Vector3(1.25f, .1f, 0),
                new Vector3(-.05f, -.05f, 0),
                new Vector3(.05f, .05f, 0),
                new Vector3(0, 0, 0),
                1f,
                .24f,
                new Color(1, .18f, .025f, 1),
                18,
                7341));
    require(burst != null, "particle burst unexpectedly rejected");
    game.step(.016f);
    updateRig();
    ImageStats burstImage = render("burst-world.png", true);
    require(
        burstImage.orangeCount > 5,
        "actual framebuffer contains no orange burst pixels: " + burstImage.orangeCount);

    // World-only postprocessing changes the world pixel while the later viewmodel pixel stays put.
    WorldPostProcess.ResourceIds postIds = post.resourceIds();
    ImageStats washed;
    int overlayPixelDistance;
    int overlayX = burstImage.redCenterX, overlayY = burstImage.redCenterY;
    int[] overlayBefore = readOutputPixel(overlayX, overlayY);
    require(
        isRedForeground(overlayBefore), "control pixel is not viewmodel foreground before effect");
    WorldPostProcess.ControlLease desaturate = post.controls().acquire();
    try {
      desaturate.setDesaturation(1);
      washed = render("world-desaturated.png", true);
      require(
          washed.greenCount < burstImage.greenCount / 2,
          "world desaturation left world-green pixels unchanged");
      int[] overlayAfter = readOutputPixel(overlayX, overlayY);
      require(
          isRedForeground(overlayAfter), "control pixel is not viewmodel foreground after effect");
      overlayPixelDistance = colorDistance(overlayBefore, overlayAfter);
      require(
          overlayPixelDistance < 4, "world postprocess changed the later viewmodel overlay pixel");
      require(
          canonical(worldTransform, canonicalPosition, canonicalScale),
          "postprocess/render changed canonical transform");
    } finally {
      desaturate.close();
    }

    ImageStats restored = render("world-restored.png", true);
    require(
        restored.greenCount > burstImage.greenCount / 2,
        "closing postprocess contribution did not restore the live world");
    require(Gdx.gl.glGetError() == GL20.GL_NO_ERROR, "GL error in integrated Feel3D render proof");
    writeVerification(
        base,
        moved,
        viewMoved,
        burstImage,
        washed,
        restored,
        postIds,
        firstDelta,
        overlayPixelDistance);

    int particleProgram = particles.getShaderProgramId(),
        particleVbo = particles.getMeshVertexBufferId();
    int particleIbo = particles.getMeshIndexBufferId(),
        particleVao = particles.getMeshVertexArrayId();
    int sparkId = spark.getTextureObjectHandle(),
        outputTexture = output.getColorBufferTexture().getTextureObjectHandle();
    require(
        Gdx.gl.glIsProgram(particleProgram)
            && Gdx.gl.glIsBuffer(particleVbo)
            && Gdx.gl.glIsBuffer(particleIbo),
        "owned particle GL resources were missing before cleanup");
    require(Gdx.gl.glIsTexture(sparkId), "borrowed spark texture missing before cleanup");
    cleanupOwned();
    require(!Gdx.gl.glIsProgram(particleProgram), "particle shader program leaked");
    require(
        !Gdx.gl.glIsBuffer(particleVbo) && !Gdx.gl.glIsBuffer(particleIbo),
        "particle mesh buffers leaked");
    if (particleVao != 0) require(!Gdx.gl30.glIsVertexArray(particleVao), "particle VAO leaked");
    require(!Gdx.gl.glIsProgram(postIds.shaderProgram()), "postprocess shader program leaked");
    require(!Gdx.gl.glIsTexture(postIds.colorTexture()), "postprocess color texture leaked");
    require(!Gdx.gl.glIsFramebuffer(postIds.framebuffer()), "postprocess framebuffer leaked");
    if (postIds.depthRenderbuffer() != 0)
      require(
          !Gdx.gl.glIsRenderbuffer(postIds.depthRenderbuffer()),
          "postprocess depth renderbuffer leaked");
    require(Gdx.gl.glIsTexture(sparkId), "particle renderer disposed its borrowed sprite texture");
    require(
        Gdx.gl.glIsTexture(sparkId), "spark texture was not still live before probe-owned close");
    require(
        Gdx.gl.glIsTexture(outputTexture),
        "output texture was not still live before probe-owned close");
    require(Gdx.gl.glGetError() == GL20.GL_NO_ERROR, "GL error during integrated resource cleanup");
    spark.dispose();
    spark = null;
    output.dispose();
    output = null;
    require(!Gdx.gl.glIsTexture(sparkId), "probe did not release its owned spark texture");
    require(
        !Gdx.gl.glIsTexture(outputTexture),
        "probe output framebuffer texture leaked after disposal");
  }

  /** Actual Stage/multiplexer clicks drive the same public overlay used by game hosts. */
  private void previewPanelProof(ImageStats base, float[] position, float[] scale) throws IOException {
    var previousInput = Gdx.input.getInputProcessor();
    boolean previousCapture = Gdx.input.isCursorCatched();
    Stage stage = new Stage(new ScreenViewport());
    UiTheme theme = new UiTheme();
    RuntimeToolsOverlay tools = null;
    try {
      stage.getViewport().update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
      TextureRegion region = new TextureRegion(output.getColorBufferTexture());
      region.flip(false, true);
      Image image = new Image(region);
      image.setBounds(342, 240, SIZE, SIZE); stage.addActor(image);
      int borrowedChildren = stage.getRoot().getChildren().size;
      tools = new RuntimeToolsOverlay(stage, theme, () -> game);
      final int[] gameplayClicks = {0}, gameplayKeys = {0};
      InputMultiplexer input = new InputMultiplexer(tools.inspector().inputProcessor(), stage,
          tools.inspector().gizmoInputProcessor(), new InputAdapter() {
            @Override public boolean touchDown(int x, int y, int p, int b) { gameplayClicks[0]++; return true; }
            @Override public boolean keyDown(int key) {
              if (stage.getKeyboardFocus() != null || key != Input.Keys.W) return false;
              gameplayKeys[0]++; return true;
            }
          });
      Gdx.input.setInputProcessor(input);
      tools.update(0);
      tools.inspector().selectObject(worldTransform.getGameObject());
      input.keyDown(Input.Keys.F1); input.keyUp(Input.Keys.F1); tools.update(0); stage.act(0);
      ((com.badlogic.gdx.scenes.scene2d.ui.TextField) stage.getRoot().findActor("feel-parameter-Hz")).setText("bad");
      click(stage, input, "feel-play"); tools.update(0);
      require(((com.badlogic.gdx.scenes.scene2d.ui.Label) stage.getRoot().findActor("feel-status"))
          .getText().toString().contains("Invalid next preset"), "invalid typed parameter error vanished on update");
      tools.feelPreview().setParameters(new FeelPreviewPanel.Parameters(22, 0, 0, 2, .7, 1));
      click(stage, input, "feel-play");
      require(tools.feelPreview().isPlaying(), "native Play click did not start owned playback");
      game.step(.1f); updateRig(); tools.update(.1f);
      ImageStats moved = render("preview-model-play.png", false);
      require(Math.abs(moved.greenCenterX - base.greenCenterX) > 5, "panel Play did not move actual model pixels");
      click(stage, input, "feel-pause");
      game.step(.1f); updateRig(); tools.update(.1f);
      ImageStats paused = render("preview-model-paused.png", false);
      require(paused.greenCenterX == moved.greenCenterX && paused.greenCount == moved.greenCount,
          "panel Pause did not hold actual model pixels");
      click(stage, input, "feel-resume");
      game.step(.05f); updateRig(); tools.update(.05f);
      ImageStats resumed = render("preview-model-resumed.png", false);
      require(resumed.greenCenterX != paused.greenCenterX, "panel Resume did not advance actual model pixels");
      capturePanel(stage, "panel-model.png");
      click(stage, input, "feel-stop"); updateRig();
      ImageStats stopped = render("preview-model-stopped.png", false);
      require(stopped.greenCenterX == base.greenCenterX && stopped.greenCount == base.greenCount,
          "panel Stop did not restore model pixels");
      require(canonical(worldTransform, position, scale), "panel preview changed canonical Transform3D");

      focusHandoffProof(stage, theme, tools, input, gameplayKeys);

      // The selected object is explicit; changing to the camera uses its existing manager/providers.
      tools.inspector().selectObject(worldCamera.getGameObject()); tools.update(0);
      tools.feelPreview().selectPreset(FeelPreviewPanel.Preview.LANDING);
      tools.feelPreview().setParameters(new FeelPreviewPanel.Parameters(0, -12, 0, 2, .7, 1));
      click(stage, input, "feel-play"); game.step(.1f); updateRig(); tools.update(.1f);
      ImageStats camera = render("preview-camera-play.png", false);
      require(camera.greenCount != base.greenCount || camera.greenCenterX != base.greenCenterX
          || camera.greenCenterY != base.greenCenterY, "panel camera preset did not change actual model pixels");
      capturePanel(stage, "panel-camera.png");
      click(stage, input, "feel-stop"); updateRig();
      ImageStats cameraStopped = render("preview-camera-stopped.png", false);
      require(cameraStopped.greenCount == base.greenCount, "camera Stop failed to restore rendered output");
      click(stage, input, "feel-clock"); click(stage, input, "feel-play"); tools.update(0);
      require(((com.badlogic.gdx.scenes.scene2d.ui.Label) stage.getRoot().findActor("feel-status"))
          .getText().toString().contains("Clock unavailable"), "missing UI manager was not reported clearly");
      require(cameraEffects.activeCount() == 0, "unavailable clock installed an effect");
      capturePanel(stage, "panel-unavailable-clock.png");
      click(stage, input, "feel-clock"); click(stage, input, "feel-play");
      var external = cameraEffects.play(PresentationEffects3D.fovKick(
          PresentationEffects3D.Surface.WORLD_CAMERA, 3, 2, .7, 1));
      Game original = game;
      try (Game replacement = new Game()) {
        replacement.createObject("same-id replacement"); replacement.start();
        game = replacement; tools.update(0);
        require(!tools.feelPreview().isPlaying() && cameraEffects.activeCount() == 1 && external.isActive(),
            "scene transition did not isolate exact owned preview");
      } finally { game = original; external.cancel(); }
      require(gameplayClicks[0] == 0, "panel clicks reached gameplay");
      java.util.List<org.javity.ui.flex.Flex> ownedTrees = new java.util.ArrayList<>();
      collectFlex(stage.getRoot(), ownedTrees);
      tools.close(); tools.close();
      for (var tree : ownedTrees) {
        boolean disposed = false;
        try { tree.getPrefWidth(); } catch (IllegalStateException expected) { disposed = true; }
        require(disposed, "overlay retained a live Yoga Flex after close");
      }
      require(stage.getRoot().getChildren().size == borrowedChildren, "overlay tree retained actors after close");
      require(worldEffects.activeCount() == 0 && cameraEffects.activeCount() == 0,
          "preview owned effects survived overlay close");
      require(Gdx.gl.glIsTexture(output.getColorBufferTexture().getTextureObjectHandle()),
          "overlay closed borrowed texture");
      Path proof = Path.of("docs/artifacts/feel-preview/verification.json");
      Files.writeString(proof, "{\n  \"result\": \"PASS\",\n  \"actualStageClicks\": [\"play\",\"pause\",\"resume\",\"stop\"],\n"
          + "  \"modelCenterX\": [" + base.greenCenterX + "," + moved.greenCenterX + "," + paused.greenCenterX + "," + resumed.greenCenterX + "," + stopped.greenCenterX + "],\n"
          + "  \"gameplayClicks\": 0, \"canonicalTransformPreserved\": true, \"overlayTreeDetached\": true,\n"
          + "  \"ownedFlexTreesClosed\": " + ownedTrees.size() + ", \"clockUnavailableRefused\": true, \"sceneIdentityIsolation\": true,\n"
          + "  \"pngOrientation\": \"upright\"\n}\n");
    } finally {
      if (tools != null) tools.close();
      stage.dispose(); theme.dispose();
      Gdx.input.setInputProcessor(previousInput); Gdx.input.setCursorCatched(previousCapture);
    }
  }

  private void focusHandoffProof(Stage stage, UiTheme theme, RuntimeToolsOverlay tools,
      InputMultiplexer input, int[] gameplayKeys) throws IOException {
    int[] handoffs = {0};
    tools.setInteractionChanged(() -> handoffs[0]++);
    input.keyDown(Input.Keys.F1); input.keyUp(Input.Keys.F1);
    Gdx.input.setCursorCatched(true);
    input.keyDown(Input.Keys.F1); input.keyUp(Input.Keys.F1); tools.update(0);
    require(!Gdx.input.isCursorCatched(), "F1 open did not release captured cursor");
    click(stage, input, "feel-play");
    var newer = worldEffects.play(PresentationEffects3D.punchPosition(1, 0, 0, 2, .7, 1));
    try {
      click(stage, input, "feel-parameter-Hz");
      Actor field = stage.getRoot().findActor("feel-parameter-Hz");
      require(stage.getKeyboardFocus() == field, "actual Feel field click did not acquire keyboard focus");
      Vector2 hover = stage.stageToScreenCoordinates(field.localToStageCoordinates(
          new Vector2(field.getWidth() / 2, field.getHeight() / 2)));
      input.mouseMoved(Math.round(hover.x), Math.round(hover.y)); stage.act(0);
      Actor panel = stage.getRoot().findActor("feel-preview-panel");
      require(stage.getScrollFocus() != null && panel.isAscendantOf(stage.getScrollFocus()),
          "actual Feel hover did not acquire panel scroll focus");
      String text = ((com.badlogic.gdx.scenes.scene2d.ui.TextField) field).getText();
      int beforeHandoff = handoffs[0], beforeGameplay = gameplayKeys[0];
      input.keyDown(Input.Keys.F1); input.keyUp(Input.Keys.F1);
      require(stage.getKeyboardFocus() == null, "hidden Feel field retained keyboard focus after F1");
      require(stage.getScrollFocus() == null, "hidden Feel ScrollPane retained scroll focus after F1");
      require(Gdx.input.isCursorCatched() && handoffs[0] == beforeHandoff + 1,
          "F1 hide did not restore captured cursor and host input handoff");
      require(!tools.feelPreview().isPlaying() && newer.isActive() && worldEffects.activeCount() == 1,
          "F1 hide did not cancel only its exact owned playback");
      require(input.keyDown(Input.Keys.W) && gameplayKeys[0] == beforeGameplay + 1,
          "gameplay keyboard-focus gate stayed blocked after F1");
      input.keyUp(Input.Keys.W); input.keyTyped('9');
      require(((com.badlogic.gdx.scenes.scene2d.ui.TextField) field).getText().equals(text),
          "hidden Feel field received typed gameplay input");
    } finally { newer.cancel(); }

    // A borrowed host field outside both tool panels keeps its focus across their hide.
    var host = theme.textField("borrowed host", "text-sm text-slate-200 bg-slate-800 rounded");
    host.setName("feel-proof-host-field"); host.setBounds(350, 140, 240, 32); stage.addActor(host);
    try {
      input.keyDown(Input.Keys.F1); input.keyUp(Input.Keys.F1); tools.update(0);
      click(stage, input, "feel-proof-host-field");
      stage.setScrollFocus(host);
      require(stage.getKeyboardFocus() == host, "actual host field click did not acquire focus");
      input.keyDown(Input.Keys.F1); input.keyUp(Input.Keys.F1);
      require(stage.getKeyboardFocus() == host && stage.getScrollFocus() == host,
          "Feel hide cleared unrelated borrowed host focus");
      require(Gdx.input.isCursorCatched(), "borrowed-host focus test did not restore cursor");
    } finally { host.remove(); }
    input.keyDown(Input.Keys.F1); input.keyUp(Input.Keys.F1); tools.update(0);
    tools.setInteractionChanged(() -> {});
    Files.writeString(Path.of("docs/artifacts/feel-preview/focus-handoff.json"),
        "{\n  \"result\": \"PASS\",\n  \"actualFieldClickAndHover\": true,\n"
        + "  \"feelKeyboardAndScrollFocusReleased\": true,\n  \"cursorAndGameplayHandoff\": true,\n"
        + "  \"ownedPlaybackOnlyCancelled\": true,\n  \"borrowedHostFocusPreserved\": true\n}\n");
  }

  private void collectFlex(Actor actor, java.util.List<org.javity.ui.flex.Flex> trees) {
    if (actor instanceof org.javity.ui.flex.Flex flex) trees.add(flex);
    if (actor instanceof com.badlogic.gdx.scenes.scene2d.Group group)
      for (Actor child : group.getChildren()) collectFlex(child, trees);
  }

  private void click(Stage stage, InputMultiplexer input, String name) {
    stage.act(0); stage.draw();
    Actor actor = stage.getRoot().findActor(name);
    require(actor != null && actor.getWidth() > 0 && actor.getHeight() > 0, "button has no actual bounds: " + name);
    Vector2 point = actor.localToStageCoordinates(new Vector2(actor.getWidth() / 2, actor.getHeight() / 2));
    require(stage.hit(point.x, point.y, true) == actor || actor.isAscendantOf(stage.hit(point.x, point.y, true)),
        "button obscured or clipped: " + name + " at " + point);
    Vector2 screen = stage.stageToScreenCoordinates(point);
    require(input.touchDown(Math.round(screen.x), Math.round(screen.y), 0, Input.Buttons.LEFT), "unhandled click: " + name);
    input.touchUp(Math.round(screen.x), Math.round(screen.y), 0, Input.Buttons.LEFT);
  }

  private void capturePanel(Stage stage, String file) {
    Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    Gdx.gl.glClearColor(.025f, .035f, .06f, 1); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    stage.act(0); stage.draw();
    Pixmap pixels = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    try {
      FileHandle out = Gdx.files.local("docs/artifacts/feel-preview/" + file); out.parent().mkdirs();
      PixmapIO.writePNG(out, pixels, -1, true);
    } finally { pixels.dispose(); }
  }

  private ImageStats render(String file, boolean drawParticles) {
    output.begin();
    clear();
    try {
      post.renderWorld(
          () -> {
            renderer.renderWorld(game, worldCamera, null);
            if (drawParticles) particles.draw(pool, worldCamera.camera, spark, 0, 0, SIZE, SIZE);
          });
      renderer.renderViewmodel(rig.viewmodelCamera(), viewInstance);
      Pixmap pixels = Pixmap.createFromFrameBuffer(0, 0, SIZE, SIZE);
      try {
        FileHandle out = Gdx.files.local((file.startsWith("preview-")
            ? "docs/artifacts/feel-preview/" : "docs/artifacts/feel3d/") + file);
        out.parent().mkdirs();
        if (file.startsWith("preview-")) PixmapIO.writePNG(out, pixels, -1, true);
        else PixmapIO.writePNG(out, pixels);
      } finally {
        pixels.dispose();
      }
      return inspect();
    } finally {
      output.end();
    }
  }

  private void updateRig() {
    rig.update(new Vector3(0, 0, 4), new Vector3(0, 0, -1), null, worldCameraPose, viewCameraPose);
  }

  private void clear() {
    Gdx.gl.glViewport(0, 0, SIZE, SIZE);
    Gdx.gl.glClearColor(.025f, .035f, .06f, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
  }

  private ImageStats inspect() {
    ByteBuffer p = ByteBuffer.allocateDirect(SIZE * SIZE * 4);
    Gdx.gl.glReadPixels(0, 0, SIZE, SIZE, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, p);
    int green = 0, red = 0, orange = 0, gx = 0, gy = 0, rx = 0, ry = 0;
    for (int y = 0; y < SIZE; y++)
      for (int x = 0; x < SIZE; x++) {
        int i = (y * SIZE + x) * 4,
            r = p.get(i) & 255,
            g = p.get(i + 1) & 255,
            b = p.get(i + 2) & 255;
        if (g > r * 1.4 && g > b * 1.2 && g > 40) {
          green++;
          gx += x;
          gy += y;
        }
        if (r > g * 1.5 && r > b * 1.3 && r > 40) {
          red++;
          rx += x;
          ry += y;
        }
        if (r > g * 1.6 && g > b * 1.7 && r > 90 && g > 12) orange++;
      }
    return new ImageStats(
        green,
        green == 0 ? 0 : gx / green,
        green == 0 ? 0 : gy / green,
        red,
        red == 0 ? 0 : rx / red,
        red == 0 ? 0 : ry / red,
        orange);
  }

  private int[] readOutputPixel(int x, int y) {
    output.begin();
    try {
      ByteBuffer b = ByteBuffer.allocateDirect(4);
      Gdx.gl.glReadPixels(x, y, 1, 1, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, b);
      return new int[] {b.get(0) & 255, b.get(1) & 255, b.get(2) & 255};
    } finally {
      output.end();
    }
  }

  private int colorDistance(int[] a, int[] b) {
    return Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) + Math.abs(a[2] - b[2]);
  }

  private boolean isRedForeground(int[] rgb) {
    return rgb[0] > rgb[1] * 1.5 && rgb[0] > rgb[2] * 1.3 && rgb[0] > 40;
  }

  private Texture makeSpark() {
    Pixmap p = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
    p.setBlending(Pixmap.Blending.None);
    for (int y = 0; y < 16; y++)
      for (int x = 0; x < 16; x++) {
        float dx = (x + .5f - 8) / 8f, dy = (y + .5f - 8) / 8f;
        float alpha = Math.max(0, Math.min(1, (1 - (float) Math.sqrt(dx * dx + dy * dy)) * 5));
        p.setColor(1, 1, 1, alpha);
        p.drawPixel(x, y);
      }
    Texture t = new Texture(p);
    p.dispose();
    t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
    return t;
  }

  private void cleanupOwned() {
    Throwable first = null;
    try {
      if (particles != null) particles.close();
    } catch (Throwable t) {
      first = t;
    } finally {
      particles = null;
    }
    try {
      if (post != null) post.dispose();
    } catch (Throwable t) {
      if (first == null) first = t;
      else first.addSuppressed(t);
    } finally {
      post = null;
    }
    try {
      if (game != null) game.close();
    } catch (Throwable t) {
      if (first == null) first = t;
      else first.addSuppressed(t);
    } finally {
      game = null;
    }
    try {
      if (renderer != null) renderer.dispose();
    } catch (Throwable t) {
      if (first == null) first = t;
      else first.addSuppressed(t);
    } finally {
      renderer = null;
    }
    try {
      if (worldModel != null) worldModel.dispose();
    } catch (Throwable t) {
      if (first == null) first = t;
      else first.addSuppressed(t);
    } finally {
      worldModel = null;
    }
    try {
      if (viewModel != null) viewModel.dispose();
    } catch (Throwable t) {
      if (first == null) first = t;
      else first.addSuppressed(t);
    } finally {
      viewModel = null;
    }
    if (first instanceof RuntimeException e) throw e;
    if (first instanceof Error e) throw e;
    if (first != null) throw new IllegalStateException("Feel3D cleanup failed", first);
  }

  private boolean canonical(Transform3D t, float[] p, float[] s) {
    return t.position.x == p[0]
        && t.position.y == p[1]
        && t.position.z == p[2]
        && t.scale.x == s[0]
        && t.scale.y == s[1]
        && t.scale.z == s[2];
  }

  private void writeVerification(
      ImageStats base,
      ImageStats moved,
      ImageStats view,
      ImageStats burst,
      ImageStats washed,
      ImageStats restored,
      WorldPostProcess.ResourceIds ids,
      float fovDelta,
      int overlayPixelDistance)
      throws IOException {
    Path path = Path.of("docs/artifacts/feel3d/verification.json");
    Files.createDirectories(path.getParent());
    String report =
        String.format(
            Locale.ROOT,
            "{\n"
                + "  \"result\": \"PASS\",\n"
                + "  \"gl30\": true,\n"
                + "  \"worldModelPixels\": %d,\n"
                + "  \"movedWorldModelPixels\": %d,\n"
                + "  \"modelCenterX\": [%d, %d],\n"
                + "  \"viewmodelPixels\": %d,\n"
                + "  \"viewmodelCenter\": {\"base\": [%d, %d], \"recoilBob\": [%d, %d]},\n"
                + "  \"particleOrangePixels\": %d,\n"
                + "  \"worldGreenPixelsAfterPostprocess\": %d,\n"
                + "  \"worldGreenPixelsAfterRestore\": %d,\n"
                + "  \"presentedWorldFovDeltaDegrees\": %.5f,\n"
                + "  \"viewmodelPixelDistanceAcrossWorldPostprocess\": %d,\n"
                + "  \"postprocessIds\": {\"fbo\": %d, \"colorTexture\": %d, \"shader\": %d},\n"
                + "  \"checks\": [\"canonicalTransform\", \"liveBaseFov\", \"worldViewmodelSplit\","
                + " \"actualModelPixels\", \"actualParticlePixels\", \"worldPostprocessPixels\","
                + " \"ownedResourceCleanup\", \"borrowedTextureSurvival\"]\n"
                + "}\n",
            base.greenCount,
            moved.greenCount,
            base.greenCenterX,
            moved.greenCenterX,
            view.redCount,
            base.redCenterX,
            base.redCenterY,
            view.redCenterX,
            view.redCenterY,
            burst.orangeCount,
            washed.greenCount,
            restored.greenCount,
            fovDelta,
            overlayPixelDistance,
            ids.framebuffer(),
            ids.colorTexture(),
            ids.shaderProgram());
    Files.writeString(path, report);
  }

  private void require(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }

  private record ImageStats(
      int greenCount,
      int greenCenterX,
      int greenCenterY,
      int redCount,
      int redCenterX,
      int redCenterY,
      int orangeCount) {}
}
