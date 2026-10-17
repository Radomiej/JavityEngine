import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader;
import com.badlogic.gdx.graphics.g3d.model.*;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.utils.*;
import java.nio.file.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.javity.animation.*;
import org.javity.core.*;
import org.javity.core.Game;
import org.javity.render.*;

/** External-asset validation harness. Uses production Javity animation/rendering unchanged. */
public final class ImportedAnimationProbe extends ApplicationAdapter {
  static String root, output;
  static boolean interactive, gl30, poseCache;
  Game game;
  Model source;
  ModelRenderer model;
  ModelPoseAnimator animator;
  GdxRenderer renderer;
  Camera3D camera;
  SpriteBatch batch;
  BitmapFont font;
  String label = "";
  final Map<String, Object> report = new LinkedHashMap<>();
  final java.util.List<String> checks = new ArrayList<>();
  ModelAnimationLayer tail;
  boolean paused;

  public static void main(String[] args) {
    root = args[0];
    output = args[1];
    interactive = Arrays.asList(args).contains("--interactive");
    gl30 = Arrays.asList(args).contains("--gl30");
    poseCache = Arrays.asList(args).contains("--pose-cache");
    var c = new Lwjgl3ApplicationConfiguration();
    c.setTitle("Javity - imported Fox animation validation");
    c.setWindowedMode(1000, 700);
    c.setForegroundFPS(60);
    c.setInitialVisible(interactive);
    c.setAudioConfig(16, 512, 9);
    c.disableAudio(true);
    c.setOpenGLEmulation(
        gl30
            ? Lwjgl3ApplicationConfiguration.GLEmulation.GL30
            : Lwjgl3ApplicationConfiguration.GLEmulation.GL20,
        gl30 ? 3 : 2,
        gl30 ? 2 : 0);
    new Lwjgl3Application(new ImportedAnimationProbe(), c);
  }

  @Override
  public void create() {
    try {
      source =
          new G3dModelLoader(new JsonReader()).loadModel(Gdx.files.absolute(root + "/Fox.g3dj"));
      renderer = new GdxRenderer(24, 4);
      renderer.resize(1000, 700);
      batch = new SpriteBatch();
      font = new BitmapFont();
      game = new Game();
      if (poseCache) game.addSystem(new PoseBatchSystem(128));
      GameObject hero = game.createObject("Imported Fox - 24 joints");
      hero.addComponent(new Transform3D()).scale.set(.02f, .02f, .02f);
      model = hero.addComponent(new ModelRenderer(source));
      animator = hero.addComponent(new ModelPoseAnimator());
      camera = game.createObject("Camera").addComponent(new Camera3D(42));
      camera.camera.position.set(3, 1.9f, 3.8f);
      camera.camera.lookAt(0, .65f, 0);
      camera.camera.near = .03f;
      camera.camera.far = 50;
      game.start();
      game.step(0);
      tail = animator.addLayer("tail override", NodeMask.subtreeIds("b_Tail01_012"));
      report.put("asset", "Fox / PixelMannen, tomkranis, AsoboStudio, scurest");
      report.put("joints", 24);
      report.put("vertices", 1728);
      require(!gl30 || Gdx.gl30 != null, "requested GL30 available");
      report.put("profile", gl30 ? "GL30" : "GL20");
      report.put("poseCacheEnabled", poseCache);
      Path coreJar =
          Path.of(
              ModelPoseAnimator.class.getProtectionDomain().getCodeSource().getLocation().toURI());
      report.put("loadedCoreJarSha256", sha256(coreJar));
      report.put("loadedCoreJar", coreJar.getFileName().toString());
      report.put("glVersion", Gdx.gl.glGetString(GL20.GL_VERSION));
      validate();
      if (poseCache) validateBatching();
      if (!interactive && !gl30)
        for (String clip : new String[] {"Survey", "Walk", "Run"}) {
          var clipPlayback = animator.play(clip, PlayMode.LOOP);
          for (int i = 0; i < 20; i++) {
            clipPlayback.seek(clipPlayback.duration() * i / 20);
            shot("frames/" + clip + "-" + String.format("%02d", i));
          }
        }
      animator.play("Walk", PlayMode.LOOP);
      label = "Walk | 1 Survey  2 Walk  3 Run  4 Crossfade  5 Mixer  6 Tail layer | Space pause";
      Gdx.input.setInputProcessor(
          new InputAdapter() {
            @Override
            public boolean keyDown(int key) {
              if (key == Input.Keys.SPACE) {
                paused = !paused;
                if (paused) game.pause();
                else game.resume();
                return true;
              }
              if (key == Input.Keys.NUM_1 || key == Input.Keys.NUM_2 || key == Input.Keys.NUM_3) {
                tail.stop().setWeight(0);
                String clip =
                    key == Input.Keys.NUM_1 ? "Survey" : key == Input.Keys.NUM_2 ? "Walk" : "Run";
                animator.play(clip, PlayMode.LOOP);
                label = clip;
                return true;
              }
              if (key == Input.Keys.NUM_4) {
                tail.stop().setWeight(0);
                animator.play(AnimationTransition.clip("Run").loop().fade(.6));
                label = "Crossfade to Run (0.6s)";
                return true;
              }
              if (key == Input.Keys.NUM_5) {
                tail.stop().setWeight(0);
                animator.play(
                    LinearModelMixer.builder(1).add("Walk", 0).add("Run", 1).build(),
                    PlayMode.LOOP,
                    .5);
                label = "Phase synchronous Walk / Run mixer 50%";
                return true;
              }
              if (key == Input.Keys.NUM_6) {
                animator.play("Walk", PlayMode.LOOP);
                tail.play("Run", PlayMode.LOOP);
                tail.setWeight(1);
                label = "Walk + masked Run tail override";
                return true;
              }
              return false;
            }
          });
      report.put("checks", checks);
      report.put("result", "PASS");
      writeReport();
      if (!interactive) Gdx.app.exit();
    } catch (Throwable failure) {
      report.put("result", "FAIL");
      report.put("error", failure.toString());
      report.put("checks", checks);
      writeReport();
      throw new RuntimeException(failure);
    }
  }

