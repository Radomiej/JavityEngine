package org.javity.wasm;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.javity.platform.PlatformProvider;
import org.javity.platform.PlatformResult;

/** ORBIT implementation of the portable platform-services SPI. */
public final class OrbitPlatformProvider implements PlatformProvider {
  private boolean closed;

  @Override
  public Capabilities capabilities() {
    boolean enabled = !closed && OrbitBrowser.available();
    return new Capabilities(enabled, enabled, enabled, enabled);
  }

  @Override
  public Operation player(Callback<Player> cb) {
    return call(
        "player.get",
        "{}",
        json -> {
          cb.complete(PlatformResult.success(parsePlayer(json)));
        },
        cb);
  }

  @Override
  public Operation profile(Callback<Profile> cb) {
    return call(
        "player.get",
        "{}",
        json -> {
          cb.complete(PlatformResult.success(parseProfile(json)));
        },
        cb);
  }

  @Override
  public Operation getSave(String slot, Callback<Save> cb) {
    if (!validSlot(slot)) return invalid(cb, "slot is required");
    return call(
        "saves.get",
        "{\"slot\":" + quote(slot) + "}",
        json -> {
          cb.complete(PlatformResult.success(parseSave(slot, json)));
        },
        cb);
  }

  @Override
  public Operation putSave(String slot, String payload, long expectedRevision, Callback<Save> cb) {
    if (!validSlot(slot) || payload == null || expectedRevision < 0)
      return invalid(cb, "invalid save request");
    try {
      payload = org.javity.platform.PlatformSavePayload.canonicalize(payload);
    } catch (IllegalArgumentException invalid) {
      return invalid(cb, invalid.getMessage());
    }
    return call(
        "saves.put",
        "{\"slot\":"
            + quote(slot)
            + ",\"data\":"
            + payload
            + ",\"expectedRevision\":"
            + expectedRevision
            + "}",
        json -> {
          cb.complete(PlatformResult.success(parseSave(slot, json)));
        },
        cb);
  }

  @Override
  public Operation listRooms(Callback<RoomList> cb) {
    return call(
        "multiplayer.list",
        "{}",
        json -> {
          cb.complete(PlatformResult.success(parseRoomList(json)));
        },
        cb);
  }

  @Override
  public Operation createRoom(RoomRequest req, Callback<Room> cb) {
    if (req == null || !validMode(req.mode()) || req.maxPlayers() < 2 || req.maxPlayers() > 16) {
      cb.complete(
          PlatformResult.failure(
              PlatformResult.ErrorCode.INVALID_ARGUMENT, "invalid room request"));
      return () -> {};
    }
    String params =
        "{\"mode\":"
            + quote(req.mode())
            + ",\"private\":"
            + req.isPrivate()
            + ",\"ranked\":"
            + req.ranked()
            + ",\"maxPlayers\":"
            + req.maxPlayers()
            + "}";
    return call(
        "multiplayer.create",
        params,
        json -> cb.complete(PlatformResult.success(parseRoom(json))),
        cb);
  }

  @Override
  public Operation joinRoom(String roomId, String inviteCode, Callback<RoomTicket> cb) {
    if (!validSlot(roomId) || (inviteCode != null && !validSlot(inviteCode)))
      return invalid(cb, "invalid room join request");
    String params =
        "{\"roomId\":"
            + quote(roomId)
            + (inviteCode == null ? "" : ",\"inviteCode\":" + quote(inviteCode))
            + "}";
    return call(
        "multiplayer.join",
        params,
        json -> cb.complete(PlatformResult.success(parseRoomTicket(json))),
        cb);
  }

  @Override
  public Operation closeRoom(String roomId, Callback<RoomCloseResult> cb) {
    if (!validSlot(roomId)) return invalid(cb, "roomId is required");
    return call(
        "multiplayer.close",
        "{\"roomId\":" + quote(roomId) + "}",
        json -> cb.complete(PlatformResult.success(parseRoomCloseResult(json))),
        cb);
  }

