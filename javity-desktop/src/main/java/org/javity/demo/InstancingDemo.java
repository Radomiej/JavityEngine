package org.javity.demo;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.*;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.profiling.GLProfiler;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.utils.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.IntBuffer;
import java.util.HashSet;
import java.util.Set;
import org.javity.core.*;
import org.javity.core.Game;
import org.javity.presentation.Presentation3D;
import org.javity.render.*;

/** Deterministic real-GL comparison; artifacts are reproducible from both Gradle smoke tasks. */
public final class InstancingDemo extends ApplicationAdapter {
  private final boolean gl20;
  private Model model;
  private ModelBatch ordinary;
  private StaticInstanceBatch instances;
  private FrameBuffer target;
  private GLProfiler profiler;
  private NativeCalls calls;
  private final Array<Renderable> candidates = new Array<>();
  private final Pool<Renderable> pool =
      new Pool<>() {
        protected Renderable newObject() {
          return new Renderable();
        }
      };
  private OrthographicCamera camera;
  private final Environment environment = new Environment();
  private int assertions;

  public InstancingDemo(boolean gl20) {
    this.gl20 = gl20;
  }

  @Override
  public void create() {
    try {
      run();
      dispose();
      System.out.println(
          "instancingSmoke " + (gl20 ? "GL20" : "GL33") + ": PASS assertions=" + assertions);
    } catch (Throwable failure) {
      failure.printStackTrace();
      System.exit(1);
    } finally {
      dispose();
      Gdx.app.exit();
    }
  }

