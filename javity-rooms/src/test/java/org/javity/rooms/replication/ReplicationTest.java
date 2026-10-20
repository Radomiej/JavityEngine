package org.javity.rooms.replication;

import static org.junit.Assert.*;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import org.javity.core.Game;
import org.junit.Test;

public class ReplicationTest {
  private static final class MutableValue {
    int value;

    MutableValue(int value) {
      this.value = value;
    }
  }

  private static ReplicationRegistry registry() {
    ReplicationRegistry r = new ReplicationRegistry();
    r.register(Transform2DState.schema());
    return r;
  }

  @Test
  public void stableNetworkIdsDoNotDependOnSceneObjectIdsAndBindingsAreExplicit() {
    Game a = new Game();
    a.start();
    GameObjectBinding binding = new GameObjectBinding();
    try (InProcessReplicationExample ex = new InProcessReplicationExample()) {
      var first = a.createObject("unrelated");
      var second = a.createObject("player");
      assertEquals(1, first.id);
      assertEquals(2, second.id);
      binding.bind(ex.player, second);
      ex.sendFull(1);
      binding.apply2D(ex.client, ex.player, Transform2DState.SCHEMA_ID);
      assertEquals(0, second.transform.position.x, 0.0001f);
      ex.moveAuthoritatively(3, 4, 2);
      binding.apply2D(ex.client, ex.player, Transform2DState.SCHEMA_ID);
      assertEquals(3, second.transform.position.x, 0.0001f);
      assertSame(second, binding.get(ex.player));
    } finally {
      binding.close();
      a.close();
    }
  }

  @Test
  public void commandsRequireOwnershipSequenceAndRegisteredValidation() {
    var server = new AuthoritativeReplicationServer(registry());
    try {
      var id = server.spawn("alice", Transform2DState.SCHEMA_ID, 1, new Transform2DState(0, 0));
      List<Long> applied = new ArrayList<>();
      server.registerCommand(
          "move", p -> p.length == 1 && p[0] >= 0, (target, p) -> applied.add((long) p[0]));
      assertThrows(
          SecurityException.class,
          () -> server.accept(new PlayerCommand("mallory", id, "move", 1, new byte[] {1})));
      assertThrows(
          IllegalArgumentException.class,
          () -> server.accept(new PlayerCommand("alice", id, "move", 1, new byte[] {-1})));
      assertTrue(server.accept(new PlayerCommand("alice", id, "move", 1, new byte[] {2})));
      assertFalse(server.accept(new PlayerCommand("alice", id, "move", 1, new byte[] {3})));
      assertEquals(List.of(2L), applied);
    } finally {
      server.close();
    }
  }

  @Test
  public void appliesLifecycleAtomicallyRejectsMalformedAndStaleSnapshots() {
    var r = registry();
    var client = new ReplicationClient(r, 4);
    var id = new NetworkId(77);
    try {
      var initial =
          new ReplicationEnvelope(
              1,
              true,
              List.of(
                  new ReplicationEnvelope.Spawn(
                      id,
                      Transform2DState.SCHEMA_ID,
                      1,
                      ByteBuffer.allocate(8).putFloat(1).putFloat(2).array())));
      assertTrue(client.apply(initial, 10));
      var malformed =
          new ReplicationEnvelope(
              2,
              false,
              List.of(
                  new ReplicationEnvelope.Update(
                      id, ByteBuffer.allocate(8).putFloat(9).putFloat(9).array()),
                  new ReplicationEnvelope.Spawn(
                      new NetworkId(88), "missing.schema", 1, new byte[0])));
      assertThrows(IllegalArgumentException.class, () -> client.apply(malformed, 20));
      assertEquals(1, client.revision());
      assertEquals(new Transform2DState(1, 2), client.latest(id));
      assertEquals(1, client.historySize(id));
      assertFalse(client.apply(initial, 30));
      assertTrue(
          client.apply(
              new ReplicationEnvelope(2, false, List.of(new ReplicationEnvelope.Despawn(id))), 20));
      assertNull(client.latest(id));
    } finally {
      client.close();
    }
  }

