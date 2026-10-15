package org.javity.ui.effects;

import com.badlogic.gdx.scenes.scene2d.Actor;
import java.util.Objects;
import org.javity.core.GameObject;
import org.javity.effects.FloatTarget;
import org.javity.ui.declarative.UiDocument;

/** Validated animation targets for native UI properties that do not affect Yoga layout. */
public final class UiTargets {
  private UiTargets() {}

  /**
   * Creates a target for the alpha channel of a keyed actor. The document, actor, and game object
   * are borrowed; the returned target does not change their ownership.
   */
  public static FloatTarget alpha(GameObject owner, UiDocument document, String key) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(document, "document");
    if (key == null || key.isBlank()) throw new IllegalArgumentException("UI key is required");
    if (owner.isDestroyed() || owner.game.isDisposed())
      throw new IllegalArgumentException("Target owner is not live");
    Actor actor = document.actor(key);
    if (actor == null) throw new IllegalArgumentException("No UI actor for key: " + key);
    return new FloatTarget() {
      @Override
      public GameObject gameObject() {
        return owner;
      }

      @Override
      public Object identity() {
        return actor;
      }

      @Override
      public String propertyKey() {
        return "ui.alpha";
      }

      @Override
      public boolean isValid() {
        return !document.isClosed()
            && document.actor(key) == actor
            && !owner.isDestroyed()
            && !owner.game.isDisposed();
      }

      @Override
      public float get() {
        requireValid();
        return actor.getColor().a;
      }

      @Override
      public void set(float value) {
        requireValid();
        if (!Float.isFinite(value) || value < 0 || value > 1)
          throw new IllegalArgumentException("UI alpha must be finite and within [0, 1]");
        actor.getColor().a = value;
      }

      private void requireValid() {
        if (!isValid()) throw new IllegalStateException("UI effect target is no longer valid");
      }
    };
  }
}