  @Override
  public void close() {
    closed = true;
    OrbitBrowser.dispose();
  }

  private interface Success {
    void accept(String json);
  }

  private <T> Operation invalid(Callback<T> cb, String message) {
    cb.complete(PlatformResult.failure(PlatformResult.ErrorCode.INVALID_ARGUMENT, message));
    return () -> {};
  }

  private <T> Operation call(String method, String params, Success success, Callback<T> callback) {
    if (closed || !OrbitBrowser.available()) {
      callback.complete(
          PlatformResult.failure(PlatformResult.ErrorCode.UNAVAILABLE, "ORBIT is unavailable"));
      return () -> {};
    }
    final boolean[] cancelled = {false};
    OrbitBrowser.request(
        method,
        params,
        json -> {
          if (!closed && !cancelled[0])
            try {
              success.accept(json);
            } catch (RuntimeException e) {
              callback.complete(
                  PlatformResult.failure(PlatformResult.ErrorCode.REQUEST_FAILED, e.getMessage()));
            }
        },
        error -> {
          if (!closed && !cancelled[0]) callback.complete(cast(error(error)));
        });
    return () -> cancelled[0] = true;
  }

  private PlatformResult<?> error(String json) {
    try {
      JsonValue v = parse(json);
      String code = string(v, "code");
      String message = stringOr(v, "message", "ORBIT request failed");
      PlatformResult.ErrorCode mapped =
          switch (code) {
            case "CONFLICT", "SAVE_CONFLICT" -> PlatformResult.ErrorCode.CONFLICT;
            case "NOT_FOUND" -> PlatformResult.ErrorCode.NOT_FOUND;
            case "NOT_AUTHENTICATED", "ACCOUNT_REQUIRED" ->
                PlatformResult.ErrorCode.NOT_AUTHENTICATED;
            case "INVALID_REQUEST" -> PlatformResult.ErrorCode.INVALID_ARGUMENT;
            case "UNSUPPORTED" -> PlatformResult.ErrorCode.UNSUPPORTED;
            case "OFFLINE" -> PlatformResult.ErrorCode.OFFLINE;
            case "CLOSED" -> PlatformResult.ErrorCode.CLOSED;
            case "UNAVAILABLE", "NOT_IN_LAUNCHER", "TIMEOUT" ->
                PlatformResult.ErrorCode.UNAVAILABLE;
            default -> PlatformResult.ErrorCode.REQUEST_FAILED;
          };
      return PlatformResult.failure(mapped, message);
    } catch (RuntimeException ex) {
      return PlatformResult.failure(
          PlatformResult.ErrorCode.REQUEST_FAILED, "ORBIT request failed");
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static <T> PlatformResult<T> cast(PlatformResult<?> r) {
    return (PlatformResult) r;
  }

  static JsonValue parse(String json) {
    return new JsonReader().parse(json);
  }

  static Player parsePlayer(String json) {
    JsonValue v = parse(json);
    return new Player(requiredString(v, "id"), requiredString(v, "nickname"));
  }

  static Profile parseProfile(String json) {
    JsonValue v = parse(json);
    return new Profile(requiredString(v, "id"), requiredString(v, "nickname"), "");
  }

  static Save parseSave(String slot, String json) {
    JsonValue v = parse(json);
    if (v == null || v.isNull()) return null;
    if (!v.isObject() || !v.has("data"))
      throw new IllegalArgumentException("save response is missing data");
    return new Save(
        slot,
        v.get("data").toJson(com.badlogic.gdx.utils.JsonWriter.OutputType.json),
        requiredRevision(v));
  }

  static RoomList parseRoomList(String json) {
    JsonValue root = parse(json),
        values = root == null || !root.isObject() ? null : root.get("items");
    if (values == null || !values.isArray())
      throw new IllegalArgumentException("room page is missing items");
    java.util.ArrayList<Room> rooms = new java.util.ArrayList<>();
    for (JsonValue v = values.child; v != null; v = v.next) rooms.add(room(v));
    return new RoomList(rooms);
  }

  static Room parseRoom(String json) {
    return room(parse(json));
  }

  static RoomTicket parseRoomTicket(String json) {
    JsonValue v = parse(json);
    String endpoint = requiredString(v, "endpoint"),
        external = requiredString(v, "externalRoomId"),
        ticket = requiredString(v, "ticket");
    int expires = requiredInt(v, "expiresIn", 1, Integer.MAX_VALUE);
    return new RoomTicket(endpoint, external, ticket, expires);
  }

  static RoomCloseResult parseRoomCloseResult(String json) {
    if ("true".equals(json)) return new RoomCloseResult(true);
    JsonValue v = parse(json);
    JsonValue ok = v == null ? null : v.get("ok");
    if (ok == null || !ok.isBoolean())
      throw new IllegalArgumentException("room close response is missing ok");
    return new RoomCloseResult(ok.asBoolean());
  }

  private static String string(JsonValue v, String key) {
    return v.getString(key, "");
  }

  private static String stringOr(JsonValue v, String key, String fallback) {
    return v == null ? fallback : v.getString(key, fallback);
  }

  private static Room room(JsonValue v) {
    String invite = null;
    if (v.has("inviteCode")) {
      JsonValue code = v.get("inviteCode");
      if (code == null || !code.isString() || code.asString().isEmpty())
        throw new IllegalArgumentException("invalid inviteCode");
      invite = code.asString();
    }
    JsonValue privacy = v.get("private"), ranked = v.get("ranked");
    if (privacy == null || !privacy.isBoolean() || ranked == null || !ranked.isBoolean())
      throw new IllegalArgumentException("room response is missing flags");
    return new Room(
        requiredString(v, "id"),
        requiredString(v, "gameId"),
        requiredString(v, "version"),
        requiredString(v, "ownerId"),
        requiredString(v, "status"),
        requiredString(v, "mode"),
        privacy.asBoolean(),
        ranked.asBoolean(),
        requiredInt(v, "maxPlayers", 2, 16),
        invite);
  }

  private static String requiredString(JsonValue v, String key) {
    JsonValue value = v == null ? null : v.get(key);
    if (value == null || !value.isString() || value.asString().isBlank())
      throw new IllegalArgumentException("missing required field: " + key);
    return value.asString();
  }

  private static int requiredInt(JsonValue v, String key, int min, int max) {
    JsonValue value = v == null ? null : v.get(key);
    if (value == null || !value.isNumber())
      throw new IllegalArgumentException("missing required field: " + key);
    double number = value.asDouble();
    if (!Double.isFinite(number) || number != Math.floor(number) || number < min || number > max)
      throw new IllegalArgumentException("invalid field: " + key);
    return (int) number;
  }

  private static long requiredRevision(JsonValue v) {
    JsonValue value = v.get("revision");
    if (value == null || !value.isNumber())
      throw new IllegalArgumentException("missing required field: revision");
    double number = value.asDouble();
    if (!Double.isFinite(number)
        || number != Math.floor(number)
        || number < 1
        || number > 9007199254740991d) throw new IllegalArgumentException("invalid revision");
    return (long) number;
  }

  private static boolean validSlot(String s) {
    return s != null && !s.isBlank();
  }

  private static boolean validMode(String s) {
    return "lobby".equals(s) || "tic-tac-toe".equals(s);
  }

  private static String quote(String s) {
    if (s == null) throw new IllegalArgumentException("value is required");
    StringBuilder out = new StringBuilder(s.length() + 2).append('"');
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\b' -> out.append("\\b");
        case '\f' -> out.append("\\f");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (c < 0x20) {
            out.append("\\u00");
            String hex = Integer.toHexString(c);
            if (hex.length() < 2) out.append('0');
            out.append(hex);
          } else out.append(c);
        }
      }
    }
    return out.append('"').toString();
  }
}