  @Test
  public void requiresFullSnapshotInitiallyAndAfterReconnectOrDeltaGap() {
    var client = new ReplicationClient(registry(), 4);
    var id = new NetworkId(91);
    try {
      var spawnDelta =
          new ReplicationEnvelope(
              1,
              false,
              List.of(
                  new ReplicationEnvelope.Spawn(
                      id,
                      Transform2DState.SCHEMA_ID,
                      1,
                      ByteBuffer.allocate(8).putFloat(1).putFloat(2).array())));
      assertThrows(IllegalStateException.class, () -> client.apply(spawnDelta, 1));
      assertTrue(client.needsFullSnapshot());
      assertEquals(-1, client.revision());
      assertTrue(client.entities().isEmpty());
      var full =
          new ReplicationEnvelope(
              4,
              true,
              List.of(
                  new ReplicationEnvelope.Spawn(
                      id,
                      Transform2DState.SCHEMA_ID,
                      1,
                      ByteBuffer.allocate(8).putFloat(1).putFloat(2).array())));
      assertTrue(client.apply(full, 2));
      var gap =
          new ReplicationEnvelope(
              6,
              false,
              List.of(
                  new ReplicationEnvelope.Update(
                      id, ByteBuffer.allocate(8).putFloat(3).putFloat(4).array())));
      assertThrows(IllegalStateException.class, () -> client.apply(gap, 3));
      assertTrue(client.needsFullSnapshot());
      assertEquals(4, client.revision());
      assertEquals(new Transform2DState(1, 2), client.latest(id));
      assertEquals(1, client.historySize(id));
      var recovery =
          new ReplicationEnvelope(
              6,
              true,
              List.of(
                  new ReplicationEnvelope.Spawn(
                      id,
                      Transform2DState.SCHEMA_ID,
                      1,
                      ByteBuffer.allocate(8).putFloat(5).putFloat(6).array())));
      assertTrue(client.apply(recovery, 4));
      assertFalse(client.needsFullSnapshot());
      assertEquals(new Transform2DState(5, 6), client.latest(id));
      client.reconnect();
      assertThrows(IllegalStateException.class, () -> client.apply(gap, 5));
      assertTrue(client.needsFullSnapshot());
      assertTrue(client.entities().isEmpty());
    } finally {
      client.close();
    }
  }

  @Test
  public void historyIsBoundedInterpolatedAndResetOnReconnect() {
    var r = registry();
    var server = new AuthoritativeReplicationServer(r);
    var client = new ReplicationClient(r, 3);
    try {
      var id = server.spawn("p", Transform2DState.SCHEMA_ID, 1, new Transform2DState(0, 0));
      client.apply(server.fullSnapshot(), 1);
      for (int i = 1; i <= 5; i++) {
        server.update(id, new Transform2DState(i * 10, 0));
        client.apply(server.snapshot(), i + 1);
      }
      assertEquals(3, client.historySize(id));
      assertEquals(30, ((Transform2DState) client.interpolate(id, 4)).x(), 0.001);
      client.reconnect();
      assertEquals(-1, client.revision());
      assertTrue(client.entities().isEmpty());
      assertTrue(client.apply(server.fullSnapshot(), 20));
      assertEquals(new Transform2DState(50, 0), client.latest(id));
    } finally {
      client.close();
      server.close();
    }
  }

  @Test
  public void mutableCodecResultsAreDefensivelyCopiedAtClientBoundary() {
    var r = registry();
    r.register(
        new ReplicationSchema<>(
            "test.mutable",
            1,
            new StateCodec<MutableValue>() {
              public byte[] encode(MutableValue value) {
                return ByteBuffer.allocate(4).putInt(value.value).array();
              }

              public MutableValue decode(byte[] bytes) {
                if (bytes.length != 4) throw new IllegalArgumentException();
                return new MutableValue(ByteBuffer.wrap(bytes).getInt());
              }

              public MutableValue interpolate(MutableValue a, MutableValue b, double alpha) {
                return new MutableValue((int) Math.round(a.value + (b.value - a.value) * alpha));
              }
            }));
    var client = new ReplicationClient(r, 4);
    var id = new NetworkId(123);
    try {
      assertTrue(
          client.apply(
              new ReplicationEnvelope(
                  1,
                  true,
                  List.of(
                      new ReplicationEnvelope.Spawn(
                          id, "test.mutable", 1, ByteBuffer.allocate(4).putInt(2).array()))),
              10));
      assertTrue(
          client.apply(
              new ReplicationEnvelope(
                  2,
                  false,
                  List.of(
                      new ReplicationEnvelope.Update(
                          id, ByteBuffer.allocate(4).putInt(8).array()))),
              20));
      MutableValue latest = (MutableValue) client.latest(id);
      latest.value = 99;
      assertEquals(8, ((MutableValue) client.latest(id)).value);
      MutableValue interpolated = (MutableValue) client.interpolate(id, 15);
      assertEquals(5, interpolated.value);
      interpolated.value = 100;
      assertEquals(5, ((MutableValue) client.interpolate(id, 15)).value);
    } finally {
      client.close();
    }
  }

