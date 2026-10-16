package org.javity.ui.inspector;

import static org.junit.Assert.*;

import com.badlogic.gdx.math.*;
import java.util.*;
import org.javity.core.*;
import org.junit.Test;

public class SceneInspectionTest {
  static final class Marker extends Component {}

  @Test
  public void includesInactiveAndDuplicateComponentsWithoutChangingScene() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("actor").addTag("player");
      object.addComponent(new Marker());
      Marker disabled = object.addComponent(new Marker());
      disabled.setEnabled(false);
      object.setActive(false);
      SceneInspection model = new SceneInspection();
      model.setScene(game);
      assertTrue(model.select(object.id));
      var snapshot = model.capture();
      assertEquals(1, snapshot.objects().size());
      assertFalse(snapshot.objects().getFirst().active());
      assertEquals(2, snapshot.objects().getFirst().components());
      assertTrue(snapshot.details().stream().anyMatch(x -> x.contains("disabled")));
      assertEquals(2, object.getComponents().size());
      assertFalse(game.isPaused());
      assertThrows(UnsupportedOperationException.class, () -> snapshot.details().clear());
      assertThrows(UnsupportedOperationException.class, () -> snapshot.objects().clear());
    }
  }

  @Test
  public void listsRealTransformHierarchyAndLeaves2DObjectsFlat() {
    try (Game game = new Game()) {
      GameObject child = game.createObject("child");
      Transform3D childT = child.addComponent(new Transform3D());
      GameObject root = game.createObject("parent");
      Transform3D rootT = root.addComponent(new Transform3D());
      childT.setParent(rootT);
      GameObject flat = game.createObject("2d");
      SceneInspection model = new SceneInspection();
      model.setScene(game);
      var rows = model.capture().objects();
      assertEquals(
          List.of(root.id, child.id, flat.id), rows.stream().map(SceneInspection.Row::id).toList());
      assertEquals(List.of(0, 1, 0), rows.stream().map(SceneInspection.Row::depth).toList());
    }
  }

  @Test
  public void selectionCannotLeakAcrossScenesWithReusedIds() {
    try (Game first = new Game();
        Game second = new Game()) {
      GameObject a = first.createObject("first");
      GameObject b = second.createObject("second");
      assertEquals(a.id, b.id);
      SceneInspection model = new SceneInspection();
      model.setScene(first);
      model.select(a.id);
      assertEquals(Integer.valueOf(a.id), model.capture().selectedId());
      model.setScene(second);
      assertNull(model.capture().selectedId());
      assertFalse(model.capture().details().toString().contains("first"));
    }
  }

  @Test
  public void destroyedObjectAndDisposedSceneDropSelection() {
    Game game = new Game();
    GameObject object = game.createObject("removed");
    SceneInspection model = new SceneInspection();
    model.setScene(game);
    model.select(object.id);
    object.destroy();
    assertNull(model.capture().selectedId());
    assertTrue(model.capture().objects().isEmpty());
    GameObject live = game.createObject("last");
    model.select(live.id);
    game.close();
    assertNull(model.capture().selectedId());
    assertTrue(model.capture().objects().isEmpty());
    assertFalse(model.select(live.id));
  }

  @Test
  public void snapshotsReflectLiveTransformsAndComponentsButRemainImmutable() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("moving");
      Transform3D transform = object.addComponent(new Transform3D());
      SceneInspection model = new SceneInspection();
      model.setScene(game);
      model.select(object.id);
      var before = model.capture();
      transform.position.z = 42;
      object.addComponent(new Marker());
      var after = model.capture();
      assertFalse(before.details().equals(after.details()));
      assertTrue(after.details().toString().contains("42.00"));
      assertEquals(1, before.objects().getFirst().components());
      assertEquals(2, after.objects().getFirst().components());
    }
  }

  @Test
  public void typedTransformEditsValidateAndUndoRedo() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("editable");
      Transform3D transform = object.addComponent(new Transform3D());
      SceneInspection model = new SceneInspection();
      model.setScene(game);
      assertTrue(model.select(object.id));

      model.edit(SceneInspection.Transform2DField.POSITION_X, 12.5f);
      model.edit(SceneInspection.Transform3DField.POSITION_Z, -3f);
      model.edit(SceneInspection.Transform3DField.ROTATION_X, 1f);
      assertEquals(12.5f, object.transform.position.x, 0f);
      assertEquals(-3f, transform.position.z, 0f);
      assertEquals(1f / (float) Math.sqrt(2), transform.rotation.x, .00001f);
      assertTrue(model.canUndo());
      assertTrue(model.undo());
      assertEquals(0f, transform.rotation.x, 0f);
      assertTrue(model.undo());
      assertEquals(0f, transform.position.z, 0f);
      assertTrue(model.undo());
      assertEquals(0f, object.transform.position.x, 0f);
      assertFalse(model.canUndo());
      assertTrue(model.redo());
      assertEquals(12.5f, object.transform.position.x, 0f);
      assertThrows(
          IllegalArgumentException.class,
          () -> model.edit(SceneInspection.Transform2DField.ROTATION, Float.NaN));
      assertEquals(0f, object.transform.rotation, 0f);
    }
  }

  @Test
  public void editHistoryIsBoundToSceneObjectAndComponentIdentity() {
    try (Game first = new Game();
        Game second = new Game()) {
      GameObject object = first.createObject("first");
      Transform3D component = object.addComponent(new Transform3D());
      SceneInspection model = new SceneInspection();
      model.setScene(first);
      model.select(object.id);
      model.edit(SceneInspection.Transform3DField.POSITION_X, 4f);
      model.setScene(second);
      assertFalse(model.undo());
      assertEquals(4f, component.position.x, 0f);

      model.setScene(first);
      model.select(object.id);
      model.edit(SceneInspection.Transform3DField.POSITION_X, 9f);
      object.removeComponent(component);
      Transform3D replacement = object.addComponent(new Transform3D());
      assertFalse(model.undo());
      assertEquals(9f, component.position.x, 0f);
      assertEquals(0f, replacement.position.x, 0f);
    }
  }

  @Test
  public void physicsOwnedTransformsAreRejectedWithoutPhysicsDependency() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("body");
      object.addComponent(new GuardedTransformComponent());
      SceneInspection model = new SceneInspection();
      model.setScene(game);
      model.select(object.id);
      assertThrows(
          IllegalStateException.class,
          () -> model.edit(SceneInspection.Transform2DField.POSITION_X, 2f));
      assertEquals(0f, object.transform.position.x, 0f);
    }
  }

  @Test
  public void newlyAddedTransformOwnerMakesExistingHistoryStale() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("becomes physical");
      SceneInspection model = new SceneInspection();
      model.setScene(game);
      model.select(object.id);
      model.edit(SceneInspection.Transform2DField.POSITION_X, 8f);
      object.addComponent(new GuardedTransformComponent());
      assertFalse(model.undo());
      assertEquals(8f, object.transform.position.x, 0f);
    }
  }

  @Test
  public void staleValueAndQuaternionEditsDoNotOverwriteOtherChanges() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("3d");
      Transform3D transform = object.addComponent(new Transform3D());
      SceneInspection model = new SceneInspection();
      model.setScene(game);
      model.select(object.id);
      model.edit(SceneInspection.Transform3DField.ROTATION_X, .5f);
      assertEquals(1f, transform.rotation.len(), .00001f);
      assertTrue(model.undo());
      assertEquals(0f, transform.rotation.x, 0f);
      assertTrue(model.redo());
      transform.rotation.set(0, 0, 1, 0);
      assertFalse(model.undo());
      assertEquals(1f, transform.rotation.z, 0f);

      model.editRotation3D(0, 0, 0, 2);
      assertEquals(1f, transform.rotation.w, 0f);
      assertThrows(IllegalArgumentException.class, () -> model.editRotation3D(0, 0, 0, 0));
    }
  }

  @Test
  public void editHistoryHasAFixedBound() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("bounded");
      SceneInspection model = new SceneInspection();
      model.setScene(game);
      model.select(object.id);
      for (int i = 1; i <= 300; i++) model.edit(SceneInspection.Transform2DField.POSITION_X, i);
      int undos = 0;
      while (model.undo()) undos++;
      assertEquals(256, undos);
      assertFalse(model.canUndo());
    }
  }

  @Test
  public void fullTransformGizmoCommitIsAtomicAndSessionRejectsReplacementOrParentMotion() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("gizmo");
      Transform3D transform = object.addComponent(new Transform3D());
      SceneInspection inspection = new SceneInspection();
      inspection.setScene(game);
      inspection.select(object.id);
      var session = inspection.beginTransform3DEdit();
      var baseline = inspection.selectedTransform3D();
      assertEquals(0f, transform.position.x, 0f); // Previewing does not mutate the scene.
      inspection.commitTransform3D(
          session, new SceneInspection.Transform3DState(3, 4, 5, 0, 0, 0, 1, 2, 3, 4));
      assertTrue(inspection.undo());
      assertEquals(baseline, inspection.selectedTransform3D());
      assertFalse(inspection.undo());
      assertTrue(inspection.redo());
      assertEquals(
          new SceneInspection.Transform3DState(3, 4, 5, 0, 0, 0, 1, 2, 3, 4),
          inspection.selectedTransform3D());

      var staleSession = inspection.beginTransform3DEdit();
      object.removeComponent(transform);
      Transform3D replacement = object.addComponent(new Transform3D());
      assertThrows(
          IllegalStateException.class,
          () ->
              inspection.commitTransform3D(
                  staleSession,
                  new SceneInspection.Transform3DState(1, 0, 0, 0, 0, 0, 1, 1, 1, 1)));
      assertEquals(0f, replacement.position.x, 0f);
    }
  }

  @Test
  public void pureTranslationHistoryPreservesNearUnitQuaternionBitsAcrossUndoAndRedo() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("near-unit quaternion");
      Transform3D transform = object.addComponent(new Transform3D());
      transform.rotation.set(0, 0.0019999986980110407f, 0, 0.9999979734420776f);
      float qx = transform.rotation.x;
      float qy = transform.rotation.y;
      float qz = transform.rotation.z;
      float qw = transform.rotation.w;

      SceneInspection inspection = new SceneInspection();
      inspection.setScene(game);
      inspection.select(object.id);
      inspection.translateSelected3D(Vector3.X, false, 0);

      assertEquals(qx, transform.rotation.x, 0f);
      assertEquals(qy, transform.rotation.y, 0f);
      assertEquals(qz, transform.rotation.z, 0f);
      assertEquals(qw, transform.rotation.w, 0f);
      assertTrue("The translation must be undoable", inspection.undo());
      assertEquals(0f, transform.position.x, 0f);
      assertEquals(qx, transform.rotation.x, 0f);
      assertEquals(qy, transform.rotation.y, 0f);
      assertEquals(qz, transform.rotation.z, 0f);
      assertEquals(qw, transform.rotation.w, 0f);
      assertTrue("The translation must be redoable", inspection.redo());
      assertEquals(1f, transform.position.x, 0f);
      assertEquals(qx, transform.rotation.x, 0f);
      assertEquals(qy, transform.rotation.y, 0f);
      assertEquals(qz, transform.rotation.z, 0f);
      assertEquals(qw, transform.rotation.w, 0f);
    }
  }

  @Test
  public void worldTranslationUsesParentInverseAndPositiveSnapStep() {
    try (Game game = new Game()) {
      GameObject parentObject = game.createObject("parent");
      Transform3D parent = parentObject.addComponent(new Transform3D());
      parent.rotation.setFromAxis(Vector3.Z, 90);
      GameObject child = game.createObject("child");
      Transform3D transform = child.addComponent(new Transform3D());
      transform.setParent(parent);
      SceneInspection inspection = new SceneInspection();
      inspection.setScene(game);
      inspection.select(child.id);
      inspection.translateSelected3D(new Vector3(.6f, 0, 0), false, 1f);
      Vector3 world = transform.worldMatrix().getTranslation(new Vector3());
      assertEquals(1f, world.x, .0001f);
      assertEquals(0f, world.y, .0001f);
      assertEquals(0f, transform.position.x, .0001f);
      assertEquals(-1f, transform.position.y, .0001f);
      assertTrue(inspection.undo());
      inspection.translateSelected3D(new Vector3(1, 0, 0), true, 0);
      assertEquals(1f, transform.position.x, .0001f);
      assertEquals(0f, transform.position.y, .0001f);
      assertThrows(
          IllegalArgumentException.class,
          () -> inspection.translateSelected3D(new Vector3(1, 0, 0), false, -1));
    }
  }

  @Test
  public void worldTranslationRejectsSingularParentAndParentedWorldRotationOrScale() {
    try (Game game = new Game()) {
      Transform3D parent = game.createObject("singular").addComponent(new Transform3D());
      parent.scale.x = 0;
      GameObject child = game.createObject("child");
      Transform3D t = child.addComponent(new Transform3D());
      t.setParent(parent);
      SceneInspection inspection = new SceneInspection();
      inspection.setScene(game);
      inspection.select(child.id);
      assertThrows(
          IllegalStateException.class,
          () -> inspection.translateSelected3D(new Vector3(1, 0, 0), false, 0));
      assertThrows(
          IllegalStateException.class, () -> inspection.rotateSelected3D(Vector3.X, 1, false, 0));
      assertThrows(
          IllegalStateException.class,
          () -> inspection.scaleSelected3D(new Vector3(2, 1, 1), false, 0));
      assertEquals(0f, t.position.x, 0f);
    }
  }

  @Test
  public void localTranslationUsesLocalAxisAndDragSessionRejectsParentMotion() {
    try (Game game = new Game()) {
      Transform3D parent = game.createObject("parent").addComponent(new Transform3D());
      parent.rotation.setFromAxis(Vector3.Z, 90);
      Transform3D child = game.createObject("child").addComponent(new Transform3D());
      child.rotation.setFromAxis(Vector3.Z, 90);
      child.setParent(parent);
      SceneInspection inspection = new SceneInspection();
      inspection.setScene(game);
      inspection.select(child.getGameObject().id);
      inspection.translateSelected3D(Vector3.X, true, 0);
      assertEquals(0f, child.position.x, .0001f);
      assertEquals(1f, child.position.y, .0001f);

      var session = inspection.beginTransform3DEdit();
      Transform3DStateProxy baseline = new Transform3DStateProxy(inspection.selectedTransform3D());
      parent.position.x = 1;
      assertThrows(
          IllegalStateException.class,
          () ->
              inspection.commitTransform3D(
                  session,
                  new SceneInspection.Transform3DState(
                      baseline.x,
                      baseline.y,
                      baseline.z,
                      baseline.qx,
                      baseline.qy,
                      baseline.qz,
                      baseline.qw,
                      baseline.sx,
                      baseline.sy,
                      baseline.sz)));
    }
  }

  @Test
  public void translationSnapRoundsOnlyTheDraggedIncrementAndPreservesOffGridBaseline() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("off grid");
      Transform3D transform = object.addComponent(new Transform3D());
      transform.position.set(.23f, .31f, .17f);
      transform.rotation.setFromAxis(Vector3.Z, 90);
      SceneInspection inspection = new SceneInspection();
      inspection.setScene(game);
      inspection.select(object.id);
      inspection.translateSelected3D(new Vector3(.6f, 0, 0), true, .5f);
      assertEquals(.23f, transform.position.x, .0001f);
      assertEquals(.81f, transform.position.y, .0001f);
      assertEquals(.17f, transform.position.z, .0001f);
    }
  }

  @Test
  public void localRotationScaleAnd2DManipulationAreHappyPathAtomicEdits() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("manipulated");
      Transform3D transform = object.addComponent(new Transform3D());
      object.transform.position.set(.23f, .31f);
      object.transform.rotation = .17f;
      object.transform.scale.set(1.3f, .7f);
      SceneInspection inspection = new SceneInspection();
      inspection.setScene(game);
      inspection.select(object.id);

      inspection.manipulateSelected2D(1.14f, -.36f, .34f, 1.2f, .8f, .1f);
      assertEquals(1.33f, object.transform.position.x, .0001f);
      assertEquals(-.09f, object.transform.position.y, .0001f);
      assertEquals(.47f, object.transform.rotation, .0001f);
      assertEquals(1.56f, object.transform.scale.x, .0001f);
      assertEquals(.56f, object.transform.scale.y, .0001f);
      assertTrue(inspection.undo());
      assertEquals(.23f, object.transform.position.x, .0001f);
      assertEquals(.31f, object.transform.position.y, .0001f);
      assertEquals(.17f, object.transform.rotation, .0001f);
      assertEquals(1.3f, object.transform.scale.x, .0001f);

      inspection.rotateSelected3D(Vector3.Z, .63f, true, .2f);
      assertEquals((float) Math.sin(.3f), transform.rotation.z, .0001f);
      assertEquals((float) Math.cos(.3f), transform.rotation.w, .0001f);
      assertTrue(inspection.undo());
      assertEquals(0f, transform.rotation.z, .0001f);

      inspection.scaleSelected3D(new Vector3(1.5f, .75f, 1), true, .25f);
      assertEquals(1.5f, transform.scale.x, .0001f);
      assertEquals(.75f, transform.scale.y, .0001f);
      assertEquals(1f, transform.scale.z, .0001f);
      assertTrue(inspection.undo());
      assertEquals(1f, transform.scale.x, .0001f);
      assertEquals(1f, transform.scale.y, .0001f);
    }
  }

  @Test
  public void worldScaleOnQuarterTurn2DMapsToTheMatchingLocalAxisAndRejectsShear() {
    var quarterTurn = new SceneInspection.Transform2DState(2, 3, (float) (Math.PI / 2), 4, 5);
    var scaled = SceneInspection.scaledTransform2D(quarterTurn, 0, 1.5f, false, 0);
    assertEquals(4f, scaled.scaleX(), .0001f);
    assertEquals(7.5f, scaled.scaleY(), .0001f);
    var halfTurn = new SceneInspection.Transform2DState(0, 0, (float) Math.PI, 4, 5);
    var halfTurnScaled = SceneInspection.scaledTransform2D(halfTurn, 0, 1.5f, false, 0);
    assertEquals(6f, halfTurnScaled.scaleX(), .0001f);
    assertEquals(5f, halfTurnScaled.scaleY(), .0001f);
    var rotated45 = new SceneInspection.Transform2DState(0, 0, (float) (Math.PI / 4), 4, 5);
    assertThrows(
        IllegalStateException.class,
        () -> SceneInspection.scaledTransform2D(rotated45, 0, 1.5f, false, 0));
  }

  @Test
  public void stale2DGizmoSessionCannotCommitAfterSelectionChangesAwayAndBack() {
    try (Game game = new Game()) {
      GameObject first = game.createObject("first");
      GameObject second = game.createObject("second");
      SceneInspection inspection = new SceneInspection();
      inspection.setScene(game);
      inspection.select(first.id);
      var session = inspection.beginTransform2DEdit();
      inspection.select(second.id);
      inspection.select(first.id);
      assertThrows(
          IllegalStateException.class,
          () ->
              inspection.commitTransform2D(
                  session, new SceneInspection.Transform2DState(1, 0, 0, 1, 1)));
      assertEquals(0f, first.transform.position.x, 0f);
    }
  }

  @Test
  public void cameraProjectionStampRejectsCameraMotionAndViewportResize() {
    float[] initial = new Matrix4().idt().val.clone();
    assertTrue(RuntimeInspector.cameraProjectionUnchanged(initial, 1000, 680, initial, 1000, 680));
    float[] moved = new Matrix4().setToTranslation(1, 0, 0).val.clone();
    assertFalse(RuntimeInspector.cameraProjectionUnchanged(initial, 1000, 680, moved, 1000, 680));
    assertFalse(RuntimeInspector.cameraProjectionUnchanged(initial, 1000, 680, initial, 900, 600));
  }

  private record Transform3DStateProxy(
      float x,
      float y,
      float z,
      float qx,
      float qy,
      float qz,
      float qw,
      float sx,
      float sy,
      float sz) {
    Transform3DStateProxy(SceneInspection.Transform3DState s) {
      this(s.x(), s.y(), s.z(), s.qx(), s.qy(), s.qz(), s.qw(), s.scaleX(), s.scaleY(), s.scaleZ());
    }
  }

  static final class GuardedTransformComponent extends Component implements TransformEditPolicy {
    @Override
    public boolean allowsInspectorTransformEdit() {
      return false;
    }
  }
}
