package org.javity.render.lighting;

import static org.junit.Assert.*;

import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.attributes.*;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.math.Vector3;
import org.javity.core.*;
import org.junit.Test;

public class LightSnapshotTest {
  private static GameObject object(Game game) {
    GameObject object = game.createObject("light");
    object.addComponent(new Transform3D());
    return object;
  }

  private static Environment select(Game game, Environment legacy) {
    return RenderEnvironment3D.select(game, legacy, 2, 4, 2);
  }

  @Test
  public void legacyFallbackUnchanged() {
    try (Game game = new Game()) {
      Environment legacy = new Environment().add(new DirectionalLight().set(1, 1, 1, 0, -1, 0));
      object(game).addComponent(new SpotLight3D());
      game.start();
      assertSame(legacy, select(game, legacy));
      assertEquals(
          1,
          legacy.get(DirectionalLightsAttribute.class, DirectionalLightsAttribute.Type)
              .lights
              .size);
    }
  }

  @Test
  public void sceneOverrideDoesNotDoubleSun() {
    try (Game game = new Game()) {
      game.addSystem(new RenderEnvironment3D()).setAmbient(.1f, .2f, .3f);
      DirectionalLight3D light = object(game).addComponent(new DirectionalLight3D());
      light.setColor(.2f, .3f, .4f);
      light.setIntensity(2);
      game.start();
      Environment legacy = new Environment().add(new DirectionalLight());
      Environment scene = select(game, legacy);
      assertNotSame(legacy, scene);
      assertTrue(scene.has(SceneLightingAttribute.Type));
      var dirs =
          scene.get(DirectionalLightsAttribute.class, DirectionalLightsAttribute.Type).lights;
      assertEquals(1, dirs.size);
      assertEquals(.4f, dirs.first().color.r, 0);
      assertEquals(.2f, scene.get(ColorAttribute.class, ColorAttribute.AmbientLight).color.g, 0);
      light.setColor(1, 1, 1);
      assertEquals(.4f, dirs.first().color.r, 0);
    }
  }

  @Test
  public void removalDisablesLight() {
    try (Game game = new Game()) {
      RenderEnvironment3D scene = game.addSystem(new RenderEnvironment3D());
      GameObject owner = object(game);
      var light = owner.addComponent(new PointLight3D());
      game.start();
      assertEquals(
          1,
          scene
              .snapshot(2, 4, 2)
              .get(PointLightsAttribute.class, PointLightsAttribute.Type)
              .lights
              .size);
      light.setEnabled(false);
      assertFalse(scene.snapshot(2, 4, 2).has(PointLightsAttribute.Type));
      light.setEnabled(true);
      owner.setActive(false);
      assertFalse(scene.snapshot(2, 4, 2).has(PointLightsAttribute.Type));
      owner.setActive(true);
      owner.removeComponent(light);
      assertFalse(scene.snapshot(2, 4, 2).has(PointLightsAttribute.Type));
    }
  }

  @Test
  public void parentRotationDefinesDirection() {
    try (Game game = new Game()) {
      RenderEnvironment3D scene = game.addSystem(new RenderEnvironment3D());
      Transform3D parent = object(game).getComponent(Transform3D.class);
      parent.rotation.set(Vector3.Y, 90);
      parent.scale.set(2, 3, 4);
      parent.position.set(8, 0, 0);
      GameObject owner = object(game);
      owner.getComponent(Transform3D.class).setParent(parent);
      owner.addComponent(new DirectionalLight3D());
      owner.addComponent(new SpotLight3D());
      game.start();
      Environment env = scene.snapshot(2, 4, 2);
      Vector3 direction =
          env.get(DirectionalLightsAttribute.class, DirectionalLightsAttribute.Type)
              .lights
              .first()
              .direction;
      assertEquals(-1, direction.x, 1e-5);
      assertEquals(1, direction.len(), 1e-5);
      var spot = env.get(SpotLightsAttribute.class, SpotLightsAttribute.Type).lights.first();
      assertEquals(direction, spot.direction);
      assertEquals(8, spot.position.x, 0);
      parent.scale.z = 0;
      assertThrows(IllegalArgumentException.class, () -> scene.snapshot(2, 4, 2));
    }
  }

