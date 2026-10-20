package org.javity.input;

import com.badlogic.gdx.InputAdapter;
import java.util.*;

/**
 * Pollable actions. Put after UI input processors and call {@link #finishFrame()} after Game.step.
 */
public final class InputActions extends InputAdapter {
  private final LinkedHashMap<String, InputContext> contexts = new LinkedHashMap<>();
  private final Set<String> downControls = new HashSet<>();
  private final Map<String, Float> axes = new HashMap<>();
  private final Set<Integer> downKeys = new HashSet<>();
  private final Set<String> down = new HashSet<>(),
      pressed = new HashSet<>(),
      released = new HashSet<>();
  private final Map<String, Float> values = new HashMap<>();
  private final InputContext defaultContext = new InputContext("default", 0);

  public InputActions() {
    defaultContext.onChange(this::recompute);
    contexts.put(defaultContext.name(), defaultContext);
  }

  /**
   * Add a context. Higher priorities are evaluated first; equal priorities use registration order.
   */
  public InputContext addContext(String name, int priority) {
    if (contexts.containsKey(Objects.requireNonNull(name)))
      throw new IllegalArgumentException("Duplicate input context: " + name);
    InputContext context = new InputContext(name, priority);
    context.onChange(this::recompute);
    contexts.put(name, context);
    return context;
  }

  /**
   * Remove a registered context and immediately recompute affected actions. The default context
   * cannot be removed.
   */
  public boolean removeContext(String name) {
    if ("default".equals(name))
      throw new IllegalArgumentException("The default context cannot be removed");
    InputContext context = contexts.remove(name);
    if (context == null) return false;
    context.onChange(() -> {});
    recompute();
    return true;
  }

  public InputContext context(String name) {
    InputContext context = contexts.get(name);
    if (context == null) throw new IllegalArgumentException("Unknown input context: " + name);
    return context;
  }

  /** Legacy bindings belong to the always-active default context. */
  public void bind(String action, int... keys) {
    Objects.requireNonNull(action, "action");
    if (action.isBlank()) throw new IllegalArgumentException("action cannot be blank");
    List<InputBinding> replacement = new ArrayList<>(keys.length);
    for (int key : keys) replacement.add(new InputBinding.Key(key));
    defaultContext.replaceAction(action, replacement);
  }

  public boolean isDown(String action) {
    return down.contains(action);
  }

  public boolean justPressed(String action) {
    return pressed.contains(action);
  }

  public boolean justReleased(String action) {
    return released.contains(action);
  }

  public float value(String action) {
    return values.getOrDefault(action, 0f);
  }

  public float axis(String negative, String positive) {
    return value(positive) - value(negative);
  }

  /**
   * Feed a named controller/device button. Native backends can implement this tiny source contract.
   */
  public void buttonDown(String source, String control) {
    downControls.add(control(source, control));
    recompute();
  }

  public void buttonUp(String source, String control) {
    downControls.remove(control(source, control));
    recompute();
  }

  /** Feed a normalized analog value in [-1, 1]. */
  public void axisValue(String source, String axis, float value) {
    if (!Float.isFinite(value) || value < -1f || value > 1f)
      throw new IllegalArgumentException("axis value must be finite and within [-1, 1]");
    axes.put(control(source, axis), value);
    recompute();
  }

  /** Feed pointer button state; pointer coordinates remain available to the host/UI. */
  public void pointerDown(int pointer, int button) {
    buttonDown("pointer:" + pointer, Integer.toString(button));
  }

  public void pointerUp(int pointer, int button) {
    buttonUp("pointer:" + pointer, Integer.toString(button));
  }

  /** Feed a touch as a named held control. Cancel has release semantics. */
  public void touchDown(int pointer) {
    buttonDown("touch", Integer.toString(pointer));
  }

  public void touchUp(int pointer) {
    buttonUp("touch", Integer.toString(pointer));
  }

  public void touchCancelled(int pointer) {
    touchUp(pointer);
  }

  public void cancelTouches() {
    downControls.removeIf(s -> s.startsWith("touch\u0000"));
    recompute();
  }

  @Override
  public boolean keyDown(int key) {
    boolean changed = downKeys.add(key);
    if (changed) recompute();
    return contexts.values().stream().anyMatch(c -> c.active() && c.hasKey(key));
  }

  @Override
  public boolean keyUp(int key) {
    boolean changed = downKeys.remove(key);
    if (changed) recompute();
    return contexts.values().stream().anyMatch(c -> c.active() && c.hasKey(key));
  }

  @Override
  public boolean touchDown(int screenX, int screenY, int pointer, int button) {
    pointerDown(pointer, button);
    touchDown(pointer);
    return contexts.values().stream()
        .anyMatch(
            c ->
                c.active()
                    && (c.hasButton("pointer:" + pointer, Integer.toString(button))
                        || c.hasButton("touch", Integer.toString(pointer))));
  }

  @Override
  public boolean touchUp(int screenX, int screenY, int pointer, int button) {
    pointerUp(pointer, button);
    touchUp(pointer);
    return contexts.values().stream()
        .anyMatch(
            c ->
                c.active()
                    && (c.hasButton("pointer:" + pointer, Integer.toString(button))
                        || c.hasButton("touch", Integer.toString(pointer))));
  }

  @Override
  public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
    boolean handled =
        contexts.values().stream()
            .anyMatch(
                c ->
                    c.active()
                        && (c.hasButton("pointer:" + pointer, Integer.toString(button))
                            || c.hasButton("touch", Integer.toString(pointer))));
    pointerUp(pointer, button);
    touchCancelled(pointer);
    return handled;
  }

  /** Clear every source on window focus loss; held actions emit one release edge. */
  public void focusLost() {
    releaseAll();
  }

  public void finishFrame() {
    pressed.clear();
    released.clear();
  }

  public void releaseAll() {
    pressed.clear();
    downKeys.clear();
    downControls.clear();
    axes.clear();
    recompute();
  }

  private void recompute() {
    LinkedHashMap<String, Float> next = new LinkedHashMap<>();
    List<InputContext> ordered = new ArrayList<>(contexts.values());
    ordered.sort(Comparator.comparingInt(InputContext::priority).reversed());
    Set<String> blockedControls = new HashSet<>();
    for (InputContext context : ordered) {
      if (!context.active()) continue;
      for (String action : context.actions()) {
        if (next.containsKey(action)) continue;
        float v = context.value(action, downKeys, downControls, axes, blockedControls);
        if (v != 0f) next.put(action, v);
      }
      blockedControls.addAll(context.consumedControls(downKeys, downControls, axes));
    }
    Set<String> names = new HashSet<>(down);
    names.addAll(next.keySet());
    for (String action : names) {
      boolean wasDown = down.contains(action), nowDown = next.getOrDefault(action, 0f) != 0f;
      if (!wasDown && nowDown) pressed.add(action);
      if (wasDown && !nowDown) released.add(action);
    }
    down.clear();
    down.addAll(next.keySet());
    values.clear();
    values.putAll(next);
  }

  private static String control(String source, String name) {
    return InputBinding.identifier(source, "source")
        + '\u0000'
        + InputBinding.identifier(name, "control");
  }
}
