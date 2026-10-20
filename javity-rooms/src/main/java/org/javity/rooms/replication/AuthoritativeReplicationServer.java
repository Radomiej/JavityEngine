package org.javity.rooms.replication;

import java.util.*;
import java.util.function.Predicate;

/**
 * In-process authoritative state table and validated command boundary, owned by one server thread.
 */
public final class AuthoritativeReplicationServer implements AutoCloseable {
  public static final int MAX_COMMAND_BYTES = 4096;
  public static final int MAX_STATE_BYTES = 1_048_576;
  public static final int MAX_ENTITIES = 16_384;
  public static final int MAX_PENDING_CHANGES = 32_768;
  public static final long MAX_TOTAL_STATE_BYTES = 16L * 1_048_576;

  private static final class Entity {
    final String schema;
    final int version;
    final String owner;
    byte[] state;

    Entity(String s, int v, String o, byte[] b) {
      schema = s;
      version = v;
      owner = o;
      state = b;
    }
  }

  @FunctionalInterface
  public interface CommandHandler {
    void apply(NetworkId target, byte[] validatedPayload);
  }

  private record CommandDefinition(Predicate<byte[]> validator, CommandHandler handler) {}

  private final ReplicationRegistry registry;
  private final Thread owner = Thread.currentThread();
  private final TreeMap<NetworkId, Entity> entities = new TreeMap<>();
  private final Map<String, CommandDefinition> handlers = new HashMap<>();
  private final Map<String, Long> sequences = new HashMap<>();
  private final Set<NetworkId> dirty = new TreeSet<>();
  private final Set<NetworkId> announced = new HashSet<>();
  private final Set<NetworkId> despawned = new TreeSet<>();
  private long nextId = 1, revision, totalStateBytes;
  private boolean closed;

  public AuthoritativeReplicationServer(ReplicationRegistry registry) {
    this.registry = Objects.requireNonNull(registry);
  }

  public NetworkId spawn(String ownerSession, String schema, int version, Object initialState) {
    open();
    if (ownerSession == null || ownerSession.isBlank())
      throw new IllegalArgumentException("owner required");
    byte[] encoded = checkedState(registry.encode(schema, version, initialState));
    if (entities.size() >= MAX_ENTITIES)
      throw new IllegalStateException("room entity limit reached");
    if (totalStateBytes + encoded.length > MAX_TOTAL_STATE_BYTES)
      throw new IllegalStateException("room state budget exceeded");
    if (dirty.size() + despawned.size() >= MAX_PENDING_CHANGES)
      throw new IllegalStateException("pending change limit reached");
    if (nextId <= 0 || nextId == Long.MAX_VALUE)
      throw new IllegalStateException("network id space exhausted");
    NetworkId id = new NetworkId(nextId++);
    entities.put(id, new Entity(schema, version, ownerSession, encoded));
    totalStateBytes += encoded.length;
    dirty.add(id);
    return id;
  }

  public void update(NetworkId id, Object state) {
    open();
    Entity e = require(id);
    byte[] encoded = checkedState(registry.encode(e.schema, e.version, state));
    if (!dirty.contains(id) && dirty.size() + despawned.size() >= MAX_PENDING_CHANGES)
      throw new IllegalStateException("pending change limit reached");
    if (totalStateBytes - e.state.length + encoded.length > MAX_TOTAL_STATE_BYTES)
      throw new IllegalStateException("room state budget exceeded");
    totalStateBytes = totalStateBytes - e.state.length + encoded.length;
    e.state = encoded;
    dirty.add(id);
  }

  public boolean despawn(NetworkId id) {
    open();
    Entity existing = entities.get(id);
    if (existing == null) return false;
    boolean needsTombstone = announced.contains(id);
    if (needsTombstone && dirty.size() + despawned.size() >= MAX_PENDING_CHANGES)
      throw new IllegalStateException("pending change limit reached");
    entities.remove(id);
    totalStateBytes -= existing.state.length;
    dirty.remove(id);
    if (announced.remove(id)) despawned.add(id);
    return true;
  }

