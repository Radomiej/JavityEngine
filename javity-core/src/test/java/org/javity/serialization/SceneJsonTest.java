package org.javity.serialization;

import static org.junit.Assert.*;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.javity.core.*;
import org.javity.render.*;
import org.junit.Test;

public class SceneJsonTest {
  private static final class ExportMarker extends Component {}

  @Test
  public void prefabRoundTripClonesComponentsAndHierarchy() {
    SceneJson json = SceneJson.standard();
    try (Game source = new Game();
        Game target = new Game()) {
      var parent = source.createObject("parent").setPosition(10, 20).addTag("player");
      var p = parent.addComponent(new Transform3D());
      p.position.set(1, 2, 3);
      var child = source.createObject("child");
      var c = child.addComponent(new Transform3D());
      c.setParent(p);
      child.addComponent(ShapeRenderer2D.circle(10));
      child.setActive(false);
      Prefab prefab = new Prefab(json.write(source.getObjects()));
      var copied = prefab.instantiate(target, json);
      assertEquals(2, copied.size());
      assertEquals(10, copied.get(0).transform.position.x, 0);
      assertTrue(copied.get(0).hasTag("player"));
      assertFalse(copied.get(1).isActive());
      assertSame(
          copied.get(0).getComponent(Transform3D.class),
          copied.get(1).getComponent(Transform3D.class).getParent());
      assertNotSame(p, copied.get(0).getComponent(Transform3D.class));
    }
  }

