package org.javity.demo;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.utils.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import org.javity.maplibre.*;

/** Real-city GPU acceptance probe, separate from the interactive demo. */
final class GorzowSmokeProbe {
  private final MapLibreMap map;
  private final OfflineMapPackage mapPackage;
  private final byte[] databaseBefore;
  private final Map<String, Object> proof = new LinkedHashMap<>();
  private int frames, stable, phase, waterX, waterY, dayWater;
  private int textureId;
  private boolean finished;

  GorzowSmokeProbe(MapLibreMap map, OfflineMapPackage mapPackage) {
    this.map = map;
    this.mapPackage = mapPackage;
    try {
      databaseBefore = Files.readAllBytes(mapPackage.root().resolve("gorzow.mbtiles"));
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }

  void afterMapDraw() {
    if (map.isFullyLoaded() && stable >= 19 && (phase == 0 || phase == 1 || phase == 3))
      capture(phase == 1 ? "demo-gorzow-night-map.png" : "demo-gorzow-map.png");
  }

  void afterFrame() {
    frames++;
    require(Gdx.gl.glGetError() == 0, "City map left a GL error");
    require(frames < 600, "Gorzów did not load within 600 frames");
    if (++stable < 20 || !map.isFullyLoaded()) return;
    if (phase == 0) {
      var camera = map.camera();
      require(
          Math.abs(camera.longitude() - 15.2385) < 1e-6
              && Math.abs(camera.latitude() - 52.7319) < 1e-6,
          "Camera outside Gorzów");
      var root = new JsonReader().parse(mapPackage.resolvedStyle("day"));
      String waterColor = null;
      List<String> renderedLayers = new ArrayList<>();
      for (var layer = root.get("layers").child; layer != null; layer = layer.next)
        if (!layer.getString("type").equals("background"))
          renderedLayers.add(layer.getString("id"));
      for (var layer = root.get("layers").child; layer != null; layer = layer.next)
        if (layer.getString("id").equals("water"))
          waterColor = layer.get("paint").getString("fill-color");
      Color expected = Color.valueOf(Objects.requireNonNull(waterColor));
      int buildings = 0, roads = 0, water = 0;
      Set<String> names = new LinkedHashSet<>();
      // Query actual rendered MVT geometry, not just a hardcoded city caption.
      for (int y = 120; y < Gdx.graphics.getHeight() - 72; y += 24) {
        for (int x = 24; x < Gdx.graphics.getWidth() - 24; x += 24) {
          var hits = map.query(x, y, renderedLayers);
          for (var hit : hits) {
            if ("building".equals(hit.sourceLayer())) buildings++;
            if ("transportation".equals(hit.sourceLayer())) roads++;
            var properties = new JsonReader().parse(hit.geoJson()).get("properties");
            if (properties != null) {
              String name = properties.getString("name:pl", properties.getString("name", ""));
              if (!name.isBlank()) names.add(name);
            }
            if ("water".equals(hit.sourceLayer())) {
              int pixel = rgba(x, y);
              if (closeRgb(pixel, expected)) {
                water++;
                waterX = x;
                waterY = y;
                dayWater = pixel;
              }
            }
          }
        }
      }
      require(buildings > 20, "Expected dense real city building geometry");
      require(roads > 5, "Expected real Gorzów streets");
      require(water > 5, "Expected visible river geometry and matching water pixels");
      require(!names.isEmpty(), "Expected real named map features");
      proof.put("city", "Gorzów Wielkopolski");
      proof.put("buildingHits", buildings);
      proof.put("roadHits", roads);
      proof.put("waterPixelsAndGeometry", water);
      proof.put("queriedNames", String.join("; ", names));
      proof.put("nativeMvtQuery", true);
      proof.put("offline", true);
      capture("demo-gorzow.png");
      map.setStyle("night");
      phase = 1;
      stable = 0;
    } else if (phase == 1) {
      require(rgba(waterX, waterY) != dayWater, "Night style did not change river pixels");
      proof.put("dayNight", true);
      capture("demo-gorzow-night.png");
      map.panBy(30, -15);
      map.zoomBy(1.15, map.width() / 2.0, map.height() / 2.0);
      phase = 2;
      stable = 0;
    } else if (phase == 2) {
      require(map.camera().zoom() > 15.1, "City zoom did not change");
      require(Math.abs(map.camera().longitude() - 15.2385) > 1e-6, "City pan did not change");
      proof.put("panZoom", true);
      map.setStyle("day");
      map.jumpTo(mapPackage.initialCamera());
      phase = 3;
      stable = 0;
    } else {
      capture("demo-gorzow.png");
      textureId = nativeTextureId();
      require(textureId != 0 && Gdx.gl.glIsTexture(textureId), "Missing Native texture");
      proof.put("frames", frames);
      proof.put("renderedFrames", map.renderedFrames());
      finished = true;
      Gdx.app.exit();
    }
  }

  void afterMapClosed() {
    if (!finished) return;
    try {
      require(
          Arrays.equals(
              databaseBefore, Files.readAllBytes(mapPackage.root().resolve("gorzow.mbtiles"))),
          "Native modified the source MBTiles");
      require(!Gdx.gl.glIsTexture(textureId), "Native city texture survived close");
      require(Gdx.gl.glGetError() == 0, "City cleanup GL error");
      proof.put("sourceDatabaseUnmodified", true);
      proof.put("nativeTextureDeleted", true);
      proof.put("cleanup", true);
      var json = new JsonValue(JsonValue.ValueType.object);
      for (var e : proof.entrySet()) {
        Object v = e.getValue();
        json.addChild(
            e.getKey(),
            v instanceof Boolean b
                ? new JsonValue(b)
                : v instanceof Number n
                    ? new JsonValue(n.longValue())
                    : new JsonValue(String.valueOf(v)));
      }
      Path out = output();
      Files.createDirectories(out);
      Files.writeString(
          out.resolve("gorzow-verification.json"),
          json.prettyPrint(JsonWriter.OutputType.json, 100));
      System.out.println("GORZOW_SMOKE_PASS " + proof);
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static boolean closeRgb(int rgba, Color c) {
    return Math.abs((rgba >>> 24) - c.r * 255) < 4
        && Math.abs((rgba >>> 16 & 255) - c.g * 255) < 4
        && Math.abs((rgba >>> 8 & 255) - c.b * 255) < 4;
  }

  private static Path output() {
    return Path.of(System.getProperty("javity.captureDir", "docs"));
  }

  private static void capture(String name) {
    Path out = output();
    try {
      Files.createDirectories(out);
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
    Pixmap pixmap =
        ScreenUtils.getFrameBufferPixmap(
            0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    try {
      PixmapIO.writePNG(
          new com.badlogic.gdx.files.FileHandle(out.resolve(name).toFile()), pixmap, 6, true);
    } finally {
      pixmap.dispose();
    }
  }

  private static int rgba(int x, int topY) {
    ByteBuffer pixel = BufferUtils.newByteBuffer(4);
    double scaleX = (double) Gdx.graphics.getBackBufferWidth() / Gdx.graphics.getWidth();
    double scaleY = (double) Gdx.graphics.getBackBufferHeight() / Gdx.graphics.getHeight();
    Gdx.gl.glReadPixels(
        (int) (x * scaleX),
        (int) ((Gdx.graphics.getHeight() - topY) * scaleY),
        1,
        1,
        GL20.GL_RGBA,
        GL20.GL_UNSIGNED_BYTE,
        pixel);
    return (pixel.get(0) & 255) << 24
        | (pixel.get(1) & 255) << 16
        | (pixel.get(2) & 255) << 8
        | pixel.get(3) & 255;
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

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
