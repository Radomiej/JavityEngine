package org.javity.demo;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;
import java.util.ArrayList;
import java.util.List;
import net.mgsx.gltf.scene3d.attributes.PBRColorAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRFloatAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRTextureAttribute;
import org.javity.assets.AssetLease;
import org.javity.core.*;
import org.javity.gltf.*;
import org.javity.render.*;
import org.javity.render.InstancedModel;
import org.javity.render.lighting.*;
import org.javity.serialization.AssetId;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;

/** Same GameObjects, camera and light components feed the normal engine renderer's PBR pass. */
final class GltfDemo extends ApplicationAdapter {
  private static final String FIXTURES = "org/javity/gltf/fixtures/";
  private final boolean smoke;
  private final List<GltfInstance> instances = new ArrayList<>();
  private final List<AssetLease<GltfAsset>> leases = new ArrayList<>();
  private final List<Integer> textureIds = new ArrayList<>();
  private final List<Texture> textures = new ArrayList<>();
  private final List<GltfAsset> imported = new ArrayList<>();
  private Game game;
  private GdxRenderer renderer;
  private GltfAssets assets;
  private Camera3D camera;
  private DirectionalLight3D sun;
  private PointLight3D bulb;
  private ModelRenderer skinA, skinB;
  private Transform3D staticTransform;
  private FrameBuffer fbo;
  private Throwable failure;
  private long originalContext, replacementContext;
  private GLCapabilities originalCapabilities, replacementCapabilities;
  private boolean cleaned;

  GltfDemo(boolean smoke) {
    this.smoke = smoke;
  }

  @Override
  public void create() {
    try {
      setup();
      if (smoke) {
        verify();
        cleanup();
        System.out.println(
            "gltfGpuSmoke: PASS (GLB/glTF, five PBR maps, independent skin poses, scene"
                + " light/transform pixels, rollback, cleanup)");
        Gdx.app.exit();
      }
    } catch (Throwable next) {
      failure = next;
      next.printStackTrace();
      Gdx.app.exit();
    }
  }

  private void setup() {
    game = new Game();
    assets = game.addSystem(new GltfAssets(12));
    assets.register(new AssetId<>("fixture/static"), fixture("static.glb"));
    assets.register(new AssetId<>("fixture/skinned"), fixture("skinned.glb"));
    RenderEnvironment3D environment = game.addSystem(new RenderEnvironment3D());
    environment.setAmbient(.025f, .025f, .025f);
    renderer = new PbrShaderProvider(12, 2, 4, 2).createRenderer();
    renderer.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    camera = game.createObject("camera").addComponent(new Camera3D(45));
    camera.camera.position.set(0, .1f, 5);
    camera.camera.lookAt(0, .1f, 0);
    GameObject light = game.createObject("scene directional light");
    light.addComponent(new Transform3D());
    sun = light.addComponent(new DirectionalLight3D());
    sun.setIntensity(2);
    GameObject point = game.createObject("finite range scene point light");
    point.addComponent(new Transform3D()).position.set(0, 0, 1);
    bulb = point.addComponent(new PointLight3D());
    bulb.setRange(4);
    bulb.setIntensity(0);
    ModelRenderer staticRenderer = model("static", "fixture/static", -1.2f);
    staticTransform = staticRenderer.getComponent(Transform3D.class);
    skinA = model("skin A", "fixture/skinned", 0);
    skinB = model("skin B", "fixture/skinned", 1.2f);
    for (AssetLease<GltfAsset> lease : leases) lease.close();
    skinA.play("lift", true);
    skinB.play("lift", true);
    skinA.animation.update(.15f);
    skinB.animation.update(.7f);
    game.start();
    game.step(0);
  }

