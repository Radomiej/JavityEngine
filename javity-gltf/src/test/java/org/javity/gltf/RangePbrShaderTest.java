package org.javity.gltf;

import static org.junit.Assert.*;

import org.junit.Test;

public class RangePbrShaderTest {
  @Test
  public void pinnedPatchFailsOnMissingOrAmbiguousUpstreamSeams() {
    String declaration = "uniform PointLight u_pointLights[numPointLights];";
    String contribution =
        "PBRLightContribs contrib = getPointLightContribution(pbrSurface, u_pointLights[i]);";
    String source = declaration + "\n" + contribution;
    assertTrue(RangePbrShader.fragmentSource(source).contains("u_javityPointRanges[i]"));
    assertThrows(IllegalStateException.class, () -> RangePbrShader.fragmentSource(contribution));
    assertThrows(IllegalStateException.class, () -> RangePbrShader.fragmentSource(declaration));
    assertThrows(
        IllegalStateException.class,
        () -> RangePbrShader.fragmentSource(source + "\n" + contribution));
    assertThrows(
        IllegalStateException.class,
        () -> RangePbrShader.fragmentSource(source + "\n" + declaration));
  }
}
