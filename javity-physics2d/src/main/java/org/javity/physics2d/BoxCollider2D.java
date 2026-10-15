package org.javity.physics2d;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;

public final class BoxCollider2D extends Collider2D {
  private float width, height;

  public BoxCollider2D(float width, float height) {
    setSize(width, height);
  }

  public float getWidth() {
    return width;
  }

  public float getHeight() {
    return height;
  }

  public BoxCollider2D setSize(float width, float height) {
    positive(width);
    positive(height);
    if (this.width != width || this.height != height) {
      this.width = width;
      this.height = height;
      revision++;
    }
    return this;
  }

  @Override
  Shape createShape(float scale) {
    float halfWidth = meters(width, scale) / 2, halfHeight = meters(height, scale) / 2;
    positive(halfWidth);
    positive(halfHeight);
    Vector2 center = new Vector2(meters(offsetX(), scale), meters(offsetY(), scale));
    PolygonShape shape = new PolygonShape();
    shape.setAsBox(halfWidth, halfHeight, center, 0);
    return shape;
  }
}
