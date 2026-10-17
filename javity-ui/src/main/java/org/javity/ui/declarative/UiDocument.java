package org.javity.ui.declarative;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.EventListener;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.FocusListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.javity.ui.controls.ProgressBar;
import org.javity.ui.controls.Slider;
import org.javity.ui.controls.Toggle;
import org.javity.ui.flex.Flex;
import org.javity.ui.flex.ImageFit;
import org.javity.ui.flex.UiImage;
import org.javity.ui.flex.UiTheme;
import org.javity.ui.flex.UtilityStyles;
import org.javity.ui.yoga.YogaBackend;
import org.javity.ui.yoga.YogaBackends;
import org.javity.ui.yoga.YogaNode;

/** Owns one keyed Scene2D/Yoga tree. The theme and Stage remain caller-owned. */
public final class UiDocument implements AutoCloseable {
  private static final Set<String> TYPES =
      Set.of(
          "View",
          "Text",
          "Button",
          "Progress",
          "TextField",
          "ScrollView",
          "Toggle",
          "Slider",
          "Image");
  private static final Set<String> PROPS =
      Set.of(
          "text",
          "source",
          "fit",
          "wrap",
          "value",
          "checked",
          "min",
          "max",
          "step",
          "duration",
          "delay",
          "disabled",
          "onClick",
          "onChange",
          "onKeyDown",
          "onKeyUp",
          "onFocus",
          "onBlur");

  private static final class Entry {
    final String key;
    final String type;
    final Actor actor;
    final Flex scrollContent;
    EventListener clickListener;
    EventListener changeListener;
    EventListener hoverListener;
    EventListener focusListener;
    boolean suppressChange;
    boolean hovered;
    Runnable clickHandler;
    Consumer<String> changeHandler;
    Consumer<Boolean> toggleHandler;
    Consumer<Float> sliderHandler;
    Function<UiKeyEvent, Boolean> keyDownHandler;
    Function<UiKeyEvent, Boolean> keyUpHandler;
    Runnable focusHandler;
    Runnable blurHandler;
    String classes = "";
    String resolvedClasses = "";
    Boolean disabled;

    Entry(String key, String type, Actor actor, Flex scrollContent) {
      this.key = key;
      this.type = type;
      this.actor = actor;
      this.scrollContent = scrollContent;
    }

    Flex container() {
      if (actor instanceof Flex flex) return flex;
      return scrollContent;
    }
  }

  private final UiTheme theme;
  private final YogaBackend yogaBackend;
  private final Flex root;
  private Map<String, Entry> entries = new HashMap<>();
  private UiNode current;
  private boolean closed;
  private Stage mountedStage;
  private EventListener navigationListener;
  private final Set<Integer> pressedKeys = new java.util.HashSet<>();

  public UiDocument(UiTheme theme) {
    this(YogaBackends.createDefault(), theme);
  }

  public UiDocument(YogaBackend yogaBackend, UiTheme theme) {
    this.yogaBackend = java.util.Objects.requireNonNull(yogaBackend);
    this.theme = theme;
    this.root = new Flex(yogaBackend, theme, "flex flex-col");
  }

  public void render(UiNode node) {
    requireOpen();
    if (node == null) throw new IllegalArgumentException("UI document root is required");
    Map<String, UiNode> nodes = new HashMap<>();
    validateTree(node, 0, nodes);
    Map<String, Map<String, Object>> values = resolveValues(node);
    validateTheme(node);
    resolveImageRegions(node, values);
    Stage renderStage = mountedStage;
    Actor focusedBefore = renderStage == null ? null : renderStage.getKeyboardFocus();
    Map<String, Entry> next = new HashMap<>();
    List<Entry> created = new ArrayList<>();
    try {
      prepare(node, values, next, created);
      applyAll(node, next, values, "");
      reconcile(root, List.of(node), next);
      reconcileDescendants(node, next);
    } catch (RuntimeException | Error e) {
      for (Entry entry : created) disposeEntry(entry);
      throw e;
    }
    for (Entry old : entries.values()) if (next.get(old.key) != old) disposeListeners(old);
    for (Entry old : entries.values()) if (next.get(old.key) != old) disposeEntry(old);
    entries = next;
    current = node;
    if (renderStage != null
        && renderStage == mountedStage
        && focusedBefore != null
        && entryFor(focusedBefore) == null
        && renderStage.getKeyboardFocus() == focusedBefore) renderStage.setKeyboardFocus(null);
  }

  public Flex root() {
    requireOpen();
    return root;
  }

