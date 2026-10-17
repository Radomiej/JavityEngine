package org.javity.demo;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import java.util.List;
import org.javity.core.Game;
import org.javity.map.*;
import org.javity.ui.flex.*;

/** Offline synthetic tiles, deliberately not geographic street data. */
public final class MapDemo extends ApplicationAdapter {
  private final boolean smoke;
  private Game game;
  private MapView view;
  private MapMarker marker;
  private MapRenderer renderer;
  private SpriteBatch batch;
  private OrthographicCamera camera;
  private Stage stage;
  private InputMultiplexer inputMux;
  private UiTheme theme;
  private Flex root;
  private int frames, loads, stableLoads, dragX, dragY;
  private final GeoCoordinate initial = new GeoCoordinate(51.4027, 21.1471);

  public MapDemo(boolean smoke) {
    this.smoke = smoke;
  }

  public void create() {
    game = new Game();
    view = game.createObject("Map").addComponent(new MapView(initial, 12));
    marker = game.createObject("Radom marker").addComponent(new MapMarker(initial));
    marker.color.set(Color.SCARLET);
    marker.setSize(18);
    game.createObject("Route")
        .addComponent(
            new MapRoute(
                List.of(
                    new GeoCoordinate(51.398, 21.13), initial, new GeoCoordinate(51.41, 21.16))));
    renderer = new MapRenderer(this::tile, 64);
    batch = new SpriteBatch();
    camera = new OrthographicCamera();
    theme = new UiTheme();
    for (char c : "ĄĆĘŁŃÓŚŹŻąćęłńóśźż".toCharArray())
      if (!theme.getFont().getData().hasGlyph(c))
        throw new IllegalStateException("Missing Polish glyph: " + c);
    stage = new Stage(new ScreenViewport());
    root = new Flex(theme, "flex-col p-6 gap-3");
    root.setFillParent(true);
    stage.addActor(root);
    Flex panel =
        root.add(
            new Flex(theme, "flex-col p-4 gap-3 bg-slate-800 rounded-xl border border-slate-700"),
            "w-80");
    panel.add(theme.label("Mapy · zażółć gęślą jaźń", "text-xl text-cyan-400"), "");
    panel.add(theme.label("Offline synthetic XYZ tiles", "text-slate-400"), "");
    panel.add(theme.label("Drag to pan / scroll to zoom", "text-slate-400"), "");
    panel.add(
        theme.button(
            "Zoom +",
            "rounded-lg bg-blue-600",
            () ->
                view.zoomAt(
                    Math.min(22, view.getZoom() + 1),
                    view.getWidth() / 2.0,
                    view.getHeight() / 2.0)),
        "h-10 w-full");
    panel.add(
        theme.button(
            "Reset",
            "rounded-lg bg-slate-700",
            () -> {
              view.setCenter(initial);
              view.setZoom(12);
            }),
        "h-10 w-full");
    InputAdapter input =
        new InputAdapter() {
          private boolean dragging;

          public boolean touchDown(int x, int y, int pointer, int button) {
            if (button != Input.Buttons.LEFT) return false;
            dragging = true;
            dragX = x;
            dragY = y;
            return true;
          }

          public boolean touchDragged(int x, int y, int pointer) {
            if (!dragging) return false;
            view.pan(x - dragX, dragY - y);
            dragging = true;
            dragX = x;
            dragY = y;
            return true;
          }

          public boolean touchUp(int x, int y, int pointer, int button) {
            boolean was = dragging;
            dragging = false;
            return was;
          }

          public boolean touchCancelled(int x, int y, int pointer, int button) {
            dragging = false;
            return true;
          }

          public boolean scrolled(float x, float y) {
            view.zoomAt(
                Math.max(0, Math.min(22, view.getZoom() - (int) Math.signum(y))),
                Gdx.input.getX(),
                view.getHeight() - Gdx.input.getY());
            return true;
          }
        };
    inputMux = new InputMultiplexer(stage, input);
    Gdx.input.setInputProcessor(inputMux);
    game.start();
    resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
  }

  private Pixmap tile(TileKey key) {
    loads++;
    Pixmap p = new Pixmap(256, 256, Pixmap.Format.RGBA8888);
    boolean even = (key.x() + key.y()) % 2 == 0;
    p.setColor(even ? .08f : .1f, even ? .16f : .2f, even ? .22f : .27f, 1);
    p.fill();
    p.setColor(.18f, .34f, .4f, 1);
    for (int i = 0; i < 256; i += 32) {
      p.drawLine(i, 0, i, 255);
      p.drawLine(0, i, 255, i);
    }
    p.setColor(.36f, .6f, .62f, 1);
    p.drawRectangle(0, 0, 256, 256);
    return p;
  }

