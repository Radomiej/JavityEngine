package org.javity.ui.inspector;

import static org.junit.Assert.*;

import org.javity.core.*;
import org.javity.render.lighting.*;
import org.junit.Test;

public class LightEditTest {
  @Test
  public void oneAtomicLightEditUndoRedoAndExternalConflict() {
    try (Game game = new Game()) {
      var object = game.createObject("spot");
      object.addComponent(new Transform3D());
      var light = object.addComponent(new SpotLight3D());
      var inspection = new SceneInspection();
      inspection.setScene(game);
      inspection.select(object.id);
      var before = SceneInspection.lightState(light);
      var next = new SceneInspection.LightState(.2f, .3f, .4f, 2, 8, 40, 3);
      inspection.commitLightEdit(inspection.beginLightEdit(light), next);
      assertEquals(next, SceneInspection.lightState(light));
      inspection.edit(SceneInspection.Transform3DField.POSITION_X, 4);
      assertTrue(inspection.undo());
      assertEquals(0, object.getComponent(Transform3D.class).position.x, 0);
      assertTrue(inspection.undo());
      assertEquals(before, SceneInspection.lightState(light));
      assertTrue(inspection.redo());
      assertEquals(next, SceneInspection.lightState(light));
      assertTrue(inspection.redo());
      assertEquals(4, object.getComponent(Transform3D.class).position.x, 0);
      var edit = inspection.beginLightEdit(light);
      light.setIntensity(3);
      assertTrue(inspection.undo());
      assertThrows(IllegalStateException.class, () -> inspection.commitLightEdit(edit, before));
      assertFalse(inspection.undo());
    }
  }

  @Test
  public void componentSceneSelectionAndValidationConflicts() {
    try (Game game = new Game();
        Game second = new Game()) {
      var object = game.createObject("light");
      object.addComponent(new Transform3D());
      var light = object.addComponent(new DirectionalLight3D());
      var inspection = new SceneInspection();
      inspection.setScene(game);
      inspection.select(object.id);
      var edit = inspection.beginLightEdit(light);
      var valid = new SceneInspection.LightState(1, 1, 1, 2, 0, 0, 0);
      assertThrows(
          IllegalArgumentException.class,
          () ->
              inspection.commitLightEdit(
                  edit, new SceneInspection.LightState(1, 1, 1, 2, 5, 0, 0)));
      object.removeComponent(light);
      assertThrows(IllegalStateException.class, () -> inspection.commitLightEdit(edit, valid));
      inspection.setScene(second);
      assertThrows(IllegalStateException.class, () -> inspection.commitLightEdit(edit, valid));
      assertFalse(inspection.canUndo());
    }
  }

  @Test
  public void selectionSceneIdentityAndRemovedUndoAreIndependentConflicts() {
    try (Game game = new Game();
        Game next = new Game()) {
      var owner = game.createObject("sun");
      owner.addComponent(new Transform3D());
      var sun = owner.addComponent(new DirectionalLight3D());
      var other = game.createObject("other");
      var replacement = next.createObject("same id");
      replacement.addComponent(new Transform3D());
      replacement.addComponent(new DirectionalLight3D());
      var inspection = new SceneInspection();
      inspection.setScene(game);
      inspection.select(owner.id);
      var state = new SceneInspection.LightState(1, 1, 1, 2, 0, 0, 0);
      var selectionEdit = inspection.beginLightEdit(sun);
      inspection.select(other.id);
      assertThrows(
          IllegalStateException.class, () -> inspection.commitLightEdit(selectionEdit, state));
      inspection.select(owner.id);
      var sceneEdit = inspection.beginLightEdit(sun);
      inspection.setScene(next);
      inspection.select(replacement.id);
      assertEquals(owner.id, replacement.id);
      assertThrows(IllegalStateException.class, () -> inspection.commitLightEdit(sceneEdit, state));
      inspection.setScene(game);
      inspection.select(owner.id);
      inspection.commitLightEdit(inspection.beginLightEdit(sun), state);
      owner.removeComponent(sun);
      assertFalse(inspection.undo());
      assertFalse(inspection.canRedo());
    }
  }
}
