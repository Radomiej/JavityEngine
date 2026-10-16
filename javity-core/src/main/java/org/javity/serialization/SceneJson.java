package org.javity.serialization;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import org.javity.core.*;
import org.javity.render.*;

/**
 * Versioned scene data, no arbitrary class-name loading. Unknown component types fail explicitly.
 */
public final class SceneJson {
  private final Map<String, ComponentCodec<?>> keys = new LinkedHashMap<>();
  private final Map<Class<?>, ComponentCodec<?>> types = new HashMap<>();

  /** One export failure, with stable input indexes so repeated component types remain distinct. */
  public record ExportIssue(
      String path,
      int objectIndex,
      String objectName,
      int componentIndex,
      String componentType,
      String message) {
    @Override
    public String toString() {
      String identity = "object[" + objectIndex + "] '" + objectName + "'";
      if (componentIndex >= 0) identity += " component[" + componentIndex + "] " + componentType;
      return path + " (" + identity + "): " + message;
    }
  }

  /**
   * Immutable result of encoding a single snapshot. Writers are invoked only during preparation.
   */
  public static final class ExportReport {
    private final String candidate;
    private final List<ExportIssue> issues;

    private ExportReport(String candidate, List<ExportIssue> issues) {
      this.candidate = candidate;
      this.issues = List.copyOf(issues);
    }

    public String candidate() {
      return candidate;
    }

    public List<ExportIssue> issues() {
      return issues;
    }

    public boolean isValid() {
      return issues.isEmpty() && candidate != null;
    }
  }

  public <T extends Component> SceneJson register(ComponentCodec<T> codec) {
    Objects.requireNonNull(codec);
    if (keys.containsKey(codec.key()) || types.containsKey(codec.type()))
      throw new IllegalArgumentException("Duplicate codec");
    keys.put(codec.key(), codec);
    types.put(codec.type(), codec);
    return this;
  }

  public <T extends Component> SceneJson register(
      String key, Class<T> type, Function<T, JsonValue> writer, Function<JsonValue, T> reader) {
    return register(
        new ComponentCodec<T>() {
          public String key() {
            return key;
          }

          public Class<T> type() {
            return type;
          }

          public JsonValue write(T c) {
            return writer.apply(c);
          }

          public T read(JsonValue v) {
            return reader.apply(v);
          }
        });
  }

  public static JsonValue object() {
    return new JsonValue(JsonValue.ValueType.object);
  }

  public static JsonValue array() {
    return new JsonValue(JsonValue.ValueType.array);
  }

  public static void number(JsonValue o, String key, float value) {
    if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite scene value");
    o.addChild(key, new JsonValue(value));
  }

  public static void number(JsonValue o, String key, double value) {
    if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite scene value");
    o.addChild(key, new JsonValue(value));
  }

  public static double finiteDouble(JsonValue o, String key, double fallback) {
    JsonValue value = o.get(key);
    double result = value == null ? fallback : value.asDouble();
    if (!Double.isFinite(result)) throw new IllegalArgumentException("Non-finite scene value");
    return result;
  }

  public static void text(JsonValue o, String key, String value) {
    o.addChild(key, new JsonValue(Objects.requireNonNull(value)));
  }

  public static float finite(JsonValue o, String key, float fallback) {
    float v = o.getFloat(key, fallback);
    if (!Float.isFinite(v)) throw new IllegalArgumentException("Non-finite scene value");
    return v;
  }

  @SuppressWarnings("unchecked")
  private JsonValue encode(ComponentCodec<?> codec, Component c) {
    return ((ComponentCodec<Component>) codec).write(c);
  }

  public String write(List<GameObject> objects) {
    ExportReport report = prepare(objects);
    if (!report.isValid()) throw new IllegalArgumentException(formatIssues(report.issues()));
    return report.candidate();
  }

  /** Prepare an explicit selection without writing it to disk. */
  public ExportReport prepare(List<GameObject> objects) {
    return prepareSelection(objects);
  }

