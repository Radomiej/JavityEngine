package org.javity.physics2d;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Shape;
import java.util.*;
import java.util.function.Consumer;
import org.javity.core.*;

/** Independent of visual scale. Several colliders on one object form a compound body. */
public abstract sealed class Collider2D extends Component
    permits BoxCollider2D, CircleCollider2D, PolygonCollider2D {
  private final Vector2 offset = new Vector2();
  private boolean trigger;
  private float density = 1, friction = .2f, restitution;
  private int category = 1, mask = 0xffff;
  long revision;

  private record Listener(Class<? extends Component> type, Consumer<GameObject> callback) {}

  private final List<Listener> listeners = new ArrayList<>();

  public boolean isTrigger() {
    return trigger;
  }

  public Vector2 getOffset() {
    return offset.cpy();
  }

  float offsetX() {
    return offset.x;
  }

  float offsetY() {
    return offset.y;
  }

  public float getDensity() {
    return density;
  }

  public float getFriction() {
    return friction;
  }

  public float getRestitution() {
    return restitution;
  }

  public int getCategory() {
    return category;
  }

  public int getMask() {
    return mask;
  }

  public Collider2D setTrigger(boolean value) {
    if (trigger != value) {
      trigger = value;
      revision++;
    }
    return this;
  }

  public Collider2D setOffset(float x, float y) {
    RigidBody2D.finite(x);
    RigidBody2D.finite(y);
    if (offset.x != x || offset.y != y) {
      offset.set(x, y);
      revision++;
    }
    return this;
  }

  public Collider2D setDensity(float value) {
    RigidBody2D.positiveOrZero(value);
    if (density != value) {
      density = value;
      revision++;
    }
    return this;
  }

  public Collider2D setFriction(float value) {
    RigidBody2D.positiveOrZero(value);
    if (friction != value) {
      friction = value;
      revision++;
    }
    return this;
  }

  public Collider2D setRestitution(float value) {
    RigidBody2D.positiveOrZero(value);
    if (restitution != value) {
      restitution = value;
      revision++;
    }
    return this;
  }

  public Collider2D setFilter(int category, int mask) {
    if ((category & ~0xffff) != 0 || (mask & ~0xffff) != 0)
      throw new IllegalArgumentException("Filters are unsigned 16-bit values");
    if (this.category != category || this.mask != mask) {
      this.category = category;
      this.mask = mask;
      revision++;
    }
    return this;
  }

  public Runnable onContactEnter(Class<? extends Component> type, Consumer<GameObject> callback) {
    var listener = new Listener(Objects.requireNonNull(type), Objects.requireNonNull(callback));
    listeners.add(listener);
    return () -> listeners.remove(listener);
  }

  void fireEnter(GameObject other) {
    for (Listener listener : List.copyOf(listeners)) {
      if (!isInitialized()
          || !isEnabled()
          || !getGameObject().isActive()
          || getGameObject().isDestroyed()
          || other.isDestroyed()) break;
      if (other.hasComponent(listener.type)) listener.callback.accept(other);
    }
  }

  @Override
  public void onDestroy() {
    listeners.clear();
  }

  abstract Shape createShape(float unitsPerMeter);

  static float meters(float value, float scale) {
    float result = value / scale;
    RigidBody2D.finite(result);
    return result;
  }

  static void positive(float value) {
    RigidBody2D.finite(value);
    if (value <= 0) throw new IllegalArgumentException("Collider dimensions must be positive");
  }
}
