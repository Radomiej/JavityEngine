package org.javity.physics2d;

import org.javity.core.GameObject;

/** Snapshot with no native handles; callbacks may safely change the scene. */
public record Contact2D(GameObject other, Collider2D collider, Collider2D otherCollider) {}
