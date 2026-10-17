package org.javity.physics2d;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import java.util.*;

/** Convex polygon, 3..8 vertices, local world units; clockwise/counterclockwise accepted. */
public final class PolygonCollider2D extends Collider2D {
  private List<Vector2> vertices;

  public PolygonCollider2D(Vector2... vertices) {
    setVertices(Arrays.asList(vertices));
  }

  public List<Vector2> getVertices() {
    return vertices.stream().map(Vector2::cpy).toList();
  }

  public PolygonCollider2D setVertices(List<Vector2> points) {
    if (points.size() < 3 || points.size() > 8)
      throw new IllegalArgumentException("Polygon needs 3..8 vertices");
    List<Vector2> copy =
        points.stream()
            .map(
                p -> {
                  RigidBody2D.finite(p.x);
                  RigidBody2D.finite(p.y);
                  return p.cpy();
                })
            .toList();
    double sign = 0;
    for (int i = 0; i < copy.size(); i++) {
      Vector2 a = copy.get(i),
          b = copy.get((i + 1) % copy.size()),
          c = copy.get((i + 2) % copy.size());
      double cross = ((double) b.x - a.x) * (c.y - b.y) - ((double) b.y - a.y) * (c.x - b.x);
      if (cross == 0 || !Double.isFinite(cross) || (sign != 0 && Math.signum(cross) != sign))
        throw new IllegalArgumentException("Strictly convex polygon required");
      sign = Math.signum(cross);
    }
    // Every other vertex must lie inside each directed edge (also rejects self-intersection).
    for (int i = 0; i < copy.size(); i++) {
      Vector2 a = copy.get(i), b = copy.get((i + 1) % copy.size());
      for (int j = 0; j < copy.size(); j++)
        if (j != i && j != (i + 1) % copy.size()) {
          Vector2 c = copy.get(j);
          double cross = ((double) b.x - a.x) * (c.y - a.y) - ((double) b.y - a.y) * (c.x - a.x);
          if (Math.signum(cross) != sign)
            throw new IllegalArgumentException("Convex, non-intersecting polygon required");
        }
    }
    vertices = copy;
    revision++;
    return this;
  }

  Shape createShape(float scale) {
    Vector2[] points =
        vertices.stream()
            .map(p -> new Vector2(meters(p.x + offsetX(), scale), meters(p.y + offsetY(), scale)))
            .toArray(Vector2[]::new);
    for (int i = 0; i < points.length; i++)
      if (points[i].dst(points[(i + 1) % points.length]) < .01f)
        throw new IllegalArgumentException("Polygon edges must be >= .01 meter");
    PolygonShape shape = new PolygonShape();
    try {
      shape.set(points);
      return shape;
    } catch (RuntimeException | Error e) {
      shape.dispose();
      throw e;
    }
  }
}