  void validate() {
    JsonValue oracle = new JsonReader().parse(Gdx.files.absolute(root + "/oracle.json"));
    double max = 0;
    for (JsonValue sample : oracle.get("samples")) {
      String clip = sample.getString("clip");
      float t = sample.getFloat("time");
      animator.play(clip, PlayMode.LOOP).seek(t);
      float[] actual = skin();
      float[] expected = sample.get("vertices").asFloatArray();
      for (int i = 0; i < actual.length; i++)
        max = Math.max(max, Math.abs(actual[i] - expected[i]));
      if (t == .25f) shot(clip.toLowerCase() + "-025");
    }
    require(max < .01, "glTF oracle skinning max error " + max);
    report.put("oracleMaxErrorAssetUnits", max);
    report.put("oracleToleranceAssetUnits", .01);
    report.put("worldModelScale", .02);
    checks.add("12 original glTF oracle samples x 1728 skinned vertices");
    for (String clip : new String[] {"Survey", "Walk", "Run"}) {
      var p = animator.play(clip, PlayMode.LOOP).seek(.25);
      float[] a = pixels();
      p.seek(.5);
      float[] b = pixels();
      int diff = 0;
      for (int i = 0; i < a.length; i++) if (Math.abs(a[i] - b[i]) > 6) diff++;
      require(diff > 300, "visible animated pixels " + clip + ": " + diff);
      report.put(clip + "ChangedChannels", diff);
    }
    checks.add("all three clips change actual geometry pixels, HUD excluded");
    animator.play("Walk", PlayMode.LOOP).seek(.25);
    Map<String, Pose> outgoing = poses(model.instance);
    var fading = animator.play(AnimationTransition.clip("Run").loop().fade(.5));
    require(delta(outgoing, poses(model.instance)) < .001, "crossfade starts at outgoing pose");
    advance(.25);
    Map<String, Pose> incoming = reference("Run", .25);
    compareBlend(outgoing, incoming, poses(model.instance), .5f);
    require(fading.isFading(), "crossfade mid active");
    shot("crossfade-mid");
    advance(.25);
    require(!fading.isFading(), "crossfade completes");
    require(
        delta(reference("Run", .5), poses(model.instance)) < .001,
        "crossfade endpoint matches original glTF reference");
    checks.add("crossfade continuity, independently calculated midpoint and endpoint");
    animator.play("Walk", PlayMode.LOOP).seek(.25);
    var before = poses(model.instance);
    tail.play("Run", PlayMode.LOOP).seek(.5);
    tail.setWeight(1);
    var after = poses(model.instance);
    var ref = reference("Run", .5);
    Set<String> selected = new HashSet<>();
    collectNames(model.instance.getNode("b_Tail01_012", true), selected);
    double maskedChange = 0;
    for (String id : before.keySet()) {
      require(before.get(id) != null, "node exists");
      if (selected.contains(id)) {
        require(after.get(id).difference(ref.get(id)) < .001, "masked pose reference " + id);
        maskedChange = Math.max(maskedChange, after.get(id).difference(before.get(id)));
      } else require(after.get(id).difference(before.get(id)) < .001, "mask isolation " + id);
    }
    require(maskedChange > .01, "tail override actually changes pose");
    shot("tail-mask");
    tail.stop().setWeight(0);
    checks.add(
        "tail subtree mask matches source clip and leaves all other local node poses unchanged");
    var mixer = LinearModelMixer.builder(1).add("Walk", 0).add("Run", 1).build();
    var mixed = animator.play(mixer, PlayMode.LOOP, .5).seek(.25);
    compareBlend(
        reference("Walk", source.getAnimation("Walk").duration * .25),
        reference("Run", source.getAnimation("Run").duration * .25),
        poses(model.instance),
        .5f);
    shot("mixer-50");
    checks.add("phase-synchronous mixer matches independent original glTF clip samples");
    double time = mixed.time();
    game.pause();
    advance(.2);
    require(mixed.time() == time, "WORLD pause time");
    game.resume();
    mixed.pause();
    advance(.2);
    require(mixed.time() == time, "playback pause time");
    mixed.resume().setSpeed(2);
    game.step(.1f);
    require(Math.abs(mixed.time() - time - .2) < 1e-5, "speed and resume");
    checks.add("WORLD pause, playback pause/resume and 2x speed");
    var fresh = animator.play("Survey", PlayMode.LOOP);
    double clock = fresh.time();
    float[] first = skin();
    drawScene();
    drawScene();
    require(fresh.time() == clock, "render never advances pose clock");
    require(arrayDelta(first, skin()) < .0001, "repeat rendering stable skin");
    Game other = new Game();
    var otherObject = other.createObject("independent");
    otherObject.addComponent(new Transform3D());
    var otherRenderer = otherObject.addComponent(new ModelRenderer(source));
    var otherAnimator = otherObject.addComponent(new ModelPoseAnimator());
    other.start();
    otherAnimator.play("Run", PlayMode.LOOP).seek(.5);
    require(arrayDelta(first, skin()) < .0001, "second instance isolation");
    other.close();
    require(!model.instance.nodes.isEmpty(), "borrowed model survives scene close");
    checks.add("independent instances and single update per frame");
    int[] events = {0, 0};
    var once =
        animator.play(AnimationTransition.clip("Survey").markers(new AnimationMarker("half", .5)));
    once.onMarker("half", () -> events[0]++).onComplete(() -> events[1]++);
    advance(once.duration() + .01);
    require(events[0] == 1 && events[1] == 1 && once.isCompleted(), "marker and complete once");
    game.step(.1f);
    require(events[0] == 1 && events[1] == 1, "no duplicate completion");
    checks.add("named marker and ONCE completion fire once");
  }

