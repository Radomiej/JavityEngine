package org.javity.demo;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader;
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader;
import com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.utils.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import org.javity.animation.*;
import org.javity.core.*;
import org.javity.core.Game;
import org.javity.render.*;

/** Native verification workload. Timed sections contain no readback or screenshot operations. */
public final class InstancingBenchmark extends ApplicationAdapter {
  private static final String OUT = "docs/artifacts/instancing/";
  private final Array<Renderable> candidates = new Array<>();
  private final Pool<Renderable> pool =
      new Pool<>() {
        protected Renderable newObject() {
          return new Renderable();
        }
      };
  private final Environment environment = new Environment();
  private final List<Map<String, Object>> results = new ArrayList<>();
  private final Calls calls = new Calls();
  private Model fox;
  private ModelBatch ordinary;
  private FrameBuffer target;
  private OrthographicCamera camera;
  private int assertions;
  private boolean gpuTimers;

  public void create() {
    try {
      String startedUtc = java.time.Instant.now().toString();
      gpuTimers =
          org.lwjgl.opengl.GL.getCapabilities().OpenGL33
              || org.lwjgl.opengl.GL.getCapabilities().GL_ARB_timer_query;
      fox =
          new G3dModelLoader(new JsonReader())
              .loadModel(Gdx.files.local("tools/animation-validation/fox/Fox.g3dj"));
      var config = new DefaultShader.Config();
      config.numBones = 24;
      config.numBoneWeights = 4;
      ordinary = new ModelBatch(new DefaultShaderProvider(config));
      target = new FrameBuffer(Pixmap.Format.RGBA8888, 640, 480, true);
      camera = new OrthographicCamera(106, 79.5f);
      camera.position.set(0, 30, 90);
      camera.lookAt(0, 0, 0);
      camera.near = .1f;
      camera.far = 200;
      camera.update();
      environment.set(ColorAttribute.createAmbientLight(.9f, .9f, .9f, 1));
      calls.install();
      for (int count : new int[] {64, 256, 1024})
        for (boolean independent : new boolean[] {false, true}) {
          Map<String, Object> baseline = run(count, independent, false, false);
          Map<String, Object> optimized = run(count, independent, true, false);
          check(
              ((Number) baseline.get("actualDraws")).longValue() == 300L * count,
              "ordinary native draws");
          check(
              ((Number) optimized.get("actualDraws")).longValue() == 300L * ((count + 255) / 256),
              "instanced native draws");
          check(
              baseline.get("events").equals(optimized.get("events")),
              "independent event callbacks unchanged");
        }
      // Optional CPU service measured separately, on exactly the same256 synchronous actors.
      Map<String, Object> cached = run(256, false, true, true);
      Map<String, Object> uncached =
          results.stream()
              .filter(
                  r ->
                      r.get("count").equals(256)
                          && r.get("mode").equals("instanced")
                          && r.get("phase").equals("synchronous")
                          && r.get("cpuPoseCache").equals(false))
              .findFirst()
              .orElseThrow();
      check(
          ((Number) cached.get("fullPoseCompositions")).longValue()
              < ((Number) uncached.get("fullPoseCompositions")).longValue(),
          "shared CPU poses reduce actual full compositions");
      check(
          cached.get("events").equals(uncached.get("events")),
          "cached clocks retain independent callbacks");
      Map<String, Object> shared =
          results.stream()
              .filter(
                  r ->
                      r.get("count").equals(256)
                          && r.get("mode").equals("instanced")
                          && r.get("phase").equals("synchronous")
                          && !((Boolean) r.get("cpuPoseCache")))
              .findFirst()
              .orElseThrow();
      Map<String, Object> independent =
          results.stream()
              .filter(
                  r ->
                      r.get("count").equals(256)
                          && r.get("mode").equals("instanced")
                          && r.get("phase").equals("independent"))
              .findFirst()
              .orElseThrow();
      check(
          ((Number) shared.get("paletteUploadBytes")).longValue()
              < ((Number) independent.get("paletteUploadBytes")).longValue(),
          "shared GPU rows reduce actual uploads");
      showcase();
      Map<String, Object> metadata = new LinkedHashMap<>();
      metadata.put("schema", 1);
      metadata.put("startedUtc", startedUtc);
      metadata.put("completedUtc", java.time.Instant.now().toString());
      metadata.put("seed", 42);
      metadata.put("warmupFrames", 120);
      metadata.put("measuredFrames", 300);
      metadata.put("deltaSeconds", 1f / 60);
      metadata.put("viewport", List.of(640, 480));
      metadata.put(
          "screenshotOrientation",
          "numeric screenshots retain raw framebuffer bottom-left orientation; separate perspective"
              + " showcase PNGs explicitly flipY=true for upright display");
      metadata.put("camera", List.of(106, 79.5, 0, 30, 90, 0, 0, 0, .1, 200));
      metadata.put("glVersion", Gdx.gl.glGetString(GL20.GL_VERSION));
      metadata.put("glRenderer", Gdx.gl.glGetString(GL20.GL_RENDERER));
      metadata.put("glVendor", Gdx.gl.glGetString(GL20.GL_VENDOR));
      metadata.put("fixtureSha256", hash(Path.of("tools/animation-validation/fox/Fox.g3dj")));
      Path loadedCore =
          Path.of(Game.class.getProtectionDomain().getCodeSource().getLocation().toURI());
      metadata.put("coreCodeSource", loadedCore.toString());
      metadata.put("coreJarSha256", hash(loadedCore));
      metadata.put(
          "workloadCodeSource",
          InstancingBenchmark.class.getProtectionDomain().getCodeSource().getLocation().toString());
      try (var stream =
          InstancingBenchmark.class.getResourceAsStream("InstancingBenchmark.class")) {
        metadata.put(
            "workloadClassSha256",
            HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes())));
      }
      Map<String, String> loadedJars = new LinkedHashMap<>();
      for (Class<?> type :
          List.of(
              Game.class,
              com.badlogic.gdx.Gdx.class,
              com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application.class)) {
        Path file = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (Files.isRegularFile(file)) loadedJars.put(file.toString(), hash(file));
      }
      metadata.put("loadedJarSha256", loadedJars);
      metadata.put("jvm", System.getProperty("java.runtime.version"));
      Path cpuQuota = Path.of("/sys/fs/cgroup/cpu.max");
      metadata.put(
          "cpuQuota",
          Files.isReadable(cpuQuota) ? Files.readString(cpuQuota).trim() : "not exposed");
      metadata.put(
          "environment",
          System.getProperty("os.name")
              + " "
              + System.getProperty("os.arch")
              + "; "
              + System.getProperty(
                  "javity.benchmark.environment", "unspecified co-resident workloads"));
      metadata.put("gpuTimerSupported", gpuTimers);
      metadata.put(
          "gpuTimerStatus",
          gpuTimers
              ? "actual GL_TIME_ELAPSED queries, results collected after glFinish outside timed CPU"
                  + " sections"
              : "driver exposes neither OpenGL33 nor GL_ARB_timer_query");
      metadata.put(
          "timingScope",
          "CPU wall time: step and render submission; driver waits included. Collection and counter"
              + " observation excluded. Drain before each warmup. No measured readback.");
      metadata.put(
          "uploadScope",
          "actual glBufferSubData instance bytes + glTexSubImage2D RGBA32F palette bytes; excludes"
              + " immutable geometry/allocation");
      metadata.put("results", results);
      metadata.put("assertions", assertions);
      metadata.put("status", "PASS");
      Gdx.files
          .local(OUT + "benchmark.json")
          .writeString(jsonValue(metadata).prettyPrint(JsonWriter.OutputType.json, 120), false);
      System.out.println("instancingBenchmark: PASS assertions=" + assertions);
    } catch (Throwable e) {
      e.printStackTrace();
      System.exit(1);
    } finally {
      dispose();
      Gdx.app.exit();
    }
  }

  private Map<String, Object> run(
      int count, boolean independent, boolean optimized, boolean cached) {
    Gdx.gl.glFinish(); // isolate each run from the preceding workload
    try (Game game = new Game()) {
      if (cached) game.addSystem(new PoseBatchSystem(1024));
      List<ModelRenderer> models = new ArrayList<>();
      List<ModelPoseAnimator> poses = new ArrayList<>();
      int[] events = new int[count];
      Random random = new Random(42);
      double[] offsets = new double[count];
      for (int i = 0; i < count; i++) {
        GameObject o = game.createObject("crowd " + i);
        o.addComponent(new Transform3D());
        models.add(o.addComponent(new ModelRenderer(fox)));
        poses.add(o.addComponent(new ModelPoseAnimator()));
        offsets[i] = independent ? random.nextDouble() * .99 : 0;
      }
      game.start();
      game.step(0);
      for (int i = 0; i < count; i++) {
        final int owner = i;
        poses
            .get(i)
            .play("Walk", PlayMode.LOOP)
            .seek(offsets[i] * fox.getAnimation("Walk").duration)
            .onLoop(() -> events[owner]++)
            .onMarker("foot", .5, () -> events[owner]++);
      }
      try (SkinOwner skin = new SkinOwner()) {
        long stepNs = 0,
            renderNs = 0,
            draws = 0,
            palette = 0,
            instance = 0,
            updates = 0,
            compositions = 0;
        int[] queries = new int[300];
        if (gpuTimers) org.lwjgl.opengl.GL33C.glGenQueries(queries);
        for (int frame = 0; frame < 420; frame++) {
          if (frame == 120) Arrays.fill(events, 0);
          long before = full(poses), t = System.nanoTime();
          game.step(1f / 60);
          long afterStep = System.nanoTime();
          collect(models);
          calls.reset();
          if (gpuTimers && frame >= 120)
            org.lwjgl.opengl.GL33C.glBeginQuery(
                org.lwjgl.opengl.GL33C.GL_TIME_ELAPSED, queries[frame - 120]);
          long startRender = System.nanoTime();
          target.begin();
          Gdx.gl.glViewport(0, 0, 640, 480);
          Gdx.gl.glClearColor(.06f, .08f, .1f, 1);
          Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
          Array<Renderable> fallback =
              optimized ? skin.batch.render(candidates, camera, environment) : candidates;
          ordinary.begin(camera);
          for (Renderable r : fallback) {
            r.environment = environment;
            ordinary.render(r);
          }
          ordinary.end();
          target.end();
          long endRender = System.nanoTime();
          if (gpuTimers && frame >= 120)
            org.lwjgl.opengl.GL33C.glEndQuery(org.lwjgl.opengl.GL33C.GL_TIME_ELAPSED);
          if (optimized)
            check(
                skin.batch.stats().instanced() == count
                    && skin.batch.stats().culled() == 0
                    && fallback.isEmpty(),
                "all actors visible and supported");
          if (frame >= 120) {
            stepNs += afterStep - t;
            renderNs += endRender - startRender;
            draws += calls.draws;
            palette += calls.palette;
            instance += calls.instance;
            updates += calls.updates;
            compositions += full(poses) - before;
          }
        }
        Gdx.gl.glFinish();
        long gpuNs = 0;
        if (gpuTimers) {
          for (int query : queries)
            gpuNs +=
                org.lwjgl.opengl.GL33C.glGetQueryObjectui64(
                    query, org.lwjgl.opengl.GL33C.GL_QUERY_RESULT);
          org.lwjgl.opengl.GL33C.glDeleteQueries(queries);
        }
        Set<String> unique = new HashSet<>();
        for (Renderable r : candidates) {
          StringBuilder key = new StringBuilder();
          for (Matrix4 m : r.bones)
            for (float f : m.val) key.append(Float.floatToIntBits(f)).append(',');
          unique.add(key.toString());
        }
        if (optimized)
          check(unique.size() == (independent ? count : 1), "actual matrix palette diversity");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", count);
        result.put("phase", independent ? "independent" : "synchronous");
        result.put("mode", optimized ? "instanced" : "ordinary");
        result.put("cpuPoseCache", cached);
        result.put("gpuElapsedMsPerFrame", gpuTimers ? gpuNs / 300e6 : null);
        result.put("eventScope", "300 measured frames only");
        result.put("actualDraws", draws);
        result.put("fullPoseCompositions", compositions);
        result.put("uniquePalettesFinalFrame", unique.size());
        result.put("paletteUploadBytes", palette);
        result.put("instanceUploadBytes", instance);
        result.put("paletteUpdates", updates);
        result.put("stepMsPerFrame", stepNs / 300e6);
        result.put("renderSubmissionMsPerFrame", renderNs / 300e6);
        result.put("events", Arrays.stream(events).boxed().toList());
        results.add(result);
        System.out.println(
            "benchmark "
                + count
                + " "
                + result.get("phase")
                + " "
                + result.get("mode")
                + " cache="
                + cached
                + " step="
                + result.get("stepMsPerFrame")
                + " render="
                + result.get("renderSubmissionMsPerFrame")
                + " draws="
                + draws
                + " compositions="
                + compositions
                + " palette="
                + palette);
        if (count == 64 && !cached) screenshot(result.get("phase") + "-" + result.get("mode"));
        return result;
      }
    }
  }

  private void showcase() {
    pool.freeAll(candidates);
    candidates.clear();
    String[] clips = {"Walk", "Run", "Survey"};
    for (int i = 0; i < 6; i++) {
      ModelInstance m = new ModelInstance(fox);
      var animation = new com.badlogic.gdx.graphics.g3d.utils.AnimationController(m);
      animation.setAnimation(clips[i % 3], -1);
      animation.update(.2f + i * .05f);
      m.transform
          .setToTranslation((i % 3 - 1) * 3.6f, 0, (i / 3) * -4f)
          .rotate(Vector3.Y, -25)
          .scale(.018f, .018f, .018f);
      m.getRenderables(candidates, pool);
    }
    PerspectiveCamera view = new PerspectiveCamera(45, 1280, 960);
    view.position.set(6, 4, 10);
    view.lookAt(0, 1, -1);
    view.near = .1f;
    view.far = 100;
    view.update();
    FrameBuffer large = new FrameBuffer(Pixmap.Format.RGBA8888, 1280, 960, true);
    Pixmap pair = new Pixmap(2560, 960, Pixmap.Format.RGBA8888);
    pair.setBlending(Pixmap.Blending.None);
    try (SkinOwner skin = new SkinOwner()) {
      for (int mode = 0; mode < 2; mode++) {
        for (Renderable r : candidates) r.shader = null;
        large.begin();
        Gdx.gl.glViewport(0, 0, 1280, 960);
        Gdx.gl.glClearColor(.06f, .08f, .1f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Array<Renderable> fallback =
            mode == 0 ? candidates : skin.batch.render(candidates, view, environment);
        ordinary.begin(view);
        for (Renderable r : fallback) {
          r.environment = environment;
          ordinary.render(r);
        }
        ordinary.end();
        Pixmap p = Pixmap.createFromFrameBuffer(0, 0, 1280, 960);
        large.end();
        PixmapIO.writePNG(
            Gdx.files.local(OUT + "showcase-" + (mode == 0 ? "ordinary" : "instanced") + ".png"),
            p,
            -1,
            true);
        pair.drawPixmap(p, mode * 1280, 0);
        p.dispose();
      }
      PixmapIO.writePNG(Gdx.files.local(OUT + "showcase-comparison.png"), pair, -1, true);
    } finally {
      pair.dispose();
      large.dispose();
    }
  }

  private void collect(List<ModelRenderer> models) {
    pool.freeAll(candidates);
    candidates.clear();
    for (int i = 0; i < models.size(); i++) {
      ModelInstance m = models.get(i).instance;
      m.transform
          .setToTranslation((i % 32 - 15.5f) * 3, (i / 32 - 15.5f) * 2.1f, 0)
          .scale(.012f, .012f, .012f);
      m.getRenderables(candidates, pool);
    }
    for (Renderable r : candidates) r.shader = null;
  }

  private void screenshot(String name) {
    target.begin();
    Pixmap p = Pixmap.createFromFrameBuffer(0, 0, 640, 480);
    target.end();
    PixmapIO.writePNG(Gdx.files.local(OUT + "crowd-" + name + ".png"), p);
    p.dispose();
  }

  private static long full(List<ModelPoseAnimator> poses) {
    return poses.stream().mapToLong(ModelPoseAnimator::fullPoseCompositions).sum();
  }

  private void check(boolean value, String message) {
    assertions++;
    if (!value) throw new AssertionError(message);
  }

  private static JsonValue jsonValue(Object value) {
    if (value == null) return new JsonValue(JsonValue.ValueType.nullValue);
    if (value instanceof Map<?, ?> map) {
      JsonValue object = new JsonValue(JsonValue.ValueType.object);
      for (var entry : map.entrySet())
        object.addChild(entry.getKey().toString(), jsonValue(entry.getValue()));
      return object;
    }
    if (value instanceof Iterable<?> list) {
      JsonValue array = new JsonValue(JsonValue.ValueType.array);
      for (Object item : list) array.addChild(jsonValue(item));
      return array;
    }
    if (value instanceof Boolean bool) return new JsonValue(bool);
    if (value instanceof Float || value instanceof Double)
      return new JsonValue(((Number) value).doubleValue());
    if (value instanceof Number number) return new JsonValue(number.longValue());
    return new JsonValue(value.toString());
  }

  private static String hash(Path path) throws Exception {
    return HexFormat.of()
        .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
  }

  private static final class SkinOwner implements AutoCloseable {
    final SkinnedInstanceBatch batch = new SkinnedInstanceBatch(256, 1024);

    public void close() {
      batch.dispose();
    }
  }

  public void dispose() {
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
    calls.uninstall();
  }

  private static final class Calls implements InvocationHandler {
    GL20 old, old20;
    GL30 old30;
    long draws, palette, instance, updates;
    int bound;
    Set<Integer> dynamic = new HashSet<>();
    boolean installed;

    void install() {
      old = Gdx.gl;
      old20 = Gdx.gl20;
      old30 = Gdx.gl30;
      GL30 proxy =
          (GL30)
              Proxy.newProxyInstance(GL30.class.getClassLoader(), new Class[] {GL30.class}, this);
      Gdx.gl = proxy;
      Gdx.gl20 = proxy;
      Gdx.gl30 = proxy;
      installed = true;
    }

    void uninstall() {
      if (!installed) return;
      Gdx.gl = old;
      Gdx.gl20 = old20;
      Gdx.gl30 = old30;
      installed = false;
    }

    void reset() {
      draws = palette = instance = updates = 0;
    }

    public Object invoke(Object p, Method m, Object[] a) throws Throwable {
      Object result;
      try {
        result = m.invoke(old30, a);
      } catch (InvocationTargetException e) {
        throw e.getCause();
      }
      String n = m.getName();
      if (n.startsWith("glDraw") && (n.contains("Elements") || n.contains("Arrays"))) draws++;
      if (n.equals("glBindBuffer") && (Integer) a[0] == GL20.GL_ARRAY_BUFFER)
        bound = (Integer) a[1];
      if (n.equals("glBufferData")
          && (Integer) a[0] == GL20.GL_ARRAY_BUFFER
          && (Integer) a[3] == GL20.GL_DYNAMIC_DRAW) dynamic.add(bound);
      if (n.equals("glBufferSubData")
          && (Integer) a[0] == GL20.GL_ARRAY_BUFFER
          && dynamic.contains(bound)) instance += ((Number) a[2]).longValue();
      if (n.equals("glDeleteBuffer")) dynamic.remove((Integer) a[0]);
      if (n.equals("glTexSubImage2D") && (Integer) a[7] == GL20.GL_FLOAT) {
        palette += (long) (Integer) a[4] * (Integer) a[5] * 16;
        updates++;
      }
      return result;
    }
  }
}