  @Test
  public void invalidValuesRefused() {
    var light = new SpotLight3D();
    var scene = new RenderEnvironment3D();
    assertThrows(IllegalArgumentException.class, () -> light.setColor(-1, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> light.setColor(0, Float.NaN, 0));
    assertThrows(IllegalArgumentException.class, () -> light.setIntensity(Float.POSITIVE_INFINITY));
    assertThrows(IllegalArgumentException.class, () -> light.setIntensity(-1));
    assertThrows(IllegalArgumentException.class, () -> light.setRange(0));
    assertThrows(IllegalArgumentException.class, () -> light.setCutoffDegrees(90));
    assertThrows(IllegalArgumentException.class, () -> light.setCutoffDegrees(0));
    assertThrows(IllegalArgumentException.class, () -> light.setExponent(-1));
    assertThrows(IllegalArgumentException.class, () -> scene.setAmbient(0, -1, 0));
    assertThrows(IllegalArgumentException.class, () -> scene.setFog(1, 1, 1, 5, 5));
    assertThrows(IllegalArgumentException.class, () -> scene.setFog(1, 1, 1, -1, 5));
  }

  @Test
  public void deterministicOverflow() {
    try (Game game = new Game()) {
      var scene = game.addSystem(new RenderEnvironment3D());
      GameObject first = object(game), second = object(game);
      second.addComponent(new PointLight3D()).setColor(0, 1, 0);
      first.addComponent(new PointLight3D()).setColor(1, 0, 0);
      first.addComponent(new PointLight3D()).setColor(0, 0, 1);
      first.addComponent(new DirectionalLight3D());
      first.addComponent(new SpotLight3D());
      game.start();
      var points =
          scene.snapshot(0, 2, 0).get(PointLightsAttribute.class, PointLightsAttribute.Type).lights;
      assertEquals(1, points.get(0).color.r, 0);
      assertEquals(1, points.get(1).color.b, 0);
      assertEquals(new RenderEnvironment3D.Overflow(1, 1, 1), scene.overflow());
      assertFalse(scene.snapshot(0, 0, 0).has(PointLightsAttribute.Type));
      assertEquals(new RenderEnvironment3D.Overflow(1, 3, 1), scene.overflow());
    }
  }

  @Test
  public void sceneSwapDoesNotRetainLights() {
    try (Game first = new Game();
        Game second = new Game()) {
      first.addSystem(new RenderEnvironment3D());
      second.addSystem(new RenderEnvironment3D());
      GameObject a = object(first), b = object(second);
      assertEquals(a.id, b.id);
      a.addComponent(new DirectionalLight3D());
      first.start();
      second.start();
      Environment legacy = new Environment();
      assertTrue(select(first, legacy).has(DirectionalLightsAttribute.Type));
      assertFalse(select(second, legacy).has(DirectionalLightsAttribute.Type));
    }
  }

  @Test
  public void fogAndRangesAreCopiedAndCleared() {
    try (Game game = new Game()) {
      var scene = game.addSystem(new RenderEnvironment3D());
      scene.setFog(.2f, .3f, .4f, 2, 10);
      var point = object(game).addComponent(new PointLight3D());
      point.setRange(7);
      point.setIntensity(3);
      var spot = object(game).addComponent(new SpotLight3D());
      spot.setRange(8);
      spot.setCutoffDegrees(30);
      spot.setExponent(2);
      game.start();
      Environment env = scene.snapshot(2, 4, 2);
      var marker = env.get(SceneLightingAttribute.class, SceneLightingAttribute.Type);
      assertEquals(7, marker.pointRange(0), 0);
      assertEquals(8, marker.spotRange(0), 0);
      assertEquals(2, marker.fogNear(), 0);
      assertEquals(10, marker.fogFar(), 0);
      assertEquals(
          3,
          env.get(PointLightsAttribute.class, PointLightsAttribute.Type).lights.first().intensity,
          0);
      point.setRange(99);
      assertEquals(7, marker.pointRange(0), 0);
      scene.clearFog();
      assertFalse(scene.snapshot(2, 4, 2).has(ColorAttribute.Fog));
    }
  }
}
