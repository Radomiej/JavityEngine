package org.javity.render.lighting;

import static org.junit.Assert.*;

import com.badlogic.gdx.graphics.g3d.attributes.*;
import org.javity.core.*;
import org.junit.Test;

public class LightSnapshotReuseTest {
  @Test
  public void shrinkingCapacityAndCountsClearPooledLightsAndRanges() {
    try (Game game = new Game()) {
      var scene = game.addSystem(new RenderEnvironment3D());
      var owner = game.createObject("lights");
      owner.addComponent(new Transform3D());
      owner.addComponent(new DirectionalLight3D());
      var first = owner.addComponent(new PointLight3D());
      var second = owner.addComponent(new PointLight3D());
      var spot = owner.addComponent(new SpotLight3D());
      first.setRange(7);
      second.setRange(9);
      spot.setRange(11);
      game.start();
      scene.snapshot(1, 2, 1);
      scene.snapshot(1, 2, 1); // Warm both staging buffers.
      var env = scene.snapshot(0, 1, 0);
      assertFalse(env.has(DirectionalLightsAttribute.Type));
      assertFalse(env.has(SpotLightsAttribute.Type));
      assertEquals(1, env.get(PointLightsAttribute.class, PointLightsAttribute.Type).lights.size);
      var marker = env.get(SceneLightingAttribute.class, SceneLightingAttribute.Type);
      assertEquals(7, marker.pointRange(0), 0);
      assertEquals(1, marker.pointRange(1), 0);
      assertEquals(1, marker.spotRange(0), 0);
      var copy = (SceneLightingAttribute) marker.copy();
      assertEquals(0, copy.compareTo(marker));
      assertEquals(new RenderEnvironment3D.Overflow(1, 1, 1), scene.overflow());
      first.setEnabled(false);
      second.setEnabled(false);
      spot.setEnabled(false);
      assertSame(env, scene.snapshot(1, 2, 1));
      assertFalse(env.has(PointLightsAttribute.Type));
      assertFalse(env.has(SpotLightsAttribute.Type));
      marker = env.get(SceneLightingAttribute.class, SceneLightingAttribute.Type);
      assertEquals(1, marker.pointRange(0), 0);
      assertEquals(1, marker.spotRange(0), 0);
      first.setEnabled(true);
      first.setRange(23);
      scene.snapshot(1, 2, 1);
      scene.snapshot(1, 2, 1);
      assertEquals(7, copy.pointRange(0), 0);
      assertEquals(1, copy.pointRange(1), 0);
      assertTrue(
          copy.compareTo(env.get(SceneLightingAttribute.class, SceneLightingAttribute.Type)) < 0);
    }
  }