  private void run() {
    camera = new OrthographicCamera(18, 18);
    camera.position.set(0, 0, 20);
    camera.lookAt(0, 0, 0);
    camera.near = .1f;
    camera.far = 50;
    camera.update();
    environment.set(ColorAttribute.createAmbientLight(.35f, .4f, .45f, 1));
    environment.add(new DirectionalLight().set(.7f, .65f, .6f, -.4f, -.5f, -1));
    model =
        new ModelBuilder()
            .createBox(
                .65f,
                .65f,
                .65f,
                new Material(ColorAttribute.createDiffuse(.2f, .7f, .35f, 1)),
                VertexAttributes.Usage.Position
                    | VertexAttributes.Usage.Normal
                    | VertexAttributes.Usage.TextureCoordinates);
    ordinary = new ModelBatch();
    instances = new StaticInstanceBatch(256);
    target = new FrameBuffer(Pixmap.Format.RGBA8888, 256, 256, true);
    profiler = new GLProfiler(Gdx.graphics);
    profiler.enable();
    calls = new NativeCalls();
    calls.install();
    populate(256);
    Pixmap baseline = image(false, "ordinary-256");
    check(
        profiler.getDrawCalls() == 256, "ordinary 256 measured draws: " + profiler.getDrawCalls());
    Pixmap optimized = image(true, "optimized-256");
    compare(baseline, optimized);
    Pixmap pair = new Pixmap(512, 256, Pixmap.Format.RGBA8888);
    pair.setBlending(Pixmap.Blending.None);
    pair.drawPixmap(baseline, 0, 0);
    pair.drawPixmap(optimized, 256, 0);
    PixmapIO.writePNG(
        Gdx.files.local(
            "docs/artifacts/instancing/" + (gl20 ? "gl20" : "gl33") + "-comparison.png"),
        pair);
    pair.dispose();
    optimized.dispose();
    if (gl20) {
      check(instances.stats().fallback() == 256, "GL20 retains all candidates");
      check(instances.fallbackReasons().containsKey("GL30_UNAVAILABLE"), "GL20 explicit reason");
      check(profiler.getDrawCalls() == 256, "GL20 actual ordinary draw count");
      baseline.dispose();
      return;
    }
    check(
        instances.stats().instanced() == 256 && instances.stats().draws() == 1,
        "256 in one instanced draw " + instances.stats() + instances.fallbackReasons());
    check(
        profiler.getDrawCalls() == 1 && calls.instancedDraws == 1 && calls.ordinaryDraws == 0,
        "actual GL instanced draw count=" + profiler.getDrawCalls());
    check(calls.subDataBytes == instances.stats().uploadBytes(), "actual instance upload bytes");
    check(instances.stats().uploadBytes() == 256 * 64, "first matrix upload");
    System.out.println(
        "measured: ordinaryDraws=256 instancedDraws="
            + calls.instancedDraws
            + " dynamicUploadBytes="
            + calls.subDataBytes
            + " stats="
            + instances.stats());
    image(true, "unchanged").dispose();
    check(
        instances.stats().uploadBytes() == 0 && calls.subDataBytes == 0,
        "unchanged compact chunks do not upload");
    ((ColorAttribute) candidates.first().material.get(ColorAttribute.Diffuse))
        .color.set(.8f, .1f, .2f, 1);
    compareAndClose();
    ((ColorAttribute) candidates.first().material.get(ColorAttribute.Diffuse))
        .color.set(.2f, .7f, .35f, 1);
    image(true, "restored-material").dispose();
    candidates.get(12).worldTransform.translate(.05f, 0, 0);
    image(true, "one-dirty").dispose();
    check(
        instances.stats().uploadBytes() > 0 && instances.stats().uploadBytes() <= 64,
        "one dirty matrix range");
    compareAndClose();
    populate(257);
    image(true, "capacity-257").dispose();
    check(
        instances.stats().instanced() == 257 && profiler.getDrawCalls() == 2,
        "capacity overflow draws twice");
    candidates.removeIndex(17);
    image(true, "removed").dispose();
    check(
        instances.stats().instanced() == 256 && profiler.getDrawCalls() == 1,
        "remove no ghost instance");
    compareAndClose();
    ((ColorAttribute) candidates.get(0).material.get(ColorAttribute.Diffuse))
        .color.set(.9f, .1f, .2f, 1);
    image(true, "changed-material").dispose();
    check(
        instances.stats().groups() == 2 && profiler.getDrawCalls() == 2,
        "material values split groups");
    compareAndClose();
    candidates.get(1).worldTransform.scale(-1, 1, 1);
    image(true, "fallback-mirror").dispose();
    check(
        instances.stats().fallback() == 1
            && instances.fallbackReasons().containsKey("MIRRORED_TRANSFORM"),
        "negative scale fallback");
    compareAndClose();
    candidates.get(2).worldTransform.setTranslation(1000, 0, 0);
    image(true, "culled").dispose();
    check(instances.stats().culled() == 1, "final world transform culling");
    compareAndClose();
    populate(256);
    // DefaultShader ignores AlphaTest without blendedFlag. Preserve that behavior in both paths.
    for (Renderable r : candidates) r.material.set(FloatAttribute.createAlphaTest(.5f));
    compareAndClose();
    // Explicit nonblended BlendingAttribute enables DefaultShader's cutout variant.
    Pixmap opaquePixels = image(false, "alpha-test-without-blended-flag");
    int opaqueCoverage = foreground(opaquePixels);
    opaquePixels.dispose();
    Pixmap cutout = new Pixmap(2, 2, Pixmap.Format.RGBA8888);
    cutout.setColor(1, 1, 1, 1);
    cutout.fill();
    cutout.setBlending(Pixmap.Blending.None);
    cutout.drawPixel(0, 0, 0);
    Texture texture = new Texture(cutout);
    cutout.dispose();
    try {
      for (Renderable r : candidates)
        r.material.set(
            TextureAttribute.createDiffuse(texture),
            new BlendingAttribute(false, GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 1));
      compareAndClose();
      Pixmap holes = image(true, "alpha-test-holes");
      check(
          foreground(holes) < opaqueCoverage * .9f,
          "alpha-test visible holes " + foreground(holes) + "/" + opaqueCoverage);
      holes.dispose();
    } finally {
      for (Renderable r : candidates) {
        r.material.remove(TextureAttribute.Diffuse);
        r.material.remove(BlendingAttribute.Type);
      }
      texture.dispose();
    }
    check(!model.meshes.first().isInstanced(), "borrowed source remains ordinary");
    Pixmap sourceAfter = image(false, "source-after");
    compare(baseline, sourceAfter);
    sourceAfter.dispose();
    Mesh source = model.meshes.first();
    float[] original =
        source.getVertices(new float[source.getNumVertices() * source.getVertexSize() / 4]);
    float[] edited = original.clone();
    for (int i = 0; i < edited.length; i += source.getVertexSize() / 4) edited[i] *= .9f;
    source.setVertices(edited);
    instances.invalidateGeometry(source);
    compareAndClose();
    source.setVertices(original);
    instances.invalidateGeometry(source);
    compareAndClose();
    candidates.clear();
    image(true, "empty").dispose();
    check(instances.stats().groups() == 0, "scene removal prunes groups");
    baseline.dispose();
    populate(256);
    stateRoundTrip();
    allocationFailure();
    rendererIntegration();
    Pixmap beforeDisposal = image(false, "source-before-batch-disposal");
    instances.dispose();
    instances = null;
    Pixmap afterDisposal = image(false, "source-after-batch-disposal");
    compare(beforeDisposal, afterDisposal);
    beforeDisposal.dispose();
    afterDisposal.dispose();
    check(calls.ordinaryDraws == 256, "borrowed source draws after owned batch disposal");
    check(Gdx.gl.glGetError() == GL20.GL_NO_ERROR, "GL remains clean");
  }

