package org.javity.render.lighting;

import static org.junit.Assert.*;
import java.util.Arrays;
import org.junit.Test;

public class SceneLightingOrderingTest {
  @Test public void portableRangeOrderPreservesJdkFloatArraySemantics() {
    float[][] cases = {{}, {0}, {-0f}, {1}, {-1}, {1,2}, {1,2,3}, {Float.NEGATIVE_INFINITY},
        {Float.POSITIVE_INFINITY}, {Float.NaN}, {Float.intBitsToFloat(0x7fc00001)}};
    for (float[] a : cases) for (float[] b : cases) {
      int expected = Integer.signum(Arrays.compare(a,b));
      assertEquals(expected, Integer.signum(new SceneLightingAttribute(a,new float[0],0,10)
          .compareTo(new SceneLightingAttribute(b,new float[0],0,10))));
      assertEquals(expected, Integer.signum(new SceneLightingAttribute(new float[0],a,0,10)
          .compareTo(new SceneLightingAttribute(new float[0],b,0,10))));
    }
  }
  @Test public void pointThenSpotThenFogHaveStablePriority() {
    var a = new SceneLightingAttribute(new float[]{1},new float[]{9},5,10);
    var b = new SceneLightingAttribute(new float[]{2},new float[]{0},0,1);
    assertTrue(a.compareTo(b)<0);
    assertTrue(new SceneLightingAttribute(new float[]{1},new float[]{2},0,1)
        .compareTo(new SceneLightingAttribute(new float[]{1},new float[]{3},0,1))<0);
    assertTrue(new SceneLightingAttribute(new float[]{1},new float[]{2},1,10)
        .compareTo(new SceneLightingAttribute(new float[]{1},new float[]{2},2,5))<0);
    assertTrue(new SceneLightingAttribute(new float[]{1},new float[]{2},1,5)
        .compareTo(new SceneLightingAttribute(new float[]{1},new float[]{2},1,10))<0);
  }
}
