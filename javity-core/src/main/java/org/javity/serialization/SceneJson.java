package org.javity.serialization;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.utils.*;
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
    JsonValue root = object();
    root.addChild("version", new JsonValue(1L));
    JsonValue entries = array();
    root.addChild("objects", entries);
    IdentityHashMap<Component, String> refs = new IdentityHashMap<>();
    for (int i = 0; i < objects.size(); i++) {
      var cs = objects.get(i).getComponents();
      for (int j = 0; j < cs.size(); j++) refs.put(cs.get(j), i + ":" + j);
    }
    for (GameObject actor : objects) {
      if (actor.isDestroyed())
        throw new IllegalArgumentException("Cannot serialize destroyed object");
      JsonValue o = object();
      entries.addChild(o);
      text(o, "name", actor.name);
      o.addChild("active", new JsonValue(actor.isActive()));
      var t = actor.transform;
      number(o, "x", t.position.x);
      number(o, "y", t.position.y);
      number(o, "rotation", t.rotation);
      number(o, "sx", t.scale.x);
      number(o, "sy", t.scale.y);
      number(o, "ox", t.visualOffset.x);
      number(o, "oy", t.visualOffset.y);
      JsonValue tags = array();
      actor.getTags().stream().sorted().forEach(tag -> tags.addChild(new JsonValue(tag)));
      o.addChild("tags", tags);
      JsonValue components = array();
      o.addChild("components", components);
      for (Component c : actor.getComponents()) {
        ComponentCodec<?> codec = types.get(c.getClass());
        if (codec == null)
          throw new IllegalArgumentException("Register codec for " + c.getClass().getName());
        JsonValue item = object();
        text(item, "type", codec.key());
        item.addChild("enabled", new JsonValue(c.isEnabled()));
        text(item, "updateMode", c.updateMode.name());
        JsonValue data = encode(codec, c);
        item.addChild("data", data);
        if (c instanceof Transform3D t3 && t3.getParent() != null) {
          String parent = refs.get(t3.getParent());
          if (parent == null) throw new IllegalArgumentException("Parent outside prefab selection");
          text(data, "parent", parent);
        }
        components.addChild(item);
      }
    }
    return root.toJson(JsonWriter.OutputType.json);
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
                "ShapeMaterial2D requires an explicit SceneJson codec and cannot be serialized by the standard codec");
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
          return s;
        });
    return schema;
  }
}
