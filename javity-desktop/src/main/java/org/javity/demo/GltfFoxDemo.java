package org.javity.demo;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import java.util.Arrays;
import org.javity.animation.ModelPoseAnimator;
import org.javity.animation.PlayMode;
import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.core.ResourceScope;
import org.javity.core.Transform3D;
import org.javity.gltf.GltfAsset;
import org.javity.gltf.GltfInstance;
import org.javity.gltf.PbrShaderProvider;
import org.javity.render.Camera3D;
import org.javity.render.GdxRenderer;
import org.javity.render.ModelRenderer;
import org.javity.render.lighting.DirectionalLight3D;
import org.javity.render.lighting.RenderEnvironment3D;

/** Direct original glTF import; no G3DJ conversion or duplicated Fox assets. */
public final class GltfFoxDemo extends ApplicationAdapter {
  private static final int WIDTH = 800, HEIGHT = 600;
  private static final String[] CLIPS = {"Survey", "Walk", "Run"};
  private final boolean smoke;
  private final FileHandle assetRoot, output;
  private final ResourceScope resources = new ResourceScope();
  private final JsonValue report = new JsonValue(JsonValue.ValueType.object);
  private final ModelRenderer[] models = new ModelRenderer[2];
  private final ModelPoseAnimator[] animators = new ModelPoseAnimator[2];
  private Game game;
  private GltfAsset asset;
  private GdxRenderer renderer;
  private Camera3D camera;
  private DirectionalLight3D keyLight, fillLight;
  private SpriteBatch batch;
  private BitmapFont font;
  private Throwable failure;
  private boolean cleaned, paused;
  private String clip = "Walk";

  private GltfFoxDemo(boolean smoke, String assetRoot, String output) {
    this.smoke = smoke;
    this.assetRoot = new FileHandle(assetRoot);
    this.output = new FileHandle(output);
  }

  /** Run from repo root; --smoke exits after assertions, --gl20 chooses GL2 instead of GL3. */
  public static void main(String[] args) {
    var flags = Arrays.asList(args);
    boolean gl20 = flags.contains("--gl20"), smoke = flags.contains("--smoke");
    String assetRoot = "tools/animation-validation/fox";
    String output = "build/reports/gltf-fox/" + (gl20 ? "gl20" : "gl30");
    for (String arg : args) {
      if (arg.startsWith("--asset-root=")) assetRoot = arg.substring("--asset-root=".length());
      if (arg.startsWith("--output=")) output = arg.substring("--output=".length());
    }
    var config = new Lwjgl3ApplicationConfiguration();
    config.setTitle("Javity | original glTF Fox | two independent ModelPoseAnimator instances");
    config.setWindowedMode(WIDTH, HEIGHT);
    config.setInitialVisible(!smoke);
    config.setForegroundFPS(60);
    config.disableAudio(true);
    config.setOpenGLEmulation(
        gl20
            ? Lwjgl3ApplicationConfiguration.GLEmulation.GL20
            : Lwjgl3ApplicationConfiguration.GLEmulation.GL30,
        gl20 ? 2 : 3,
        gl20 ? 0 : 2);
    new Lwjgl3Application(new GltfFoxDemo(smoke, assetRoot, output), config);
  }

  @Override
  public void create() {
    try {
      setup();
      if (smoke) {
        verify();
        cleanup();
        report.addChild("result", new JsonValue("PASS"));
        writeReport();
        System.out.println("gltfFoxSmoke: PASS " + report.toJson(JsonWriter.OutputType.json));
        Gdx.app.exit();
      }
    } catch (Throwable next) {
      fail(next);
    }
  }

