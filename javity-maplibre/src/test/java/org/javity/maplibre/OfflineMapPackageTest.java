package org.javity.maplibre;

import static org.junit.Assert.*;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;

public class OfflineMapPackageTest {
  private static final byte[] SQLITE_HEADER =
      new byte[] {'S', 'Q', 'L', 'i', 't', 'e', ' ', 'f', 'o', 'r', 'm', 'a', 't', ' ', '3', 0};

  @Test
  public void opensAndResolvesOfflineStyleWithoutChangingMapLibreContent() throws Exception {
    Path root =
        createPackage(
            """
            {"version":1,"styles":{"day":"styles/day.json"},"defaultStyle":"day",
             "sources":{"town":"tiles/town.mbtiles"},
             "initialCamera":{"longitude":18.65,"latitude":54.35,"zoom":12},
             "attribution":"Town data"}
            """);
    Files.createDirectories(root.resolve("styles"));
    Files.createDirectories(root.resolve("sprites"));
    Files.createDirectories(root.resolve("glyphs/Open Sans"));
    Files.writeString(root.resolve("sprites/day.json"), "{}");
    Files.write(root.resolve("sprites/day.png"), new byte[] {1});
    Files.write(root.resolve("glyphs/Open Sans/0-255.pbf"), new byte[] {1});
    writeMbtiles(root);
    String style =
        """
        {"version":8,"name":"Day","sprite":"sprites/day",
         "glyphs":"glyphs/{fontstack}/{range}.pbf",
         "sources":{"town":{"type":"vector","url":"mbtiles://@town"}},
         "layers":[{"id":"roads","type":"line","source":"town","source-layer":"roads",
           "layout":{"line-cap":"round"},
           "paint":{"line-color":["interpolate",["linear"],["zoom"],5,"#000",15,["get","colour"]]}}]}
        """;
    Files.writeString(root.resolve("styles/day.json"), style);

    OfflineMapPackage pack = OfflineMapPackage.open(root);

    assertEquals(root.toRealPath(), pack.root());
    assertEquals("Town data", pack.attribution());
    assertEquals(List.of("day"), List.copyOf(pack.styleNames()));
    assertEquals(new OfflineMapPackage.Camera(18.65, 54.35, 12.0, 0.0, 0.0), pack.initialCamera());
    JsonValue original = new JsonReader().parse(style);
    JsonValue resolved = new JsonReader().parse(pack.resolvedStyle("day"));
    assertEquals(8, resolved.getInt("version"));
    assertEquals(
        original.get("layers").toJson(com.badlogic.gdx.utils.JsonWriter.OutputType.json),
        resolved.get("layers").toJson(com.badlogic.gdx.utils.JsonWriter.OutputType.json));
    String mbtiles = root.resolve("tiles/town.mbtiles").toRealPath().toUri().getRawPath();
    assertEquals("mbtiles://" + mbtiles, resolved.get("sources").get("town").getString("url"));
    assertTrue("MBTiles URL should encode spaces", mbtiles.contains("%20"));
    assertEquals(
        root.resolve("glyphs").toRealPath().toUri().toString() + "{fontstack}/{range}.pbf",
        resolved.getString("glyphs"));
    assertEquals(
        root.toRealPath().toUri().toString() + "sprites/day", resolved.getString("sprite"));
    assertEquals(
        "#000", resolved.get("layers").get(0).get("paint").get("line-color").get(4).asString());
  }

  @Test
  public void rejectsManifestPathsThatAreAbsoluteOrEscapePackage() throws Exception {
    Path root = createPackage(manifest("../outside.json", "tiles/town.mbtiles"));
    assertOpenFails(root);

    root = createPackage(manifest("style.json", "/tmp/town.mbtiles"));
    Files.writeString(root.resolve("style.json"), emptyStyle());
    assertOpenFails(root);
  }

