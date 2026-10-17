package org.javity.ui.inspector;

import static org.junit.Assert.*;
import java.util.*;
import org.javity.core.*;
import org.junit.Test;

public class SceneInspectionFilterTest {
  @Test public void filterByNameIdTag() {
    try (Game game = new Game()) {
      GameObject target = game.createObject("Pickaxe").addTag("HeldTool");
      game.createObject("other");
      SceneInspection model = new SceneInspection();
      model.setScene(game);
      for (String query : List.of("PICK", "heldTOOL", String.valueOf(target.id)))
        assertEquals(List.of(target.id), ids(model.visibleRows(query, Set.of())));
      assertTrue(model.visibleRows("absent", Set.of()).isEmpty());
    }
  }

  @Test public void matchingChildKeepsAncestors() {
    try (Game game = new Game()) {
      GameObject parent = game.createObject("parent");
      Transform3D root = parent.addComponent(new Transform3D());
      GameObject child = game.createObject("needle");
      child.addComponent(new Transform3D()).setParent(root);
      game.createObject("unrelated");
      SceneInspection model = new SceneInspection();
      model.setScene(game);
      assertEquals(List.of(parent.id, child.id), ids(model.visibleRows("Needle", Set.of())));
      assertEquals(List.of(0, 1), model.visibleRows("needle", Set.of()).stream().map(SceneInspection.Row::depth).toList());
    }
  }

  @Test public void collapsePreservesSelectionAndDoesNotMutateCallerSet() {
    try (Game game = new Game()) {
      GameObject parent = game.createObject("parent");
      Transform3D root = parent.addComponent(new Transform3D());
      GameObject child = game.createObject("child");
      child.addComponent(new Transform3D()).setParent(root);
      SceneInspection model = new SceneInspection();
      model.setScene(game);
      model.select(child.id);
      Set<Integer> collapsed = Set.of(parent.id);
      assertEquals(List.of(parent.id), ids(model.visibleRows("child", collapsed)));
      assertEquals(Integer.valueOf(child.id), model.capture().selectedId());
      assertEquals(Set.of(parent.id), collapsed);
      assertEquals(List.of(parent.id, child.id), ids(model.visibleRows("child", Set.of())));
    }
  }

  @Test public void sceneSwapClearsCollapseAndSelection() {
    try (Game first = new Game(); Game second = new Game()) {
      GameObject a = first.createObject("first"), b = second.createObject("second");
      assertEquals(a.id, b.id);
      RuntimeInspectionSession session = new RuntimeInspectionSession();
      session.bind(first);
      session.inspection.select(a.id);
      session.collapsed.add(a.id);
      session.query = "first";
      session.bind(first);
      assertFalse(session.collapsed.isEmpty());
      session.bind(second);
      assertTrue(session.collapsed.isEmpty());
      assertEquals("", session.query);
      assertNull(session.inspection.capture().selectedId());
    }
  }

  @Test public void inactiveDuplicateComponentsRemainVisible() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("inactive").addTag("tag");
      object.addComponent(new Marker());
      object.addComponent(new Marker()).setEnabled(false);
      object.setActive(false);
      SceneInspection model = new SceneInspection();
      model.setScene(game);
      model.select(object.id);
      var rows = model.visibleRows("TAG", Set.of());
      assertEquals(1, rows.size());
      assertFalse(rows.getFirst().active());
      assertEquals(2, rows.getFirst().components());
      assertTrue(model.capture().details().toString().contains("disabled"));
      assertThrows(UnsupportedOperationException.class, () -> rows.clear());
    }
  }
  private static List<Integer> ids(List<SceneInspection.Row> rows) {
    return rows.stream().map(SceneInspection.Row::id).toList();
  }
  private static final class Marker extends Component {}
}
