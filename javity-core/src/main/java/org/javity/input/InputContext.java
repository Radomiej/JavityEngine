package org.javity.input;

import java.util.*;

/** Mutable action map registered with an {@link InputActions} controller. */
public final class InputContext {
  private final String name;
  private final int priority;
  private final LinkedHashMap<String, List<InputBinding>> bindings = new LinkedHashMap<>();
  private final Set<String> consuming = new HashSet<>();
  private Runnable changed = () -> {};
  private boolean active = true;

  InputContext(String name, int priority) {
    this.name = Objects.requireNonNull(name, "name");
    if (name.isBlank()) throw new IllegalArgumentException("context name cannot be blank");
    this.priority = priority;
  }

  void onChange(Runnable changed) {
    this.changed = changed;
  }

  public String name() {
    return name;
  }

  public int priority() {
    return priority;
  }

  public boolean active() {
    return active;
  }

  public void setActive(boolean active) {
    if (this.active != active) {
      this.active = active;
      changed.run();
    }
  }

  public void activate() {
    setActive(true);
  }

  public void deactivate() {
    setActive(false);
  }

  public InputContext bindKey(String action, int key) {
    return add(action, new InputBinding.Key(key));
  }

  /** Button names and source names are supplied by the host's controller adapter. */
  public InputContext bindButton(String action, String source, String button) {
    return add(action, new InputBinding.Button(source, button));
  }

  /** Bind one side of a normalized axis. Direction must be -1 or +1. */
  public InputContext bindAxis(
      String action, String source, String axis, int direction, float deadzone) {
    return add(action, new InputBinding.Axis(source, axis, direction, deadzone));
  }

  /** Prevent held bindings for this action from reaching lower priority contexts. */
  public InputContext consume(String action) {
    consuming.add(validAction(action));
    changed.run();
    return this;
  }

  public InputContext stopConsuming(String action) {
    consuming.remove(action);
    changed.run();
    return this;
  }

  private InputContext add(String action, InputBinding binding) {
    bindings.computeIfAbsent(validAction(action), ignored -> new ArrayList<>()).add(binding);
    changed.run();
    return this;
  }

  void replaceAction(String action, List<InputBinding> replacement) {
    bindings.put(action, new ArrayList<>(replacement));
    changed.run();
  }

  public InputContext unbind(String action) {
    action = validAction(action);
    bindings.remove(action);
    consuming.remove(action);
    changed.run();
    return this;
  }

  public InputContext clearBindings() {
    bindings.clear();
    consuming.clear();
    changed.run();
    return this;
  }

  private static String validAction(String action) {
    Objects.requireNonNull(action, "action");
    if (action.isBlank()) throw new IllegalArgumentException("action cannot be blank");
    return action;
  }

  void clearAction(String action) {
    bindings.remove(action);
    changed.run();
  }

  boolean hasKey(int key) {
    return bindings.values().stream()
        .flatMap(Collection::stream)
        .anyMatch(b -> b instanceof InputBinding.Key k && k.key() == key);
  }

  boolean hasButton(String source, String button) {
    return bindings.values().stream()
        .flatMap(Collection::stream)
        .anyMatch(
            b ->
                b instanceof InputBinding.Button x
                    && x.source().equals(source)
                    && x.button().equals(button));
  }

  Set<String> actions() {
    LinkedHashSet<String> names = new LinkedHashSet<>(bindings.keySet());
    names.addAll(consuming);
    return names;
  }

  float value(
      String action,
      Set<Integer> keys,
      Set<String> buttons,
      Map<String, Float> axes,
      Set<String> blocked) {
    float result = 0f;
    for (InputBinding binding : bindings.getOrDefault(action, List.of())) {
      if (blocked.contains(identity(binding))) continue;
      float v = 0f;
      if (binding instanceof InputBinding.Key key) v = keys.contains(key.key()) ? 1f : 0f;
      else if (binding instanceof InputBinding.Button button)
        v = buttons.contains(control(button.source(), button.button())) ? 1f : 0f;
      else if (binding instanceof InputBinding.Axis axis) {
        float raw = axes.getOrDefault(control(axis.source(), axis.axis()), 0f) * axis.direction();
        v = raw <= axis.deadzone() ? 0f : (raw - axis.deadzone()) / (1f - axis.deadzone());
      }
      if (Math.abs(v) > Math.abs(result)) result = v;
    }
    return result;
  }

  Set<String> consumedControls(Set<Integer> keys, Set<String> buttons, Map<String, Float> axes) {
    Set<String> result = new HashSet<>();
    for (String action : consuming)
      for (InputBinding binding : bindings.getOrDefault(action, List.of())) {
        boolean held =
            binding instanceof InputBinding.Key key && keys.contains(key.key())
                || binding instanceof InputBinding.Button button
                    && buttons.contains(control(button.source(), button.button()))
                || binding instanceof InputBinding.Axis axis
                    && Math.abs(axes.getOrDefault(control(axis.source(), axis.axis()), 0f))
                        > axis.deadzone();
        if (held) result.add(identity(binding));
      }
    return result;
  }

  private static String identity(InputBinding binding) {
    if (binding instanceof InputBinding.Key key) return "key:" + key.key();
    if (binding instanceof InputBinding.Button button)
      return control(button.source(), button.button());
    InputBinding.Axis axis = (InputBinding.Axis) binding;
    return control(axis.source(), axis.axis());
  }

  private static String control(String source, String name) {
    return InputBinding.identifier(source, "source")
        + '\u0000'
        + InputBinding.identifier(name, "control");
  }
}
