package org.javity.rooms.replication;

/**
 * Deterministic transport seam example: the envelope can be delivered directly or encoded by a host
 * transport.
 */
public final class InProcessReplicationExample implements AutoCloseable {
  public final ReplicationRegistry registry = new ReplicationRegistry();
  public final AuthoritativeReplicationServer server;
  public final ReplicationClient client;
  public final NetworkId player;

  public InProcessReplicationExample() {
    registry.register(Transform2DState.schema());
    server = new AuthoritativeReplicationServer(registry);
    client = new ReplicationClient(registry, 8);
    player =
        server.spawn("player-session", Transform2DState.SCHEMA_ID, 1, new Transform2DState(0, 0));
  }

  public void sendFull(long timeNanos) {
    client.apply(server.fullSnapshot(), timeNanos);
  }

  public void moveAuthoritatively(float x, float y, long timeNanos) {
    server.update(player, new Transform2DState(x, y));
    client.apply(server.snapshot(), timeNanos);
  }

  @Override
  public void close() {
    client.close();
    server.close();
  }
}
