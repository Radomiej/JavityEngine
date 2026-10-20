package org.javity.rooms.replication;

import java.util.*;

/** Immutable transport-neutral snapshot message. A full snapshot replaces client state. */
public record ReplicationEnvelope(long revision, boolean full, List<Change> changes) {
  public ReplicationEnvelope {
    if (revision < 0) throw new IllegalArgumentException("negative revision");
    changes = List.copyOf(Objects.requireNonNull(changes));
  }

  public sealed interface Change permits Spawn, Update, Despawn {
    NetworkId id();
  }

  public record Spawn(NetworkId id, String schemaId, int schemaVersion, byte[] state)
      implements Change {
    public Spawn {
      Objects.requireNonNull(id);
      if (schemaId == null || !schemaId.matches("[a-zA-Z0-9_.-]{1,96}") || schemaVersion < 1)
        throw new IllegalArgumentException("invalid schema");
      state = Objects.requireNonNull(state).clone();
    }

    @Override
    public byte[] state() {
      return state.clone();
    }
  }

  public record Update(NetworkId id, byte[] state) implements Change {
    public Update {
      Objects.requireNonNull(id);
      state = Objects.requireNonNull(state).clone();
    }

    @Override
    public byte[] state() {
      return state.clone();
    }
  }

  public record Despawn(NetworkId id) implements Change {
    public Despawn {
      Objects.requireNonNull(id);
    }
  }
}
