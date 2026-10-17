package org.javity.ui.declarative;

/** Immutable primitive key data for declarative UI handlers. */
public record UiKeyEvent(int keyCode, String key, boolean shift, boolean ctrl, boolean alt) {
  public UiKeyEvent {
    if (key == null) throw new IllegalArgumentException("key is required");
  }
}
