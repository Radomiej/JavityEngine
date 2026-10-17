package org.javity.ui.yoga;

/** Creates opaque Yoga nodes for the active platform implementation. */
public interface YogaBackend {
  YogaNode createNode();
}
