package org.javity.render;

import static org.junit.Assert.*;

import java.util.Map;
import org.javity.core.Game;
import org.javity.effects.Clock;
import org.javity.services.TimeManager;
import org.junit.Test;

public class ShapeShaderAnimatorTest {
  @Test
  public void worldAndUiClocksRespectFreezeAndPauseAndBorrowMaterial() {
    try (Game game = new Game()) {
      TimeManager time = game.addSystem(new TimeManager());
      ShapeMaterial2D worldMaterial = animatedMaterial();
      ShapeMaterial2D uiMaterial = animatedMaterial();
      var worldObject = game.createObject("world");
      worldObject.addComponent(new ShapeShaderAnimator(worldMaterial, "u_time", 2, Clock.WORLD));
      var uiObject = game.createObject("ui");
      uiObject.addComponent(new ShapeShaderAnimator(uiMaterial, "u_time", 2, Clock.UI));
      game.start();

      time.freeze(.2);
      game.step(.1f);
      assertEquals(0, worldMaterial.getFloat("u_time"), 0);
      assertEquals(.2f, uiMaterial.getFloat("u_time"), 1e-6);
      game.step(.1f);
      assertEquals(.4f, uiMaterial.getFloat("u_time"), 1e-6);
      time.setBaseScale(1);
      game.step(.1f);
      assertEquals(.2f, worldMaterial.getFloat("u_time"), 1e-6);
      worldObject.destroy();
      game.step(.1f);
      assertEquals(.2f, worldMaterial.getFloat("u_time"), 1e-6);
    }
  }

  @Test
  public void animatorRejectsWrongUniformAndNonFiniteRateWithoutChangingMaterial() {
    ShapeMaterial2D material = animatedMaterial();
    assertThrows(
        IllegalArgumentException.class,
        () -> new ShapeShaderAnimator(material, "u_colorA", 1, Clock.WORLD));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ShapeShaderAnimator(material, "u_time", Float.NaN, Clock.WORLD));
    assertEquals(0, material.getFloat("u_time"), 0);
  }

  @Test
  public void animatorRejectsDeltaThatWouldOverflowItsFloatUniform() {
    ShapeMaterial2D material = animatedMaterial();
    ShapeShaderAnimator animator = new ShapeShaderAnimator(material, "u_time", 2, Clock.WORLD);
    assertThrows(IllegalStateException.class, () -> animator.onUpdate(Float.MAX_VALUE));
    assertEquals(0, material.getFloat("u_time"), 0);
  }

  private static ShapeMaterial2D animatedMaterial() {
    return ShapeMaterial2D.custom(
        "vec4 shapeColor(vec2 local, vec4 tint) { return tint * (0.5 + 0.5*sin(u_time)); }",
        Map.of("u_time", ShapeMaterial2D.UniformType.FLOAT));
  }
}
