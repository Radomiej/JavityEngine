package org.javity.physics2d;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;

public final class CircleCollider2D extends Collider2D {
  private float radius;

  public CircleCollider2D(float radius) {
    setRadius(radius);
  }

  public float getRadius() {
    return radius;
  }

  public CircleCollider2D setRadius(float radius) {
    positive(radius);
    if (this.radius != radius) {
      this.radius = radius;
      revision++;
    }
    return this;
  }

  @Override
  Shape createShape(float scale) {
    float value = meters(radius, scale);
    positive(value);
    Vector2 center = new Vector2(meters(offsetX(), scale), meters(offsetY(), scale));
    CircleShape shape = new CircleShape();
    shape.setRadius(value);
    shape.setPosition(center);
    return shape;
  }
}
