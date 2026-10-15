package org.javity.demo;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import java.util.Map;
import org.javity.core.Game;
import org.javity.effects.Clock;
import org.javity.render.GdxRenderer;
import org.javity.render.ShapeMaterial2D;
import org.javity.render.ShapeRenderer2D;
import org.javity.render.ShapeShaderAnimator;
import org.javity.ui.flex.UiTheme;

/** Small interactive gallery; Space pauses WORLD while the UI shader keeps animating. */
public final class ShapeMaterialsDemo extends ApplicationAdapter {
  private final boolean smoke;
  private Game game;
  private GdxRenderer renderer;
  private Stage stage;
  private UiTheme theme;
  private ShapeMaterial2D pulse, stripes;
  private Label status;
  private int frames;
  private float worldAtPause, uiAtPause;
  private Pixmap pausePixels;
  private boolean disposed;

  public ShapeMaterialsDemo(boolean smoke) {
    this.smoke = smoke;
  }

  @Override
  public void create() {
    try {
      if (smoke) ShapeMaterialSmokeProbe.run();
      game = new Game();
      renderer = new GdxRenderer();
      theme = new UiTheme();
      stage = new Stage(new ScreenViewport());
      label("SHAPE LAB", 40, 656, "text-2xl text-cyan-400");
      label(
          "GLSL materials · shared GPU programs · independent uniforms",
          40,
          622,
          "text-base text-white");
      label("LINEAR GRADIENT", 70, 522, "text-sm text-slate-400");
      label("PULSE / UI CLOCK", 435, 522, "text-sm text-slate-400");
      label("MOVING STRIPES / WORLD", 760, 522, "text-sm text-slate-400");
      ShapeRenderer2D gradient = rectangle("gradient", 190, 420, 240, 140);
      gradient.cornerRadius = 24;
      gradient.material =
          ShapeMaterial2D.gradient(Color.valueOf("22d3ee"), Color.valueOf("a78bfa"), .25f);
      pulse = ShapeMaterial2D.pulse(Color.valueOf("fb7185"), Color.valueOf("a78bfa"), .6f);
      var pulseObject = game.createObject("UI pulse").setPosition(540, 420);
      ShapeRenderer2D disk = pulseObject.addComponent(ShapeRenderer2D.circle(76));
      disk.space = ShapeRenderer2D.Space.SCREEN;
      disk.material = pulse;
      pulseObject.addComponent(new ShapeShaderAnimator(pulse, "u_time", 1, Clock.UI));
      stripes =
          ShapeMaterial2D.stripes(Color.valueOf("34d399"), Color.valueOf("0f766e"), .03f, .5f);
      ShapeRenderer2D bands = rectangle("world stripes", 880, 420, 240, 140);
      bands.cornerRadius = 24;
      bands.material = stripes;
      bands
          .getGameObject()
          .addComponent(new ShapeShaderAnimator(stripes, "u_time", 1, Clock.WORLD));

      String colorFunction = "vec4 shapeColor(vec2 local, vec4 tint) { return u_color*tint; }";
      Map<String, ShapeMaterial2D.UniformType> schema =
          Map.of("u_color", ShapeMaterial2D.UniformType.VEC4);
      rectangle("independent red", 220, 185, 120, 80).material =
          ShapeMaterial2D.custom(colorFunction, schema)
              .setColor("u_color", Color.valueOf("fb7185"));
      rectangle("independent green", 440, 185, 120, 80).material =
          ShapeMaterial2D.custom(colorFunction, schema)
              .setColor("u_color", Color.valueOf("34d399"));
      ShapeRenderer2D border = rectangle("custom stroke", 660, 185, 120, 80);
      border.cornerRadius = 18;
      border.strokeWidth = 5;
      border.material =
          ShapeMaterial2D.gradient(Color.valueOf("fbbf24"), Color.valueOf("fb7185"), 0);
      rectangle("default sentinel", 890, 185, 80, 80);
      label("SAME GLSL · SEPARATE VALUES", 110, 110, "text-sm text-slate-400");
      label("SDF STROKE", 605, 110, "text-sm text-slate-400");
      label("DEFAULT", 850, 110, "text-sm text-slate-400");
      status = label("SPACE: pause WORLD — UI pulse keeps running", 40, 40, "text-base text-white");
      game.start();
      resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
      Gdx.input.setInputProcessor(
          new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
              if (keycode != Input.Keys.SPACE) return false;
              if (game.isPaused()) game.resume();
              else game.pause();
              return true;
            }
          });
    } catch (RuntimeException | Error failure) {
      try {
        dispose();
      } catch (RuntimeException | Error close) {
        if (close != failure) failure.addSuppressed(close);
      }
      throw failure;
    }
  }

  private ShapeRenderer2D rectangle(String name, float x, float y, float width, float height) {
    var object = game.createObject(name).setPosition(x, y);
    var shape = object.addComponent(ShapeRenderer2D.rectangle(width, height));
    shape.space = ShapeRenderer2D.Space.SCREEN;
    return shape;
  }

  private Label label(String text, float x, float y, String classes) {
    Label label = theme.label(text, classes);
    label.setPosition(x, y);
    stage.addActor(label);
    return label;
  }

  @Override
  public void render() {
    Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    Gdx.gl.glClearColor(.025f, .035f, .06f, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    float delta = smoke ? 1f / 60f : Gdx.graphics.getDeltaTime();
    game.step(delta);
    renderer.render(game);
    status.setText(
        game.isPaused()
            ? "WORLD PAUSED · UI pulse is still running · SPACE: resume"
            : "SPACE: pause WORLD — UI pulse keeps running");
    stage.act(delta);
    stage.draw();
    frames++;
    if (smoke) verifySmoke();
  }

  private void verifySmoke() {
    if (frames == 20) {
      worldAtPause = stripes.getFloat("u_time");
      uiAtPause = pulse.getFloat("u_time");
      pausePixels = capture();
      game.pause();
    }
    if (frames == 50) {
      if (Math.abs(stripes.getFloat("u_time") - worldAtPause) > .00001f
          || !(pulse.getFloat("u_time") > uiAtPause + .45f))
        throw new IllegalStateException("Shader clocks did not respect WORLD/UI pause");
      Pixmap after = capture();
      try {
        if (differences(pausePixels, after, 470, 610, 350, 490) < 500)
          throw new IllegalStateException(
              "UI pulse uniform advanced without visible pixels changing");
        if (differences(pausePixels, after, 765, 995, 355, 485) != 0)
          throw new IllegalStateException("WORLD stripes changed pixels while paused");
      } finally {
        after.dispose();
        pausePixels.dispose();
        pausePixels = null;
      }
      game.resume();
    }
    if (frames == 90) {
      if (!(stripes.getFloat("u_time") > worldAtPause + .6f))
        throw new IllegalStateException("WORLD material did not resume");
      Pixmap pixels = capture();
      try {
        var file = Gdx.files.local("artifacts/shape-materials/gallery.png");
        file.parent().mkdirs();
        PixmapIO.writePNG(file, pixels, -1, true);
      } finally {
        pixels.dispose();
      }
      if (Gdx.gl.glGetError() != GL20.GL_NO_ERROR)
        throw new IllegalStateException("Material gallery GL error");
      Gdx.app.log(
          "ShapeMaterials",
          "PASS: native shader gallery, GPU ownership, WORLD/UI pause and visible animation");
      Gdx.app.exit();
    }
  }

  private static Pixmap capture() {
    return Pixmap.createFromFrameBuffer(
        0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
  }

  private static int differences(Pixmap a, Pixmap b, int x0, int x1, int y0, int y1) {
    int count = 0;
    for (int y = y0; y < y1; y++)
      for (int x = x0; x < x1; x++) if (a.getPixel(x, y) != b.getPixel(x, y)) count++;
    return count;
  }

  @Override
  public void resize(int width, int height) {
    if (renderer != null) renderer.resize(width, height);
    if (stage != null) stage.getViewport().update(width, height, true);
  }

  @Override
  public void dispose() {
    if (disposed) return;
    disposed = true;
    Throwable failure = null;
    AutoCloseable[] owners = {
      game,
      () -> {
        if (stage != null) stage.dispose();
      },
      () -> {
        if (renderer != null) renderer.dispose();
      },
      () -> {
        if (theme != null) theme.dispose();
      },
      () -> {
        if (pausePixels != null) pausePixels.dispose();
      }
    };
    for (AutoCloseable owner : owners)
      if (owner != null)
        try {
          owner.close();
        } catch (Exception | Error e) {
          if (failure == null) failure = e;
          else failure.addSuppressed(e);
        }
    if (failure instanceof RuntimeException e) throw e;
    if (failure instanceof Error e) throw e;
    if (failure != null) throw new IllegalStateException(failure);
  }
}
