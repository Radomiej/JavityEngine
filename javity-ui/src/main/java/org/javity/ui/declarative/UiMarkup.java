package org.javity.ui.declarative;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.javity.ui.flex.ImageFit;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/** Parser for a deliberately small, secure XML-compatible UI markup subset. */
public final class UiMarkup {
  private static final int MAX_DEPTH = 64;
  private static final int MAX_NODES = 2048;
  private static final String BINDING_START = "${";

  private UiMarkup() {}

  public static UiNode parse(
      String markup, Map<String, Runnable> events, Map<String, Supplier<?>> bindings) {
    return parseWithTypedEvents(markup, events, bindings);
  }

  /** Parses markup whose event table may also contain typed change and key consumers. */
  public static UiNode parseWithTypedEvents(
      String markup, Map<String, ?> events, Map<String, Supplier<?>> bindings) {
    if (markup == null) throw new IllegalArgumentException("Markup is required");
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      Element root =
          factory
              .newDocumentBuilder()
              .parse(new InputSource(new StringReader(markup)))
              .getDocumentElement();
      return parseElement(
          root,
          "0",
          1,
          new int[] {0},
          events == null ? Map.of() : events,
          bindings == null ? Map.of() : bindings);
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid UI markup: " + e.getMessage(), e);
    }
  }

  private static UiNode parseElement(
      Element element,
      String path,
      int depth,
      int[] count,
      Map<String, ?> events,
      Map<String, Supplier<?>> bindings) {
    if (depth > MAX_DEPTH)
      throw new IllegalArgumentException("UI markup exceeds maximum nesting depth");
    if (++count[0] > MAX_NODES)
      throw new IllegalArgumentException("UI markup exceeds maximum node count");
    String type = canonicalType(element.getTagName());
    Map<String, Object> props = new HashMap<>();
    String key = null, classes = "";
    for (int i = 0; i < element.getAttributes().getLength(); i++) {
      Node attribute = element.getAttributes().item(i);
      String name = attribute.getNodeName();
      String value = attribute.getNodeValue();
      switch (name) {
        case "key", "id" -> key = value;
        case "class", "className" -> classes = value;
        case "text" -> props.put("text", bind(value, bindings));
        case "source" -> props.put("source", value);
        case "fit" -> props.put("fit", value);
        case "value" ->
            props.put(
                "value",
                type.equals("Progress") || type.equals("Slider")
                    ? progressValue(value, bindings)
                    : bind(value, bindings));
        case "min", "max", "step", "duration", "delay" ->
            props.put(name, finiteNumber(value, name));
        case "checked" -> props.put(name, booleanValue(value, name));
        case "wrap" -> props.put(name, booleanValue(value, name));
        case "disabled" -> props.put("disabled", booleanValue(value, name));
        case "onClick" -> {
          Object callback = events.get(value);
          if (!(callback instanceof Runnable))
            throw new IllegalArgumentException("Unknown UI click event: " + value);
          props.put("onClick", (Runnable) callback);
        }
        case "onChange" -> {
          Object callback = events.get(value);
          if (!(callback instanceof Consumer<?>))
            throw new IllegalArgumentException("Unknown typed UI change event: " + value);
          props.put(name, callback);
        }
        case "onKeyDown", "onKeyUp" -> {
          Object callback = events.get(value);
          if (!(callback instanceof Function<?, ?>))
            throw new IllegalArgumentException("Unknown UI key event: " + value);
          props.put(name, callback);
        }
        case "onFocus", "onBlur" -> {
          Object callback = events.get(value);
          if (!(callback instanceof Runnable))
            throw new IllegalArgumentException("Unknown UI focus event: " + value);
          props.put(name, callback);
        }
        default -> throw new IllegalArgumentException("Unsupported UI attribute: " + name);
      }
    }
    if (type.equals("Progress")) {
      float min = ((Number) props.getOrDefault("min", 0f)).floatValue();
      float max = ((Number) props.getOrDefault("max", 1f)).floatValue();
      if (max <= min) throw new IllegalArgumentException("Progress max must be greater than min");
      if (((Number) props.getOrDefault("duration", 0f)).floatValue() < 0)
        throw new IllegalArgumentException("Progress duration must be nonnegative");
      if (((Number) props.getOrDefault("delay", 0f)).floatValue() < 0)
        throw new IllegalArgumentException("Progress delay must be nonnegative");
    }
    if (type.equals("Slider")) {
      for (String required : List.of("min", "max", "value", "step"))
        if (!props.containsKey(required))
          throw new IllegalArgumentException("Slider requires " + required);
      float min = ((Number) props.getOrDefault("min", 0f)).floatValue();
      float max = ((Number) props.getOrDefault("max", 1f)).floatValue();
      float step = ((Number) props.getOrDefault("step", .1f)).floatValue();
      if (max <= min) throw new IllegalArgumentException("Slider max must be greater than min");
      if (step <= 0) throw new IllegalArgumentException("Slider step must be positive");
    }
    if (type.equals("Toggle") && !props.containsKey("checked"))
      throw new IllegalArgumentException("Toggle requires controlled checked boolean");
    if (type.equals("Image")) {
      String source = (String) props.get("source");
      if (source == null || source.isBlank())
        throw new IllegalArgumentException("Image requires a registered source name");
      if (props.containsKey("fit")) ImageFit.fromValue((String) props.get("fit"));
    }
    List<UiNode> children = new ArrayList<>();
    StringBuilder textContent = new StringBuilder();
    NodeList childNodes = element.getChildNodes();
    for (int i = 0; i < childNodes.getLength(); i++) {
      Node child = childNodes.item(i);
      if (child instanceof Element childElement) {
        children.add(
            parseElement(
                childElement, path + "/" + children.size(), depth + 1, count, events, bindings));
      } else if (child.getNodeType() == Node.TEXT_NODE
          || child.getNodeType() == Node.CDATA_SECTION_NODE) {
        textContent.append(child.getNodeValue());
      } else if (child.getNodeType() != Node.COMMENT_NODE) {
        throw new IllegalArgumentException("Unsupported markup node");
      }
    }
    String body = textContent.toString();
    if (!Boolean.TRUE.equals(props.get("wrap"))) body = body.trim();
    if (type.equals("Image") && (!children.isEmpty() || !body.isBlank()))
      throw new IllegalArgumentException("Image cannot have children or text content");
    if (!body.isEmpty()) {
      if (!type.equals("Text") && !type.equals("Button")) {
        throw new IllegalArgumentException("Text content is only supported by Text and Button");
      }
      if (props.containsKey("text"))
        throw new IllegalArgumentException("Use either text attribute or body text");
      props.put("text", bind(body, bindings));
    }
    return new UiNode(type, key == null || key.isEmpty() ? path : key, classes, props, children);
  }

  private static String canonicalType(String tag) {
    return switch (tag) {
      case "View", "div" -> "View";
      case "Text", "span", "label" -> "Text";
      case "Button", "button" -> "Button";
      case "Progress", "progress" -> "Progress";
      case "TextField", "input" -> "TextField";
      case "ScrollView", "scroll" -> "ScrollView";
      case "Toggle", "toggle" -> "Toggle";
      case "Slider", "slider" -> "Slider";
      case "Image", "img" -> "Image";
      default -> throw new IllegalArgumentException("Unsupported UI tag: " + tag);
    };
  }

  private static Object bind(String text, Map<String, Supplier<?>> bindings) {
    int first = text.indexOf(BINDING_START);
    if (first < 0) return text;
    List<String> parts = new ArrayList<>();
    List<Supplier<?>> sources = new ArrayList<>();
    int offset = 0;
    while (first >= 0) {
      int end = text.indexOf('}', first + 2);
      if (end < 0) throw new IllegalArgumentException("Unclosed UI binding in: " + text);
      String name = text.substring(first + 2, end);
      Supplier<?> source = bindings.get(name);
      if (name.isBlank() || source == null)
        throw new IllegalArgumentException("Unknown UI binding: " + name);
      parts.add(text.substring(offset, first));
      sources.add(source);
      offset = end + 1;
      first = text.indexOf(BINDING_START, offset);
    }
    parts.add(text.substring(offset));
    if (sources.size() == 1 && parts.get(0).isEmpty() && parts.get(1).isEmpty())
      return sources.get(0);
    return (Supplier<String>)
        () -> {
          StringBuilder value = new StringBuilder(parts.get(0));
          for (int i = 0; i < sources.size(); i++) {
            Object resolved = sources.get(i).get();
            value.append(resolved == null ? "" : resolved);
            value.append(parts.get(i + 1));
          }
          return value.toString();
        };
  }

  private static Object progressValue(String text, Map<String, Supplier<?>> bindings) {
    if (!text.contains(BINDING_START)) return finiteNumber(text, "value");
    Object binding = bind(text, bindings);
    if (binding instanceof Supplier<?>
        && text.startsWith(BINDING_START)
        && text.endsWith("}")
        && text.indexOf('}', 2) == text.length() - 1) return binding;
    throw new IllegalArgumentException("Progress value binding must be a single numeric ${name}");
  }

  private static float finiteNumber(String value, String name) {
    try {
      float parsed = Float.parseFloat(value);
      if (!Float.isFinite(parsed)) throw new NumberFormatException("not finite");
      return parsed;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid numeric " + name + ": " + value, e);
    }
  }

  private static boolean booleanValue(String value, String name) {
    if (value.isEmpty() || value.equalsIgnoreCase("true")) return true;
    if (value.equalsIgnoreCase("false")) return false;
    throw new IllegalArgumentException("Invalid boolean " + name + ": " + value);
  }
}
