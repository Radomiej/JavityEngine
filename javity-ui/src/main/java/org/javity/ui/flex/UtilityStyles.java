package org.javity.ui.flex;

import com.badlogic.gdx.graphics.Color;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.javity.ui.yoga.YogaNode;
import org.javity.ui.yoga.YogaNode.Align;
import org.javity.ui.yoga.YogaNode.Edge;
import org.javity.ui.yoga.YogaNode.FlexDirection;
import org.javity.ui.yoga.YogaNode.Gutter;
import org.javity.ui.yoga.YogaNode.Justify;
import org.javity.ui.yoga.YogaNode.Wrap;

/** Strict, measured subset of Tailwind 4.3.3 utilities used by the native UI. */
public final class UtilityStyles {
  private static final Pattern SIZE =
      Pattern.compile(
          "(p|px|py|pt|pr|pb|pl|m|mx|my|mt|mr|mb|ml|gap-x|gap-y|gap|min-w|min-h|max-w|max-h|w|h|basis)-(.+)");
  private static final Pattern SIMPLE_NUMBER = Pattern.compile("[0-9]+(?:\\.[0-9]+)?");
  private static final Pattern PIXELS = Pattern.compile("\\[([0-9]+(?:\\.[0-9]+)?)px\\]");

  public record Visual(
      Color background,
      Color text,
      float textScale,
      float fontSize,
      float lineHeight,
      float radius,
      float borderWidth,
      Color borderColor) {
    public float radiusFor(float width, float height) {
      return Math.min(radius, Math.max(0, Math.min(width, height) / 2));
    }

    public float borderFor(float width, float height) {
      return Math.min(borderWidth, Math.max(0, Math.min(width, height) / 2));
    }

    public Visual withBackground(Color color) {
      return new Visual(
          color, text, textScale, fontSize, lineHeight, radius, borderWidth, borderColor);
    }
  }

  public record Insets(float top, float right, float bottom, float left) {
    public float horizontal() {
      return left + right;
    }

    public float vertical() {
      return top + bottom;
    }
  }

  private UtilityStyles() {}

  public static Visual apply(YogaNode node, String classes) {
    return apply(node, classes, "");
  }

