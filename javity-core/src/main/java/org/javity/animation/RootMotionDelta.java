package org.javity.animation;

/** Immutable translation in model space; no world transform or physics movement is implied. */
public record RootMotionDelta(float x, float y, float z) {
  public RootMotionDelta {
    if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z))
      throw new IllegalArgumentException("Root-motion translation must be finite");
  }
}
