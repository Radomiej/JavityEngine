package org.javity.physics2d;

import com.badlogic.gdx.math.Vector2;
import org.javity.core.GameObject;

/** Copied hit geometry, no native pointers. */
public record RayHit2D(
    GameObject object, Collider2D collider, Vector2 point, Vector2 normal, float fraction) {
  public RayHit2D {
    point = point.cpy();
    normal = normal.cpy();
  }

  public Vector2 point() {
    return point.cpy();
  }

  public Vector2 normal() {
    return normal.cpy();
  }
}