  @Test
  public void rejectsEscapingSymlinkForManifestResources() throws Exception {
    Path outside = Files.createTempDirectory("offline-map-outside");
    Files.writeString(outside.resolve("style.json"), emptyStyle());
    Path root = createPackage(manifest("style.json", "tiles/town.mbtiles"));
    try {
      Files.createSymbolicLink(root.resolve("style.json"), outside.resolve("style.json"));
    } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
      return; // Symlinks are not available on every supported filesystem.
    }
    Files.createDirectories(root.resolve("tiles"));
    Files.write(root.resolve("tiles/town.mbtiles"), SQLITE_HEADER);
    assertOpenFails(root);
  }

  @Test
  public void rejectsEscapingSymlinkInsideTemplatedResourceTree() throws Exception {
    Path outside = Files.createTempFile("offline-map-glyph", ".pbf");
    Path root = createPackage(manifest("style.json", "tiles/town.mbtiles"));
    Files.createDirectories(root.resolve("glyphs/Open Sans"));
    try {
      Files.createSymbolicLink(root.resolve("glyphs/Open Sans/0-255.pbf"), outside);
    } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
      return;
    }
    Files.writeString(
        root.resolve("style.json"),
        """
        {"version":8,"glyphs":"glyphs/{fontstack}/{range}.pbf",
         "sources":{"town":{"type":"vector","url":"mbtiles://@town"}},"layers":[]}
        """);
    writeMbtiles(root);

    assertOpenFails(root);
  }

  @Test
  public void rejectsRemoteUnknownUnmappedAndTraversingStyleResources() throws Exception {
    for (String resource :
        List.of(
            "https://example.invalid/{z}/{x}/{y}.pbf",
            "custom://host/data",
            "mbtiles://@missing",
            "../outside/{z}/{x}/{y}.pbf",
            "/tmp/data/{z}/{x}/{y}.pbf")) {
      Path root = validPackageWithSourceUrl(resource);
      assertOpenFails(root);
    }
  }

  @Test
  public void rejectsRemoteGlyphsAndSprites() throws Exception {
    for (String member :
        List.of(
            "\"glyphs\":\"https://example.invalid/{fontstack}/{range}.pbf\",",
            "\"sprite\":\"https://example.invalid/sprite\",")) {
      Path root = createPackage(manifest("style.json", "tiles/town.mbtiles"));
      Files.writeString(
          root.resolve("style.json"),
          """
          {"version":8,%s"sources":{"town":{"type":"vector","url":"mbtiles://@town"}},"layers":[]}
          """
              .formatted(member));
      writeMbtiles(root);
      assertOpenFails(root);
    }
  }

  @Test
  public void rejectsMalformedCameraAndNonSqliteMbtiles() throws Exception {
    Path root =
        createPackage(
            """
            {"version":1,"styles":{"day":"style.json"},"defaultStyle":"day",
             "sources":{"town":"tiles/town.mbtiles"},
             "initialCamera":{"longitude":0,"latitude":0,"zoom":5},"attribution":"Town"}
            """);
    Files.writeString(root.resolve("style.json"), styleWithMbtilesSource("town"));
    Files.createDirectories(root.resolve("tiles"));
    Files.writeString(root.resolve("tiles/town.mbtiles"), "not sqlite");
    assertOpenFails(root);

    root =
        createPackage(
            """
            {"version":1,"styles":{"day":"style.json"},"defaultStyle":"day",
             "sources":{"town":"tiles/town.mbtiles"},
             "initialCamera":{"longitude":0,"latitude":86,"zoom":5},"attribution":"Town"}
            """);
    Files.writeString(root.resolve("style.json"), styleWithMbtilesSource("town"));
    writeMbtiles(root);
    assertOpenFails(root);
  }

  @Test
  public void acceptsBoundedCameraAndDefaultsOptionalAngles() throws Exception {
    Path root =
        createPackage(
            """
            {"version":1,"styles":{"day":"style.json"},"defaultStyle":"day",
             "sources":{"town":"tiles/town.mbtiles"},
             "initialCamera":{"longitude":-180,"latitude":85.05112878,"zoom":22},"attribution":"Town"}
            """);
    Files.writeString(root.resolve("style.json"), styleWithMbtilesSource("town"));
    writeMbtiles(root);

    assertEquals(
        new OfflineMapPackage.Camera(-180, 85.05112878, 22, 0, 0),
        OfflineMapPackage.open(root).initialCamera());
  }

  @Test
  public void cameraConstructorRejectsNonFiniteAndOutOfRangeValues() {
    assertCameraFails(() -> new OfflineMapPackage.Camera(Double.NaN, 0, 5, 0, 0));
    assertCameraFails(() -> new OfflineMapPackage.Camera(181, 0, 5, 0, 0));
    assertCameraFails(() -> new OfflineMapPackage.Camera(0, 0, 5, 0, 61));
  }

  private Path validPackageWithSourceUrl(String url) throws Exception {
    Path root = createPackage(manifest("style.json", "tiles/town.mbtiles"));
    Files.writeString(
        root.resolve("style.json"),
        """
        {"version":8,"sources":{"town":{"type":"vector","tiles":["%s"]}},"layers":[]}
        """
            .formatted(url));
    writeMbtiles(root);
    return root;
  }

  private Path createPackage(String manifest) throws IOException {
    Path root = Files.createTempDirectory("offline map package ");
    Files.writeString(root.resolve("map.json"), manifest);
    return root;
  }

  private void writeMbtiles(Path root) throws IOException {
    Files.createDirectories(root.resolve("tiles"));
    Files.write(root.resolve("tiles/town.mbtiles"), SQLITE_HEADER);
  }

  private String manifest(String style, String source) {
    return """
    {"version":1,"styles":{"day":"%s"},"defaultStyle":"day",
     "sources":{"town":"%s"},"initialCamera":{"longitude":0,"latitude":0,"zoom":5},
     "attribution":"Town"}
    """
        .formatted(style, source);
  }

  private String styleWithMbtilesSource(String source) {
    return """
    {"version":8,"sources":{"town":{"type":"vector","url":"mbtiles://@%s"}},"layers":[]}
    """
        .formatted(source);
  }

  private String emptyStyle() {
    return "{\"version\":8,\"sources\":{},\"layers\":[]}";
  }

  private void assertOpenFails(Path root) {
    try {
      OfflineMapPackage.open(root);
      fail("Expected invalid offline package: " + root);
    } catch (IllegalArgumentException expected) {
      assertNotNull(expected.getMessage());
    }
  }

  private void assertCameraFails(Runnable constructor) {
    try {
      constructor.run();
      fail("Expected invalid camera");
    } catch (IllegalArgumentException expected) {
      assertNotNull(expected.getMessage());
    }
  }
}
