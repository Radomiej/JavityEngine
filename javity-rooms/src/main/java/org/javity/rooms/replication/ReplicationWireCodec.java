package org.javity.rooms.replication;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Base64;

/** Bounded, explicit JSON v1 codec for snapshot envelopes and untrusted player commands. */
public final class ReplicationWireCodec {
  public static final int VERSION = 1;
  public static final int MAX_JSON_BYTES = 24 * 1_048_576;
  public static final int MAX_CHANGES = ReplicationClient.MAX_CHANGES;
  public static final int MAX_STATE_BYTES = ReplicationClient.MAX_STATE_BYTES;
  public static final int MAX_ENVELOPE_STATE_BYTES = ReplicationClient.MAX_ENVELOPE_BYTES;
  public static final int MAX_COMMAND_BYTES = AuthoritativeReplicationServer.MAX_COMMAND_BYTES;

  public String encodeSnapshot(ReplicationEnvelope envelope) {
    Objects.requireNonNull(envelope);
    if (envelope.changes().size() > MAX_CHANGES)
      throw new IllegalArgumentException("too many changes");
    if (envelope.full()
        && envelope.changes().stream()
            .anyMatch(change -> !(change instanceof ReplicationEnvelope.Spawn)))
      throw new IllegalArgumentException("full snapshot must contain only spawns");
    StringBuilder out =
        new StringBuilder("{\"v\":1,\"revision\":")
            .append(envelope.revision())
            .append(",\"full\":")
            .append(envelope.full())
            .append(",\"changes\":[");
    long total = 0;
    boolean comma = false;
    for (ReplicationEnvelope.Change change : envelope.changes()) {
      if (comma) out.append(',');
      comma = true;
      if (change instanceof ReplicationEnvelope.Spawn s) {
        byte[] state = s.state();
        checkState(state);
        total += state.length;
        out.append("{\"type\":\"spawn\",\"id\":")
            .append(s.id().value())
            .append(",\"schema\":\"")
            .append(s.schemaId())
            .append("\",\"schemaVersion\":")
            .append(s.schemaVersion())
            .append(",\"state\":\"")
            .append(Base64.getEncoder().encodeToString(state))
            .append("\"}");
      } else if (change instanceof ReplicationEnvelope.Update u) {
        byte[] state = u.state();
        checkState(state);
        total += state.length;
        out.append("{\"type\":\"update\",\"id\":")
            .append(u.id().value())
            .append(",\"state\":\"")
            .append(Base64.getEncoder().encodeToString(state))
            .append("\"}");
      } else if (change instanceof ReplicationEnvelope.Despawn d)
        out.append("{\"type\":\"despawn\",\"id\":").append(d.id().value()).append('}');
      if (total > MAX_ENVELOPE_STATE_BYTES)
        throw new IllegalArgumentException("snapshot payload too large");
      if (out.length() > MAX_JSON_BYTES)
        throw new IllegalArgumentException("snapshot JSON too large");
    }
    out.append("]}");
    return checkedJson(out.toString());
  }

  public ReplicationEnvelope decodeSnapshot(String json) {
    JsonValue root = parse(json);
    fields(root, Set.of("v", "revision", "full", "changes"));
    version(root);
    long revision = integer(required(root, "revision"), "revision");
    if (revision < 0) throw bad("negative revision");
    JsonValue fullValue = required(root, "full");
    if (!fullValue.isBoolean()) throw bad("full must be boolean");
    JsonValue changesValue = required(root, "changes");
    if (!changesValue.isArray()) throw bad("changes must be an array");
    if (changesValue.size > MAX_CHANGES) throw bad("too many changes");
    List<ReplicationEnvelope.Change> changes = new ArrayList<>(changesValue.size);
    long total = 0;
    for (JsonValue item = changesValue.child; item != null; item = item.next) {
      if (!item.isObject()) throw bad("change must be an object");
      String type = string(required(item, "type"), "type");
      long idValue = integer(required(item, "id"), "id");
      NetworkId id = new NetworkId(idValue);
      switch (type) {
        case "spawn" -> {
          fields(item, Set.of("type", "id", "schema", "schemaVersion", "state"));
          String schema = string(required(item, "schema"), "schema");
          int schemaVersion = positiveInt(required(item, "schemaVersion"), "schemaVersion");
          byte[] state = base64(required(item, "state"), MAX_STATE_BYTES);
          total += state.length;
          changes.add(new ReplicationEnvelope.Spawn(id, schema, schemaVersion, state));
        }
        case "update" -> {
          fields(item, Set.of("type", "id", "state"));
          byte[] state = base64(required(item, "state"), MAX_STATE_BYTES);
          total += state.length;
          changes.add(new ReplicationEnvelope.Update(id, state));
        }
        case "despawn" -> {
          fields(item, Set.of("type", "id"));
          changes.add(new ReplicationEnvelope.Despawn(id));
        }
        default -> throw bad("unknown change type");
      }
      if (total > MAX_ENVELOPE_STATE_BYTES) throw bad("snapshot payload too large");
    }
    boolean full = fullValue.asBoolean();
    if (full && changes.stream().anyMatch(change -> !(change instanceof ReplicationEnvelope.Spawn)))
      throw bad("full snapshot must contain only spawns");
    return new ReplicationEnvelope(revision, full, changes);
  }

