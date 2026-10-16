package org.javity.ui.inspector;

import java.util.HashSet;
import java.util.Set;
import org.javity.core.Game;

/** Inspector-owned view state, bound to the same scene identity as selection and history. */
final class RuntimeInspectionSession {
  final SceneInspection inspection = new SceneInspection();
  final Set<Integer> collapsed = new HashSet<>();
  String query = "";
  private Game scene;

  boolean bind(Game value) {
    if (scene == value) return false;
    scene = value;
    collapsed.clear();
    query = "";
    inspection.setScene(value);
    return true;
  }
}
