package org.javity.demo;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.javity.animation.AnimationMarker;
import org.javity.animation.AnimationTransition;
import org.javity.animation.LinearModelMixer;
import org.javity.animation.ModelAnimationLayer;
import org.javity.animation.ModelPoseAnimator;
import org.javity.animation.ModelPosePlayback;
import org.javity.animation.NodeMask;
import org.javity.animation.PlayMode;
import org.javity.audio.AudioBus;
import org.javity.audio.DuckHandle;
import org.javity.audio.SoundManager;
import org.javity.audio.VoiceHandle;
import org.javity.core.Component;
import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.core.Transform3D;
import org.javity.effects.CancelMode;
import org.javity.effects.Clock;
import org.javity.effects.EffectSystem;
import org.javity.effects.Feedback;
import org.javity.effects.FeedbackEffect;
import org.javity.effects.FeedbackPlayer;
import org.javity.effects.PlaybackHandle;
import org.javity.input.Pickable2D;
import org.javity.input.PointerPicker;
import org.javity.presentation.FloatingTextHandle;
import org.javity.presentation.FloatingTextPool;
import org.javity.presentation.PresentationEffects;
import org.javity.presentation.PresentationHandle;
import org.javity.presentation.ScreenEffects;
import org.javity.presentation.ScreenFlash;
import org.javity.render.Camera2D;
import org.javity.render.Camera3D;
import org.javity.render.GdxRenderer;
import org.javity.render.ModelRenderer;
import org.javity.render.ShapeRenderer2D;
import org.javity.render.SpriteRenderer2D;
import org.javity.script.ScriptUiReloader;
import org.javity.script.ScriptUiRuntime;
import org.javity.services.AssetSceneLoadJob;
import org.javity.services.AssetSpec;
import org.javity.services.LoadHandle;
import org.javity.services.LoadState;
import org.javity.services.SceneAssets;
import org.javity.services.SceneManager;
import org.javity.services.SceneTransition;
import org.javity.services.TimeLease;
import org.javity.services.TimeManager;
import org.javity.signals.Radio;
import org.javity.signals.Signal;
import org.javity.ui.controls.ProgressBar;
import org.javity.ui.controls.Slider;
import org.javity.ui.controls.Toggle;
import org.javity.ui.declarative.UiDocument;
import org.javity.ui.declarative.UiNode;
import org.javity.ui.flex.ImageFit;
import org.javity.ui.flex.UiImage;
import org.javity.ui.flex.UiTheme;

/** Compact playable integration of scene, audio, feedback, animation and React UI systems. */
public final class MmSystemsDemo extends ApplicationAdapter {
  private static final float STEP = 1f / 60f;
  private static final Color INK = Color.valueOf("#07111c");
  private static final Color FLASH = Color.valueOf("#b8fff0");
  private static final List<AssetSpec<?>> SCENE_ASSETS =
      List.of(
          new AssetSpec<>("runtime/tile.png", Texture.class),
          new AssetSpec<>("runtime/avatar.atlas", TextureAtlas.class),
          new AssetSpec<>("runtime/sound.wav", Sound.class),
          new AssetSpec<>("runtime/music.wav", Music.class));
  private static final String[] SCENE_SIGNAL_NAMES = {"health", "droneState", "canFire"};

  private final boolean smoke;
  private final String devPath;
  private GdxRenderer renderer;
  private ShapeRenderer transitionShape;
  private final Matrix4 transitionProjection = new Matrix4();
  private Stage stage;
  private UiTheme theme;
  private UiDocument document;
  private ScriptUiRuntime runtime;
  private ScriptUiReloader reloader;
  private Texture hostPortrait;
  private JsonValue mmFixture;
  private final Map<String, String> nativeHudStates = new LinkedHashMap<>();
  private Model skinModel;
  private SceneManager scenes;
  private Game hostGame;
  private Radio hostRadio;
  private Signal<Float> hostGeneration, hostVolume, hostLoadProgress;
  private Signal<Float> hostHealth;
  private Signal<Boolean> hostMuted, hostCanFire;
  private Signal<String> hostLoadStatus, hostDroneState;
  private Game currentScene;
  private RelayController currentController;
  private RelayController previousController;
  private AssetSceneLoadJob pendingJob;
  private AssetManager pendingAssetManager;
  private LoadHandle pendingLoad;
  private LoadHandle lastTerminalLoad;
  private LoadState lastTerminalState;
  private boolean loadCompleted, loadFailed;
  private Throwable loadFailure;
  private InputMultiplexer input;
  private int frames;
  private int generation = 0;
  private float volume = .7f;
  private boolean muted;
  private float devPollAccumulator;
  private boolean devPolledInitially;
  private int probeReleaseX;
  private int probeReleaseY;
  private int probeSampleX;
  private int probeSampleY;
  private Actor activeProbeActor;
  private String activeProbeOriginalClasses;
  private String activeProbeName;
  private int fireActionCalls, healActionCalls, volumeActionCalls, muteActionCalls;
  private int healthEvents, markerEvents, sequenceCueEvents, sequenceCompletions;
  private int feedbackClosed, customFeedbackStarted, customFeedbackUpdated;
  private int staleTextCancelled;
  private float expectedHealthAfterHit = Float.NaN;
  private float authoritativePilotX = 460;
  private String lastUiDevLine = "";
  private int uiReloads, uiReloadErrors;
  private int smokePhase;
  private int phaseFrames;
  private Actor retainedFire, retainedSlider;
  private Actor retainedHudRoot;
  private int retainedUiReloads;
  private Matrix4 hitBoneBefore;
  private final Vector2 pilotTransformAtImpact = new Vector2();
  private final Vector2 cameraTransformAtImpact = new Vector2();
  private Pixmap idleCapture, hitCapture;
  private Texture cancelledCandidateTexture;
  private Texture retainedLiveTexture;
  private int freshAttackBaseline;
  private final AtomicInteger textReuseCount = new AtomicInteger();
  private int cancelOrder;
  private int expectedCleanupMarker;
  private boolean sawFreezeWithUiAdvance;
  private int probeUiUpdates, probeFrozenScaleFrames;
  private int activeHitStopLeaseFrames;
  private float probeLastDelta, probeLastScale;
  private boolean sawPresentationWithoutTransformMutation;
  private boolean sawGainTrail, sawLossTrail;
  private boolean recordedCancel, recordedFailure, recordedSuccess;
  private boolean disposed;
  private boolean shutdownProbeArmed, shutdownLiveResourcesObserved, shutdownResourcesReleased;
  private boolean shutdownLoadCancelled,
      shutdownCandidateTextureDisposed,
      shutdownLiveTextureDisposed;
  private int shutdownLiveResourceCount, shutdownReleasedResourceCount;
  private Texture shutdownCandidateTexture, shutdownLiveTexture;
  private LoadHandle shutdownLoadHandle;
  private float finalSmokeHealth;
  private String finalSmokeHealthText = "", finalSmokeGenerationText = "";

  public MmSystemsDemo(boolean smoke, String devPath) {
    this.smoke = smoke;
    this.devPath = devPath;
  }

  @Override
  public void create() {
    try {
      createDemo();
    } catch (RuntimeException | Error failure) {
      try {
        dispose();
      } catch (RuntimeException | Error cleanup) {
        if (cleanup != failure) failure.addSuppressed(cleanup);
      }
      throw failure;
    }
  }

  private void createDemo() {
    mkdir("artifacts/mm-systems");
    renderer = new GdxRenderer();
    renderer.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    transitionShape = new ShapeRenderer();
    stage = new Stage(new ScreenViewport());
    theme = new UiTheme();
    hostPortrait = new Texture(Gdx.files.classpath("runtime/avatar.png"));
    mmFixture =
        new JsonReader().parse(readResource("/org/javity/demo/ui-reference/mm-fixture.json"));
    JsonValue pilotRegion =
        fixtureNode(mmFixture.get("tree"), "pilot-image").get("props").get("region");
    theme.registerImage(
        "pilot",
        new TextureRegion(
            hostPortrait,
            pilotRegion.getInt("x"),
            pilotRegion.getInt("y"),
            pilotRegion.getInt("width"),
            pilotRegion.getInt("height")));
    skinModel = GeneratedSkinModel.create();
    scenes = new SceneManager();
    hostGame = new Game();
    hostRadio = hostGame.addSystem(new Radio());
    hostGeneration = hostRadio.channel("sceneGeneration", Float.class, 0f);
    hostVolume = hostRadio.channel("volume", Float.class, volume);
    hostMuted = hostRadio.channel("muted", Boolean.class, muted);
    hostLoadProgress = hostRadio.channel("loadProgress", Float.class, 0f);
    hostLoadStatus = hostRadio.channel("loadStatus", String.class, "READY");
    hostHealth = hostRadio.channel("health", Float.class, 1f);
    hostDroneState = hostRadio.channel("droneState", String.class, "PATROL");
    hostCanFire = hostRadio.channel("canFire", Boolean.class, true);
    hostGame.start();

    document = new UiDocument(theme);
    document.mount(stage);
    document.root().setFillParent(true);
    runtime = new ScriptUiRuntime(document);
    runtime.bindNumberAction("volume", this::setVolume);
    runtime.bindBooleanAction("mute", this::setMuted);
    runtime.bindAction("fire", this::fire);
    runtime.bindAction("heal", this::heal);
    runtime.bindAction("deploy", this::deploy);
    runtime.bindAction("cancel", this::cancelDeploy);
    runtime.bindSignal("sceneGeneration", hostGeneration);
    runtime.bindSignal("health", hostHealth);
    runtime.bindSignal("droneState", hostDroneState);
    runtime.bindSignal("canFire", hostCanFire);
    runtime.bindSignal("volume", hostVolume);
    runtime.bindSignal("muted", hostMuted);
    runtime.bindSignal("loadProgress", hostLoadProgress);
    runtime.bindSignal("loadStatus", hostLoadStatus);
    String bundle =
        devPath == null ? readResource("/org/javity/script/javity-mm.js") : readPath(devPath);
    runtime.eval(bundle);
    if (devPath != null) reloader = new ScriptUiReloader(runtime, Path.of(devPath));
    if (smoke) assertImageRefreshContract();

    installInput();
    requestScene(false, false, null);
    Gdx.gl.glClearColor(INK.r, INK.g, INK.b, 1);
    if (smoke) Gdx.app.log("MmSystems", "starting deterministic Relay Ward smoke");
  }

