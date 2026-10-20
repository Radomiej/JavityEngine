package org.javity.gltf;

import static org.junit.Assert.*;

import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.attributes.*;
import net.mgsx.gltf.scene3d.lights.PointLightEx;
import org.javity.core.*;
import org.javity.render.lighting.*;
import org.junit.Test;

public class PbrEnvironmentTest {
  @Test
  public void scenePointRangeMetadataSurvivesThePbrEnvironmentAdapter() {
    try (Game game = new Game()) {
      RenderEnvironment3D source = game.addSystem(new RenderEnvironment3D());
      GameObject object = game.createObject("point");
      object.addComponent(new Transform3D());
      PointLight3D point = object.addComponent(new PointLight3D());
      point.setRange(7);
      point.setIntensity(3);
      game.start();
      game.step(0);
      PbrShaderProvider provider = new PbrShaderProvider(12, 2, 4, 2);
      Environment original = source.snapshot(2, 4, 2);
      Environment adapted = provider.adaptEnvironment(original);
      assertEquals(
          7,
          adapted.get(SceneLightingAttribute.class, SceneLightingAttribute.Type).pointRange(0),
          0);
      assertEquals(
          7,
          original.get(SceneLightingAttribute.class, SceneLightingAttribute.Type).pointRange(0),
          0);
      assertEquals(
          3,
          original
              .get(PointLightsAttribute.class, PointLightsAttribute.Type)
              .lights
              .first()
              .intensity,
          0);
      Environment explicitRange = new Environment();
      PointLightEx ranged = new PointLightEx();
      ranged.range = 7f;
      explicitRange.add(ranged);
      Environment adaptedExplicit = provider.adaptEnvironment(explicitRange);
      assertSame(
          ranged,
          adaptedExplicit
              .get(PointLightsAttribute.class, PointLightsAttribute.Type)
              .lights
              .first());
      provider.dispose();
    }
  }

  @Test
  public void separateSourcesKeepTheirShadowAndLightsUntilViewEnds() {
    PbrShaderProvider provider = new PbrShaderProvider(12, 2, 4, 2);
    Environment original = new Environment();
    original.add(new com.badlogic.gdx.graphics.g3d.environment.PointLight());
    com.badlogic.gdx.graphics.g3d.environment.ShadowMap shadow =
        new com.badlogic.gdx.graphics.g3d.environment.ShadowMap() {
          @Override
          public com.badlogic.gdx.math.Matrix4 getProjViewTrans() {
            return new com.badlogic.gdx.math.Matrix4();
          }

          @Override
          public com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor getDepthMap() {
            return null;
          }
        };
    original.shadowMap = shadow;
    Environment firstGroup = provider.adaptEnvironment(original);
    Environment secondGroup = provider.adaptEnvironment(new Environment());
    assertNotSame(firstGroup, secondGroup);
    assertSame(shadow, firstGroup.shadowMap);
    assertNull(secondGroup.shadowMap);
    assertEquals(
        1, firstGroup.get(PointLightsAttribute.class, PointLightsAttribute.Type).lights.size);
    assertNull(secondGroup.get(PointLightsAttribute.class, PointLightsAttribute.Type));
    provider.beginView();
    Environment empty = new Environment();
    assertNull(
        provider
            .adaptEnvironment(empty)
            .get(PointLightsAttribute.class, PointLightsAttribute.Type));
    assertNull(provider.adaptEnvironment(null));
    provider.contextRestored();
    provider.dispose();
    provider.dispose();
    assertNull(firstGroup.shadowMap);
    assertEquals(0, firstGroup.size());
    assertThrows(IllegalStateException.class, () -> provider.adaptEnvironment(empty));
  }

  @Test
  public void legacyDiffuseAndUnknownMaterialAttributesFailBeforeShaderAllocation() {
    PbrShaderProvider provider = new PbrShaderProvider(12, 2, 4, 0);
    var renderable = new com.badlogic.gdx.graphics.g3d.Renderable();
    renderable.material =
        new com.badlogic.gdx.graphics.g3d.Material(ColorAttribute.createDiffuse(1, 0, 0, 1));
    assertThrows(IllegalArgumentException.class, () -> provider.getShader(renderable));
    renderable.material =
        new com.badlogic.gdx.graphics.g3d.Material(FloatAttribute.createShininess(10));
    assertThrows(IllegalArgumentException.class, () -> provider.getShader(renderable));
    var pbr =
        new com.badlogic.gdx.graphics.g3d.Material(
            new net.mgsx.gltf.scene3d.attributes.PBRColorAttribute(
                net.mgsx.gltf.scene3d.attributes.PBRColorAttribute.BaseColorFactor,
                com.badlogic.gdx.graphics.Color.RED),
            net.mgsx.gltf.scene3d.attributes.PBRFloatAttribute.createRoughness(.5f));
    PbrShaderProvider.validateMaterial(pbr);
    provider.dispose();
  }

  @Test
  public void outputModeIsExplicitAndDefaultRemainsDisplayGamma() {
    PbrShaderProvider display = new PbrShaderProvider(12, 2, 4, 0);
    PbrShaderProvider linear =
        new PbrShaderProvider(12, 2, 4, 0, PbrShaderProvider.OutputColorSpace.LINEAR);
    assertEquals(PbrShaderProvider.OutputColorSpace.SRGB, display.outputColorSpace());
    assertEquals(PbrShaderProvider.OutputColorSpace.LINEAR, linear.outputColorSpace());
    display.dispose();
    linear.dispose();
    assertThrows(NullPointerException.class, () -> new PbrShaderProvider(12, 2, 4, 0, null));
  }

  @Test
  public void incompatibleFogAndSpotSemanticsAreRejectedExplicitly() {
    PbrShaderProvider provider = new PbrShaderProvider(12, 2, 4, 2);
    Environment fog = new Environment();
    fog.set(new ColorAttribute(ColorAttribute.Fog, 1, 1, 1, 1));
    assertThrows(IllegalArgumentException.class, () -> provider.adaptEnvironment(fog));
    Environment spots = new Environment();
    spots.add(new com.badlogic.gdx.graphics.g3d.environment.SpotLight());
    assertThrows(IllegalArgumentException.class, () -> provider.adaptEnvironment(spots));
    provider.dispose();
  }
}
