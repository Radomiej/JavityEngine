package org.javity.rooms.replication;

import java.util.Objects;

/** Untrusted client input. Authority validates sender ownership and sequence before dispatch. */
public record PlayerCommand(
    String sender, NetworkId target, String command, long sequence, byte[] payload) {
  public PlayerCommand {
    if (sender == null || sender.isBlank()) throw new IllegalArgumentException("sender required");
    Objects.requireNonNull(target);
    if (command == null || !command.matches("[a-zA-Z0-9_.-]{1,96}"))
      throw new IllegalArgumentException("invalid command");
    if (sequence < 0) throw new IllegalArgumentException("negative sequence");
    payload = Objects.requireNonNull(payload).clone();
  }

  @Override
  public byte[] payload() {
    return payload.clone();
  }
}
