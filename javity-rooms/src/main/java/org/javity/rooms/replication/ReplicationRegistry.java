package org.javity.rooms.replication;

import java.util.*;

/** Explicit schema registry shared by authority and clients. */
public final class ReplicationRegistry {
  private final Map<String, ReplicationSchema<?>> schemas = new HashMap<>();

  public synchronized <T> void register(ReplicationSchema<T> schema) {
    Objects.requireNonNull(schema);
    if (schemas.putIfAbsent(schema.id(), schema) != null)
      throw new IllegalArgumentException("duplicate schema: " + schema.id());
  }

  public synchronized ReplicationSchema<?> require(String id, int version) {
    ReplicationSchema<?> schema = schemas.get(id);
    if (schema == null || schema.version() != version)
      throw new IllegalArgumentException("unsupported schema " + id + " v" + version);
    return schema;
  }

  @SuppressWarnings("unchecked")
  public <T> byte[] encode(String id, int version, T state) {
    return ((ReplicationSchema<T>) require(id, version)).encode(state);
  }

  public Object decode(String id, int version, byte[] bytes) {
    return decodeUnchecked(require(id, version), bytes);
  }

  public Object copy(String id, int version, Object value) {
    return decode(id, version, encodeUnchecked(require(id, version), value));
  }

  @SuppressWarnings("unchecked")
  private static <T> byte[] encodeUnchecked(ReplicationSchema<T> s, Object value) {
    return s.encode((T) value);
  }

  private static <T> Object decodeUnchecked(ReplicationSchema<T> schema, byte[] bytes) {
    return schema.decode(bytes);
  }

  public Object interpolate(String id, int version, Object a, Object b, double alpha) {
    return interpolateUnchecked(require(id, version), a, b, alpha);
  }

  @SuppressWarnings("unchecked")
  private static <T> Object interpolateUnchecked(
      ReplicationSchema<T> s, Object a, Object b, double alpha) {
    return s.codec().interpolate((T) a, (T) b, alpha);
  }
}