  /** Mounts this document's owned root on a borrowed Stage and installs keyboard navigation. */
  public UiDocument mount(Stage stage) {
    requireOpen();
    if (stage == null) throw new IllegalArgumentException("Stage is required");
    if (mountedStage == stage) return this;
    unmount();
    requireOpen();
    if (mountedStage == stage) return this;
    if (mountedStage != null) unmount();
    requireOpen();
    mountedStage = stage;
    stage.addActor(root);
    navigationListener = new NavigationListener(stage);
    stage.getRoot().addCaptureListener(navigationListener);
    return this;
  }

  /** Removes this document's root and navigation listener; the Stage remains caller-owned. */
  public void unmount() {
    if (mountedStage == null) return;
    Stage previousStage = mountedStage;
    EventListener previousListener = navigationListener;
    mountedStage = null;
    navigationListener = null;
    pressedKeys.clear();
    if (previousListener != null) previousStage.getRoot().removeCaptureListener(previousListener);
    if (root.getStage() == previousStage) {
      root.getParent().removeActor(root, false);
      previousStage.unfocus(root);
    }
  }

  private final class NavigationListener extends InputListener {
    private final Stage stage;

    private NavigationListener(Stage stage) {
      this.stage = stage;
    }

    @Override
    public boolean keyDown(InputEvent event, int keycode) {
      if (closed || mountedStage != stage || root.getStage() != stage) return false;
      boolean firstKeyDown = pressedKeys.add(keycode);
      Actor actualFocus = stage.getKeyboardFocus();
      if (actualFocus != null && !isWithin(actualFocus, root)) return false;
      Entry entry = entryFor(actualFocus);
      UiKeyEvent key = keyEvent(keycode);
      if (entry != null && entry.keyDownHandler != null) {
        boolean consumed = Boolean.TRUE.equals(entry.keyDownHandler.apply(key));
        if (closed || mountedStage != stage || !isLiveFocusable(entry, stage)) {
          event.stop();
          return true;
        }
        if (consumed) {
          event.stop();
          return true;
        }
      }
      if (keycode == com.badlogic.gdx.Input.Keys.TAB) {
        event.stop();
        focusNext(stage, key.shift());
        return true;
      }
      if (entry == null || Boolean.TRUE.equals(entry.disabled)) return false;
      Actor actor = entry.actor;
      if (actor instanceof TextField) return false;
      if (actor instanceof Button button
          && (keycode == com.badlogic.gdx.Input.Keys.ENTER
              || keycode == com.badlogic.gdx.Input.Keys.SPACE)) {
        event.stop();
        if (firstKeyDown
            && isLiveFocusable(entry, stage)
            && !button.isDisabled()
            && entry.clickHandler != null) entry.clickHandler.run();
        return true;
      }
      if (actor instanceof Toggle toggle
          && (keycode == com.badlogic.gdx.Input.Keys.ENTER
              || keycode == com.badlogic.gdx.Input.Keys.SPACE)) {
        event.stop();
        if (firstKeyDown && isLiveFocusable(entry, stage)) toggle.activate();
        return true;
      }
      if (actor instanceof Slider slider) {
        if (keycode == com.badlogic.gdx.Input.Keys.LEFT
            || keycode == com.badlogic.gdx.Input.Keys.DOWN) {
          event.stop();
          if (firstKeyDown && isLiveFocusable(entry, stage)) slider.stepBy(-1);
          return true;
        }
        if (keycode == com.badlogic.gdx.Input.Keys.RIGHT
            || keycode == com.badlogic.gdx.Input.Keys.UP) {
          event.stop();
          if (firstKeyDown && isLiveFocusable(entry, stage)) slider.stepBy(1);
          return true;
        }
      }
      return false;
    }

    @Override
    public boolean keyUp(InputEvent event, int keycode) {
      if (closed || mountedStage != stage || root.getStage() != stage) return false;
      Entry entry = entryFor(stage.getKeyboardFocus());
      pressedKeys.remove(keycode);
      UiKeyEvent key = keyEvent(keycode);
      if (entry == null || entry.keyUpHandler == null) return false;
      boolean consumed = Boolean.TRUE.equals(entry.keyUpHandler.apply(key));
      if (consumed) event.stop();
      return consumed;
    }
  }

  private UiKeyEvent keyEvent(int keycode) {
    com.badlogic.gdx.Input input = com.badlogic.gdx.Gdx.input;
    return new UiKeyEvent(
        keycode,
        keyName(keycode),
        modifierPressed(
            keycode,
            com.badlogic.gdx.Input.Keys.SHIFT_LEFT,
            com.badlogic.gdx.Input.Keys.SHIFT_RIGHT,
            input),
        modifierPressed(
            keycode,
            com.badlogic.gdx.Input.Keys.CONTROL_LEFT,
            com.badlogic.gdx.Input.Keys.CONTROL_RIGHT,
            input),
        modifierPressed(
            keycode,
            com.badlogic.gdx.Input.Keys.ALT_LEFT,
            com.badlogic.gdx.Input.Keys.ALT_RIGHT,
            input));
  }