  private void setup() {
    require(assetRoot.child("Fox.gltf").exists(), "Missing original Fox.gltf at " + assetRoot);
    asset = resources.own(GltfAsset.load(assetRoot.child("Fox.gltf"), 24));
    GltfInstance first = resources.own(asset.instantiate());
    GltfInstance second = resources.own(asset.instantiate());
    game = new Game();
    resources.own(game);
    RenderEnvironment3D environment = game.addSystem(new RenderEnvironment3D());
    environment.setAmbient(.15f, .15f, .15f);
    renderer = new PbrShaderProvider(24, 2, 0, 0).createRenderer();
    resources.own(renderer::dispose);
    renderer.resize(WIDTH, HEIGHT);
    camera = game.createObject("camera").addComponent(new Camera3D(40));
    camera.camera.position.set(7, 3.8f, 5.2f);
    camera.camera.lookAt(0, .8f, 0);
    camera.camera.near = .05f;
    camera.camera.far = 50;
    keyLight = light("key", 2.5f, -35, -35);
    fillLight = light("fill", 1, 35, 135);
    fox(first, 0, -1.5f);
    fox(second, 1, 1.5f);
    batch = new SpriteBatch();
    resources.own(batch::dispose);
    font = new BitmapFont();
    resources.own(font::dispose);
    game.start();
    animators[0].play("Walk", PlayMode.LOOP).seek(.25);
    animators[1].play("Walk", PlayMode.LOOP).seek(.5);
    require(asset.maxBones() == 24, "Original 24-joint skin was not retained");
    for (String name : CLIPS)
      require(models[0].instance.getAnimation(name) != null, "Lost original clip " + name);
    report.addChild("source", new JsonValue(assetRoot.child("Fox.gltf").path()));
    report.addChild("profile", new JsonValue(Gdx.gl30 == null ? "GL20" : "GL30"));
    report.addChild("glVersion", new JsonValue(Gdx.gl.glGetString(GL20.GL_VERSION)));
    report.addChild("worldScale", new JsonValue(.02));
    report.addChild("bones", new JsonValue(asset.maxBones()));
    report.addChild(
        "credit",
        new JsonValue(
            "Fox: PixelMannen (CC0); rig/animation: tomkranis; glTF: AsoboStudio/scurest (CC BY"
                + " 4.0). See tools/animation-validation/fox/NOTICE.md and SOURCE.md."));
  }

  private DirectionalLight3D light(String name, float intensity, float pitch, float yaw) {
    GameObject object = game.createObject(name);
    object.addComponent(new Transform3D()).rotation.setEulerAngles(yaw, pitch, 0);
    DirectionalLight3D light = object.addComponent(new DirectionalLight3D());
    light.setIntensity(intensity);
    return light;
  }

  private void fox(GltfInstance instance, int index, float x) {
    GameObject object = game.createObject("original glTF Fox " + index);
    Transform3D transform = object.addComponent(new Transform3D());
    transform.position.x = x;
    transform.scale.set(.02f, .02f, .02f);
    models[index] = object.addComponent(instance.createRenderer());
    animators[index] = object.addComponent(new ModelPoseAnimator());
  }