  static String sha256(Path path) throws Exception {
    byte[] digest =
        java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
    return java.util.HexFormat.of().formatHex(digest);
  }

  void validateBatching() {
    final int count = 256, frames = 20;
    int[] cachedEvents = {0}, plainEvents = {0};
    try (Game cached = new Game();
        Game uncached = new Game()) {
      PoseBatchSystem service = cached.addSystem(new PoseBatchSystem(32));
      ModelRenderer[] shared = new ModelRenderer[count], plain = new ModelRenderer[count];
      ModelPoseAnimator[] a = new ModelPoseAnimator[count], b = new ModelPoseAnimator[count];
      for (int i = 0; i < count; i++) {
        GameObject co = cached.createObject("cached Fox " + i),
            uo = uncached.createObject("uncached Fox " + i);
        shared[i] = co.addComponent(new ModelRenderer(source));
        plain[i] = uo.addComponent(new ModelRenderer(source));
        a[i] = co.addComponent(new ModelPoseAnimator());
        b[i] = uo.addComponent(new ModelPoseAnimator());
      }
      cached.start();
      uncached.start();
      for (int i = 0; i < count; i++) {
        a[i].play(AnimationTransition.clip("Walk").loop().markers(new AnimationMarker("hit", .05)))
            .onMarker("hit", () -> cachedEvents[0]++);
        b[i].play(AnimationTransition.clip("Walk").loop().markers(new AnimationMarker("hit", .05)))
            .onMarker("hit", () -> plainEvents[0]++);
      }
      cached.step(.1f);
      uncached.step(.1f);
      require(
          service.stats().frameSamplerExecutions() == 1 && service.stats().frameHits() == 255,
          "256 imported Fox owners share one actual composition");
      require(
          cachedEvents[0] == 256 && plainEvents[0] == 256,
          "256 independent imported Fox marker events");
      for (int i = 0; i < count; i++) {
        compareBoundNodes(shared[i].instance.nodes, plain[i].instance.nodes);
        NodePart sharedPart = findSkinned(shared[i].instance.nodes),
            plainPart = findSkinned(plain[i].instance.nodes);
        require(sharedPart.bones != plainPart.bones, "independent palette arrays");
        for (int bone = 0; bone < sharedPart.bones.length; bone++) {
          require(sharedPart.bones[bone] != plainPart.bones[bone], "independent skin matrices");
          require(
              Arrays.equals(sharedPart.bones[bone].val, plainPart.bones[bone].val),
              "bit exact cached and uncached skin matrices");
          if (i > 0)
            require(
                sharedPart.bones[bone] != findSkinned(shared[0].instance.nodes).bones[bone],
                "per owner cached skin matrices");
        }
      }
      require(
          arrayDelta(skin(shared[0]), skin(plain[0])) == 0,
          "bit exact cached and uncached imported skinned vertices");
      require(
          service.sourceStats().snapshotsCreated() == 1,
          "one copied immutable Fox clip snapshot for 256 bindings");
      for (int warm = 0; warm < 10; warm++) {
        cached.step(.016f);
        uncached.step(.016f);
      }
      var bean =
          (com.sun.management.ThreadMXBean)
              java.lang.management.ManagementFactory.getThreadMXBean();
      require(bean.isThreadAllocatedMemorySupported(), "thread allocation measurement available");
      bean.setThreadAllocatedMemoryEnabled(true);
      long thread = Thread.currentThread().threadId();
      long allocations = bean.getThreadAllocatedBytes(thread), start = System.nanoTime();
      for (int frame = 0; frame < frames; frame++) cached.step(.016f);
      long cachedNanos = System.nanoTime() - start,
          cachedBytes = bean.getThreadAllocatedBytes(thread) - allocations;
      allocations = bean.getThreadAllocatedBytes(thread);
      start = System.nanoTime();
      for (int frame = 0; frame < frames; frame++) uncached.step(.016f);
      long plainNanos = System.nanoTime() - start,
          plainBytes = bean.getThreadAllocatedBytes(thread) - allocations;
      compareBoundNodes(shared[0].instance.nodes, plain[0].instance.nodes);
      require(
          service.stats().frameSamplerExecutions() == 1 && service.stats().frameHits() == 255,
          "one composition every measured frame");
      require(
          service.sourceStats().snapshotsCreated() == 1,
          "steady state has no full clip snapshot copies");
      Map<String, Object> cost = new LinkedHashMap<>();
      cost.put("owners", count);
      cost.put("frames", frames);
      cost.put("warmupFrames", 10);
      cost.put("cachedNanosecondsPerFrame", cachedNanos / frames);
      cost.put("uncachedNanosecondsPerFrame", plainNanos / frames);
      cost.put("cachedAllocatedBytesPerFrame", cachedBytes / frames);
      cost.put("uncachedAllocatedBytesPerFrame", plainBytes / frames);
      cost.put("actualCachedCompositionsLastFrame", service.stats().frameSamplerExecutions());
      cost.put("actualCachedHitsLastFrame", service.stats().frameHits());
      cost.put("snapshotCopies", service.sourceStats().snapshotsCreated());
      cost.put("sourceSnapshotMatchChecks", service.sourceStats().comparisons());
      cost.put("markerEvents", cachedEvents[0]);
      report.put("batchingCost", cost);
      checks.add(
          "256 Fox owners: exact local/socket/skin matrices and vertices versus uncached;"
              + " independent 256 events; one composition and immutable source copy; measured"
              + " CPU/allocation cost");
    }
  }