  private boolean modifierPressed(
      int eventKey, int leftKey, int rightKey, com.badlogic.gdx.Input input) {
    return pressedKeys.contains(leftKey)
        || pressedKeys.contains(rightKey)
        || (input != null
            && (eventKey != leftKey && input.isKeyPressed(leftKey)
                || eventKey != rightKey && input.isKeyPressed(rightKey)));
  }

  private static String keyName(int keycode) {
    return switch (keycode) {
      case com.badlogic.gdx.Input.Keys.TAB -> "Tab";
      case com.badlogic.gdx.Input.Keys.ENTER -> "Enter";
      case com.badlogic.gdx.Input.Keys.SPACE -> "Space";
      case com.badlogic.gdx.Input.Keys.ESCAPE -> "Escape";
      case com.badlogic.gdx.Input.Keys.LEFT -> "ArrowLeft";
      case com.badlogic.gdx.Input.Keys.RIGHT -> "ArrowRight";
      case com.badlogic.gdx.Input.Keys.UP -> "ArrowUp";
      case com.badlogic.gdx.Input.Keys.DOWN -> "ArrowDown";
      case com.badlogic.gdx.Input.Keys.SHIFT_LEFT, com.badlogic.gdx.Input.Keys.SHIFT_RIGHT ->
          "Shift";
      case com.badlogic.gdx.Input.Keys.CONTROL_LEFT, com.badlogic.gdx.Input.Keys.CONTROL_RIGHT ->
          "Control";
      case com.badlogic.gdx.Input.Keys.ALT_LEFT, com.badlogic.gdx.Input.Keys.ALT_RIGHT -> "Alt";
      default -> com.badlogic.gdx.Input.Keys.toString(keycode);
    };
  }

  private static boolean isWithin(Actor actor, Actor ancestor) {
    for (Actor cursor = actor; cursor != null; cursor = cursor.getParent())
      if (cursor == ancestor) return true;
    return false;
  }

  private Entry entryFor(Actor actor) {
    if (actor == null) return null;
    for (Entry entry : entries.values()) if (entry.actor == actor) return entry;
    return null;
  }

  private boolean isLiveFocusable(Entry entry, Stage stage) {
    return !closed
        && mountedStage == stage
        && entries.get(entry.key) == entry
        && entry.actor.getStage() == stage
        && !Boolean.TRUE.equals(entry.disabled);
  }

  private void focusNext(Stage stage, boolean backwards) {
    List<Entry> focusable = new ArrayList<>();
    if (current != null) collectFocusable(current, focusable);
    focusable.removeIf(entry -> !isLiveFocusable(entry, stage));
    if (focusable.isEmpty()) return;
    int index = -1;
    for (int i = 0; i < focusable.size(); i++)
      if (focusable.get(i).actor == stage.getKeyboardFocus()) index = i;
    Entry target =
        focusable.get(
            backwards
                ? (index <= 0 ? focusable.size() - 1 : index - 1)
                : (index + 1) % focusable.size());
    if (isLiveFocusable(target, stage)) {
      stage.setKeyboardFocus(target.actor);
      if (!isLiveFocusable(target, stage) || stage.getKeyboardFocus() != target.actor) return;
    }
  }

  private void collectFocusable(UiNode node, List<Entry> out) {
    Entry entry = entries.get(node.key());
    if (entry != null
        && (entry.actor instanceof Button
            || entry.actor instanceof Toggle
            || entry.actor instanceof Slider
            || entry.actor instanceof TextField)) out.add(entry);
    for (UiNode child : node.children()) collectFocusable(child, out);
  }

  public Actor actor(String key) {
    requireOpen();
    Entry entry = entries.get(key);
    return entry == null ? null : entry.actor;
  }

  /** Re-evaluates dynamic text/value properties without invoking layout or Yoga. */
  public void refresh() {
    requireOpen();
    if (current == null) return;
    Map<String, Map<String, Object>> values = resolveValues(current);
    resolveImageRegions(current, values);
    applyAll(current, entries, values, "");
  }

  public boolean isClosed() {
    return closed;
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    // The document owns its mountpoint, but the host Group/Stage remains caller-owned.
    unmount();
    root.remove();
    for (Entry entry : entries.values()) disposeListeners(entry);
    for (Entry entry : entries.values()) disposeEntry(entry);
    entries.clear();
    current = null;
    root.dispose();
  }