  private void verify() {
    require(models[0].instance.model == models[1].instance.model, "GPU models were duplicated");
    NodePart first = skinned(models[0].instance.nodes), second = skinned(models[1].instance.nodes);
    require(
        first.bones != second.bones && first.bones[0] != second.bones[0], "Shared skin palette");
    require(first.meshPart.mesh.getNumVertices() == 1728, "Original vertex count changed");
    JsonValue oracle = new JsonReader().parse(assetRoot.child("oracle.json"));
    double maxError = 0;
    for (JsonValue sample : oracle.get("samples")) {
      animators[0].play(sample.getString("clip"), PlayMode.LOOP).seek(sample.getDouble("time"));
      maxError = Math.max(maxError, delta(skin(models[0]), sample.get("vertices").asFloatArray()));
    }
    require(maxError < .01, "Original glTF oracle skin mismatch: " + maxError);
    report.addChild("oracleSamples", new JsonValue(oracle.get("samples").size));
    report.addChild("verticesPerSample", new JsonValue(first.meshPart.mesh.getNumVertices()));
    report.addChild("oracleMaxErrorAssetUnits", new JsonValue(maxError));
    JsonValue changed = new JsonValue(JsonValue.ValueType.object);
    report.addChild("changedGeometryPixels", changed);
    for (String name : CLIPS) {
      animators[0].play(name, PlayMode.LOOP).seek(.25);
      animators[1].play(name, PlayMode.LOOP).seek(.5);
      float[] unchanged = skin(models[1]);
      float[] before = skin(models[0]);
      int[] pixels = pixels();
      animators[0].current().seek(.5);
      require(delta(before, skin(models[0])) > .1, name + " did not change actual skin vertices");
      require(delta(unchanged, skin(models[1])) == 0, name + " mutated the other instance");
      int differences = changedPixels(pixels, pixels());
      require(differences > 100, name + " did not change geometry framebuffer: " + differences);
      changed.addChild(name, new JsonValue(differences));
      animators[0].current().seek(.25);
      shot(name.toLowerCase() + "-independent.png", name + " | left 0.25s / right 0.50s");
    }
    double a = animators[0].current().time(), b = animators[1].current().time();
    float[] stable = skin(models[0]);
    pixels();
    pixels();
    require(
        animators[0].current().time() == a && animators[1].current().time() == b,
        "Render advanced animation clocks");
    require(delta(stable, skin(models[0])) == 0, "Render changed skin pose");
    game.step(.05f);
    require(
        Math.abs(animators[0].current().time() - a - .05) < 1e-6, "Game step did not advance pose");
    report.addChild("independentInstances", new JsonValue(true));
    report.addChild("renderClockStable", new JsonValue(true));
    report.addChild("gameStepAdvancesAnimation", new JsonValue(true));
    int[] lit = pixels();
    keyLight.setIntensity(0);
    fillLight.setIntensity(0);
    int lightChanges = changedPixels(lit, pixels(false));
    require(lightChanges > 1500, "Scene lights did not affect the imported PBR material");
    keyLight.setIntensity(2.5f);
    fillLight.setIntensity(1);
    report.addChild("sceneLightChangedPixels", new JsonValue(lightChanges));
    require(
        renderer.instanceStats().instanced() == 0, "Skinned PBR Fox entered non-PBR instancing");
  }

  /** Deforms the actual imported interleaved mesh using its per-instance GPU bone palette. */
  private static float[] skin(ModelRenderer model) {
    NodePart part = skinned(model.instance.nodes);
    require(part != null && part.bones.length == 24, "Missing 24-joint imported skin");
    Mesh mesh = part.meshPart.mesh;
    int stride = mesh.getVertexSize() / Float.BYTES;
    float[] vertices = new float[mesh.getNumVertices() * stride];
    mesh.getVertices(vertices);
    int position = mesh.getVertexAttribute(VertexAttributes.Usage.Position).offset / Float.BYTES;
    float[] result = new float[mesh.getNumVertices() * 3];
    Vector3 source = new Vector3(), transformed = new Vector3(), sum = new Vector3();
    for (int i = 0; i < mesh.getNumVertices(); i++) {
      source.set(
          vertices[i * stride + position],
          vertices[i * stride + position + 1],
          vertices[i * stride + position + 2]);
      sum.setZero();
      for (VertexAttribute attribute : mesh.getVertexAttributes()) {
        if (attribute.usage != VertexAttributes.Usage.BoneWeight) continue;
        int offset = i * stride + attribute.offset / Float.BYTES;
        float weight = vertices[offset + 1];
        if (weight != 0)
          sum.mulAdd(transformed.set(source).mul(part.bones[(int) vertices[offset]]), weight);
      }
      result[i * 3] = sum.x;
      result[i * 3 + 1] = sum.y;
      result[i * 3 + 2] = sum.z;
    }
    return result;
  }

  private static NodePart skinned(Array<Node> nodes) {
    for (Node node : nodes) {
      for (NodePart part : node.parts) if (part.bones != null) return part;
      for (Node child : node.getChildren()) {
        Array<Node> children = new Array<>();
        children.add(child);
        NodePart part = skinned(children);
        if (part != null) return part;
      }
    }
    return null;
  }

  private static double delta(float[] first, float[] second) {
    require(first.length == second.length, "Skin vertex count mismatch");
    double max = 0;
    for (int i = 0; i < first.length; i++) max = Math.max(max, Math.abs(first[i] - second[i]));
    return max;
  }

  private int[] pixels() {
    return pixels(true);
  }

