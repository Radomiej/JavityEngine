package org.javity.serialization;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import java.util.*;
import java.util.function.UnaryOperator;
import org.javity.core.Game;
import org.javity.core.GameObject;

/** Versioned prefab definitions, pure migrations, and deterministic serialized-field overrides. */
public final class PrefabCatalog {
  private final int currentVersion;
  private final Map<PrefabId, PrefabDefinition> definitions = new LinkedHashMap<>();
  private final Map<PrefabId, Map<Integer, UnaryOperator<String>>> migrations = new HashMap<>();

  public PrefabCatalog(int currentVersion) {
    if (currentVersion < 1) throw new IllegalArgumentException("Current version must be positive");
    this.currentVersion = currentVersion;
  }

  public PrefabCatalog register(PrefabDefinition definition) {
    Objects.requireNonNull(definition, "definition");
    if (definition.version() > currentVersion)
      throw new IllegalArgumentException(
          "Prefab version is newer than catalog: " + definition.id());
    if (definitions.putIfAbsent(definition.id(), definition) != null)
      throw new IllegalArgumentException("Duplicate prefab ID: " + definition.id().value());
    return this;
  }

  /** Registers the pure migration from {@code fromVersion} to {@code fromVersion + 1}. */
  public PrefabCatalog registerMigration(
      PrefabId id, int fromVersion, UnaryOperator<String> migration) {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(migration, "migration");
    if (fromVersion < 1 || fromVersion >= currentVersion)
      throw new IllegalArgumentException("Migration source version is outside catalog range");
    Map<Integer, UnaryOperator<String>> steps =
        migrations.computeIfAbsent(id, ignored -> new HashMap<>());
    if (steps.putIfAbsent(fromVersion, migration) != null)
      throw new IllegalArgumentException(
          "Duplicate migration for " + id.value() + " v" + fromVersion);
    return this;
  }

  public <T> T resolve(AssetResolver resolver, AssetId<T> id) {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(resolver, "resolver");
    T asset = resolver.resolve(id);
    if (asset == null) throw new IllegalArgumentException("Unresolved asset ID: " + id.value());
    return asset;
  }

  public List<GameObject> instantiate(
      Game game,
      PrefabId id,
      SceneJson schema,
      AssetResolver resolver,
      List<PrefabOverride> overrides) {
    Objects.requireNonNull(game, "game");
    Objects.requireNonNull(schema, "schema");
    Objects.requireNonNull(resolver, "resolver");
    Objects.requireNonNull(overrides, "overrides");
    PrefabDefinition definition = definitions.get(Objects.requireNonNull(id, "id"));
    if (definition == null) throw new IllegalArgumentException("Unknown prefab ID: " + id.value());
    for (AssetId<?> asset : definition.assets()) resolveUntyped(resolver, asset);
    String document = migrate(definition);
    JsonValue root = new JsonReader().parse(document);
    List<PrefabOverride> ordered = List.copyOf(overrides);
    Set<String> paths = new HashSet<>();
    for (PrefabOverride override : ordered) {
      Objects.requireNonNull(override, "override");
      if (!paths.add(override.path()))
        throw new IllegalArgumentException("Duplicate prefab override path: " + override.path());
      apply(root, override);
    }
    // SceneJson remains the sole component constructor and retains its rollback behavior.
    return new Prefab(root.toJson(JsonWriter.OutputType.json)).instantiate(game, schema);
  }

  private static Object resolveUntyped(AssetResolver resolver, AssetId<?> id) {
    return resolveCaptured(resolver, id);
  }

  private static <T> T resolveCaptured(AssetResolver resolver, AssetId<T> id) {
    T result = resolver.resolve(id);
    if (result == null) throw new IllegalArgumentException("Unresolved asset ID: " + id.value());
    return result;
  }

