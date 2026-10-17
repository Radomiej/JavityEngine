package org.javity.render;

import static org.junit.Assert.*;

import org.javity.core.Game;
import org.javity.serialization.SceneJson;
import org.junit.Test;

public class ShapeStylesTest {
  @Test
  public void stylesRoundTripThroughShapeCodec() {
    SceneJson json = SceneJson.standard();
    try (Game source = new Game();
        Game target = new Game()) {
      ShapeRenderer2D shape = ShapeRenderer2D.rectangle(30, 20);
      shape.antiAlias = false;
      shape.cornerRadius = 6;
      shape.lineCap = ShapeRenderer2D.LineCap.SQUARE;
      shape.lineJoin = ShapeRenderer2D.LineJoin.ROUND;
      shape.space = ShapeRenderer2D.Space.SCREEN;
      source.createObject("styled").addComponent(shape);

      var restored = json.read(target, json.write(source.getObjects())).get(0);
      ShapeRenderer2D copy = restored.getComponent(ShapeRenderer2D.class);
      assertFalse(copy.antiAlias);
      assertEquals(6, copy.cornerRadius, 0);
      assertEquals(ShapeRenderer2D.LineCap.SQUARE, copy.lineCap);
      assertEquals(ShapeRenderer2D.LineJoin.ROUND, copy.lineJoin);
      assertEquals(ShapeRenderer2D.Space.SCREEN, copy.space);
    }
  }

  @Test
  public void versionOneJsonWithoutNewFieldsUsesKindSpecificDefaults() {
    String document =
        """
        {"version":1,"objects":[{"name":"legacy","components":[
          {"type":"shape2d","data":{"kind":"POLYGON","points":[[0,0],[1,0],[0,1]],"color":"ffffffff"}},
          {"type":"shape2d","data":{"kind":"RECTANGLE","width":10,"height":5,"points":[],"color":"ffffffff"}}
        ]}]}
        """;
    try (Game game = new Game()) {
      var shapes =
          SceneJson.standard().read(game, document).get(0).getComponents(ShapeRenderer2D.class);
      assertFalse(shapes.get(0).antiAlias);
      assertTrue(shapes.get(1).antiAlias);
      for (ShapeRenderer2D shape : shapes) {
        assertEquals(0, shape.cornerRadius, 0);
        assertEquals(ShapeRenderer2D.LineCap.ROUND, shape.lineCap);
        assertEquals(ShapeRenderer2D.LineJoin.ROUND, shape.lineJoin);
        assertEquals(ShapeRenderer2D.Space.WORLD, shape.space);
      }
    }
  }

  @Test
  public void rejectsInvalidCornerRadiusOnReadAndWrite() {
    SceneJson json = SceneJson.standard();
    String document =
        """
        {"version":1,"objects":[{"name":"bad","components":[
          {"type":"shape2d","data":{"kind":"RECTANGLE","width":10,"height":5,"points":[],"color":"ffffffff","cornerRadius":-1}}
        ]}]}
        """;
    try (Game target = new Game();
        Game source = new Game()) {
      assertThrows(IllegalArgumentException.class, () -> json.read(target, document));
      ShapeRenderer2D shape = ShapeRenderer2D.rectangle(10, 5);
      shape.cornerRadius = Float.NaN;
      source.createObject("bad").addComponent(shape);
      assertThrows(IllegalArgumentException.class, () -> json.write(source.getObjects()));
    }
  }
}