  @Test
  public void warmSnapshotsReuseTwoIndependentSetsOfCopiedValues() {
    try (Game game = new Game()) {
      var scene = game.addSystem(new RenderEnvironment3D());
      var owner = game.createObject("lights");
      var transform = owner.addComponent(new Transform3D());
      var directional = owner.addComponent(new DirectionalLight3D());
      var point = owner.addComponent(new PointLight3D());
      var spot = owner.addComponent(new SpotLight3D());
      game.start();
      var env = scene.snapshot(1, 1, 1);
      var firstMarker = env.get(SceneLightingAttribute.class, SceneLightingAttribute.Type);
      var firstPoint =
          env.get(PointLightsAttribute.class, PointLightsAttribute.Type).lights.first();
      var firstSpot = env.get(SpotLightsAttribute.class, SpotLightsAttribute.Type).lights.first();
      var firstDirectional =
          env.get(DirectionalLightsAttribute.class, DirectionalLightsAttribute.Type).lights.first();
      scene.snapshot(1, 1, 1);
      var secondMarker = env.get(SceneLightingAttribute.class, SceneLightingAttribute.Type);
      assertNotSame(firstMarker, secondMarker);
      assertNotSame(
          firstPoint,
          env.get(PointLightsAttribute.class, PointLightsAttribute.Type).lights.first());
      scene.snapshot(1, 1, 1);
      assertSame(firstMarker, env.get(SceneLightingAttribute.class, SceneLightingAttribute.Type));
      assertSame(
          firstPoint,
          env.get(PointLightsAttribute.class, PointLightsAttribute.Type).lights.first());
      assertSame(
          firstSpot, env.get(SpotLightsAttribute.class, SpotLightsAttribute.Type).lights.first());
      assertSame(
          firstDirectional,
          env.get(DirectionalLightsAttribute.class, DirectionalLightsAttribute.Type)
              .lights
              .first());
      point.setColor(3, 4, 5);
      point.setIntensity(6);
      point.setRange(17);
      spot.setRange(19);
      directional.setIntensity(2);
      transform.position.set(7, 8, 9);
      assertEquals(1, firstPoint.color.r, 0);
      assertEquals(0, firstPoint.position.x, 0);
      assertEquals(10, firstMarker.pointRange(0), 0);
      assertEquals(10, firstMarker.spotRange(0), 0);
      scene.snapshot(1, 1, 1);
      var currentPoint =
          env.get(PointLightsAttribute.class, PointLightsAttribute.Type).lights.first();
      assertEquals(3, currentPoint.color.r, 0);
      assertEquals(6, currentPoint.intensity, 0);
      assertEquals(7, currentPoint.position.x, 0);
      assertEquals(
          17, env.get(SceneLightingAttribute.class, SceneLightingAttribute.Type).pointRange(0), 0);
    }
  }

  @Test
  public void failedStagingDoesNotChangePublishedValuesOrOverflowEvenAfterRepeatedFailures() {
    try (Game game = new Game()) {
      var scene = game.addSystem(new RenderEnvironment3D());
      var owner = game.createObject("lights");
      var transform = owner.addComponent(new Transform3D());
      var point = owner.addComponent(new PointLight3D());
      var directional = owner.addComponent(new DirectionalLight3D());
      owner.addComponent(new SpotLight3D());
      game.start();
      scene.snapshot(1, 1, 0);
      var env = scene.snapshot(1, 1, 0);
      var marker = env.get(SceneLightingAttribute.class, SceneLightingAttribute.Type);
      var copiedPoint =
          env.get(PointLightsAttribute.class, PointLightsAttribute.Type).lights.first();
      var overflow = scene.overflow();
      point.setRange(99);
      point.setColor(2, 3, 4);
      transform.position.set(5, 6, 7);
      directional.setColor(Float.MAX_VALUE, 1, 1);
      directional.setIntensity(2);
      scene.setFog(1, 2, 3, 4, 5);
      for (int i = 0; i < 3; i++) {
        assertThrows(IllegalArgumentException.class, () -> scene.snapshot(1, 1, 1));
        assertSame(marker, env.get(SceneLightingAttribute.class, SceneLightingAttribute.Type));
        assertEquals(10, marker.pointRange(0), 0);
        assertEquals(1, copiedPoint.color.r, 0);
        assertEquals(0, copiedPoint.position.x, 0);
        assertFalse(env.has(ColorAttribute.Fog));
        assertEquals(overflow, scene.overflow());
      }
      directional.setIntensity(1);
      assertSame(env, scene.snapshot(1, 1, 1));
      assertEquals(
          99, env.get(SceneLightingAttribute.class, SceneLightingAttribute.Type).pointRange(0), 0);
      assertEquals(
          2,
          env.get(PointLightsAttribute.class, PointLightsAttribute.Type).lights.first().color.r,
          0);
      assertTrue(env.has(ColorAttribute.Fog));
      // Overflow lights still undergo transform validation, even with zero capacity.
      transform.scale.z = 0;
      assertThrows(IllegalArgumentException.class, () -> scene.snapshot(0, 0, 0));
    }
  }
}
