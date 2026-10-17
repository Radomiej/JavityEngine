package org.javity.physics2d;

/** Implement on a Component to receive contacts for its object's collider pairs. */
public interface CollisionListener2D {
  default void onCollisionEnter(Contact2D contact) {}

  default void onCollisionExit(Contact2D contact) {}

  default void onTriggerEnter(Contact2D contact) {}

  default void onTriggerExit(Contact2D contact) {}
}
