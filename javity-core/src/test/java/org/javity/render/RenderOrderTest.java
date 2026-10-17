package org.javity.render;

import static org.junit.Assert.*;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class RenderOrderTest {
  @Test
  public void screenShapesAfterWorldAndStableTies() {
    SpriteRenderer2D world = new SpriteRenderer2D(new TextureRegion());
    world.layer = 100;
    ShapeRenderer2D screen = ShapeRenderer2D.circle(2);
    screen.space = ShapeRenderer2D.Space.SCREEN;
    screen.layer = -100;
    ShapeRenderer2D first = ShapeRenderer2D.circle(3), second = ShapeRenderer2D.circle(4);
    var list = new ArrayList<Render2D>(List.of(screen, first, second, world));
    list.sort(GdxRenderer.RENDER_ORDER);
    assertEquals(List.of(first, second, world, screen), list);
  }
}