  static void compareBoundNodes(Array<Node> cached, Array<Node> plain) {
    require(cached.size == plain.size, "equal bound node count");
    for (int i = 0; i < cached.size; i++) compareBoundNode(cached.get(i), plain.get(i));
  }

  static void compareBoundNode(Node cached, Node plain) {
    require(
        cached != plain
            && cached.localTransform != plain.localTransform
            && cached.globalTransform != plain.globalTransform,
        "per owner node and socket matrices");
    require(
        Arrays.equals(cached.localTransform.val, plain.localTransform.val),
        "bit exact cached local pose");
    require(
        Arrays.equals(cached.globalTransform.val, plain.globalTransform.val),
        "bit exact cached socket pose");
    require(cached.getChildCount() == plain.getChildCount(), "equal child count");
    for (int i = 0; i < cached.getChildCount(); i++)
      compareBoundNode(cached.getChild(i), plain.getChild(i));
  }

  void advance(double seconds) {
    while (seconds > 1e-8) {
      float dt = (float) Math.min(.05, seconds);
      game.step(dt);
      seconds -= dt;
    }
  }

  record Pose(Vector3 t, Quaternion q, Vector3 s) {
    double difference(Pose b) {
      return Math.max(Math.max(t.dst(b.t), s.dst(b.s)), 1 - Math.min(1, Math.abs(q.dot(b.q))));
    }
  }