  private void installInput() {
    InputProcessor world =
        new InputAdapter() {
          @Override
          public boolean keyDown(int keycode) {
            if (stage.getKeyboardFocus() != null) return false;
            if (keycode == Input.Keys.SPACE) {
              fire();
              return true;
            }
            if (keycode == Input.Keys.H) {
              heal();
              return true;
            }
            if (keycode == Input.Keys.D) {
              deploy();
              return true;
            }
            if (keycode == Input.Keys.ESCAPE) {
              cancelDeploy();
              return true;
            }
            return false;
          }

          @Override
          public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (button == Input.Buttons.LEFT
                && screenX >= 370
                && screenX < 1100
                && screenY >= 110
                && screenY < 625) {
              stage.setKeyboardFocus(null);
              return currentController != null
                  && currentController.pointerPicker.touchDown(screenX, screenY, pointer, button);
            }
            return false;
          }

          @Override
          public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            return currentController != null
                && currentController.pointerPicker.touchUp(screenX, screenY, pointer, button);
          }
        };
    input = new InputMultiplexer(stage, world);
    Gdx.input.setInputProcessor(input);
  }

  private void assertImageRefreshContract() {
    TextureRegion borrowed = new TextureRegion(hostPortrait, 0, 0, 32, 32);
    theme.registerImage("mm-refresh-probe", borrowed);
    AtomicReference<String> text = new AtomicReference<>("before");
    UiDocument probe = new UiDocument(theme);
    try {
      java.util.function.Supplier<String> textSupplier = text::get;
      UiNode label =
          UiNode.element(
              "Text",
              "probe-label",
              "",
              java.util.Map.of("text", textSupplier),
              java.util.List.of());
      UiNode image =
          UiNode.element(
              "Image",
              "probe-image",
              "",
              java.util.Map.of("source", "mm-refresh-probe", "fit", "contain"),
              java.util.List.of());
      probe.render(
          UiNode.element(
              "View", "probe-root", "", java.util.Map.of(), java.util.List.of(label, image)));
      Actor imageActor = probe.actor("probe-image");
      if (!(imageActor instanceof UiImage nativeImage)
          || nativeImage.borrowedRegion() != borrowed
          || nativeImage.fit() != ImageFit.CONTAIN)
        throw new IllegalStateException(
            "Image props did not resolve to the registered borrowed source");
      theme.unregisterImage("mm-refresh-probe");
      probe.refresh();
      if (probe.actor("probe-image") != imageActor || nativeImage.borrowedRegion() != borrowed)
        throw new IllegalStateException(
            "Refresh did not retain a mounted image after registry removal");
      text.set("after");
      UiNode invalidImage =
          UiNode.element(
              "Image",
              "probe-image",
              "",
              java.util.Map.of("source", "mm-unknown-source", "fit", "cover"),
              java.util.List.of());
      UiNode invalidTree =
          UiNode.element(
              "View", "probe-root", "", java.util.Map.of(), java.util.List.of(label, invalidImage));
      try {
        probe.render(invalidTree);
        throw new IllegalStateException("Unknown image source unexpectedly committed");
      } catch (IllegalArgumentException expected) {
        if (!expected.getMessage().contains("Unknown registered image source")) throw expected;
      }
      if (!"before".contentEquals(((Label) probe.actor("probe-label")).getText()))
        throw new IllegalStateException(
            "Invalid image replacement mutated unrelated text before validation");
      if (probe.actor("probe-image") != imageActor
          || nativeImage.borrowedRegion() != borrowed
          || nativeImage.fit() != ImageFit.CONTAIN)
        throw new IllegalStateException(
            "Invalid image replacement mutated the existing image actor");
    } finally {
      probe.close();
      theme.unregisterImage("mm-refresh-probe");
    }
  }

  private void requestScene(boolean arena, boolean fail, AssetManager supplied) {
    requestScene(arena, fail, supplied, new SceneTransition(.18, .18));
  }

  private void requestScene(
      boolean arena, boolean fail, AssetManager supplied, SceneTransition transition) {
    List<AssetSpec<?>> specs = new ArrayList<>(SCENE_ASSETS);
    if (fail) specs.add(new AssetSpec<>("runtime/missing-mm-scene.png", Texture.class));
    AssetManager manager = supplied == null ? new AssetManager() : supplied;
    AssetSceneLoadJob job =
        new AssetSceneLoadJob(specs, game -> configureScene(game, arena), 4, manager);
    pendingJob = job;
    pendingAssetManager = manager;
    loadCompleted = false;
    loadFailed = false;
    loadFailure = null;
    pendingLoad = scenes.requestLoading(job, transition);
    pendingLoad
        .onComplete(() -> loadCompleted = true)
        .onFailure(
            error -> {
              loadFailed = true;
              loadFailure = error;
            });
    publishLoad("LOADING", 0);
  }

  private void configureScene(Game game, boolean arena) {
    SceneAssets assets = game.getSystem(SceneAssets.class);
    Radio radio = game.addSystem(new Radio());
    Signal<Float> health = radio.channel("health", Float.class, 1f);
    Signal<String> droneState = radio.channel("droneState", String.class, "PATROL");
    Signal<Boolean> canFire = radio.channel("canFire", Boolean.class, true);
    TimeManager time = game.addSystem(new TimeManager());
    EffectSystem effects = game.addSystem(new EffectSystem());
    SoundManager sounds = game.addSystem(new SoundManager());
    sounds.setMasterVolume(volume);
    sounds.setBusVolume(AudioBus.SFX, muted ? 0 : 1);
    FloatingTextPool text =
        game.addSystem(new FloatingTextPool(theme.getFont(), Clock.WORLD, 8, 28));
    ScreenEffects screen =
        game.addSystem(new ScreenEffects(Gdx.graphics.getWidth(), Gdx.graphics.getHeight()));

    GameObject cameraObject = game.createObject("arena camera");
    cameraObject.setPosition(Gdx.graphics.getWidth() / 2f, Gdx.graphics.getHeight() / 2f);
    Camera2D camera = cameraObject.addComponent(new Camera2D());
    camera.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    PresentationEffects cameraShake = cameraObject.addComponent(new PresentationEffects(Clock.UI));
    Camera3D camera3 = cameraObject.addComponent(new Camera3D(50));
    camera3.camera.position.set(0, 0, 8);
    camera3.camera.lookAt(0, 0, 0);
    camera3.camera.near = .1f;
    camera3.camera.far = 40;

    Texture tile = assets.get("runtime/tile.png", Texture.class);
    TextureAtlas atlas = assets.get("runtime/avatar.atlas", TextureAtlas.class);
    Sound sound = assets.get("runtime/sound.wav", Sound.class);
    Music music = assets.get("runtime/music.wav", Music.class);
    GameObject floor = game.createObject("relay floor").setPosition(640, 260);
    ShapeRenderer2D floorShape = floor.addComponent(ShapeRenderer2D.rectangle(520, 6));
    floorShape.color.set(Color.valueOf(arena ? "#235365" : "#173d4c"));
    GameObject relay = game.createObject("relay beacon").setPosition(690, 375);
    ShapeRenderer2D relayShape = relay.addComponent(ShapeRenderer2D.rectangle(18, 112));
    relayShape.color.set(Color.valueOf("#20c9b5"));
    GameObject pilot = game.createObject("pilot").setPosition(470, 340);
    pilot.addComponent(new SpriteRenderer2D(atlas.findRegion("avatar", 0))).width = 64;
    pilot.getComponent(SpriteRenderer2D.class).height = 64;
    pilot.addComponent(new PresentationEffects(Clock.UI));
    cameraObject.addComponent(new FreezePresentationProbe(time, cameraObject, pilot));
    GameObject arenaInput = game.createObject("arena input").setPosition(735, 367);
    arenaInput.addComponent(new Pickable2D(730, 515, ignored -> MmSystemsDemo.this.fire()));

    GameObject dressing = game.createObject("arena tile accent").setPosition(975, 240);
    SpriteRenderer2D tileSprite =
        dressing.addComponent(new SpriteRenderer2D(new TextureRegion(tile)));
    tileSprite.width = 112;
    tileSprite.height = 64;
    tileSprite.tint.set(.2f, .55f, .62f, .3f);
    tileSprite.layer = -2;

    GameObject drone = game.createObject("training drone");
    Transform3D droneTransform = drone.addComponent(new Transform3D());
    droneTransform.position.set(0, 0, 0);
    droneTransform.scale.set(.68f, .68f, .68f);
    ModelRenderer model = drone.addComponent(new ModelRenderer(skinModel));
    ModelPoseAnimator animator = drone.addComponent(new ModelPoseAnimator());
    LinearModelMixer mixer =
        LinearModelMixer.builder(1.2).add("bend-left", 0).add("bend-right", 1).build();
    ModelPosePlayback locomotion = animator.play(mixer, PlayMode.LOOP, .42);
    ModelAnimationLayer upper = animator.addLayer("upper-action", NodeMask.paths("root/tip"));
    upper.setWeight(0);
    GameObject sequencerObject = game.createObject("relay sequence and feedback");
    var sequencer = sequencerObject.addComponent(new org.javity.sequence.Sequencer(Clock.WORLD));
    FeedbackPlayer feedbackPlayer = sequencerObject.addComponent(new FeedbackPlayer(Clock.UI));
    RelayController controller =
        sequencerObject.addComponent(
            new RelayController(
                arena,
                radio,
                health,
                droneState,
                canFire,
                assets,
                sounds,
                time,
                text,
                screen,
                cameraShake,
                upper,
                model,
                locomotion,
                sequencer,
                feedbackPlayer,
                sound,
                music,
                camera,
                cameraObject,
                pilot,
                droneTransform,
                game));
    // Kept for renderer discovery and explicit smoke diagnostics.
    if (effects == null || controller == null)
      throw new IllegalStateException("Scene services missing");
    if (arena) controller.setArenaLabel("ARENA");
  }

  private void adoptCurrentSceneIfChanged() {
    Game adopted = scenes.current();
    if (adopted != currentScene) {
      previousController = currentController;
      for (String name : SCENE_SIGNAL_NAMES) runtime.unbindSignal(name);
      currentScene = adopted;
      currentController = null;
      if (adopted != null) {
        Radio radio = adopted.getSystem(Radio.class);
        runtime.bindSignal("health", radio.channel("health", Float.class, 1f));
        runtime.bindSignal("droneState", radio.channel("droneState", String.class, "PATROL"));
        runtime.bindSignal("canFire", radio.channel("canFire", Boolean.class, true));
        currentController =
            adopted.getObjects().stream()
                .map(object -> object.getComponent(RelayController.class))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseThrow();
        currentController.applySettings(volume, muted);
        generation++;
        hostGeneration.set((float) generation);
        publishLoad("READY", 0);
      }
    }
    if (pendingLoad != null) {
      if (loadCompleted) {
        publishLoad("COMPLETE", 1);
        if (currentScene != null && currentScene == scenes.current()) recordedSuccess = true;
        lastTerminalLoad = pendingLoad;
        lastTerminalState = pendingLoad.state();
        pendingLoad = null;
        pendingJob = null;
        pendingAssetManager = null;
      } else if (loadFailed) {
        publishLoad("FAILED", 0);
        if (smoke) recordedFailure = true;
        lastTerminalLoad = pendingLoad;
        lastTerminalState = pendingLoad.state();
        pendingLoad = null;
        pendingJob = null;
        pendingAssetManager = null;
      } else if (pendingLoad.state() == LoadState.CANCELLED) {
        publishLoad("CANCELLED", 0);
        if (smoke) recordedCancel = true;
        lastTerminalLoad = pendingLoad;
        lastTerminalState = pendingLoad.state();
        pendingLoad = null;
        pendingJob = null;
        pendingAssetManager = null;
      } else {
        publishLoad(pendingLoad.state().name(), (float) pendingLoad.progress());
      }
    }
  }

  private void publishLoad(String status, float progress) {
    hostLoadStatus.set(status);
    hostLoadProgress.set(Math.max(0, Math.min(1, progress)));
  }

  private void setVolume(double value) {
    volumeActionCalls++;
    volume = (float) Math.max(0, Math.min(1, value));
    hostVolume.set(volume);
    if (currentController != null) currentController.applySettings(volume, muted);
  }

  private void setMuted(boolean value) {
    muteActionCalls++;
    muted = value;
    hostMuted.set(muted);
    if (currentController != null) currentController.applySettings(volume, muted);
  }

  private void fire() {
    fireActionCalls++;
    RelayController controller = currentController;
    if (controller != null) controller.fire();
  }

  private void heal() {
    healActionCalls++;
    RelayController controller = currentController;
    if (controller != null) controller.heal();
  }

  private void deploy() {
    if (pendingLoad != null) return;
    RelayController controller = currentController;
    boolean arena = controller == null || !controller.arena;
    requestScene(arena, false, null);
  }

  private void cancelDeploy() {
    if (pendingLoad != null) pendingLoad.cancel();
  }

  @Override
  public void render() {
    Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    Gdx.gl.glClearColor(INK.r, INK.g, INK.b, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    float delta = smoke ? STEP : Gdx.graphics.getDeltaTime();
    scenes.step(delta);
    adoptCurrentSceneIfChanged();
    if (runtime != null) runtime.tick(delta);
    if (devPath != null) {
      if (!devPolledInitially) {
        devPolledInitially = true;
        pollDevBundle();
      } else {
        devPollAccumulator += delta;
        if (devPollAccumulator >= .25f) {
          devPollAccumulator %= .25f;
          pollDevBundle();
        }
      }
    }
    if (currentScene != null) renderer.render(currentScene);
    drawTransitionOverlay();
    document.refresh();
    if (smoke && activeProbeActor != null) {
      String probeClasses = activeProbeOriginalClasses + " active:bg-red-500 focus:bg-cyan-400";
      if (activeProbeActor instanceof Slider slider) slider.setStyleClasses(probeClasses);
      else if (activeProbeActor instanceof Toggle toggle) toggle.setStyleClasses(probeClasses);
    }
    stage.act(delta);
    stage.draw();
    frames++;
    if (smoke) smokeStep();
  }

  private void drawTransitionOverlay() {
    float alpha = scenes == null ? 0 : (float) scenes.overlayAlpha();
    if (alpha <= 0) return;
    transitionProjection.setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    transitionShape.setProjectionMatrix(transitionProjection);
    Gdx.gl.glEnable(GL20.GL_BLEND);
    Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    transitionShape.begin(ShapeRenderer.ShapeType.Filled);
    transitionShape.setColor(INK.r, INK.g, INK.b, alpha);
    transitionShape.rect(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    transitionShape.end();
    Gdx.gl.glDisable(GL20.GL_BLEND);
  }

  private void pollDevBundle() {
    String rootBefore = actorIdentity("dev-hud", "mm-root");
    ScriptUiReloader.PollResult result = reloader.poll();
    if (result == ScriptUiReloader.PollResult.RELOADED) uiReloads++;
    if (result == ScriptUiReloader.PollResult.ERROR) uiReloadErrors++;
    if (result == ScriptUiReloader.PollResult.RELOADED
        || result == ScriptUiReloader.PollResult.ERROR) {
      String rootAfter = actorIdentity("dev-hud", "mm-root");
      String line =
          "{\"poll\":\""
              + result
              + "\",\"reloads\":"
              + uiReloads
              + ",\"errors\":"
              + uiReloadErrors
              + ",\"rootBefore\":\""
              + rootBefore
              + "\",\"rootAfter\":\""
              + rootAfter
              + "\",\"retainedRoot\":"
              + rootBefore.equals(rootAfter)
              + ",\"health\":"
              + jsonNumber(currentController == null ? 0 : currentController.health.get())
              + ",\"generation\":"
              + generation
              + ",\"renderedHealth\":\""
              + jsonEscape(actorText("dev-health"))
              + "\",\"renderedGeneration\":\""
              + jsonEscape(actorText("dev-generation"))
              + "\",\"error\":\""
              + jsonEscape(reloader.lastError().orElse(""))
              + "\"}";
      if (!line.equals(lastUiDevLine)) {
        Gdx.app.log("MM_UI_DEV", line);
        System.out.println("MM_UI_DEV " + line);
        lastUiDevLine = line;
      }
    }
  }

  private String actorIdentity(String... keys) {
    for (String key : keys) {
      Actor actor = document.actor(key);
      if (actor != null) return Integer.toHexString(System.identityHashCode(actor));
    }
    return "none";
  }

  private String actorText(String key) {
    Actor actor = document.actor(key);
    return actor instanceof Label label ? label.getText().toString() : "";
  }

  private Actor hudRootActor() {
    return document.actor(devPath == null ? "mm-root" : "dev-hud");
  }

  private String sceneId(String id) {
    if (devPath == null) return id;
    return switch (id) {
      case "fire-button" -> "dev-fire";
      case "health-bar" -> "dev-load-progress";
      case "mute-toggle" -> "dev-muted";
      case "volume-slider" -> "dev-volume";
      default -> id;
    };
  }

  private String hudText(String id) {
    return actorText(
        devPath == null
            ? sceneId(id)
            : (id.equals("health-number") ? "dev-health" : "dev-generation"));
  }

  private float renderedDevHealth() {
    String text = actorText("dev-health");
    java.util.regex.Matcher matcher =
        java.util.regex.Pattern.compile("[-+]?(?:\\d+\\.?\\d*|\\.\\d+)").matcher(text);
    if (!matcher.find()) return Float.NaN;
    try {
      return Float.parseFloat(matcher.group());
    } catch (NumberFormatException invalid) {
      return Float.NaN;
    }
  }

  private void clickArenaTarget() {
    Vector2 point = currentController.camera.worldToScreen(735, 367);
    int x = Math.round(point.x), y = Math.round(point.y);
    int before = fireActionCalls;
    if (!input.touchDown(x, y, 0, Input.Buttons.LEFT)
        || !input.touchUp(x, y, 0, Input.Buttons.LEFT)
        || fireActionCalls != before + 1)
      failSmoke("real arena click did not clear HUD focus and fire through the InputMultiplexer");
  }

  private static String jsonEscape(String text) {
    return text.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r");
  }

  private static String jsonNumber(float value) {
    return Float.isFinite(value) ? String.format(Locale.ROOT, "%.4f", value) : "null";
  }

  private void smokeStep() {
    try {
      switch (smokePhase) {
        case 0 -> {
          if (currentScene != null
              && pendingLoad == null
              && Math.abs(scenes.overlayAlpha()) < .001) {
            assertCurrentScene();
            if (devPath == null)
              ((ProgressBar) document.actor(sceneId("health-bar"))).finishAnimation();
            smokePhase = 10;
          }
        }
        case 10 -> {
          if (currentScene != null
              && pendingLoad == null
              && Math.abs(scenes.overlayAlpha()) < .001) {
            captureHudState("idle", "01-idle.png");
            idleCapture = capturePixmap();
            retainedFire = document.actor(sceneId("fire-button"));
            retainedSlider = document.actor(sceneId("volume-slider"));
            assertSignalsAndActors();
            Vector2 pickPoint = currentController.camera.worldToScreen(735, 367);
            int actionBeforePick = fireActionCalls;
            if (!currentController.pointerPicker.touchDown(
                    Math.round(pickPoint.x), Math.round(pickPoint.y), 0, Input.Buttons.LEFT)
                || !currentController.pointerPicker.touchUp(
                    Math.round(pickPoint.x), Math.round(pickPoint.y), 0, Input.Buttons.LEFT)
                || fireActionCalls != actionBeforePick + 1)
              failSmoke("real Camera2D-to-PointerPicker path did not hit the arena target");
            injectKey(Input.Keys.SPACE);
            smokePhase = 1;
            phaseFrames = 0;
          }
        }
        case 1 -> {
          phaseFrames++;
          if (currentController.markerEvents > 0) {
            hitCapture = capturePixmap();
            assertImpactFrame();
            writePng("02-hit.png", hitCapture);
            smokePhase = 2;
            phaseFrames = 0;
          } else if (phaseFrames > 150)
            failSmoke("impact marker did not fire within 150 fixed steps");
        }
        case 2 -> {
          phaseFrames++;
          if (!sawFreezeWithUiAdvance && phaseFrames > 30)
            failSmoke(
                "WORLD hit-stop did not preserve UI-clock advancement (uiUpdates="
                    + probeUiUpdates
                    + ", frozen="
                    + probeFrozenScaleFrames
                    + ", lease="
                    + activeHitStopLeaseFrames
                    + ", delta="
                    + probeLastDelta
                    + ", scale="
                    + probeLastScale
                    + ")");
          if (phaseFrames == 22) assertDelayedHitSystems();
          if (phaseFrames == 15) {
            if (devPath == null) {
              sawLossTrail = verifyProgressTrail(false);
              captureHudState("hit", "03-hit-health-trail.png");
            }
          }
          if (phaseFrames >= 30) {
            heal();
            smokePhase = 3;
            phaseFrames = 0;
          }
        }
        case 3 -> {
          phaseFrames++;
          if (phaseFrames == 18) {
            if (devPath == null) {
              sawGainTrail = verifyProgressTrail(true);
              capture("04-heal-health-trail.png");
            }
          }
          if (phaseFrames >= 25) {
            driveSettingsKeyboard();
            if (devPath == null)
              ((ProgressBar) document.actor(sceneId("health-bar"))).finishAnimation();
            smokePhase = 11;
            phaseFrames = 0;
          }
        }
        case 11 -> {
          phaseFrames++;
          if (phaseFrames >= 1) {
            if (devPath == null) {
              captureHudState("settings", "05-settings.png");
              beginControlStateProbe("volume-slider", "slider");
              smokePhase = 12;
            } else {
              smokePhase = 4;
            }
            phaseFrames = 0;
          }
        }
        case 12 -> {
          phaseFrames++;
          if (phaseFrames >= 1) {
            assertControlStateProbe("#fb2c36");
            capture("09-slider-pressed.png");
            releaseControlStateProbe();
            smokePhase = 13;
            phaseFrames = 0;
          }
        }
        case 13 -> {
          phaseFrames++;
          if (phaseFrames >= 1) {
            assertControlStateProbe("#00d3f3");
            capture("10-slider-focused.png");
            restoreControlStateProbe();
            beginControlStateProbe("mute-toggle", "toggle");
            smokePhase = 14;
            phaseFrames = 0;
          }
        }
        case 14 -> {
          phaseFrames++;
          if (phaseFrames >= 1) {
            assertControlStateProbe("#fb2c36");
            capture("11-toggle-pressed.png");
            releaseControlStateProbe();
            smokePhase = 15;
            phaseFrames = 0;
          }
        }
        case 15 -> {
          phaseFrames++;
          if (phaseFrames >= 1) {
            assertControlStateProbe("#00d3f3");
            capture("12-toggle-focused.png");
            restoreControlStateProbe();
            smokePhase = 4;
            phaseFrames = 0;
          }
        }
        case 4 -> {
          phaseFrames++;
          if (phaseFrames == 2) {
            AssetManager candidateAssets = new AssetManager();
            requestScene(true, false, candidateAssets);
            smokePhase = 5;
            phaseFrames = 0;
          }
        }
        case 5 -> {
          phaseFrames++;
          if (pendingJob != null
              && pendingLoad != null
              && pendingJob.isReady()
              && pendingLoad.state() == LoadState.FADING_OUT
              && pendingLoad.progress() > .7) {
            cancelledCandidateTexture = pendingJob.assets().get("runtime/tile.png", Texture.class);
            retainedLiveTexture =
                currentController.sceneAssets.get("runtime/tile.png", Texture.class);
            if (cancelledCandidateTexture.getTextureObjectHandle() == 0
                || retainedLiveTexture.getTextureObjectHandle() == 0)
              failSmoke("loaded candidate/live textures have no native GL handles");
            captureHudState("loading", "06-loading-cancel.png");
            pendingLoad.cancel();
            if (scenes.current() != currentScene || currentScene.isDisposed())
              failSmoke("cancel discarded live scene");
            if (pendingAssetManager.getAssetNames().size != 0)
              failSmoke("cancelled candidate AssetManager retained assets");
            if (cancelledCandidateTexture.getTextureObjectHandle() != 0
                || retainedLiveTexture.getTextureObjectHandle() == 0)
              failSmoke(
                  "cancel did not dispose candidate resources while preserving live resources");
            if (currentController == null || currentController.health.isClosed())
              failSmoke("cancel removed old scene health");
            smokePhase = 6;
            phaseFrames = 0;
          } else if (phaseFrames > 240) failSmoke("candidate did not reach cancellable FADING_OUT");
        }
        case 6 -> {
          phaseFrames++;
          if (phaseFrames > 240) failSmoke("cancel transition did not settle");
          if (pendingLoad == null && Math.abs(scenes.overlayAlpha()) < .001) {
            if (currentScene == null || currentScene.isDisposed())
              failSmoke("cancelled old scene not retained");
            // Capture the React/native actor boundary after any development-bundle reloads
            // that happened during the earlier gameplay phases.
            retainedFire = document.actor(sceneId("fire-button"));
            retainedSlider = document.actor(sceneId("volume-slider"));
            retainedHudRoot = hudRootActor();
            retainedUiReloads = uiReloads;
            if (retainedFire == null
                || retainedSlider == null
                || retainedHudRoot == null
                || retainedFire.getStage() == null
                || retainedSlider.getStage() == null
                || retainedHudRoot.getStage() == null)
              failSmoke("React HUD actors were not mounted at the scene deploy boundary");
            requestScene(true, false, null);
            smokePhase = 7;
            phaseFrames = 0;
          }
        }
        case 7 -> {
          phaseFrames++;
          if (currentScene != null
              && currentController != null
              && currentController.arena
              && pendingLoad == null
              && Math.abs(scenes.overlayAlpha()) < .001
              && hostLoadStatus.get().equals("COMPLETE")) {
            if (!recordedSuccess) failSmoke("scene success callback not observed");
            if (currentController == null || currentController.health.isClosed())
              failSmoke("new scene radio not rebound");
            if (Math.abs(currentController.soundManager.getMasterVolume() - volume) > .001f
                || (currentController.soundManager.getBusVolume(AudioBus.SFX) == 0) != muted)
              failSmoke("host audio settings did not carry to the new scene");
            Actor currentFire = document.actor(sceneId("fire-button"));
            Actor currentSlider = document.actor(sceneId("volume-slider"));
            Actor currentHudRoot = hudRootActor();
            if (uiReloads == retainedUiReloads) {
              if (retainedFire != currentFire || retainedSlider != currentSlider)
                failSmoke(
                    "sceneGeneration actor identity mismatch without a valid UI reload: fireReused="
                        + (retainedFire == currentFire)
                        + ", sliderReused="
                        + (retainedSlider == currentSlider));
            } else if (retainedFire.getStage() != null
                || retainedSlider.getStage() != null
                || retainedHudRoot.getStage() != null
                || currentFire == null
                || currentSlider == null
                || currentHudRoot == null
                || currentFire.getStage() == null
                || currentSlider.getStage() == null
                || currentHudRoot.getStage() == null) {
              failSmoke(
                  "valid UI reload did not detach old root and mount live replacement actors");
            }
            if (previousController == null || !previousController.health.isClosed())
              failSmoke("old scene radio remained live after successful handoff");
            String healthText = hudText("health-number");
            String generationText = hudText("mm-generation");
            boolean renderedAtFullHealth =
                devPath == null
                    ? healthText.contains("100")
                    : Math.abs(renderedDevHealth() - 1f) < .001f;
            boolean renderedGeneration =
                devPath == null
                    ? generationText.contains(Integer.toString(generation))
                    : generationText.contains(Integer.toString(generation));
            if (!renderedAtFullHealth || !renderedGeneration)
              failSmoke("compiled production HUD did not render rebound health/generation signals");
            captureHudState("arena", "07-arena.png");
            freshAttackBaseline = currentController.markerEvents;
            clickArenaTarget();
            smokePhase = 8;
            phaseFrames = 0;
          } else if (phaseFrames > 240) failSmoke("arena scene did not adopt");
        }
        case 8 -> {
          phaseFrames++;
          if (phaseFrames > 240) failSmoke("fresh scene attack did not reach impact marker");
          if (currentController != null && currentController.markerEvents > freshAttackBaseline) {
            if (Math.abs(currentController.health.get() - .65f) > .01f
                || !(devPath == null
                    ? hudText("health-number").contains("65")
                    : Math.abs(renderedDevHealth() - .65f) < .01f)
                || !hudText("mm-generation").contains(Integer.toString(generation)))
              failSmoke("fresh scene attack did not update actual compiled HUD health/generation");
            AssetManager failureAssets = new AssetManager();
            requestScene(false, true, failureAssets);
            smokePhase = 9;
            phaseFrames = 0;
          }
        }
        case 9 -> {
          phaseFrames++;
          if (loadFailed) {
            if (loadFailure == null
                || lastTerminalLoad == null
                || lastTerminalState != LoadState.FAILED
                || lastTerminalLoad.state() != LoadState.FAILED)
              failSmoke("missing asset did not surface a failed LoadHandle");
            if (scenes.current() != currentScene
                || currentScene.isDisposed()
                || scenes.overlayAlpha() != 0)
              failSmoke("failed load replaced the live scene or left fade overlay");
            capture("08-failed-load.png");
            finishSmoke();
          } else if (phaseFrames > 300)
            failSmoke("missing asset did not fail within bounded frames");
        }
        case 16 -> {
          phaseFrames++;
          if (pendingLoad != null
              && pendingJob != null
              && pendingJob.isReady()
              && pendingLoad.state() == LoadState.FADING_OUT
              && currentController != null
              && currentController.closeProbeResourcesAreLive()) {
            shutdownLoadHandle = pendingLoad;
            shutdownCandidateTexture = pendingJob.assets().get("runtime/tile.png", Texture.class);
            shutdownLiveTexture =
                currentController.sceneAssets.get("runtime/tile.png", Texture.class);
            if (shutdownCandidateTexture.getTextureObjectHandle() == 0
                || shutdownLiveTexture.getTextureObjectHandle() == 0)
              failSmoke("shutdown probe did not retain live candidate and current scene textures");
            finalSmokeHealth = currentController.health.get();
            finalSmokeHealthText = hudText("health-number");
            finalSmokeGenerationText = hudText("mm-generation");
            writeNativeHudGeometry();
            shutdownProbeArmed = true;
            Gdx.app.exit();
          } else if (phaseFrames > 180) {
            failSmoke(
                "shutdown probe did not reach active resources plus cancellable loaded scene");
          }
        }
        default -> {}
      }
    } catch (RuntimeException | Error failure) {
      writeFailureCapture(failure);
      throw failure;
    }
  }

  private void assertCurrentScene() {
    if (currentScene == null || currentScene.isDisposed() || currentController == null)
      failSmoke("initial scene did not start");
    if (currentController.sceneAssets.get("runtime/tile.png", Texture.class) == null
        || currentController.soundManager == null
        || currentController.musicVoice == null) failSmoke("scene assets/audio were not loaded");
    if (!currentController.musicVoice.isActive()) failSmoke("native music voice was not acquired");
    if (currentController.locomotion == null || currentController.upperLayer == null)
      failSmoke("pose mixer/layer was not created");
  }

  private void assertSignalsAndActors() {
    if (Math.abs(currentController.health.get() - 1) > .001f || healthEvents != 0)
      failSmoke("initial health signal was not stable");
    if (retainedFire == null || retainedSlider == null) failSmoke("React HUD controls missing");
    if (runtime == null || runtime.isClosed()) failSmoke("React UI runtime not live");
  }

  private void assertImpactFrame() {
    if (currentController.health.get() > .66f || currentController.health.get() < .64f)
      failSmoke("impact did not apply exactly 35 damage");
    if (currentController.markerEvents != 1 || markerEvents != 1)
      failSmoke("impact marker did not fire exactly once");
    if (currentController.impactVoice == null || !currentController.impactVoice.isActive())
      failSmoke("impact voice was not owned by a native voice lease");
    if (!currentController.duckActiveAtImpact)
      failSmoke("music duck lease was not active at impact");
    if (currentController.textHandle == null || !currentController.textHandle.isActive())
      failSmoke("damage floating text was not pooled");
    if (currentController.hitStopLease == null || !currentController.hitStopLease.isActive())
      failSmoke("impact did not acquire an active WORLD hit-stop lease");
    Matrix4 bone = skinBone(currentController.skinRenderer);
    if (hitBoneBefore == null || matrixDelta(hitBoneBefore, bone) < .02f)
      failSmoke("masked attack did not change the real skin bone matrix");
    int changed = differingPixels(idleCapture, hitCapture, 420, 860, 170, 570);
    if (changed < 500)
      failSmoke("scene framebuffer did not show visible hit/pose change: " + changed);
  }

  private void assertDelayedHitSystems() {
    if (currentController.sequenceCueEvents < 2 || sequenceCueEvents < 2)
      failSmoke("sequencer cues did not run");
    if (currentController.sequenceCompletions < 1 || sequenceCompletions < 1)
      failSmoke("sequence completion did not run");
    if (!sawFreezeWithUiAdvance || probeFrozenScaleFrames == 0 || activeHitStopLeaseFrames == 0)
      failSmoke("WORLD hit-stop did not preserve UI-clock advancement");
    if (!sawPresentationWithoutTransformMutation)
      failSmoke("presentation changed authoritative transform or was not rendered");
    if (currentController.feedbackStarted < 1
        || !currentController.feedbackResourceAcquired
        || currentController.feedbackVoice == null
        || currentController.feedbackVoice.isActive())
      failSmoke("feedback did not acquire and close its owned native audio voice");
    if (customFeedbackStarted < 1 || customFeedbackUpdated < 1)
      failSmoke("custom feedback did not receive start/update callbacks");
  }

  private void driveSettingsKeyboard() {
    Actor fireBefore = document.actor(sceneId("fire-button"));
    Actor sliderBefore = document.actor(sceneId("volume-slider"));
    for (int i = 0; i < 8 && stage.getKeyboardFocus() != sliderBefore; i++)
      injectKey(Input.Keys.TAB);
    if (stage.getKeyboardFocus() != sliderBefore)
      failSmoke("Tab navigation did not focus volume slider");
    float oldVolume = volume;
    for (int i = 0; i < 7; i++) injectKey(Input.Keys.LEFT);
    if (Math.abs(volume - .35f) > .001f || volumeActionCalls < 1 || !(volume < oldVolume))
      failSmoke("focused slider did not reach the declared 35% settings state");
    Actor toggle = document.actor(sceneId("mute-toggle"));
    for (int i = 0; i < 8 && stage.getKeyboardFocus() != toggle; i++) injectKey(Input.Keys.TAB);
    if (stage.getKeyboardFocus() != toggle) failSmoke("keyboard focus did not reach mute toggle");
    int beforeCalls = muteActionCalls;
    injectKey(Input.Keys.SPACE);
    injectKeyUp(Input.Keys.SPACE);
    if (muteActionCalls != beforeCalls + 1 || !muted)
      failSmoke("focused Toggle did not call typed mute action once");
    for (int i = 0; i < 8 && stage.getKeyboardFocus() != sliderBefore; i++)
      injectKey(Input.Keys.TAB);
    injectKeyUp(Input.Keys.TAB);
    if (stage.getKeyboardFocus() != sliderBefore)
      failSmoke("Tab did not return keyboard focus to the slider");
    if (document.actor(sceneId("fire-button")) != fireBefore
        || document.actor(sceneId("volume-slider")) != sliderBefore)
      failSmoke("keyed controls were replaced by signal updates");
    if (Math.abs(currentController.soundManager.getMasterVolume() - volume) > .001f
        || currentController.soundManager.getBusVolume(AudioBus.SFX) != 0)
      failSmoke("audio settings did not update current buses");
  }

  private void beginControlStateProbe(String id, String kind) {
    activeProbeActor = document.actor(id);
    if (activeProbeActor == null) failSmoke("missing control for active/focus probe: " + id);
    JsonValue node = fixtureNode(mmFixture.get("tree"), id);
    if (node == null) failSmoke("missing fixture style for active/focus probe: " + id);
    activeProbeOriginalClasses = node.getString("classes", "");
    activeProbeName = kind;
    String probeClasses = activeProbeOriginalClasses + " active:bg-red-500 focus:bg-cyan-400";
    Vector2 local = new Vector2();
    if (activeProbeActor instanceof Slider slider) {
      slider.setStyleClasses(probeClasses);
      local.set(Math.max(20, volume * slider.getWidth()), slider.getHeight() / 2f);
      probeSampleX =
          Math.round(
              activeProbeActor.localToStageCoordinates(
                      new Vector2(
                          Math.max(20, volume * slider.getWidth() - 35), slider.getHeight() / 2f))
                  .x);
      probeSampleY =
          Gdx.graphics.getHeight()
              - Math.round(
                  activeProbeActor.localToStageCoordinates(
                          new Vector2(
                              Math.max(20, volume * slider.getWidth() - 35),
                              slider.getHeight() / 2f))
                      .y);
    } else if (activeProbeActor instanceof Toggle toggle) {
      toggle.setStyleClasses(probeClasses);
      local.set(toggle.getWidth() / 2f, toggle.getHeight() / 2f);
      probeSampleX =
          Math.round(
              activeProbeActor.localToStageCoordinates(
                      new Vector2(toggle.getWidth() / 2f - 6, toggle.getHeight() / 2f))
                  .x);
      probeSampleY =
          Gdx.graphics.getHeight()
              - Math.round(
                  activeProbeActor.localToStageCoordinates(
                          new Vector2(toggle.getWidth() / 2f - 6, toggle.getHeight() / 2f))
                      .y);
    } else {
      failSmoke(
          "unexpected active/focus probe actor type: " + activeProbeActor.getClass().getName());
    }
    Vector2 stagePoint = activeProbeActor.localToStageCoordinates(local);
    int screenX = Math.round(stagePoint.x);
    int screenY = Gdx.graphics.getHeight() - Math.round(stagePoint.y);
    probeReleaseX = screenX + Math.round(activeProbeActor.getWidth()) + 20;
    probeReleaseY = screenY;
    if (!input.touchDown(screenX, screenY, 0, Input.Buttons.LEFT))
      failSmoke(kind + " active-state probe pointerDown was not consumed");
    if (stage.getKeyboardFocus() != activeProbeActor)
      failSmoke(kind + " pointerDown did not also assign keyboard focus");
  }

  private void assertControlStateProbe(String expectedColor) {
    if (activeProbeActor == null || stage.getKeyboardFocus() != activeProbeActor)
      failSmoke(activeProbeName + " active/focus probe lost its keyboard focus");
    Pixmap pixels = capturePixmap();
    try {
      int actual = rgb(pixels, probeSampleX, probeSampleY);
      int expected = colorRgb(expectedColor);
      if (maxRgbDelta(actual, expected) > 8)
        failSmoke(
            activeProbeName + " state color expected=" + expectedColor + " actual=" + hex(actual));
    } finally {
      pixels.dispose();
    }
  }

  private void releaseControlStateProbe() {
    if (!input.touchUp(probeReleaseX, probeReleaseY, 0, Input.Buttons.LEFT))
      failSmoke(activeProbeName + " active-state probe pointerUp was not consumed");
    if (stage.getKeyboardFocus() != activeProbeActor)
      failSmoke(activeProbeName + " outside pointerUp unexpectedly removed keyboard focus");
  }

  private void restoreControlStateProbe() {
    if (activeProbeActor instanceof Slider slider)
      slider.setStyleClasses(activeProbeOriginalClasses);
    else if (activeProbeActor instanceof Toggle toggle)
      toggle.setStyleClasses(activeProbeOriginalClasses);
    activeProbeActor = null;
    activeProbeOriginalClasses = null;
    activeProbeName = null;
  }

  private boolean verifyProgressTrail(boolean gain) {
    Actor actor = document.actor(sceneId("health-bar"));
    if (!(actor instanceof ProgressBar)) failSmoke("health actor is not a ProgressBar");
    ProgressBar bar = (ProgressBar) actor;
    float foreground = bar.getDisplayedValue();
    float delayed = gain ? bar.getDelayedIncrease() : bar.getDelayedDecrease();
    if (!(delayed - foreground > .015f)) return false;
    Pixmap pixels = capturePixmap();
    try {
      Vector2 origin = bar.localToStageCoordinates(new Vector2());
      float width = Math.max(1, bar.getWidth() - 4);
      float innerX = origin.x + 2;
      int xForeground = Math.round(innerX + width * foreground * .5f);
      int xTrail = Math.round(innerX + width * (foreground + delayed) * .5f);
      int y = Math.round(Gdx.graphics.getHeight() - origin.y - bar.getHeight() / 2f);
      int front = rgb(pixels, xForeground, y);
      int trail = rgb(pixels, xTrail, y);
      int expectedFront = colorRgb("#00d3f3");
      int expectedTrail = colorRgb(gain ? "#00bc7d" : "#ffb900");
      if (maxRgbDelta(front, expectedFront) > 8 || maxRgbDelta(trail, expectedTrail) > 8)
        failSmoke(
            "ProgressBar fill colors failed: foreground=" + hex(front) + " trail=" + hex(trail));
      return true;
    } finally {
      pixels.dispose();
    }
  }

  private void injectKey(int key) {
    InputProcessor processor = Gdx.input.getInputProcessor();
    if (processor == null || !processor.keyDown(key)) {
      if (key != Input.Keys.TAB && key != Input.Keys.SHIFT_LEFT)
        throw new IllegalStateException("Installed input did not accept key " + key);
    }
    if (processor != null) processor.keyUp(key);
  }

  private void injectKeyUp(int key) {
    InputProcessor processor = Gdx.input.getInputProcessor();
    if (processor != null) processor.keyUp(key);
  }

  private void pressKey(int key) {
    InputProcessor processor = Gdx.input.getInputProcessor();
    if (processor != null) processor.keyDown(key);
  }

  private void capture(String name) {
    Pixmap image = capturePixmap();
    try {
      writePng(name, image);
    } finally {
      image.dispose();
    }
  }

  private void captureHudState(String state, String image) {
    capture(image);
    if (devPath != null) return;
    JsonValue fixtureState = mmFixture.get("states").get(state);
    assertProgressCheckpoints(mmFixture.get("tree"), fixtureState, state);
    StringBuilder elements = new StringBuilder();
    appendHudGeometry(mmFixture.get("tree"), elements, true);
    nativeHudStates.put(state, "{\"elements\":{" + elements + "}}");
  }

  private void assertProgressCheckpoints(JsonValue node, JsonValue fixtureState, String state) {
    String key = node.getString("key");
    if (node.getString("type").equals("Progress")) {
      String signal = node.get("props").getString("valueSignal");
      float target = fixtureState.getFloat(signal);
      JsonValue checkpoint = fixtureState.get("progress");
      checkpoint = checkpoint == null ? null : checkpoint.get(key);
      float displayed = checkpoint == null ? target : checkpoint.getFloat("displayed");
      float trail = checkpoint == null ? displayed : checkpoint.getFloat("trail");
      Actor actor = document.actor(key);
      if (!(actor instanceof ProgressBar bar)
          || Math.abs(bar.getValue() - target) > .002f
          || Math.abs(bar.getDisplayedValue() - displayed) > .002f
          || Math.abs(Math.max(bar.getDelayedIncrease(), bar.getDelayedDecrease()) - trail) > .002f)
        failSmoke(
            state
                + "."
                + key
                + " progress missed authored checkpoint: target="
                + target
                + " displayed="
                + (actor instanceof ProgressBar bar ? bar.getDisplayedValue() : "missing")
                + " trail="
                + (actor instanceof ProgressBar bar
                    ? Math.max(bar.getDelayedIncrease(), bar.getDelayedDecrease())
                    : "missing"));
    }
    JsonValue children = node.get("children");
    if (children != null)
      for (JsonValue child : children) assertProgressCheckpoints(child, fixtureState, state);
  }

  private void appendHudGeometry(JsonValue node, StringBuilder out, boolean first) {
    String key = node.getString("key");
    Actor actor = document.actor(key);
    if (actor != null) {
      if (!first) out.append(',');
      Vector2 bottomLeft = actor.localToStageCoordinates(new Vector2());
      float x = bottomLeft.x;
      float y = Gdx.graphics.getHeight() - bottomLeft.y - actor.getHeight();
      out.append('"')
          .append(jsonEscape(key))
          .append("\":{\"rect\":{\"x\":")
          .append(jsonNumber(x))
          .append(",\"y\":")
          .append(jsonNumber(y))
          .append(",\"width\":")
          .append(jsonNumber(actor.getWidth()))
          .append(",\"height\":")
          .append(jsonNumber(actor.getHeight()))
          .append('}');
      if (actor instanceof Label label)
        out.append(",\"text\":\"").append(jsonEscape(label.getText().toString())).append('"');
      if (actor instanceof ProgressBar bar) {
        float displayed = bar.getDisplayedValue();
        float trail = Math.max(bar.getDelayedIncrease(), bar.getDelayedDecrease());
        out.append(",\"progress\":{\"target\":")
            .append(jsonNumber(bar.getValue()))
            .append(",\"displayed\":")
            .append(jsonNumber(displayed))
            .append(",\"trail\":")
            .append(jsonNumber(trail))
            .append('}');
      }
      out.append('}');
      first = false;
    }
    JsonValue children = node.get("children");
    if (children != null)
      for (JsonValue child : children) {
        boolean before = out.length() == 0;
        appendHudGeometry(child, out, before);
      }
  }

  private JsonValue fixtureNode(JsonValue node, String key) {
    if (node.getString("key", "").equals(key)) return node;
    JsonValue children = node.get("children");
    if (children != null)
      for (JsonValue child : children) {
        JsonValue found = fixtureNode(child, key);
        if (found != null) return found;
      }
    return null;
  }

  private Pixmap capturePixmap() {
    return Pixmap.createFromFrameBuffer(
        0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
  }

  private void writePng(String name, Pixmap image) {
    PixmapIO.writePNG(Gdx.files.local("artifacts/mm-systems/" + name), image, -1, true);
  }

  private void finishSmoke() {
    if (!recordedCancel || !recordedFailure || !recordedSuccess)
      failSmoke("scene transactions did not cover cancel/success/failure");
    if (devPath == null && (!sawGainTrail || !sawLossTrail))
      failSmoke("ProgressBar gain/loss color proof missing");
    if (fireActionCalls < 2 || healActionCalls < 1 || volumeActionCalls < 1 || muteActionCalls != 1)
      failSmoke("typed action counts were unexpected");
    if (currentController == null || currentController.health.isClosed())
      failSmoke("final scene signal is not live");
    beginShutdownProbe();
  }

  private void beginShutdownProbe() {
    if (currentController == null || pendingLoad != null)
      failSmoke("shutdown probe requires a settled live scene");
    currentController.beginCloseProbe();
    requestScene(true, false, new AssetManager(), new SceneTransition(2, 2));
    smokePhase = 16;
    phaseFrames = 0;
  }

  private void writeNativeHudGeometry() {
    if (devPath != null) return;
    StringBuilder states = new StringBuilder();
    for (var entry : nativeHudStates.entrySet()) {
      if (states.length() > 0) states.append(',');
      states.append('"').append(jsonEscape(entry.getKey())).append("\":").append(entry.getValue());
    }
    String json =
        "{\"viewport\":{\"width\":"
            + Gdx.graphics.getWidth()
            + ",\"height\":"
            + Gdx.graphics.getHeight()
            + ",\"dpr\":1},\"states\":{"
            + states
            + "}}\n";
    Gdx.files.local("artifacts/mm-systems/native-geometry.json").writeString(json, false);
  }

  private void writeEvidence() {
    int glError = Gdx.gl.glGetError();
    if (glError != GL20.GL_NO_ERROR) failSmoke("OpenGL error before evidence write: " + glError);
    String json =
        "{\n"
            + "  \"status\": \"PASS\",\n"
            + "  \"viewport\": {\"width\": "
            + Gdx.graphics.getWidth()
            + ", \"height\": "
            + Gdx.graphics.getHeight()
            + ", \"dpr\": 1},\n"
            + "  \"font\": \"Open Sans\",\n"
            + "  \"actions\": {\"fire\": "
            + fireActionCalls
            + ", \"heal\": "
            + healActionCalls
            + ", \"volume\": "
            + volumeActionCalls
            + ", \"mute\": "
            + muteActionCalls
            + "},\n"
            + "  \"signals\": {\"healthEvents\": "
            + healthEvents
            + ", \"sceneGeneration\": "
            + generation
            + ", \"health\": "
            + jsonNumber(finalSmokeHealth)
            + ", \"renderedHealth\": \""
            + jsonEscape(finalSmokeHealthText)
            + "\", \"renderedGeneration\": \""
            + finalSmokeGenerationText
            + "\"},\n"
            + "  \"transactions\": {\"cancel\": "
            + recordedCancel
            + ", \"success\": "
            + recordedSuccess
            + ", \"failure\": "
            + recordedFailure
            + "},\n"
            + "  \"animation\": {\"impactMarkers\": "
            + markerEvents
            + ", \"sequenceCues\": "
            + sequenceCueEvents
            + ", \"sequenceCompletions\": "
            + sequenceCompletions
            + "},\n"
            + "  \"progressBar\": {\"stableForeground\": \"#00d3f3\", \"gainTrail\": "
            + sawGainTrail
            + ", \"lossTrail\": "
            + sawLossTrail
            + "},\n"
            + "  \"devReload\": {\"reloads\": "
            + uiReloads
            + ", \"errors\": "
            + uiReloadErrors
            + "},\n"
            + "  \"shutdown\": {\"liveResourcesBeforeClose\": "
            + shutdownLiveResourcesObserved
            + ", \"ownedResourcesReleasedOnce\": "
            + shutdownResourcesReleased
            + ", \"liveResourceCount\": "
            + shutdownLiveResourceCount
            + ", \"releasedResourceCount\": "
            + shutdownReleasedResourceCount
            + ", \"loadingSceneCancelled\": "
            + shutdownLoadCancelled
            + ", \"candidateTextureDisposed\": "
            + shutdownCandidateTextureDisposed
            + ", \"currentTextureDisposed\": "
            + shutdownLiveTextureDisposed
            + "},\n"
            + "  \"glError\": "
            + glError
            + "\n}\n";
    Gdx.files.local("artifacts/mm-systems/native-smoke.json").writeString(json, false);
  }

  private void failSmoke(String message) {
    throw new IllegalStateException("Relay Ward smoke: " + message);
  }

  private void writeFailureCapture(Throwable failure) {
    Pixmap image = capturePixmap();
    try {
      writePng("failure.png", image);
    } finally {
      image.dispose();
    }
    Gdx.files
        .local("artifacts/mm-systems/failure.txt")
        .writeString(failure.toString() + "\n", false);
  }

  private void mkdir(String path) {
    Gdx.files.local(path).file().mkdirs();
  }

  private static String readResource(String path) {
    return Gdx.files.classpath(path.startsWith("/") ? path.substring(1) : path).readString("UTF-8");
  }

  private static String readPath(String path) {
    return Gdx.files.absolute(path).readString("UTF-8");
  }

  private static int rgb(Pixmap image, int x, int topY) {
    int y = image.getHeight() - 1 - topY;
    int pixel =
        image.getPixel(
            Math.max(0, Math.min(image.getWidth() - 1, x)),
            Math.max(0, Math.min(image.getHeight() - 1, y)));
    return pixel >>> 8;
  }

  private static int colorRgb(String hex) {
    return Color.rgba8888(Color.valueOf(hex)) >>> 8;
  }

  private static int maxRgbDelta(int left, int right) {
    return Math.max(
        Math.max(
            Math.abs((left >>> 16 & 255) - (right >>> 16 & 255)),
            Math.abs((left >>> 8 & 255) - (right >>> 8 & 255))),
        Math.abs((left & 255) - (right & 255)));
  }

  private static String hex(int rgb) {
    return String.format(Locale.ROOT, "#%06x", rgb & 0xffffff);
  }

  private static Matrix4 skinBone(ModelRenderer renderer) {
    return renderer.instance.nodes.first().parts.first().bones[1];
  }

  private static float matrixDelta(Matrix4 a, Matrix4 b) {
    float max = 0;
    for (int i = 0; i < 16; i++) max = Math.max(max, Math.abs(a.val[i] - b.val[i]));
    return max;
  }

  private static int differingPixels(Pixmap a, Pixmap b, int x0, int x1, int y0, int y1) {
    int changed = 0;
    int maxX = Math.min(Math.min(a.getWidth(), b.getWidth()), x1);
    int maxY = Math.min(Math.min(a.getHeight(), b.getHeight()), y1);
    for (int y = Math.max(0, y0); y < maxY; y++)
      for (int x = Math.max(0, x0); x < maxX; x++)
        if (a.getPixel(x, y) != b.getPixel(x, y)) changed++;
    return changed;
  }

  @Override
  public void resize(int width, int height) {
    if (renderer != null) renderer.resize(width, height);
    if (stage != null) stage.getViewport().update(width, height, true);
    if (currentScene != null) {
      for (GameObject object : currentScene.getObjects()) {
        Camera2D camera = object.getComponent(Camera2D.class);
        if (camera != null) camera.resize(width, height);
        ScreenEffects screen = currentScene.getSystem(ScreenEffects.class);
        if (screen != null) screen.resize(width, height);
      }
    }
  }

  @Override
  public void dispose() {
    if (disposed) return;
    disposed = true;
    Throwable failure = null;
    try {
      if (reloader != null) reloader.close();
    } catch (RuntimeException | Error e) {
      failure = append(failure, e);
    }
    try {
      if (runtime != null) runtime.close();
    } catch (RuntimeException | Error e) {
      failure = append(failure, e);
    }
    try {
      if (document != null) document.close();
    } catch (RuntimeException | Error e) {
      failure = append(failure, e);
    }
    try {
      if (stage != null) stage.dispose();
    } catch (RuntimeException | Error e) {
      failure = append(failure, e);
    }
    RelayController closingController = currentController;
    if (shutdownProbeArmed && closingController != null) {
      shutdownLiveResourceCount = closingController.closeProbeLiveResourceCount();
      shutdownLiveResourcesObserved = closingController.closeProbeResourcesAreLive();
    }
    try {
      if (scenes != null) scenes.close();
    } catch (RuntimeException | Error e) {
      failure = append(failure, e);
    }
    if (shutdownProbeArmed) {
      shutdownLoadCancelled =
          shutdownLoadHandle != null && shutdownLoadHandle.state() == LoadState.CANCELLED;
      shutdownCandidateTextureDisposed =
          shutdownCandidateTexture != null
              && shutdownCandidateTexture.getTextureObjectHandle() == 0;
      shutdownLiveTextureDisposed =
          shutdownLiveTexture != null && shutdownLiveTexture.getTextureObjectHandle() == 0;
      shutdownResourcesReleased =
          closingController != null && closingController.closeProbeResourcesReleased();
      shutdownReleasedResourceCount =
          closingController == null ? 0 : closingController.closeProbeReleasedResourceCount();
      if (!shutdownLiveResourcesObserved
          || !shutdownResourcesReleased
          || !shutdownLoadCancelled
          || shutdownLiveResourceCount != 8
          || shutdownReleasedResourceCount != 8
          || !shutdownCandidateTextureDisposed
          || !shutdownLiveTextureDisposed) {
        failure =
            append(
                failure,
                new IllegalStateException(
                    "Shutdown probe failed: live="
                        + shutdownLiveResourcesObserved
                        + " resourcesReleased="
                        + shutdownResourcesReleased
                        + " liveCount="
                        + shutdownLiveResourceCount
                        + " releasedCount="
                        + shutdownReleasedResourceCount
                        + " loadCancelled="
                        + shutdownLoadCancelled
                        + " candidateTextureDisposed="
                        + shutdownCandidateTextureDisposed
                        + " currentTextureDisposed="
                        + shutdownLiveTextureDisposed));
      }
    }
    try {
      if (hostGame != null) hostGame.dispose();
    } catch (RuntimeException | Error e) {
      failure = append(failure, e);
    }
    try {
      if (renderer != null) renderer.dispose();
    } catch (RuntimeException | Error e) {
      failure = append(failure, e);
    }
    try {
      if (theme != null) theme.dispose();
    } catch (RuntimeException | Error e) {
      failure = append(failure, e);
    }
    try {
      if (skinModel != null) skinModel.dispose();
    } catch (RuntimeException | Error e) {
      failure = append(failure, e);
    }
    try {
      if (hostPortrait != null) hostPortrait.dispose();
    } catch (RuntimeException | Error e) {
      failure = append(failure, e);
    }
    try {
      if (transitionShape != null) transitionShape.dispose();
    } catch (RuntimeException | Error e) {
      failure = append(failure, e);
    }
    if (idleCapture != null) idleCapture.dispose();
    if (hitCapture != null) hitCapture.dispose();
    if (smoke && shutdownProbeArmed && failure == null) {
      try {
        writeEvidence();
        Gdx.app.log("MmSystems", "PASS: scene/audio/feedback/animation/React/shutdown smoke");
      } catch (RuntimeException | Error e) {
        failure = append(failure, e);
      }
    }
    if (failure instanceof RuntimeException e) throw e;
    if (failure instanceof Error e) throw e;
  }

  private static Throwable append(Throwable first, Throwable next) {
    if (first == null) return next;
    if (first != next) first.addSuppressed(next);
    return first;
  }

  private final class RelayController extends Component {
    private final boolean arena;
    private final Radio radio;
    private final Signal<Float> health;
    private final Signal<String> state;
    private final Signal<Boolean> canFire;
    private final SceneAssets sceneAssets;
    private final SoundManager soundManager;
    private final TimeManager timeManager;
    private final FloatingTextPool floatingText;
    private final ScreenEffects screenEffects;
    private final PresentationEffects cameraShake;
    private final ModelAnimationLayer upperLayer;
    private final ModelRenderer skinRenderer;
    private final ModelPosePlayback locomotion;
    private final org.javity.sequence.Sequencer sequencer;
    private final FeedbackPlayer feedbackPlayer;
    private final Sound impactSound;
    private final Music music;
    private final Camera2D camera;
    private final GameObject cameraObject, pilot;
    private final PointerPicker pointerPicker;
    private final com.badlogic.gdx.math.Vector2 initialPilotPosition = new Vector2();
    private final org.javity.core.Transform3D droneTransform;
    private float cooldown;
    private float healValue = 0;
    private boolean attackActive;
    private boolean dead;
    private String arenaLabel = "HANGAR";
    private VoiceHandle musicVoice, launchVoice, impactVoice;
    private VoiceHandle feedbackVoice;
    private DuckHandle musicDuck;
    private ScreenFlash flash;
    private PresentationHandle playerShake;
    private FloatingTextHandle textHandle;
    private PlaybackHandle feedbackHandle;
    private Matrix4 previousPose;
    private int markerEvents, sequenceCueEvents, sequenceCompletions;
    private int feedbackStarted, feedbackClosed;
    private boolean feedbackResourceAcquired;
    private boolean duckActiveAtImpact;
    private TimeLease hitStopLease;
    private float lastWorldDelta;
    private VoiceHandle closeProbeVoice, closeProbeFeedbackVoice;
    private DuckHandle closeProbeDuck;
    private FloatingTextHandle closeProbeText;
    private ScreenFlash closeProbeFlash;
    private PresentationHandle closeProbePresentation;
    private TimeLease closeProbeTime;
    private PlaybackHandle closeProbeFeedback;
    private boolean closeProbeStarted, closeProbeReleased;
    private int closeProbeFeedbackCloseCount, closeProbeReleasedCount;

    private RelayController(
        boolean arena,
        Radio radio,
        Signal<Float> health,
        Signal<String> state,
        Signal<Boolean> canFire,
        SceneAssets sceneAssets,
        SoundManager soundManager,
        TimeManager timeManager,
        FloatingTextPool floatingText,
        ScreenEffects screenEffects,
        PresentationEffects cameraShake,
        ModelAnimationLayer upperLayer,
        ModelRenderer skinRenderer,
        ModelPosePlayback locomotion,
        org.javity.sequence.Sequencer sequencer,
        FeedbackPlayer feedbackPlayer,
        Sound impactSound,
        Music music,
        Camera2D camera,
        GameObject cameraObject,
        GameObject pilot,
        org.javity.core.Transform3D droneTransform,
        Game game) {
      this.arena = arena;
      this.radio = radio;
      this.health = health;
      this.state = state;
      this.canFire = canFire;
      this.sceneAssets = sceneAssets;
      this.soundManager = soundManager;
      this.timeManager = timeManager;
      this.floatingText = floatingText;
      this.screenEffects = screenEffects;
      this.cameraShake = cameraShake;
      this.upperLayer = upperLayer;
      this.skinRenderer = skinRenderer;
      this.locomotion = locomotion;
      this.sequencer = sequencer;
      this.feedbackPlayer = feedbackPlayer;
      this.impactSound = impactSound;
      this.music = music;
      this.camera = camera;
      this.cameraObject = cameraObject;
      this.pilot = pilot;
      this.pointerPicker = new PointerPicker(game, camera);
      this.droneTransform = droneTransform;
      initialPilotPosition.set(pilot.transform.position.x, pilot.transform.position.y);
    }

    private void setArenaLabel(String label) {
      arenaLabel = label;
    }

    private void beginCloseProbe() {
      closeProbeStarted = true;
      closeProbeVoice = soundManager.play(impactSound, AudioBus.SFX, false, .16f, .94f, 0);
      closeProbeDuck = soundManager.duck(AudioBus.MUSIC, .38f, 10);
      closeProbeText =
          floatingText.spawn(
              "CLOSE",
              pilot.transform.position.x + 28,
              pilot.transform.position.y + 44,
              10,
              Color.valueOf("#fda4af"));
      closeProbeFlash = screenEffects.flash(FLASH, .32f, 10);
      closeProbePresentation =
          pilot.getComponent(PresentationEffects.class).shake(2, 1, .01f, 10, 18, 20261017L);
      closeProbeTime = timeManager.freeze(10);
      Feedback ownedVoice =
          Feedback.resource(
              10,
              () -> {
                closeProbeFeedbackVoice =
                    soundManager.play(impactSound, AudioBus.SFX, false, .1f, 1.02f, 0);
                return () -> {
                  if (closeProbeFeedbackVoice != null) closeProbeFeedbackVoice.close();
                  closeProbeFeedbackCloseCount++;
                };
              });
      closeProbeFeedback = feedbackPlayer.play(ownedVoice);
    }

    private boolean closeProbeResourcesAreLive() {
      return closeProbeStarted && closeProbeLiveResourceCount() == 8;
    }

    private int closeProbeLiveResourceCount() {
      int count = 0;
      if (closeProbeVoice != null && closeProbeVoice.isActive()) count++;
      if (closeProbeFeedbackVoice != null && closeProbeFeedbackVoice.isActive()) count++;
      if (closeProbeDuck != null && closeProbeDuck.isActive()) count++;
      if (closeProbeText != null && closeProbeText.isActive()) count++;
      if (closeProbeFlash != null && closeProbeFlash.isActive()) count++;
      if (closeProbePresentation != null && closeProbePresentation.isActive()) count++;
      if (closeProbeTime != null && closeProbeTime.isActive()) count++;
      if (closeProbeFeedback != null && closeProbeFeedback.isRunning()) count++;
      return count;
    }

    private boolean closeProbeResourcesReleased() {
      return closeProbeReleased;
    }

    private int closeProbeReleasedResourceCount() {
      return closeProbeReleasedCount;
    }

    @Override
    protected void onRelease() {
      if (!closeProbeStarted) return;
      if (closeProbeFeedback != null && closeProbeFeedback.isRunning())
        closeProbeFeedback.cancel(CancelMode.KEEP_CURRENT);
      if (closeProbeFeedbackVoice != null && closeProbeFeedbackVoice.isActive())
        closeProbeFeedbackVoice.close();
      if (closeProbeVoice != null) closeProbeVoice.close();
      if (closeProbeDuck != null) closeProbeDuck.close();
      if (closeProbeText != null) closeProbeText.close();
      if (closeProbeFlash != null) closeProbeFlash.close();
      if (closeProbePresentation != null) closeProbePresentation.close();
      if (closeProbeTime != null) closeProbeTime.close();
      if (launchVoice != null) launchVoice.close();
      if (impactVoice != null) impactVoice.close();
      if (feedbackVoice != null && feedbackVoice.isActive()) feedbackVoice.close();
      if (musicVoice != null) musicVoice.close();
      if (musicDuck != null) musicDuck.close();
      if (textHandle != null) textHandle.close();
      if (flash != null) flash.close();
      if (playerShake != null) playerShake.close();
      if (hitStopLease != null) hitStopLease.close();
      if (closeProbeFeedbackVoice != null && !closeProbeFeedbackVoice.isActive())
        closeProbeReleasedCount++;
      if (closeProbeVoice != null && !closeProbeVoice.isActive()) closeProbeReleasedCount++;
      if (closeProbeDuck != null && !closeProbeDuck.isActive()) closeProbeReleasedCount++;
      if (closeProbeText != null && !closeProbeText.isActive()) closeProbeReleasedCount++;
      if (closeProbeFlash != null && !closeProbeFlash.isActive()) closeProbeReleasedCount++;
      if (closeProbePresentation != null && !closeProbePresentation.isActive())
        closeProbeReleasedCount++;
      if (closeProbeTime != null && !closeProbeTime.isActive()) closeProbeReleasedCount++;
      if (closeProbeFeedback != null && !closeProbeFeedback.isRunning()) closeProbeReleasedCount++;
      closeProbeReleased = closeProbeFeedbackCloseCount == 1 && closeProbeReleasedCount == 8;
    }

    @Override
    public void onCreate() {
      musicVoice = soundManager.play(music, AudioBus.MUSIC, true, .14f, 0);
      previousPose = skinBone(skinRenderer).cpy();
    }

    @Override
    public void onUpdate(float delta) {
      lastWorldDelta = delta;
      if (cooldown > 0) {
        cooldown = Math.max(0, cooldown - delta);
        if (cooldown == 0 && attackActive) {
          attackActive = false;
          upperLayer.fadeTo(0, .16);
          state.set(dead ? "DISABLED" : "PATROL");
          canFire.set(!dead);
          if (launchVoice != null) {
            launchVoice.close();
            launchVoice = null;
          }
          if (impactVoice != null) {
            impactVoice.close();
            impactVoice = null;
          }
        }
      }
    }

    private void applySettings(float nextVolume, boolean nextMuted) {
      soundManager.setMasterVolume(nextVolume);
      soundManager.setBusVolume(AudioBus.SFX, nextMuted ? 0 : 1);
    }

    private void fire() {
      if (attackActive || dead || cooldown > 0) return;
      attackActive = true;
      cooldown = .95f;
      canFire.set(false);
      state.set("WINDUP");
      hitBoneBefore = skinBone(skinRenderer).cpy();
      upperLayer
          .play(
              AnimationTransition.clip("upper-reach")
                  .speed(1)
                  .markers(
                      new AnimationMarker("attack-windup", .15),
                      new AnimationMarker("impact", .5),
                      new AnimationMarker("recover", .9)))
          .onMarker("impact", this::impact)
          .onMarker("recover", () -> state.set(dead ? "DISABLED" : "RECOVER"));
      upperLayer.fadeTo(1, .08);
      org.javity.sequence.Sequence sequence =
          org.javity.sequence.Sequence.builder(.65)
              .cue(
                  0,
                  () -> {
                    if (isCurrent()) {
                      launchVoice =
                          soundManager.play(impactSound, AudioBus.SFX, false, .18f, .92f, 0);
                      sequenceCueEvents++;
                      MmSystemsDemo.this.sequenceCueEvents++;
                    }
                  })
              .cue(
                  .18,
                  () -> {
                    if (isCurrent()) {
                      musicDuck = soundManager.duck(AudioBus.MUSIC, .28f, .55);
                      sequenceCueEvents++;
                      MmSystemsDemo.this.sequenceCueEvents++;
                    }
                  })
              .build();
      sequencer
          .play(sequence, 120, false)
          .onComplete(
              () -> {
                if (isCurrent()) {
                  sequenceCompletions++;
                  MmSystemsDemo.this.sequenceCompletions++;
                }
              });
    }

    private void impact() {
      if (!isCurrent() || !attackActive || dead) return;
      markerEvents++;
      MmSystemsDemo.this.markerEvents++;
      float next = Math.max(0, health.get() - .35f);
      health.set(next);
      healthEvents++;
      dead = next <= 0;
      state.set(dead ? "DISABLED" : "STAGGER");
      impactVoice = soundManager.play(impactSound, AudioBus.SFX, false, .42f, 1.12f, 0);
      textHandle =
          floatingText.spawn(
              "-35",
              pilot.transform.position.x + 54,
              pilot.transform.position.y + 24,
              .85f,
              Color.valueOf("#fda4af"));
      hitStopLease = timeManager.freeze(.06);
      flash = screenEffects.flash(FLASH, .48f, .24);
      playerShake =
          pilot.getComponent(PresentationEffects.class).shake(14, 7, .045f, .28, 18, 20261015L);
      cameraShake.shake(7, 5, .01f, .28, 18, 20261016L);
      duckActiveAtImpact = musicDuck != null && musicDuck.isActive();
      pilotTransformAtImpact.set(pilot.transform.position);
      cameraTransformAtImpact.set(cameraObject.transform.position);
      feedbackStarted++;
      Feedback resource =
          Feedback.resource(
              .22,
              () -> {
                feedbackResourceAcquired = true;
                feedbackVoice = soundManager.play(impactSound, AudioBus.SFX, false, .08f, 1.25f, 0);
                return () -> {
                  feedbackVoice.close();
                  feedbackClosed++;
                };
              });
      Feedback custom =
          Feedback.custom(
              .12,
              () ->
                  new FeedbackEffect() {
                    @Override
                    public void start() {
                      customFeedbackStarted++;
                    }

                    @Override
                    public void update(double elapsed, double delta) {
                      customFeedbackUpdated++;
                    }
                  });
      feedbackHandle = feedbackPlayer.play(Feedback.parallel(resource, custom));
    }

    private void heal() {
      if (health.get() >= 1) return;
      float before = health.get();
      float after = Math.min(1, before + .2f);
      health.set(after);
      healthEvents++;
      floatingText.spawn(
          "+20",
          pilot.transform.position.x,
          pilot.transform.position.y + 34,
          .72f,
          Color.valueOf("#86efac"));
      state.set(dead && after > 0 ? "RECOVERING" : "PATROL");
      if (after > 0) dead = false;
      canFire.set(!dead && cooldown <= 0);
    }

    private boolean isCurrent() {
      return isInitialized()
          && getGameObject() != null
          && getGameObject().containsComponent(this)
          && currentScene == getGame()
          && !getGame().isDisposed();
    }
  }

  private final class FreezePresentationProbe extends Component {
    private final TimeManager time;
    private final GameObject cameraObject;
    private final GameObject pilot;

    private FreezePresentationProbe(TimeManager time, GameObject cameraObject, GameObject pilot) {
      this.time = time;
      this.cameraObject = cameraObject;
      this.pilot = pilot;
      updateMode = UpdateMode.UI;
    }

    @Override
    public void onUpdate(float delta) {
      probeUiUpdates++;
      probeLastDelta = delta;
      probeLastScale = (float) time.getScale();
      if (time.getScale() == 0) probeFrozenScaleFrames++;
      if (currentController != null
          && currentController.hitStopLease != null
          && currentController.hitStopLease.isActive()) {
        activeHitStopLeaseFrames++;
      }
      if (time.getScale() == 0 && delta > 0) sawFreezeWithUiAdvance = true;
    }

    @Override
    public void onLateUpdate(float delta) {
      RelayController controller = currentController;
      if (controller == null
          || controller.getGame() != getGame()
          || controller.playerShake == null
          || !controller.playerShake.isActive()) return;
      org.javity.presentation.Presentation2D pilotPose =
          pilot.getComponent(org.javity.presentation.Presentation2D.class);
      org.javity.presentation.Presentation2D cameraPose =
          cameraObject.getComponent(org.javity.presentation.Presentation2D.class);
      if (pilotPose == null || cameraPose == null) return;
      boolean authoritativeStable =
          pilot.transform.position.epsilonEquals(pilotTransformAtImpact, .0001f)
              && cameraObject.transform.position.epsilonEquals(cameraTransformAtImpact, .0001f);
      boolean visualMotion =
          Math.abs(pilotPose.presentedX() - pilot.transform.position.x) > .25f
              || Math.abs(pilotPose.presentedY() - pilot.transform.position.y) > .25f;
      boolean cameraMotion =
          Math.abs(cameraPose.presentedX() - cameraObject.transform.position.x) > .25f
              || Math.abs(cameraPose.presentedY() - cameraObject.transform.position.y) > .25f;
      if (authoritativeStable && visualMotion && cameraMotion) {
        controller.camera.applyTo(renderer.camera2D);
        if (Math.abs(renderer.camera2D.position.x - cameraPose.presentedX()) < .001f
            && Math.abs(renderer.camera2D.position.y - cameraPose.presentedY()) < .001f)
          sawPresentationWithoutTransformMutation = true;
      }
    }
  }
}
