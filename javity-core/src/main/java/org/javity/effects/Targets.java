package org.javity.effects;

import com.badlogic.gdx.graphics.Color;
import java.util.Objects;
import java.util.function.DoubleSupplier;
import org.javity.core.GameObject;
import org.javity.render.ShapeRenderer2D;
import org.javity.render.SpriteRenderer2D;

/** Supported effect properties. Targets cannot address another object's state. */
public final class Targets {
  private Targets() {}

  public static FloatTarget positionX(GameObject object) {
    return transform(
        object,
        "position.x",
        () -> object.transform.position.x,
        v -> object.transform.position.x = v);
  }

  public static FloatTarget positionY(GameObject object) {
    return transform(
        object,
        "position.y",
        () -> object.transform.position.y,
        v -> object.transform.position.y = v);
  }

  public static FloatTarget scaleX(GameObject object) {
    return transform(
        object, "scale.x", () -> object.transform.scale.x, v -> object.transform.scale.x = v);
  }

  public static FloatTarget scaleY(GameObject object) {
    return transform(
        object, "scale.y", () -> object.transform.scale.y, v -> object.transform.scale.y = v);
  }

  public static FloatTarget rotation(GameObject object) {
    return transform(
        object, "rotation", () -> object.transform.rotation, v -> object.transform.rotation = v);
  }

  public static FloatTarget spriteRed(SpriteRenderer2D component) {
    return color(component, component.tint, "sprite.color.r", 0);
  }

  public static FloatTarget spriteGreen(SpriteRenderer2D component) {
    return color(component, component.tint, "sprite.color.g", 1);
  }

  public static FloatTarget spriteBlue(SpriteRenderer2D component) {
    return color(component, component.tint, "sprite.color.b", 2);
  }

  public static FloatTarget spriteAlpha(SpriteRenderer2D component) {
    return color(component, component.tint, "sprite.color.a", 3);
  }

  public static FloatTarget shapeRed(ShapeRenderer2D component) {
    return color(component, component.color, "shape.color.r", 0);
  }

  public static FloatTarget shapeGreen(ShapeRenderer2D component) {
    return color(component, component.color, "shape.color.g", 1);
  }

  public static FloatTarget shapeBlue(ShapeRenderer2D component) {
    return color(component, component.color, "shape.color.b", 2);
  }

  public static FloatTarget shapeAlpha(ShapeRenderer2D component) {
    return color(component, component.color, "shape.color.a", 3);
  }

  private static FloatTarget transform(
      GameObject object, String key, DoubleSupplier get, FloatSetter set) {
    Objects.requireNonNull(object, "object");
    if (object.isDestroyed()) throw new IllegalArgumentException("Target object is destroyed");
    return new Target(object, object, key, () -> !object.isDestroyed(), get, set);
  }

  private static FloatTarget color(
      org.javity.core.Component component, Color color, String key, int channel) {
    Objects.requireNonNull(component, "component");
    Objects.requireNonNull(color, "color");
    GameObject owner = component.getGameObject();
    if (owner == null)
      throw new IllegalArgumentException("Color component must belong to an object");
    if (owner.isDestroyed() || !owner.getComponents().contains(component))
      throw new IllegalArgumentException("Color component is not attached to a live object");
    return new Target(
        owner,
        component,
        key,
        () ->
            !owner.isDestroyed()
                && component.getGameObject() == owner
                && owner.getComponents().contains(component),
        () -> channel(color, channel),
        v -> setChannel(color, channel, v));
  }

  private static float channel(Color c, int channel) {
    return switch (channel) {
      case 0 -> c.r;
      case 1 -> c.g;
      case 2 -> c.b;
      default -> c.a;
    };
  }

  private static void setChannel(Color c, int channel, float value) {
    switch (channel) {
      case 0 -> c.r = value;
      case 1 -> c.g = value;
      case 2 -> c.b = value;
      default -> c.a = value;
    }
  }

  private record Target(
      GameObject gameObject,
      Object identity,
      String propertyKey,
      Valid valid,
      DoubleSupplier read,
      FloatSetter write)
      implements FloatTarget {
    @Override
    public boolean isValid() {
      return valid.check();
    }

    @Override
    public float get() {
      if (!isValid()) throw new IllegalStateException("Effect target is no longer valid");
      return finite((float) read.getAsDouble());
    }

    @Override
    public void set(float value) {
      if (!isValid()) throw new IllegalStateException("Effect target is no longer valid");
      write.accept(finite(value));
    }

    private static float finite(float value) {
      if (!Float.isFinite(value)) throw new IllegalArgumentException("Target value must be finite");
      return value;
    }
  }

  @FunctionalInterface
  private interface Valid {
    boolean check();
  }

  @FunctionalInterface
  private interface FloatSetter {
    void accept(float value);
  }
}
