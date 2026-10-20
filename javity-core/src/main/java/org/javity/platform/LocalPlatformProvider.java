package org.javity.platform;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.javity.platform.PlatformResult.ErrorCode;

/** Explicit offline provider for demos and tests. State exists only for this provider instance. */
public final class LocalPlatformProvider implements PlatformProvider {
  private final Player player;
  private final Profile profile;
  private final Map<String, Save> saves = new LinkedHashMap<>();
  private final Map<String, Room> rooms = new LinkedHashMap<>();
  private long nextRoomId;
  private boolean closed;

  public LocalPlatformProvider() {
    this("local-player", "Local Player");
  }

  public LocalPlatformProvider(String playerId, String displayName) {
    if (playerId == null || playerId.isBlank() || displayName == null || displayName.isBlank())
      throw new IllegalArgumentException("player identity is required");
    player = new Player(playerId, displayName);
    profile = new Profile(playerId, displayName, "");
  }

  @Override
  public Capabilities capabilities() {
    return new Capabilities(true, true, true, true);
  }

  @Override
  public Operation player(Callback<Player> cb) {
    return done(cb, player);
  }

  @Override
  public Operation profile(Callback<Profile> cb) {
    return done(cb, profile);
  }

  @Override
  public Operation getSave(String slot, Callback<Save> cb) {
    if (closed) return fail(cb, ErrorCode.CLOSED, "provider is closed");
    if (!validSlot(slot)) return fail(cb, ErrorCode.INVALID_ARGUMENT, "slot must be non-empty");
    Save value = saves.get(slot);
    return done(cb, value);
  }

  @Override
  public Operation putSave(String slot, String payload, long expectedRevision, Callback<Save> cb) {
    if (closed) return fail(cb, ErrorCode.CLOSED, "provider is closed");
    if (!validSlot(slot) || payload == null || expectedRevision < 0)
      return fail(cb, ErrorCode.INVALID_ARGUMENT, "invalid save request");
    Save current = saves.get(slot);
    long actual = current == null ? 0 : current.revision();
    if (actual != expectedRevision)
      return fail(
          cb,
          ErrorCode.CONFLICT,
          "save revision changed; expected " + expectedRevision + " but was " + actual);
    Save updated = new Save(slot, payload, actual + 1);
    saves.put(slot, updated);
    return done(cb, updated);
  }

  @Override
  public Operation listRooms(Callback<RoomList> cb) {
    if (closed) return fail(cb, ErrorCode.CLOSED, "provider is closed");
    return done(cb, new RoomList(new ArrayList<>(rooms.values())));
  }

  @Override
  public Operation createRoom(RoomRequest req, Callback<Room> cb) {
    if (closed) return fail(cb, ErrorCode.CLOSED, "provider is closed");
    if (req == null || !validMode(req.mode()) || req.maxPlayers() < 2 || req.maxPlayers() > 16)
      return fail(cb, ErrorCode.INVALID_ARGUMENT, "invalid room request");
    String id = "local-room-" + (++nextRoomId);
    Room room =
        new Room(
            id,
            "local-game",
            "local",
            player.id(),
            "ready",
            req.mode(),
            req.isPrivate(),
            req.ranked(),
            req.maxPlayers(),
            null);
    rooms.put(id, room);
    return done(cb, room);
  }

  @Override
  public Operation joinRoom(String roomId, String inviteCode, Callback<RoomTicket> cb) {
    if (closed) return fail(cb, ErrorCode.CLOSED, "provider is closed");
    if (!validSlot(roomId) || (inviteCode != null && !validSlot(inviteCode)))
      return fail(cb, ErrorCode.INVALID_ARGUMENT, "invalid room join request");
    if (!rooms.containsKey(roomId)) return fail(cb, ErrorCode.NOT_FOUND, "room does not exist");
    return fail(cb, ErrorCode.UNSUPPORTED, "local rooms do not provide a network join ticket");
  }

  @Override
  public Operation closeRoom(String roomId, Callback<RoomCloseResult> cb) {
    if (closed) return fail(cb, ErrorCode.CLOSED, "provider is closed");
    if (!validSlot(roomId)) return fail(cb, ErrorCode.INVALID_ARGUMENT, "roomId is required");
    if (rooms.remove(roomId) == null) return fail(cb, ErrorCode.NOT_FOUND, "room does not exist");
    return done(cb, new RoomCloseResult(true));
  }

  @Override
  public void close() {
    closed = true;
    saves.clear();
    rooms.clear();
  }

  private boolean validSlot(String slot) {
    return slot != null && !slot.isBlank();
  }

  private boolean validMode(String mode) {
    return "lobby".equals(mode) || "tic-tac-toe".equals(mode);
  }

  private <T> Operation done(Callback<T> cb, T value) {
    return complete(cb, PlatformResult.success(value));
  }

  private <T> Operation fail(Callback<T> cb, ErrorCode code, String message) {
    return complete(cb, PlatformResult.failure(code, message));
  }

  private <T> Operation complete(Callback<T> cb, PlatformResult<T> result) {
    if (closed) result = PlatformResult.failure(ErrorCode.CLOSED, "provider is closed");
    final PlatformResult<T> outcome = result;
    cb.complete(outcome);
    return () -> {};
  }
}