  /** Validate and encode the full live scene as one immutable runtime snapshot candidate. */
  public ExportReport prepare(Game game) {
    Objects.requireNonNull(game, "game");
    List<GameObject> objects = game.getObjects();
    List<ExportIssue> issues = new ArrayList<>();
    if (game.isDisposed())
      issues.add(issue("scene", -1, "<scene>", -1, null, "Cannot export a disposed game"));
    IdentityHashMap<GameObject, Boolean> live = new IdentityHashMap<>();
    for (GameObject object : objects) live.put(object, Boolean.TRUE);
    for (int i = 0; i < objects.size(); i++)
      if (objects.get(i).game != game)
        issues.add(
            issue(
                "objects[" + i + "]",
                i,
                safeName(objects.get(i)),
                -1,
                null,
                "Object belongs to a different game"));
    return prepare(objects, game, live, issues);
  }

  /** Validate and encode a caller-selected set of objects as a single prefab/runtime snapshot. */
  public ExportReport prepareSelection(List<GameObject> objects) {
    return prepare(objects, null, null, new ArrayList<>());
  }

  private ExportReport prepare(
      List<GameObject> input,
      Game expectedGame,
      IdentityHashMap<GameObject, Boolean> live,
      List<ExportIssue> issues) {
    if (input == null) {
      issues.add(issue("objects", -1, "<selection>", -1, null, "Object list is null"));
      return new ExportReport(null, issues);
    }
    List<GameObject> objects = new ArrayList<>(input);
    IdentityHashMap<GameObject, Integer> refs = new IdentityHashMap<>();
    Game selectionGame = expectedGame;
    for (int i = 0; i < objects.size(); i++) {
      GameObject object = objects.get(i);
      String name = safeName(object);
      if (object == null) {
        issues.add(issue("objects[" + i + "]", i, name, -1, null, "Object is null"));
        continue;
      }
      Integer firstIndex = refs.get(object);
      if (firstIndex != null) {
        issues.add(issue("objects[" + i + "]", i, name, -1, null, "Duplicate object identity"));
        continue;
      }
      refs.put(object, i);
      if (object.isDestroyed())
        issues.add(issue("objects[" + i + "]", i, name, -1, null, "Object is destroyed"));
      if (selectionGame == null) selectionGame = object.game;
      else if (object.game != selectionGame)
        issues.add(
            issue("objects[" + i + "]", i, name, -1, null, "Object belongs to a different game"));
      if (live != null && !live.containsKey(object))
        issues.add(
            issue(
                "objects[" + i + "]",
                i,
                name,
                -1,
                null,
                "Object is not part of the current live scene"));
    }
    if (selectionGame != null && selectionGame.isDisposed())
      issues.add(
          issue("scene", -1, "<scene>", -1, null, "Cannot export objects from a disposed game"));

    IdentityHashMap<Transform3D, String> transformRefs = new IdentityHashMap<>();
    for (int i = 0; i < objects.size(); i++) {
      GameObject object = objects.get(i);
      if (object == null || !Objects.equals(refs.get(object), i)) continue;
      var components = object.getComponents();
      for (int j = 0; j < components.size(); j++)
        if (components.get(j) instanceof Transform3D transform)
          transformRefs.put(transform, i + ":" + j);
    }

    JsonValue root = object();
    root.addChild("version", new JsonValue(1L));
    JsonValue entries = array();
    root.addChild("objects", entries);
    for (int i = 0; i < objects.size(); i++) {
      GameObject actor = objects.get(i);
      if (actor == null || actor.isDestroyed()) continue;
      if (!Objects.equals(refs.get(actor), i)) continue;
      String name = safeName(actor);
      JsonValue o = object();
      entries.addChild(o);
      if (actor.name == null)
        issues.add(issue("objects[" + i + "].name", i, name, -1, null, "Object name is null"));
      else text(o, "name", actor.name);
      o.addChild("active", new JsonValue(actor.isActive()));
      var t = actor.transform;
      putFinite(o, "x", t.position.x, i, name, "transform", issues);
      putFinite(o, "y", t.position.y, i, name, "transform", issues);
      putFinite(o, "rotation", t.rotation, i, name, "transform", issues);
      putFinite(o, "sx", t.scale.x, i, name, "transform", issues);
      putFinite(o, "sy", t.scale.y, i, name, "transform", issues);
      putFinite(o, "ox", t.visualOffset.x, i, name, "transform", issues);
      putFinite(o, "oy", t.visualOffset.y, i, name, "transform", issues);
      JsonValue tags = array();
      actor.getTags().stream().sorted().forEach(tag -> tags.addChild(new JsonValue(tag)));
      o.addChild("tags", tags);
      JsonValue components = array();
      o.addChild("components", components);
      var cs = actor.getComponents();
      for (int j = 0; j < cs.size(); j++) {
        Component component = cs.get(j);
        String componentType = component.getClass().getName();
        String path = "objects[" + i + "].components[" + j + "]";
        if (component instanceof Transform3D t3) {
          validateTransform3D(t3, path, i, name, j, componentType, issues);
          if (t3.getParent() != null && !transformRefs.containsKey(t3.getParent()))
            issues.add(
                issue(
                    path + ".parent",
                    i,
                    name,
                    j,
                    componentType,
                    "Parent Transform3D is outside the selected snapshot"));
        }
        if (component.updateMode == null)
          issues.add(
              issue(
                  path + ".updateMode",
                  i,
                  name,
                  j,
                  componentType,
                  "Component update mode is null"));
        ComponentCodec<?> codec = types.get(component.getClass());
        if (codec == null) {
          issues.add(
              issue(
                  path,
                  i,
                  name,
                  j,
                  componentType,
                  "No explicitly registered SceneJson codec for " + componentType));
          continue;
        }
        JsonValue data;
        try {
          JsonValue encoded = encode(codec, component);
          if (encoded == null) throw new IllegalArgumentException("Codec returned null data");
          validateJson(encoded, path + ".data", i, name, j, componentType, issues);
          data = new JsonValue(encoded);
          if (component instanceof Transform3D t3 && t3.getParent() != null) {
            String parent = transformRefs.get(t3.getParent());
            if (parent != null && !data.isObject()) {
              issues.add(
                  issue(
                      path + ".data",
                      i,
                      name,
                      j,
                      componentType,
                      "Transform3D codec data must be an object to encode parent reference"));
            } else if (parent != null) {
              text(data, "parent", parent);
            }
          }
        } catch (RuntimeException failure) {
          issues.add(issue(path + ".data", i, name, j, componentType, message(failure)));
          continue;
        }
        JsonValue item = object();
        text(item, "type", codec.key());
        item.addChild("enabled", new JsonValue(component.isEnabled()));
        if (component.updateMode != null) text(item, "updateMode", component.updateMode.name());
        item.addChild("data", data);
        components.addChild(item);
      }
    }
    if (!issues.isEmpty()) return new ExportReport(null, issues);
    try {
      return new ExportReport(root.toJson(JsonWriter.OutputType.json), List.of());
    } catch (RuntimeException failure) {
      issues.add(issue("document", -1, "<scene>", -1, null, message(failure)));
      return new ExportReport(null, issues);
    }
  }