  /** Applies base utilities plus one visual state. State utilities cannot affect Yoga geometry. */
  public static Visual apply(YogaNode node, String classes, String state) {
    Color background = null, text = Color.BLACK.cpy();
    float textScale = 1, fontSize = 16, lineHeight = 24, radius = 0, borderWidth = 0;
    boolean halfOpacity = false;
    Color borderColor = null;
    boolean borderSpecified = false;
    for (String raw : classes.trim().split("\\s+")) {
      if (raw.isEmpty()) continue;
      String token = raw;
      int colon = token.indexOf(':');
      if (colon >= 0) {
        String variant = token.substring(0, colon);
        token = token.substring(colon + 1);
        if (!variant.equals("hover")
            && !variant.equals("active")
            && !variant.equals("focus")
            && !variant.equals("disabled"))
          throw new IllegalArgumentException("Unsupported UI variant: " + raw);
        if (token.isEmpty()) throw new IllegalArgumentException("Invalid UI variant: " + raw);
        validateStateUtility(token, raw);
        if (!variant.equals(state)) continue;
      }
      switch (token) {
        case "flex" -> node.setFlexDirection(FlexDirection.ROW);
        case "flex-row" -> node.setFlexDirection(FlexDirection.ROW);
        case "flex-col" -> node.setFlexDirection(FlexDirection.COLUMN);
        case "flex-wrap" -> node.setFlexWrap(Wrap.WRAP);
        case "flex-nowrap" -> node.setFlexWrap(Wrap.NO_WRAP);
        case "appearance-none" -> {} // Used only by UiDocument's Progress node contract.
        case "outline-none" -> {} // Scene2D controls have no browser user-agent outline.
        case "overflow-y-auto" -> {} // UiDocument maps this to its clipping ScrollPane.
        case "flex-1" -> {
          node.setFlexGrow(1);
          node.setFlexBasis(0);
        }
        case "grow" -> node.setFlexGrow(1);
        case "shrink-0" -> node.setFlexShrink(0);
        case "items-start" -> node.setAlignItems(Align.START);
        case "items-center" -> node.setAlignItems(Align.CENTER);
        case "items-end" -> node.setAlignItems(Align.END);
        case "items-stretch" -> node.setAlignItems(Align.STRETCH);
        case "self-start" -> node.setAlignSelf(Align.START);
        case "self-center" -> node.setAlignSelf(Align.CENTER);
        case "self-end" -> node.setAlignSelf(Align.END);
        case "self-stretch" -> node.setAlignSelf(Align.STRETCH);
        case "justify-start" -> node.setJustifyContent(Justify.START);
        case "justify-center" -> node.setJustifyContent(Justify.CENTER);
        case "justify-end" -> node.setJustifyContent(Justify.END);
        case "justify-between" -> node.setJustifyContent(Justify.SPACE_BETWEEN);
        case "w-full" -> node.setWidthPercent(100);
        case "h-full" -> node.setHeightPercent(100);
        case "w-auto" -> node.setWidthAuto();
        case "h-auto" -> node.setHeightAuto();
        case "text-sm" -> {
          fontSize = 14;
          lineHeight = 20;
          textScale = .875f;
        }
        case "text-base" -> {
          fontSize = 16;
          lineHeight = 24;
          textScale = 1;
        }
        case "text-lg" -> {
          fontSize = 18;
          lineHeight = 28;
          textScale = 1.125f;
        }
        case "text-xl" -> {
          fontSize = 20;
          lineHeight = 28;
          textScale = 1.25f;
        }
        case "text-2xl" -> {
          fontSize = 24;
          lineHeight = 32;
          textScale = 1.5f;
        }
        case "opacity-50" -> halfOpacity = true;
        case "rounded", "rounded-sm" -> radius = 4;
        case "rounded-none" -> radius = 0;
        case "rounded-md" -> radius = 6;
        case "rounded-lg" -> radius = 8;
        case "rounded-xl" -> radius = 12;
        case "rounded-2xl" -> radius = 16;
        case "rounded-3xl" -> radius = 24;
        case "rounded-full" -> radius = Float.POSITIVE_INFINITY;
        case "border" -> {
          borderWidth = 1;
          borderSpecified = true;
        }
        default -> {
          if (token.matches("border-[0-9]+")) {
            borderWidth = number(token.substring(7), token);
            borderSpecified = true;
          } else if (token.startsWith("border-[#"))
            borderColor = color(token.substring(8, token.length() - 1));
          else if (token.startsWith("border-")) borderColor = color(token.substring(7));
          else if (token.matches("rounded-[0-9]+")) radius = number(token.substring(8), token) * 4;
          else if (token.startsWith("bg-[#"))
            background = color(token.substring(4, token.length() - 1));
          else if (token.startsWith("bg-")) background = color(token.substring(3));
          else if (token.startsWith("text-[#"))
            text = color(token.substring(6, token.length() - 1));
          else if (token.matches(
              "text-(white|black|slate-[0-9]+|cyan-[0-9]+|blue-[0-9]+|red-[0-9]+|amber-[0-9]+|emerald-[0-9]+)"))
            text = color(token.substring(5));
          else if (applySize(node, token)) {
          } else throw new IllegalArgumentException("Unsupported UI utility: " + raw);
        }
      }
    }
    if (borderSpecified) node.setBorder(Edge.ALL, borderWidth);
    if (halfOpacity) {
      if (background != null) background.a *= .5f;
      text.a *= .5f;
      if (borderColor != null) borderColor.a *= .5f;
    }
    return new Visual(
        background,
        text,
        textScale,
        fontSize,
        lineHeight,
        radius,
        borderWidth,
        borderColor == null ? text.cpy() : borderColor);
  }

  private static boolean applySize(YogaNode node, String token) {
    Matcher matcher = SIZE.matcher(token);
    if (!matcher.matches()) return false;
    String prop = matcher.group(1), size = matcher.group(2);
    float value;
    if (size.equals("full") && (prop.equals("w") || prop.equals("h"))) {
      if (prop.equals("w")) node.setWidthPercent(100);
      else node.setHeightPercent(100);
      return true;
    }
    if (size.equals("auto") && (prop.equals("w") || prop.equals("h"))) {
      if (prop.equals("w")) node.setWidthAuto();
      else node.setHeightAuto();
      return true;
    }
    Matcher pixels = PIXELS.matcher(size);
    if (pixels.matches()) value = number(pixels.group(1), token);
    else if (SIMPLE_NUMBER.matcher(size).matches()) value = number(size, token) * 4;
    else return false;
    switch (prop) {
      case "p" -> node.setPadding(Edge.ALL, value);
      case "px" -> node.setPadding(Edge.HORIZONTAL, value);
      case "py" -> node.setPadding(Edge.VERTICAL, value);
      case "pt" -> node.setPadding(Edge.TOP, value);
      case "pr" -> node.setPadding(Edge.RIGHT, value);
      case "pb" -> node.setPadding(Edge.BOTTOM, value);
      case "pl" -> node.setPadding(Edge.LEFT, value);
      case "m" -> node.setMargin(Edge.ALL, value);
      case "mx" -> node.setMargin(Edge.HORIZONTAL, value);
      case "my" -> node.setMargin(Edge.VERTICAL, value);
      case "mt" -> node.setMargin(Edge.TOP, value);
      case "mr" -> node.setMargin(Edge.RIGHT, value);
      case "mb" -> node.setMargin(Edge.BOTTOM, value);
      case "ml" -> node.setMargin(Edge.LEFT, value);
      case "gap" -> node.setGap(Gutter.ALL, value);
      case "gap-x" -> node.setGap(Gutter.COLUMN, value);
      case "gap-y" -> node.setGap(Gutter.ROW, value);
      case "w" -> node.setWidth(value);
      case "h" -> node.setHeight(value);
      case "min-w" -> node.setMinWidth(value);
      case "min-h" -> node.setMinHeight(value);
      case "max-w" -> node.setMaxWidth(value);
      case "max-h" -> node.setMaxHeight(value);
      case "basis" -> node.setFlexBasis(value);
      default -> throw new IllegalStateException(prop);
    }
    return true;
  }

