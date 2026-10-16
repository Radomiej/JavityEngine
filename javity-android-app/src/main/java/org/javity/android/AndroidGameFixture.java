package org.javity.android;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.Layout;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.core.Transform3D;
import org.javity.render.Camera3D;
import org.javity.render.DashPattern2D;
import org.javity.render.GdxRenderer;
import org.javity.render.ModelRenderer;
import org.javity.render.ShapeMaterial2D;
import org.javity.render.ShapeRenderer2D;
import org.javity.script.ScriptUiRuntime;
import org.javity.script.quickjs.QuickJsScriptUiEngineFactory;
import org.javity.signals.Radio;
import org.javity.signals.Signal;
import org.javity.ui.declarative.UiDocument;
import org.javity.ui.flex.UiTheme;
import org.javity.voxel.AtlasLayout;
import org.javity.voxel.BlockDefinition;
import org.javity.voxel.BlockRegistry;
import org.javity.voxel.ChunkPos;
import org.javity.voxel.VoxelWorld;
import org.javity.voxel.controller.VoxelFpsController;
import org.javity.voxel.gpu.VoxelChunkRenderer;

/**
 * Small platform-neutral game fixture hosted by {@link JavityGameActivity}. The JS bundle uses the
 * same Javity React host and generated sample that desktop uses; only the host factory is Android
 * specific.
 */
public final class AndroidGameFixture extends ApplicationAdapter {
  private static final String ATLAS = "org/javity/demo/voxels/atlas.png";
  private static final AtlasLayout ATLAS_LAYOUT = new AtlasLayout(80, 40, 16, 2, 4, 2);
  private final FixtureInputState inputState = new FixtureInputState();
  private final LookPointerClaim lookPointer = new LookPointerClaim();
  private final Vector3 cameraEye = new Vector3();
  private final Vector3 cameraDirection = new Vector3();
  private final Vector2 lastLook = new Vector2();

  private Game game;
  private Radio radio;
  private Signal<Double> score;
  private Signal<String> compactUi;
  private VoxelWorld world;
  private VoxelChunkRenderer chunks;
  private Texture atlas;
  private Model sampleModel;
  private GameObject modelObject;
  private ModelRenderer modelRenderer;
  private GameObject sdfRoundedObject;
  private GameObject sdfCircleObject;
  private GameObject sdfDashedObject;
  private GdxRenderer renderer;
  private GameObject player;
  private Transform3D playerTransform;
  private Camera3D cameraComponent;
  private VoxelFpsController controller;
  private Stage stage;
  private ScreenViewport viewport;
  private Actor lookArea;
  private InputProcessor ownedInputProcessor;
  private UiTheme theme;
  private UiDocument document;
  private ScriptUiRuntime runtime;
  private Label signalLabel;
  private TextButton jumpButton;
  private TextButton pauseButton;
  private TextButton forwardButton;
  private TextButton leftButton;
  private TextButton backwardButton;
  private TextButton rightButton;
  private Table touchControls;
  private String appliedResponsiveMode;
  private volatile float unitsPerPixel = 1f;
  private volatile float densityScaleSnapshot = 1f;
  private boolean focused = true;
  private boolean paused;
  private boolean lifecycleSuspended;
  private volatile boolean resourcesReady;
  private volatile boolean disposed;
  private volatile long contextRebuilds;
  private volatile int renderedWidth;
  private volatile int renderedHeight;
  private volatile int framebufferWidth;
  private volatile int framebufferHeight;
  private volatile int logicalWidthSnapshot;
  private volatile int logicalHeightSnapshot;
  private volatile int renderedVoxelChunkSnapshot;
  private volatile int lookEvents;
  private volatile long inputEventCount;
  private volatile long completedFrames;
  private volatile String committedScoreSnapshot = "";
  private volatile boolean poisoned;
  private volatile boolean terminalDisposalRequested;
  private volatile boolean terminalDisposalComplete;
  private volatile boolean lifecycleSuspendedSnapshot;
  private Throwable lifecycleCleanupFailure;
  private volatile Thread ownerThread;
  private AndroidManagedContextProbe contextProbe;
  private Input ownedInput;

  @Override
  public void create() {
    ownerThread = Thread.currentThread();
    try {
      createCpuWorld();
      createRenderResources();
      createUi();
      resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
      contextProbe = new AndroidManagedContextProbe();
      resourcesReady = true;
    } catch (RuntimeException | Error failure) {
      FixtureLifecycle.rethrow(rollbackGeneration(failure, true));
    }
  }