  static Map<String, Pose> poses(ModelInstance instance) {
    Map<String, Pose> out = new LinkedHashMap<>();
    for (Node n : instance.nodes) collect(n, out);
    return out;
  }

  static void collect(Node n, Map<String, Pose> out) {
    out.put(n.id, new Pose(n.translation.cpy(), n.rotation.cpy(), n.scale.cpy()));
    for (Node c : n.getChildren()) collect(c, out);
  }

  static void collectNames(Node n, Set<String> out) {
    out.add(n.id);
    for (Node c : n.getChildren()) collectNames(c, out);
  }

  Map<String, Pose> reference(String clip, double time) {
    JsonValue oracle = new JsonReader().parse(Gdx.files.absolute(root + "/oracle.json"));
    for (JsonValue sample : oracle.get("samples")) {
      if (sample.getString("clip").equals(clip)
          && Math.abs(sample.getDouble("time") - time) < 1e-6) {
        Map<String, Pose> result = new LinkedHashMap<>();
        for (JsonValue p : sample.get("poses")) {
          float[] t = p.get("translation").asFloatArray(),
              q = p.get("rotation").asFloatArray(),
              scale = p.get("scale").asFloatArray();
          result.put(
              p.name,
              new Pose(
                  new Vector3(t),
                  new Quaternion(q[0], q[1], q[2], q[3]).nor(),
                  new Vector3(scale)));
        }
        return result;
      }
    }
    throw new AssertionError("Missing glTF oracle pose " + clip + " " + time);
  }

  static double delta(Map<String, Pose> a, Map<String, Pose> b) {
    double max = 0;
    for (String id : a.keySet()) max = Math.max(max, a.get(id).difference(b.get(id)));
    return max;
  }

  static void compareBlend(
      Map<String, Pose> a, Map<String, Pose> b, Map<String, Pose> actual, float alpha) {
    for (String id : a.keySet()) {
      Pose x = a.get(id), y = b.get(id);
      Pose expected =
          new Pose(
              x.t.cpy().lerp(y.t, alpha),
              x.q.cpy().slerp(y.q, alpha).nor(),
              x.s.cpy().lerp(y.s, alpha));
      require(
          expected.difference(actual.get(id)) < .002,
          "blend pose " + id + " delta=" + expected.difference(actual.get(id)));
    }
  }

  float[] skin() {
    return skin(model);
  }

  static float[] skin(ModelRenderer model) {
    NodePart part = findSkinned(model.instance.nodes);
    Mesh mesh = part.meshPart.mesh;
    int stride = mesh.getVertexSize() / 4;
    float[] v = new float[mesh.getNumVertices() * stride];
    mesh.getVertices(v);
    float[] out = new float[mesh.getNumVertices() * 3];
    for (int i = 0; i < mesh.getNumVertices(); i++) {
      Vector3 sum = new Vector3();
      for (int w = 0; w < 4; w++) {
        int o = i * stride + 8 + w * 2;
        float weight = v[o + 1];
        if (weight != 0)
          sum.mulAdd(
              new Vector3(v[i * stride], v[i * stride + 1], v[i * stride + 2])
                  .mul(part.bones[(int) v[o]]),
              weight);
      }
      out[i * 3] = sum.x;
      out[i * 3 + 1] = sum.y;
      out[i * 3 + 2] = sum.z;
    }
    return out;
  }

