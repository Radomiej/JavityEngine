package org.javity.demo;

import com.badlogic.gdx.*;
import com.badlogic.gdx.audio.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import java.nio.file.Path;
import java.util.*;
import org.javity.audio.AudioSource;
import org.javity.core.*;
import org.javity.core.Game;
import org.javity.input.*;
import org.javity.map.*;
import org.javity.physics2d.*;
import org.javity.render.*;
import org.javity.serialization.*;
import org.javity.services.*;
import org.javity.ui.flex.*;

/** Migrated TestApp patterns: input/move, delayed audio, lifecycle and scene chooser. */
public final class RuntimeDemo extends ApplicationAdapter {
  private final boolean smoke;
  private SceneManager scenes;
  private GdxRenderer renderer;
  private Stage stage;
  private UiTheme theme;
  private Flex root;
  private InputActions actions;
  private PointerPicker picker;
  private InputMultiplexer input;
  private Game game, old;
  private GameObject player, cameraObject;
  private Camera2D camera;
  private Physics2D physics;
  private MapView map;
  private MapRenderer maps;
  private AsyncTileSource tiles;
  private SpriteBatch mapBatch;
  private OrthographicCamera mapCamera;
  private TextButton nextButton;
  private Music music;
  private AudioSource audio;
  private int frames, clicks, timerCalls, created, destroyed;
  private Vector2 paused;

  public RuntimeDemo(boolean smoke) {
    this.smoke = smoke;
  }

