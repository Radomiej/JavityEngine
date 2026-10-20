package org.javity.render.lighting;

import static org.junit.Assert.*;

import com.badlogic.gdx.graphics.g3d.Attribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import java.util.HashSet;
import java.util.Objects;
import org.junit.Test;

public class SceneLightingEqualityTest {
  private static SceneLightingAttribute value(float point, float spot, float near, float far) {
    return new SceneLightingAttribute(new float[] {point}, new float[] {spot}, near, far);
  }

  private static void assertSameValue(SceneLightingAttribute a, SceneLightingAttribute b) {
    assertTrue(Objects.equals(a, b));
    assertTrue(Objects.equals(b, a));
    assertEquals(0, a.compareTo(b));
    assertEquals(0, b.compareTo(a));
    assertEquals(a.hashCode(), b.hashCode());
  }

  private static void assertDifferentValue(SceneLightingAttribute a, SceneLightingAttribute b) {
    assertFalse(Objects.equals(a, b));
    assertFalse(Objects.equals(b, a));
    assertNotEquals(0, a.compareTo(b));
    assertNotEquals(0, b.compareTo(a));
  }

  @Test
  public void eachActiveRangeAndFogValueParticipatesInEquality() {
    var original = value(1, 2, 3, 4);
    assertSameValue(original, value(1, 2, 3, 4));
    assertDifferentValue(original, value(9, 2, 3, 4));
    assertDifferentValue(original, value(1, 9, 3, 4));
    assertDifferentValue(original, value(1, 2, 9, 4));
    assertDifferentValue(original, value(1, 2, 3, 9));
    assertDifferentValue(
        original, new SceneLightingAttribute(new float[] {1, 2}, new float[] {2}, 3, 4));
    assertDifferentValue(
        original, new SceneLightingAttribute(new float[] {1}, new float[] {2, 3}, 3, 4));
  }

  @Test
  public void signedZeroAndNanFollowFloatComparisonInEveryField() {
    float alternateNan = Float.intBitsToFloat(0x7fc00001);
    float[][] cases = {
      {0f, -0f}, {Float.NaN, alternateNan}, {Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY}
    };
    for (float[] pair : cases) {
      for (int field = 0; field < 4; field++) {
        float[] a = {1, 2, 3, 4}, b = {1, 2, 3, 4};
        a[field] = pair[0];
        b[field] = pair[1];
        var left = value(a[0], a[1], a[2], a[3]);
        var right = value(b[0], b[1], b[2], b[3]);
        if (Float.compare(pair[0], pair[1]) == 0) assertSameValue(left, right);
        else {
          assertDifferentValue(left, right);
          assertNotEquals(left.hashCode(), right.hashCode());
        }
      }
    }
  }

  @Test
  public void retainedCapacityAndStaleSuffixDoNotAffectEqualityOrCopies() {
    var expanded = new SceneLightingAttribute(new float[] {1, 99}, new float[] {2, 98}, 3, 4);
    expanded.prepare(8, 12);
    expanded.setPointRange(7, 97);
    expanded.setSpotRange(11, 96);
    expanded.finish(1, 1, 3, 4);
    var compact = value(1, 2, 3, 4);
    assertSameValue(expanded, compact);
    var copy = (SceneLightingAttribute) expanded.copy();
    assertNotSame(expanded, copy);
    assertSameValue(expanded, copy);
    assertEquals(1, copy.pointRange(1), 0);
    assertEquals(1, copy.spotRange(1), 0);
    expanded.setPointRange(0, 5);
    assertSameValue(compact, copy);
    assertDifferentValue(expanded, copy);
    expanded.finish(0, 0, 3, 4);
    assertSameValue(expanded, new SceneLightingAttribute(new float[0], new float[0], 3, 4));
  }

  @Test
  public void hashCollisionsDoNotImplyEquality() {
    int bits = Float.floatToIntBits(1);
    var a =
        new SceneLightingAttribute(
            new float[] {1, Float.intBitsToFloat(bits + 31)}, new float[0], 0, 1);
    var b =
        new SceneLightingAttribute(
            new float[] {Float.intBitsToFloat(bits + 1), 1}, new float[0], 0, 1);
    assertEquals(a.hashCode(), b.hashCode());
    assertDifferentValue(a, b);
    var values = new HashSet<SceneLightingAttribute>();
    values.add(a);
    values.add(b);
    assertEquals(2, values.size());
    assertTrue(values.contains(a.copy()));
    assertTrue(values.contains(b.copy()));
  }

  @Test
  public void objectDispatchRejectsNullUnrelatedObjectsAndOtherAttributeTypes() {
    Attribute marker = value(1, 2, 3, 4);
    Attribute ambient = new ColorAttribute(ColorAttribute.AmbientLight);
    assertTrue(marker.equals(marker));
    assertFalse(marker.equals(null));
    assertFalse(marker.equals(new Object()));
    assertFalse(marker.equals(ambient));
    assertFalse(ambient.equals(marker));
    assertNotEquals(0, marker.compareTo(ambient));
    assertNotEquals(0, ambient.compareTo(marker));
  }
}
