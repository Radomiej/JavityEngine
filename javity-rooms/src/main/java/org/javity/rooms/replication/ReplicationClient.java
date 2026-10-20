package org.javity.rooms.replication;

import java.util.*;

/** Client-side atomic snapshot receiver with bounded decoded interpolation history. */
public final class ReplicationClient implements AutoCloseable {
  public static final int MAX_ENTITIES = 16_384;
  public static final int MAX_CHANGES = 32_768;
  public static final int MAX_STATE_BYTES = 1_048_576;
  public static final int MAX_ENVELOPE_BYTES = 16 * 1_048_576;

  private record Sample(long time, Object state) {}

  private static final class Track {
    final String schema;
    final int version;
    final ArrayDeque<Sample> samples = new ArrayDeque<>();

    Track(String s, int v) {
      schema = s;
      version = v;
    }
  }

  private final ReplicationRegistry registry;
  private final int historyLimit;
  private final Thread owner = Thread.currentThread();
  private final Map<NetworkId, Track> tracks = new HashMap<>();
  private long revision = -1;
  private boolean needsFull = true;
  private boolean closed;

  public ReplicationClient(ReplicationRegistry registry, int historyLimit) {
    this.registry = Objects.requireNonNull(registry);
    if (historyLimit < 2 || historyLimit > 128)
      throw new IllegalArgumentException("historyLimit must be 2..128");
    this.historyLimit = historyLimit;
  }

  /**
   * Rejects stale/out-of-order envelopes with false. Malformed envelopes throw without partial
   * mutation.
   */
  public boolean apply(ReplicationEnvelope envelope, long sampleTimeNanos) {
    open();
    Objects.requireNonNull(envelope);
    if (envelope.revision() <= revision) return false;
    if (sampleTimeNanos < 0) throw new IllegalArgumentException("negative sample time");
    if (envelope.changes().size() > MAX_CHANGES)
      throw new IllegalArgumentException("too many changes");
    if (envelope.full()
        && envelope.changes().stream()
            .anyMatch(change -> !(change instanceof ReplicationEnvelope.Spawn)))
      throw new IllegalArgumentException("full snapshot must contain only spawns");
    long totalBytes = 0;
    for (ReplicationEnvelope.Change change : envelope.changes()) {
      if (change instanceof ReplicationEnvelope.Spawn s) totalBytes += s.state().length;
      else if (change instanceof ReplicationEnvelope.Update u) totalBytes += u.state().length;
      if (totalBytes > MAX_ENVELOPE_BYTES) throw new IllegalArgumentException("envelope too large");
    }
    if (needsFull && !envelope.full()) throw new IllegalStateException("full snapshot required");
    if (!envelope.full() && envelope.revision() != revision + 1) {
      needsFull = true;
      throw new IllegalStateException("delta revision gap; full snapshot required");
    }
    Map<NetworkId, Track> next = envelope.full() ? new HashMap<>() : copyTracks(tracks);
    Set<NetworkId> touched = new HashSet<>();
    for (ReplicationEnvelope.Change c : envelope.changes()) {
      if (!touched.add(c.id())) throw new IllegalArgumentException("duplicate entity change");
      if (c instanceof ReplicationEnvelope.Spawn s) {
        byte[] bytes = s.state();
        checkBytes(bytes);
        Object decoded = registry.decode(s.schemaId(), s.schemaVersion(), bytes);
        Track old = next.get(s.id());
        if (old != null && (!old.schema.equals(s.schemaId()) || old.version != s.schemaVersion()))
          throw new IllegalArgumentException("schema changed for live id");
        Track t = old == null ? new Track(s.schemaId(), s.schemaVersion()) : old;
        append(t, sampleTimeNanos, registry.copy(s.schemaId(), s.schemaVersion(), decoded));
        next.put(s.id(), t);
      } else if (c instanceof ReplicationEnvelope.Update u) {
        byte[] bytes = u.state();
        checkBytes(bytes);
        Track t = next.get(u.id());
        if (t == null) throw new IllegalArgumentException("update for unknown id");
        Object decoded = registry.decode(t.schema, t.version, bytes);
        append(t, sampleTimeNanos, registry.copy(t.schema, t.version, decoded));
      } else if (c instanceof ReplicationEnvelope.Despawn d) {
        next.remove(d.id()); // Per-peer baselines differ, so unknown tombstones are harmless.
      }
      if (next.size() > MAX_ENTITIES) throw new IllegalArgumentException("too many entities");
    }
    tracks.clear();
    tracks.putAll(next);
    revision = envelope.revision();
    needsFull = false;
    return true;
  }

  private static void checkBytes(byte[] b) {
    if (b.length > MAX_STATE_BYTES) throw new IllegalArgumentException("state too large");
  }

  private void append(Track t, long time, Object state) {
    if (!t.samples.isEmpty() && time <= t.samples.getLast().time)
      throw new IllegalArgumentException("sample times must increase");
    t.samples.addLast(new Sample(time, state));
    while (t.samples.size() > historyLimit) t.samples.removeFirst();
  }

  private static Map<NetworkId, Track> copyTracks(Map<NetworkId, Track> source) {
    Map<NetworkId, Track> copy = new HashMap<>();
    source.forEach(
        (id, t) -> {
          Track n = new Track(t.schema, t.version);
          n.samples.addAll(t.samples);
          copy.put(id, n);
        });
    return copy;
  }

  public long revision() {
    check();
    return revision;
  }

  public boolean needsFullSnapshot() {
    open();
    return needsFull;
  }

  public Set<NetworkId> entities() {
    check();
    return Set.copyOf(tracks.keySet());
  }

  public Object latest(NetworkId id) {
    check();
    Track t = tracks.get(id);
    return t == null ? null : registry.copy(t.schema, t.version, t.samples.getLast().state);
  }

  public Object interpolate(NetworkId id, long renderTimeNanos) {
    check();
    Track t = tracks.get(id);
    if (t == null) return null;
    Sample a = t.samples.getFirst();
    if (renderTimeNanos <= a.time) return registry.copy(t.schema, t.version, a.state);
    Sample prev = a;
    for (Sample b : t.samples) {
      if (renderTimeNanos <= b.time) {
        double alpha = (double) (renderTimeNanos - prev.time) / (b.time - prev.time);
        Object left = registry.copy(t.schema, t.version, prev.state),
            right = registry.copy(t.schema, t.version, b.state);
        Object value =
            registry.interpolate(t.schema, t.version, left, right, Math.max(0, Math.min(1, alpha)));
        return registry.copy(t.schema, t.version, value);
      }
      prev = b;
    }
    return registry.copy(t.schema, t.version, prev.state);
  }

  public int historySize(NetworkId id) {
    check();
    Track t = tracks.get(id);
    return t == null ? 0 : t.samples.size();
  }

  /** Starts a new connection epoch. A fresh full snapshot is required before state is visible. */
  public void reconnect() {
    open();
    tracks.clear();
    revision = -1;
    needsFull = true;
  }

  private void check() {
    open();
  }

  private void owner() {
    if (Thread.currentThread() != owner)
      throw new IllegalStateException("replication client accessed outside owner thread");
  }

  private void open() {
    owner();
    if (closed) throw new IllegalStateException("client closed");
  }

  @Override
  public void close() {
    owner();
    if (closed) return;
    closed = true;
    tracks.clear();
  }
}
