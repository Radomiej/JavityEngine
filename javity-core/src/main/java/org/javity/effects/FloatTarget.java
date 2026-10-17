package org.javity.effects;

import org.javity.core.GameObject;

/** A typed, validated view of one mutable float property. */
public interface FloatTarget {
  GameObject gameObject();

  Object identity();

  String propertyKey();

  boolean isValid();

  float get();

  void set(float value);
}
