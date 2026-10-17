package org.javity.demo;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.*;
import com.badlogic.gdx.graphics.g3d.environment.*;
import com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader;
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader;
import com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.profiling.GLProfiler;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.utils.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.*;
import java.util.*;
import org.javity.animation.*;
import org.javity.avatar.*;
import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.core.Transform3D;
import org.javity.render.*;

/** Reproducible imported Fox and prepared-avatar proof; no render-time animation updates. */
public final class SkinnedInstancingDemo extends ApplicationAdapter {
  private static final String OUTPUT = "docs/artifacts/skinned-instancing/";
  private final boolean gl20;
  private Model fox;
  private Game game;
  private ModelBatch ordinary;
  private SkinnedInstanceBatch batch;
  private FrameBuffer target;
  private OrthographicCamera camera;
  private GLProfiler profiler;
  private Calls calls;
  private final Environment environment = new Environment();
  private final Array<Renderable> candidates = new Array<>();
  private final java.util.List<ModelRenderer> actors = new ArrayList<>();
  private final java.util.List<ModelPoseAnimator> animators = new ArrayList<>();
  private final Pool<Renderable> pool =
      new Pool<>() {
        protected Renderable newObject() {
          return new Renderable();
        }
      };
  private int assertions;

  SkinnedInstancingDemo(boolean gl20) {
    this.gl20 = gl20;
  }

  @Override
  public void create() {
    try {
      System.out.println(
          "actual GL: "
              + Gdx.gl.glGetString(GL20.GL_VERSION)
              + " renderer: "
              + Gdx.gl.glGetString(GL20.GL_RENDERER));
      run();
      dispose();
      System.out.println(
          "skinnedInstancingSmoke " + (gl20 ? "GL20" : "GL33") + ": PASS assertions=" + assertions);
    } catch (Throwable failure) {
      failure.printStackTrace();
      System.exit(1);
    } finally {
      dispose();
      Gdx.app.exit();
    }
  }

  private void run() {
    fox =
        new G3dModelLoader(new JsonReader())
            .loadModel(Gdx.files.local("tools/animation-validation/fox/Fox.g3dj"));
    DefaultShader.Config config = new DefaultShader.Config();
    config.numBones = 24;
    config.numBoneWeights = 4;
    ordinary = new ModelBatch(new DefaultShaderProvider(config));
    batch = new SkinnedInstanceBatch(256, 1024);
    target = new FrameBuffer(Pixmap.Format.RGBA8888, 640, 480, true);
    camera = new OrthographicCamera(13.4f, 10.05f);
    camera.position.set(0, 4, 12);
    camera.lookAt(0, 0, 0);
    camera.near = .1f;
    camera.far = 60;
    camera.update();
    environment.set(ColorAttribute.createAmbientLight(.45f, .45f, .5f, 1));
    environment.add(new DirectionalLight().set(.9f, .9f, 1, -1, -.8f, -.4f));
    game = new Game();
    for (int i = 0; i < 6; i++) {
      GameObject object = game.createObject("Fox " + i);
      object.addComponent(new Transform3D());
      actors.add(object.addComponent(new ModelRenderer(fox)));
      animators.add(object.addComponent(new ModelPoseAnimator()));
    }
    game.start();
    game.step(0);
    profiler = new GLProfiler(Gdx.graphics);
    profiler.enable();
    calls = new Calls();
    calls.install();
    String[] clips = {"Walk", "Run", "Survey"};
    for (int i = 0; i < 6; i++)
      animators.get(i).play(clips[i % 3], PlayMode.LOOP).seek(i < 3 ? .25 : .5);
    collect();
    Pixmap first = image(false, "independent-ordinary");
    check(profiler.getDrawCalls() == 6, "six actual ordinary Fox draws");
    Pixmap optimized = image(true, "independent-instanced");
    compare(first, optimized, "six independent Walk/Run/Survey poses");
    Pixmap pair = new Pixmap(1280, 480, Pixmap.Format.RGBA8888);
    pair.setBlending(Pixmap.Blending.None);
    pair.drawPixmap(first, 0, 0);
    pair.drawPixmap(optimized, 640, 0);
    PixmapIO.writePNG(Gdx.files.local(OUTPUT + (gl20 ? "gl20" : "gl33") + "-comparison.png"), pair);
    pair.dispose();
    first.dispose();
    optimized.dispose();
    if (gl20) {
      check(
          batch.stats().fallback() == 6 && batch.fallbackReasons().containsKey("GL30_UNAVAILABLE"),
          "explicit animated GL20 fallback");
      return;
    }
    check(
        batch.stats().instanced() == 6 && batch.stats().draws() == 1 && calls.instanced == 1,
        "independent poses in one actual draw " + batch.stats() + batch.fallbackReasons());
    check(
        batch.stats().uploadBytes() == calls.instanceBytes + calls.paletteBytes,
        "measured uploads match stats");
    check(calls.paletteBytes == 6 * 24 * 64, "six distinct full palettes uploaded");
    System.out.println(
        "independent poses actual draws 6 -> "
            + profiler.getDrawCalls()
            + ", paletteBytes="
            + calls.paletteBytes
            + ", instanceBytes="
            + calls.instanceBytes);
    verifyGpuVertices();
    image(true, "unchanged").dispose();
    check(
        calls.paletteBytes == 0 && calls.instanceBytes == 0 && batch.stats().uploadBytes() == 0,
        "unchanged second view no uploads");
    long composed = compositions();
    double time = animators.get(0).current().time();
    image(true, "second-view").dispose();
    image(true, "third-view").dispose();
    check(
        compositions() == composed && animators.get(0).current().time() == time,
        "two renders never advance clocks or compose poses");
    comparePair("ordinary-after-instance");
    for (ModelPoseAnimator animator : animators) animator.play("Walk", PlayMode.LOOP).seek(.25);
    collect();
    comparePair("shared-pose");
    check(calls.paletteBytes <= 24 * 64, "equal poses share one palette row");
    for (int i = 0; i < 6; i++)
      animators.get(i).play(AnimationTransition.clip("Run").loop().fade(.5));
    game.step(.2f);
    collect();
    comparePair("crossfade-mid");
    for (int i = 0; i < 6; i++) {
      animators.get(i).play("Walk", PlayMode.LOOP).seek(.25);
      var tail = animators.get(i).addLayer("tail", NodeMask.subtreeIds("b_Tail01_012"));
      tail.play("Run", PlayMode.LOOP).seek(.5);
      tail.setWeight(1);
    }
    collect();
    comparePair("tail-mask");
    for (ModelPoseAnimator animator : animators)
      animator
          .play(LinearModelMixer.builder(1).add("Walk", 0).add("Run", 1).build(), PlayMode.LOOP, .5)
          .seek(.25);
    collect();
    comparePair("mixer");
    verifyNegativeControl();
    verifyAlphaTest();
    verifyFallbacks();
    verifyBudgetAndPages();
    verifyStateAndFailures();
    verifyLimits();
    verifyOracle();
    verifyAvatar();
    verifySceneBoundary();
    check(!fox.meshes.first().isInstanced(), "borrowed Fox mesh stays ordinary");
    int texture =
        ((TextureAttribute) fox.materials.first().get(TextureAttribute.Diffuse))
            .textureDescription.texture.getTextureObjectHandle();
    batch.dispose();
    batch = null;
    check(Gdx.gl.glIsTexture(texture), "borrowed diffuse texture survives batch disposal");
    for (int handle : calls.paletteTextures)
      check(!Gdx.gl.glIsTexture(handle), "owned palette texture deleted " + handle);
    check(Gdx.gl.glGetError() == GL20.GL_NO_ERROR, "no remaining GL error");
  }

