package org.javity.demo;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.*;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Vector3;
import java.nio.file.Path;
import org.javity.core.*;
import org.javity.render.*;
import org.javity.render.lighting.*;

/**
 * Real framebuffer controls for scene surface lighting. Sky state/recovery has its own shared gate.
 */
public final class EnvironmentGpuSmokeProbe extends ApplicationAdapter {
  private Throwable failure;
  private int checks;

  public static void main(String[] args) throws Exception {
    boolean gl20 = args.length > 0;
    var config = new Lwjgl3ApplicationConfiguration();
    config.setWindowedMode(128, 128);
    config.setInitialVisible(false);
    config.disableAudio(true);
    config.setOpenGLEmulation(
        gl20
            ? Lwjgl3ApplicationConfiguration.GLEmulation.GL20
            : Lwjgl3ApplicationConfiguration.GLEmulation.GL30,
        gl20 ? 2 : 3,
        gl20 ? 0 : 2);
    var probe = new EnvironmentGpuSmokeProbe();
    new Lwjgl3Application(probe, config);
    if (probe.failure != null)
      throw new AssertionError("Environment native controls failed", probe.failure);
    System.out.println(
        "environmentSmoke " + (gl20 ? "GL20" : "GL30") + ": PASS checks=" + probe.checks);
    if (!gl20) {
      int result =
          new ProcessBuilder(
                  Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                  "--enable-native-access=ALL-UNNAMED",
                  "-cp",
                  System.getProperty("java.class.path"),
                  EnvironmentGpuSmokeProbe.class.getName(),
                  "--gl20")
              .inheritIO()
              .start()
              .waitFor();
      if (result != 0) throw new AssertionError("GL20 child failed");
    }
  }

  @Override
  public void create() {
    try {
      run();
    } catch (Throwable t) {
      failure = t;
      t.printStackTrace();
    } finally {
      Gdx.app.exit();
    }
  }

  private void check(boolean condition, String message) {
    checks++;
    if (!condition) throw new AssertionError(message);
    System.out.println("PASS " + message);
  }

  private void run() {
    var renderer = new GdxRenderer();
    renderer.resize(128, 128);
    var fbo = new FrameBuffer(Pixmap.Format.RGBA8888, 128, 128, true);
    Model lit = box(true), unlit = box(false);
    try (Game game = new Game();
        Game legacy = new Game()) {
      var env = game.addSystem(new RenderEnvironment3D());
      Camera3D camera = camera(game), oldCamera = camera(legacy);
      var target = game.createObject("pixel target");
      var t = target.addComponent(new Transform3D());
      var surface = target.addComponent(new ModelRenderer(lit));
      target.addComponent(new InstancedModel());
      var oldTarget = legacy.createObject("legacy unlit");
      oldTarget.addComponent(new Transform3D());
      oldTarget.addComponent(new ModelRenderer(unlit));
      var lightOwner = game.createObject("lights");
      var lightT = lightOwner.addComponent(new Transform3D());
      lightT.position.z = 3;
      var sun = lightOwner.addComponent(new DirectionalLight3D());
      var point = lightOwner.addComponent(new PointLight3D());
      point.setEnabled(false);
      var spot = lightOwner.addComponent(new SpotLight3D());
      spot.setEnabled(false);
      game.start();
      legacy.start();
      fbo.begin();
      try {
        int bright = red(draw(renderer, game, camera));
        lightT.rotation.set(Vector3.Y, 180);
        int dark = red(draw(renderer, game, camera));
        check(
            bright > 180 && dark < 3,
            "directionalChangesPixels bright=" + bright + " dark=" + dark);
        sun.setEnabled(false);
        lightT.rotation.idt();
        point.setEnabled(true);
        point.setRange(10);
        int near = red(draw(renderer, game, camera));
        lightT.position.z = 8;
        int far = red(draw(renderer, game, camera));
        check(near > far + 50, "pointAttenuates near=" + near + " far=" + far);
        point.setEnabled(false);
        spot.setEnabled(true);
        spot.setRange(10);
        lightT.position.z = 3;
        int inside = red(draw(renderer, game, camera));
        lightT.rotation.set(Vector3.Y, 60);
        int outside = red(draw(renderer, game, camera));
        check(
            inside > 80 && outside < 3,
            "spotActuallyLightsCone inside=" + inside + " outside=" + outside);
        lightT.rotation.idt();
        env.setFog(.1f, .2f, .8f, 1, 12);
        verifyRelink(renderer, game, camera);
        env.clearFog();
        spot.setEnabled(false);
        check(red(draw(renderer, game, camera)) < 3, "disabledLightGone");
        env.setAmbient(1, 1, 1);
        env.setFog(.1f, .2f, .8f, 0, 1);
        int fog = draw(renderer, game, camera);
        check(blue(fog) > 190 && red(fog) < 35, "lit world fog");
        target.removeComponent(surface);
        game.step(0);
        surface = target.addComponent(new ModelRenderer(unlit));
        game.step(0);
        fog = draw(renderer, game, camera);
        check(blue(fog) > 190 && red(fog) < 35, "unlit world fog independent of normals");
        env.clearFog();
        check(red(draw(renderer, game, camera)) > 240, "unlit fog disabled control");
        env.setFog(.1f, .2f, .8f, 1, 8);
        t.position.z = 1;
        int close = red(draw(renderer, game, camera));
        t.position.z = -3;
        int distant = red(draw(renderer, game, camera));
        check(close > distant + 90, "unlit distance fog close=" + close + " distant=" + distant);
        check(red(draw(renderer, legacy, oldCamera)) > 240, "legacy unlit behavior unchanged");
        check(renderer.instanceStats().instanced() == 0, "scene world uses ordinary fallback");
        verifyViewmodelAndClock(renderer, game, camera, env, lit, sun);
        env.clearFog();
        target.setActive(false);
        verifySkinned(renderer, game, camera, env);
        check(Gdx.gl.glGetError() == GL20.GL_NO_ERROR, "no residual GL errors");
      } finally {
        fbo.end();
      }
    } finally {
      renderer.dispose();
      fbo.dispose();
      lit.dispose();
      unlit.dispose();
    }
  }