  private ModelRenderer model(String name, String id, float x) {
    AssetLease<GltfAsset> lease = assets.request(new AssetId<>(id));
    leases.add(lease);
    {
      require(
          lease.state() == AssetLease.State.READY, "Failed loading " + id + ": " + lease.failure());
      GltfAsset asset = lease.get();
      imported.add(asset);
      GltfInstance instance = asset.instantiate();
      instances.add(instance);
      Material material = instance.modelInstance().materials.first();
      require(material.has(PBRColorAttribute.BaseColorFactor), "Lost base color factor");
      require(
          material.has(PBRFloatAttribute.Metallic) && material.has(PBRFloatAttribute.Roughness),
          "Lost metallic/roughness factors");
      for (long attribute :
          new long[] {
            PBRTextureAttribute.BaseColorTexture,
            PBRTextureAttribute.NormalTexture,
            PBRTextureAttribute.MetallicRoughnessTexture,
            PBRTextureAttribute.OcclusionTexture,
            PBRTextureAttribute.EmissiveTexture
          }) {
        require(material.has(attribute), "Lost PBR texture channel " + attribute);
        Texture texture =
            material.get(TextureAttribute.class, attribute).textureDescription.texture;
        require(texture.isManaged(), "Imported texture cannot recover context");
        textures.add(texture);
        textureIds.add(texture.getTextureObjectHandle());
      }
      GameObject object = game.createObject(name);
      Transform3D transform = object.addComponent(new Transform3D());
      transform.position.set(x, -.3f, 0);
      // Custom PBR providers must keep these ordinary: current instance shaders aren't PBR.
      object.addComponent(new InstancedModel());
      return object.addComponent(instance.createRenderer());
    }
  }