  private long compositions() {
    return animators.stream().mapToLong(ModelPoseAnimator::fullPoseCompositions).sum();
  }

  private void collect() {
    candidates.clear();
    for (int i = 0; i < actors.size(); i++) {
      ModelRenderer actor = actors.get(i);
      actor
          .instance
          .transform
          .setToTranslation((i % 3 - 1) * 4.1f, (i / 3) * -3.9f + 1.4f, 0)
          .scale(.018f, .018f, .018f);
      actor.instance.getRenderables(candidates, pool);
    }
  }

  private Pixmap image(boolean optimized, String name) {
    for (Renderable r : candidates) r.shader = null;
    profiler.reset();
    calls.reset();
    target.begin();
    try {
      Gdx.gl.glViewport(0, 0, 640, 480);
      Gdx.gl.glClearColor(.06f, .08f, .1f, 1);
      Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
      Array<Renderable> fallback =
          optimized ? batch.render(candidates, camera, environment) : candidates;
      ordinary.begin(camera);
      try {
        for (Renderable r : fallback) {
          r.environment = environment;
          ordinary.render(r);
        }
      } finally {
        ordinary.end();
      }
      Pixmap pixels = Pixmap.createFromFrameBuffer(0, 0, 640, 480);
      if (name != null)
        PixmapIO.writePNG(
            Gdx.files.local(OUTPUT + (gl20 ? "gl20-" : "gl33-") + name + ".png"), pixels);
      return pixels;
    } finally {
      target.end();
    }
  }

  private void comparePair(String name) {
    Pixmap a = image(false, name + "-ordinary"), b = image(true, name + "-instanced");
    try {
      compare(a, b, name);
    } finally {
      a.dispose();
      b.dispose();
    }
  }