  @Test
  public void serverDespawnAndCloseAreTerminal() {
    var server = new AuthoritativeReplicationServer(registry());
    var id = server.spawn("p", Transform2DState.SCHEMA_ID, 1, new Transform2DState(0, 0));
    server.fullSnapshot();
    assertTrue(server.despawn(id));
    assertFalse(server.despawn(id));
    assertEquals(1, server.snapshot().changes().size());
    server.close();
    assertThrows(IllegalStateException.class, server::snapshot);
  }

  @Test
  public void roomWideFullBaselineAndPeerSpecificDespawnTombstonesAreSafe() {
    var schemas = registry();
    var server = new AuthoritativeReplicationServer(schemas);
    var oldPeer = new ReplicationClient(schemas, 4);
    var latePeer = new ReplicationClient(schemas, 4);
    try {
      var initial = server.fullSnapshot();
      assertTrue(oldPeer.apply(initial, 1));
      var id = server.spawn("alice", Transform2DState.SCHEMA_ID, 1, new Transform2DState(2, 3));
      var roomReset = server.fullSnapshot();
      assertTrue(oldPeer.apply(roomReset, 2));
      assertTrue(latePeer.apply(roomReset, 2));
      assertTrue(oldPeer.entities().contains(id));
      assertTrue(latePeer.entities().contains(id));
      assertTrue(server.despawn(id));
      var delta = server.snapshot();
      assertTrue(oldPeer.apply(delta, 3));
      assertTrue(latePeer.apply(delta, 3));
      assertTrue(oldPeer.entities().isEmpty());
      assertTrue(latePeer.entities().isEmpty());
      var neverPublished =
          server.spawn("alice", Transform2DState.SCHEMA_ID, 1, new Transform2DState(4, 5));
      assertTrue(server.despawn(neverPublished));
      assertTrue(server.snapshot().changes().isEmpty());
    } finally {
      oldPeer.close();
      latePeer.close();
      server.close();
    }
  }

  @Test
  public void commandSequencesArePerSessionPerCommandAndRemoveSessionClearsState() {
    var server = new AuthoritativeReplicationServer(registry());
    var a = server.spawn("alice", Transform2DState.SCHEMA_ID, 1, new Transform2DState(0, 0));
    var b = server.spawn("alice", Transform2DState.SCHEMA_ID, 1, new Transform2DState(0, 0));
    try {
      List<Long> applied = new ArrayList<>();
      server.registerCommand(
          "fire", p -> p.length == 0, (target, p) -> applied.add(target.value()));
      assertTrue(server.accept(new PlayerCommand("alice", a, "fire", 4, new byte[0])));
      assertFalse(server.accept(new PlayerCommand("alice", b, "fire", 4, new byte[0])));
      assertTrue(server.accept(new PlayerCommand("alice", b, "fire", 5, new byte[0])));
      assertEquals(2, server.removeSession("alice"));
      assertEquals(0, server.removeSession("alice"));
      var rejoined =
          server.spawn("alice", Transform2DState.SCHEMA_ID, 1, new Transform2DState(0, 0));
      assertTrue(server.accept(new PlayerCommand("alice", rejoined, "fire", 0, new byte[0])));
    } finally {
      server.close();
    }
  }

