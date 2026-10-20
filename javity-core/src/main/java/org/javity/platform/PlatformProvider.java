package org.javity.platform;

import java.util.List;

/** Provider SPI. Implementations may finish on any thread; PlatformServices marshals delivery. */
public interface PlatformProvider extends AutoCloseable {
  record Capabilities(boolean player, boolean profile, boolean saves, boolean rooms) {}

  record Player(String id, String displayName) {}

  record Profile(String id, String displayName, String avatarUrl) {}

  record Save(String slot, String payload, long revision) {}

  /**
   * Room metadata. Creation may return a one-time invite code; do not log or expose it to
   * telemetry.
   */
  final class Room {
    private final String id, gameId, version, ownerId, status, mode, inviteCode;
    private final int maxPlayers;
    private final boolean privateRoom, ranked;

    public Room(
        String id,
        String gameId,
        String version,
        String ownerId,
        String status,
        String mode,
        boolean privateRoom,
        boolean ranked,
        int maxPlayers,
        String inviteCode) {
      this.id = id;
      this.gameId = gameId;
      this.version = version;
      this.ownerId = ownerId;
      this.status = status;
      this.mode = mode;
      this.privateRoom = privateRoom;
      this.ranked = ranked;
      this.maxPlayers = maxPlayers;
      this.inviteCode = inviteCode;
    }

    public String id() {
      return id;
    }

    public String gameId() {
      return gameId;
    }

    public String version() {
      return version;
    }

    public String ownerId() {
      return ownerId;
    }

    public String status() {
      return status;
    }

    public String mode() {
      return mode;
    }

    public boolean isPrivate() {
      return privateRoom;
    }

    public boolean ranked() {
      return ranked;
    }

    public int maxPlayers() {
      return maxPlayers;
    }

    /**
     * One-time private invite code when returned by room creation. Do not log or expose to
     * telemetry.
     */
    public String inviteCode() {
      return inviteCode;
    }
  }

  final class RoomTicket {
    private final String endpoint, externalRoomId, ticket;
    private final int expiresIn;

    public RoomTicket(String endpoint, String externalRoomId, String ticket, int expiresIn) {
      this.endpoint = endpoint;
      this.externalRoomId = externalRoomId;
      this.ticket = ticket;
      this.expiresIn = expiresIn;
    }

    public String endpoint() {
      return endpoint;
    }

    public String externalRoomId() {
      return externalRoomId;
    }

    /** Short-lived join capability; treat as a secret and never log it. */
    public String ticket() {
      return ticket;
    }

    public int expiresIn() {
      return expiresIn;
    }
  }

  record RoomList(List<Room> items) {
    public RoomList {
      items = List.copyOf(items);
    }
  }

  record RoomRequest(String mode, boolean isPrivate, boolean ranked, int maxPlayers) {}

  record RoomCloseResult(boolean ok) {}

  @FunctionalInterface
  interface Callback<T> {
    void complete(PlatformResult<T> result);
  }

  interface Operation {
    void cancel();
  }

  Capabilities capabilities();

  Operation player(Callback<Player> callback);

  Operation profile(Callback<Profile> callback);

  Operation getSave(String slot, Callback<Save> callback);

  Operation putSave(String slot, String payload, long expectedRevision, Callback<Save> callback);

  Operation listRooms(Callback<RoomList> callback);

  Operation createRoom(RoomRequest request, Callback<Room> callback);

  Operation joinRoom(String roomId, String inviteCode, Callback<RoomTicket> callback);

  Operation closeRoom(String roomId, Callback<RoomCloseResult> callback);

  @Override
  void close();
}