  private static void validateStateUtility(String token, String raw) {
    if (token.matches("border-[0-9]+"))
      throw new IllegalArgumentException(
          "Border width cannot change through a state variant: " + raw);
    if (!(token.startsWith("bg-")
        || token.startsWith("text-")
        || token.startsWith("border-")
        || token.equals("opacity-50")
        || token.equals("outline-none")))
      throw new IllegalArgumentException("Only visual utilities support state variants: " + raw);
    // Validate named colors even while a non-active variant is being parsed.
    if (token.startsWith("bg-")) color(token.substring(3));
    else if (token.startsWith("text-")) {
      String value = token.substring(5);
      if (value.startsWith("[#")) color(value.substring(1, value.length() - 1));
      else color(value);
    } else if (token.startsWith("border-") && !token.matches("border-[0-9]+")) {
      String value = token.substring(7);
      if (value.startsWith("[#")) color(value.substring(1, value.length() - 1));
      else color(value);
    }
  }

  private static float number(String value, String token) {
    float result = Float.parseFloat(value);
    if (!Float.isFinite(result) || result < 0 || result > Float.MAX_VALUE / 4)
      throw new IllegalArgumentException("Invalid size: " + token);
    return result;
  }

  public static Color color(String name) {
    if (name.startsWith("#")) {
      String hex = name.substring(1);
      if (hex.matches("[0-9a-fA-F]{3}"))
        hex =
            ""
                + hex.charAt(0)
                + hex.charAt(0)
                + hex.charAt(1)
                + hex.charAt(1)
                + hex.charAt(2)
                + hex.charAt(2);
      else if (hex.matches("[0-9a-fA-F]{4}"))
        hex =
            ""
                + hex.charAt(0)
                + hex.charAt(0)
                + hex.charAt(1)
                + hex.charAt(1)
                + hex.charAt(2)
                + hex.charAt(2)
                + hex.charAt(3)
                + hex.charAt(3);
      else if (!hex.matches("[0-9a-fA-F]{6}([0-9a-fA-F]{2})?"))
        throw new IllegalArgumentException("Unsupported UI color: " + name);
      return Color.valueOf(hex);
    }
    return Color.valueOf(
        switch (name) {
          case "white" -> "ffffff";
          case "black" -> "000000";
          case "slate-900" -> "0f172b";
          case "slate-800" -> "1d293d";
          case "slate-700" -> "314158";
          case "slate-400" -> "90a1b9";
          case "slate-200" -> "e2e8f0";
          case "cyan-400" -> "00d3f3";
          case "emerald-500" -> "00bc7d";
          case "blue-600" -> "155dfc";
          case "blue-700" -> "1447e6";
          case "red-500" -> "fb2c36";
          case "amber-400" -> "ffb900";
          default -> throw new IllegalArgumentException("Unsupported UI color: " + name);
        });
  }

  /** Padding utilities, used to place Scene2D content inside the same CSS content box. */
  public static Insets padding(String classes) {
    float top = 0, right = 0, bottom = 0, left = 0;
    for (String raw : classes.trim().split("\\s+")) {
      if (raw.isEmpty()) continue;
      int colon = raw.indexOf(':');
      String token = colon < 0 ? raw : raw.substring(colon + 1);
      Matcher matcher = SIZE.matcher(token);
      if (!matcher.matches()) continue;
      String prop = matcher.group(1);
      if (!(prop.equals("p")
          || prop.equals("px")
          || prop.equals("py")
          || prop.equals("pt")
          || prop.equals("pr")
          || prop.equals("pb")
          || prop.equals("pl"))) continue;
      String rawSize = matcher.group(2);
      Matcher pixels = PIXELS.matcher(rawSize);
      float value = pixels.matches() ? number(pixels.group(1), raw) : number(rawSize, raw) * 4;
      switch (prop) {
        case "p" -> {
          top = value;
          right = value;
          bottom = value;
          left = value;
        }
        case "px" -> {
          right = value;
          left = value;
        }
        case "py" -> {
          top = value;
          bottom = value;
        }
        case "pt" -> top = value;
        case "pr" -> right = value;
        case "pb" -> bottom = value;
        case "pl" -> left = value;
      }
    }
    return new Insets(top, right, bottom, left);
  }
}
