package org.javity.gltf;

import com.badlogic.gdx.graphics.g3d.Attributes;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.attributes.PointLightsAttribute;
import net.mgsx.gltf.scene3d.lights.PointLightEx;
import net.mgsx.gltf.scene3d.shaders.PBRShader;
import net.mgsx.gltf.scene3d.shaders.PBRShaderConfig;
import org.javity.render.lighting.SceneLightingAttribute;

/**
 * Pinned gdx-gltf 2.3.0 PBR extension: finite point range follows Javity's squared cutoff. The
 * upstream BRDF, materials, textures, shadows and color handling remain upstream code.
 */
final class RangePbrShader extends PBRShader {
  private final int[] pointRanges;

  RangePbrShader(Renderable renderable, PBRShaderConfig config, String prefix) {
    super(renderable, config, prefix);
    pointRanges = new int[config.numPointLights];
    for (int i = 0; i < pointRanges.length; i++)
      pointRanges[i] = register("u_javityPointRanges[" + i + "]");
  }

  @Override
  protected void bindLights(Renderable renderable, Attributes attributes) {
    super.bindLights(renderable, attributes);
    SceneLightingAttribute scene =
        attributes.get(SceneLightingAttribute.class, SceneLightingAttribute.Type);
    PointLightsAttribute points =
        attributes.get(PointLightsAttribute.class, PointLightsAttribute.Type);
    for (int i = 0; i < pointRanges.length; i++) {
      float range = 0; // Unbounded legacy lights preserve upstream 1/(1+distance^2).
      if (points != null && i < points.lights.size) {
        if (scene != null) range = scene.pointRange(i);
        else if (points.lights.get(i) instanceof PointLightEx extended && extended.range != null)
          range = extended.range;
      }
      if (!Float.isFinite(range) || range < 0)
        throw new IllegalArgumentException("Point range must be finite and nonnegative");
      set(pointRanges[i], range);
    }
  }

  /** Patch the expanded Apache2 upstream shader at exact release-specific seams; fail on drift. */
  static String fragmentSource(String upstream) {
    String declaration = "uniform PointLight u_pointLights[numPointLights];";
    String result =
        replaceOnce(
            upstream,
            declaration,
            declaration + "\nuniform float u_javityPointRanges[numPointLights];");
    String contribution =
        "PBRLightContribs contrib = getPointLightContribution(pbrSurface, u_pointLights[i]);";
    return replaceOnce(
        result,
        contribution,
        contribution
            + "\n"
            + "        float javityRange = u_javityPointRanges[i];\n"
            + "        vec3 javityDelta = u_pointLights[i].position - v_position.xyz;\n"
            + "        float javityDistance = length(javityDelta);\n"
            + "        float javityCutoff = javityRange > 0.0 ? max(0.0, 1.0 - javityDistance /"
            + " javityRange) : 1.0;\n"
            + "        float javityAttenuation = javityRange > 0.0 ? javityCutoff * javityCutoff *"
            + " (1.0 + dot(javityDelta,javityDelta)) : 1.0;\n"
            + "        contrib.diffuse *= javityAttenuation;\n"
            + "        contrib.specular *= javityAttenuation;\n"
            + "        contrib.transmission *= javityAttenuation;");
  }

  private static String replaceOnce(String source, String expected, String replacement) {
    int first = source.indexOf(expected);
    if (first < 0 || source.indexOf(expected, first + expected.length()) >= 0)
      throw new IllegalStateException("gdx-gltf 2.3.0 PBR shader source drift at " + expected);
    return source.substring(0, first) + replacement + source.substring(first + expected.length());
  }
}