  private void verifyViewmodelAndClock(
      GdxRenderer renderer,
      Game game,
      Camera3D camera,
      RenderEnvironment3D env,
      Model lit,
      DirectionalLight3D sun) {
    env.setFog(.1f, .2f, .8f, 0, 1);
    draw(renderer, game, camera);
    ModelInstance hands = new ModelInstance(lit);
    hands.transform.setToTranslation(0, 0, 1);
    renderer.renderViewmodel(camera.camera, hands);
    Pixmap p = Pixmap.createFromFrameBuffer(0, 0, 128, 128);
    int hand = p.getPixel(64, 64);
    p.dispose();
    check(
        red(hand) > 60 && red(hand) > blue(hand) * .8f,
        "fogAffectsWorldOnly / legacy viewmodel remains visible");
    var shapes = new com.badlogic.gdx.graphics.glutils.ShapeRenderer();
    try {
      shapes.setProjectionMatrix(new com.badlogic.gdx.math.Matrix4().setToOrtho2D(0, 0, 128, 128));
      shapes.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled);
      shapes.setColor(Color.YELLOW);
      shapes.rect(5, 5, 20, 10);
      shapes.end();
      p = Pixmap.createFromFrameBuffer(0, 0, 128, 128);
      check(
          red(p.getPixel(10, 10)) > 240 && blue(p.getPixel(10, 10)) < 5,
          "viewmodelAndHudRemainVisible");
      p.dispose();
    } finally {
      shapes.dispose();
    }
    var cycle =
        game.createObject("WORLD clock proof").addComponent(new DayNightCycle3D(sun, env, 1));
    game.step(0);
    cycle.setPhase(.5);
    float day = sun.intensity();
    game.pause();
    game.step(.05f);
    check(cycle.phase() == .5, "dayNightPauseClock");
    game.resume();
    game.step(.05f);
    check(cycle.phase() > .54 && cycle.phase() < .56, "dayNight WORLD resumes");
    Pixmap face = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
    face.setColor(Color.CYAN);
    face.fill();
    Cubemap cube = new Cubemap(face, face, face, face, face, face);
    face.dispose();
    try {
      var sky = org.javity.render.sky.SkySettings.cubemap(cube);
      env.setSky(sky);
      cycle.setPhase(0);
      check(env.sky() == sky && sun.intensity() < day, "cubemap not replaced by cycle");
    } finally {
      env.clearSky();
      cycle.getGameObject().destroy();
      game.step(0);
      cube.dispose();
    }
  }

  private void verifyRelink(GdxRenderer renderer, Game game, Camera3D camera) {
    try {
      Pixmap before = image(renderer, game, camera);
      Object provider = field(renderer, "modelShaders");
      var shaders = (com.badlogic.gdx.utils.Array<?>) field(provider, "shaders");
      java.util.List<com.badlogic.gdx.graphics.glutils.ShaderProgram> programs =
          new java.util.ArrayList<>();
      for (Object shader : shaders)
        programs.add(((com.badlogic.gdx.graphics.g3d.shaders.BaseShader) shader).program);
      var selected =
          programs.stream()
              .filter(p -> Gdx.gl.glGetUniformLocation(p.getHandle(), "u_sceneFogRange") >= 0)
              .findFirst()
              .orElseThrow();
      int old = Gdx.gl.glGetUniformLocation(selected.getHandle(), "u_sceneFogRange");
      GL20 original = Gdx.gl, original20 = Gdx.gl20;
      GL30 original30 = Gdx.gl30;
      GL20 proxy =
          (GL20)
              java.lang.reflect.Proxy.newProxyInstance(
                  GL20.class.getClassLoader(),
                  new Class<?>[] {original30 == null ? GL20.class : GL30.class},
                  (o, m, args) -> {
                    if (m.getName().equals("glShaderSource")
                        && ((String) args[1]).contains("v_scenePosition")) {
                      String source = (String) args[1];
                      args[1] =
                          "#extension GL_ARB_explicit_uniform_location : require\n"
                              + "#extension GL_ARB_explicit_attrib_location : require\n"
                              + source
                                  .replace(
                                      "uniform vec3 u_sceneAmbient;",
                                      "layout(location=200) uniform vec3 u_sceneAmbient;")
                                  .replace(
                                      "uniform vec2 u_sceneFogRange;",
                                      "layout(location=210) uniform vec2 u_sceneFogRange;");
                    }
                    try {
                      return m.invoke(original30 == null ? original20 : original30, args);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                      throw e.getCause();
                    }
                  });
      // Real driver relink with forced moved uniforms; refresh libGDX alias maps per host contract.
      for (var program : programs) {
        Gdx.gl.glDeleteProgram(program.getHandle());
        Gdx.gl.glDeleteShader((int) field(program, "vertexShaderHandle"));
        Gdx.gl.glDeleteShader((int) field(program, "fragmentShaderHandle"));
      }
      Gdx.gl = proxy;
      Gdx.gl20 = proxy;
      if (original30 != null) Gdx.gl30 = (GL30) proxy;
      try {
        com.badlogic.gdx.graphics.glutils.ShaderProgram.invalidateAllShaderPrograms(Gdx.app);
      } finally {
        Gdx.gl = original;
        Gdx.gl20 = original20;
        Gdx.gl30 = original30;
      }
      for (var program : programs) {
        check(program.isCompiled(), "scene managed relink compiled: " + program.getLog());
        for (String name :
            java.util.List.of(
                "uniforms",
                "uniformTypes",
                "uniformSizes",
                "attributes",
                "attributeTypes",
                "attributeSizes"))
          ((com.badlogic.gdx.utils.ObjectIntMap<?>) field(program, name)).clear();
        for (String name : java.util.List.of("fetchAttributes", "fetchUniforms")) {
          var m = com.badlogic.gdx.graphics.glutils.ShaderProgram.class.getDeclaredMethod(name);
          m.setAccessible(true);
          m.invoke(program);
        }
      }
      int moved = Gdx.gl.glGetUniformLocation(selected.getHandle(), "u_sceneFogRange");
      check(
          old != moved && moved == 210,
          "newUniformLocationsRefreshAfterRelink " + old + " -> " + moved);
      renderer.restoreContextShaderBindings();
      Pixmap after = image(renderer, game, camera);
      int changed = difference(before, after);
      before.dispose();
      after.dispose();
      check(changed == 0, "scene light/fog pixels retained after real relink");
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }

  private static Object field(Object target, String name) throws ReflectiveOperationException {
    for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
      try {
        var f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
      } catch (NoSuchFieldException ignored) {
      }
    }
    throw new NoSuchFieldException(name);
  }

  private void verifySkinned(
      GdxRenderer renderer, Game game, Camera3D camera, RenderEnvironment3D env) {
    env.setAmbient(1, 1, 1);
    try (var fixture = GeneratedAvatarModel.create()) {
      var object = game.createObject("scene skinned texture");
      object.addComponent(new Transform3D()).scale.set(1.8f, 1.8f, 1.8f);
      var model = object.addComponent(new ModelRenderer(fixture.model));
      object.addComponent(new InstancedModel());
      game.step(0);
      Pixmap first = image(renderer, game, camera);
      model.animation.setAnimation("arm-sweep", -1);
      model.animation.update(.5f);
      Pixmap second = image(renderer, game, camera);
      int changed = difference(first, second);
      first.dispose();
      second.dispose();
      check(changed > 100, "scene skinned textured pose changes pixels=" + changed);
      try (var binding =
          org.javity.avatar.AvatarDefinition.builder()
              .socket("arm", "root/left-arm", new com.badlogic.gdx.math.Matrix4())
              .build()
              .bind(model)) {
        var base = new com.badlogic.gdx.math.Matrix4().translate(1, 2, 3);
        float[] original = base.val.clone();
        var bone = model.instance.getNode("left-arm", true).globalTransform.cpy();
        var shapes = new com.badlogic.gdx.graphics.glutils.ShapeRenderer();
        try {
          shapes.setProjectionMatrix(camera.camera.combined);
          shapes.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line);
          org.javity.ui.inspector.SocketGizmoAdapter.draw(
              shapes, camera.camera, binding, "arm", base);
          org.javity.ui.inspector.LightGizmoAdapter.draw(
              shapes, camera.camera, game.getObjectsWith(Light3D.class).getFirst());
          shapes.end();
          check(
              java.util.Arrays.equals(original, base.val)
                  && java.util.Arrays.equals(
                      bone.val, model.instance.getNode("left-arm", true).globalTransform.val),
              "socket and light gizmos borrow canonical pose without mutation");
        } finally {
          shapes.dispose();
        }
      }
      Pixmap mask = new Pixmap(2, 2, Pixmap.Format.RGBA8888);
      mask.setBlending(Pixmap.Blending.None);
      mask.setColor(Color.WHITE);
      mask.fill();
      mask.drawPixel(0, 0, 0xffffff00);
      mask.drawPixel(0, 1, 0xffffff00);
      Texture texture = new Texture(mask);
      mask.dispose();
      try {
        for (Material material : model.instance.materials)
          material.set(
              TextureAttribute.createDiffuse(texture),
              FloatAttribute.createAlphaTest(.5f),
              new BlendingAttribute(false, GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 1));
        Pixmap cut = alphaImage(renderer, game, camera);
        int cutCount = count(cut);
        for (Material material : model.instance.materials)
          material.remove(FloatAttribute.AlphaTest);
        // Disable blending remains false: without alpha-test, transparent texels still write RGB.
        Pixmap solid = alphaImage(renderer, game, camera);
        int solidCount = count(solid);
        int holes = 0;
        for (int y = 0; y < 128; y++)
          for (int x = 0; x < 128; x++) {
            int a = cut.getPixel(x, y), b = solid.getPixel(x, y);
            if (((a >>> 16) & 255) > 240 && red(a) < 5 && ((b >>> 16) & 255) < 5 && red(b) < 5)
              holes++;
          }
        cut.dispose();
        solid.dispose();
        check(
            cutCount > 100 && solidCount > 100 && holes > 100,
            "scene skinned alpha-test / opaque="
                + cutCount
                + " transparent holes revealing later background="
                + holes);
      } finally {
        object.destroy();
        game.step(0);
        texture.dispose();
      }
    }
  }

  private static Pixmap alphaImage(GdxRenderer renderer, Game game, Camera3D camera) {
    Pixmap first = image(renderer, game, camera);
    first.dispose();
    var shapes = new com.badlogic.gdx.graphics.glutils.ShapeRenderer();
    try {
      Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
      Gdx.gl.glDepthFunc(GL20.GL_LESS);
      Gdx.gl.glDepthMask(true);
      shapes.setProjectionMatrix(camera.camera.combined);
      shapes.setTransformMatrix(new com.badlogic.gdx.math.Matrix4().translate(0, 0, -1));
      shapes.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled);
      shapes.setColor(Color.GREEN);
      shapes.rect(-3, -3, 6, 6);
      shapes.end();
      Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
      return Pixmap.createFromFrameBuffer(0, 0, 128, 128);
    } finally {
      shapes.dispose();
    }
  }

  private static Model box(boolean normal) {
    return new ModelBuilder()
        .createBox(
            1,
            1,
            1,
            new Material(ColorAttribute.createDiffuse(Color.WHITE)),
            VertexAttributes.Usage.Position | (normal ? VertexAttributes.Usage.Normal : 0));
  }

  private static Camera3D camera(Game game) {
    var c = game.createObject("camera").addComponent(new Camera3D(50));
    c.camera.position.set(0, 0, 4);
    c.camera.lookAt(0, 0, 0);
    c.camera.far = 30;
    c.camera.update();
    return c;
  }

  private static Pixmap image(GdxRenderer renderer, Game game, Camera3D camera) {
    Gdx.gl.glClearColor(0, 0, 0, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    renderer.renderWorld(game, camera, null);
    return Pixmap.createFromFrameBuffer(0, 0, 128, 128);
  }

  private static int draw(GdxRenderer renderer, Game game, Camera3D camera) {
    Pixmap p = image(renderer, game, camera);
    try {
      return p.getPixel(64, 64);
    } finally {
      p.dispose();
    }
  }

  private static int red(int c) {
    return c >>> 24;
  }

  private static int blue(int c) {
    return (c >>> 8) & 255;
  }

  private static int difference(Pixmap a, Pixmap b) {
    int n = 0;
    for (int y = 0; y < 128; y++)
      for (int x = 0; x < 128; x++) if (a.getPixel(x, y) != b.getPixel(x, y)) n++;
    return n;
  }

  private static int count(Pixmap a) {
    int n = 0;
    for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) if (red(a.getPixel(x, y)) > 20) n++;
    return n;
  }
}
