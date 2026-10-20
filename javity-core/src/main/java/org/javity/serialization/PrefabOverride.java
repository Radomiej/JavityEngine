package org.javity.serialization;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import java.util.Objects;

/** Immutable scalar replacement at a validated serialized prefab path. */
public record PrefabOverride(String path, String jsonValue) {
  public PrefabOverride {
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(jsonValue, "jsonValue");
    if (path.isBlank()) throw new IllegalArgumentException("Override path is blank");
    JsonValue value = new JsonReader().parse(jsonValue);
    if (value.isObject() || value.isArray() || value.isNull())
      throw new IllegalArgumentException("Override value must be a JSON scalar");
    jsonValue = value.toJson(JsonWriter.OutputType.json);
  }

  public static PrefabOverride number(String path, double value) {
    if (!Double.isFinite(value))
      throw new IllegalArgumentException("Override number must be finite");
    return new PrefabOverride(path, Double.toString(value));
  }

  public static PrefabOverride bool(String path, boolean value) {
    return new PrefabOverride(path, Boolean.toString(value));
  }

  public static PrefabOverride text(String path, String value) {
    JsonValue encoded = new JsonValue(Objects.requireNonNull(value));
    return new PrefabOverride(path, encoded.toJson(JsonWriter.OutputType.json));
  }
}