  /** Write the full current scene to the explicit destination using sibling-temp atomic replace. */
  public ExportReport export(Game game, Path destination) throws IOException {
    Objects.requireNonNull(destination, "destination");
    ExportReport report = prepare(game);
    save(report, destination);
    return report;
  }

  /** Write an explicit object selection to the explicit destination using atomic replacement. */
  public ExportReport exportSelection(List<GameObject> objects, Path destination)
      throws IOException {
    Objects.requireNonNull(destination, "destination");
    ExportReport report = prepareSelection(objects);
    save(report, destination);
    return report;
  }

  /** Save a previously prepared candidate without invoking any component codec again. */
  public void save(ExportReport report, Path destination) throws IOException {
    saveCandidate(report, destination);
  }

  private static void saveCandidate(ExportReport report, Path destination) throws IOException {
    Objects.requireNonNull(destination, "destination");
    if (!report.isValid()) throw new IllegalArgumentException(formatIssues(report.issues()));
    Path target = destination.toAbsolutePath();
    Path parent = target.getParent();
    if (parent == null) throw new IOException("Export destination has no parent: " + target);
    Path temporary = Files.createTempFile(parent, "." + target.getFileName() + ".", ".tmp");
    try {
      Files.writeString(
          temporary,
          report.candidate(),
          StandardCharsets.UTF_8,
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE);
      try {
        Files.move(
            temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException unsupported) {
        throw new IOException("Atomic replacement is unavailable for " + target, unsupported);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private void putFinite(
      JsonValue target,
      String key,
      float value,
      int oi,
      String name,
      String path,
      List<ExportIssue> issues) {
    if (!Float.isFinite(value)) {
      issues.add(
          issue(
              "objects[" + oi + "]." + path + "." + key,
              oi,
              name,
              -1,
              null,
              "Transform value is non-finite"));
      return;
    }
    target.addChild(key, new JsonValue(value));
  }

  private static void validateJson(
      JsonValue value,
      String path,
      int oi,
      String name,
      int ci,
      String componentType,
      List<ExportIssue> issues) {
    if (value.isNumber() && !Double.isFinite(value.asDouble()))
      issues.add(issue(path, oi, name, ci, componentType, "JSON number is non-finite"));
    for (JsonValue child = value.child; child != null; child = child.next)
      validateJson(
          child,
          path + (value.isArray() ? "[" + childIndex(value, child) + "]" : "." + child.name),
          oi,
          name,
          ci,
          componentType,
          issues);
  }

  private static int childIndex(JsonValue parent, JsonValue wanted) {
    int index = 0;
    for (JsonValue child = parent.child; child != null; child = child.next, index++)
      if (child == wanted) return index;
    return index;
  }

  private static void validateTransform3D(
      Transform3D t,
      String path,
      int oi,
      String name,
      int ci,
      String type,
      List<ExportIssue> issues) {
    finiteTransform(t.position.x, path + ".position.x", oi, name, ci, type, issues);
    finiteTransform(t.position.y, path + ".position.y", oi, name, ci, type, issues);
    finiteTransform(t.position.z, path + ".position.z", oi, name, ci, type, issues);
    finiteTransform(t.scale.x, path + ".scale.x", oi, name, ci, type, issues);
    finiteTransform(t.scale.y, path + ".scale.y", oi, name, ci, type, issues);
    finiteTransform(t.scale.z, path + ".scale.z", oi, name, ci, type, issues);
    finiteTransform(t.rotation.x, path + ".rotation.x", oi, name, ci, type, issues);
    finiteTransform(t.rotation.y, path + ".rotation.y", oi, name, ci, type, issues);
    finiteTransform(t.rotation.z, path + ".rotation.z", oi, name, ci, type, issues);
    finiteTransform(t.rotation.w, path + ".rotation.w", oi, name, ci, type, issues);
    if (t.rotation.len2() == 0)
      issues.add(issue(path + ".rotation", oi, name, ci, type, "Quaternion must be nonzero"));
  }

  private static void finiteTransform(
      float value,
      String path,
      int oi,
      String name,
      int ci,
      String type,
      List<ExportIssue> issues) {
    if (!Float.isFinite(value))
      issues.add(issue(path, oi, name, ci, type, "Transform value is non-finite"));
  }

  private static String safeName(GameObject object) {
    return object == null ? "<null>" : String.valueOf(object.name);
  }

  private static String message(Throwable failure) {
    return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
  }

  private static ExportIssue issue(
      String path, int oi, String name, int ci, String type, String message) {
    return new ExportIssue(path, oi, name, ci, type, message);
  }

  private static String formatIssues(List<ExportIssue> issues) {
    return String.join("\n", issues.stream().map(ExportIssue::toString).toList());
  }

  public List<GameObject> read(Game game, String document) {
    JsonValue root = new JsonReader().parse(document);
    if (root.getInt("version", -1) != 1)
      throw new IllegalArgumentException("Unsupported scene schema");
    JsonValue entries = root.require("objects");
    if (!entries.isArray()) throw new IllegalArgumentException("objects array required");
    for (JsonValue o : entries)
      for (JsonValue c : o.require("components"))
        if (!keys.containsKey(c.getString("type")))
          throw new IllegalArgumentException("Unknown codec: " + c.getString("type"));
    List<GameObject> created = new ArrayList<>();
    Map<String, Transform3D> refs = new HashMap<>();
    Map<Transform3D, String> parents = new LinkedHashMap<>();
    try {
      int oi = 0;
      for (JsonValue o : entries) {
        GameObject actor = game.createObject(o.getString("name"));
        created.add(actor);
        actor.setPosition(finite(o, "x", 0), finite(o, "y", 0));
        var t = actor.transform;
        t.rotation = finite(o, "rotation", 0);
        t.scale.set(finite(o, "sx", 1), finite(o, "sy", 1));
        t.visualOffset.set(finite(o, "ox", 0), finite(o, "oy", 0));
        actor.setActive(o.getBoolean("active", true));
        if (o.has("tags")) for (JsonValue tag : o.get("tags")) actor.addTag(tag.asString());
        int ci = 0;
        for (JsonValue c : o.require("components")) {
          ComponentCodec<?> codec = keys.get(c.getString("type"));
          JsonValue data = c.require("data");
          Component component = codec.read(data);
          if (component.getClass() != codec.type())
            throw new IllegalArgumentException("Codec returned wrong type");
          actor.addComponent(component);
          component.setEnabled(c.getBoolean("enabled", true));
          component.updateMode = Component.UpdateMode.valueOf(c.getString("updateMode", "WORLD"));
          if (component instanceof Transform3D transform) {
            refs.put(oi + ":" + ci, transform);
            if (data.has("parent")) parents.put(transform, data.getString("parent"));
          }
          ci++;
        }
        oi++;
      }
      for (var entry : parents.entrySet()) {
        Transform3D parent = refs.get(entry.getValue());
        if (parent == null) throw new IllegalArgumentException("Unknown parent reference");
        entry.getKey().setParent(parent);
      }
      return List.copyOf(created);
    } catch (RuntimeException | Error e) {
      for (GameObject actor : created) actor.destroy();
      throw e;
    }
  }

  public static SceneJson standard() {
    SceneJson schema = new SceneJson();
    schema.register(
        "transform3d",
        Transform3D.class,
        t -> {
          JsonValue d = object();
          number(d, "x", t.position.x);
          number(d, "y", t.position.y);
          number(d, "z", t.position.z);
          number(d, "qx", t.rotation.x);
          number(d, "qy", t.rotation.y);
          number(d, "qz", t.rotation.z);
          number(d, "qw", t.rotation.w);
          number(d, "sx", t.scale.x);
          number(d, "sy", t.scale.y);
          number(d, "sz", t.scale.z);
          return d;
        },
        d -> {
          Transform3D t = new Transform3D();
          t.position.set(finite(d, "x", 0), finite(d, "y", 0), finite(d, "z", 0));
          t.rotation.set(
              finite(d, "qx", 0), finite(d, "qy", 0), finite(d, "qz", 0), finite(d, "qw", 1));
          t.scale.set(finite(d, "sx", 1), finite(d, "sy", 1), finite(d, "sz", 1));
          return t;
        });
    schema.register(
        "camera2d",
        Camera2D.class,
        c -> {
          var d = object();
          number(d, "zoom", c.getZoom());
          number(d, "pixelsPerUnit", c.getPixelsPerUnit());
          return d;
        },
        d -> {
          var c = new Camera2D();
          c.setZoom(finite(d, "zoom", 1));
          c.setPixelsPerUnit(finite(d, "pixelsPerUnit", 1));
          return c;
        });
    schema.register(
        "shape2d",
        ShapeRenderer2D.class,
        s -> {
          if (s.material != null)
            throw new IllegalArgumentException(
                "ShapeMaterial2D requires an explicit SceneJson codec and cannot be serialized by"
                    + " the standard codec");
          s.validateDashConfiguration();
          var d = object();
          if (!Float.isFinite(s.cornerRadius) || s.cornerRadius < 0)
            throw new IllegalArgumentException("Invalid corner radius");
          text(d, "kind", s.kind.name());
          number(d, "width", s.width);
          number(d, "height", s.height);
          number(d, "radius", s.radius);
          number(d, "cornerRadius", s.cornerRadius);
          number(d, "stroke", s.strokeWidth);
          d.addChild("antiAlias", new JsonValue(s.antiAlias));
          text(d, "lineCap", Objects.requireNonNull(s.lineCap).name());
          text(d, "lineJoin", Objects.requireNonNull(s.lineJoin).name());
          text(d, "space", Objects.requireNonNull(s.space).name());
          text(d, "color", s.color.toString());
          d.addChild("layer", new JsonValue(s.layer));
          d.addChild("order", new JsonValue(s.order));
          JsonValue pts = array();
          for (Vector2 p : s.getPoints()) {
            JsonValue v = array();
            v.addChild(new JsonValue(p.x));
            v.addChild(new JsonValue(p.y));
            pts.addChild(v);
          }
          d.addChild("points", pts);
          if (s.dashPattern() != null) {
            JsonValue dashes = array();
            for (float length : s.dashPattern().lengths()) dashes.addChild(new JsonValue(length));
            d.addChild("dashes", dashes);
            number(d, "dashPhase", s.dashPhase());
          }
          return d;
        },
        d -> {
          List<Vector2> pts = new ArrayList<>();
          for (JsonValue p : d.require("points"))
            pts.add(new Vector2(p.getFloat(0), p.getFloat(1)));
          float stroke = finite(d, "stroke", 0);
          if (stroke < 0) throw new IllegalArgumentException("Invalid stroke");
          float cornerRadius = finite(d, "cornerRadius", 0);
          if (cornerRadius < 0) throw new IllegalArgumentException("Invalid corner radius");
          ShapeRenderer2D.Kind kind = ShapeRenderer2D.Kind.valueOf(d.getString("kind"));
          ShapeRenderer2D s =
              switch (kind) {
                case RECTANGLE ->
                    ShapeRenderer2D.rectangle(finite(d, "width", 0), finite(d, "height", 0));
                case CIRCLE -> ShapeRenderer2D.circle(finite(d, "radius", 0));
                case POLYGON -> ShapeRenderer2D.polygon(pts);
                case POLYLINE -> ShapeRenderer2D.polyline(pts, stroke);
              };
          s.strokeWidth = stroke;
          s.cornerRadius = cornerRadius;
          s.antiAlias = d.getBoolean("antiAlias", kind != ShapeRenderer2D.Kind.POLYGON);
          s.lineCap = ShapeRenderer2D.LineCap.valueOf(d.getString("lineCap", "ROUND"));
          s.lineJoin = ShapeRenderer2D.LineJoin.valueOf(d.getString("lineJoin", "ROUND"));
          s.space = ShapeRenderer2D.Space.valueOf(d.getString("space", "WORLD"));
          s.color.set(Color.valueOf(d.getString("color")));
          s.layer = d.getInt("layer", 0);
          s.order = d.getInt("order", 0);
          boolean hasDashes = d.has("dashes"), hasDashPhase = d.has("dashPhase");
          if (hasDashes != hasDashPhase)
            throw new IllegalArgumentException("Dash pattern and phase must be provided together");
          if (hasDashes) {
            if (kind != ShapeRenderer2D.Kind.POLYLINE)
              throw new IllegalArgumentException("Dashes are supported only for polylines");
            JsonValue dashValues = d.get("dashes");
            if (dashValues == null || !dashValues.isArray())
              throw new IllegalArgumentException("Dashes must be an array");
            float[] lengths = new float[dashValues.size];
            for (int i = 0; i < lengths.length; i++) {
              double value = dashValues.get(i).asDouble();
              if (!Double.isFinite(value) || Math.abs(value) > Float.MAX_VALUE)
                throw new IllegalArgumentException("Dash lengths must be finite floats");
              lengths[i] = (float) value;
            }
            double phase = finiteDouble(d, "dashPhase", 0);
            s.setDashes(new DashPattern2D(lengths), phase);
            s.validateDashConfiguration();
          }
          return s;
        });
    return schema;
  }
}