  private void validateTree(UiNode node, int depth, Map<String, UiNode> seen) {
    if (depth > 64) throw new IllegalArgumentException("UI tree exceeds maximum nesting depth");
    if (!TYPES.contains(node.type()))
      throw new IllegalArgumentException("Unsupported UI node type: " + node.type());
    if (seen.putIfAbsent(node.key(), node) != null)
      throw new IllegalArgumentException("Duplicate UI key: " + node.key());
    for (String name : node.props().keySet()) {
      if (!PROPS.contains(name))
        throw new IllegalArgumentException("Unsupported UI property: " + name);
    }
    YogaNode probe = yogaBackend.createNode();
    try {
      for (String token : node.classes().split("\\s+")) {
        int colon = token.indexOf(':');
        if (colon >= 0) {
          String state = token.substring(0, colon);
          boolean supported =
              switch (node.type()) {
                case "Button" -> Set.of("hover", "active", "focus", "disabled").contains(state);
                case "TextField" -> Set.of("hover", "focus", "disabled").contains(state);
                case "Toggle", "Slider" ->
                    Set.of("hover", "focus", "disabled", "active").contains(state);
                default -> false;
              };
          if (!supported)
            throw new IllegalArgumentException(
                "State variant " + state + " is not supported on " + node.type());
        }
        if (token.equals("appearance-none") && !node.type().equals("Progress"))
          throw new IllegalArgumentException("appearance-none is only supported on Progress");
        else if (token.equals("overflow-y-auto") && !node.type().equals("ScrollView"))
          throw new IllegalArgumentException("overflow-y-auto is only supported on ScrollView");
      }
      UtilityStyles.apply(probe, node.classes());
    } finally {
      probe.freeNode();
    }
    Set<String> allowed =
        switch (node.type()) {
          case "View", "ScrollView" -> Set.of();
          case "Text" -> Set.of("text", "wrap");
          case "Image" -> Set.of("source", "fit");
          case "Button" ->
              Set.of("text", "disabled", "onClick", "onKeyDown", "onKeyUp", "onFocus", "onBlur");
          case "Progress" -> Set.of("value", "min", "max", "duration", "delay", "disabled");
          case "TextField" ->
              Set.of("value", "disabled", "onChange", "onKeyDown", "onKeyUp", "onFocus", "onBlur");
          case "Toggle" ->
              Set.of(
                  "checked", "disabled", "onChange", "onKeyDown", "onKeyUp", "onFocus", "onBlur");
          case "Slider" ->
              Set.of(
                  "value",
                  "min",
                  "max",
                  "step",
                  "disabled",
                  "onChange",
                  "onKeyDown",
                  "onKeyUp",
                  "onFocus",
                  "onBlur");
          default -> Set.of();
        };
    if (node.type().equals("Toggle") && !node.props().containsKey("checked"))
      throw new IllegalArgumentException("Toggle requires controlled checked boolean");
    if (node.type().equals("Slider"))
      for (String name : Set.of("min", "max", "value", "step"))
        if (!node.props().containsKey(name))
          throw new IllegalArgumentException("Slider requires " + name);
    for (String name : node.props().keySet()) {
      if (!allowed.contains(name))
        throw new IllegalArgumentException("Property " + name + " is not valid on " + node.type());
      Object value = node.props().get(name);
      if (value instanceof Supplier<?> && !name.equals("text") && !name.equals("value"))
        throw new IllegalArgumentException("Only text/value properties can be dynamic");
      if (name.equals("text") && !(value instanceof String) && !(value instanceof Supplier<?>))
        throw new IllegalArgumentException("text must be a String or Supplier");
      if (node.type().equals("Image")
          && name.equals("source")
          && (!(value instanceof String source) || source.isBlank()))
        throw new IllegalArgumentException(
            "Image source must be a nonempty registered name string");
      if (node.type().equals("Image") && name.equals("fit")) {
        if (!(value instanceof String fit))
          throw new IllegalArgumentException("Image fit must be a string");
        ImageFit.fromValue(fit);
      }
      if (name.equals("value")) {
        boolean valid =
            node.type().equals("Progress") || node.type().equals("Slider")
                ? value instanceof Number || value instanceof Supplier<?>
                : value instanceof String || value instanceof Supplier<?>;
        if (!valid)
          throw new IllegalArgumentException(
              node.type().equals("Progress") || node.type().equals("Slider")
                  ? node.type() + " value must be a number or Supplier"
                  : "TextField value must be a String or Supplier");
      }
      if (name.equals("disabled") && !(value instanceof Boolean))
        throw new IllegalArgumentException("disabled must be boolean");
      if (name.equals("wrap") && !(value instanceof Boolean))
        throw new IllegalArgumentException("wrap must be boolean");
      if (name.equals("checked") && !(value instanceof Boolean))
        throw new IllegalArgumentException("checked must be boolean");
      if (name.equals("onClick") && !(value instanceof Runnable))
        throw new IllegalArgumentException("onClick must be Runnable");
      if (name.equals("onChange") && !(value instanceof Consumer<?>))
        throw new IllegalArgumentException("onChange must be a typed Consumer");
      if ((name.equals("min") || name.equals("max") || name.equals("step"))
          && !(value instanceof Number))
        throw new IllegalArgumentException(name + " must be numeric");
      if ((name.equals("duration") || name.equals("delay")) && !(value instanceof Number))
        throw new IllegalArgumentException(name + " must be numeric");
      if ((name.equals("onKeyDown") || name.equals("onKeyUp"))
          && !(value instanceof Function<?, ?>))
        throw new IllegalArgumentException(name + " must be Function<UiKeyEvent, Boolean>");
      if ((name.equals("onFocus") || name.equals("onBlur")) && !(value instanceof Runnable))
        throw new IllegalArgumentException(name + " must be Runnable");
    }
    if (node.type().equals("Image") && !node.props().containsKey("source"))
      throw new IllegalArgumentException("Image requires source");
    if (node.type().equals("Image") && !node.children().isEmpty())
      throw new IllegalArgumentException("Image cannot have children");
    if (!node.children().isEmpty()
        && !node.type().equals("View")
        && !node.type().equals("ScrollView"))
      throw new IllegalArgumentException(node.type() + " cannot have children");
    if (node.type().equals("ScrollView")
        && (node.children().size() != 1 || !node.children().getFirst().type().equals("View")))
      throw new IllegalArgumentException("ScrollView requires exactly one View child");
    for (UiNode child : node.children()) validateTree(child, depth + 1, seen);
  }