  private void compare(Pixmap a, Pixmap b, String name) {
    int background = a.getPixel(0, 0), foreground = 0, errors = 0, silhouette = 0, maximum = 0;
    for (int y = 0; y < a.getHeight(); y++)
      for (int x = 0; x < a.getWidth(); x++) {
        int p = a.getPixel(x, y), q = b.getPixel(x, y);
        if (p != background) foreground++;
        boolean edge = false;
        for (int dy = -1; dy <= 1 && !edge; dy++)
          for (int dx = -1; dx <= 1; dx++) {
            int xx = Math.max(0, Math.min(a.getWidth() - 1, x + dx)),
                yy = Math.max(0, Math.min(a.getHeight() - 1, y + dy));
            if ((a.getPixel(xx, yy) == background) != (p == background)
                || (b.getPixel(xx, yy) == background) != (q == background)) {
              edge = true;
              break;
            }
          }
        for (int shift : new int[] {24, 16, 8}) {
          int delta = Math.abs((p >>> shift & 255) - (q >>> shift & 255));
          if (!edge) maximum = Math.max(maximum, delta);
          if (delta > 2) {
            if (edge) silhouette++;
            else errors++;
          }
        }
      }
    System.out.println(
        name
            + ": foreground="
            + foreground
            + " offSilhouetteMaxRGB="
            + maximum
            + " offSilhouetteErrors="
            + errors
            + " silhouetteChannelDifferences="
            + silhouette);
    check(foreground > 500, name + " nonempty control");
    check(
        errors == 0,
        name
            + " RGB <=2/255 off one-pixel silhouette band; max="
            + maximum
            + " channels="
            + errors);
  }

  private void verifyGpuVertices() {
    calls.capture = true;
    try {
      image(true, null).dispose();
    } finally {
      calls.capture = false;
    }
    float[] gpu = calls.captured;
    check(gpu != null, "production shader transform feedback captured");
    int cursor = 0;
    double error = 0;
    Matrix4 inverseCamera = new Matrix4(camera.combined).inv();
    for (Renderable r : candidates) {
      Mesh mesh = r.meshPart.mesh;
      int stride = mesh.getVertexSize() / 4;
      float[] vertices = mesh.getVertices(new float[mesh.getNumVertices() * stride]);
      short[] indices = new short[mesh.getNumIndices()];
      mesh.getIndices(indices);
      Matrix4 inverse = new Matrix4(r.worldTransform).inv().mul(inverseCamera);
      for (int index = r.meshPart.offset; index < r.meshPart.offset + r.meshPart.size; index++) {
        int vertex = indices[index] & 0xffff;
        Vector3 expected = new Vector3();
        for (VertexAttribute a : mesh.getVertexAttributes())
          if (a.usage == VertexAttributes.Usage.BoneWeight) {
            int at = vertex * stride + a.offset / 4;
            expected.mulAdd(
                new Vector3(
                        vertices[vertex * stride],
                        vertices[vertex * stride + 1],
                        vertices[vertex * stride + 2])
                    .mul(r.bones[(int) vertices[at]]),
                vertices[at + 1]);
          }
        float x = gpu[cursor++], y = gpu[cursor++], z = gpu[cursor++], w = gpu[cursor++];
        float[] m = inverse.val;
        float ax = m[0] * x + m[4] * y + m[8] * z + m[12] * w,
            ay = m[1] * x + m[5] * y + m[9] * z + m[13] * w,
            az = m[2] * x + m[6] * y + m[10] * z + m[14] * w;
        error =
            Math.max(
                error,
                Math.max(
                    Math.abs(ax - expected.x),
                    Math.max(Math.abs(ay - expected.y), Math.abs(az - expected.z))));
      }
    }
    check(
        cursor == gpu.length && error <= .01,
        "actual production GPU vertices <=.01 source units, error=" + error);
    System.out.println(
        "production GPU transform-feedback source vertex error="
            + error
            + " vertices="
            + cursor / 4);
  }

  private void verifyNegativeControl() {
    animators.get(1).play("Survey", PlayMode.LOOP).seek(.7);
    collect();
    Pixmap expected = image(false, null);
    Matrix4[] original = candidates.get(1).bones;
    candidates.get(1).bones = candidates.get(0).bones;
    Pixmap wrong = image(true, "negative-wrong-row");
    int changed = 0;
    for (int y = 0; y < 480; y++)
      for (int x = 0; x < 640; x++) if (expected.getPixel(x, y) != wrong.getPixel(x, y)) changed++;
    candidates.get(1).bones = original;
    expected.dispose();
    wrong.dispose();
    System.out.println("wrong-row negative control changed pixels=" + changed);
    check(changed > 50, "wrong-row negative control changes visible pixels: " + changed);
  }

  private void verifyAlphaTest() {
    Pixmap pixels = new Pixmap(2, 2, Pixmap.Format.RGBA8888);
    pixels.setColor(Color.WHITE);
    pixels.fill();
    pixels.setBlending(Pixmap.Blending.None);
    pixels.drawPixel(0, 0, 0);
    Texture texture = new Texture(pixels);
    pixels.dispose();
    java.util.List<Material> originals = new ArrayList<>();
    try {
      for (Renderable r : candidates) {
        originals.add(r.material);
        r.material = new Material(r.material);
        r.material.set(
            TextureAttribute.createDiffuse(texture),
            FloatAttribute.createAlphaTest(.5f),
            new BlendingAttribute(false, GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 1));
      }
      comparePair("alpha-test");
    } finally {
      for (int i = 0; i < candidates.size; i++) candidates.get(i).material = originals.get(i);
      texture.dispose();
    }
  }

