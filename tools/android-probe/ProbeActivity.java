package org.javity.androidprobe;

import android.os.Bundle;
import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.android.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import org.javity.core.*;
import org.javity.core.Game;
import org.javity.render.*;
import org.javity.voxel.*;
import org.javity.voxel.controller.VoxelFpsController;
import org.javity.voxel.gpu.VoxelChunkRenderer;

/** Diagnostic only: deliberately uses the existing engine jars without porting desktop modules. */
public final class ProbeActivity extends AndroidApplication {
  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
    config.useAccelerometer = false;
    config.useCompass = false;
    config.disableAudio = true;
    initialize(new Probe(getIntent().getStringExtra("mode")), config);
  }

  private static final class Probe extends ApplicationAdapter {
    private final String mode;
    private Game game;
    private GdxRenderer renderer;
    private SpriteBatch batch;
    private BitmapFont font;
    private Model model;
    private Texture atlas;
    private VoxelChunkRenderer chunks;
    private Camera3D camera;
    private String status = "STARTING";
    private int frames;
    private boolean failed;

    Probe(String mode) {
      this.mode = mode == null ? "basic" : mode;
    }

    @Override
    public void create() {
      try {
        Gdx.app.log(
            "JavityAndroidProbe",
            "CREATE mode=" + mode + " GL=" + Gdx.gl.glGetString(GL20.GL_VERSION));
        batch = new SpriteBatch();
        font = new BitmapFont();
        font.getData().setScale(1.6f);
        Game test = new Game();
        GameObject mover = test.createObject("controller");
        CharacterController2D controller =
            mover.addComponent(new CharacterController2D().setSpeed(2));
        controller.move(1, 0);
        test.start();
        for (int i = 0; i < 10; i++) test.step(.1f);
        if (Math.abs(mover.transform.position.x - 2) > 1e-4f
            || !controller.hasComponent(CharacterController2D.class))
          throw new AssertionError("2D controller/component lookup");
        test.close();
        Gdx.app.log("JavityAndroidProbe", "CPU_COMPONENTS_PASS Java25DEX");
        game = new Game();
        renderer = new GdxRenderer();
        camera = game.createObject("camera").addComponent(new Camera3D(67));
        camera.camera.position.set(7, 6, 10);
        camera.camera.lookAt(2, 1, 2);
        camera.camera.near = .1f;
        camera.camera.far = 100;
        if (mode.equals("voxels")) createVoxels();
        else {
          model =
              new ModelBuilder()
                  .createBox(
                      2,
                      2,
                      2,
                      new Material(ColorAttribute.createDiffuse(Color.CYAN)),
                      VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
          GameObject cube = game.createObject("cube");
          cube.addComponent(new Transform3D());
          cube.addComponent(new ModelRenderer(model));
          if (mode.equals("shapes")) {
            ShapeRenderer2D shape = ShapeRenderer2D.rectangle(220, 100);
            shape.antiAlias = true;
            shape.cornerRadius = 20;
            shape.color.set(Color.ORANGE);
            game.createObject("rectangle").setPosition(300, 250).addComponent(shape);
          } else {
            Pixmap pixel = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            pixel.setColor(Color.WHITE);
            pixel.fill();
            atlas = new Texture(pixel);
            pixel.dispose();
            SpriteRenderer2D sprite = new SpriteRenderer2D(new TextureRegion(atlas));
            sprite.width = 220;
            sprite.height = 100;
            sprite.tint.set(Color.ORANGE);
            game.createObject("sprite").setPosition(300, 250).addComponent(sprite);
          }
        }
        if (!mode.equals("voxels")) game.start();
        status = "READY " + mode;
      } catch (Throwable failure) {
        reportFailure(failure);
      }
    }

    private void createVoxels() {
      BlockRegistry registry = new BlockRegistry();
      int stone = registry.register(new BlockDefinition("stone", 2));
      VoxelWorld world = new VoxelWorld(registry, 1);
      world.loadChunk(new ChunkPos(0, 0, 0));
      for (int z = 0; z < 8; z++) for (int x = 0; x < 8; x++) world.setBlock(x, 0, z, stone);
      world.setBlock(3, 1, 3, stone);
      world.setBlock(3, 2, 3, stone);
      world.setBlock(4, 1, 3, stone);
      GameObject player = game.createObject("FPS");
      Transform3D feet = player.addComponent(new Transform3D());
      feet.position.set(5.5f, 1, 5.5f);
      VoxelFpsController fps = player.addComponent(new VoxelFpsController(world));
      game.start();
      for (int i = 0; i < 30; i++) game.step(1f / 60);
      if (!fps.isGrounded()) throw new AssertionError("FPS ground");
      fps.setJump(true);
      game.step(1f / 60);
      if (fps.getVerticalVelocity() <= 0) throw new AssertionError("FPS jump");
      fps.setJump(false);
      for (int i = 0; i < 120; i++) game.step(1f / 60);
      if (!fps.isGrounded()) throw new AssertionError("FPS landing");
      Gdx.app.log("JavityAndroidProbe", "CPU_VOXEL_FPS_PASS");
      atlas = new Texture(Gdx.files.internal("voxels/atlas.png"));
      chunks = new VoxelChunkRenderer(game, world, atlas, new AtlasLayout(80, 40, 16, 2, 4, 2));
    }

    @Override
    public void resize(int w, int h) {
      if (renderer != null) renderer.resize(w, h);
    }

    @Override
    public void render() {
      Gdx.gl.glClearColor(failed ? .35f : .04f, .08f, .15f, 1);
      Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
      if (!failed && game != null)
        try {
          game.step(1f / 60);
          if (chunks != null) {
            camera.camera.update();
            chunks.update(camera.camera, 1);
          }
          renderer.render(game);
          if (++frames == 60) {
            int error = Gdx.gl.glGetError();
            if (error != 0) throw new AssertionError("GL error " + error);
            if (chunks != null && chunks.faceCount() == 0)
              throw new AssertionError("No voxel mesh");
            status = "PASS " + mode + " / 60 frames / GL0";
            Gdx.app.log(
                "JavityAndroidProbe",
                "GPU_PASS mode="
                    + mode
                    + " frames=60 faces="
                    + (chunks == null ? 0 : chunks.faceCount()));
          }
        } catch (Throwable failure) {
          reportFailure(failure);
        }
      if (batch != null && font != null) {
        batch
            .getProjectionMatrix()
            .setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        batch.begin();
        font.draw(batch, "JAVITY / ANDROID 15\n" + status, 24, Gdx.graphics.getHeight() - 64);
        batch.end();
      }
    }

    private void reportFailure(Throwable t) {
      failed = true;
      status = "FAIL " + mode + " / " + t.getClass().getSimpleName();
      Gdx.app.error("JavityAndroidProbe", status, t);
    }

    @Override
    public void pause() {
      Gdx.app.log("JavityAndroidProbe", "PAUSE " + mode);
    }

    @Override
    public void resume() {
      Gdx.app.log("JavityAndroidProbe", "RESUME " + mode);
    }

    @Override
    public void dispose() {
      if (chunks != null) chunks.close();
      if (game != null) game.close();
      if (renderer != null) renderer.dispose();
      if (model != null) model.dispose();
      if (atlas != null) atlas.dispose();
      if (batch != null) batch.dispose();
      if (font != null) font.dispose();
      Gdx.app.log("JavityAndroidProbe", "DISPOSE " + mode);
    }
  }
}