  private void validateTheme(UiNode node) {
    if (theme == null
        && Set.of(
                "Button",
                "TextField",
                "Text",
                "ScrollView",
                "Progress",
                "Toggle",
                "Slider",
                "Image")
            .contains(node.type()))
      throw new IllegalStateException("UiTheme is required to render " + node.type());
    for (UiNode child : node.children()) validateTheme(child);
  }

  private Map<String, Map<String, Object>> resolveValues(UiNode node) {
    Map<String, Map<String, Object>> resolved = new HashMap<>();
    collectValues(node, resolved);
    return resolved;
  }

  private void collectValues(UiNode node, Map<String, Map<String, Object>> all) {
    Map<String, Object> values = new HashMap<>();
    for (var property : node.props().entrySet()) {
      Object value = property.getValue();
      if (value instanceof Supplier<?> supplier) value = supplier.get();
      if (property.getKey().equals("text")
          || (property.getKey().equals("value") && node.type().equals("TextField"))) {
        if (!(value instanceof String))
          throw new IllegalArgumentException(property.getKey() + " binding must produce String");
      }
      if (node.type().equals("Image")
          && property.getKey().equals("source")
          && !(value instanceof String source && !source.isBlank()))
        throw new IllegalArgumentException(
            "Image source must be a nonempty registered name string");
      if (property.getKey().equals("value")
          && (node.type().equals("Progress") || node.type().equals("Slider"))
          && !(value instanceof Number))
        throw new IllegalArgumentException("Progress value must be numeric");
      if ((property.getKey().equals("min")
              || property.getKey().equals("max")
              || property.getKey().equals("step"))
          && !(value instanceof Number))
        throw new IllegalArgumentException(property.getKey() + " must be numeric");
      if ((property.getKey().equals("duration") || property.getKey().equals("delay"))
          && !(value instanceof Number))
        throw new IllegalArgumentException(property.getKey() + " must be numeric");
      if (value instanceof Number number) {
        double doubleValue = number.doubleValue();
        float floatValue = number.floatValue();
        if (!Double.isFinite(doubleValue)
            || !Float.isFinite(floatValue)
            || (doubleValue != 0 && floatValue == 0))
          throw new IllegalArgumentException(property.getKey() + " must be finite as a float");
      }
      values.put(property.getKey(), value);
    }
    if (node.type().equals("Progress")) {
      float min = number(values.getOrDefault("min", 0f));
      float max = number(values.getOrDefault("max", 1f));
      if (max <= min) throw new IllegalArgumentException("Progress max must be greater than min");
      if (number(values.getOrDefault("duration", 0f)) < 0)
        throw new IllegalArgumentException("Progress duration must be nonnegative");
      if (number(values.getOrDefault("delay", 0f)) < 0)
        throw new IllegalArgumentException("Progress delay must be nonnegative");
    }
    if (node.type().equals("Slider")) {
      float min = number(values.getOrDefault("min", 0f));
      float max = number(values.getOrDefault("max", 1f));
      float step = number(values.getOrDefault("step", .1f));
      if (max <= min) throw new IllegalArgumentException("Slider max must be greater than min");
      if (step <= 0) throw new IllegalArgumentException("Slider step must be positive");
    }
    all.put(node.key(), values);
    for (UiNode child : node.children()) collectValues(child, all);
  }