  private void verifyFallbacks() {
    Environment fog = new Environment();
    fog.set(new ColorAttribute(ColorAttribute.Fog, Color.GRAY));
    check(
        batch.render(candidates, camera, fog).size == 6
            && batch
                .fallbackReasons()
                .containsKey("UNSUPPORTED_ENVIRONMENT_ATTRIBUTE:" + ColorAttribute.Fog),
        "fog reasoned fallback");
    Environment spot =
        new Environment().add(new SpotLight().set(Color.WHITE, Vector3.Zero, Vector3.Z, 1, 30, 1));
    check(
        batch.render(candidates, camera, spot).size == 6
            && batch
                .fallbackReasons()
                .containsKey("UNSUPPORTED_ENVIRONMENT_ATTRIBUTE:" + SpotLightsAttribute.Type),
        "spot reasoned fallback");
    Material saved = candidates.first().material;
    candidates.first().material = new Material(saved);
    candidates.first().material.set(FloatAttribute.createShininess(8));
    image(true, "unsupported-material").dispose();
    check(batch.stats().fallback() == 1, "unsupported material ordinary fallback");
    candidates.first().material = saved;
    for (Renderable r : candidates) r.shader = null;
    int offset = candidates.first().meshPart.offset;
    candidates.first().meshPart.offset = -1;
    check(
        batch.render(candidates, camera, environment).size == 1
            && batch.fallbackReasons().containsKey("INVALID_MESH_RANGE"),
        "negative range rejected before draw");
    candidates.first().meshPart.offset = offset;
    collect();
    comparePair("fallback-restored");
  }

  private void verifyBudgetAndPages() {
    for (int i = 0; i < 6; i++) animators.get(i).play("Survey", PlayMode.LOOP).seek(i * .1);
    collect();
    SkinnedInstanceBatch saved = batch;
    batch = new SkinnedInstanceBatch(2, 2);
    try {
      comparePair("budget-fallback");
      check(
          batch.stats().instanced() == 2 && batch.stats().fallback() == 4,
          "total palette row overflow explicit fallback");
    } finally {
      batch.dispose();
      batch = saved;
    }
    Array<Renderable> original = new Array<>(candidates);
    candidates.clear();
    for (int i = 0; i < 257; i++) {
      Renderable r = new Renderable().set(original.first());
      r.shader = null;
      r.bones = new Matrix4[24];
      for (int b = 0; b < 24; b++) r.bones[b] = new Matrix4(original.first().bones[b]);
      r.bones[0].val[Matrix4.M03] += i * .001f;
      candidates.add(r);
    }
    image(true, "palette-page-split").dispose();
    check(
        batch.stats().instanced() == 257 && calls.instanced == 2 && batch.stats().groups() == 2,
        "257 unique poses split at deterministic palette page boundary: " + batch.stats());
    check(calls.paletteBytes == 257L * 24 * 64, "257 complete distinct rows uploaded");
    candidates.clear();
    candidates.addAll(original);
    batch = new SkinnedInstanceBatch(2, 1024);
    try {
      image(true, null).dispose();
      check(
          calls.instanced == 3 && batch.stats().instanced() == 6,
          "instance capacity2 splits six actors into3draws");
    } finally {
      batch.dispose();
      batch = saved;
    }
  }

  private void verifyStateAndFailures() {
    for (Renderable r : candidates) r.shader = null;
    Pixmap pixels = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
    pixels.setColor(Color.MAGENTA);
    pixels.fill();
    Texture zero = new Texture(pixels), one = new Texture(pixels);
    pixels.dispose();
    int active = integer(GL20.GL_ACTIVE_TEXTURE);
    try {
      zero.bind(0);
      one.bind(1);
      Gdx.gl.glActiveTexture(GL20.GL_TEXTURE3);
      batch.render(candidates, camera, environment);
      check(integer(GL20.GL_ACTIVE_TEXTURE) == GL20.GL_TEXTURE3, "caller active texture restored");
      Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
      check(
          integer(GL20.GL_TEXTURE_BINDING_2D) == zero.getTextureObjectHandle(),
          "diffuse touched binding restored");
      Gdx.gl.glActiveTexture(GL20.GL_TEXTURE1);
      check(
          integer(GL20.GL_TEXTURE_BINDING_2D) == one.getTextureObjectHandle(),
          "palette touched binding restored");
      Gdx.gl.glActiveTexture(GL20.GL_TEXTURE3);
      batch.invalidateGeometry(candidates.first().meshPart.mesh);
      calls.failDynamic = true;
      check(
          batch.render(candidates, camera, environment).size == 6,
          "allocation failure returns whole undrawn group");
      check(
          integer(GL20.GL_ACTIVE_TEXTURE) == GL20.GL_TEXTURE3,
          "active unit restored after preparation failure");
      batch.render(candidates, camera, environment);
      float original = candidates.first().bones[0].val[Matrix4.M03];
      candidates.first().bones[0].val[Matrix4.M03] += .001f;
      calls.failPalette = true;
      check(
          batch.render(candidates, camera, environment).size == 6,
          "palette upload failure returns whole undrawn group");
      candidates.first().bones[0].val[Matrix4.M03] = original;
      Gdx.gl.glActiveTexture(GL20.GL_TEXTURE1);
      check(
          integer(GL20.GL_TEXTURE_BINDING_2D) == one.getTextureObjectHandle(),
          "palette binding restored on upload failure");
      comparePair("after-upload-failure");
    } finally {
      zero.dispose();
      one.dispose();
      Gdx.gl.glActiveTexture(active);
    }
  }