  private void createCpuWorld() {
    BlockRegistry registry = new BlockRegistry();
    int grass = registry.register(new BlockDefinition("grass", 1, 1, 2, 0, 1, 1));
    int dirt = registry.register(new BlockDefinition("dirt", 2));
    int stone = registry.register(new BlockDefinition("stone", 3));
    world = new VoxelWorld(registry, 2);
    world.loadChunk(new ChunkPos(0, 0, 0));
    for (int z = 0; z < 16; z++) {
      for (int x = 0; x < 16; x++) {
        world.setBlock(x, 0, z, x % 7 == 0 ? stone : dirt);
        world.setBlock(x, 1, z, grass);
      }
    }
    for (int y = 2; y < 5; y++) {
      world.setBlock(11, y, 4, stone);
      world.setBlock(11, y, 5, stone);
    }

    game = new Game();
    radio = game.addSystem(new Radio());
    score = radio.channel("score", Double.class, 0d);
    compactUi = radio.channel("compactUi", String.class, "portrait");

    player = game.createObject("touch-controlled voxel player");
    playerTransform = player.addComponent(new Transform3D());
    playerTransform.position.set(8f, 2f, 11f);
    cameraComponent = player.addComponent(new Camera3D(67f));
    cameraComponent.camera.near = .1f;
    cameraComponent.camera.far = 120f;
    controller = player.addComponent(new VoxelFpsController(world));

    modelObject = game.createObject("rendered 3D sample model");
    Transform3D modelTransform = modelObject.addComponent(new Transform3D());
    // Bottom rests at floor Y=2; the initial front face is visible beside high-DPI controls.
    modelTransform.position.set(9.2f, 3.4f, 6f);

    sdfRoundedObject = game.createObject("GLES SDF rounded material probe");
    ShapeRenderer2D rounded = sdfRoundedObject.addComponent(ShapeRenderer2D.rectangle(132f, 50f));
    rounded.space = ShapeRenderer2D.Space.SCREEN;
    rounded.cornerRadius = 12f;
    rounded.material =
        ShapeMaterial2D.gradient(Color.valueOf("2563eb"), Color.valueOf("f472b6"), .35f);

    sdfCircleObject = game.createObject("GLES SDF anti-aliased circle probe");
    ShapeRenderer2D circle = sdfCircleObject.addComponent(ShapeRenderer2D.circle(20f));
    circle.space = ShapeRenderer2D.Space.SCREEN;
    circle.color.set(Color.valueOf("facc15"));

    sdfDashedObject = game.createObject("GLES SDF dashed line probe");
    ShapeRenderer2D dashed =
        sdfDashedObject.addComponent(
            ShapeRenderer2D.polyline(List.of(new Vector2(0, 0), new Vector2(150, 0)), 9f));
    dashed.space = ShapeRenderer2D.Space.SCREEN;
    dashed.lineCap = ShapeRenderer2D.LineCap.BUTT;
    dashed.color.set(Color.valueOf("22d3ee"));
    dashed.setDashes(new DashPattern2D(16f, 9f), 0);
    game.start();
  }

  private void createRenderResources() {
    if (renderer != null || chunks != null || atlas != null || sampleModel != null)
      throw new IllegalStateException("Previous render generation still owns resources");
    if (Gdx.gl30 == null) throw new IllegalStateException("The Android fixture requires GLES 3");
    atlas = new Texture(Gdx.files.internal(ATLAS), false);
    atlas.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
    atlas.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
    renderer = new GdxRenderer();
    renderer.resize(Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());

    ModelBuilder builder = new ModelBuilder();
    sampleModel =
        builder.createBox(
            1.6f,
            2.8f,
            1.6f,
            new Material(ColorAttribute.createDiffuse(Color.valueOf("39c6c8"))),
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
    modelRenderer = modelObject.addComponent(new ModelRenderer(sampleModel));
    game.initializePendingComponents();

    chunks = new VoxelChunkRenderer(game, world, atlas, ATLAS_LAYOUT);
    contextRebuilds++;
  }

  private void createUi() {
    if (runtime != null || document != null || theme != null || stage != null)
      throw new IllegalStateException("Previous UI generation still owns resources");
    float density = Math.max(.5f, Gdx.graphics.getDensity());
    densityScaleSnapshot = density;
    unitsPerPixel = 1f / density;
    viewport = new ScreenViewport();
    viewport.setUnitsPerPixel(unitsPerPixel);
    stage = new Stage(viewport);

    theme = new UiTheme();
    document = new UiDocument(theme);
    document.mount(stage);
    document.root().setFillParent(true);

    runtime = new ScriptUiRuntime(document, new QuickJsScriptUiEngineFactory());
    runtime.bindSignal("score", score);
    runtime.bindSignal("compactUi", compactUi);
    runtime.bindAction("androidIncrement", () -> score.set(score.get() + 1d));
    runtime.eval(readResource("/org/javity/script/javity-sample.js"));
    runtime.eval(readResource("/org/javity/android/android-fixture.js"));

    // A full-screen transparent area is first in the Stage so movement controls and the React HUD
    // take ownership before look input. InputMultiplexer also routes Stage before keyboard
    // controls.
    lookArea = new Actor();
    lookArea.setName("right-side-look-input");
    lookArea.addListener(
        new InputListener() {
          @Override
          public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
            if (!focused || paused || lifecycleSuspended) return false;
            if (!lookPointer.claim(pointer)) return false;
            lastLook.set(event.getStageX(), event.getStageY());
            return true;
          }

          @Override
          public void touchDragged(InputEvent event, float x, float y, int pointer) {
            if (!lookPointer.owns(pointer) || !focused || paused || lifecycleSuspended) return;
            float dx = event.getStageX() - lastLook.x;
            float dy = event.getStageY() - lastLook.y;
            lastLook.set(event.getStageX(), event.getStageY());
            controller.addLookInput(-dx * .22f, dy * .22f);
            lookEvents++;
          }

          @Override
          public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
            if (lookPointer.release(pointer)) lastLook.setZero();
          }
        });
    stage.addActor(lookArea);

    touchControls = new Table();
    touchControls.setName("touch-movement-controls");
    forwardButton = movementButton("W", Input.Keys.W);
    leftButton = movementButton("A", Input.Keys.A);
    backwardButton = movementButton("S", Input.Keys.S);
    rightButton = movementButton("D", Input.Keys.D);
    jumpButton = theme.button("JUMP", AppUiStyles.JUMP, () -> {});
    jumpButton.setName("jump-control");
    jumpButton.addListener(holdJumpListener());
    touchControls.add(jumpButton).colspan(3).width(168f).height(46f).padTop(6f);
    touchControls.row();
    pauseButton = theme.button("PAUSE / RESUME", AppUiStyles.PAUSE, this::togglePause);
    pauseButton.setName("pause-resume-control");
    stage.addActor(touchControls);
    appliedResponsiveMode = null;
    applyResponsiveMode(compactUi.get());
    document.root().toFront();
    touchControls.toFront();

