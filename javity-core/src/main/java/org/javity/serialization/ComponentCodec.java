package org.javity.serialization;

import com.badlogic.gdx.utils.JsonValue;
import org.javity.core.Component;

/**
 * Explicit allowlisted type and data schema. Asset-backed codecs receive assets through constructor
 * closures.
 */
public interface ComponentCodec<T extends Component> {
  String key();

  Class<T> type();

  JsonValue write(T component);

  T read(JsonValue data);
}
