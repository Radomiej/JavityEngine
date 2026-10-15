package org.javity.demo;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.JsonReader;
import org.javity.maplibre.*;
import org.javity.ui.flex.UiTheme;

/** Real Gorzów Wielkopolski, offline OpenStreetMap/OpenMapTiles vector data. */
public final class GorzowDemo extends ApplicationAdapter {
  private final boolean smoke;
  private BundledOfflineMap bundle;
  private MapLibreMap map;
  private SpriteBatch batch;
  private ShapeRenderer shapes;
  private UiTheme theme;
  private GorzowSmokeProbe probe;
  private String selected = "Kliknij ulicę lub budynek, aby odczytać dane OSM";
  private float lastX, lastY;
  private boolean dragging, closed;

  public GorzowDemo(boolean smoke) {
    this.smoke = smoke;
  }

  @Override
  public void create() {
    int startupError = Gdx.gl.glGetError();
    if (startupError != 0) Gdx.app.log("Gorzów", "libGDX GLES startup status=" + startupError);
    try {
      bundle = BundledOfflineMap.open("org/javity/demo/maplibre-gorzow/");
      map =
          new MapLibreMap(
              bundle.mapPackage(), Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), ratio());
      batch = new SpriteBatch();
      shapes = new ShapeRenderer();
      theme = new UiTheme();
      if (smoke) probe = new GorzowSmokeProbe(map, bundle.mapPackage());
      Gdx.input.setInputProcessor(
          new InputAdapter() {
            @Override
            public boolean scrolled(float x, float y) {
              if (!smoke) map.zoomBy(Math.pow(1.25, -y), Gdx.input.getX(), Gdx.input.getY());
              return true;
            }
          });
    } catch (java.io.IOException | RuntimeException | Error e) {
      try {
        dispose();
      } catch (RuntimeException | Error cleanup) {
        e.addSuppressed(cleanup);
      }
      if (e instanceof Error error) throw error;
      throw new IllegalStateException("Could not open Gorzów map", e);
    }
  }

  @Override
  public void render() {
    if (!smoke) input();
    map.update();
    int width = Gdx.graphics.getWidth(), height = Gdx.graphics.getHeight();
    Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    Gdx.gl.glClearColor(.04f, .07f, .10f, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    var projection = new Matrix4().setToOrtho2D(0, 0, width, height);
    batch.setProjectionMatrix(projection);
    batch.begin();
    map.draw(batch, 0, 0, width, height);
    batch.end();
    if (probe != null) probe.afterMapDraw();
    shapes.setProjectionMatrix(projection);
    shapes.begin(ShapeRenderer.ShapeType.Filled);
    shapes.setColor(.055f, .09f, .13f, 1);
    shapes.rect(0, height - 100, width, 100);
    shapes.rect(0, 0, width, 66);
    shapes.setColor(.3f, .85f, .7f, 1);
    shapes.rect(25, height - 40, 5, 22);
    shapes.end();
    BitmapFont font = theme.getFont();
    batch.begin();
    font.setColor(Color.WHITE);
    font.getData().setScale(1.25f);
    font.draw(batch, "GORZÓW WIELKOPOLSKI", 44, height - 20);
    font.getData().setScale(1);
    font.setColor(.68f, .8f, .86f, 1);
    font.draw(
        batch,
        "Prawdziwe dane OSM  ·  MapLibre Native  ·  MBTiles offline  ·  "
            + (map.style().equals("day") ? "dzień" : "noc"),
        26,
        height - 53);
    font.draw(
        batch,
        "Przeciągnij: przesuwanie   Kółko: zoom   N: dzień/noc   R: kamera startowa",
        26,
        height - 77);
    font.setColor(Color.WHITE);
    font.draw(batch, selected, 26, 48);
    font.setColor(.68f, .8f, .86f, 1);
    font.draw(batch, bundle.mapPackage().attribution(), 26, 25);
    batch.end();
    if (probe != null) probe.afterFrame();
  }

  private void input() {
    if (Gdx.input.isKeyJustPressed(Input.Keys.N))
      map.setStyle(map.style().equals("day") ? "night" : "day");
    if (Gdx.input.isKeyJustPressed(Input.Keys.R)) map.jumpTo(bundle.mapPackage().initialCamera());
    float x = Gdx.input.getX(), y = Gdx.input.getY();
    if (Gdx.input.justTouched() && y > 100 && y < Gdx.graphics.getHeight() - 66) {
      lastX = x;
      lastY = y;
      dragging = true;
      var hits = map.query(x, y, null);
      selected = "Brak obiektu w tym miejscu";
      if (!hits.isEmpty()) {
        var hit = hits.getFirst();
        var properties = new JsonReader().parse(hit.geoJson()).get("properties");
        selected =
            properties == null
                ? hit.sourceLayer()
                : properties.getString(
                    "name:pl",
                    properties.getString("name", properties.getString("class", hit.sourceLayer())));
      }
    } else if (Gdx.input.isTouched() && dragging) {
      map.panBy(x - lastX, y - lastY);
      lastX = x;
      lastY = y;
    } else if (!Gdx.input.isTouched()) dragging = false;
  }

  private double ratio() {
    return (double) Gdx.graphics.getBackBufferWidth() / Math.max(1, Gdx.graphics.getWidth());
  }

  @Override
  public void resize(int width, int height) {
    if (map != null && width > 0 && height > 0) map.resize(width, height, ratio());
  }

  @Override
  public void dispose() {
    if (closed) return;
    closed = true;
    Gdx.input.setInputProcessor(null);
    // Keep the package until Native workers and the cleanup/read-only proof finish.
    Throwable failure = null;
    for (Runnable cleanup :
        new Runnable[] {
          () -> {
            if (map != null) map.close();
          },
          () -> {
            if (batch != null) batch.dispose();
          },
          () -> {
            if (shapes != null) shapes.dispose();
          },
          () -> {
            if (theme != null) theme.dispose();
          },
          () -> {
            if (probe != null) probe.afterMapClosed();
          },
          () -> {
            if (bundle != null)
              try {
                bundle.close();
              } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
              }
          }
        }) {
      try {
        cleanup.run();
      } catch (RuntimeException | Error e) {
        if (failure == null) failure = e;
        else failure.addSuppressed(e);
      }
    }
    if (failure instanceof Error e) throw e;
    if (failure != null) throw new IllegalStateException("City demo cleanup failed", failure);
  }
}
