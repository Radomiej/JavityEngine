package org.javity.rooms.replication;

/** Stable room identity; deliberately unrelated to GameObject.id. */
public record NetworkId(long value) implements Comparable<NetworkId> {
  public NetworkId {
    if (value <= 0) throw new IllegalArgumentException("network id must be positive");
  }

  @Override
  public int compareTo(NetworkId other) {
    return Long.compare(value, other.value);
  }
}
