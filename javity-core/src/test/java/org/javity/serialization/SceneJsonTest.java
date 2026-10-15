package org.javity.serialization;

import static org.junit.Assert.*;

import java.util.Map;
import org.javity.core.*;
import org.javity.render.*;
import org.junit.Test;

public class SceneJsonTest {
  @Test
  public void prefabRoundTripClonesComponentsAndHierarchy() {
    SceneJson json = SceneJson.standard();
    try (Game source = new Game();
        Game target = new Game()) {
      var parent = source.createObject("parent").setPosition(10, 20).addTag("player");
      var p = parent.addComponent(new Transform3D());
      p.position.set(1, 2, 3);
      var child = source.createObject("child");
      var c = child.addComponent(new Transform3D());
      c.setParent(p);
      child.addComponent(ShapeRenderer2D.circle(10));
      child.setActive(false);
      Prefab prefab = new Prefab(json.write(source.getObjects()));
      var copied = prefab.instantiate(target, json);
      assertEquals(2, copied.size());
      assertEquals(10, copied.get(0).transform.position.x, 0);
      assertTrue(copied.get(0).hasTag("player"));
      assertFalse(copied.get(1).isActive());
      assertSame(
          copied.get(0).getComponent(Transform3D.class),
          copied.get(1).getComponent(Transform3D.class).getParent());
      assertNotSame(p, copied.get(0).getComponent(Transform3D.class));
    }
  }

  @Test
  public void rejectsUnknownTypeVersionAndRollsBack() {
    SceneJson json = SceneJson.standard();
    try (Game g = new Game()) {
      assertThrows(
          IllegalArgumentException.class, () -> json.read(g, "{\"version\":99,\"objects\":[]}"));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              json.read(
                  g,
                  "{\"version\":1,\"objects\":[{\"name\":\"x\",\"components\":[{\"type\":\"java.lang.Runtime\",\"data\":{}}]}]}"));
      assertEquals(0, g.getObjects().size());
      g.createObject("bad").addComponent(new Component() {});
      assertThrows(IllegalArgumentException.class, () -> json.write(g.getObjects()));
    }
  }

  @Test
  public void shapeMaterialsFailSerializationWhileOrdinaryShapeStylesStillRoundTrip() {
    SceneJson json = SceneJson.standard();
    try (Game source = new Game(); Game target = new Game()) {
      ShapeRenderer2D ordinary = ShapeRenderer2D.rectangle(24, 12);
      ordinary.cornerRadius = 3;
      ordinary.color.set(.2f, .4f, .6f, .8f);
      source.createObject("ordinary-shape").addComponent(ordinary);
      var restored = json.read(target, json.write(source.getObjects())).get(0);
      ShapeRenderer2D copy = restored.getComponent(ShapeRenderer2D.class);
      assertEquals(3, copy.cornerRadius, 0);
      assertEquals(.2f, copy.color.r, 0);

      try (Game materialGame = new Game()) {
        ShapeRenderer2D shape = ShapeRenderer2D.rectangle(10, 10);
        shape.material =
            ShapeMaterial2D.custom(
                "vec4 shapeColor(vec2 local, vec4 tint) { return tint * u_tint; }",
                Map.of("u_tint", ShapeMaterial2D.UniformType.VEC4));
        materialGame.createObject("custom-material").addComponent(shape);
        IllegalArgumentException error =
            assertThrows(IllegalArgumentException.class, () -> json.write(materialGame.getObjects()));
        assertTrue(error.getMessage().toLowerCase().contains("material"));
        assertTrue(error.getMessage().toLowerCase().contains("codec"));
      }
    }
  }
}
