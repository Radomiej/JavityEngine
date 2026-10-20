package org.javity.platform;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

/** Validates and canonicalizes one JSON value for a versioned platform save. */
public final class PlatformSavePayload {
  private PlatformSavePayload() {}

  public static String canonicalize(String json) {
    if (json == null) throw new IllegalArgumentException("save payload is required");
    JsonValue envelope;
    try {
      envelope = new JsonReader().parse("{\"payload\":" + json + "}");
    } catch (RuntimeException invalidJson) {
      throw new IllegalArgumentException("save payload must be one JSON value", invalidJson);
    }
    JsonValue payload = envelope == null ? null : envelope.child;
    if (envelope == null
        || !envelope.isObject()
        || payload == null
        || payload.next != null
        || !"payload".equals(payload.name)) {
      throw new IllegalArgumentException("save payload must be one JSON value");
    }
    if (payload.isString())
      return com.badlogic.gdx.utils.JsonWriter.OutputType.json.quoteValue(payload.asString());
    return payload.isNull()
        ? "null"
        : payload.toJson(com.badlogic.gdx.utils.JsonWriter.OutputType.json);
  }
}