  private void allocationFailure() {
    Set<Integer> buffers = new HashSet<>(calls.liveBuffers),
        programs = new HashSet<>(calls.livePrograms);
    try (BatchScope scope = new BatchScope(new StaticInstanceBatch(256))) {
      for (Renderable r : candidates) r.shader = null;
      calls.failDynamic = true;
      Array<Renderable> fallback = scope.batch.render(candidates, camera, environment);
      check(
          fallback.size == candidates.size && scope.batch.stats().instanced() == 0,
          "allocation failure preserves all candidates");
      check(
          scope.batch.fallbackReasons().keySet().stream()
              .anyMatch(k -> k.startsWith("GPU_PREPARATION_FAILED")),
          "allocation failure reason");
    }
    check(
        !calls.failDynamic
            && calls.liveBuffers.equals(buffers)
            && calls.livePrograms.equals(programs),
        "allocation failure cleans every buffer/program " + calls.liveBuffers + "/" + buffers);
    compareAndClose();
    // Dispose is idempotent and only owned resources are released.
    StaticInstanceBatch disposed = new StaticInstanceBatch(1);
    disposed.dispose();
    disposed.dispose();
    boolean rejected = false;
    try {
      disposed.render(candidates, camera, environment);
    } catch (IllegalStateException expected) {
      rejected = true;
    }
    check(
        rejected && !model.meshes.first().isInstanced(),
        "disposed batch rejects rendering and source remains borrowed");
  }

  private record BatchScope(StaticInstanceBatch batch) implements AutoCloseable {
    public void close() {
      batch.dispose();
    }
  }

