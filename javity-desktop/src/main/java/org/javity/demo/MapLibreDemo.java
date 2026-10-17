package org.javity.demo;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.ScreenUtils;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.nio.file.Files;
import java.util.*;
import org.javity.maplibre.*;

/** Authored offline MVT town, rendered by MapLibre Native in the libGDX window. */
public final class MapLibreDemo extends ApplicationAdapter {
  private final boolean smoke;
  private Path temporaryPackage;
  private OfflineMapPackage mapPackage;
  private MapLibreMap map;
  private SpriteBatch batch;
  private BitmapFont font;
  private ShapeRenderer shapes;
  private int frames, phase, stable;
  private String selected = "Click a building or road";
  private String previousStyle = "day";
  private double lastX, lastY;
  private boolean dragging;
  private boolean closed;
  private int startupGlError;
  private int queries;
  private byte[] databaseBefore;
  private final Map<String, Object> proof = new LinkedHashMap<>();

  public MapLibreDemo(boolean smoke) {
    this.smoke = smoke;
  }

  @Override
  public void create() {
    // libGDX's initial GLES capability probing can leave GL_INVALID_ENUM. Record
    // and drain before our first GL call; subsequent errors always fail smoke.
    startupGlError = Gdx.gl.glGetError();
    if (startupGlError != 0) Gdx.app.log("MapLibre", "libGDX startup GL status=" + startupGlError);
    try {
      temporaryPackage = Files.createTempDirectory("javity maplibre town ");
      String base = "org/javity/demo/maplibre-town/";
      try (var in =
          Objects.requireNonNull(
              getClass().getClassLoader().getResourceAsStream(base + "fixture-source.json"))) {
        byte[] bytes = in.readAllBytes();
        Files.write(temporaryPackage.resolve("fixture-source.json"), bytes);
        var hashes =
            new JsonReader()
                .parse(new String(bytes, java.nio.charset.StandardCharsets.UTF_8))
                .get("sha256");
        for (var hash = hashes.child; hash != null; hash = hash.next) copyResource(base, hash.name);
      }
      databaseBefore = Files.readAllBytes(temporaryPackage.resolve("town.mbtiles"));
      mapPackage = OfflineMapPackage.open(temporaryPackage);
      map =
          new MapLibreMap(
              mapPackage, Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), pixelRatio());
      batch = new SpriteBatch();
      font = new BitmapFont();
      shapes = new ShapeRenderer();
      Gdx.input.setInputProcessor(
          new InputAdapter() {
            @Override
            public boolean scrolled(float x, float y) {
              map.zoomBy(Math.pow(1.25, -y), Gdx.input.getX(), Gdx.input.getY());
              return true;
            }
          });
    } catch (IOException | RuntimeException | Error e) {
      try {
        dispose();
      } catch (RuntimeException cleanup) {
        e.addSuppressed(cleanup);
      }
      throw new IllegalStateException("Could not open offline MapLibre demo", e);
    }
  }

  private void copyResource(String base, String file) throws IOException {
    Path target = temporaryPackage.resolve(file).normalize();
    if (!target.startsWith(temporaryPackage))
      throw new IllegalArgumentException("Invalid bundled map path");
    Files.createDirectories(target.getParent());
    try (var in =
        Objects.requireNonNull(
            getClass().getClassLoader().getResourceAsStream(base + file), file)) {
      Files.copy(in, target);
    }
  }

  private double pixelRatio() {
    return (double) Gdx.graphics.getBackBufferWidth() / Math.max(1, Gdx.graphics.getWidth());
  }

  @Override
  public void render() {
    frames++;
    if (!smoke) input();
    map.update();
    int width = Gdx.graphics.getWidth(), height = Gdx.graphics.getHeight();
    Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    Gdx.gl.glClearColor(0.04f, 0.07f, 0.10f, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    Matrix4 projection = new Matrix4().setToOrtho2D(0, 0, width, height);
    batch.setProjectionMatrix(projection);
    batch.begin();
    map.draw(batch, 0, 0, width, height);
    batch.end();
    if (smoke && stable >= 19 && map.isFullyLoaded() && (phase == 1 || phase == 7))
      capture(phase == 1 ? "demo-maplibre-night-map.png" : "demo-maplibre-map.png");
    shapes.setProjectionMatrix(projection);
    shapes.begin(ShapeRenderer.ShapeType.Filled);
    shapes.setColor(0.055f, 0.09f, 0.13f, 1);
    shapes.rect(0, height - 94, width, 94);
    shapes.rect(0, 0, width, 56);
    shapes.setColor(0.3f, 0.85f, 0.70f, 1);
    shapes.rect(25, height - 38, 5, 19);
    shapes.end();
    batch.begin();
    font.setColor(Color.WHITE);
    font.draw(batch, "JAVITY / OFFLINE VECTOR MAP", 44, height - 22);
    font.setColor(0.63f, 0.76f, 0.82f, 1);
    font.draw(
        batch, "MapLibre Native  |  MVT + MBTiles  |  style: " + map.style(), 26, height - 52);
    font.draw(
        batch,
        "Drag: pan    Wheel: zoom    N: day/night    R: reset    Click: feature query",
        26,
        height - 75);
    font.setColor(Color.WHITE);
    font.draw(batch, selected, 26, 40);
    font.setColor(0.63f, 0.76f, 0.82f, 1);
    font.draw(batch, map.attribution(), 26, 20);
    batch.end();
    if (smoke) verify();
  }

  private void input() {
    if (Gdx.input.isKeyJustPressed(Input.Keys.N))
      map.setStyle(map.style().equals("day") ? "night" : "day");
    if (Gdx.input.isKeyJustPressed(Input.Keys.R)) map.jumpTo(mapPackage.initialCamera());
    if (Gdx.input.justTouched()) {
      lastX = Gdx.input.getX();
      lastY = Gdx.input.getY();
      dragging = true;
      var hits = map.query(lastX, lastY, null);
      selected =
          hits.isEmpty()
              ? "No feature at this position"
              : new JsonReader().parse(hits.getFirst().geoJson()).get("properties").toString();
    } else if (Gdx.input.isTouched() && dragging) {
      double x = Gdx.input.getX(), y = Gdx.input.getY();
      map.panBy(x - lastX, y - lastY);
      lastX = x;
      lastY = y;
    } else dragging = false;
  }

  private void verify() {
    require(Gdx.gl.glGetError() == 0, "Map/UI rendering left an OpenGL error");
    if (++stable < 20 || !map.isFullyLoaded()) {
      require(frames < 600, "Offline map did not fully load");
      return;
    }
    if (phase == 0) {
      var camera = map.camera();
      var center = map.pixelFor(new MapLibreMap.Coordinate(21, 52));
      var roundTrip = map.coordinateFor(center.x(), center.y());
      require(
          Math.abs(roundTrip.longitude() - 21) < 1e-7 && Math.abs(roundTrip.latitude() - 52) < 1e-7,
          "Projection round trip");
      // Town hall lies +80,+20 base-tile units from center. At z14, 4096 MVT units = 512 pixels.
      var hits =
          map.query(
              center.x() + 25 * Math.pow(2, camera.zoom() - 14) / 8,
              center.y(),
              List.of("building"));
      queries = hits.size();
      require(
          hits.stream().anyMatch(f -> f.geoJson().contains("Ratusz")),
          "Native rendered building query returned no town hall");
      double pixelPerUnit = Math.pow(2, camera.zoom() - 14) / 8;
      int lake = rgba(center.x() - 450 * pixelPerUnit, center.y() - 160 * pixelPerUnit);
      require(
          Math.abs((lake >>> 24) - 115) <= 3
              && Math.abs((lake >>> 16 & 255) - 185) <= 3
              && Math.abs((lake >>> 8 & 255) - 207) <= 3,
          "Map image/projection Y mismatch; expected lake color, got " + Integer.toHexString(lake));
      proof.put("northLakePixel", true);
      map.setFeatureState("town", "buildings", "301", "{\"selected\":true}");
      java.util.concurrent.atomic.AtomicReference<Throwable> wrongThread =
          new java.util.concurrent.atomic.AtomicReference<>();
      Thread thread =
          Thread.ofVirtual()
              .start(
                  () -> {
                    try {
                      map.camera();
                    } catch (Throwable error) {
                      wrongThread.set(error);
                    }
                  });
      try {
        thread.join();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError(e);
      }
      require(
          wrongThread.get() instanceof IllegalStateException,
          "Cross-thread map access was accepted");
      proof.put("threadGuard", true);
      proof.put("featureQuery", true);
      proof.put("queriedFeatures", queries);
      proof.put("projectionRoundTrip", true);
      proof.put("dayCenterRgba", rgba(center.x(), center.y()));
      phase = 8;
      stable = 0;
    } else if (phase == 8) {
      var center = map.pixelFor(new MapLibreMap.Coordinate(21, 52));
      double pixelPerUnit = Math.pow(2, map.camera().zoom() - 14) / 8;
      var stateHits = map.query(center.x() + 25 * pixelPerUnit, center.y(), List.of("building"));
      require(
          stateHits.stream().anyMatch(f -> f.stateJson() != null && f.stateJson().contains("true")),
          "Feature state query missing state");
      proof.put("featureState", true);
      map.setStyle("night");
      phase = 1;
      stable = 0;
    } else if (phase == 1) {
      var center = map.pixelFor(new MapLibreMap.Coordinate(21, 52));
      int night = rgba(center.x(), center.y());
      require(
          night != (int) proof.get("dayCenterRgba"), "Style switch did not change rendered pixels");
      proof.put("nightCenterRgba", night);
      proof.put("styleSwitch", true);
      var before = map.camera();
      map.panBy(28, -18);
      map.zoomBy(1.2, center.x(), center.y());
      proof.put("beforePanLongitude", before.longitude());
      phase++;
      stable = 0;
    } else if (phase == 2) {
      require(
          Math.abs(map.camera().longitude() - (double) proof.get("beforePanLongitude")) > 1e-6,
          "Pan did not move camera");
      require(map.camera().zoom() > 14.1, "Overzoom did not advance");
      proof.put("panZoom", true);
      map.setStyle("day");
      map.jumpTo(mapPackage.initialCamera());
      phase++;
      stable = 0;
    } else if (phase == 3) {
      map.setLayerProperty("background", "background-opacity", "0.5");
      phase++;
      stable = 0;
    } else if (phase == 4) {
      int corner = rgba(50, 180);
      require(
          Math.abs((corner >>> 24) - 121) <= 4
              && Math.abs((corner >>> 16 & 255) - 128) <= 4
              && Math.abs((corner >>> 8 & 255) - 128) <= 4,
          "Premultiplied map composition mismatch: " + Integer.toHexString(corner));
      proof.put("premultipliedAlpha", true);
      map.setLayerProperty("background", "background-opacity", "1");
      phase++;
      stable = 0;
    } else if (phase == 5) {
      map.setLayerProperty("background", "background-opacity", "0.5");
      map.setLayerFilter("building", "[\"==\",[\"get\",\"kind\"],\"town-hall\"]");
      map.setFeatureState("town", "buildings", "301", "{\"selected\":true}");
      map.resize(Gdx.graphics.getWidth() / 2, Gdx.graphics.getHeight() / 2, pixelRatio() * 2);
      phase++;
      stable = 0;
    } else if (phase == 6) {
      var center = map.pixelFor(new MapLibreMap.Coordinate(21, 52));
      require(
          Math.abs(center.x() - map.width() * 0.5) < 0.05
              && Math.abs(center.y() - map.height() * 0.5) < 0.05,
          "Resize/DPI projection mismatch");
      var dpiState = map.query(center.x() + 12.5, center.y(), List.of("building"));
      require(
          dpiState.stream().anyMatch(f -> f.stateJson() != null && f.stateJson().contains("true")),
          "DPI recreation lost feature state");
      require(
          map.query(center.x() + 110, center.y(), List.of("building")).isEmpty(),
          "DPI recreation lost layer filter");
      int alpha = rgba(Gdx.graphics.getWidth() - 30, 150);
      require(Math.abs((alpha >>> 24) - 121) <= 4, "DPI recreation lost layer property");
      proof.put("resizeAndDpi", true);
      proof.put("dpiPreservesStyleAndState", true);
      map.setStyle("day");
      map.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), pixelRatio());
      phase++;
      stable = 0;
    } else {
      proof.put("runtime", "MapLibre Native FFI 0.202610.0 / OpenGL EGL");
      proof.put("offlineMbtiles", true);
      proof.put("packagePathContainsSpaces", true);
      proof.put("startupGlError", startupGlError);
      proof.put("renderedFrames", map.renderedFrames());
      proof.put("frames", frames);
      Path out = Path.of(System.getProperty("javity.captureDir", "docs"));
      try {
        Files.createDirectories(out);
        capture("demo-maplibre.png");
        int textureId = nativeTextureId();
        require(
            textureId != 0 && Gdx.gl.glIsTexture(textureId), "Native texture missing before close");
        MapLibreMap closedMap = map;
        dispose();
        closedMap.close();
        boolean rejected = false;
        try {
          closedMap.camera();
        } catch (IllegalStateException expected) {
          rejected = true;
        }
        require(rejected, "Closed map accepted camera access");
        proof.put("closedGuard", true);
        require(!Gdx.gl.glIsTexture(textureId), "Native texture survived closing its owner");
        require(Gdx.gl.glGetError() == 0, "Native map cleanup GL error");
        proof.put("cleanup", true);
        proof.put("nativeTextureDeleted", true);
        proof.put("closeIdempotent", true);
        var json =
            new com.badlogic.gdx.utils.JsonValue(com.badlogic.gdx.utils.JsonValue.ValueType.object);
        for (var entry : proof.entrySet()) {
          Object value = entry.getValue();
          var item =
              value instanceof Boolean v
                  ? new com.badlogic.gdx.utils.JsonValue(v)
                  : value instanceof Integer v
                      ? new com.badlogic.gdx.utils.JsonValue(v.longValue())
                      : value instanceof Long v
                          ? new com.badlogic.gdx.utils.JsonValue(v)
                          : value instanceof Number v
                              ? new com.badlogic.gdx.utils.JsonValue(v.doubleValue())
                              : new com.badlogic.gdx.utils.JsonValue(String.valueOf(value));
          json.addChild(entry.getKey(), item);
        }
        Files.writeString(
            out.resolve("maplibre-verification.json"),
            json.prettyPrint(com.badlogic.gdx.utils.JsonWriter.OutputType.json, 100));
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
      System.out.println("MAPLIBRE_SMOKE_PASS " + proof);
      Gdx.app.exit();
    }
  }

  private void capture(String filename) {
    Path out = Path.of(System.getProperty("javity.captureDir", "docs"));
    try {
      Files.createDirectories(out);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    Pixmap shot =
        ScreenUtils.getFrameBufferPixmap(
            0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    try {
      PixmapIO.writePNG(
          new com.badlogic.gdx.files.FileHandle(out.resolve(filename).toFile()), shot, 6, true);
    } finally {
      shot.dispose();
    }
  }

  private int nativeTextureId() {
    try {
      var field = MapLibreMap.class.getDeclaredField("texture");
      field.setAccessible(true);
      return ((Texture) field.get(map)).getTextureObjectHandle();
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }

  private int rgba(double x, double topY) {
    ByteBuffer bytes = BufferUtils.newByteBuffer(4);
    Gdx.gl.glReadPixels(
        (int) (x * pixelRatio()),
        (int) ((Gdx.graphics.getHeight() - topY) * pixelRatio()),
        1,
        1,
        GL20.GL_RGBA,
        GL20.GL_UNSIGNED_BYTE,
        bytes);
    return (bytes.get(0) & 255) << 24
        | (bytes.get(1) & 255) << 16
        | (bytes.get(2) & 255) << 8
        | bytes.get(3) & 255;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  @Override
  public void resize(int width, int height) {
    if (map != null && width > 0 && height > 0) map.resize(width, height, pixelRatio());
  }

  @Override
  public void dispose() {
    if (closed) return;
    closed = true;
    Gdx.input.setInputProcessor(null);
    try {
      if (map != null) map.close();
    } finally {
      if (smoke && databaseBefore != null && temporaryPackage != null) {
        try {
          require(
              Arrays.equals(
                  databaseBefore, Files.readAllBytes(temporaryPackage.resolve("town.mbtiles"))),
              "Native modified the source MBTiles");
          proof.put("databaseUnmodified", true);
        } catch (IOException e) {
          throw new IllegalStateException(e);
        }
      }
      if (batch != null) batch.dispose();
      if (font != null) font.dispose();
      if (shapes != null) shapes.dispose();
      if (temporaryPackage != null)
        try (var files = Files.walk(temporaryPackage)) {
          for (Path path : files.sorted(Comparator.reverseOrder()).toList())
            Files.deleteIfExists(path);
        } catch (IOException e) {
          throw new IllegalStateException(e);
        }
    }
  }
}
