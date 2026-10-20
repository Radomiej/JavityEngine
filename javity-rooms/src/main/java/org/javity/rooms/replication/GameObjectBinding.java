package org.javity.rooms.replication;

import java.util.*;
import org.javity.core.GameObject;

/** Explicit app adapter joining network IDs to caller-created GameObjects. */
public final class GameObjectBinding implements AutoCloseable {
  private final Map<NetworkId, GameObject> objects = new HashMap<>();
  private boolean closed;

  public void bind(NetworkId id, GameObject object) {
    open();
    if (objects.putIfAbsent(Objects.requireNonNull(id), Objects.requireNonNull(object)) != null)
      throw new IllegalArgumentException("network id already bound");
  }

  public GameObject get(NetworkId id) {
    open();
    return objects.get(id);
  }

  public boolean unbind(NetworkId id) {
    open();
    return objects.remove(id) != null;
  }

  /**
   * Drops mappings absent from the latest committed client snapshot; GameObjects remain
   * caller-owned.
   */
  public void prune(ReplicationClient client) {
    open();
    Objects.requireNonNull(client);
    Set<NetworkId> live = client.entities();
    objects.keySet().removeIf(id -> !live.contains(id));
  }

  /** Applies a typed state only when caller explicitly supplies the matching transform schema. */
  public void apply2D(ReplicationClient client, NetworkId id, String schemaId) {
    open();
    GameObject o = objects.get(id);
    if (o == null) return;
    Object value = client.latest(id);
    if (value instanceof Transform2DState s && schemaId.equals(Transform2DState.SCHEMA_ID))
      o.setPosition(s.x(), s.y());
  }

  private void open() {
    if (closed) throw new IllegalStateException("binding closed");
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    objects.clear();
  }
}
