package org.javity.demo;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.*;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.BufferUtils;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import org.javity.core.*;
import org.javity.render.*;
import org.javity.render.lighting.*;

/**
 * Foundation gate: compile/draw the scene variant on real GL20/GL30; detailed pixel controls
 * follow.
 */
public final class SceneLightingGpuSmokeLauncher {
  public static void main(String[] args) throws Exception {
    boolean gl20 = args.length > 0;
    var config = new Lwjgl3ApplicationConfiguration();
    config.setTitle("Scene lighting shader smoke");
    config.setWindowedMode(64, 64);
    config.setInitialVisible(false);
    config.setOpenGLEmulation(
        gl20
            ? Lwjgl3ApplicationConfiguration.GLEmulation.GL20
            : Lwjgl3ApplicationConfiguration.GLEmulation.GL30,
        gl20 ? 2 : 3,
        gl20 ? 0 : 2);
    Probe probe = new Probe();
    new Lwjgl3Application(probe, config);
    if (probe.failure != null)
      throw new AssertionError("Scene shader foundation failed", probe.failure);
    System.out.println(
        "sceneLightingShaderSmoke: PASS "
            + (gl20 ? "GL20" : "GL30")
            + " (ordinary constructor spot draw, scene/legacy switch, fog variant, instance"
            + " fallback)");
    if (!gl20) {
      int result =
          new ProcessBuilder(
                  Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                  "--enable-native-access=ALL-UNNAMED",
                  "-cp",
                  System.getProperty("java.class.path"),
                  SceneLightingGpuSmokeLauncher.class.getName(),
                  "--gl20")
              .inheritIO()
              .start()
              .waitFor();
      if (result != 0) throw new AssertionError("GL20 child failed: " + result);
    }
  }

  private static final class Probe extends ApplicationAdapter {
    Throwable failure;

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

    private void run() {
      GdxRenderer renderer = new GdxRenderer();
      Model model =
          new ModelBuilder()
              .createBox(
                  1,
                  1,
                  1,
                  new Material(ColorAttribute.createDiffuse(1, 1, 1, 1)),
                  VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
      FrameBuffer fbo = new FrameBuffer(Pixmap.Format.RGBA8888, 64, 64, true);
      try (Game scene = new Game();
          Game legacy = new Game()) {
        RenderEnvironment3D environment = scene.addSystem(new RenderEnvironment3D());
        Camera3D camera = populate(scene, model), legacyCamera = populate(legacy, model);
        GameObject light = scene.createObject("spot");
        light.addComponent(new Transform3D()).position.set(0, 0, 3);
        light.addComponent(new SpotLight3D()).setIntensity(2);
        scene.start();
        legacy.start();
        renderer.resize(64, 64);
        fbo.begin();
        try {
          draw(renderer, scene, camera);
          ByteBuffer pixel = BufferUtils.newByteBuffer(4);
          Gdx.gl.glReadPixels(32, 32, 1, 1, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, pixel);
          if ((pixel.get(0) & 255) < 30)
            throw new AssertionError("Ordinary renderer constructor did not draw scene spotlight");
          if (renderer.instanceStats().instanced() != 0)
            throw new AssertionError("Scene marker must use ordinary fallback");
          draw(renderer, legacy, legacyCamera);
          environment.setFog(.2f, .3f, .4f, 1, 10);
          draw(renderer, scene, camera);
          environment.clearFog();
          light.setActive(false);
          draw(renderer, scene, camera);
          Gdx.gl.glReadPixels(32, 32, 1, 1, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, pixel);
          if ((pixel.get(0) & 255) > 2) throw new AssertionError("Removed spot retained light");
          if (Gdx.gl.glGetError() != GL20.GL_NO_ERROR) throw new AssertionError("OpenGL error");
        } finally {
          fbo.end();
        }
      } finally {
        renderer.dispose();
        fbo.dispose();
        model.dispose();
      }
    }

    private Camera3D populate(Game game, Model model) {
      Camera3D camera = game.createObject("camera").addComponent(new Camera3D(50));
      camera.camera.position.set(0, 0, 3);
      camera.camera.lookAt(0, 0, 0);
      camera.camera.near = .1f;
      camera.camera.far = 20;
      camera.camera.update();
      GameObject object = game.createObject("instance candidate");
      object.addComponent(new Transform3D());
      object.addComponent(new ModelRenderer(model));
      object.addComponent(new InstancedModel());
      return camera;
    }

    private void draw(GdxRenderer renderer, Game game, Camera3D camera) {
      Gdx.gl.glClearColor(0, 0, 0, 1);
      Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
      renderer.renderWorld(game, camera, null);
    }
  }
}