  private static int integer(int name) {
    IntBuffer value = BufferUtils.newIntBuffer(1);
    Gdx.gl.glGetIntegerv(name, value);
    return value.get(0);
  }

  private void verifyLimits() {
    for (Renderable r : candidates) r.shader = null;
    int[][] limits = {
      {GL20.GL_MAX_VERTEX_ATTRIBS, 11},
      {GL20.GL_MAX_TEXTURE_SIZE, 32},
      {GL20.GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS, 0},
      {GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS, 1}
    };
    String[] reasons = {
      "VERTEX_ATTRIBUTE_LIMIT",
      "PALETTE_TEXTURE_SIZE",
      "PALETTE_VERTEX_TEXTURE_UNITS",
      "PALETTE_COMBINED_TEXTURE_UNITS"
    };
    for (int i = 0; i < limits.length; i++) {
      calls.limitName = limits[i][0];
      calls.limitValue = limits[i][1];
      SkinnedInstanceBatch limited = new SkinnedInstanceBatch(256, 1024);
      try {
        check(
            limited.render(candidates, camera, environment).size == 6
                && limited.fallbackReasons().containsKey(reasons[i]),
            "capability fallback " + reasons[i] + limited.fallbackReasons());
      } finally {
        limited.dispose();
        calls.limitName = 0;
      }
    }
  }

  private void verifyOracle() {
    for (ModelPoseAnimator animator : animators)
      for (var layer : animator.layers()) layer.stop().setWeight(0);
    JsonValue oracle =
        new JsonReader().parse(Gdx.files.local("tools/animation-validation/fox/oracle.json"));
    double max = 0;
    for (JsonValue sample : oracle.get("samples")) {
      animators.get(0).play(sample.getString("clip"), PlayMode.LOOP).seek(sample.getFloat("time"));
      collect();
      Renderable r = candidates.first();
      Mesh mesh = r.meshPart.mesh;
      int stride = mesh.getVertexSize() / 4;
      float[] vertices = mesh.getVertices(new float[mesh.getNumVertices() * stride]);
      float[] expected = sample.get("vertices").asFloatArray();
      for (int i = 0; i < mesh.getNumVertices(); i++) {
        Vector3 actual = new Vector3();
        for (VertexAttribute a : mesh.getVertexAttributes())
          if (a.usage == VertexAttributes.Usage.BoneWeight) {
            int at = i * stride + a.offset / 4;
            float weight = vertices[at + 1];
            if (weight != 0)
              actual.mulAdd(
                  new Vector3(
                          vertices[i * stride], vertices[i * stride + 1], vertices[i * stride + 2])
                      .mul(r.bones[(int) vertices[at]]),
                  weight);
          }
        max =
            Math.max(
                max,
                Math.max(
                    Math.abs(actual.x - expected[i * 3]),
                    Math.max(
                        Math.abs(actual.y - expected[i * 3 + 1]),
                        Math.abs(actual.z - expected[i * 3 + 2]))));
      }
    }
    check(max <= .01, "original glTF oracle CPU palette vertex error <=.01: " + max);
    System.out.println("original glTF oracle max vertex error source units=" + max);
  }

