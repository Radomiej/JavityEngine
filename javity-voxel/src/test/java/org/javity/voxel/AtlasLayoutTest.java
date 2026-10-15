package org.javity.voxel;

import static org.junit.Assert.*;

import org.junit.Test;

public class AtlasLayoutTest {
  @Test
  public void mapsTopOriginTilesToInsetNormalizedUvCoordinates() {
    AtlasLayout layout = new AtlasLayout(80, 40, 16, 2, 4, 2);
    assertEquals(8, layout.tileCount());
    assertEquals(2.5f / 80f, layout.u0(0), 0.000001f);
    assertEquals(2.5f / 40f, layout.v0(0), 0.000001f);
    assertEquals(17.5f / 80f, layout.u1(0), 0.000001f);
    assertEquals(17.5f / 40f, layout.v1(0), 0.000001f);
    assertEquals(62.5f / 80f, layout.u0(3), 0.000001f);
    assertEquals(22.5f / 40f, layout.v0(4), 0.000001f);
    assertEquals(77.5f / 80f, layout.u1(7), 0.000001f);
    assertEquals(37.5f / 40f, layout.v1(7), 0.000001f);
    assertThrows(IllegalArgumentException.class, () -> layout.u0(8));
  }

  @Test
  public void rejectsInvalidAtlasMetadataOrCellsThatDoNotFit() {
    assertThrows(IllegalArgumentException.class, () -> new AtlasLayout(79, 40, 16, 2, 4, 2));
    assertThrows(IllegalArgumentException.class, () -> new AtlasLayout(80, 40, 16, -1, 4, 2));
    assertThrows(IllegalArgumentException.class, () -> new AtlasLayout(80, 40, 16, 2, 0, 2));
    assertThrows(IllegalArgumentException.class, () -> new AtlasLayout(80, 40, 16, 2, 5, 2));
    assertThrows(IllegalArgumentException.class, () -> new AtlasLayout(80, 40, 0, 2, 4, 2));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AtlasLayout(
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AtlasLayout(80, 40, Integer.MAX_VALUE, Integer.MAX_VALUE, 1, 1));
  }
}