  private void resolveImageRegions(UiNode node, Map<String, Map<String, Object>> values) {
    if (node.type().equals("Image")) {
      Map<String, Object> props = values.get(node.key());
      String source = (String) props.get("source");
      Entry existing = entries.get(node.key());
      Object region =
          existing != null
                  && existing.actor instanceof UiImage mounted
                  && source.equals(mounted.source())
              ? mounted.borrowedRegion()
              : theme.lookupImage(source);
      props.put("resolvedImageRegion", region);
      props.put("resolvedImageFit", ImageFit.fromValue((String) props.get("fit")));
    }
    for (UiNode child : node.children()) resolveImageRegions(child, values);
  }

  private void prepare(
      UiNode node,
      Map<String, Map<String, Object>> values,
      Map<String, Entry> next,
      List<Entry> created) {
    Entry old = entries.get(node.key());
    Entry entry;
    if (old != null && old.type.equals(node.type())) entry = old;
    else {
      entry = create(node, values.get(node.key()));
      created.add(entry);
    }
    next.put(node.key(), entry);
    for (UiNode child : node.children()) prepare(child, values, next, created);
  }

  private Entry create(UiNode node, Map<String, Object> values) {
    Entry entry =
        switch (node.type()) {
          case "View" ->
              new Entry(
                  node.key(), node.type(), new Flex(yogaBackend, theme, node.classes()), null);
          case "Text" ->
              new Entry(
                  node.key(),
                  node.type(),
                  theme.label("", node.classes(), Boolean.TRUE.equals(node.props().get("wrap"))),
                  null);
          case "Image" ->
              new Entry(
                  node.key(),
                  node.type(),
                  new UiImage(
                      theme,
                      (String) values.get("source"),
                      (com.badlogic.gdx.graphics.g2d.TextureRegion)
                          values.get("resolvedImageRegion"),
                      (ImageFit) values.get("resolvedImageFit"),
                      node.classes()),
                  null);
          case "Button" ->
              new Entry(node.key(), node.type(), theme.button("", node.classes()), null);
          case "Progress" ->
              new Entry(node.key(), node.type(), new ProgressBar(theme, node.classes()), null);
          case "TextField" ->
              new Entry(node.key(), node.type(), theme.textField("", node.classes()), null);
          case "Toggle" ->
              new Entry(node.key(), node.type(), new Toggle(theme, node.classes()), null);
          case "Slider" ->
              new Entry(node.key(), node.type(), new Slider(theme, node.classes()), null);
          case "ScrollView" -> {
            Flex content = new Flex(yogaBackend, theme, "flex flex-col");
            yield new Entry(
                node.key(), node.type(), theme.scrollPane(content, node.classes()), content);
          }
          default -> throw new IllegalArgumentException("Unsupported UI node type: " + node.type());
        };
    entry.classes = node.classes();
    if (entry.actor instanceof TextButton) {
      entry.clickListener =
          new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
              if (entry.actor instanceof Button button
                  && !button.isDisabled()
                  && isLiveEntry(entry)
                  && entry.clickHandler != null) entry.clickHandler.run();
            }
          };
      entry.actor.addListener(entry.clickListener);
    } else if (entry.actor instanceof TextField) {
      entry.hoverListener =
          new InputListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
              entry.hovered = true;
              if (entry.actor instanceof TextField field)
                theme.updateTextField(field, entry.resolvedClasses, "hover");
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
              entry.hovered = false;
              if (entry.actor instanceof TextField field)
                theme.updateTextField(field, entry.resolvedClasses);
            }
          };
      entry.actor.addListener(entry.hoverListener);
      entry.changeListener =
          new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
              if (isLiveEntry(entry)
                  && !entry.suppressChange
                  && actor instanceof TextField field
                  && !field.isDisabled()
                  && entry.changeHandler != null) entry.changeHandler.accept(field.getText());
            }
          };
      entry.actor.addListener(entry.changeListener);
    }
    if (entry.actor instanceof Button
        || entry.actor instanceof Toggle
        || entry.actor instanceof Slider
        || entry.actor instanceof TextField) {
      entry.focusListener =
          new FocusListener() {
            @Override
            public void keyboardFocusChanged(
                FocusListener.FocusEvent event, Actor actor, boolean focused) {
              Runnable callback = focused ? entry.focusHandler : entry.blurHandler;
              if (callback != null && isLiveEntry(entry)) callback.run();
            }
          };
      entry.actor.addListener(entry.focusListener);
    }
    return entry;
  }

  private void applyAll(
      UiNode node,
      Map<String, Entry> target,
      Map<String, Map<String, Object>> values,
      String inheritedTextStyle) {
    Entry entry = target.get(node.key());
    Map<String, Object> props = values.get(node.key());
    String styleClasses = inheritTextStyles(inheritedTextStyle, node.classes());
    entry.resolvedClasses = styleClasses;
    boolean classesChanged = !entry.classes.equals(node.classes());
    if (classesChanged && entry.actor instanceof Flex flex) flex.setStyleClasses(node.classes());
    if (entry.actor instanceof Label label && node.type().equals("Text"))
      theme.updateLabel(label, styleClasses, Boolean.TRUE.equals(props.get("wrap")));
    if (entry.actor instanceof UiImage image)
      image.updateImage(
          (String) props.get("source"),
          (com.badlogic.gdx.graphics.g2d.TextureRegion) props.get("resolvedImageRegion"),
          (ImageFit) props.get("resolvedImageFit"),
          node.classes());
    if (entry.actor instanceof TextButton button) {
      theme.updateButton(button, styleClasses);
      boolean disabled = Boolean.TRUE.equals(props.get("disabled"));
      if (entry.disabled == null || entry.disabled != disabled) button.setDisabled(disabled);
      entry.disabled = disabled;
      String text = String.valueOf(props.getOrDefault("text", ""));
      if (!button.getText().toString().equals(text)) button.setText(text);
      entry.clickHandler = (Runnable) props.get("onClick");
    }
    if (entry.actor instanceof Label label && node.type().equals("Text"))
      setLabelText(label, String.valueOf(props.getOrDefault("text", "")));
    if (entry.actor instanceof TextField field) {
      theme.updateTextField(field, styleClasses, entry.hovered ? "hover" : "");
      boolean disabled = Boolean.TRUE.equals(props.get("disabled"));
      if (entry.disabled == null || entry.disabled != disabled) field.setDisabled(disabled);
      entry.disabled = disabled;
      String text = String.valueOf(props.getOrDefault("value", ""));
      if (!field.getText().equals(text)) {
        entry.suppressChange = true;
        try {
          field.setText(text);
        } finally {
          entry.suppressChange = false;
        }
      }
      @SuppressWarnings("unchecked")
      Consumer<String> change = (Consumer<String>) props.get("onChange");
      entry.changeHandler = change;
    }
    if (entry.actor instanceof Toggle toggle) {
      toggle.setStyleClasses(styleClasses);
      toggle.setChecked(Boolean.TRUE.equals(props.get("checked")));
      boolean disabled = Boolean.TRUE.equals(props.get("disabled"));
      toggle.setDisabled(disabled);
      @SuppressWarnings("unchecked")
      Consumer<Boolean> change = (Consumer<Boolean>) props.get("onChange");
      entry.toggleHandler = change;
      toggle.setOnChange(
          checked -> {
            Consumer<Boolean> handler = entry.toggleHandler;
            if (isLiveEntry(entry) && handler != null) handler.accept(checked);
          });
      entry.disabled = disabled;
    }
    if (entry.actor instanceof Slider slider) {
      slider.setStyleClasses(styleClasses);
      float min = number(props.getOrDefault("min", 0f));
      float max = number(props.getOrDefault("max", 1f));
      float step = number(props.getOrDefault("step", .1f));
      slider.setRange(min, max);
      slider.setStep(step);
      slider.setValue(number(props.getOrDefault("value", min)));
      boolean disabled = Boolean.TRUE.equals(props.get("disabled"));
      slider.setDisabled(disabled);
      @SuppressWarnings("unchecked")
      Consumer<Float> change = (Consumer<Float>) props.get("onChange");
      entry.sliderHandler = change;
      slider.setOnChange(
          value -> {
            Consumer<Float> handler = entry.sliderHandler;
            if (isLiveEntry(entry) && handler != null) handler.accept(value);
          });
      entry.disabled = disabled;
    }
    @SuppressWarnings("unchecked")
    Function<UiKeyEvent, Boolean> keyDown = (Function<UiKeyEvent, Boolean>) props.get("onKeyDown");
    @SuppressWarnings("unchecked")
    Function<UiKeyEvent, Boolean> keyUp = (Function<UiKeyEvent, Boolean>) props.get("onKeyUp");
    entry.keyDownHandler = keyDown;
    entry.keyUpHandler = keyUp;
    entry.focusHandler = (Runnable) props.get("onFocus");
    entry.blurHandler = (Runnable) props.get("onBlur");
    if (entry.actor instanceof ProgressBar progress) {
      float min = number(props.getOrDefault("min", 0f));
      float max = number(props.getOrDefault("max", 1f));
      progress.setRange(min, max);
      progress.setDuration(number(props.getOrDefault("duration", 0f)));
      progress.setDelay(number(props.getOrDefault("delay", 0f)));
      progress.setValue(number(props.getOrDefault("value", min)));
      progress.setStyleClasses(styleClasses);
      boolean disabled = Boolean.TRUE.equals(props.get("disabled"));
      if (entry.disabled == null || entry.disabled != disabled)
        progress.setTouchable(disabled ? Touchable.disabled : Touchable.enabled);
      entry.disabled = disabled;
    }
    if (entry.actor instanceof ScrollPane pane) {
      if (classesChanged) theme.updateScrollPane(pane, node.classes());
      pane.setTouchable(Touchable.childrenOnly);
    }
    entry.classes = node.classes();
    String childInherited = inheritTextStyles(inheritedTextStyle, node.classes());
    for (UiNode child : node.children()) applyAll(child, target, values, childInherited);
  }

  /** CSS color and font size inherit; box, background, and border properties remain local. */
  private static String inheritTextStyles(String parent, String own) {
    String size = null, color = null;
    for (String token : parent.split("\\s+")) {
      if (token.matches("text-(sm|base|lg|xl|2xl)")) size = token;
      else if (token.startsWith("text-")) color = token;
    }
    boolean hasSize = false, hasColor = false;
    for (String token : own.split("\\s+")) {
      if (token.matches("text-(sm|base|lg|xl|2xl)")) hasSize = true;
      else if (token.startsWith("text-")) hasColor = true;
    }
    StringBuilder result = new StringBuilder();
    if (!hasSize && size != null) result.append(size).append(' ');
    if (!hasColor && color != null) result.append(color).append(' ');
    return result.append(own).toString().trim();
  }

  private static void setLabelText(Label label, String text) {
    if (!label.getText().toString().equals(text)) label.setText(text);
  }

  private void reconcileDescendants(UiNode node, Map<String, Entry> target) {
    Entry entry = target.get(node.key());
    Flex container = entry.container();
    if (container != null) {
      reconcile(container, node.children(), target);
      for (UiNode child : node.children()) reconcileDescendants(child, target);
    }
  }

  private void reconcile(Flex parent, List<UiNode> children, Map<String, Entry> target) {
    Set<Actor> wanted = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    for (UiNode child : children) wanted.add(target.get(child.key()).actor);
    for (Actor actor : parent.getChildren().toArray(Actor[]::new))
      if (!wanted.contains(actor)) parent.removeActor(actor, false);
    for (int i = 0; i < children.size(); i++) {
      UiNode child = children.get(i);
      Entry entry = target.get(child.key());
      Actor actor = entry.actor;
      if (actor.getParent() != parent)
        parent.addActorAt(Math.min(i, parent.getChildren().size), actor);
      else actor.setZIndex(Math.min(i, parent.getChildren().size - 1));
      parent.add(actor, child.classes());
    }
  }

  private void disposeListeners(Entry entry) {
    if (entry.clickListener != null) entry.actor.removeListener(entry.clickListener);
    if (entry.changeListener != null) entry.actor.removeListener(entry.changeListener);
    if (entry.hoverListener != null) entry.actor.removeListener(entry.hoverListener);
    if (entry.focusListener != null) entry.actor.removeListener(entry.focusListener);
    entry.clickListener = null;
    entry.changeListener = null;
    entry.hoverListener = null;
    entry.focusListener = null;
    entry.clickHandler = null;
    entry.changeHandler = null;
    entry.toggleHandler = null;
    entry.sliderHandler = null;
    entry.keyDownHandler = null;
    entry.keyUpHandler = null;
    entry.focusHandler = null;
    entry.blurHandler = null;
  }

  private boolean isLiveEntry(Entry entry) {
    return !closed && entries.get(entry.key) == entry;
  }

  private void disposeEntry(Entry entry) {
    if (entry.actor instanceof Flex flex) flex.dispose();
    if (entry.scrollContent != null) entry.scrollContent.dispose();
    if (entry.actor instanceof ProgressBar progress) progress.close();
    entry.actor.remove();
  }

  private static float number(Object value) {
    if (!(value instanceof Number number))
      throw new IllegalArgumentException("Expected numeric value");
    float result = number.floatValue();
    if (!Float.isFinite(result))
      throw new IllegalArgumentException("Expected finite numeric value");
    return result;
  }

  private void requireOpen() {
    if (closed) throw new IllegalStateException("UiDocument is closed");
  }
}