  private void verifyAvatar() {
    for (boolean cached : new boolean[] {false, true}) {
      try (GeneratedAvatarModel fixture = GeneratedAvatarModel.create();
          Game world = new Game()) {
        if (cached) world.addSystem(new PoseBatchSystem(16));
        GameObject object = world.createObject("prepared local avatar");
        Transform3D transform = object.addComponent(new Transform3D());
        ModelRenderer model = object.addComponent(new ModelRenderer(fixture.model));
        ModelPoseAnimator animator = object.addComponent(new ModelPoseAnimator());
        InstancedModel marker = object.addComponent(new InstancedModel());
        org.javity.presentation.Presentation3D presentation =
            object.addComponent(new org.javity.presentation.Presentation3D());
        GameObject second = world.createObject("second canonical actor");
        second.addComponent(new Transform3D()).position.x = 1.4f;
        second.addComponent(new ModelRenderer(fixture.model));
        ModelPoseAnimator other = second.addComponent(new ModelPoseAnimator());
        InstancedModel secondMarker = second.addComponent(new InstancedModel());
        Camera3D camera3 = world.createObject("camera").addComponent(new Camera3D(50));
        camera3.camera.position.set(.6f, 0, 4);
        camera3.camera.lookAt(.6f, 0, 0);
        camera3.camera.near = .1f;
        camera3.camera.far = 30;
        GdxRenderer renderer = new GdxRenderer(2, 2);
        renderer.resize(640, 480);
        try (PreparedAvatarGeometry prepared =
                AvatarMeshPreparator.prepare(
                    fixture.model,
                    new PartKey("root", 0),
                    Map.of("body", new int[] {0, 1}, "arms", new int[] {2, 3}));
            AvatarBinding binding =
                AvatarDefinition.builder()
                    .region(AvatarPart.TORSO, "body")
                    .region(AvatarPart.LEFT_ARM, "arms")
                    .socket("hand", "root/left-arm", new Matrix4().setToTranslation(.1f, 0, 0))
                    .build()
                    .bind(model, prepared)) {
          world.start();
          world.step(0);
          var presentationChannel = presentation.openChannel();
          presentationChannel.setX(.12f);
          animator.play("arm-sweep", PlayMode.LOOP).seek(.2);
          other.play("arm-sweep", PlayMode.LOOP).seek(.2);
          long before = animator.fullPoseCompositions() + other.fullPoseCompositions();
          world.step(.1f);
          long actual = animator.fullPoseCompositions() + other.fullPoseCompositions() - before;
          check(
              actual == (cached ? 1 : 2),
              "actual full compositions cached=" + cached + " count=" + actual);
          AvatarView view = binding.view();
          Matrix4 socket = binding.socketTransform("hand", transform.worldMatrix(), new Matrix4());
          float[] canonical = model.instance.nodes.first().globalTransform.val.clone();
          double time = animator.current().time();
          for (AvatarPreset preset :
              new AvatarPreset[] {
                AvatarPreset.FULL_BODY, AvatarPreset.ARMS_ONLY, AvatarPreset.BODY_WITHOUT_HEAD
              }) {
            view.preset(preset);
            marker.setEnabled(false);
            secondMarker.setEnabled(false);
            Pixmap a = avatarImage(renderer, world, camera3, view, false);
            int ordinaryDraws = profiler.getDrawCalls();
            marker.setEnabled(true);
            secondMarker.setEnabled(true);
            Pixmap b = avatarImage(renderer, world, camera3, view, false);
            try {
              compare(a, b, "prepared-world-" + preset + "-cache-" + cached);
              if (!cached)
                PixmapIO.writePNG(
                    Gdx.files.local(OUTPUT + "avatar-" + preset + "-instanced.png"), b);
            } finally {
              a.dispose();
              b.dispose();
            }
            int expected = preset == AvatarPreset.ARMS_ONLY ? 2 : 3;
            check(
                renderer.instanceStats().instanced() == expected
                    && renderer.instanceStats().fallback() == 0,
                "WORLD selected prepared regions opt in: "
                    + renderer.instanceStats()
                    + renderer.instanceFallbackReasons());
            check(calls.instanced == expected, "prepared regions have separate actual draws");
            check(ordinaryDraws == expected, "ordinary region baseline draw count");
            Pixmap vm = avatarImage(renderer, world, camera3, view, true);
            vm.dispose();
            check(
                calls.instanced == 0 && profiler.getDrawCalls() == expected - 1,
                "viewmodel remains ordinary");
          }
          check(
              Arrays.equals(canonical, model.instance.nodes.first().globalTransform.val),
              "canonical nodes unchanged by both passes");
          check(
              Arrays.equals(
                  socket.val,
                  binding.socketTransform("hand", transform.worldMatrix(), new Matrix4()).val),
              "canonical socket unchanged");
          check(animator.current().time() == time, "avatar renders never advance clocks");
          view.parts(Set.of());
          avatarImage(renderer, world, camera3, view, false).dispose();
          check(
              renderer.instanceStats().instanced() == 1,
              "hidden regions pruned while second actor remains");
          view.preset(AvatarPreset.FULL_BODY);
          avatarImage(renderer, world, camera3, view, false).dispose();
          check(
              renderer.instanceStats().instanced() == 3, "prepared regions reenter after pruning");
        } finally {
          renderer.dispose();
        }
      }
    }
  }

