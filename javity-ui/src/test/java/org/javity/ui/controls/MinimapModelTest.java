package org.javity.ui.controls;

import static org.junit.Assert.*;

import org.junit.Test;

public class MinimapModelTest {
  @Test
  public void projectsWorldCoordinatesWithBottomLeftOriginAndClampsEdges() {
    var model = new MinimapModel(-100, -50, 400, 200);
    assertEquals(.5f, model.normalizedX(100), .001f);
    assertEquals(.5f, model.normalizedY(50), .001f);
    assertEquals(0, model.normalizedX(-500), .001f);
    assertEquals(1, model.normalizedY(999), .001f);
  }

  @Test
  public void markersAreCopiedAndCanBeUpdatedWithoutOldSnapshotsChanging() {
    var model = new MinimapModel(0, 0, 100, 100);
    model.put("hero", 20, 30, 0xffcc88ff, 6);
    var before = model.markers();
    model.put("hero", 70, 80, 0xffffffff, 8);
    assertEquals(20, before.get(0).x(), .001f);
    assertEquals(70, model.markers().get(0).x(), .001f);
    model.remove("hero");
    assertTrue(model.markers().isEmpty());
  }

  @Test
  public void rejectsInvalidBoundsAndNonfiniteMarkerPositions() {
    assertThrows(IllegalArgumentException.class, () -> new MinimapModel(0, 0, 0, 10));
    var model = new MinimapModel(0, 0, 10, 10);
    assertThrows(IllegalArgumentException.class, () -> model.put("bad", Float.NaN, 0, 0, 5));
    assertThrows(IllegalArgumentException.class, () -> model.put("bad", 1, 1, 0, -1));
  }
}
