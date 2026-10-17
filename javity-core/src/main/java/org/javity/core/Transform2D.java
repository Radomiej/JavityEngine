package org.javity.core;

import com.badlogic.gdx.math.Vector2;

/** World units and radians, Y up; visualOffset affects rendering only. */
public final class Transform2D {
  public final Vector2 position = new Vector2();
  public final Vector2 scale = new Vector2(1, 1);
  public final Vector2 visualOffset = new Vector2();
  public float rotation;
}