  private void verifySceneBoundary() {
    GdxRenderer renderer = new GdxRenderer(24, 4);
    renderer.resize(640, 480);
    Model rigid =
        new com.badlogic.gdx.graphics.g3d.utils.ModelBuilder()
            .createBox(
                .2f,
                .2f,
                .2f,
                new Material(ColorAttribute.createDiffuse(Color.GREEN)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
    try {
      for (int pass = 0; pass < 2; pass++) {
        Set<Integer> before = new HashSet<>(calls.dynamicBuffers);
        Game world = new Game();
        GameObject object = world.createObject("frame-zero actor");
        object.addComponent(new Transform3D()).scale.set(.02f, .02f, .02f);
        object.addComponent(new ModelRenderer(fox));
        object.addComponent(new InstancedModel());
        ModelPoseAnimator animator = object.addComponent(new ModelPoseAnimator());
        GameObject staticObject = world.createObject("frame-zero rigid");
        staticObject.addComponent(new Transform3D());
        staticObject.addComponent(new ModelRenderer(rigid));
        staticObject.addComponent(new InstancedModel());
        Camera3D cam = world.createObject("camera").addComponent(new Camera3D(50));
        cam.camera.position.set(0, 1, 5);
        cam.camera.lookAt(0, 1, 0);
        cam.camera.near = .1f;
        cam.camera.far = 30;
        world.start();
        animator.play(pass == 0 ? "Walk" : "Run", PlayMode.LOOP).seek(.25);
        check(world.getFrameIndex() == 0, "scene starts at frame zero");
        calls.reset();
        renderer.render(world);
        check(
            renderer.instanceStats().instanced() == 2 && calls.paletteBytes > 0,
            "new frame-zero scene uploads fresh skin rows");
        int palette = calls.lastPalette;
        Set<Integer> owned = new HashSet<>(calls.dynamicBuffers);
        owned.removeAll(before);
        check(!owned.isEmpty(), "scene allocates owned instance buffers");
        world.close();
        try (Game menu = new Game()) {
          menu.start();
          renderer.render(menu);
          check(
              renderer.instanceStats().submitted() == 0,
              "camera-less new scene clears previous stats");
          check(!Gdx.gl.glIsTexture(palette), "camera-less scene releases previous palette");
          for (int buffer : owned)
            check(!Gdx.gl.glIsBuffer(buffer), "camera-less scene releases instance buffer");
          for (String field : new String[] {"instanceOwnerIds", "avatarInstanceIds"}) {
            try {
              var f = GdxRenderer.class.getDeclaredField(field);
              f.setAccessible(true);
              check(
                  ((Map<?, ?>) f.get(renderer)).isEmpty(),
                  "scene releases stable owner map " + field);
            } catch (ReflectiveOperationException e) {
              throw new AssertionError(e);
            }
          }
        }
      }
    } finally {
      renderer.dispose();
      rigid.dispose();
    }
  }

  private Pixmap avatarImage(
      GdxRenderer renderer, Game world, Camera3D camera3, AvatarView view, boolean viewmodel) {
    profiler.reset();
    calls.reset();
    target.begin();
    try {
      Gdx.gl.glViewport(0, 0, 640, 480);
      Gdx.gl.glClearColor(.06f, .08f, .1f, 1);
      Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
      if (viewmodel) renderer.renderViewmodel(camera3.camera, view);
      else renderer.renderWorld(world, camera3, view);
      return Pixmap.createFromFrameBuffer(0, 0, 640, 480);
    } finally {
      target.end();
    }
  }

  private void check(boolean value, String message) {
    assertions++;
    if (!value) throw new AssertionError(message);
  }

  @Override
  public void dispose() {
    if (batch != null) {
      batch.dispose();
      batch = null;
    }
    if (game != null) {
      game.close();
      game = null;
    }
    if (ordinary != null) {
      ordinary.dispose();
      ordinary = null;
    }
    if (target != null) {
      target.dispose();
      target = null;
    }
    if (fox != null) {
      fox.dispose();
      fox = null;
    }
    if (calls != null) {
      check(
          calls.liveBuffers.isEmpty(),
          "all tracked owned GPU buffers deleted: " + calls.liveBuffers);
      check(calls.livePrograms.isEmpty(), "all tracked programs deleted: " + calls.livePrograms);
      check(calls.liveShaders.isEmpty(), "all tracked shader stages deleted: " + calls.liveShaders);
      calls.restore();
      calls = null;
    }
    if (profiler != null) {
      profiler.disable();
      profiler = null;
    }
  }

  private static final class Calls implements InvocationHandler {
    GL20 previous20, previous;
    GL30 previous30;
    long instanceBytes, paletteBytes;
    int instanced;
    boolean capture, failDynamic, failPalette;
    int limitName, limitValue;
    float[] captured;
    final Set<Integer> paletteShaders = new HashSet<>(), palettePrograms = new HashSet<>();
    final Set<Integer> paletteTextures = new HashSet<>();
    int boundTexture, boundArray, lastPalette;
    final Set<Integer> dynamicBuffers = new HashSet<>();
    final Set<Integer> liveBuffers = new HashSet<>(),
        livePrograms = new HashSet<>(),
        liveShaders = new HashSet<>();

    void reset() {
      instanceBytes = paletteBytes = 0;
      instanced = 0;
    }

    void install() {
      previous = Gdx.gl;
      previous20 = Gdx.gl20;
      previous30 = Gdx.gl30;
      if (previous30 != null) {
        GL30 proxy =
            (GL30)
                Proxy.newProxyInstance(
                    GL30.class.getClassLoader(), new Class<?>[] {GL30.class}, this);
        Gdx.gl = proxy;
        Gdx.gl20 = proxy;
        Gdx.gl30 = proxy;
      }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      try {
        String name = method.getName();
        if (name.equals("glGetIntegerv") && (int) args[0] == limitName && limitName != 0) {
          ((IntBuffer) args[1]).put(0, limitValue);
          return null;
        }
        if (name.equals("glBufferData") && (int) args[3] == GL20.GL_DYNAMIC_DRAW && failDynamic) {
          failDynamic = false;
          throw new IllegalStateException("injected instance allocation failure");
        }
        if (name.equals("glTexSubImage2D") && (int) args[7] == GL20.GL_FLOAT && failPalette) {
          failPalette = false;
          throw new IllegalStateException("injected palette upload failure");
        }
        if (name.equals("glShaderSource")
            && ((String) args[1]).contains("uniform highp sampler2D u_palette"))
          paletteShaders.add((int) args[0]);
        if (name.equals("glAttachShader") && paletteShaders.contains((int) args[1]))
          palettePrograms.add((int) args[0]);
        if (name.equals("glLinkProgram") && palettePrograms.contains((int) args[0]))
          previous30.glTransformFeedbackVaryings(
              (int) args[0], new String[] {"gl_Position"}, GL30.GL_INTERLEAVED_ATTRIBS);
        boolean feedback = capture && name.equals("glDrawElementsInstanced");
        int feedbackBuffer = 0, floats = 0;
        if (feedback) {
          floats = (int) args[1] * (int) args[4] * 4;
          feedbackBuffer = previous30.glGenBuffer();
          previous30.glBindBuffer(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, feedbackBuffer);
          previous30.glBufferData(
              GL30.GL_TRANSFORM_FEEDBACK_BUFFER, floats * 4, null, GL30.GL_STREAM_READ);
          previous30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, feedbackBuffer);
          previous30.glBeginTransformFeedback(GL20.GL_TRIANGLES);
        }
        Object result;
        try {
          result = method.invoke(previous30, args);
        } finally {
          if (feedback) {
            previous30.glEndTransformFeedback();
            captured = new float[floats];
            org.lwjgl.opengl.GL15.glGetBufferSubData(
                GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, captured);
            previous30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, 0);
            previous30.glBindBuffer(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0);
            previous30.glDeleteBuffer(feedbackBuffer);
          }
        }
        switch (method.getName()) {
          case "glDrawElementsInstanced", "glDrawArraysInstanced" -> instanced++;
          case "glBindBuffer" -> {
            if ((int) args[0] == GL20.GL_ARRAY_BUFFER) boundArray = (int) args[1];
          }
          case "glBufferData" -> {
            if ((int) args[0] == GL20.GL_ARRAY_BUFFER && (int) args[3] == GL20.GL_DYNAMIC_DRAW)
              dynamicBuffers.add(boundArray);
          }
          case "glGenBuffer" -> liveBuffers.add((int) result);
          case "glDeleteBuffer" -> {
            dynamicBuffers.remove((int) args[0]);
            liveBuffers.remove((int) args[0]);
          }
          case "glCreateProgram" -> livePrograms.add((int) result);
          case "glDeleteProgram" -> {
            livePrograms.remove((int) args[0]);
            palettePrograms.remove((int) args[0]);
          }
          case "glCreateShader" -> liveShaders.add((int) result);
          case "glDeleteShader" -> {
            liveShaders.remove((int) args[0]);
            paletteShaders.remove((int) args[0]);
          }
          case "glBufferSubData" -> {
            if ((int) args[0] == GL20.GL_ARRAY_BUFFER && dynamicBuffers.contains(boundArray))
              instanceBytes += ((Number) args[2]).longValue();
          }
          case "glBindTexture" -> boundTexture = ((Number) args[1]).intValue();
          case "glTexImage2D" -> {
            if (((Number) args[2]).intValue() == GL30.GL_RGBA32F) {
              paletteTextures.add(boundTexture);
              lastPalette = boundTexture;
            }
          }
          case "glTexSubImage2D" -> {
            if (((Number) args[7]).intValue() == GL20.GL_FLOAT)
              paletteBytes += ((Number) args[4]).longValue() * ((Number) args[5]).longValue() * 16;
          }
        }
        return result;
      } catch (InvocationTargetException e) {
        throw e.getCause();
      }
    }

    void restore() {
      Gdx.gl = previous;
      Gdx.gl20 = previous20;
      Gdx.gl30 = previous30;
    }
  }
}