    signalLabel = (Label) document.actor("android-score-value");
    if (signalLabel == null)
      throw new IllegalStateException("React fixture did not commit its score label");
    committedScoreSnapshot = signalLabel.getText().toString();
    ownedInput = Gdx.input;
    ownedInputProcessor = new TrackingInputMultiplexer(stage, new KeyboardControls());
    ownedInput.setInputProcessor(ownedInputProcessor);
  }

  private static String responsiveMode(float logicalHeight, float unitsPerPixel) {
    if (logicalHeight >= 360f) return "portrait";
    // The shorter HUD preserves a useful SDF strip when logical controls must be 60 physical px.
    return unitsPerPixel > .75f ? "compact-short" : "compact-tall";
  }

  private void applyResponsiveMode(String mode) {
    if (mode.equals(appliedResponsiveMode)) return;
    if (compactUi != null && !mode.equals(compactUi.get())) compactUi.set(mode);
    if (touchControls == null) return;
    if (appliedResponsiveMode != null) clearHeldInput();
    touchControls.clearChildren();
    if ("portrait".equals(mode)) {
      touchControls.bottom().left().pad(12f);
      touchControls.add().size(54f);
      touchControls.add(forwardButton).size(54f).pad(3f);
      touchControls.add().size(54f);
      touchControls.row();
      touchControls.add(leftButton).size(54f).pad(3f);
      touchControls.add(backwardButton).size(54f).pad(3f);
      touchControls.add(rightButton).size(54f).pad(3f);
      touchControls.row();
      touchControls.add(jumpButton).colspan(3).width(168f).height(46f).padTop(6f);
      touchControls.row();
      touchControls.add(pauseButton).colspan(3).width(168f).height(42f).padTop(4f);
      setControlFontScale(1f);
    } else {
      float cell = Math.max(40f, 60f * unitsPerPixel);
      touchControls.bottom().left().pad(4f * unitsPerPixel);
      touchControls.add().size(cell);
      touchControls.add(forwardButton).size(cell);
      touchControls.add().size(cell);
      touchControls.row();
      touchControls.add(leftButton).size(cell);
      touchControls.add(backwardButton).size(cell);
      touchControls.add(rightButton).size(cell);
      touchControls.row();
      touchControls.add(jumpButton).colspan(1).width(cell * 1.5f).height(cell);
      touchControls.add(pauseButton).colspan(2).width(cell * 2f).height(cell);
      setControlFontScale(.78f);
    }
    touchControls.pack();
    touchControls.setPosition(0f, 0f);
    touchControls.invalidateHierarchy();
    appliedResponsiveMode = mode;
  }

  private void setControlFontScale(float scale) {
    forwardButton.getLabel().setFontScale(scale);
    leftButton.getLabel().setFontScale(scale);
    backwardButton.getLabel().setFontScale(scale);
    rightButton.getLabel().setFontScale(scale);
    jumpButton.getLabel().setFontScale(scale);
    pauseButton.getLabel().setFontScale(scale);
  }

  private TextButton movementButton(String text, int keycode) {
    TextButton button = theme.button(text, AppUiStyles.MOVEMENT, () -> {});
    button.setName("move-" + keycode);
    button.addListener(
        new InputListener() {
          @Override
          public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
            if (!focused || paused || lifecycleSuspended) return false;
            inputState.touch(keycode, event.getPointer(), true);
            syncMovement();
            return true;
          }

          @Override
          public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
            inputState.touch(keycode, event.getPointer(), false);
            syncMovement();
          }
        });
    return button;
  }

  private InputListener holdJumpListener() {
    return new InputListener() {
      @Override
      public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
        if (!focused || paused || lifecycleSuspended) return false;
        inputState.jumpPointer(event.getPointer(), true);
        controller.setJump(inputState.jumpHeld());
        return true;
      }

      @Override
      public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
        inputState.jumpPointer(event.getPointer(), false);
        controller.setJump(inputState.jumpHeld());
      }
    };
  }

  private void syncMovement() {
    if (!focused || paused || lifecycleSuspended) {
      controller.setMoveInput(0f, 0f);
      return;
    }
    float forward =
        (inputState.held(Input.Keys.W) ? 1f : 0f) - (inputState.held(Input.Keys.S) ? 1f : 0f);
    float right =
        (inputState.held(Input.Keys.D) ? 1f : 0f) - (inputState.held(Input.Keys.A) ? 1f : 0f);
    controller.setMoveInput(forward, right);
  }

  @Override
  public void render() {
    if (!resourcesReady || disposed) return;
    float delta = Math.min(Gdx.graphics.getDeltaTime(), .1f);
    game.step(delta);
    if (!focused || paused || lifecycleSuspended) {
      controller.setMoveInput(0f, 0f);
      controller.setJump(false);
    }
    controller.getEyePosition(cameraEye);
    controller.getLookDirection(cameraDirection);
    cameraComponent.camera.position.set(cameraEye);
    cameraComponent.camera.direction.set(cameraDirection);
    cameraComponent.camera.up.set(Vector3.Y);
    cameraComponent.camera.update();

    chunks.update(cameraComponent.camera, 1);
    renderedVoxelChunkSnapshot = chunks.renderedChunkCount();
    Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    Gdx.gl.glClearColor(.055f, .095f, .14f, 1f);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    renderer.renderWorld(game, cameraComponent, null);
    renderer.render2D(game);
    Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    runtime.tick(delta);
    stage.act(delta);
    stage.draw();
    if (signalLabel != null) committedScoreSnapshot = signalLabel.getText().toString();
    renderedWidth = Gdx.graphics.getWidth();
    renderedHeight = Gdx.graphics.getHeight();
    framebufferWidth = Gdx.graphics.getBackBufferWidth();
    framebufferHeight = Gdx.graphics.getBackBufferHeight();
    completedFrames++;
  }

  @Override
  public void resize(int width, int height) {
    if (stage == null) return;
    viewport.update(width, height, true);
    applyResponsiveMode(responsiveMode(viewport.getWorldHeight(), unitsPerPixel));
    logicalWidthSnapshot = Math.round(viewport.getWorldWidth());
    logicalHeightSnapshot = Math.round(viewport.getWorldHeight());
    if (lookArea != null) {
      float worldWidth = viewport.getWorldWidth();
      float worldHeight = viewport.getWorldHeight();
      lookArea.setBounds(worldWidth * .48f, 0f, worldWidth * .52f, worldHeight);
    }
    int framebufferWidth = Gdx.graphics.getBackBufferWidth();
    int framebufferHeight = Gdx.graphics.getBackBufferHeight();
    if (renderer != null) renderer.resize(framebufferWidth, framebufferHeight);
    layoutGlesFixtures(framebufferWidth, framebufferHeight);
  }

  /** Fits the SDF probes in the measured clear strip between the HUD and touch controls. */
  private void layoutGlesFixtures(int width, int height) {
    if (sdfRoundedObject == null || stage == null || width <= 0 || height <= 0) return;
    if (document != null) document.root().validate();
    Actor controlsActor = stage.getRoot().findActor("touch-movement-controls");
    if (controlsActor instanceof Layout layout) layout.validate();
    float[] hud = actorSurfaceBounds("android-hud");
    float[] controls = actorSurfaceBounds("touch-movement-controls");
    float pixelsPerSurfaceY = height / Math.max(1f, Gdx.graphics.getHeight());
    // Actor bounds use Android surface coordinates (top-left origin); renderer SCREEN space uses
    // physical framebuffer coordinates (bottom-left origin). Keep the probes inside that gap.
    float freeTop = hud[3] * pixelsPerSurfaceY;
    float freeBottom = controls[1] * pixelsPerSurfaceY;
    float freeHeight = freeBottom - freeTop;
    if (freeHeight < 8f)
      throw new IllegalStateException(
          "No measured HUD/control gap for GLES probes: " + freeHeight + "px");
    float scale = Math.min(1f, Math.max(.12f, (freeHeight - 4f) / 50f));
    sdfRoundedObject.transform.scale.set(scale, scale);
    sdfCircleObject.transform.scale.set(scale, scale);
    sdfDashedObject.transform.scale.set(scale, scale);
    float roundedWidth = 132f * scale;
    float circleDiameter = 40f * scale;
    float dashedWidth = 150f * scale;
    float gap = 8f * scale;
    float rowWidth = roundedWidth + gap + circleDiameter + gap + dashedWidth;
    if (rowWidth > width - 16f)
      throw new IllegalStateException(
          "Measured GLES probe row is wider than framebuffer: " + rowWidth + "px");
    float rowLeft = (width - rowWidth) * .5f;
    float rowCenterY = height - (freeTop + freeBottom) * .5f;
    // SDF rectangles/circles are centered at local zero; polyline points retain their coordinates.
    sdfRoundedObject.transform.position.set(rowLeft + 66f * scale, rowCenterY);
    sdfCircleObject.transform.position.set(rowLeft + roundedWidth + gap + 20f * scale, rowCenterY);
    sdfDashedObject.transform.position.set(
        rowLeft + roundedWidth + gap + circleDiameter + gap, rowCenterY);
  }

  @Override
  public void pause() {
    lifecycleSuspended = true;
    lifecycleSuspendedSnapshot = true;
    Throwable failure = FixtureLifecycle.attempt(null, this::clearHeldInput);
    if (game != null && !game.isDisposed())
      failure = FixtureLifecycle.attempt(failure, game::pause);
    failure = releaseUiResources(failure);
    failure = releaseRenderResources(failure);
    AndroidManagedContextProbe oldProbe = contextProbe;
    contextProbe = null;
    failure = closeResource(oldProbe, failure);
    if (failure != null) {
      rememberLifecycleFailure(failure);
      poisoned = true;
    }
    if (terminalDisposalRequested) failure = disposeCpuState(failure);
    FixtureLifecycle.rethrow(failure);
  }

  @Override
  public void resume() {
    if (disposed || game == null) return;
    if (poisoned)
      throw new IllegalStateException(
          "Fixture lifecycle cleanup failed; refusing resource replacement");
    // Some Android backend versions report the initial surface resume after create().
    if (generationReady()) {
      lifecycleSuspended = !focused;
      if (!paused && !lifecycleSuspended) game.resume();
      return;
    }
    if (hasAnyGenerationResource()) {
      Throwable cleanupFailure = releaseUiResources(null);
      cleanupFailure = releaseRenderResources(cleanupFailure);
      if (cleanupFailure != null) {
        rememberLifecycleFailure(cleanupFailure);
        poisoned = true;
        FixtureLifecycle.rethrow(cleanupFailure);
      }
    }
    try {
      createRenderResources();
      lifecycleSuspended = !focused;
      if (!paused && !lifecycleSuspended) game.resume();
      else game.pause();
      clearHeldInput();
      createUi();
      resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
      renderer.resize(Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
      if (contextProbe != null)
        throw new IllegalStateException("Previous context probe still owns resources");
      contextProbe = new AndroidManagedContextProbe();
      lifecycleSuspendedSnapshot = false;
      resourcesReady = true;
    } catch (RuntimeException | Error primaryFailure) {
      FixtureLifecycle.rethrow(rollbackGeneration(primaryFailure, false));
    }
  }

  /** AndroidActivity forwards focus loss here so no touch/key hold survives System UI or pause. */
  public void setWindowFocused(boolean next) {
    focused = next;
    if (!next) {
      lifecycleSuspended = true;
      if (game != null && !game.isDisposed()) game.pause();
      clearHeldInput();
    } else if (!disposed) {
      lifecycleSuspended = false;
      if (!paused && game != null && !game.isDisposed()) game.resume();
    }
  }

  private void togglePause() {
    paused = !paused;
    if (paused) {
      if (game != null && !game.isDisposed()) game.pause();
      clearHeldInput();
    } else if (!lifecycleSuspended && focused && game != null && !game.isDisposed()) {
      game.resume();
    }
  }

  private void clearHeldInput() {
    inputState.clear();
    lookPointer.clear();
    lastLook.setZero();
    Throwable failure = null;
    if (stage != null) failure = FixtureLifecycle.attempt(failure, stage::cancelTouchFocus);
    if (controller != null) {
      failure = FixtureLifecycle.attempt(failure, () -> controller.setMoveInput(0f, 0f));
      failure = FixtureLifecycle.attempt(failure, () -> controller.setSprint(false));
      failure = FixtureLifecycle.attempt(failure, () -> controller.setJump(false));
    }
    FixtureLifecycle.rethrow(failure);
  }

  private Throwable releaseRenderResources(Throwable failure) {
    resourcesReady = false;
    VoxelChunkRenderer oldChunks = chunks;
    chunks = null;
    renderedVoxelChunkSnapshot = 0;
    failure = closeResource(oldChunks, failure);
    ModelRenderer oldModelRenderer = modelRenderer;
    modelRenderer = null;
    if (modelObject != null && oldModelRenderer != null && !modelObject.isDestroyed()) {
      failure =
          FixtureLifecycle.attempt(
              failure,
              () -> {
                modelObject.removeComponent(oldModelRenderer);
                game.step(0f);
              });
    }
    Model oldModel = sampleModel;
    sampleModel = null;
    failure = closeResource(oldModel, failure);
    GdxRenderer oldRenderer = renderer;
    renderer = null;
    failure = closeResource(oldRenderer, failure);
    Texture oldAtlas = atlas;
    atlas = null;
    failure = closeResource(oldAtlas, failure);
    resourcesReady = false;
    return failure;
  }

  private Throwable releaseUiResources(Throwable failure) {
    ScriptUiRuntime oldRuntime = runtime;
    runtime = null;
    failure = closeResource(oldRuntime, failure);
    UiDocument oldDocument = document;
    document = null;
    failure = closeResource(oldDocument, failure);
    UiTheme oldTheme = theme;
    theme = null;
    failure = closeResource(oldTheme, failure);
    Stage oldStage = stage;
    stage = null;
    viewport = null;
    lookArea = null;
    jumpButton = null;
    pauseButton = null;
    forwardButton = null;
    leftButton = null;
    backwardButton = null;
    rightButton = null;
    touchControls = null;
    appliedResponsiveMode = null;
    signalLabel = null;
    Input oldInput = ownedInput;
    InputProcessor oldInputProcessor = ownedInputProcessor;
    ownedInput = null;
    ownedInputProcessor = null;
    if (oldInput != null && oldInputProcessor != null)
      failure =
          FixtureLifecycle.attempt(
              failure,
              () -> {
                if (oldInput.getInputProcessor() == oldInputProcessor)
                  oldInput.setInputProcessor(null);
              });
    failure = closeResource(oldStage, failure);
    return failure;
  }

  @Override
  public void dispose() {
    if (disposed) return;
    disposed = true;
    resourcesReady = false;
    Throwable failure = withPriorLifecycleFailure(null);
    failure = FixtureLifecycle.attempt(failure, this::clearHeldInput);
    if (game != null && !game.isDisposed())
      failure = FixtureLifecycle.attempt(failure, game::pause);
    failure = releaseUiResources(failure);
    failure = releaseRenderResources(failure);
    AndroidManagedContextProbe oldProbe = contextProbe;
    contextProbe = null;
    failure = closeResource(oldProbe, failure);
    Game oldGame = game;
    game = null;
    failure = closeResource(oldGame, failure);
    if (failure != null) {
      rememberLifecycleFailure(failure);
      poisoned = true;
    }
    if (failure == null && lifecycleCleanupFailure == null && !poisoned)
      terminalDisposalComplete = true;
    FixtureLifecycle.rethrow(failure);
  }

  /** Requests full disposal at the next safe GL-thread lifecycle point. */
  public void requestTerminalDisposal() {
    terminalDisposalRequested = true;
  }

  /** True after an Activity lifecycle pause released this fixture's GL generation. */
  public boolean isLifecycleSuspended() {
    return lifecycleSuspendedSnapshot;
  }

  /** Completion signal for Activity recreation instrumentation. */
  public boolean isTerminalDisposalComplete() {
    return terminalDisposalComplete;
  }

  /**
   * Closes the retained CPU Game after pause has already released every GL owner. This may run from
   * a queued callback on the old GL thread while its EGL context is absent; it must stay strictly
   * CPU-only.
   */
  public void disposeRetainedCpuAfterPause() {
    requireOwnerThread();
    if (!lifecycleSuspendedSnapshot)
      FixtureLifecycle.rethrow(
          rememberCpuCleanupFailure("Cannot close paused fixture before lifecycle pause"));
    if (resourcesReady
        || renderer != null
        || chunks != null
        || atlas != null
        || sampleModel != null
        || stage != null
        || theme != null
        || document != null
        || runtime != null
        || contextProbe != null)
      FixtureLifecycle.rethrow(rememberCpuCleanupFailure("Paused fixture still owns GL resources"));
    FixtureLifecycle.rethrow(disposeCpuState(null));
  }

  private Throwable disposeCpuState(Throwable failure) {
    failure = withPriorLifecycleFailure(failure);
    if (disposed) return failure;
    if (resourcesReady
        || renderer != null
        || chunks != null
        || atlas != null
        || sampleModel != null
        || stage != null
        || theme != null
        || document != null
        || runtime != null
        || contextProbe != null)
      return rememberCpuCleanupFailure("CPU disposal found retained GL resources", failure);
    disposed = true;
    resourcesReady = false;
    Game oldGame = game;
    game = null;
    failure = closeResource(oldGame, failure);
    if (failure == null && lifecycleCleanupFailure == null && !poisoned)
      terminalDisposalComplete = true;
    else {
      rememberLifecycleFailure(failure);
      poisoned = true;
    }
    return failure;
  }

  private Throwable withPriorLifecycleFailure(Throwable failure) {
    if (lifecycleCleanupFailure == null) return failure;
    if (failure == null || failure == lifecycleCleanupFailure) return lifecycleCleanupFailure;
    lifecycleCleanupFailure.addSuppressed(failure);
    return lifecycleCleanupFailure;
  }

  private void rememberLifecycleFailure(Throwable failure) {
    if (failure == null) return;
    if (lifecycleCleanupFailure == null) lifecycleCleanupFailure = failure;
    else if (lifecycleCleanupFailure != failure) lifecycleCleanupFailure.addSuppressed(failure);
  }

  private Throwable rememberCpuCleanupFailure(String message) {
    return rememberCpuCleanupFailure(message, null);
  }

  private Throwable rememberCpuCleanupFailure(String message, Throwable failure) {
    Throwable rejected = append(failure, new IllegalStateException(message));
    rememberLifecycleFailure(rejected);
    poisoned = true;
    return rejected;
  }

  private boolean generationReady() {
    return resourcesReady
        && renderer != null
        && chunks != null
        && atlas != null
        && sampleModel != null
        && stage != null
        && theme != null
        && document != null
        && runtime != null;
  }

  private boolean hasAnyGenerationResource() {
    return renderer != null
        || chunks != null
        || atlas != null
        || sampleModel != null
        || stage != null
        || theme != null
        || document != null
        || runtime != null;
  }

  /** Attempts every rollback phase while keeping the construction failure primary. */
  private Throwable rollbackGeneration(Throwable primary, boolean closeGame) {
    resourcesReady = false;
    int previousSuppressed = primary == null ? 0 : primary.getSuppressed().length;
    Throwable failure = primary;
    failure = FixtureLifecycle.attempt(failure, this::clearHeldInput);
    if (game != null && !game.isDisposed())
      failure = FixtureLifecycle.attempt(failure, game::pause);
    failure = releaseUiResources(failure);
    failure = releaseRenderResources(failure);
    AndroidManagedContextProbe oldProbe = contextProbe;
    contextProbe = null;
    failure = closeResource(oldProbe, failure);
    if (closeGame) {
      Game oldGame = game;
      game = null;
      failure = closeResource(oldGame, failure);
    }
    if (failure != primary
        || (primary != null && primary.getSuppressed().length > previousSuppressed))
      poisoned = true;
    if (failure != null) rememberLifecycleFailure(failure);
    return failure;
  }

  /**
   * Density and resource hooks consumed by instrumentation; all bounds are reported in logical UI
   * units.
   */
  public void onGraphicsContextRestored() {
    requireOwnerThread();
    if (renderer != null) renderer.restoreContextShaderBindings();
  }

  public AndroidManagedContextProbe.Snapshot contextProbeSnapshotForInstrumentation() {
    requireOwnerThread();
    if (contextProbe == null) throw new IllegalStateException("Context probe is not ready");
    return contextProbe.renderAndSnapshot();
  }

  public long managedUploadCountForInstrumentation() {
    requireOwnerThread();
    if (contextProbe == null) throw new IllegalStateException("Context probe is not ready");
    return contextProbe.managedUploadCount();
  }

  public boolean isDisposedForInstrumentation() {
    return disposed;
  }

  public long completedFramesForInstrumentation() {
    return completedFrames;
  }

  public boolean isReady() {
    return resourcesReady;
  }

  public float unitsPerPixel() {
    return unitsPerPixel;
  }

  public float densityScale() {
    return densityScaleSnapshot;
  }

  public int logicalWidth() {
    return logicalWidthSnapshot;
  }

  public int logicalHeight() {
    return logicalHeightSnapshot;
  }

  public int renderedWidth() {
    return renderedWidth;
  }

  public int renderedHeight() {
    return renderedHeight;
  }

  public int framebufferWidth() {
    return framebufferWidth;
  }

  public int framebufferHeight() {
    return framebufferHeight;
  }

  public int renderedVoxelChunks() {
    return renderedVoxelChunkSnapshot;
  }

  public long contextRebuilds() {
    return contextRebuilds;
  }

  public int lookEvents() {
    return lookEvents;
  }

  public String committedScoreText() {
    return committedScoreSnapshot;
  }

  public boolean isRuntimeOpen() {
    return runtime != null && !runtime.isClosed();
  }

  /** Called by instrumentation on the GL thread to exercise the same registered Java action. */
  public void invokeIncrementForInstrumentation() {
    if (runtime == null || runtime.isClosed())
      throw new IllegalStateException("Runtime is not open");
    runtime.eval("JavityGame.call('androidIncrement')");
  }

  /** Runs a full app-level resource teardown/recreation cycle on the libGDX owner thread. */
  public void recreateResourcesForInstrumentation() {
    pause();
    resume();
  }

  /** Physical screen coordinates for a real Android tap on the declarative React button. */
  public Vector2 incrementButtonSurfaceCenter() {
    requireOwnerThread();
    if (document == null) throw new IllegalStateException("UI document is not mounted");
    Actor button = document.actor("android-increment");
    if (button == null) throw new IllegalStateException("React increment button is missing");
    return button.localToScreenCoordinates(
        new Vector2(button.getWidth() * .5f, button.getHeight() * .5f));
  }

  /** Bounds in GL SurfaceView-local screen coordinates for measured instrumentation gestures. */
  public float[] actorSurfaceBounds(String actorName) {
    requireOwnerThread();
    if (stage == null) throw new IllegalStateException("Stage is not mounted");
    Actor actor = document == null ? null : document.actor(actorName);
    if (actor == null) actor = stage.getRoot().findActor(actorName);
    if (actor == null) throw new IllegalArgumentException("No fixture actor named " + actorName);
    Vector2 first = actor.localToScreenCoordinates(new Vector2(0f, 0f));
    Vector2 second =
        actor.localToScreenCoordinates(new Vector2(actor.getWidth(), actor.getHeight()));
    return new float[] {
      Math.min(first.x, second.x), Math.min(first.y, second.y),
      Math.max(first.x, second.x), Math.max(first.y, second.y)
    };
  }

  /**
   * Draws the existing world pass without/with its sample box and reads the same initial front-face
   * ROI before any SCREEN shapes or UI. No resources or model ownership are transferred.
   */
  public ModelPixelProof initialModelPixelProofForInstrumentation() {
    requireOwnerThread();
    if (!generationReady() || modelRenderer == null || !modelRenderer.isEnabled())
      throw new IllegalStateException("Sample model is not ready for its initial pixel proof");
    if (inputEventCount != 0 || lookEvents != 0)
      throw new IllegalStateException("Initial model pixel proof must precede touch input");
    Transform3D modelTransform = modelObject.getComponent(Transform3D.class);
    Vector3 frontCenter = new Vector3(modelTransform.position).add(0f, 0f, .8f);
    int width = Gdx.graphics.getBackBufferWidth();
    int height = Gdx.graphics.getBackBufferHeight();
    cameraComponent.camera.viewportWidth = width;
    cameraComponent.camera.viewportHeight = height;
    cameraComponent.camera.update();
    cameraComponent.camera.project(frontCenter, 0, 0, width, height);
    int sampleSize = 8;
    int x = Math.round(frontCenter.x) - sampleSize / 2;
    int y = Math.round(frontCenter.y) - sampleSize / 2;
    if (frontCenter.z <= 0f
        || frontCenter.z >= 1f
        || x < 0
        || y < 0
        || x + sampleSize > width
        || y + sampleSize > height)
      throw new IllegalStateException("Initial sample model ROI is outside the framebuffer");
    java.nio.IntBuffer state = com.badlogic.gdx.utils.BufferUtils.newIntBuffer(1);
    Gdx.gl.glGetIntegerv(GL20.GL_PACK_ALIGNMENT, state);
    int packAlignment = state.get(0);
    Gdx.gl.glGetIntegerv(GL20.GL_DEPTH_WRITEMASK, state);
    boolean depthMask = state.get(0) != 0;
    boolean depthTest = Gdx.gl.glIsEnabled(GL20.GL_DEPTH_TEST);
    boolean enabled = modelRenderer.isEnabled();
    try {
      Gdx.gl.glPixelStorei(GL20.GL_PACK_ALIGNMENT, 1);
      Gdx.gl.glDepthMask(true);
      modelRenderer.setEnabled(false);
      drawWorldOnlyForModelProof(width, height);
      int without = cyanPixels(x, y, sampleSize);
      modelRenderer.setEnabled(true);
      drawWorldOnlyForModelProof(width, height);
      int with = cyanPixels(x, y, sampleSize);
      return new ModelPixelProof(x, y, sampleSize, sampleSize, with, without);
    } finally {
      modelRenderer.setEnabled(enabled);
      Gdx.gl.glPixelStorei(GL20.GL_PACK_ALIGNMENT, packAlignment);
      Gdx.gl.glDepthMask(depthMask);
      if (depthTest) Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
      else Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    }
  }

  private void drawWorldOnlyForModelProof(int width, int height) {
    Gdx.gl.glViewport(0, 0, width, height);
    Gdx.gl.glClearColor(.055f, .095f, .14f, 1f);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    renderer.renderWorld(game, cameraComponent, null);
  }

  private static int cyanPixels(int x, int y, int size) {
    java.nio.ByteBuffer pixels = com.badlogic.gdx.utils.BufferUtils.newByteBuffer(size * size * 4);
    Gdx.gl.glReadPixels(x, y, size, size, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, pixels);
    int cyan = 0;
    for (int i = 0; i < size * size; i++) {
      int red = pixels.get(i * 4) & 255;
      int green = pixels.get(i * 4 + 1) & 255;
      int blue = pixels.get(i * 4 + 2) & 255;
      if (green > 70 && blue > 70 && green > red * 1.6f && blue > red * 1.6f) cyan++;
    }
    return cyan;
  }

  /**
   * Copied world-only framebuffer observations; all coordinates use bottom-left physical pixels.
   */
  public static final class ModelPixelProof {
    public final int x, y, width, height;
    public final int cyanPixelsWithModel, cyanPixelsWithoutModel;

    private ModelPixelProof(int x, int y, int width, int height, int with, int without) {
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
      this.cyanPixelsWithModel = with;
      this.cyanPixelsWithoutModel = without;
    }
  }

  /** Immutable diagnostics copied on the GDX owner thread; never exposes Stage/Actor/runtime. */
  public FixtureSnapshot diagnosticsSnapshot() {
    requireOwnerThread();
    return new FixtureSnapshot(
        resourcesReady,
        runtime != null && !runtime.isClosed(),
        densityScaleSnapshot,
        unitsPerPixel,
        logicalWidthSnapshot,
        logicalHeightSnapshot,
        renderedWidth,
        renderedHeight,
        framebufferWidth,
        framebufferHeight,
        renderedVoxelChunkSnapshot,
        modelObject == null ? 0 : modelObject.getComponents(ModelRenderer.class).size(),
        contextRebuilds,
        lookEvents,
        inputEventCount,
        completedFrames,
        lookPointer.ownsPointer(),
        region(sdfRoundedObject, -66f, -25f, 132f, 50f),
        region(sdfCircleObject, -20f, -20f, 40f, 40f),
        region(sdfDashedObject, 0f, -4.5f, 150f, 9f),
        committedScoreSnapshot,
        inputState.held(Input.Keys.W) || inputState.held(Input.Keys.S),
        inputState.held(Input.Keys.A) || inputState.held(Input.Keys.D),
        inputState.jumpHeld());
  }

  private void requireOwnerThread() {
    if (ownerThread != null && Thread.currentThread() != ownerThread)
      throw new IllegalStateException("Fixture diagnostics must be copied on the GDX owner thread");
  }

  private static GlesRegion region(
      GameObject object, float offsetX, float offsetY, float width, float height) {
    if (object == null) return null;
    return new GlesRegion(
        object.transform.position.x + offsetX * object.transform.scale.x,
        object.transform.position.y + offsetY * object.transform.scale.y,
        width * object.transform.scale.x,
        height * object.transform.scale.y);
  }

  public static final class FixtureSnapshot {
    public final boolean ready;
    public final boolean runtimeOpen;
    public final float density;
    public final float unitsPerPixel;
    public final int logicalWidth;
    public final int logicalHeight;
    public final int renderedWidth;
    public final int renderedHeight;
    public final int framebufferWidth;
    public final int framebufferHeight;

    /** Owned non-empty chunk models; this count is not proof of visible pixels. */
    public final int ownedVoxelChunkModels;

    public final int modelRendererComponents;
    public final long resourceGeneration;
    public final int lookEvents;
    public final long inputEventCount;
    public final long completedFrames;
    public final boolean lookPointerOwned;
    public final GlesRegion roundedSdfRegion;
    public final GlesRegion circleSdfRegion;
    public final GlesRegion dashedSdfRegion;
    public final String committedScore;
    public final boolean moving;
    public final boolean strafing;
    public final boolean jumping;

    private FixtureSnapshot(
        boolean ready,
        boolean runtimeOpen,
        float density,
        float unitsPerPixel,
        int logicalWidth,
        int logicalHeight,
        int renderedWidth,
        int renderedHeight,
        int framebufferWidth,
        int framebufferHeight,
        int ownedVoxelChunkModels,
        int modelRendererComponents,
        long resourceGeneration,
        int lookEvents,
        long inputEventCount,
        long completedFrames,
        boolean lookPointerOwned,
        GlesRegion roundedSdfRegion,
        GlesRegion circleSdfRegion,
        GlesRegion dashedSdfRegion,
        String committedScore,
        boolean moving,
        boolean strafing,
        boolean jumping) {
      this.ready = ready;
      this.runtimeOpen = runtimeOpen;
      this.density = density;
      this.unitsPerPixel = unitsPerPixel;
      this.logicalWidth = logicalWidth;
      this.logicalHeight = logicalHeight;
      this.renderedWidth = renderedWidth;
      this.renderedHeight = renderedHeight;
      this.framebufferWidth = framebufferWidth;
      this.framebufferHeight = framebufferHeight;
      this.ownedVoxelChunkModels = ownedVoxelChunkModels;
      this.modelRendererComponents = modelRendererComponents;
      this.resourceGeneration = resourceGeneration;
      this.lookEvents = lookEvents;
      this.inputEventCount = inputEventCount;
      this.completedFrames = completedFrames;
      this.lookPointerOwned = lookPointerOwned;
      this.roundedSdfRegion = roundedSdfRegion;
      this.circleSdfRegion = circleSdfRegion;
      this.dashedSdfRegion = dashedSdfRegion;
      this.committedScore = committedScore;
      this.moving = moving;
      this.strafing = strafing;
      this.jumping = jumping;
    }
  }

  /** Immutable geometric bounds in physical framebuffer pixels; AA can add a one-pixel fringe. */
  public static final class GlesRegion {
    public final float x;
    public final float y;
    public final float width;
    public final float height;

    private GlesRegion(float x, float y, float width, float height) {
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
    }
  }

  private static String readResource(String path) {
    try (InputStream input = AndroidGameFixture.class.getResourceAsStream(path)) {
      if (input == null)
        throw new IllegalStateException("Missing packaged fixture resource " + path);
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException failure) {
      throw new IllegalStateException("Could not read packaged fixture resource " + path, failure);
    }
  }

  private final class KeyboardControls extends InputAdapter {
    @Override
    public boolean keyDown(int keycode) {
      if (!focused || paused || lifecycleSuspended) return false;
      if (keycode == Input.Keys.W
          || keycode == Input.Keys.A
          || keycode == Input.Keys.S
          || keycode == Input.Keys.D) {
        inputState.keyboard(keycode, true);
        syncMovement();
        return true;
      }
      if (keycode == Input.Keys.SPACE) {
        inputState.keyboard(keycode, true);
        controller.setJump(inputState.jumpHeld());
        return true;
      }
      if (keycode == Input.Keys.ESCAPE) {
        togglePause();
        return true;
      }
      return false;
    }

    @Override
    public boolean keyUp(int keycode) {
      inputState.keyboard(keycode, false);
      if (keycode == Input.Keys.SPACE) controller.setJump(inputState.jumpHeld());
      syncMovement();
      return true;
    }
  }

  /**
   * Counts injected touch events before forwarding them to Stage, preserving Stage-first routing.
   */
  private final class TrackingInputMultiplexer extends InputMultiplexer {
    TrackingInputMultiplexer(Stage stage, KeyboardControls keyboard) {
      super(stage, keyboard);
    }

    @Override
    public boolean touchDown(int x, int y, int pointer, int button) {
      inputEventCount++;
      return super.touchDown(x, y, pointer, button);
    }

    @Override
    public boolean touchUp(int x, int y, int pointer, int button) {
      inputEventCount++;
      return super.touchUp(x, y, pointer, button);
    }

    @Override
    public boolean touchDragged(int x, int y, int pointer) {
      inputEventCount++;
      return super.touchDragged(x, y, pointer);
    }
  }

  private static Throwable closeResource(Object resource, Throwable failure) {
    if (resource == null) return failure;
    try {
      if (resource instanceof AutoCloseable closeable) closeable.close();
      else if (resource instanceof Disposable disposable) disposable.dispose();
    } catch (Exception | Error cleanupFailure) {
      return append(failure, cleanupFailure);
    }
    return failure;
  }

  private static Throwable append(Throwable first, Throwable next) {
    if (first == null) return next;
    if (first != next) first.addSuppressed(next);
    return first;
  }

  private static void rethrow(Throwable failure) {
    if (failure instanceof RuntimeException runtimeException) throw runtimeException;
    if (failure instanceof Error error) throw error;
    if (failure != null) throw new IllegalStateException("Android fixture cleanup failed", failure);
  }
}