  public void create() {
    renderer = new GdxRenderer();
    theme = new UiTheme();
    stage = new Stage(new ScreenViewport());
    scenes = new SceneManager();
    root = new Flex(theme, "flex-col p-6 gap-4");
    root.setFillParent(true);
    stage.addActor(root);
    Flex panel =
        root.add(
            new Flex(theme, "flex-col p-5 gap-4 bg-slate-800 rounded-xl border border-slate-700"),
            "w-80");
    panel.setTouchable(Touchable.enabled);
    panel.add(theme.label("Runtime migration", "text-xl text-cyan-400"), "");
    panel.add(theme.label("WASD / arrows to move", "text-slate-400"), "");
    panel.add(theme.label("Click avatar to play sound", "text-slate-400"), "");
    panel.add(
        theme.button(
            "Pause / resume",
            "bg-blue-600 rounded-lg",
            () -> {
              if (game.isPaused()) {
                game.resume();
                audio.resume();
                music.play();
              } else {
                game.pause();
                audio.pause();
                music.pause();
              }
            }),
        "h-10 w-full");
    nextButton =
        panel.add(
            theme.button(
                "Next scene", "bg-slate-700 rounded-lg", () -> scenes.request(this::newScene)),
            "h-10 w-full");
    panel.add(theme.label("Text · shapes · atlas · audio", "text-slate-400"), "");
    panel.add(theme.label("Prefab / scene-local resources", "text-slate-400"), "");
    try {
      TileByteCache cache =
          new TileByteCache(
              k -> Gdx.files.classpath("runtime/tile.png").readBytes(),
              Path.of("build/runtime-tiles"),
              2,
              8,
              32,
              1000);
      tiles = new AsyncTileSource(cache);
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
    maps = new MapRenderer(tiles, 32);
    mapBatch = new SpriteBatch();
    mapCamera = new OrthographicCamera();
    scenes.request(this::newScene);
    scenes.step(0);
    resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
  }

  private Game newScene() {
    created++;
    game =
        new Game() {
          public void onDestroy() {
            destroyed++;
          }
        };
    SceneAssets assets = game.addSystem(new SceneAssets());
    physics = game.addSystem(new Physics2D(Vector2.Zero, 100));
    TextureAtlas atlas = assets.load("runtime/avatar.atlas", TextureAtlas.class);
    Sound sound = assets.load("runtime/sound.wav", Sound.class);
    music = assets.load("runtime/music.wav", Music.class);
    music.setVolume(.05f);
    music.setLooping(true);
    music.play();
    audio = game.createObject("sound").addComponent(new AudioSource(sound, false));
    audio.setVolume(.05f);
    SceneTimers timers = game.createObject("timers").addComponent(new SceneTimers());
    timers.after(
        .1,
        false,
        () -> {
          timerCalls++;
          audio.play();
        });
    cameraObject =
        game.createObject("camera")
            .setPosition(Gdx.graphics.getWidth() / 2f, Gdx.graphics.getHeight() / 2f);
    camera = cameraObject.addComponent(new Camera2D());
    camera.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    map =
        game.createObject("background map")
            .addComponent(new MapView(new GeoCoordinate(51.4, 21.1), 2));
    map.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    player = game.createObject("avatar").setPosition(630, 340);
    SpriteRenderer2D sprite =
        player.addComponent(new SpriteRenderer2D(atlas.findRegion("avatar", 0)));
    sprite.width = 48;
    sprite.height = 48;
    sprite.layer = 2;
    List<TextureRegion> frames = new ArrayList<>();
    for (var region : atlas.findRegions("avatar")) frames.add(region);
    player.addComponent(new SpriteAnimator(frames, .15f, true));
    player.addComponent(
        new Pickable2D(
            48,
            48,
            p -> {
              clicks++;
              audio.play();
            }));
    actions = new InputActions();
    actions.bind("left", Input.Keys.A, Input.Keys.LEFT);
    actions.bind("right", Input.Keys.D, Input.Keys.RIGHT);
    actions.bind("up", Input.Keys.W, Input.Keys.UP);
    actions.bind("down", Input.Keys.S, Input.Keys.DOWN);
    player.addComponent(
        new Component() {
          public void onUpdate(float dt) {
            getTransform()
                .position
                .add(actions.axis("left", "right") * 90 * dt, actions.axis("down", "up") * 90 * dt);
          }
        });
    var rectangle = game.createObject("rectangle").setPosition(620, 160);
    var square = rectangle.addComponent(ShapeRenderer2D.rectangle(72, 48));
    square.color.set(Color.ROYAL);
    var disk = game.createObject("circle").setPosition(790, 170);
    disk.addComponent(ShapeRenderer2D.circle(26)).color.set(Color.GREEN);
    var ring = game.createObject("outline").setPosition(950, 170);
    var circle = ring.addComponent(ShapeRenderer2D.circle(28));
    circle.strokeWidth = 4;
    circle.color.set(Color.CYAN);
    var line = game.createObject("line").setPosition(540, 255);
    line.addComponent(
            ShapeRenderer2D.polyline(
                List.of(new Vector2(0, 0), new Vector2(90, 35), new Vector2(150, 0)), 5))
        .color
        .set(Color.GOLD);
    game.createObject("world text")
        .setPosition(480, 440)
        .addComponent(new TextRenderer2D(theme.getFont(), "Tekst świata — zażółć gęślą jaźń"));
    var triangle = game.createObject("polygon").setPosition(880, 285);
    triangle.addComponent(
        new PolygonCollider2D(new Vector2(-30, -30), new Vector2(30, -30), new Vector2(0, 30)));
    triangle
        .addComponent(
            ShapeRenderer2D.polygon(
                List.of(new Vector2(-30, -30), new Vector2(30, -30), new Vector2(0, 30))))
        .color
        .set(Color.ORANGE);
    SceneJson schema = PhysicsSceneCodecs.install(SceneJson.standard());
    String document = schema.write(List.of(rectangle));
    var cloned = new Prefab(document).instantiate(game, schema).getFirst();
    cloned.setPosition(520, 165);
    cloned.getComponent(ShapeRenderer2D.class).color.set(Color.PURPLE);
    if (smoke) Gdx.files.local("runtime-prefab.json").writeString(document, false);
    picker = new PointerPicker(game, camera);
    input = new InputMultiplexer(stage, picker, actions);
    Gdx.input.setInputProcessor(input);
    return game;
  }

  public void render() {
    Gdx.gl.glClearColor(.03f, .05f, .09f, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    scenes.step(smoke ? 1f / 60 : Gdx.graphics.getDeltaTime());
    actions.finishFrame();
    mapBatch.setProjectionMatrix(mapCamera.combined);
    mapBatch.begin();
    maps.render(game, map, mapBatch);
    mapBatch.end();
    renderer.render(game);
    stage.act(game.unscaledDeltaTime);
    stage.draw();
    frames++;
    if (smoke) verify();
  }

  private void verify() {
    if (frames == 10) actions.keyDown(Input.Keys.D);
    if (frames == 20) {
      actions.keyUp(Input.Keys.D);
      if (player.transform.position.x <= 640 || !music.isPlaying())
        throw new IllegalStateException("Movement or native audio failed");
    }
    if (frames == 30) {
      game.pause();
      paused = player.transform.position.cpy();
    }
    if (frames == 35) {
      if (!paused.equals(player.transform.position))
        throw new IllegalStateException("Paused movement changed");
      game.resume();
    }
    if (frames == 40) {
      Vector2 p = camera.worldToScreen(player.transform.position.x, player.transform.position.y);
      if (!input.touchDown(Math.round(p.x), Math.round(p.y), 0, Input.Buttons.LEFT))
        throw new IllegalStateException("World pointer not captured");
      input.touchUp(Math.round(p.x), Math.round(p.y), 0, Input.Buttons.LEFT);
      if (clicks != 1) throw new IllegalStateException("Overlay blocked world click");
    }
    if (frames == 50
        && physics.raycast(new Vector2(800, 285), new Vector2(960, 285), 65535, false).isEmpty())
      throw new IllegalStateException("Native polygon ray failed");
    if (frames == 70) Gdx.graphics.setWindowedMode(1000, 680);
    if (frames == 90) {
      old = game;
      Vector2 p =
          stage.stageToScreenCoordinates(
              nextButton.localToStageCoordinates(
                  new Vector2(nextButton.getWidth() / 2, nextButton.getHeight() / 2)));
      input.touchDown(Math.round(p.x), Math.round(p.y), 0, Input.Buttons.LEFT);
      input.touchUp(Math.round(p.x), Math.round(p.y), 0, Input.Buttons.LEFT);
    }
    if (frames == 91 && (!old.isDisposed() || game == old))
      throw new IllegalStateException("UI scene switch failed");
    if (frames == 120) {
      if (created != 2 || destroyed != 1 || timerCalls != 2 || maps.getCachedTileCount() == 0)
        throw new IllegalStateException("Scene/timer/async tile failed");
      Pixmap pixels =
          Pixmap.createFromFrameBuffer(
              0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
      try {
        Color green = pixel(pixels, 790, 170), blue = pixel(pixels, 620, 160);
        if (green.g < .8f || blue.b < .7f) throw new IllegalStateException("Shape pixels missing");
        PixmapIO.writePNG(Gdx.files.local("runtime-smoke.png"), pixels, -1, true);
      } finally {
        pixels.dispose();
      }
      if (Gdx.gl.glGetError() != GL20.GL_NO_ERROR)
        throw new IllegalStateException("Runtime GL error");
      Gdx.app.log(
          "JavityRuntimeSmoke",
          "PASS: 120 frames; input/UI picking, atlas animation, shapes/text, native Sound/Music,"
              + " timers, prefab, polygon/raycast, async tiles, resize, 2 scenes; clicks="
              + clicks);
      Gdx.app.exit();
    }
  }

  private Color pixel(Pixmap image, float x, float y) {
    Vector2 p = camera.worldToScreen(x, y);
    return new Color(image.getPixel(Math.round(p.x), Math.round(camera.getHeight() - p.y)));
  }

  public void resize(int width, int height) {
    if (renderer != null) renderer.resize(width, height);
    if (camera != null) {
      camera.resize(width, height);
      cameraObject.setPosition(width / 2f, height / 2f);
    }
    if (map != null) map.resize(width, height);
    if (mapCamera != null) {
      mapCamera.setToOrtho(false, width, height);
      mapCamera.update();
    }
    if (stage != null) stage.getViewport().update(width, height, true);
  }

  public void pause() {
    if (actions != null) actions.releaseAll();
    if (picker != null) picker.cancelAll();
  }

  public void dispose() {
    scenes.close();
    maps.dispose();
    tiles.close();
    mapBatch.dispose();
    root.dispose();
    stage.dispose();
    theme.dispose();
    renderer.dispose();
    if (smoke && destroyed != created)
      throw new IllegalStateException("Scene resources not closed");
  }
}