  private String migrate(PrefabDefinition definition) {
    int version = definition.version();
    String json = definition.sceneJson();
    Map<Integer, UnaryOperator<String>> steps = migrations.getOrDefault(definition.id(), Map.of());
    while (version < currentVersion) {
      UnaryOperator<String> migration = steps.get(version);
      if (migration == null)
        throw new IllegalArgumentException(
            "Unsupported prefab version " + version + " for " + definition.id().value());
      try {
        json = Objects.requireNonNull(migration.apply(json), "Migration returned null JSON");
      } catch (RuntimeException | Error failure) {
        throw new IllegalArgumentException(
            "Prefab migration failed for " + definition.id().value() + " v" + version, failure);
      }
      version++;
    }
    return json;
  }

  private static void apply(JsonValue root, PrefabOverride override) {
    String path = override.path();
    String[] segments = path.split("\\.");
    if (segments.length < 2 || !segments[0].startsWith("objects["))
      throw new IllegalArgumentException("Invalid prefab override path: " + path);
    JsonValue object = indexed(root, segments[0], "objects", path);
    int segment = 1;
    String field = segments[segment++];
    if (Set.of("name", "active", "x", "y", "rotation", "sx", "sy", "ox", "oy").contains(field)) {
      if (segment != segments.length)
        throw new IllegalArgumentException("Invalid prefab override path: " + path);
    } else if (field.startsWith("components[")) {
      JsonValue component = indexed(object, field, "components", path);
      if (segment >= segments.length
          || !segments[segment++].equals("data")
          || segment >= segments.length)
        throw new IllegalArgumentException(
            "Only serialized component data fields may be overridden: " + path);
      field = segments[segments.length - 1];
      JsonValue data = component.require("data");
      for (int i = 3; i < segments.length - 1; i++) data = data.require(segments[i]);
      replace(data, field, override, path);
      return;
    } else {
      throw new IllegalArgumentException("Unknown serialized prefab field in path: " + path);
    }
    replace(object, field, override, path);
  }

  private static JsonValue indexed(JsonValue parent, String token, String key, String fullPath) {
    String prefix = key + "[";
    if (!token.startsWith(prefix) || !token.endsWith("]"))
      throw new IllegalArgumentException("Invalid prefab override path: " + fullPath);
    int index;
    try {
      index = Integer.parseInt(token.substring(prefix.length(), token.length() - 1));
    } catch (NumberFormatException invalid) {
      throw new IllegalArgumentException("Invalid prefab override path: " + fullPath, invalid);
    }
    if (index < 0) throw new IllegalArgumentException("Invalid prefab override path: " + fullPath);
    JsonValue array = parent.require(key);
    if (!array.isArray() || index >= array.size)
      throw new IllegalArgumentException("Prefab override index out of range: " + fullPath);
    return array.get(index);
  }

  private static void replace(
      JsonValue parent, String field, PrefabOverride override, String path) {
    JsonValue existing = parent == null ? null : parent.get(field);
    if (existing == null || existing.isObject() || existing.isArray() || existing.isNull())
      throw new IllegalArgumentException(
          "Override path is not an existing serialized scalar: " + path);
    JsonValue replacement = new JsonReader().parse(override.jsonValue());
    if (!sameScalarType(existing, replacement))
      throw new IllegalArgumentException("Override type does not match serialized field: " + path);
    if (existing.isString()) {
      existing.set(replacement.asString());
    } else if (existing.isBoolean()) {
      existing.set(replacement.asBoolean());
    } else {
      setNumber(existing, replacement, override.jsonValue());
    }
  }

  private static void setNumber(JsonValue target, JsonValue replacement, String source) {
    String raw = replacement.toJson(JsonWriter.OutputType.json);
    if (raw.indexOf('.') < 0 && raw.indexOf('e') < 0 && raw.indexOf('E') < 0) {
      try {
        target.set(Long.parseLong(raw), raw);
        return;
      } catch (NumberFormatException outsideLongRange) {
        // Keep large finite values as doubles if they do not fit the integer representation.
      }
    }
    double value = replacement.asDouble();
    if (!Double.isFinite(value))
      throw new IllegalArgumentException("Override number must be finite: " + source);
    target.set(value, raw);
  }

  private static boolean sameScalarType(JsonValue left, JsonValue right) {
    return (left.isNumber() && right.isNumber())
        || (left.isString() && right.isString())
        || (left.isBoolean() && right.isBoolean());
  }
}
