package org.javity.android;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** CPU-only multi-source movement and jump ownership, keyed by libGDX key codes/pointer IDs. */
final class FixtureInputState {
  private final Set<Integer> keyboard = new HashSet<>();
  private final Map<Integer, Set<Integer>> touch = new HashMap<>();
  private boolean keyboardJump;
  private final Set<Integer> jumpPointers = new HashSet<>();

  void keyboard(int key, boolean down) {
    if (down) keyboard.add(key);
    else keyboard.remove(key);
    if (key == com.badlogic.gdx.Input.Keys.SPACE) keyboardJump = down;
  }

  void touch(int key, int pointer, boolean down) {
    if (down) touch.computeIfAbsent(key, ignored -> new HashSet<>()).add(pointer);
    else {
      Set<Integer> pointers = touch.get(key);
      if (pointers != null) {
        pointers.remove(pointer);
        if (pointers.isEmpty()) touch.remove(key);
      }
    }
  }

  void jumpPointer(int pointer, boolean down) {
    if (down) jumpPointers.add(pointer);
    else jumpPointers.remove(pointer);
  }

  boolean held(int key) {
    return keyboard.contains(key) || !touch.getOrDefault(key, Set.of()).isEmpty();
  }

  boolean jumpHeld() {
    return keyboardJump || !jumpPointers.isEmpty();
  }

  void clear() {
    keyboard.clear();
    touch.clear();
    keyboardJump = false;
    jumpPointers.clear();
  }
}
