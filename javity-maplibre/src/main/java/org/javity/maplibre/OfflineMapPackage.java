package org.javity.maplibre;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** A validated, read-only view of one self-contained MapLibre map package. */
public final class OfflineMapPackage {
  private static final int MAX_JSON_BYTES = 2 * 1024 * 1024;
  private static final int MAX_TEMPLATE_TREE_ENTRIES = 10_000;
  private static final double MAX_MERCATOR_LATITUDE = 85.05112878;
  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_-]{1,128}");
  private static final Pattern TEMPLATE_TOKEN = Pattern.compile("\\{[A-Za-z0-9_-]+}");
  private static final byte[] SQLITE_HEADER =
      "SQLite format 3\0".getBytes(StandardCharsets.US_ASCII);

  private final Path root;
  private final Map<String, Path> styles;
  private final Map<String, Path> sources;
  private final String defaultStyle;
  private final Camera initialCamera;
  private final String attribution;

  private OfflineMapPackage(
      Path root,
      Map<String, Path> styles,
      Map<String, Path> sources,
      String defaultStyle,
      Camera initialCamera,
      String attribution) {
    this.root = root;
    this.styles = Collections.unmodifiableMap(new LinkedHashMap<>(styles));
    this.sources = Collections.unmodifiableMap(new LinkedHashMap<>(sources));
    this.defaultStyle = defaultStyle;
    this.initialCamera = initialCamera;
    this.attribution = attribution;
  }

  /** Opens and validates a local package. No database is opened or modified. */
  public static OfflineMapPackage open(Path packageDirectory) {
    if (packageDirectory == null) throw invalid("Package directory is required");
    try {
      Path root = packageDirectory.toRealPath();
      if (!Files.isDirectory(root))
        throw invalid("Package path is not a directory: " + packageDirectory);
      Path manifestPath = checkedExisting(root, "map.json", "manifest");
      if (!Files.isRegularFile(manifestPath))
        throw invalid("Manifest is not a regular file: map.json");
      String manifestText = readJson(manifestPath, "manifest");
      JsonValue manifest = parseObject(manifestText, "manifest");

      if (requiredInt(manifest, "version", "manifest") != 1)
        throw invalid("Unsupported map package version; expected 1");

      JsonValue stylesNode = requiredObject(manifest, "styles", "manifest");
      if (stylesNode.size == 0) throw invalid("Manifest styles must not be empty");
      Map<String, Path> styles = new LinkedHashMap<>();
      for (JsonValue style = stylesNode.child; style != null; style = style.next) {
        requireIdentifier(style.name, "style name");
        String relative = requireString(style, "style path", "manifest.styles." + style.name);
        Path stylePath = checkedExisting(root, relative, "style " + style.name);
        if (!Files.isRegularFile(stylePath))
          throw invalid("Style is not a regular file: " + relative);
        styles.put(style.name, stylePath);
      }

      String defaultStyle = requiredString(manifest, "defaultStyle", "manifest");
      if (!styles.containsKey(defaultStyle))
        throw invalid("defaultStyle does not name a declared style: " + defaultStyle);

      JsonValue sourcesNode = requiredObject(manifest, "sources", "manifest");
      if (sourcesNode.size == 0) throw invalid("Manifest sources must not be empty");
      Map<String, Path> sources = new LinkedHashMap<>();
      for (JsonValue source = sourcesNode.child; source != null; source = source.next) {
        requireIdentifier(source.name, "source name");
        String relative = requireString(source, "source path", "manifest.sources." + source.name);
        Path mbtiles = checkedExisting(root, relative, "MBTiles source " + source.name);
        if (!Files.isRegularFile(mbtiles))
          throw invalid("MBTiles source is not a regular file: " + relative);
        verifySqliteHeader(mbtiles, source.name);
        sources.put(source.name, mbtiles);
      }

      JsonValue cameraNode = requiredObject(manifest, "initialCamera", "manifest");
      Camera camera =
          new Camera(
              requiredFinite(cameraNode, "longitude", "manifest.initialCamera"),
              requiredFinite(cameraNode, "latitude", "manifest.initialCamera"),
              requiredFinite(cameraNode, "zoom", "manifest.initialCamera"),
              optionalFinite(cameraNode, "bearing", 0, "manifest.initialCamera"),
              optionalFinite(cameraNode, "pitch", 0, "manifest.initialCamera"));
      validateCamera(camera);

      String attribution = requiredString(manifest, "attribution", "manifest");
      OfflineMapPackage result =
          new OfflineMapPackage(root, styles, sources, defaultStyle, camera, attribution);
      for (Map.Entry<String, Path> entry : styles.entrySet())
        result.readAndValidateStyle(entry.getKey());
      return result;
    } catch (IOException | RuntimeException e) {
      if (e instanceof IllegalArgumentException iae) throw iae;
      throw invalid("Could not open offline map package: " + safeMessage(e), e);
    }
  }

  /** The real, canonical package directory. */
  public Path root() {
    return root;
  }

  /** Declared style IDs in manifest order. */
  public Set<String> styleNames() {
    return Collections.unmodifiableSet(new LinkedHashSet<>(styles.keySet()));
  }

  public String defaultStyle() {
    return defaultStyle;
  }

  public Camera initialCamera() {
    return initialCamera;
  }

  public String attribution() {
    return attribution;
  }

  /**
   * Returns the MapLibre style JSON v8 with only package resource URLs resolved. Layer definitions,
   * expressions, and other style members are retained as parsed JSON values.
   */
  public String resolvedStyle(String styleName) {
    if (styleName == null || !styles.containsKey(styleName))
      throw invalid("Unknown style: " + styleName);
    return readAndValidateStyle(styleName).toJson(JsonWriter.OutputType.json);
  }

  private JsonValue readAndValidateStyle(String styleName) {
    Path path = styles.get(styleName);
    JsonValue style;
    try {
      style = parseObject(readJson(path, "style " + styleName), "style " + styleName);
    } catch (IOException e) {
      throw invalid("Could not read style " + styleName + ": " + safeMessage(e), e);
    }
    if (requiredInt(style, "version", "style " + styleName) != 8)
      throw invalid("Style " + styleName + " must use MapLibre style version 8");

    JsonValue styleSources = requiredObject(style, "sources", "style " + styleName);
    JsonValue layers = style.get("layers");
    if (layers == null || !layers.isArray())
      throw invalid("Style " + styleName + " must contain a layers array");
    for (JsonValue source = styleSources.child; source != null; source = source.next) {
      if (!source.isObject()) throw invalid("Style source must be an object: " + source.name);
      JsonValue url = source.get("url");
      if (url != null) {
        String value = requireString(url, "source URL", "style source " + source.name);
        String resolved = resolveResourceUrl(value, source.name);
        url.set(resolved);
      }
      JsonValue tiles = source.get("tiles");
      if (tiles != null) {
        if (!tiles.isArray() || tiles.size == 0)
          throw invalid("Style source tiles must be a non-empty array: " + source.name);
        for (JsonValue tile = tiles.child; tile != null; tile = tile.next) {
          String value = requireString(tile, "tile URL", "style source " + source.name);
          tile.set(resolveResourceUrl(value, source.name));
        }
      }
      JsonValue data = source.get("data");
      if (data != null && data.isString()) {
        String value = data.asString();
        if (hasScheme(value) || value.startsWith("//"))
          throw invalid("Offline source data cannot use an absolute URL: " + value);
        data.set(resolveLocalResource(value, false, "source data"));
      }
    }

    JsonValue glyphs = style.get("glyphs");
    if (glyphs != null) {
      String value = requireString(glyphs, "glyph URL", "style " + styleName);
      glyphs.set(resolveLocalResource(value, true, "glyph URL"));
    }
    JsonValue sprite = style.get("sprite");
    if (sprite != null) resolveSprite(sprite, styleName);
    return style;
  }

  private String resolveResourceUrl(String value, String logicalSource) {
    if (value.startsWith("mbtiles://@")) {
      String name = value.substring("mbtiles://@".length());
      if (!IDENTIFIER.matcher(name).matches() || !sources.containsKey(name))
        throw invalid(
            "Style source " + logicalSource + " references an unmapped MBTiles source: " + value);
      return "mbtiles://" + sources.get(name).toUri().getRawPath();
    }
    if (hasScheme(value) || value.startsWith("//"))
      throw invalid(
          "Offline style URL must be a mapped MBTiles source or package-relative file: " + value);
    return resolveLocalResource(value, true, "style URL");
  }

  private void resolveSprite(JsonValue sprite, String styleName) {
    if (sprite.isString()) {
      String base = requireString(sprite, "sprite URL", "style " + styleName);
      sprite.set(resolveSpriteBase(base));
      return;
    }
    if (sprite.isArray()) {
      if (sprite.size == 0) throw invalid("Sprite array must not be empty in style " + styleName);
      for (JsonValue item = sprite.child; item != null; item = item.next) {
        if (!item.isObject())
          throw invalid("Sprite variants must be objects in style " + styleName);
        JsonValue url = item.get("url");
        if (url == null) throw invalid("Sprite variant is missing url in style " + styleName);
        url.set(resolveSpriteBase(requireString(url, "sprite URL", "style " + styleName)));
      }
      return;
    }
    throw invalid("sprite must be a relative URL or array of URL variants in style " + styleName);
  }

  private String resolveSpriteBase(String value) {
    Path base = checkedRelativePath(value, "sprite URL");
    Path json = checkedExisting(root, value + ".json", "sprite JSON");
    Path png = checkedExisting(root, value + ".png", "sprite image");
    if (!Files.isRegularFile(json) || !Files.isRegularFile(png))
      throw invalid("Sprite must have regular .json and .png files: " + value);
    return fileUrl(base);
  }

  /**
   * Resolves a package-relative path, retaining MapLibre's whole-segment placeholders. For
   * templated URLs, the static parent path must exist as a directory, and the subtree is checked
   * for symlinks before MapLibre selects requested glyphs or tiles from it.
   */
  private String resolveLocalResource(String value, boolean allowTemplate, String description) {
    Path relative = checkedRelativePath(value, description);
    int templateAt = value.indexOf('{');
    if (templateAt >= 0) {
      if (!allowTemplate)
        throw invalid("Templates are not allowed in " + description + ": " + value);
      validateTemplatePath(value, description);
      String staticPrefix = value.substring(0, templateAt);
      int lastSlash = staticPrefix.lastIndexOf('/');
      String parentText = lastSlash < 0 ? "" : staticPrefix.substring(0, lastSlash);
      Path parent =
          parentText.isEmpty()
              ? root
              : checkedExisting(root, parentText, description + " directory");
      if (!Files.isDirectory(parent))
        throw invalid(description + " template directory does not exist: " + parentText);
      validateTemplateTree(parent, description);
      return fileUrl(relative);
    }
    Path resolved = checkedExisting(root, value, description);
    if (!Files.isRegularFile(resolved))
      throw invalid(description + " is not a regular file: " + value);
    return fileUrl(relative);
  }

  private String fileUrl(Path relative) {
    String normalized = relative.toString().replace('\\', '/');
    StringBuilder out = new StringBuilder(root.toUri().toASCIIString());
    String[] segments = normalized.split("/");
    for (int i = 0; i < segments.length; i++) {
      if (i > 0) out.append('/');
      String segment = segments[i];
      out.append(encodePathSegmentWithTemplates(segment));
    }
    return out.toString();
  }

  private static String encodePathSegmentWithTemplates(String segment) {
    java.util.regex.Matcher matcher = TEMPLATE_TOKEN.matcher(segment);
    StringBuilder encoded = new StringBuilder();
    int cursor = 0;
    while (matcher.find()) {
      encoded.append(encodePathSegment(segment.substring(cursor, matcher.start())));
      encoded.append(matcher.group());
      cursor = matcher.end();
    }
    encoded.append(encodePathSegment(segment.substring(cursor)));
    return encoded.toString();
  }

  private static String encodePathSegment(String segment) {
    if (segment.isEmpty()) return "";
    try {
      return new URI(null, null, "/" + segment, null).getRawPath().substring(1);
    } catch (URISyntaxException e) {
      throw invalid("Invalid package path segment: " + segment, e);
    }
  }

  private static void validateTemplatePath(String value, String description) {
    String[] segments = value.split("/");
    boolean found = false;
    for (String segment : segments) {
      if (segment.indexOf('{') >= 0 || segment.indexOf('}') >= 0) {
        String withoutTokens = TEMPLATE_TOKEN.matcher(segment).replaceAll("");
        if (withoutTokens.indexOf('{') >= 0 || withoutTokens.indexOf('}') >= 0)
          throw invalid("Invalid URL template in " + description + ": " + value);
        found |= withoutTokens.length() != segment.length();
      }
    }
    if (!found) throw invalid("Invalid URL template in " + description + ": " + value);
  }

  /**
   * Rejects symlinks in templated subtrees so a later placeholder expansion cannot escape the
   * package.
   */
  private void validateTemplateTree(Path directory, String description) {
    try (var entries = Files.walk(directory)) {
      Iterator<Path> iterator = entries.iterator();
      int count = 0;
      while (iterator.hasNext()) {
        Path entry = iterator.next();
        if (++count > MAX_TEMPLATE_TREE_ENTRIES)
          throw invalid(
              description + " template subtree exceeds " + MAX_TEMPLATE_TREE_ENTRIES + " entries");
        if (Files.isSymbolicLink(entry)) {
          throw invalid(
              description
                  + " template subtree cannot contain symbolic links: "
                  + root.relativize(entry));
        }
      }
    } catch (IOException e) {
      throw invalid("Could not inspect " + description + " template subtree: " + safeMessage(e), e);
    }
  }

  private static Path checkedExisting(Path root, String relativeText, String description) {
    Path relative = checkedRelativePath(relativeText, description);
    try {
      Path resolved = root.resolve(relative).normalize();
      if (!resolved.startsWith(root))
        throw invalid(description + " escapes package root: " + relativeText);
      if (!Files.exists(resolved, LinkOption.NOFOLLOW_LINKS))
        throw invalid(description + " does not exist: " + relativeText);
      Path real = resolved.toRealPath();
      if (!real.startsWith(root))
        throw invalid(description + " resolves outside package root: " + relativeText);
      return real;
    } catch (IOException e) {
      throw invalid("Could not resolve " + description + ": " + safeMessage(e), e);
    }
  }

  private static Path checkedRelativePath(String text, String description) {
    if (text == null || text.isBlank())
      throw invalid(description + " must be a non-empty relative path");
    if (text.indexOf('\\') >= 0
        || text.indexOf('%') >= 0
        || text.indexOf('?') >= 0
        || text.indexOf('#') >= 0)
      throw invalid(description + " contains an unsupported path character: " + text);
    if (text.startsWith("/") || text.startsWith("//") || hasScheme(text))
      throw invalid(description + " must be package-relative: " + text);
    Path path;
    try {
      path = Path.of(text);
    } catch (RuntimeException e) {
      throw invalid("Invalid " + description + ": " + text, e);
    }
    if (path.isAbsolute()) throw invalid(description + " must be package-relative: " + text);
    for (Path segment : path) {
      if (segment.toString().equals("..") || segment.toString().equals("."))
        throw invalid(description + " contains traversal: " + text);
    }
    return path.normalize();
  }

  private static boolean hasScheme(String value) {
    try {
      return URI.create(value).isAbsolute();
    } catch (IllegalArgumentException malformed) {
      return value.matches("^[A-Za-z][A-Za-z0-9+.-]*:.*");
    }
  }

  private static String readJson(Path path, String description) throws IOException {
    long size = Files.size(path);
    if (size > MAX_JSON_BYTES) throw invalid(description + " exceeds " + MAX_JSON_BYTES + " bytes");
    try (InputStream input = Files.newInputStream(path)) {
      byte[] data = input.readNBytes(MAX_JSON_BYTES + 1);
      if (data.length > MAX_JSON_BYTES)
        throw invalid(description + " exceeds " + MAX_JSON_BYTES + " bytes");
      return new String(data, StandardCharsets.UTF_8);
    }
  }

  private static JsonValue parseObject(String text, String description) {
    try {
      JsonValue value = new JsonReader().parse(text);
      if (!value.isObject()) throw invalid(description + " must be a JSON object");
      return value;
    } catch (RuntimeException e) {
      if (e instanceof IllegalArgumentException iae) throw iae;
      throw invalid("Invalid JSON in " + description + ": " + safeMessage(e), e);
    }
  }

  private static JsonValue requiredObject(JsonValue parent, String key, String context) {
    JsonValue value = parent.get(key);
    if (value == null || !value.isObject())
      throw invalid(context + "." + key + " must be an object");
    return value;
  }

  private static String requiredString(JsonValue parent, String key, String context) {
    JsonValue value = parent.get(key);
    return requireString(value, key, context);
  }

  private static String requireString(JsonValue value, String label, String context) {
    if (value == null || !value.isString() || value.asString().isBlank())
      throw invalid(context + "." + label + " must be a non-empty string");
    return value.asString();
  }

  private static int requiredInt(JsonValue parent, String key, String context) {
    JsonValue value = parent.get(key);
    if (value == null || !value.isNumber())
      throw invalid(context + "." + key + " must be an integer");
    double number = value.asDouble();
    if (!Double.isFinite(number)
        || number != Math.rint(number)
        || number < Integer.MIN_VALUE
        || number > Integer.MAX_VALUE) throw invalid(context + "." + key + " must be an integer");
    return (int) number;
  }

  private static double requiredFinite(JsonValue parent, String key, String context) {
    JsonValue value = parent.get(key);
    if (value == null || !value.isNumber())
      throw invalid(context + "." + key + " must be a finite number");
    double number = value.asDouble();
    if (!Double.isFinite(number)) throw invalid(context + "." + key + " must be a finite number");
    return number;
  }

  private static double optionalFinite(
      JsonValue parent, String key, double fallback, String context) {
    JsonValue value = parent.get(key);
    if (value == null) return fallback;
    return requiredFinite(parent, key, context);
  }

  private static void requireIdentifier(String value, String kind) {
    if (value == null || !IDENTIFIER.matcher(value).matches())
      throw invalid("Invalid " + kind + ": " + value);
  }

  private static void validateCamera(Camera camera) {
    validateCameraValues(
        camera.longitude, camera.latitude, camera.zoom, camera.bearing, camera.pitch);
  }

  private static void validateCameraValues(
      double longitude, double latitude, double zoom, double bearing, double pitch) {
    if (!Double.isFinite(longitude)
        || !Double.isFinite(latitude)
        || !Double.isFinite(zoom)
        || !Double.isFinite(bearing)
        || !Double.isFinite(pitch)) throw invalid("Camera values must be finite");
    if (longitude < -180 || longitude > 180)
      throw invalid("Camera longitude must be between -180 and 180");
    if (latitude < -MAX_MERCATOR_LATITUDE || latitude > MAX_MERCATOR_LATITUDE)
      throw invalid("Camera latitude must be within the Web Mercator range");
    if (zoom < 0 || zoom > 22) throw invalid("Camera zoom must be between 0 and 22");
    if (pitch < 0 || pitch > 60) throw invalid("Camera pitch must be between 0 and 60");
  }

  private static void verifySqliteHeader(Path path, String name) throws IOException {
    byte[] header = new byte[SQLITE_HEADER.length];
    try (InputStream input = Files.newInputStream(path)) {
      int count = input.readNBytes(header, 0, header.length);
      if (count != header.length || !java.util.Arrays.equals(SQLITE_HEADER, header))
        throw invalid("MBTiles source " + name + " does not have a SQLite file header");
    }
  }

  private static IllegalArgumentException invalid(String message) {
    return new IllegalArgumentException(message);
  }

  private static IllegalArgumentException invalid(String message, Throwable cause) {
    return new IllegalArgumentException(message, cause);
  }

  private static String safeMessage(Throwable e) {
    return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
  }

  public record Camera(
      double longitude, double latitude, double zoom, double bearing, double pitch) {
    public Camera {
      validateCameraValues(longitude, latitude, zoom, bearing, pitch);
    }
  }
}
