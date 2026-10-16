package org.javity.script;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.javity.ui.declarative.UiKeyEvent;
import org.javity.ui.declarative.UiNode;

/** Shared JSON schema and callback-ID policy for platform script engines. */
public final class ScriptUiTreeDecoder {
  private enum NonPrimitive { VALUE }

  private ScriptUiTreeDecoder() {}

  public interface Events {
    void invoke(String id, Object value);
    boolean invokeKey(String id, UiKeyEvent event);
  }

  public record Result(UiNode root, Set<String> handlerIds) {}

  /** Parses action JSON and reduces arrays/objects to a marker so they never cross the engine SPI. */
  public static Object[] readPrimitiveArray(String json) {
    JsonValue values = new JsonReader().parse(json);
    if (!values.isArray()) throw new IllegalArgumentException("Action arguments must be a JSON array");
    List<Object> result = new ArrayList<>();
    for (JsonValue value = values.child; value != null; value = value.next) {
      if (value.isNull()) result.add(null);
      else if (value.isString()) result.add(value.asString());
      else if (value.isBoolean()) result.add(value.asBoolean());
      else if (value.isNumber()) result.add(finiteNumber(value, "action argument"));
      else result.add(NonPrimitive.VALUE);
    }
    return result.toArray();
  }

  /** Parses guest JSON without re-entering JavaScript and validates the complete tree first. */
  public static Result decode(String treeJson, String handlerIdsJson, Events events) {
    JsonValue idsValue = new JsonReader().parse(handlerIdsJson);
    if (!idsValue.isArray()) throw new IllegalArgumentException("React handler ids must be a JSON array");
    Set<String> ids = new HashSet<>();
    for (JsonValue item = idsValue.child; item != null; item = item.next) {
      String id = requireString(item, "handler id");
      if (!id.matches("h[0-9]+")) throw new IllegalArgumentException("Invalid React handler id: " + id);
      ids.add(id);
    }
    JsonValue root = new JsonReader().parse(treeJson);
    return new Result(readNode(root, ids, events), Set.copyOf(ids));
  }

  private static UiNode readNode(JsonValue value, Set<String> ids, Events events) {
    if (value == null || !value.isObject()) throw new IllegalArgumentException("React host emitted an invalid node");
    String type = memberString(value, "type", "");
    String key = memberString(value, "key", "");
    String classes = memberString(value, "classes", "");
    Map<String, Object> props = new HashMap<>();
    JsonValue source = value.get("props");
    if (source != null) {
      if (!source.isObject()) throw new IllegalArgumentException("React props must be an object");
      for (JsonValue entry = source.child; entry != null; entry = entry.next) {
        String name = entry.name;
        switch (name) {
          case "text" -> props.put(name, entry.isNull() ? "" : requireString(entry, name));
          case "source", "fit" -> props.put(name, requireString(entry, name));
          case "value" -> props.put(name, type.equals("Progress") || type.equals("Slider")
              ? finiteNumber(entry, name) : entry.isNull() ? "" : requireString(entry, name));
          case "disabled", "wrap", "checked" -> props.put(name, requireBoolean(entry, name));
          case "min", "max", "step", "duration", "delay" -> {
            double number = finiteNumber(entry, name);
            if ((name.equals("duration") || name.equals("delay")) && number < 0)
              throw new IllegalArgumentException(name + " must be finite and nonnegative");
            props.put(name, number);
          }
          case "onClick", "onChange", "onKeyDown", "onKeyUp", "onFocus", "onBlur" -> {
            String id = requireString(entry, name);
            if (!ids.contains(id)) throw new IllegalArgumentException("Unknown React handler id: " + id);
            switch (name) {
              case "onClick", "onFocus", "onBlur" -> props.put(name, (Runnable) () -> events.invoke(id, null));
              case "onKeyDown", "onKeyUp" -> props.put(name,
                  (java.util.function.Function<UiKeyEvent, Boolean>) event -> events.invokeKey(id, event));
              case "onChange" -> {
                if (type.equals("TextField")) props.put(name, (Consumer<String>) text -> events.invoke(id, text));
                else if (type.equals("Toggle")) props.put(name, (Consumer<Boolean>) checked -> events.invoke(id, checked));
                else if (type.equals("Slider")) props.put(name, (Consumer<Float>) number -> events.invoke(id, number.doubleValue()));
                else throw new IllegalArgumentException("onChange is not supported on " + type);
              }
            }
          }
          default -> throw new IllegalArgumentException("Unsupported React property: " + name);
        }
      }
    }
    List<UiNode> children = new ArrayList<>();
    JsonValue childList = value.get("children");
    if (childList != null) {
      if (!childList.isArray()) throw new IllegalArgumentException("React children must be an array");
      for (JsonValue child = childList.child; child != null; child = child.next)
        children.add(readNode(child, ids, events));
    }
    return UiNode.element(type, key, classes, props, children);
  }

  private static String memberString(JsonValue value, String key, String fallback) {
    JsonValue member = value.get(key);
    return member == null || member.isNull() ? fallback : requireString(member, key);
  }

  private static String requireString(JsonValue value, String name) {
    if (value == null || !value.isString()) throw new IllegalArgumentException(name + " must be a string");
    return value.asString();
  }

  private static boolean requireBoolean(JsonValue value, String name) {
    if (value == null || !value.isBoolean()) throw new IllegalArgumentException(name + " must be boolean");
    return value.asBoolean();
  }

  private static double finiteNumber(JsonValue value, String name) {
    if (value == null || !value.isNumber()) throw new IllegalArgumentException(name + " must be a JSON number");
    double number = value.asDouble();
    if (!Double.isFinite(number)) throw new IllegalArgumentException(name + " must be finite");
    return number;
  }
}
