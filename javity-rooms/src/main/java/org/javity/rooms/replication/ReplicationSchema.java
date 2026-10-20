package org.javity.rooms.replication;

import java.util.Objects;

/** Explicit schema registration. Schema IDs and versions are wire contract, never class names. */
public record ReplicationSchema<T>(String id, int version, StateCodec<T> codec) {
  public ReplicationSchema {
    if (id == null || !id.matches("[a-zA-Z0-9_.-]{1,96}"))
      throw new IllegalArgumentException("invalid schema id");
    if (version < 1) throw new IllegalArgumentException("schema version must be positive");
    Objects.requireNonNull(codec);
  }

  public byte[] encode(T state) {
    return Objects.requireNonNull(codec.encode(Objects.requireNonNull(state))).clone();
  }

  public T decode(byte[] bytes) {
    return Objects.requireNonNull(codec.decode(bytes.clone()));
  }
}