  private void stateRoundTrip() {
    IntBuffer names = BufferUtils.newIntBuffer(1);
    Gdx.gl30.glGenVertexArrays(1, names);
    int vao = names.get(0);
    int buffer = Gdx.gl.glGenBuffer(), element = Gdx.gl.glGenBuffer();
    Gdx.gl30.glBindVertexArray(vao);
    Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, buffer);
    Gdx.gl.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, element);
    Gdx.gl.glEnableVertexAttribArray(7);
    Gdx.gl30.glVertexAttribDivisor(7, 2);
    Gdx.gl.glEnable(GL20.GL_BLEND);
    Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
    Gdx.gl.glEnable(GL20.GL_CULL_FACE);
    Gdx.gl.glCullFace(GL20.GL_FRONT);
    Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    Gdx.gl.glDepthFunc(GL20.GL_GREATER);
    Gdx.gl.glDepthMask(false);
    Gdx.gl.glActiveTexture(GL20.GL_TEXTURE3);
    for (Renderable r : candidates) r.shader = null;
    instances.render(candidates, camera, environment);
    check(
        integer(0x85B5) == vao
            && integer(GL20.GL_ARRAY_BUFFER_BINDING) == buffer
            && integer(GL20.GL_ELEMENT_ARRAY_BUFFER_BINDING) == element,
        "VAO and buffer bindings restored");
    Gdx.gl.glGetVertexAttribiv(7, 0x88FE, names);
    check(
        names.get(0) == 2 && integer(GL20.GL_ACTIVE_TEXTURE) == GL20.GL_TEXTURE3,
        "external divisor and active texture restored");
    check(
        integer(GL20.GL_DEPTH_FUNC) == GL20.GL_GREATER
            && integer(GL20.GL_DEPTH_WRITEMASK) == 0
            && integer(GL20.GL_CULL_FACE_MODE) == GL20.GL_FRONT
            && Gdx.gl.glIsEnabled(GL20.GL_BLEND),
        "depth/blend/cull state restored");
    Gdx.gl30.glVertexAttribDivisor(7, 0);
    Gdx.gl.glDisableVertexAttribArray(7);
    Gdx.gl30.glBindVertexArray(0);
    Gdx.gl30.glDeleteVertexArrays(1, names.clear().put(vao).flip());
    Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, 0);
    Gdx.gl.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, 0);
    Gdx.gl.glDeleteBuffer(buffer);
    Gdx.gl.glDeleteBuffer(element);
    Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
    Gdx.gl.glDepthMask(true);
    Gdx.gl.glDisable(GL20.GL_BLEND);
    Gdx.gl.glCullFace(GL20.GL_BACK);
    Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
    compareAndClose();
  }

  private static int integer(int token) {
    IntBuffer result = BufferUtils.newIntBuffer(1);
    Gdx.gl.glGetIntegerv(token, result);
    return result.get(0);
  }

  private void rendererIntegration() {
    Game game = new Game();
    GdxRenderer renderer = new GdxRenderer();
    renderer.resize(256, 256);
    GameObject cameraObject = game.createObject("camera");
    Camera3D view = cameraObject.addComponent(new Camera3D(55));
    view.camera.position.set(0, 0, 8);
    view.camera.lookAt(0, 0, 0);
    view.camera.update();
    GameObject[] objects = new GameObject[16];
    InstancedModel[] markers = new InstancedModel[15];
    for (int i = 0; i < 16; i++) {
      objects[i] = game.createObject("box" + i);
      objects[i].addComponent(new Transform3D()).position.set((i % 4) - 1.5f, (i / 4) - 1.5f, 0);
      objects[i].addComponent(new ModelRenderer(model));
      if (i < 15) {
        markers[i] = objects[i].addComponent(new InstancedModel());
        markers[i].setEnabled(false);
      }
    }
    GameObject parent = game.createObject("parent");
    Transform3D parentTransform = parent.addComponent(new Transform3D());
    objects[0].getComponent(Transform3D.class).setParent(parentTransform);
    Presentation3D presentation = objects[0].addComponent(new Presentation3D());
    game.start();
    game.step(0);
    Presentation3D.Lease lease = presentation.contribute();
    try {
      Pixmap ordinaryPixels = worldImage(renderer, game, view);
      check(profiler.getDrawCalls() == 16, "renderer ordinary actual draws");
      for (InstancedModel marker : markers) marker.setEnabled(true);
      Pixmap optimizedPixels = worldImage(renderer, game, view);
      compare(ordinaryPixels, optimizedPixels);
      ordinaryPixels.dispose();
      optimizedPixels.dispose();
      check(
          renderer.instanceStats().instanced() == 15
              && calls.instancedDraws == 1
              && calls.ordinaryDraws == 1,
          "opt-in companion integration with ordinary path boundary");
      parentTransform.position.x = .25f;
      lease.set(.2f, 0, 0, 0, 0, 0, 0, 0, 0);
      for (InstancedModel marker : markers) marker.setEnabled(false);
      ordinaryPixels = worldImage(renderer, game, view);
      for (InstancedModel marker : markers) marker.setEnabled(true);
      optimizedPixels = worldImage(renderer, game, view);
      PixmapIO.writePNG(
          Gdx.files.local("docs/artifacts/instancing/integration-moved-ordinary.png"),
          ordinaryPixels);
      PixmapIO.writePNG(
          Gdx.files.local("docs/artifacts/instancing/integration-moved-optimized.png"),
          optimizedPixels);
      compare(ordinaryPixels, optimizedPixels, true);
      comparatorNegativeControls(ordinaryPixels, optimizedPixels);
      ordinaryPixels.dispose();
      optimizedPixels.dispose();
      parentTransform.position.x = 1000;
      optimizedPixels = worldImage(renderer, game, view);
      optimizedPixels.dispose();
      check(
          renderer.instanceStats().culled() == 1 && renderer.instanceStats().instanced() == 14,
          "parent/presentation final-world culling");
      objects[1].destroy();
      game.step(0);
      optimizedPixels = worldImage(renderer, game, view);
      optimizedPixels.dispose();
      check(
          renderer.instanceStats().instanced() == 13,
          "component removal updates stable owner slots");
      renderer.invalidateGeometry(model.meshes.first());
      optimizedPixels = worldImage(renderer, game, view);
      optimizedPixels.dispose();
      check(renderer.instanceStats().instanced() == 13, "renderer forwards geometry invalidation");
    } finally {
      lease.close();
      game.close();
      renderer.dispose();
    }
  }

  private Pixmap worldImage(GdxRenderer renderer, Game game, Camera3D view) {
    target.begin();
    Gdx.gl.glViewport(0, 0, 256, 256);
    Gdx.gl.glDepthMask(true);
    Gdx.gl.glClearColor(0, 0, 0, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    profiler.reset();
    calls.resetCounters();
    renderer.renderWorld(game, view, null);
    Pixmap image = Pixmap.createFromFrameBuffer(0, 0, 256, 256);
    target.end();
    return image;
  }

  /**
   * Counts actual native-bound calls and tracks real allocated names; delegates every operation.
   */
  private static final class NativeCalls implements InvocationHandler {
    GL20 previousGl, previous20;
    GL30 previous30;
    int ordinaryDraws, instancedDraws;
    long subDataBytes;
    boolean failDynamic;
    int boundArray;
    final Set<Integer> dynamicBuffers = new HashSet<>();
    final Set<Integer> liveBuffers = new HashSet<>(),
        livePrograms = new HashSet<>(),
        liveShaders = new HashSet<>();

    void install() {
      previousGl = Gdx.gl;
      previous20 = Gdx.gl20;
      previous30 = Gdx.gl30;
      Class<?> api = previous30 == null ? GL20.class : GL30.class;
      GL20 proxy = (GL20) Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[] {api}, this);
      Gdx.gl = proxy;
      Gdx.gl20 = proxy;
      if (previous30 != null) Gdx.gl30 = (GL30) proxy;
    }

    void uninstall() {
      Gdx.gl = previousGl;
      Gdx.gl20 = previous20;
      Gdx.gl30 = previous30;
    }

    void resetCounters() {
      ordinaryDraws = instancedDraws = 0;
      subDataBytes = 0;
    }

    public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
      String name = method.getName();
      if (name.equals("glBufferData")
          && failDynamic
          && (Integer) arguments[3] == GL20.GL_DYNAMIC_DRAW) {
        failDynamic = false;
        throw new IllegalStateException("injected instance buffer allocation failure");
      }
      Object result;
      try {
        result = method.invoke(previous30 == null ? previous20 : previous30, arguments);
      } catch (InvocationTargetException wrapped) {
        throw wrapped.getCause();
      }
      if (name.equals("glDrawElements") || name.equals("glDrawArrays")) ordinaryDraws++;
      if (name.equals("glDrawElementsInstanced") || name.equals("glDrawArraysInstanced"))
        instancedDraws++;
      if (name.equals("glBindBuffer") && (Integer) arguments[0] == GL20.GL_ARRAY_BUFFER)
        boundArray = (Integer) arguments[1];
      if (name.equals("glBufferData")
          && (Integer) arguments[0] == GL20.GL_ARRAY_BUFFER
          && (Integer) arguments[3] == GL20.GL_DYNAMIC_DRAW) dynamicBuffers.add(boundArray);
      if (name.equals("glBufferSubData") && dynamicBuffers.contains(boundArray))
        subDataBytes += (Integer) arguments[2];
      if (name.equals("glGenBuffer")) liveBuffers.add((Integer) result);
      if (name.equals("glDeleteBuffer")) {
        liveBuffers.remove((Integer) arguments[0]);
        dynamicBuffers.remove((Integer) arguments[0]);
      }
      if (name.equals("glCreateShader")) liveShaders.add((Integer) result);
      if (name.equals("glDeleteShader")) liveShaders.remove((Integer) arguments[0]);
      if (name.equals("glCreateProgram")) livePrograms.add((Integer) result);
      if (name.equals("glDeleteProgram")) livePrograms.remove((Integer) arguments[0]);
      return result;
    }
  }

  private void populate(int count) {
    candidates.clear();
    for (int i = 0; i < count; i++) {
      ModelInstance instance = new ModelInstance(model);
      instance.transform.setToTranslation((i % 16) - 7.5f, (i / 16) - 7.5f, 0);
      instance.transform.rotate(Vector3.Z, (i % 5) * 7).scale(.8f, .8f, .8f);
      instance.getRenderables(candidates, pool);
    }
    for (int i = 0; i < candidates.size; i++) {
      candidates.get(i).environment = environment;
      candidates.get(i).userData = (long) i;
    }
  }

  private Pixmap image(boolean optimized, String name) {
    target.begin();
    Gdx.gl.glViewport(0, 0, 256, 256);
    Gdx.gl.glClearColor(0, 0, 0, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    profiler.reset();
    calls.resetCounters();
    for (Renderable r : candidates) r.shader = null;
    long[] ids = new long[candidates.size];
    for (int i = 0; i < ids.length; i++) ids[i] = (Long) candidates.get(i).userData;
    Array<Renderable> draw =
        optimized ? instances.render(candidates, camera, environment, ids) : candidates;
    ordinary.begin(camera);
    for (Renderable r : draw) ordinary.render(r);
    ordinary.end();
    Pixmap result = Pixmap.createFromFrameBuffer(0, 0, 256, 256);
    target.end();
    PixmapIO.writePNG(
        Gdx.files.local("docs/artifacts/instancing/" + (gl20 ? "gl20-" : "gl33-") + name + ".png"),
        result);
    return result;
  }

  private void compareAndClose() {
    Pixmap a = image(false, "ordinary-current"), b = image(true, "optimized-current");
    compare(a, b);
    a.dispose();
    b.dispose();
  }

  private void compare(Pixmap a, Pixmap b) {
    compare(a, b, false);
  }

  private void compare(Pixmap a, Pixmap b, boolean movedCreaseException) {
    int max = 0, coverage = 0, creasePixels = 0;
    int foregroundA = foreground(a), foregroundB = foreground(b);
    check(
        foregroundA > 1000 && foregroundB > 1000,
        "substantial nonempty image " + foregroundA + "/" + foregroundB);
    for (int y = 0; y < 256; y++)
      for (int x = 0; x < 256; x++)
        if (((a.getPixel(x, y) >>> 8) != 0) != ((b.getPixel(x, y) >>> 8) != 0)) coverage++;
    for (int y = 1; y < 255; y++)
      for (int x = 1; x < 255; x++) {
        if (silhouette(a, x, y)) continue;
        int difference = rgbDifference(a.getPixel(x, y), b.getPixel(x, y));
        max = Math.max(max, difference);
        if (difference > 2) {
          if (!movedCreaseException || !colorCrease(a, x, y))
            throw new AssertionError("RGB interior mismatch " + difference + " at " + x + "," + y);
          creasePixels++;
          System.out.println(
              "integration crease exception: x=" + x + " y=" + y + " maxRGB=" + difference);
        }
      }
    check(
        coverage == 0 && creasePixels <= (movedCreaseException ? 1 : 0),
        "pixel coverage=" + coverage + " RGB max=" + max + " creasePixels=" + creasePixels);
    System.out.println(
        "pixels: foreground="
            + foregroundA
            + "/"
            + foregroundB
            + " coverage="
            + coverage
            + " maxRGB="
            + max
            + " creasePixels="
            + creasePixels);
  }

  private static int rgbDifference(int a, int b) {
    int max = 0;
    for (int shift : new int[] {8, 16, 24})
      max = Math.max(max, Math.abs(((a >>> shift) & 255) - ((b >>> shift) & 255)));
    return max;
  }

  private static boolean silhouette(Pixmap image, int x, int y) {
    boolean ink = (image.getPixel(x, y) >>> 8) != 0;
    for (int dy = -1; dy <= 1; dy++)
      for (int dx = -1; dx <= 1; dx++)
        if (((image.getPixel(x + dx, y + dy) >>> 8) != 0) != ink) return true;
    return false;
  }

  private static boolean colorCrease(Pixmap image, int x, int y) {
    int color = image.getPixel(x, y);
    for (int dy = -1; dy <= 1; dy++)
      for (int dx = -1; dx <= 1; dx++)
        if (rgbDifference(color, image.getPixel(x + dx, y + dy)) > 16) return true;
    return false;
  }

  private void comparatorNegativeControls(Pixmap baseline, Pixmap optimized) {
    for (boolean crease : new boolean[] {false, true}) {
      Pixmap corrupted = new Pixmap(256, 256, Pixmap.Format.RGBA8888);
      corrupted.setBlending(Pixmap.Blending.None);
      corrupted.drawPixmap(optimized, 0, 0);
      boolean changed = false;
      for (int y = 2; y < 254 && !changed; y++)
        for (int x = 2; x < 254 && !changed; x++) {
          if ((baseline.getPixel(x, y) >>> 8) == 0
              || silhouette(baseline, x, y)
              || colorCrease(baseline, x, y) != crease
              || rgbDifference(baseline.getPixel(x, y), optimized.getPixel(x, y)) > 2) continue;
          corrupted.drawPixel(x, y, baseline.getPixel(x, y) ^ 0x10000000);
          changed = true;
        }
      check(
          changed, "negative control found " + (crease ? "second crease" : "interior") + " pixel");
      boolean rejected = false;
      try {
        compare(baseline, corrupted, true);
      } catch (AssertionError expected) {
        rejected = true;
      }
      corrupted.dispose();
      check(
          rejected,
          "comparator rejects " + (crease ? "second changed crease" : "changed interior"));
    }
  }

  private static int foreground(Pixmap image) {
    int result = 0;
    for (int y = 0; y < image.getHeight(); y++)
      for (int x = 0; x < image.getWidth(); x++) if ((image.getPixel(x, y) >>> 8) != 0) result++;
    return result;
  }

  private void check(boolean ok, String label) {
    assertions++;
    if (!ok) throw new AssertionError(label);
  }

  @Override
  public void dispose() {
    if (instances != null) {
      instances.dispose();
      instances = null;
    }
    if (ordinary != null) {
      ordinary.dispose();
      ordinary = null;
    }
    if (target != null) {
      target.dispose();
      target = null;
    }
    if (model != null) {
      model.dispose();
      model = null;
    }
    if (calls != null) {
      check(
          calls.liveBuffers.isEmpty()
              && calls.livePrograms.isEmpty()
              && calls.liveShaders.isEmpty(),
          "all owned tracked GL names released buffers="
              + calls.liveBuffers
              + " programs="
              + calls.livePrograms
              + " shaders="
              + calls.liveShaders);
      calls.uninstall();
      calls = null;
    }
    if (profiler != null) {
      profiler.disable();
      profiler = null;
    }
  }
}
