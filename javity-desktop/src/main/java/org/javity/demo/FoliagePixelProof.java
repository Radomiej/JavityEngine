package org.javity.demo;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.*;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.ScreenUtils;
import java.util.Arrays;
import java.util.List;
import org.javity.core.*;
import org.javity.render.*;
import org.javity.render.foliage.*;
import org.javity.render.lighting.*;

/** Real framebuffer controls run by both GL20 and GL30 FoliageDemo smokes. */
final class FoliagePixelProof {
  private static final int SIZE = 128;

  private FoliagePixelProof() {}

  static void run() {
    Pixmap pixels = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
    pixels.setColor(0, 0, 0, 0);
    pixels.fill();
    pixels.setColor(.25f, .85f, .12f, 1);
    pixels.fillCircle(32, 32, 28);
    pixels.setColor(0, 0, 0, 0);
    pixels.fillCircle(32, 32, 12);
    Texture texture = new Texture(pixels);
    pixels.dispose();
    texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
    var region = List.of(new FoliageAtlas.Region(0, 0, 1, 1));
    FoliageAtlas atlas = new FoliageAtlas(texture, region, region);
    GdxRenderer renderer = new GdxRenderer();
    FrameBuffer target = new FrameBuffer(Pixmap.Format.RGBA8888, SIZE, SIZE, true);
    Model sentinel =
        new ModelBuilder()
            .createBox(
                20,
                20,
                .05f,
                new Material(ColorAttribute.createDiffuse(1, 0, 0, 1)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
    ModelInstance back = new ModelInstance(sentinel), front = new ModelInstance(sentinel);
    back.transform.setToTranslation(0, .5f, -2);
    front.transform.setToTranslation(0, .5f, 2);
    ModelBatch sentinels = new ModelBatch();
    GL20 originalGl = Gdx.gl20;
    Physics3DGlBufferTracker buffers = new Physics3DGlBufferTracker(originalGl);
    buffers.install();
    try (Game game = new Game();
        Game empty = new Game()) {
      RenderEnvironment3D env = game.addSystem(new RenderEnvironment3D());
      env.setAmbient(1, 1, 1);
      Camera3D camera = game.createObject("pixel camera").addComponent(new Camera3D(45));
      camera.camera.position.set(0, .55f, 3);
      camera.camera.lookAt(0, .55f, 0);
      camera.camera.near = .1f;
      camera.camera.far = 20;
      camera.camera.viewportWidth = SIZE;
      camera.camera.viewportHeight = SIZE;
      camera.camera.update();
      GameObject owner = game.createObject("cutout patch");
      Transform3D transform = owner.addComponent(new Transform3D());
      FoliagePatch3D patch =
          owner.addComponent(
              FoliagePatch3D.grass(atlas).area(.01f, .01f).count(1).scaleRange(1.8f, 1.8f));
      GameObject sunObject = game.createObject("sun");
      sunObject.addComponent(new Transform3D()).rotation.setFromAxis(1, 0, 0, -90);
      DirectionalLight3D sun = sunObject.addComponent(new DirectionalLight3D());
      sun.setEnabled(false);
      GameObject pointObject = game.createObject("point");
      pointObject.addComponent(new Transform3D()).position.set(0, 2, 2);
      PointLight3D point = pointObject.addComponent(new PointLight3D());
      point.setRange(10);
      point.setIntensity(5);
      point.setEnabled(false);
      game.start();
      empty.start();
      game.step(0);
      renderer.resize(SIZE, SIZE);
      target.begin();
      try {
        byte[] day = draw(renderer, game, camera, sentinels, back);
        int green = green(day), red = red(day);
        require(green > 70, "cutout opaque silhouettes missing: " + green);
        require(red > SIZE * SIZE / 2, "cutout gaps did not reveal red sentinel: " + red);
        var firstBuffers = buffers.liveGenerated(0, buffers.generationCount());
        require(firstBuffers.size() >= 2, "foliage GPU buffers were not allocated/bound");
        long builds = renderer.foliageStats().builds();
        require(renderer.foliageStats().patches() == 1, "patch cache missing");
        byte[] again = draw(renderer, game, camera, sentinels, back);
        require(Arrays.equals(day, again), "static repeated frame changed pixels");
        require(renderer.foliageStats().builds() == builds, "repeated frame rebuilt geometry");
        byte[] hidden = draw(renderer, game, camera, sentinels, front);
        require(green(hidden) == 0, "foreground geometry did not occlude foliage");
        camera.camera.position.z = -3;
        camera.camera.lookAt(0, .55f, 0);
        camera.camera.update();
        byte[] rear = draw(renderer, game, camera, sentinels, null);
        require(green(rear) > 70, "back-facing foliage vanished");
        camera.camera.position.z = 3;
        camera.camera.lookAt(0, .55f, 0);
        camera.camera.update();
        env.setAmbient(0, 0, 0);
        byte[] dark = draw(renderer, game, camera, sentinels, back);
        require(green(dark) < green / 5, "scene ambient did not affect foliage");
        sun.setEnabled(true);
        sun.setIntensity(1.5f);
        byte[] direct = draw(renderer, game, camera, sentinels, back);
        require(green(direct) > 50, "directional light did not light foliage");
        sun.setEnabled(false);
        point.setEnabled(true);
        byte[] local = draw(renderer, game, camera, sentinels, back);
        require(green(local) > 50, "point light did not light foliage");
        point.setEnabled(false);
        env.setAmbient(1, 1, 1);
        env.setFog(0, 0, 1, 0, .2f);
        byte[] fog = draw(renderer, game, camera, sentinels, back);
        require(blue(fog) > 50 && green(fog) < green / 5, "scene fog did not cover foliage");
        env.clearFog();
        transform.position.x = 10;
        require(
            green(draw(renderer, game, camera, sentinels, back)) == 0,
            "transform/culling retained old foliage pixels");
        require(renderer.foliageStats().builds() == builds, "transform-only edit rebuilt geometry");
        int revisionDeletionStart = buffers.deletionCount();
        int replacementGenerationStart = buffers.generationCount();
        transform.position.x = 0;
        patch.seed(45);
        draw(renderer, game, camera, sentinels, back);
        require(
            renderer.foliageStats().builds() == builds + 1,
            "revision did not rebuild exactly once");
        requireDeleted(
            buffers, firstBuffers, revisionDeletionStart, "foliage revision replacement");
        var replacementBuffers =
            buffers.liveGenerated(replacementGenerationStart, buffers.generationCount());
        int removalDeletionStart = buffers.deletionCount();
        owner.removeComponent(patch);
        game.step(0);
        require(
            green(draw(renderer, game, camera, sentinels, back)) == 0,
            "removed foliage retained pixels");
        require(renderer.foliageStats().patches() == 0, "removed cache survived");
        requireDeleted(buffers, replacementBuffers, removalDeletionStart, "foliage removal");
        int readdGenerationStart = buffers.generationCount();
        owner.addComponent(FoliagePatch3D.bushes(atlas).area(.1f, .1f).count(2));
        game.step(0);
        draw(renderer, game, camera, sentinels, back);
        require(renderer.foliageStats().patches() == 1, "re-added patch did not build");
        var readdBuffers = buffers.liveGenerated(readdGenerationStart, buffers.generationCount());
        int sceneDeletionStart = buffers.deletionCount();
        renderer.render(empty);
        require(
            renderer.foliageStats().patches() == 0, "scene switch without camera retained cache");
        requireDeleted(
            buffers, readdBuffers, sceneDeletionStart, "foliage no-camera scene transition");
        require(
            Gdx.gl.glIsTexture(texture.getTextureObjectHandle()), "borrowed atlas was disposed");
        require(Gdx.gl.glGetError() == GL20.GL_NO_ERROR, "pixel proof GL error");
        System.out.println(
            "FoliagePixelProof: PASS cutout gaps/opaque depth/front"
                + " occlusion/front-back/ambient/directional/point/fog/revision/transform/remove/readd/no-camera/GPU"
                + " buffer deletion/borrowed texture; opaque="
                + green
                + " gaps="
                + red);
      } finally {
        target.end();
      }
    } finally {
      buffers.restore(originalGl);
      renderer.dispose();
      sentinels.dispose();
      sentinel.dispose();
      target.dispose();
      require(
          Gdx.gl.glIsTexture(texture.getTextureObjectHandle()), "renderer disposed borrowed atlas");
      texture.dispose();
    }
  }

  private static void requireDeleted(
      Physics3DGlBufferTracker tracker, java.util.Set<Integer> ids, int start, String phase) {
    // GL30 vertex buffers delete through the GL30 interface, outside the GL20 event proxy.
    if (Gdx.gl30 == null) tracker.requireDeleted(ids, start, tracker.deletionCount(), phase);
    for (int id : ids) require(!Gdx.gl.glIsBuffer(id), "GPU buffer " + id + " survived " + phase);
  }

  private static byte[] draw(
      GdxRenderer renderer,
      Game game,
      Camera3D camera,
      ModelBatch sentinels,
      ModelInstance sentinel) {
    Gdx.gl.glClearColor(0, 0, 0, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    renderer.renderWorld(game, camera, null);
    if (sentinel != null) {
      sentinels.begin(camera.camera);
      sentinels.render(sentinel);
      sentinels.end();
    }
    return ScreenUtils.getFrameBufferPixels(0, 0, SIZE, SIZE, false);
  }

  private static int green(byte[] p) {
    int n = 0;
    for (int i = 0; i < p.length; i += 4)
      if ((p[i + 1] & 255) > (p[i] & 255) + 20 && (p[i + 1] & 255) > (p[i + 2] & 255) + 20) n++;
    return n;
  }

  private static int red(byte[] p) {
    int n = 0;
    for (int i = 0; i < p.length; i += 4) if ((p[i] & 255) > 180 && (p[i + 1] & 255) < 20) n++;
    return n;
  }

  private static int blue(byte[] p) {
    int n = 0;
    for (int i = 0; i < p.length; i += 4) if ((p[i + 2] & 255) > 180 && (p[i + 1] & 255) < 20) n++;
    return n;
  }

  private static void require(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
}
