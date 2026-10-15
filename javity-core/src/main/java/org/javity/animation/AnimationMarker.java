package org.javity.animation;

/** Immutable named event position within an animation clip. */
public record AnimationMarker(String name, double normalizedTime) {
  public AnimationMarker {
    if (name == null || name.isBlank())
      throw new IllegalArgumentException("Marker name is required");
    if (!Double.isFinite(normalizedTime) || normalizedTime < 0 || normalizedTime > 1)
      throw new IllegalArgumentException("Marker time must be finite and between 0 and 1");
  }
}