  private int[] pixels(boolean requireVisibleFox) {
    draw();
    Pixmap pixmap = Pixmap.createFromFrameBuffer(0, 0, WIDTH, HEIGHT);
    try {
      int[] result = new int[WIDTH * HEIGHT];
      int visible = 0;
      for (int y = 0; y < HEIGHT; y++)
        for (int x = 0; x < WIDTH; x++) {
          int color = pixmap.getPixel(x, y);
          result[y * WIDTH + x] = color;
          if ((color >>> 24 & 255) > (color >>> 16 & 255) + 15) visible++;
        }
      if (requireVisibleFox)
        require(visible > 1500, "Too few visible orange Fox pixels: " + visible);
      return result;
    } finally {
      pixmap.dispose();
    }
  }

  private static int changedPixels(int[] first, int[] second) {
    int changed = 0;
    for (int i = 0; i < first.length; i++) {
      int a = first[i], b = second[i];
      if (Math.abs((a >>> 24 & 255) - (b >>> 24 & 255)) > 6
          || Math.abs((a >>> 16 & 255) - (b >>> 16 & 255)) > 6
          || Math.abs((a >>> 8 & 255) - (b >>> 8 & 255)) > 6) changed++;
    }
    return changed;
  }

  private void draw() {
    Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    Gdx.gl.glClearColor(.045f, .065f, .09f, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    renderer.renderWorld(game, camera, null);
  }

  private void hud(String label) {
    batch
        .getProjectionMatrix()
        .setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    batch.begin();
    font.draw(batch, "JAVITY / ORIGINAL glTF FOX / PBR", 20, Gdx.graphics.getHeight() - 20);
    font.draw(batch, label, 20, Gdx.graphics.getHeight() - 45);
    font.draw(
        batch,
        "24 joints | 1728 vertices | original Fox.gltf + Fox.bin + Texture.png",
        20,
        Gdx.graphics.getHeight() - 68);
    font.draw(batch, "Fox: PixelMannen (CC0) | rig/animation: tomkranis (CC BY 4.0)", 20, 45);
    font.draw(
        batch, "glTF: AsoboStudio / scurest (CC BY 4.0) | see original source notices", 20, 24);
    batch.end();
  }

  private void shot(String name, String label) {
    draw();
    hud(label);
    Pixmap pixmap = Pixmap.createFromFrameBuffer(0, 0, WIDTH, HEIGHT);
    try {
      output.mkdirs();
      PixmapIO.writePNG(output.child(name), pixmap, -1, true);
    } finally {
      pixmap.dispose();
    }
  }

  @Override
  public void render() {
    if (smoke || failure != null) return;
    try {
      if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) Gdx.app.exit();
      if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) paused = !paused;
      for (int i = 0; i < CLIPS.length; i++)
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1 + i)) {
          clip = CLIPS[i];
          animators[0].play(clip, PlayMode.LOOP);
          animators[1].play(clip, PlayMode.LOOP).seek(.25);
        }
      if (!paused) game.step(Math.min(.05f, Gdx.graphics.getDeltaTime()));
      draw();
      hud(clip + " | 1 Survey / 2 Walk / 3 Run | Space pause | Esc exit");
    } catch (Throwable next) {
      fail(next);
    }
  }

  @Override
  public void resize(int width, int height) {
    if (renderer != null) renderer.resize(width, height);
  }

  private void fail(Throwable next) {
    failure = next;
    next.printStackTrace();
    report.addChild("result", new JsonValue("FAIL"));
    report.addChild("error", new JsonValue(next.toString()));
    writeReport();
    Gdx.app.exit();
  }

  private void writeReport() {
    output.mkdirs();
    output
        .child("verification.json")
        .writeString(report.prettyPrint(JsonWriter.OutputType.json, 100), false);
  }

  private void cleanup() {
    if (cleaned) return;
    cleaned = true;
    resources.close();
    require(asset == null || asset.isDisposed(), "Imported asset outlived closed instance handles");
  }

  @Override
  public void dispose() {
    try {
      cleanup();
    } catch (Throwable next) {
      if (failure == null) failure = next;
      else if (failure != next) failure.addSuppressed(next);
    }
    if (failure != null) throw new IllegalStateException("Original glTF Fox demo failed", failure);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
