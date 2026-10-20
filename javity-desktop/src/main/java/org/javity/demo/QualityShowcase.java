package org.javity.demo;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import java.util.ArrayList;
import java.util.List;
import net.mgsx.gltf.scene3d.attributes.PBRColorAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRFloatAttribute;
import org.javity.animation.ModelPoseAnimator;
import org.javity.animation.PlayMode;
import org.javity.assets.AssetLease;
import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.core.Transform3D;
import org.javity.gltf.DirectionalShadowPass;
import org.javity.gltf.GltfAsset;
import org.javity.gltf.GltfAssets;
import org.javity.gltf.GltfInstance;
import org.javity.gltf.HdrSettings;
import org.javity.gltf.HdrWorldPass;
import org.javity.gltf.PbrShaderProvider;
import org.javity.input.InputActions;
import org.javity.input.InputContext;
import org.javity.render.Camera3D;
import org.javity.render.GdxRenderer;
import org.javity.render.ModelRenderer;
import org.javity.render.ShadowPolicy3D;
import org.javity.render.lighting.DirectionalLight3D;
import org.javity.render.lighting.PointLight3D;
import org.javity.render.lighting.RenderEnvironment3D;
import org.javity.serialization.AssetId;
import org.javity.ui.flex.UiTheme;
import org.javity.ui.inspector.RuntimeToolsOverlay;

/** Optional, local, bounded integration of the engine's PBR, shadows, HDR, animation and tools. */
public final class QualityShowcase extends ApplicationAdapter {
  private final boolean smoke, shadowsEnabled;
  private final FileHandle output;
  private FileHandle foxRoot;
  private final boolean explicitAssetRoot;
  private final List<Model> authored = new ArrayList<>();
  private final List<GltfInstance> instances = new ArrayList<>();
  private final List<AssetLease<GltfAsset>> leases = new ArrayList<>();
  private final ModelPoseAnimator[] animators = new ModelPoseAnimator[2];
  private final ModelRenderer[] foxModels = new ModelRenderer[2];
  private final Transform3D[] foxTransforms = new Transform3D[2];
  private final JsonValue evidence = new JsonValue(JsonValue.ValueType.object);
  private final InputActions actions = new InputActions();
  private InputContext navigation;
  private InputMultiplexer input;
  private Game game;
  private Camera3D camera;
  private GdxRenderer renderer;
  private HdrWorldPass hdr;
  private RenderEnvironment3D environment;
  private DirectionalLight3D sun;
  private PointLight3D lantern;
  private GameObject firstFox;
  private Stage stage;
  private UiTheme theme;
  private RuntimeToolsOverlay tools;
  private SpriteBatch hud;
  private BitmapFont font;
  private Throwable failure;
  private boolean closed, freeFly, paused, discardMouse = true;
  private float yaw, pitch, radius = 19, exposure = 1;
  private float skyR = .055f, skyG = .10f, skyB = .15f;
  private int frame;
  private String presetName = "Afternoon";
  private final Vector3 target = new Vector3(0, 1, -1);

  private QualityShowcase(boolean smoke, boolean shadows, String assetRoot, String output) {
    this.smoke = smoke;
    this.shadowsEnabled = shadows;
    this.explicitAssetRoot = assetRoot != null;
    this.foxRoot = new FileHandle(assetRoot == null ? "tools/animation-validation/fox" : assetRoot);
    this.output = new FileHandle(output);
  }