  /**
   * Encodes client input without a sender field; authenticated identity is supplied by the host.
   */
  public String encodeCommand(NetworkId target, String command, long sequence, byte[] payload) {
    Objects.requireNonNull(target);
    if (command == null || !command.matches("[a-zA-Z0-9_.-]{1,96}"))
      throw new IllegalArgumentException("invalid command");
    if (sequence < 0) throw new IllegalArgumentException("negative sequence");
    Objects.requireNonNull(payload);
    if (payload.length > MAX_COMMAND_BYTES) throw new IllegalArgumentException("command too large");
    return checkedJson(
        "{\"v\":1,\"target\":"
            + target.value()
            + ",\"command\":\""
            + command
            + "\",\"sequence\":"
            + sequence
            + ",\"payload\":\""
            + Base64.getEncoder().encodeToString(payload)
            + "\"}");
  }

  /**
   * Decodes untrusted input and injects the host-verified session identity; JSON cannot choose
   * sender.
   */
  public PlayerCommand decodeCommand(String authenticatedSessionId, String json) {
    if (authenticatedSessionId == null || authenticatedSessionId.isBlank())
      throw new IllegalArgumentException("authenticated session required");
    JsonValue root = parse(json);
    fields(root, Set.of("v", "target", "command", "sequence", "payload"));
    version(root);
    NetworkId target = new NetworkId(integer(required(root, "target"), "target"));
    String command = string(required(root, "command"), "command");
    long sequence = integer(required(root, "sequence"), "sequence");
    if (sequence < 0) throw bad("negative sequence");
    JsonValue payloadValue = required(root, "payload");
    byte[] payload = base64(payloadValue, MAX_COMMAND_BYTES);
    return new PlayerCommand(authenticatedSessionId, target, command, sequence, payload);
  }

  private JsonValue parse(String json) {
    if (json == null) throw bad("JSON required");
    checkedJson(json);
    checkNesting(json);
    try {
      JsonValue value = new JsonReader().parse(json);
      if (value == null || !value.isObject()) throw bad("root must be an object");
      return value;
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("malformed replication JSON", e);
    }
  }

  private static String checkedJson(String json) {
    if (json.length() > MAX_JSON_BYTES
        || json.getBytes(StandardCharsets.UTF_8).length > MAX_JSON_BYTES)
      throw new IllegalArgumentException("replication JSON too large");
    return json;
  }

  private static void checkNesting(String json) {
    int depth = 0;
    boolean quoted = false, escaped = false;
    for (int i = 0; i < json.length(); i++) {
      char c = json.charAt(i);
      if (quoted) {
        if (escaped) escaped = false;
        else if (c == '\\') escaped = true;
        else if (c == '"') quoted = false;
        continue;
      }
      if (c == '"') {
        quoted = true;
        continue;
      }
      if (c == '{' || c == '[') {
        if (++depth > 8) throw bad("JSON nesting too deep");
      } else if (c == '}' || c == ']') {
        if (--depth < 0) throw bad("malformed JSON nesting");
        if (depth == 0) {
          for (int j = i + 1; j < json.length(); j++)
            if (!Character.isWhitespace(json.charAt(j))) throw bad("trailing JSON content");
          return;
        }
      }
    }
  }

  private static void fields(JsonValue object, Set<String> allowed) {
    Set<String> seen = new HashSet<>();
    for (JsonValue field = object.child; field != null; field = field.next) {
      if (!allowed.contains(field.name)) throw bad("unknown field: " + field.name);
      if (!seen.add(field.name)) throw bad("duplicate field: " + field.name);
    }
  }

  private static JsonValue required(JsonValue object, String name) {
    JsonValue value = object.get(name);
    if (value == null) throw bad("missing field: " + name);
    return value;
  }

  private static void version(JsonValue root) {
    if (integer(required(root, "v"), "v") != VERSION) throw bad("unsupported wire version");
  }

  private static long integer(JsonValue value, String name) {
    if (!value.isNumber()) throw bad(name + " must be an integer");
    String text = value.asString();
    if (!text.matches("-?(0|[1-9][0-9]*)")) throw bad(name + " must be an integer");
    try {
      return Long.parseLong(text);
    } catch (NumberFormatException e) {
      throw bad(name + " is out of range");
    }
  }

  private static int positiveInt(JsonValue value, String name) {
    long n = integer(value, name);
    if (n < 1 || n > Integer.MAX_VALUE) throw bad(name + " is out of range");
    return (int) n;
  }

  private static String string(JsonValue value, String name) {
    if (!value.isString()) throw bad(name + " must be a string");
    return value.asString();
  }

  private static byte[] base64(JsonValue value, int maxBytes) {
    String text = string(value, "payload");
    int maxBase64 = ((maxBytes + 2) / 3) * 4;
    if (text.length() > maxBase64) throw bad("state payload too large");
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(text);
    } catch (IllegalArgumentException e) {
      throw bad("invalid base64 payload");
    }
    if (decoded.length > maxBytes) throw bad("state payload too large");
    return decoded;
  }

  private static void checkState(byte[] state) {
    if (state.length > MAX_STATE_BYTES)
      throw new IllegalArgumentException("state payload too large");
  }

  private static IllegalArgumentException bad(String message) {
    return new IllegalArgumentException(message);
  }
}
