package org.javity.rooms;

import java.util.*;
import java.util.function.LongSupplier;
import org.javity.core.Game;
import org.javity.core.GameObject;

/** Headless fixed-step room simulation. All methods that mutate/read live state belong to its creator thread. */
public final class LocalRoomSimulation implements AutoCloseable {
  public static final int MAX_PLAYERS = 8;
  public static final double SPEED = 4.0;
  public static final double WORLD_LIMIT = 50.0;
  public static final long INPUT_TIMEOUT_NANOS = 250_000_000L;
  public static final double STEP_SECONDS = 1.0 / 60.0;
  public record PlayerState(String sessionId, double x, double z, long lastSequence) {}
  public record Snapshot(String roomId, long tick, List<PlayerState> players) {}
  private final String roomId;
  private final LongSupplier nanoClock;
  private final Thread owner = Thread.currentThread();
  private final Game game = new Game();
  private final LinkedHashMap<String, GameObject> objects = new LinkedHashMap<>();
  private long tick;
  private boolean closed;

  public LocalRoomSimulation(String roomId, LongSupplier nanoClock) {
    if (roomId == null || roomId.isBlank()) throw new IllegalArgumentException("roomId required");
    this.roomId = roomId;
    this.nanoClock = Objects.requireNonNull(nanoClock);
    game.start();
  }
  public LocalRoomSimulation(String roomId) { this(roomId, System::nanoTime); }
  public String roomId() { return roomId; }
  public Game game() { assertOwner(); return game; }
  public long tick() { assertOwner(); return tick; }

  public void join(String sessionId) {
    assertOpen();
    if (sessionId == null || sessionId.isBlank()) throw new IllegalArgumentException("sessionId required");
    if (objects.containsKey(sessionId)) throw new IllegalArgumentException("session already joined");
    if (objects.size() >= MAX_PLAYERS) throw new IllegalStateException("room full");
    GameObject object = game.createObject("room-player");
    object.addComponent(new RoomPlayer());
    objects.put(sessionId, object);
    game.initializePendingComponents();
  }
  /** Returns false for an older or repeated sequence; invalid vectors throw before changing state. */
  public boolean acceptInput(String sessionId, long sequence, double x, double z) {
    assertOpen();
    if (sequence < 0 || sequence > 9_007_199_254_740_991L || !Double.isFinite(x) || !Double.isFinite(z)
        || Math.abs(x) > 1 || Math.abs(z) > 1) throw new IllegalArgumentException("invalid input");
    GameObject object = objects.get(sessionId);
    if (object == null) throw new IllegalArgumentException("unknown session");
    RoomPlayer player = object.requireComponent(RoomPlayer.class);
    if (sequence <= player.lastSequence) return false;
    double length = Math.hypot(x, z);
    player.inputX = length > 1 ? x / length : x;
    player.inputZ = length > 1 ? z / length : z;
    player.lastSequence = sequence;
    player.lastInputNanos = nanoClock.getAsLong();
    return true;
  }
  public boolean leave(String sessionId) {
    assertOpen();
    GameObject object = objects.remove(sessionId);
    if (object == null) return false;
    object.destroy();
    return true;
  }
  public void step() {
    assertOpen();
    long now = nanoClock.getAsLong();
    for (GameObject object : objects.values()) {
      RoomPlayer player = object.requireComponent(RoomPlayer.class);
      double x = player.lastSequence < 0 || now - player.lastInputNanos > INPUT_TIMEOUT_NANOS ? 0 : player.inputX;
      double z = player.lastSequence < 0 || now - player.lastInputNanos > INPUT_TIMEOUT_NANOS ? 0 : player.inputZ;
      object.transform.position.x = (float) clamp(object.transform.position.x + x * SPEED * STEP_SECONDS);
      object.transform.position.y = (float) clamp(object.transform.position.y + z * SPEED * STEP_SECONDS);
    }
    game.step((float) STEP_SECONDS);
    tick++;
  }
  public Snapshot snapshot() {
    assertOpen();
    List<PlayerState> states = new ArrayList<>();
    objects.forEach((session, object) -> {
      RoomPlayer p = object.requireComponent(RoomPlayer.class);
      states.add(new PlayerState(session, object.transform.position.x, object.transform.position.y, p.lastSequence));
    });
    return new Snapshot(roomId, tick, List.copyOf(states));
  }
  private static double clamp(double x) { return Math.max(-WORLD_LIMIT, Math.min(WORLD_LIMIT, x)); }
  private void assertOwner() {
    if (Thread.currentThread() != owner) throw new IllegalStateException("room accessed outside owner thread");
  }
  private void assertOpen() { assertOwner(); if (closed) throw new IllegalStateException("room closed"); }
  @Override public void close() {
    assertOwner();
    if (closed) return;
    closed = true;
    game.close();
  }
}