  /** Ends a connection's ownership: remove its entities and per-command sequence memory. */
  public int removeSession(String sessionId) {
    open();
    if (sessionId == null || sessionId.isBlank())
      throw new IllegalArgumentException("session required");
    List<NetworkId> owned = new ArrayList<>();
    entities.forEach(
        (id, e) -> {
          if (e.owner.equals(sessionId)) owned.add(id);
        });
    long newTombstones = owned.stream().filter(announced::contains).count();
    if (dirty.size() + despawned.size() + newTombstones > MAX_PENDING_CHANGES)
      throw new IllegalStateException("pending change limit reached");
    for (NetworkId id : owned) despawn(id);
    String prefix = sessionId + "\u0000";
    sequences.keySet().removeIf(key -> key.startsWith(prefix));
    return owned.size();
  }

  public void registerCommand(String name, Predicate<byte[]> validator, CommandHandler handler) {
    open();
    if (name == null || !name.matches("[a-zA-Z0-9_.-]{1,96}"))
      throw new IllegalArgumentException("invalid command");
    if (handlers.putIfAbsent(
            name,
            new CommandDefinition(
                Objects.requireNonNull(validator), Objects.requireNonNull(handler)))
        != null) throw new IllegalArgumentException("duplicate command");
  }

  /**
   * Returns false for a repeated/older command. Invalid or unauthorized input throws before
   * dispatch.
   */
  public boolean accept(PlayerCommand command) {
    open();
    Objects.requireNonNull(command);
    byte[] payload = command.payload();
    if (payload.length > MAX_COMMAND_BYTES) throw new IllegalArgumentException("command too large");
    Entity e = require(command.target());
    if (!e.owner.equals(command.sender()))
      throw new SecurityException("sender does not own target");
    CommandDefinition definition = handlers.get(command.command());
    if (definition == null) throw new IllegalArgumentException("unknown command");
    String key = command.sender() + "\u0000" + command.command();
    long previous = sequences.getOrDefault(key, -1L);
    if (command.sequence() <= previous) return false;
    if (!definition.validator().test(payload.clone()))
      throw new IllegalArgumentException("invalid command payload");
    definition.handler().apply(command.target(), payload.clone());
    sequences.put(key, command.sequence());
    return true;
  }

  public ReplicationEnvelope fullSnapshot() {
    open();
    if (revision == Long.MAX_VALUE) throw new IllegalStateException("revision exhausted");
    revision++;
    List<ReplicationEnvelope.Change> out = new ArrayList<>();
    entities.forEach(
        (id, e) -> out.add(new ReplicationEnvelope.Spawn(id, e.schema, e.version, e.state)));
    announced.clear();
    announced.addAll(entities.keySet());
    dirty.clear();
    despawned.clear();
    return new ReplicationEnvelope(revision, true, out);
  }

  /**
   * Emits accumulated lifecycle/state changes as one ordered envelope; empty calls still advance
   * revision.
   */
  public ReplicationEnvelope snapshot() {
    open();
    if (revision == Long.MAX_VALUE) throw new IllegalStateException("revision exhausted");
    revision++;
    List<ReplicationEnvelope.Change> out = new ArrayList<>();
    for (NetworkId id : despawned) out.add(new ReplicationEnvelope.Despawn(id));
    for (NetworkId id : dirty) {
      Entity e = entities.get(id);
      if (e != null) {
        if (announced.add(id))
          out.add(new ReplicationEnvelope.Spawn(id, e.schema, e.version, e.state));
        else out.add(new ReplicationEnvelope.Update(id, e.state));
      }
    }
    despawned.clear();
    dirty.clear();
    return new ReplicationEnvelope(revision, false, out);
  }

  private Entity require(NetworkId id) {
    Entity e = entities.get(id);
    if (e == null) throw new IllegalArgumentException("unknown network id");
    return e;
  }

  private static byte[] checkedState(byte[] bytes) {
    if (bytes.length > MAX_STATE_BYTES) throw new IllegalArgumentException("state too large");
    return bytes;
  }

  private void owner() {
    if (Thread.currentThread() != owner)
      throw new IllegalStateException("replication server accessed outside owner thread");
  }

  private void open() {
    owner();
    if (closed) throw new IllegalStateException("server closed");
  }

  @Override
  public void close() {
    owner();
    if (closed) return;
    closed = true;
    entities.clear();
    totalStateBytes = 0;
    handlers.clear();
    sequences.clear();
    dirty.clear();
    despawned.clear();
    announced.clear();
  }
}