  @Test
  public void rejectsUnknownTypeVersionAndRollsBack() {
    SceneJson json = SceneJson.standard();
    try (Game g = new Game()) {
      assertThrows(
          IllegalArgumentException.class, () -> json.read(g, "{\"version\":99,\"objects\":[]}"));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              json.read(
                  g,
                  "{\"version\":1,\"objects\":[{\"name\":\"x\",\"components\":[{\"type\":\"java.lang.Runtime\",\"data\":{}}]}]}"));
      assertEquals(0, g.getObjects().size());
      g.createObject("bad").addComponent(new Component() {});
      assertThrows(IllegalArgumentException.class, () -> json.write(g.getObjects()));
    }
  }

  @Test
  public void shapeMaterialsFailSerializationWhileOrdinaryShapeStylesStillRoundTrip() {
    SceneJson json = SceneJson.standard();
    try (Game source = new Game();
        Game target = new Game()) {
      ShapeRenderer2D ordinary = ShapeRenderer2D.rectangle(24, 12);
      ordinary.cornerRadius = 3;
      ordinary.color.set(.2f, .4f, .6f, .8f);
      source.createObject("ordinary-shape").addComponent(ordinary);
      var restored = json.read(target, json.write(source.getObjects())).get(0);
      ShapeRenderer2D copy = restored.getComponent(ShapeRenderer2D.class);
      assertEquals(3, copy.cornerRadius, 0);
      assertEquals(.2f, copy.color.r, 0);

      try (Game materialGame = new Game()) {
        ShapeRenderer2D shape = ShapeRenderer2D.rectangle(10, 10);
        shape.material =
            ShapeMaterial2D.custom(
                "vec4 shapeColor(vec2 local, vec4 tint) { return tint * u_tint; }",
                Map.of("u_tint", ShapeMaterial2D.UniformType.VEC4));
        materialGame.createObject("custom-material").addComponent(shape);
        IllegalArgumentException error =
            assertThrows(
                IllegalArgumentException.class, () -> json.write(materialGame.getObjects()));
        assertTrue(error.getMessage().toLowerCase().contains("material"));
        assertTrue(error.getMessage().toLowerCase().contains("codec"));
      }
    }
  }

  @Test
  public void dashedPolylineRoundTripsAndLegacyShapeDefaultsRemainSolid() {
    SceneJson json = SceneJson.standard();
    try (Game source = new Game();
        Game target = new Game()) {
      ShapeRenderer2D line =
          ShapeRenderer2D.polyline(
              List.of(
                  new com.badlogic.gdx.math.Vector2(1, 2),
                  new com.badlogic.gdx.math.Vector2(13, 2)),
              3);
      line.setDashes(new DashPattern2D(10, 10), -5);
      line.lineCap = ShapeRenderer2D.LineCap.SQUARE;
      line.space = ShapeRenderer2D.Space.SCREEN;
      line.layer = 8;
      line.order = 11;
      source.createObject("dashed").addComponent(line);
      ShapeRenderer2D copy =
          json.read(target, json.write(source.getObjects()))
              .get(0)
              .getComponent(ShapeRenderer2D.class);
      assertArrayEquals(new float[] {10, 10}, copy.dashPattern().lengths(), 0);
      assertEquals(15, copy.dashPhase(), 0);
      assertEquals(ShapeRenderer2D.LineCap.SQUARE, copy.lineCap);
      assertEquals(ShapeRenderer2D.Space.SCREEN, copy.space);
      assertEquals(8, copy.layer);
      assertEquals(11, copy.order);
      assertEquals(new com.badlogic.gdx.math.Vector2(13, 2), copy.getPoints().get(1));

      String legacy =
          "{\"version\":1,\"objects\":[{\"name\":\"solid\",\"components\":["
              + "{\"type\":\"shape2d\",\"data\":{\"kind\":\"POLYLINE\",\"width\":0,\"height\":0,"
              + "\"radius\":0,\"cornerRadius\":0,\"stroke\":2,\"antiAlias\":true,"
              + "\"lineCap\":\"ROUND\",\"lineJoin\":\"ROUND\",\"space\":\"WORLD\","
              + "\"color\":\"ffffffff\",\"layer\":0,\"order\":0,\"points\":[[0,0],[4,0]]}}]}]}";
      ShapeRenderer2D solid = json.read(target, legacy).get(0).getComponent(ShapeRenderer2D.class);
      assertNull(solid.dashPattern());
      assertEquals(0, solid.dashPhase(), 0);
    }
  }

  @Test
  public void fractionalDashPeriodAndLargePhaseRoundTripAtDoublePrecision() {
    SceneJson json = SceneJson.standard();
    DashPattern2D pattern = new DashPattern2D(1f, .1f, 1f, .1f, 1f, .1f, 1f, .1f);
    try (Game source = new Game();
        Game target = new Game()) {
      ShapeRenderer2D line =
          ShapeRenderer2D.polyline(
              List.of(new com.badlogic.gdx.math.Vector2(), new com.badlogic.gdx.math.Vector2(2, 0)),
              2);
      line.setDashes(pattern, 1_000_000d);
      double expected = 1_000_000d % pattern.period();
      assertEquals(expected, line.dashPhase(), 0);
      source.createObject("fractional").addComponent(line);
      String document = json.write(source.getObjects());
      ShapeRenderer2D copy = json.read(target, document).get(0).getComponent(ShapeRenderer2D.class);
      assertEquals(4.4000000059604645d, copy.dashPattern().period(), 0);
      assertEquals(expected, copy.dashPhase(), 0);
    }
  }

  @Test
  public void malformedDashSerializationFailsInsteadOfDroppingToSolid() {
    SceneJson json = SceneJson.standard();
    String prefix =
        "{\"version\":1,\"objects\":[{\"name\":\"bad\",\"components\":["
            + "{\"type\":\"shape2d\",\"data\":{\"kind\":\"POLYLINE\",\"stroke\":2,"
            + "\"points\":[[0,0],[5,0]],\"dashes\":";
    String suffix = ",\"dashPhase\":0}}]}]}";
    try (Game game = new Game()) {
      for (String invalid : List.of("[1,2,3]", "[1,0]", "[1,2,3,4,5,6,7,8,9]", "[1,2,\"NaN\"]"))
        assertThrows(
            IllegalArgumentException.class, () -> json.read(game, prefix + invalid + suffix));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              json.read(
                  game,
                  prefix
                      + "[1,2]"
                      + suffix.replace("\"dashPhase\":0", "\"dashPhase\":\"Infinity\"")));
      assertEquals(0, game.getObjects().size());
    }
  }

  @Test
  public void dashPreflightFailurePreservesExportTargetAndReportsComponentPath() throws Exception {
    SceneJson json = SceneJson.standard();
    Path directory = Files.createTempDirectory("scene-json-dash-test");
    Path target = directory.resolve("runtime.json");
    try (Game game = new Game()) {
      ShapeRenderer2D line =
          ShapeRenderer2D.polyline(
              List.of(
                  new com.badlogic.gdx.math.Vector2(0, 0),
                  new com.badlogic.gdx.math.Vector2(30, 0)),
              2);
      line.setDashes(new DashPattern2D(1e-8f, 1e-8f), 0);
      GameObject object = game.createObject("unrenderable dash ratio");
      object.addComponent(line);
      Files.writeString(target, "keep existing target");
      IllegalArgumentException failure =
          assertThrows(IllegalArgumentException.class, () -> json.export(game, target));
      assertTrue(failure.getMessage().contains("components[0].data"));
      assertTrue(failure.getMessage().contains("shader precision"));
      assertEquals("keep existing target", Files.readString(target));
    } finally {
      try (var files = Files.list(directory)) {
        for (Path path : files.toList()) Files.deleteIfExists(path);
      }
      Files.deleteIfExists(directory);
    }
  }

  @Test
  public void exportAggregatesUnsupportedAndCodecFailuresWithStableComponentIdentity() {
    SceneJson json = SceneJson.standard();
    json.register(
        "export-marker",
        ExportMarker.class,
        marker -> {
          throw new IllegalStateException("resource unavailable");
        },
        data -> new ExportMarker());
    try (Game game = new Game()) {
      GameObject object = game.createObject("runtime cube");
      object.addComponent(new ExportMarker());
      object.addComponent(new ExportMarker());
      object.addComponent(new Component() {});
      var report = json.prepare(game);
      assertFalse(report.isValid());
      assertEquals(3, report.issues().size());
      assertEquals(0, report.issues().get(0).componentIndex());
      assertEquals(ExportMarker.class.getName(), report.issues().get(0).componentType());
      assertTrue(report.issues().get(0).path().endsWith("components[0].data"));
      assertEquals(1, report.issues().get(1).componentIndex());
      assertEquals(2, report.issues().get(2).componentIndex());
      assertTrue(report.issues().get(2).message().contains("No explicitly registered"));
    }
  }

  @Test
  public void exportRejectsNonFiniteTransformsAndCustomJsonNumbers() {
    SceneJson json =
        new SceneJson()
            .register(
                "export-marker",
                ExportMarker.class,
                marker -> {
                  JsonValue data = SceneJson.array();
                  data.addChild(new JsonValue(Double.NaN));
                  return data;
                },
                data -> new ExportMarker());
    try (Game game = new Game()) {
      GameObject object = game.createObject("bad values");
      object.transform.position.x = Float.POSITIVE_INFINITY;
      object.addComponent(new ExportMarker());
      var report = json.prepare(game);
      assertFalse(report.isValid());
      assertEquals(2, report.issues().size());
      assertTrue(report.issues().get(0).path().endsWith("transform.x"));
      assertTrue(report.issues().get(1).path().endsWith("data[0]"));
    }
  }

  @Test
  public void structuralAndComponentFailuresAggregateAcrossTheWholeScene() {
    SceneJson json =
        SceneJson.standard()
            .register(
                "export-marker",
                ExportMarker.class,
                marker -> {
                  throw new IllegalStateException("codec resource failure");
                },
                data -> new ExportMarker());
    try (Game game = new Game()) {
      GameObject first = game.createObject("first");
      Transform3D spatial = first.addComponent(new Transform3D());
      spatial.rotation.z = Float.NaN;
      ExportMarker broken = first.addComponent(new ExportMarker());
      broken.updateMode = null;
      first.addComponent(new Component() {});
      GameObject second = game.createObject("second");
      second.addComponent(new Component() {});
      var report = json.prepare(game);
      assertFalse(report.isValid());
      assertTrue(report.issues().stream().anyMatch(issue -> issue.path().endsWith("rotation.z")));
      assertTrue(
          report.issues().stream()
              .anyMatch(issue -> issue.path().endsWith("components[1].updateMode")));
      assertTrue(
          report.issues().stream()
              .anyMatch(
                  issue ->
                      issue.path().endsWith("components[1].data")
                          && issue.message().contains("codec resource failure")));
      assertTrue(
          report.issues().stream()
              .anyMatch(issue -> issue.objectIndex() == 0 && issue.componentIndex() == 2));
      assertTrue(
          report.issues().stream()
              .anyMatch(issue -> issue.objectIndex() == 1 && issue.componentIndex() == 0));
    }
  }

  @Test
  public void explicitExportAtomicallyReplacesTargetAndCleansSiblingTemporaryFile()
      throws Exception {
    SceneJson json = SceneJson.standard();
    Path directory = Files.createTempDirectory("scene-json-test");
    Path target = directory.resolve("runtime.json");
    try (Game source = new Game();
        Game restored = new Game()) {
      var parent = source.createObject("parent").addComponent(new Transform3D());
      var child = source.createObject("child").addComponent(new Transform3D());
      child.getComponent(Transform3D.class).setParent(parent.getComponent(Transform3D.class));
      Files.writeString(target, "previous target");
      var report = json.export(source, target);
      assertTrue(report.isValid());
      assertEquals(report.candidate(), Files.readString(target));
      var copy = json.read(restored, Files.readString(target));
      assertSame(
          copy.get(0).getComponent(Transform3D.class),
          copy.get(1).getComponent(Transform3D.class).getParent());
      try (var files = Files.list(directory)) {
        assertEquals(1, files.count());
      }
    } finally {
      try (var files = Files.list(directory)) {
        for (Path path : files.toList()) Files.deleteIfExists(path);
      }
      Files.deleteIfExists(directory);
    }
  }

  @Test
  public void failedPreflightLeavesExistingTargetUnchangedAndRejectsBadSelections()
      throws Exception {
    SceneJson json = SceneJson.standard();
    Path directory = Files.createTempDirectory("scene-json-failure");
    Path target = directory.resolve("runtime.json");
    try (Game game = new Game()) {
      game.createObject("unknown").addComponent(new Component() {});
      Files.writeString(target, "keep me");
      var report = json.prepare(game);
      assertFalse(report.isValid());
      assertThrows(IllegalArgumentException.class, () -> json.export(game, target));
      assertEquals("keep me", Files.readString(target));
      var selection = new ArrayList<GameObject>();
      GameObject item = game.getObjects().get(0);
      selection.add(item);
      selection.add(item);
      selection.add(null);
      var invalid = json.prepareSelection(selection);
      assertEquals(3, invalid.issues().size());
      item.destroy();
      assertTrue(
          json.prepareSelection(List.of(item)).issues().stream()
              .anyMatch(issue -> issue.message().contains("destroyed")));
      try (var files = Files.list(directory)) {
        assertEquals(1, files.count());
      }
    } finally {
      try (var files = Files.list(directory)) {
        for (Path path : files.toList()) Files.deleteIfExists(path);
      }
      Files.deleteIfExists(directory);
    }
  }

  @Test
  public void preparedArrayCodecCandidateIsSavedWithoutInvokingWriterAgain() throws Exception {
    int[] writes = {0};
    SceneJson json =
        new SceneJson()
            .register(
                "array-marker",
                ExportMarker.class,
                marker -> {
                  writes[0]++;
                  JsonValue payload = SceneJson.array();
                  payload.addChild(new JsonValue(7L));
                  payload.addChild(new JsonValue("kept-array"));
                  return payload;
                },
                data -> {
                  assertTrue(data.isArray());
                  assertEquals("kept-array", data.getString(1));
                  return new ExportMarker();
                });
    Path directory = Files.createTempDirectory("scene-json-array");
    Path target = directory.resolve("snapshot.json");
    try (Game source = new Game();
        Game restored = new Game()) {
      GameObject object = source.createObject("array");
      object.addComponent(new ExportMarker());
      var prepared = json.prepareSelection(List.of(object));
      assertTrue(prepared.isValid());
      assertEquals(1, writes[0]);
      json.save(prepared, target);
      assertEquals(1, writes[0]);
      assertEquals(prepared.candidate(), Files.readString(target));
      assertEquals(1, json.read(restored, Files.readString(target)).size());
      assertEquals(1, writes[0]);
    } finally {
      try (var files = Files.list(directory)) {
        for (Path path : files.toList()) Files.deleteIfExists(path);
      }
      Files.deleteIfExists(directory);
    }
  }

  @Test
  public void exportReportsForeignDisposedAndOutsideParentSelections() {
    SceneJson json = SceneJson.standard();
    try (Game first = new Game();
        Game second = new Game();
        Game restored = new Game()) {
      GameObject foreign = second.createObject("foreign");
      assertTrue(
          json.prepareSelection(List.of(first.createObject("local"), foreign)).issues().stream()
              .anyMatch(issue -> issue.message().contains("different game")));

      GameObject parent = first.createObject("parent");
      Transform3D parentTransform = parent.addComponent(new Transform3D());
      parentTransform.position.set(10, 20, 30);
      GameObject child = first.createObject("child");
      Transform3D childTransform = child.addComponent(new Transform3D());
      childTransform.position.set(2, 3, 4);
      childTransform.setParent(parentTransform);
      assertTrue(
          json.prepareSelection(List.of(child)).issues().stream()
              .anyMatch(issue -> issue.message().contains("outside the selected snapshot")));
      parent.removeComponent(parentTransform);
      var detached = json.prepareSelection(List.of(parent, child));
      assertTrue(detached.issues().toString(), detached.isValid());
      assertNull(childTransform.getParent());
      JsonValue childData =
          new JsonReader()
              .parse(detached.candidate())
              .get("objects")
              .get(1)
              .get("components")
              .get(0)
              .get("data");
      assertFalse(childData.has("parent"));
      Transform3D copied =
          json.read(restored, detached.candidate()).get(1).getComponent(Transform3D.class);
      assertNull(copied.getParent());
      assertEquals(new Vector3(2, 3, 4), copied.position);
      assertEquals(new Vector3(2, 3, 4), copied.worldMatrix().getTranslation(new Vector3()));
    }
    Game disposed = new Game();
    GameObject disposedObject = disposed.createObject("disposed");
    disposed.dispose();
    assertTrue(
        json.prepareSelection(List.of(disposedObject)).issues().stream()
            .anyMatch(issue -> issue.message().contains("disposed game")));
  }

  @Test
  public void failedFilesystemReplacementLeavesTargetAndRemovesTemporarySibling() throws Exception {
    SceneJson json = SceneJson.standard();
    Path directory = Files.createTempDirectory("scene-json-move-failure");
    Path targetDirectory = directory.resolve("occupied-target");
    Files.createDirectory(targetDirectory);
    Path sentinel = targetDirectory.resolve("keep.txt");
    Files.writeString(sentinel, "keep");
    try (Game game = new Game()) {
      game.createObject("serializable").addComponent(new Transform3D());
      assertThrows(java.io.IOException.class, () -> json.export(game, targetDirectory));
      assertEquals("keep", Files.readString(sentinel));
      try (var files = Files.list(directory)) {
        assertEquals(1, files.count());
      }
    } finally {
      Files.deleteIfExists(sentinel);
      Files.deleteIfExists(targetDirectory);
      Files.deleteIfExists(directory);
    }
  }
}