  public void render() {
    Gdx.gl.glClearColor(.04f, .07f, .1f, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    game.step(Gdx.graphics.getDeltaTime());
    batch.setProjectionMatrix(camera.combined);
    batch.begin();
    batch.setColor(Color.PINK);
    float color = batch.getPackedColor();
    renderer.render(game, view, batch);
    if (Float.floatToRawIntBits(color) != Float.floatToRawIntBits(batch.getPackedColor()))
      throw new IllegalStateException("Map leaked Batch color");
    batch.end();
    stage.act(game.unscaledDeltaTime);
    stage.draw();
    frames++;
    if (smoke) verify();
  }

  private void verify() {
    if (frames == 20) stableLoads = loads;
    if (frames == 30) {
      if (loads != stableLoads)
        throw new IllegalStateException("Stationary map reloaded cached tiles");
      GeoCoordinate before = view.getCenter();
      inputMux.touchDown(800, 350, 0, Input.Buttons.LEFT);
      inputMux.touchDragged(1100, 280, 0);
      inputMux.touchUp(1100, 280, 0, Input.Buttons.LEFT);
      if (before.equals(view.getCenter()))
        throw new IllegalStateException("Overlay blocked map dragging");
    }
    if (frames == 40) view.zoomAt(13, 700, 250);
    if (frames == 50) {
      view.setCenter(new GeoCoordinate(0, 179.9));
      view.setZoom(2);
    }
    if (frames == 60) {
      view.setEnabled(false);
      stableLoads = loads;
      view.setZoom(3);
    }
    if (frames == 70) {
      if (loads != stableLoads) throw new IllegalStateException("Disabled view loaded tiles");
      view.setEnabled(true);
    }
    if (frames == 80) Gdx.graphics.setWindowedMode(1000, 680);
    if (frames == 90) {
      view.setCenter(initial);
      view.setZoom(12);
    }
    if (frames == 100) {
      // Exercise eviction while Batch still contains geometry from prior tiles.
      MapRenderer tiny = new MapRenderer(this::tile, 1);
      try {
        batch.begin();
        tiny.render(game, view, batch);
        batch.end();
        if (tiny.getCachedTileCount() != 1)
          throw new IllegalStateException("Tiny cache exceeded limit");
      } finally {
        if (batch.isDrawing()) batch.end();
        tiny.dispose();
      }
      if (Gdx.gl.glGetError() != GL20.GL_NO_ERROR)
        throw new IllegalStateException("GL error during tile eviction");
    }
    if (frames == 120) {
      if (renderer.getCachedTileCount() > 64
          || loads <= stableLoads
          || view.getWidth() != Gdx.graphics.getWidth())
        throw new IllegalStateException("Map cache or resize failed");
      Vector2 m = view.screenPosition(marker.getCoordinate());
      Pixmap pixels =
          Pixmap.createFromFrameBuffer(
              0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
      try {
        Color red = new Color(pixels.getPixel(Math.round(m.x), Math.round(m.y)));
        if (red.r < .8f || red.g > .4f)
          throw new IllegalStateException("Map marker pixel check failed");
        Vector2 routePoint = view.screenPosition(new GeoCoordinate(51.398, 21.13)).lerp(m, .5f);
        boolean gold = false;
        for (int dx = -1; dx <= 1; dx++)
          for (int dy = -1; dy <= 1; dy++) {
            Color sample =
                new Color(
                    pixels.getPixel(Math.round(routePoint.x) + dx, Math.round(routePoint.y) + dy));
            gold |= sample.r > .8f && sample.g > .5f && sample.b < .3f;
          }
        if (!gold) throw new IllegalStateException("Route pixel check failed");
        PixmapIO.writePNG(Gdx.files.local("map-smoke.png"), pixels, -1, true);
      } finally {
        pixels.dispose();
      }
      Gdx.app.log(
          "JavityMapSmoke",
          "PASS: 120 frames, offline XYZ, markers/routes, pan/zoom/dateline, disabled view, cache,"
              + " resize, Polish font; cached="
              + renderer.getCachedTileCount()
              + ", loads="
              + loads);
      Gdx.app.exit();
    }
  }

  public void resize(int width, int height) {
    if (view != null) view.resize(Math.max(1, width), Math.max(1, height));
    if (camera != null) {
      camera.setToOrtho(false, width, height);
      camera.update();
    }
    if (stage != null) stage.getViewport().update(width, height, true);
  }

  public void dispose() {
    game.dispose();
    renderer.dispose();
    if (renderer.getCachedTileCount() != 0) throw new IllegalStateException("Cache not released");
    root.dispose();
    stage.dispose();
    theme.dispose();
    batch.dispose();
  }
}
