package org.javity.ui.flex;

/** The bounded set of object-fit behaviors supported by declarative images. */
public enum ImageFit {
  CONTAIN,
  COVER,
  STRETCH;

  public static ImageFit fromValue(String value) {
    if (value == null) return CONTAIN;
    return switch (value) {
      case "contain" -> CONTAIN;
      case "cover" -> COVER;
      case "stretch" -> STRETCH;
      default -> throw new IllegalArgumentException("Image fit must be contain, cover, or stretch");
    };
  }

  public String value() {
    return name().toLowerCase(java.util.Locale.ROOT);
  }
}