  private void verify() {
    fbo = new RecoverableFrameBuffer(Pixmap.Format.RGBA8888, 384, 256, true);
    renderer.resize(384, 256);
    float a = skinA.instance.getNode("joint").globalTransform.getTranslation(new Vector3()).y;
    float b = skinB.instance.getNode("joint").globalTransform.getTranslation(new Vector3()).y;
    require(
        Math.abs(a - .105f) < .001f && Math.abs(b - .49f) < .001f,
        "Imported skins did not keep independent clip times");
    require(
        skinA.instance.model == skinB.instance.model,
        "Instances did not share their imported GPU model");
    require(
        skinA.instance.nodes.first().parts.first().bones
            != skinB.instance.nodes.first().parts.first().bones,
        "Imported instances share bone palettes");
    Pixels lit = capture("lit.png");
    require(lit.count > 1000, "Imported PBR geometry produced too few pixels");
    require(
        renderer.instanceStats().instanced() == 0,
        "PBR materials were routed through non-PBR instancing");
    sun.setIntensity(0);
    Pixels dark = capture("light-off.png");
    require(lit.energy > dark.energy + 10000, "Scene light did not alter PBR framebuffer");
    bulb.setIntensity(4);
    Pixels pointLit = capture("point-range-four.png");
    require(
        pointLit.energy > dark.energy + 1000, "Scene point light did not illuminate PBR pixels");
    bulb.setRange(.25f);
    Pixels pointOutOfRange = capture("point-range-quarter.png");
    require(pointOutOfRange.checksum == dark.checksum, "PBR finite point range was lost");
    bulb.setIntensity(0);
    bulb.setRange(4);
    sun.setIntensity(2);
    PBRFloatAttribute roughness =
        skinA.instance.materials.first().get(PBRFloatAttribute.class, PBRFloatAttribute.Roughness);
    float savedRoughness = roughness.value;
    roughness.value = .08f;
    Pixels smooth = capture("roughness-low.png");
    require(smooth.checksum != lit.checksum, "PBR roughness factor did not affect rendered pixels");
    roughness.value = savedRoughness;
    staticTransform.position.x += .5f;
    Pixels moved = capture("transform-moved.png");
    require(
        Math.abs(moved.checksum - lit.checksum) > 1000,
        "Actual GameObject transform did not move pixels");
    staticTransform.position.x -= .5f;
    // Simulate lost imported texture names, then exercise the real managed texture reload path.
    for (int texture : new java.util.HashSet<>(textureIds)) Gdx.gl.glDeleteTexture(texture);
    Texture.invalidateAllTextures(Gdx.app);
    textureIds.clear();
    for (Texture texture : textures) textureIds.add(texture.getTextureObjectHandle());
    renderer.restoreContextShaderBindings();
    Pixels recovered = capture("shader-rebuilt.png");
    require(recovered.checksum == lit.checksum, "PBR shader recreation changed pixels");
    String meshes = Mesh.getManagedStatus(), textures = Texture.getManagedStatus();
    boolean rejected = false;
    List<Integer> failedTextureIds = new ArrayList<>();
    GL20 actualGl = Gdx.gl, actualGl20 = Gdx.gl20;
    GL20 tracking =
        (GL20)
            java.lang.reflect.Proxy.newProxyInstance(
                GL20.class.getClassLoader(),
                new Class<?>[] {GL20.class},
                (proxy, method, args) -> {
                  try {
                    Object result = method.invoke(actualGl20, args);
                    if (method.getName().equals("glGenTexture"))
                      failedTextureIds.add((Integer) result);
                    return result;
                  } catch (java.lang.reflect.InvocationTargetException cause) {
                    throw cause.getCause();
                  }
                });
    try {
      Gdx.gl = tracking;
      Gdx.gl20 = tracking;
      try {
        GltfAsset.load(fixture("invalid-after-mesh.glb"), 12).close();
      } catch (RuntimeException expected) {
        rejected = true;
      }
    } finally {
      Gdx.gl = actualGl;
      Gdx.gl20 = actualGl20;
    }
    require(!failedTextureIds.isEmpty(), "Invalid fixture never allocated textures before failing");
    for (int texture : failedTextureIds)
      require(!Gdx.gl.glIsTexture(texture), "Failed import leaked native texture " + texture);
    require(rejected, "Invalid second primitive was accepted");
    require(
        meshes.equals(Mesh.getManagedStatus()) && textures.equals(Texture.getManagedStatus()),
        "Importer failure leaked managed mesh/texture allocations");
    try (GltfAsset json = GltfAsset.load(fixture("static.gltf"), 12);
        GltfInstance instance = json.instantiate()) {
      require(
          instance.modelInstance().materials.first().has(PBRTextureAttribute.OcclusionTexture),
          "JSON glTF material channel lost");
    }
    GdxRenderer incompatible = new GdxRenderer();
    try {
      boolean guarded = false;
      try {
        incompatible.renderWorld(game, camera, null);
      } catch (IllegalArgumentException expected) {
        guarded = true;
      }
      require(guarded, "Default material pass silently accepted PBR-only renderer");
    } finally {
      incompatible.dispose();
    }
    unsharedContextProof();
    // Leases ended after model setup; instance pins protect every texture through rendering.
    for (int texture : textureIds)
      require(Gdx.gl.glIsTexture(texture), "Lease close disposed a live instance texture");
  }

  private void unsharedContextProof() {
    Pixels before = capture("context-before");
    originalContext = GLFW.glfwGetCurrentContext();
    originalCapabilities = GL.getCapabilities();
    GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
    replacementContext = GLFW.glfwCreateWindow(384, 256, "glTF unshared replacement", 0, 0);
    require(replacementContext != 0, "Cannot create unshared context");
    GLFW.glfwMakeContextCurrent(replacementContext);
    replacementCapabilities = GL.createCapabilities();
    Texture.invalidateAllTextures(Gdx.app);
    com.badlogic.gdx.graphics.glutils.ShaderProgram.invalidateAllShaderPrograms(Gdx.app);
    Mesh.invalidateAllMeshes(Gdx.app);
    com.badlogic.gdx.graphics.glutils.GLFrameBuffer.invalidateAllFrameBuffers(Gdx.app);
    renderer.restoreContextShaderBindings();
    Pixels withoutRepair = capture("context-without-vao-repair");
    // Diagnostic before adding a recovery adapter: identical-location VAOs must be inspected.
    System.out.println("gltfContextDiagnostic: before=" + before + " after=" + withoutRepair);
    textureIds.clear();
    for (Texture texture : textures) textureIds.add(texture.getTextureObjectHandle());
    require(
        withoutRepair.checksum == before.checksum,
        "Imported mesh did not recover in unshared context");
  }