  static NodePart findSkinned(Array<Node> nodes) {
    for (Node n : nodes) {
      for (NodePart p : n.parts) if (p.bones != null) return p;
      Array<Node> children = new Array<>();
      for (Node c : n.getChildren()) children.add(c);
      NodePart p = findSkinned(children);
      if (p != null) return p;
    }
    return null;
  }

  static double arrayDelta(float[] a, float[] b) {
    double m = 0;
    for (int i = 0; i < a.length; i++) m = Math.max(m, Math.abs(a[i] - b[i]));
    return m;
  }

  void drawScene() {
    Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    Gdx.gl.glClearColor(.12f, .17f, .23f, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    renderer.render(game);
  }

  float[] pixels() {
    drawScene();
    Pixmap p = Pixmap.createFromFrameBuffer(0, 0, 1000, 600);
    float[] out = new float[1000 * 600 * 3];
    int k = 0;
    for (int y = 0; y < 600; y++)
      for (int x = 0; x < 1000; x++) {
        int c = p.getPixel(x, y);
        out[k++] = (c >>> 24) & 255;
        out[k++] = (c >>> 16) & 255;
        out[k++] = (c >>> 8) & 255;
      }
    p.dispose();
    return out;
  }

  void shot(String name) {
    label = name;
    drawScene();
    drawLabel();
    Pixmap p = Pixmap.createFromFrameBuffer(0, 0, 1000, 700);
    PixmapIO.writePNG(Gdx.files.absolute(output + "/" + name + ".png"), p, -1, true);
    p.dispose();
  }

  void drawLabel() {
    batch.begin();
    font.draw(batch, "JAVITY / IMPORTED FOX / " + label, 22, 677);
    font.draw(
        batch,
        "24 joints | Survey / Walk / Run | Production ModelPoseAnimator + GdxRenderer",
        22,
        652);
    font.draw(
        batch,
        "Fox: PixelMannen; rig/animation: tomkranis; glTF: AsoboStudio & scurest | CC0 + CC BY 4.0",
        22,
        25);
    batch.end();
  }

  static JsonValue plain(Object value) {
    if (value instanceof Map<?, ?> map) {
      JsonValue out = new JsonValue(JsonValue.ValueType.object);
      for (var entry : map.entrySet())
        out.addChild(entry.getKey().toString(), plain(entry.getValue()));
      return out;
    }
    if (value instanceof Iterable<?> list) {
      JsonValue out = new JsonValue(JsonValue.ValueType.array);
      for (Object entry : list) out.addChild(plain(entry));
      return out;
    }
    if (value instanceof Boolean b) return new JsonValue(b);
    if (value instanceof Integer || value instanceof Long)
      return new JsonValue(((Number) value).longValue());
    if (value instanceof Number number) return new JsonValue(number.doubleValue());
    return new JsonValue(value.toString());
  }

  void writeReport() {
    try {
      Files.createDirectories(Path.of(output));
      Files.writeString(
          Path.of(output, "verification.json"),
          plain(report).prettyPrint(JsonWriter.OutputType.json, 100));
      System.out.println("IMPORTED_ANIMATION " + report);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  static void require(boolean ok, String message) {
    if (!ok) throw new AssertionError(message);
  }

  @Override
  public void render() {
    if (interactive && game != null) {
      game.step(Math.min(Gdx.graphics.getDeltaTime(), .05f));
      drawScene();
      drawLabel();
    }
  }

  @Override
  public void resize(int w, int h) {
    if (renderer != null) {
      renderer.resize(w, h);
      batch.getProjectionMatrix().setToOrtho2D(0, 0, w, h);
    }
  }

  @Override
  public void dispose() {
    try {
      int texture =
          source == null
              ? 0
              : ((com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute)
                      source
                          .materials
                          .first()
                          .get(com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Diffuse))
                  .textureDescription.texture.getTextureObjectHandle();
      if (game != null) game.close();
      if (renderer != null) renderer.dispose();
      if (source != null) source.dispose();
      if (batch != null) batch.dispose();
      if (font != null) font.dispose();
      require(texture == 0 || !Gdx.gl.glIsTexture(texture), "source texture released");
      report.put("resourcesClosed", true);
      writeReport();
    } catch (Throwable failure) {
      report.put("result", "FAIL");
      report.put("cleanupError", failure.toString());
      writeReport();
      throw new RuntimeException(failure);
    }
  }
}
