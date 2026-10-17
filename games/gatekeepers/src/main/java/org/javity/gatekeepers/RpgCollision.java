package org.javity.gatekeepers;

import com.badlogic.gdx.math.*;
import java.util.*;

/** Game-owned static obstacles. Shared by movement and line of sight; independent of graphics. */
public final class RpgCollision {
  private final List<Rectangle> obstacles = new ArrayList<>();

  public RpgCollision() {
    rectangle(170, 1040, 160, 92);
    rectangle(420, 1038, 143, 86);
    rectangle(172, 324, 136, 81);
    rectangle(465, 250, 127, 76);
    rectangle(250, 890, 120, 42);
    // Pond is not traversable; its enclosing stones agree with the terrain illustration.
    rectangle(270, 1220, 240, 160);
    for (var tree : RpgContent.trees()) {
      float s = tree.scale();
      rectangle(tree.x() - 8 * s, tree.y() - 46 * s, 16 * s, 16 * s);
    }
    float ax = RpgContent.ARENA_LEFT,
        ay = RpgContent.ARENA_BOTTOM,
        aw = RpgContent.ARENA_WIDTH,
        ah = RpgContent.ARENA_HEIGHT;
    rectangle(ax - 20, ay - 20, aw + 40, 12);
    rectangle(ax - 20, ay + ah + 8, aw + 40, 12);
    rectangle(ax - 20, ay, 12, ah);
    rectangle(ax + aw + 8, ay, 12, ah);
  }

  public void rectangle(float x, float y, float width, float height) {
    obstacles.add(new Rectangle(x, y, width, height));
  }

  public boolean blocked(float x, float y, float radius) {
    for (var r : obstacles)
      if (x + radius > r.x
          && x - radius < r.x + r.width
          && y + radius > r.y
          && y - radius < r.y + r.height) return true;
    return false;
  }

  public boolean clearLine(float x1, float y1, float x2, float y2) {
    for (var r : obstacles)
      if (Intersector.intersectSegmentRectangle(x1, y1, x2, y2, r)) return false;
    return true;
  }

  public void move(Vector2 point, float targetX, float targetY, float radius) {
    float dx = targetX - point.x, dy = targetY - point.y;
    int steps =
        Math.max(
            1, (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)) / Math.max(1, radius * .5f)));
    for (int i = 0; i < steps; i++) {
      float nx = point.x + dx / steps, ny = point.y + dy / steps;
      if (!blocked(nx, point.y, radius)) point.x = nx;
      if (!blocked(point.x, ny, radius)) point.y = ny;
    }
  }
}