  @Test
  public void versionedWireCodecRoundTripsAndRejectsForgedOrMalformedInput() {
    var codec = new ReplicationWireCodec();
    var id = new NetworkId(21);
    var envelope =
        new ReplicationEnvelope(
            8,
            false,
            List.of(
                new ReplicationEnvelope.Spawn(id, Transform2DState.SCHEMA_ID, 1, new byte[] {1, 2}),
                new ReplicationEnvelope.Despawn(new NetworkId(22))));
    var decoded = codec.decodeSnapshot(codec.encodeSnapshot(envelope));
    assertEquals(8, decoded.revision());
    assertFalse(decoded.full());
    assertEquals(2, decoded.changes().size());
    assertArrayEquals(
        new byte[] {1, 2}, ((ReplicationEnvelope.Spawn) decoded.changes().get(0)).state());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            codec.encodeSnapshot(
                new ReplicationEnvelope(
                    9, true, List.of(new ReplicationEnvelope.Despawn(new NetworkId(22))))));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ReplicationEnvelope.Spawn(id, "bad\"schema", 1, new byte[0]));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ReplicationEnvelope.Spawn(id, "line\nbreak", 1, new byte[0]));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ReplicationEnvelope.Spawn(id, "x".repeat(97), 1, new byte[0]));
    assertThrows(
        IllegalArgumentException.class,
        () -> codec.decodeSnapshot("{\"v\":2,\"revision\":1,\"full\":true,\"changes\":[]}"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            codec.decodeSnapshot(
                "{\"v\":1,\"revision\":1,\"full\":true,\"changes\":[],\"sender\":\"mallory\"}"));
    assertThrows(
        IllegalArgumentException.class,
        () -> codec.decodeSnapshot("{\"v\":1,\"revision\":1,\"full\":true,\"changes\":[]}{}"));
    assertThrows(
        IllegalArgumentException.class,
        () -> codec.decodeSnapshot("{\"v\":1,\"revision\":1.5,\"full\":true,\"changes\":[]}"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            codec.decodeSnapshot(
                "{\"v\":1,\"revision\":1,\"full\":true,\"changes\":[{\"type\":\"spawn\",\"id\":1,\"schema\":\"test.x\",\"schemaVersion\":1,\"state\":\"!\"}]}"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            codec.decodeSnapshot(
                "{\"v\":1,\"revision\":1,\"full\":true,\"changes\":[{\"type\":\"spawn\",\"id\":1,\"schema\":\"bad\\\"schema\",\"schemaVersion\":1,\"state\":\"\"}]}"));
    String oversizedBase64 = "A".repeat(((ReplicationWireCodec.MAX_STATE_BYTES + 2) / 3) * 4 + 4);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            codec.decodeSnapshot(
                "{\"v\":1,\"revision\":1,\"full\":true,\"changes\":[{\"type\":\"spawn\",\"id\":1,\"schema\":\"test.x\",\"schemaVersion\":1,\"state\":\""
                    + oversizedBase64
                    + "\"}]}"));
    String command = codec.encodeCommand(id, "move", 2, new byte[] {7});
    PlayerCommand trusted = codec.decodeCommand("verified-alice", command);
    assertEquals("verified-alice", trusted.sender());
    assertArrayEquals(new byte[] {7}, trusted.payload());
    String forged = command.substring(0, command.length() - 1) + ",\"sender\":\"mallory\"}";
    assertThrows(
        IllegalArgumentException.class, () -> codec.decodeCommand("verified-alice", forged));
    assertThrows(IllegalArgumentException.class, () -> codec.decodeCommand("", command));
    String hugeCommand =
        "{\"v\":1,\"target\":21,\"command\":\"move\",\"sequence\":3,\"payload\":\""
            + "A".repeat(((ReplicationWireCodec.MAX_COMMAND_BYTES + 2) / 3) * 4 + 4)
            + "\"}";
    assertThrows(
        IllegalArgumentException.class, () -> codec.decodeCommand("verified-alice", hugeCommand));
  }

  @Test
  public void twoPeersExchangeEnvelopeAcrossLoopbackSocket() throws Exception {
    var schemas = registry();
    var authority = new AuthoritativeReplicationServer(schemas);
    var client = new ReplicationClient(schemas, 4);
    var codec = new ReplicationWireCodec();
    try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      listener.setSoTimeout(3000);
      var id =
          authority.spawn("peer-a", Transform2DState.SCHEMA_ID, 1, new Transform2DState(12.5f, -3));
      ReplicationEnvelope envelope = authority.fullSnapshot();
      try (Socket peerA = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
          Socket peerB = listener.accept()) {
        peerB.setSoTimeout(3000);
        byte[] wire =
            codec.encodeSnapshot(envelope).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        DataOutputStream out = new DataOutputStream(peerA.getOutputStream());
        out.writeInt(wire.length);
        out.write(wire);
        out.flush();
        DataInputStream in = new DataInputStream(peerB.getInputStream());
        int size = in.readInt();
        assertTrue(size > 0 && size <= ReplicationWireCodec.MAX_JSON_BYTES);
        byte[] received = in.readNBytes(size);
        assertEquals(size, received.length);
        assertTrue(
            client.apply(
                codec.decodeSnapshot(new String(received, java.nio.charset.StandardCharsets.UTF_8)),
                100));
      }
      assertEquals(id, client.entities().iterator().next());
      assertEquals(new Transform2DState(12.5f, -3), client.latest(id));
    } finally {
      client.close();
      authority.close();
    }
  }
}