  /** Run from repository root. GL30 and a complete RGBA16F target are required explicitly. */
  public static void main(String[] args) {
    boolean smoke = false, shadows = true, small = false;
    String assetRoot = null, output = "build/reports/quality-showcase";
    for (String arg : args) {
      if (arg.equals("--smoke")) smoke = true;
      else if (arg.equals("--no-shadows")) shadows = false;
      else if (arg.equals("--small")) small = true;
      else if (arg.startsWith("--asset-root=")) assetRoot = arg.substring(13);
      else if (arg.startsWith("--output=")) output = arg.substring(9);
      else throw new IllegalArgumentException("Unknown quality showcase argument: " + arg);
    }
    QualityShowcase app = new QualityShowcase(smoke, shadows, assetRoot, output);
    var config = new Lwjgl3ApplicationConfiguration();
    config.setTitle("Javity | Fox Garden | PBR + directional shadow + HDR");
    config.setWindowedMode(smoke || small ? 800 : 1280, smoke || small ? 600 : 800);
    config.setInitialVisible(!smoke);
    config.setForegroundFPS(60);
    config.disableAudio(true);
    config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 2);
    config.setWindowListener(
        new Lwjgl3WindowAdapter() {
          @Override
          public void focusLost() {
            app.focusLost();
          }

          @Override
          public void focusGained() {
            if (app.tools != null) app.tools.onFocusGained();
          }
        });
    new Lwjgl3Application(app, config);
  }

  @Override
  public void create() {
    try {
      setup();
    } catch (Throwable next) {
      fail(next);
    }
  }

  private void setup() {
    if (!explicitAssetRoot && !foxRoot.child("Fox.gltf").exists())
      foxRoot = Gdx.files.classpath("org/javity/demo/quality-assets/fox");
    require(foxRoot.child("Fox.gltf").exists(), "Missing local or packaged Fox.gltf: " + foxRoot);
    game = new Game();
    GltfAssets assets = game.addSystem(new GltfAssets(24));
    environment = game.addSystem(new RenderEnvironment3D());
    camera = game.createObject("Garden camera").addComponent(new Camera3D(46));
    camera.camera.near = .08f;
    camera.camera.far = 90;
    camera.camera.position.set(12, 8, 15);
    camera.camera.lookAt(target);
    syncAngles();
    radius = camera.camera.position.dst(target);
    GameObject sunObject = game.createObject("Sun / first directional / fixed shadow volume");
    sunObject.addComponent(new Transform3D()).rotation.setEulerAngles(-35, -50, 0);
    sun = sunObject.addComponent(new DirectionalLight3D());
    GameObject lamp = game.createObject("Amber lantern / finite-range point");
    lamp.addComponent(new Transform3D()).position.set(-3, 2.3f, 1);
    lantern = lamp.addComponent(new PointLight3D());
    lantern.setColor(1, .33f, .08f);
    lantern.setIntensity(14);
    lantern.setRange(8);
    buildGarden();
    AssetId<GltfAsset> id = new AssetId<>("showcase/original-fox");
    assets.register(id, foxRoot.child("Fox.gltf"));
    for (int i = 0; i < 2; i++) {
      AssetLease<GltfAsset> lease = assets.request(id);
      leases.add(lease);
      require(lease.state() == AssetLease.State.READY, "Fox import failed: " + lease.failure());
      GltfInstance instance = lease.get().instantiate();
      instances.add(instance);
      GameObject fox =
          game.createObject(
              i == 0 ? "Fox / Walk / independent pose A" : "Fox / Survey / independent pose B");
      if (i == 0) firstFox = fox;
      Transform3D transform = fox.addComponent(new Transform3D());
      transform.position.set(i == 0 ? -1.9f : 2, .31f, i == 0 ? 1.5f : .2f);
      transform.scale.set(.023f, .023f, .023f);
      transform.rotation.setEulerAngles(i == 0 ? -25 : 145, 0, 0);
      foxTransforms[i] = transform;
      foxModels[i] = fox.addComponent(instance.createRenderer());
      fox.addComponent(new ShadowPolicy3D(true, true));
      animators[i] = fox.addComponent(new ModelPoseAnimator());
    }
    PbrShaderProvider pbr =
        new PbrShaderProvider(24, 1, 1, 0, PbrShaderProvider.OutputColorSpace.LINEAR);
    renderer = pbr.createRenderer();
    if (shadowsEnabled)
      renderer.setWorldRenderPass(
          new DirectionalShadowPass(
              pbr,
              sun,
              new DirectionalShadowPass.Settings(1024, 25, 25, 0, 60, 0, 1, -1, .001f),
              24,
              4));
    hdr = new HdrWorldPass(Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    renderer.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    game.start();
    animators[0].play("Walk", PlayMode.LOOP).seek(.2);
    animators[1].play("Survey", PlayMode.LOOP).seek(.7);
    stage = new Stage(new ScreenViewport());
    theme = new UiTheme();
    tools = new RuntimeToolsOverlay(stage, theme, () -> game);
    tools.inspector().setGizmoCameras(camera.camera, null);
    tools.inspector().selectObject(firstFox);
    tools.setDebugCameraProvider(() -> freeFly ? camera.camera : null);
    tools.setInteractionChanged(
        () -> {
          actions.releaseAll();
          navigation.setActive(!tools.inspector().isVisible());
          discardMouse = true;
        });
    hud = new SpriteBatch();
    font = new BitmapFont();
    bindInputs();
    preset(1);
    evidence.addChild("glVersion", new JsonValue(Gdx.gl.glGetString(GL20.GL_VERSION)));
    evidence.addChild("source", new JsonValue(foxRoot.child("Fox.gltf").path()));
    evidence.addChild("shadows", new JsonValue(shadowsEnabled));
    evidence.addChild(
        "pipeline",
        new JsonValue(
            "one Game -> linear PBR + directional depth/color -> RGBA16F -> ACES/sRGB ->"
                + " HUD/Stage"));
  }

  private void buildGarden() {
    Model grass = box(0x54745aff, 0, .95f), stone = box(0xb8b1a1ff, 0, .86f);
    Model dark = box(0x334750ff, .1f, .65f), brass = box(0xc3944dff, .78f, .25f);
    Model bark = box(0x574336ff, 0, .96f), leaves = cone(0x376357ff), blade = cone(0x729457ff);
    object("Meadow island", grass, 0, -.4f, -1, 20, .7f, 17, false);
    object("Fox court / shadow receiver", stone, 0, .03f, .5f, 10, .55f, 7, false);
    for (int i = 0; i < 4; i++)
      object("Approach stone " + i, stone, .5f, -.01f, 4.8f + i, 2.2f, .2f, .7f, false);
    object("North exhibit plinth", dark, -.7f, .55f, -3, 3.4f, 1, 1.8f, true);
    object("Brushed brass study", brass, -.7f, 1.5f, -3, 1.25f, 1.15f, 1.25f, true)
        .rotation
        .setEulerAngles(25, 0, 0);
    object("Rough stone study", stone, 3, .95f, -3, 1, 1.3f, 1, true);
    object("Lantern post", dark, -3, 1.1f, 1, .13f, 1.7f, .13f, true);
    Material glow = material(0xffa546ff, .1f, .35f);
    glow.set(PBRColorAttribute.createEmissive(new Color(4, 1.1f, .18f, 1)));
    Model bulb = new ModelBuilder().createSphere(.45f, .45f, .45f, 12, 8, glow, attributes());
    authored.add(bulb);
    object("Lantern glass / linear emission", bulb, -3, 2.15f, 1, 1, 1, 1, false);
    for (int i = 0; i < 7; i++) {
      float x = i < 4 ? -7 + i * 4.2f : i == 5 ? 8 : -8;
      float z = i < 4 ? -6 : i == 4 ? -1 : 4;
      object("Tree trunk " + i, bark, x, .9f, z, .35f, 2.2f, .35f, true);
      object("Tree canopy low " + i, leaves, x, 2.15f, z, 2.2f, 2.9f, 2.2f, true);
      object("Tree canopy high " + i, leaves, x, 3.2f, z, 1.65f, 2.5f, 1.65f, true);
    }
    for (int i = 0; i < 42; i++) {
      float x = -8.4f + (i % 14) * 1.28f, z = -7.4f + (i / 14) * 1.5f;
      object("Authored grass tuft " + i, blade, x, .05f, z, .36f, .55f + (i % 3) * .1f, .36f, true);
    }
    for (int i = 0; i < 9; i++)
      object("Court edging " + i, dark, -5.1f + i * 1.28f, .35f, -3.1f, .8f, .3f, .5f, true);
  }

  private static long attributes() {
    return VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;
  }

  private static Material material(int rgba, float metallic, float roughness) {
    return new Material(
        PBRColorAttribute.createBaseColorFactor(new Color(rgba)),
        PBRFloatAttribute.createMetallic(metallic),
        PBRFloatAttribute.createRoughness(roughness));
  }

  private Model box(int rgba, float metallic, float roughness) {
    Model model =
        new ModelBuilder().createBox(1, 1, 1, material(rgba, metallic, roughness), attributes());
    authored.add(model);
    return model;
  }

  private Model cone(int rgba) {
    Model model = new ModelBuilder().createCone(1, 1, 1, 7, material(rgba, 0, .94f), attributes());
    authored.add(model);
    return model;
  }

  private Transform3D object(
      String name,
      Model model,
      float x,
      float y,
      float z,
      float sx,
      float sy,
      float sz,
      boolean cast) {
    GameObject object = game.createObject(name);
    Transform3D transform = object.addComponent(new Transform3D());
    transform.position.set(x, y, z);
    transform.scale.set(sx, sy, sz);
    object.addComponent(new ModelRenderer(model));
    object.addComponent(new ShadowPolicy3D(cast, true));
    return transform;
  }

  private void bindInputs() {
    navigation = actions.addContext("showcase-navigation", 10);
    navigation
        .bindKey("left", Input.Keys.A)
        .bindKey("right", Input.Keys.D)
        .bindKey("forward", Input.Keys.W)
        .bindKey("back", Input.Keys.S)
        .bindKey("up", Input.Keys.E)
        .bindKey("down", Input.Keys.Q)
        .bindKey("turn-left", Input.Keys.LEFT)
        .bindKey("turn-right", Input.Keys.RIGHT)
        .bindKey("look-up", Input.Keys.UP)
        .bindKey("look-down", Input.Keys.DOWN)
        .bindKey("mode", Input.Keys.C)
        .bindKey("pause", Input.Keys.SPACE)
        .bindKey("point", Input.Keys.L)
        .bindKey("afternoon", Input.Keys.NUM_1)
        .bindKey("sunset", Input.Keys.NUM_2)
        .bindKey("night", Input.Keys.NUM_3)
        .bindKey("darker", Input.Keys.MINUS)
        .bindKey("brighter", Input.Keys.EQUALS)
        .bindKey("reset", Input.Keys.HOME)
        .bindKey("exit", Input.Keys.ESCAPE)
        .bindButton("look", "pointer:0", Integer.toString(Input.Buttons.RIGHT));
    InputAdapter mouse =
        new InputAdapter() {
          @Override
          public boolean scrolled(float x, float y) {
            if (tools.inspector().isVisible()) return false;
            radius = MathUtils.clamp(radius + y, 5, 36);
            return true;
          }
        };
    input =
        new InputMultiplexer(
            tools.inspector().inputProcessor(),
            stage,
            tools.inspector().gizmoInputProcessor(),
            mouse,
            actions);
    Gdx.input.setInputProcessor(input);
  }

  private void preset(int index) {
    presetName = index == 1 ? "Afternoon" : index == 2 ? "Last light" : "Lantern hour";
    skyR = index == 3 ? .003f : index == 2 ? .11f : .055f;
    skyG = index == 3 ? .008f : index == 2 ? .045f : .10f;
    skyB = index == 3 ? .022f : index == 2 ? .025f : .15f;
    environment.setAmbient(
        index == 3 ? .035f : .16f, index == 3 ? .055f : .20f, index == 3 ? .10f : .23f);
    sun.setColor(1, index == 2 ? .55f : .91f, index == 2 ? .3f : .8f);
    sun.setIntensity(index == 1 ? 2.8f : index == 2 ? 1.3f : .12f);
    exposure = index == 3 ? 1.6f : 1;
    hdr.setSettings(new HdrSettings(exposure, HdrSettings.ToneMapper.ACES));
  }

  private void controls(float dt) {
    if (actions.justPressed("exit")) Gdx.app.exit();
    if (actions.justPressed("mode")) {
      freeFly = !freeFly;
      syncAngles();
      discardMouse = true;
    }
    if (actions.justPressed("pause")) {
      paused = !paused;
      if (paused) game.pause();
      else game.resume();
    }
    if (actions.justPressed("point")) lantern.setEnabled(!lantern.isEnabled());
    if (actions.justPressed("afternoon")) preset(1);
    if (actions.justPressed("sunset")) preset(2);
    if (actions.justPressed("night")) preset(3);
    if (actions.justPressed("darker") || actions.justPressed("brighter")) {
      exposure =
          MathUtils.clamp(exposure * (actions.justPressed("brighter") ? 1.25f : .8f), .1f, 8);
      hdr.setSettings(new HdrSettings(exposure, HdrSettings.ToneMapper.ACES));
    }
    if (actions.justPressed("reset")) {
      freeFly = false;
      camera.camera.position.set(12, 8, 15);
      camera.camera.lookAt(target);
      radius = camera.camera.position.dst(target);
      syncAngles();
    }
    if (!navigation.active()) return;
    float dx = actions.axis("turn-left", "turn-right") * 70 * dt;
    float dy = actions.axis("look-down", "look-up") * 70 * dt;
    if (actions.isDown("look")) {
      if (!discardMouse) {
        dx += Gdx.input.getDeltaX() * .22f;
        dy -= Gdx.input.getDeltaY() * .22f;
      }
      discardMouse = false;
    } else discardMouse = true;
    yaw += dx;
    pitch = MathUtils.clamp(pitch + dy, -80, freeFly ? 80 : -8);
    // Same yaw/pitch and direction x right-vector convention as VoxelDemo's free-fly camera.
    float yr = yaw * MathUtils.degreesToRadians, pr = pitch * MathUtils.degreesToRadians;
    camera
        .camera
        .direction
        .set(
            MathUtils.cos(pr) * MathUtils.cos(yr),
            MathUtils.sin(pr),
            MathUtils.cos(pr) * MathUtils.sin(yr))
        .nor();
    camera.camera.up.set(Vector3.Y);
    if (freeFly) {
      Vector3 move =
          camera
              .camera
              .direction
              .cpy()
              .scl(actions.axis("back", "forward"))
              .mulAdd(
                  camera.camera.direction.cpy().crs(Vector3.Y).nor(), actions.axis("left", "right"))
              .mulAdd(Vector3.Y, actions.axis("down", "up"));
      if (!move.isZero()) camera.camera.position.mulAdd(move.nor(), 8 * dt);
    } else {
      radius = MathUtils.clamp(radius + actions.axis("forward", "back") * 8 * dt, 5, 36);
      camera.camera.position.set(target).mulAdd(camera.camera.direction, -radius);
    }
    camera.camera.update();
  }

  private void syncAngles() {
    pitch = MathUtils.asin(camera.camera.direction.y) * MathUtils.radiansToDegrees;
    yaw =
        MathUtils.atan2(camera.camera.direction.z, camera.camera.direction.x)
            * MathUtils.radiansToDegrees;
  }

  @Override
  public void render() {
    if (failure != null || closed) return;
    try {
      float dt = smoke ? 1f / 60 : Math.min(.05f, Gdx.graphics.getDeltaTime());
      controls(dt);
      game.step(dt);
      actions.finishFrame();
      tools.update(dt);
      stage.act(dt);
      draw(true);
      if (smoke && ++frame == 6) {
        verify();
        cleanup();
        evidence.addChild("cleanup", new JsonValue(true));
        evidence.addChild("result", new JsonValue("PASS"));
        writeReport();
        System.out.println("qualitySmoke: PASS " + evidence.toJson(JsonWriter.OutputType.json));
        Gdx.app.exit();
      }
    } catch (Throwable next) {
      fail(next);
    }
  }

  private void draw(boolean ui) {
    Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    hdr.renderWorld(
        () -> {
          Gdx.gl.glClearColor(skyR, skyG, skyB, 1);
          Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
          renderer.renderWorld(game, camera, null);
        });
    if (ui) {
      tools.drawGizmos();
      drawHud();
      stage.draw();
    }
  }

  private void drawHud() {
    int w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
    hud.getProjectionMatrix().setToOrtho2D(0, 0, w, h);
    hud.begin();
    font.setColor(.80f, .91f, .90f, 1);
    font.draw(hud, "J A V I T Y   /   F O X   G A R D E N", 22, h - 22);
    font.setColor(Color.WHITE);
    font.draw(
        hud,
        presetName
            + "  /  "
            + (freeFly ? "Free-fly" : "Orbit")
            + "  /  Exposure "
            + Math.round(exposure * 100) / 100f
            + (paused ? "  /  Paused" : ""),
        22,
        h - 46);
    font.draw(
        hud, "1 Afternoon   2 Last light   3 Lantern hour   L Point light   -/+ Exposure", 22, 88);
    font.draw(
        hud, "RMB / arrows Look   W/S Zoom or fly   A/D Q/E Fly   C Camera   Home Reset", 22, 67);
    font.draw(hud, "Space Pause   F1 Scene tree / transform gizmos / lights   Esc Exit", 22, 46);
    font.setColor(.76f, .80f, .80f, 1);
    font.draw(
        hud,
        "Fox: PixelMannen (CC0); rig/animation tomkranis; glTF AsoboStudio/scurest (CC BY 4.0)",
        22,
        23);
    hud.end();
  }

  private void verify() {
    paused = true;
    game.pause();
    require(
        foxModels[0].instance.model == foxModels[1].instance.model, "Fox GPU model is not shared");
    double secondTime = animators[1].current().time();
    int[] before = pixels();
    animators[0].current().seek(.55);
    int animation = changed(before, pixels());
    require(animation > 40, "Fox pose did not visibly change: " + animation);
    require(animators[1].current().time() == secondTime, "Other animator changed");
    record("independentAnimationPixels", animation);
    before = pixels();
    foxTransforms[0].position.x -= 1;
    int movement = changed(before, pixels());
    require(movement > 100, "Fox movement was not rendered");
    record("modelMovementPixels", movement);
    foxTransforms[0].position.x += 1;
    preset(3);
    before = pixels();
    press(Input.Keys.L);
    int point = changed(before, pixels());
    require(
        !lantern.isEnabled() && point > 100, "Finite point toggle had no visible effect: " + point);
    record("pointTogglePixels", point);
    press(Input.Keys.L);
    before = pixels();
    press(Input.Keys.EQUALS);
    int tone = changed(before, pixels());
    require(tone > 1000, "HDR exposure had no visible effect: " + tone);
    record("exposurePixels", tone);
    capture("fox-garden-lantern.png");
    preset(2);
    capture("fox-garden-last-light.png");
    preset(1);
    press(Input.Keys.C);
    Vector3 previous = camera.camera.position.cpy();
    input.keyDown(Input.Keys.W);
    controls(.25f);
    game.step(0);
    input.keyUp(Input.Keys.W);
    actions.finishFrame();
    require(camera.camera.position.dst(previous) > 1, "InputActions free-fly did not move camera");
    evidence.addChild("inputActionsCamera", new JsonValue(true));
    press(Input.Keys.HOME);
    capture("fox-garden.png");
    before = pixels(true);
    input.keyDown(Input.Keys.F1);
    tools.update(0);
    stage.act(0);
    int toolsPixels = changed(before, pixels(true));
    require(
        tools.inspector().isVisible() && !navigation.active() && toolsPixels > 1000,
        "F1 tools did not draw or suspend navigation");
    record("toolsTogglePixels", toolsPixels);
    capture("fox-garden-tools.png");
    input.keyDown(Input.Keys.F1);
    require(
        !tools.inspector().isVisible() && navigation.active(),
        "Tools did not hand back navigation");
    require(
        renderer.instanceStats().instanced() == 0,
        "PBR scene unexpectedly used generic instancing");
    require(Gdx.gl.glGetError() == GL20.GL_NO_ERROR, "GL error in composed showcase");
    evidence.addChild(
        "foxCredit", new JsonValue("See tools/animation-validation/fox/NOTICE.md and SOURCE.md"));
  }

  private void press(int key) {
    input.keyDown(key);
    controls(0);
    game.step(0);
    input.keyUp(key);
    actions.finishFrame();
  }

  private void record(String name, int value) {
    evidence.addChild(name, new JsonValue(value));
  }

  private int[] pixels() {
    return pixels(false);
  }

  private int[] pixels(boolean ui) {
    draw(ui);
    Pixmap p =
        Pixmap.createFromFrameBuffer(
            0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    try {
      int[] result = new int[p.getWidth() * p.getHeight()];
      for (int y = 0; y < p.getHeight(); y++)
        for (int x = 0; x < p.getWidth(); x++) result[y * p.getWidth() + x] = p.getPixel(x, y);
      return result;
    } finally {
      p.dispose();
    }
  }

  private static int changed(int[] a, int[] b) {
    int count = 0;
    for (int i = 0; i < a.length; i++)
      if (Math.abs((a[i] >>> 24 & 255) - (b[i] >>> 24 & 255)) > 6
          || Math.abs((a[i] >>> 16 & 255) - (b[i] >>> 16 & 255)) > 6
          || Math.abs((a[i] >>> 8 & 255) - (b[i] >>> 8 & 255)) > 6) count++;
    return count;
  }

  private void capture(String name) {
    draw(true);
    Pixmap p =
        Pixmap.createFromFrameBuffer(
            0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    try {
      output.mkdirs();
      PixmapIO.writePNG(output.child(name), p, -1, true);
    } finally {
      p.dispose();
    }
  }

  @Override
  public void resize(int width, int height) {
    if (tools != null) tools.inspector().onViewportChanged();
    if (renderer != null) renderer.resize(width, height);
    if (hdr != null)
      hdr.resize(Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    if (stage != null) stage.getViewport().update(width, height, true);
  }

  private void focusLost() {
    actions.focusLost();
    discardMouse = true;
    if (tools != null) tools.onFocusLost();
  }

  @Override
  public void pause() {
    focusLost();
  }

  private void fail(Throwable next) {
    if (failure == null) failure = next;
    else if (failure != next) failure.addSuppressed(next);
    next.printStackTrace();
    evidence.addChild("result", new JsonValue("FAIL"));
    evidence.addChild("error", new JsonValue(next.toString()));
    writeReport();
    Gdx.app.exit();
  }

  private void writeReport() {
    output.mkdirs();
    output
        .child("verification.json")
        .writeString(evidence.prettyPrint(JsonWriter.OutputType.json, 100), false);
  }

  private static Throwable clean(Throwable first, Runnable action) {
    try {
      action.run();
    } catch (RuntimeException | Error next) {
      if (first == null) return next;
      if (first != next) first.addSuppressed(next);
    }
    return first;
  }

  private void cleanup() {
    if (closed) return;
    closed = true;
    Gdx.input.setInputProcessor(null);
    Throwable next = null;
    if (tools != null) next = clean(next, tools::close);
    if (stage != null) next = clean(next, stage::dispose);
    if (theme != null) next = clean(next, theme::dispose);
    if (hud != null) next = clean(next, hud::dispose);
    if (font != null) next = clean(next, font::dispose);
    if (game != null) next = clean(next, game::close);
    if (hdr != null) next = clean(next, hdr::dispose);
    if (renderer != null) next = clean(next, renderer::dispose);
    for (GltfInstance instance : instances) next = clean(next, instance::close);
    for (AssetLease<GltfAsset> lease : leases) next = clean(next, lease::close);
    for (Model model : authored) next = clean(next, model::dispose);
    if (next instanceof RuntimeException e) throw e;
    if (next instanceof Error e) throw e;
  }

  @Override
  public void dispose() {
    try {
      cleanup();
    } catch (Throwable next) {
      if (failure == null) failure = next;
      else if (failure != next) failure.addSuppressed(next);
    }
    if (failure != null) throw new IllegalStateException("Quality showcase failed", failure);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