  private Pixels capture(String name) {
    fbo.begin();
    Pixmap pixmap = null;
    try {
      draw();
      pixmap = ScreenUtils.getFrameBufferPixmap(0, 0, 384, 256);
      PixmapIO.writePNG(
          new FileHandle("build/reports/gltf/" + (Gdx.gl30 == null ? "gl20/" : "gl30/") + name),
          pixmap);
      long energy = 0, checksum = 0;
      int count = 0;
      for (int y = 0; y < 256; y++)
        for (int x = 0; x < 384; x++) {
          int color = pixmap.getPixel(x, y),
              r = color >>> 24 & 255,
              g = color >>> 16 & 255,
              b = color >>> 8 & 255;
          if (r > g + 5 && r > b + 5) {
            count++;
            energy += r + g + b;
            checksum += (long) (r + g + b) * (x + 1 + y * 384);
          }
        }
      return new Pixels(count, energy, checksum);
    } finally {
      if (pixmap != null) pixmap.dispose();
      fbo.end();
    }
  }

  private void draw() {
    Gdx.gl.glClearColor(.015f, .02f, .025f, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    renderer.renderWorld(game, camera, null);
  }

  @Override
  public void render() {
    if (smoke || failure != null) return;
    try {
      if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) Gdx.app.exit();
      if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE))
        sun.setIntensity(sun.intensity() == 0 ? 2 : 0);
      game.step(Math.min(.05f, Gdx.graphics.getDeltaTime()));
      draw();
    } catch (Throwable next) {
      failure = next;
      next.printStackTrace();
      Gdx.app.exit();
    }
  }

  @Override
  public void resize(int width, int height) {
    if (renderer != null) renderer.resize(width, height);
  }

  @Override
  public void dispose() {
    try {
      cleanup();
    } catch (Throwable next) {
      if (failure == null) failure = next;
      else if (failure != next) failure.addSuppressed(next);
    }
    if (failure != null) throw new IllegalStateException("glTF demo failed", failure);
  }

  private void cleanup() {
    if (cleaned) return;
    cleaned = true;
    if (replacementContext != 0) {
      GLFW.glfwMakeContextCurrent(replacementContext);
      GL.setCapabilities(replacementCapabilities);
    }
    ResourceScope cleanup = new ResourceScope();
    if (fbo != null) cleanup.own(fbo::dispose);
    if (renderer != null) cleanup.own(renderer::dispose);
    for (GltfInstance instance : instances) cleanup.own(instance);
    for (AssetLease<GltfAsset> lease : leases) cleanup.own(lease);
    if (game != null) cleanup.own(game::close);
    try {
      cleanup.close();
      if (smoke) {
        for (GltfAsset asset : imported)
          require(asset.isDisposed(), "Asset remained alive after all instance releases");
        for (int texture : textureIds)
          require(!Gdx.gl.glIsTexture(texture), "Asset close leaked native texture " + texture);
      }
    } finally {
      if (replacementContext != 0) {
        GLFW.glfwMakeContextCurrent(originalContext);
        GL.setCapabilities(originalCapabilities);
        GLFW.glfwDestroyWindow(replacementContext);
        replacementContext = 0;
      }
    }
  }

  private static FileHandle fixture(String file) {
    return Gdx.files.classpath(FIXTURES + file);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }

  private record Pixels(int count, long energy, long checksum) {}
}
