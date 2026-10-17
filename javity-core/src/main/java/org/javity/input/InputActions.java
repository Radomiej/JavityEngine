package org.javity.input;

import com.badlogic.gdx.InputAdapter;
import java.util.*;

/** Pollable keyboard actions. Put after Stage in InputMultiplexer; finishFrame after game.step. */
public final class InputActions extends InputAdapter {
  private final Map<String, Set<Integer>> bindings = new LinkedHashMap<>();
  private final Set<Integer> down = new HashSet<>();
  private final Set<String> pressed = new HashSet<>(), released = new HashSet<>();

  public void bind(String action, int... keys) {
    Objects.requireNonNull(action);
    Set<Integer> codes = new HashSet<>();
    for (int key : keys) codes.add(key);
    bindings.put(action, Set.copyOf(codes));
  }

  public boolean isDown(String action) {
    return bindings.getOrDefault(action, Set.of()).stream().anyMatch(down::contains);
  }

  public boolean justPressed(String action) {
    return pressed.contains(action);
  }

  public boolean justReleased(String action) {
    return released.contains(action);
  }

  public float axis(String negative, String positive) {
    return (isDown(positive) ? 1 : 0) - (isDown(negative) ? 1 : 0);
  }

  public boolean keyDown(int key) {
    for (String action : bindings.keySet())
      if (bindings.get(action).contains(key) && !isDown(action)) pressed.add(action);
    down.add(key);
    return bindings.values().stream().anyMatch(s -> s.contains(key));
  }

  public boolean keyUp(int key) {
    Map<String, Boolean> before = new HashMap<>();
    for (String a : bindings.keySet()) before.put(a, isDown(a));
    down.remove(key);
    for (String a : bindings.keySet()) if (before.get(a) && !isDown(a)) released.add(a);
    return bindings.values().stream().anyMatch(s -> s.contains(key));
  }

  public void finishFrame() {
    pressed.clear();
    released.clear();
  }

  public void releaseAll() {
    pressed.clear();
    for (String a : bindings.keySet()) if (isDown(a)) released.add(a);
    down.clear();
  }
}
