package org.javity.core;

/** Component policy for tools that directly edit an object's transform. */
public interface TransformEditPolicy {
  /** Whether inspector-style direct transform edits preserve this component's invariants. */
  boolean allowsInspectorTransformEdit();
}
