package org.javity.rooms;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.LockSupport;

/** JSONL v1 process boundary used by the local Colyseus gateway. */
public final class LocalRoomWorker {
  static final int MAX_LINE_BYTES = 8192, INPUT_QUEUE_CAPACITY = 64, OUTPUT_QUEUE_CAPACITY = 64;
  private static final String STOP = new String("__STOP__");
  static final int MAX_COMMANDS_PER_TICK = 64;
  LocalRoomWorker() {}
  public static void main(String[] args) {
    if (args.length != 1 || args[0].isBlank()) { System.err.println("usage: LocalRoomWorker <roomId>"); System.exit(2); }
    int result = new LocalRoomWorker().run(args[0], System.in, System.out);
    if (result != 0) System.exit(result);
  }
  private int run(String roomId, InputStream input, PrintStream output) {
    ArrayBlockingQueue<byte[]> inbound = new ArrayBlockingQueue<>(INPUT_QUEUE_CAPACITY);
    BoundedOutput outbound = new BoundedOutput(OUTPUT_QUEUE_CAPACITY);
    AtomicBoolean eof = new AtomicBoolean();
    AtomicReference<String> framingFailure = new AtomicReference<>();
    AtomicReference<Throwable> writerFailure = new AtomicReference<>();
    Thread writer = new Thread(() -> {
      try {
        while (true) { String line = outbound.take(); if (line == STOP) return; output.println(line); output.flush();
          if (output.checkError()) throw new IOException("stdout write failed"); }
      } catch (Throwable failure) { writerFailure.set(failure); }
    }, "local-room-stdout");
    writer.setDaemon(true); writer.start();
    Thread reader = new Thread(() -> readFrames(input, inbound, eof, framingFailure), "local-room-stdin");
    reader.setDaemon(true); reader.start();
    LocalRoomSimulation room = null;
    boolean orderly = true;
    TickInputBudget inputBudget = new TickInputBudget(MAX_COMMANDS_PER_TICK);
    try {
      room = new LocalRoomSimulation(roomId);
      if (!emit(outbound, message("ready", roomId))) return 1;
      long nextStep = System.nanoTime() + 16_666_667L;
      while (true) {
        if (writerFailure.get() != null || framingFailure.get() != null) { orderly = false; break; }
        byte[] frame;
        while (inputBudget.remaining() > 0 && (frame = inbound.poll()) != null) {
          inputBudget.consume();
          if (!handle(frame, roomId, room, outbound)) { orderly = false; return 1; }
          if (shutdownRequested) { shutdownRequested = false; return 0; }
        }
        if (framingFailure.get() != null) { orderly = false; break; }
        if (eof.get() && inbound.isEmpty()) break;
        long now = System.nanoTime();
        if (now >= nextStep) {
          room.step(); inputBudget.reset(); nextStep += 16_666_667L;
          if (room.tick() % 3 == 0 && !emit(outbound, snapshot(room))) { orderly = false; break; }
        } else {
          LockSupport.parkNanos(Math.min(1_000_000L, nextStep - now));
        }
      }
      return orderly ? 0 : 1;
    } catch (Throwable failure) {
      System.err.println("local room worker failed: " + failure.getMessage()); return 1;
    } finally {
      if (room != null) try { room.close(); } catch (Throwable failure) { orderly = false; }
      outbound.offerStop();
      try { writer.join(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
      // Both auxiliaries are daemon threads; closing input unblocks the reader where supported.
      try { input.close(); } catch (IOException ignored) { }
    }
  }
  private boolean shutdownRequested;
  boolean handle(byte[] bytes, String roomId, LocalRoomSimulation room, OutputSink out) {
    String requestId = null;
    try {
      String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
      JsonValue json = new JsonReader().parse(text);
      if (json == null || !json.isObject()) throw new ProtocolException("invalid_frame", "Expected JSON object");
      requestId = string(json, "requestId");
      if (number(json, "v") != 1) throw new ProtocolException("version", "Unsupported protocol version");
      if (!roomId.equals(string(json, "roomId"))) throw new ProtocolException("room_mismatch", "roomId mismatch");
      String type = string(json, "type");
      switch (type) {
        case "join" -> { fields(json, "v", "roomId", "type", "requestId", "sessionId"); room.join(string(json,"sessionId")); ack(out, roomId, requestId); }
        case "leave" -> { fields(json, "v", "roomId", "type", "requestId", "sessionId");
          if (!room.leave(string(json,"sessionId"))) throw new ProtocolException("unknown_session", "sessionId is not joined"); ack(out, roomId, requestId); }
        case "input" -> { fields(json, "v", "roomId", "type", "requestId", "sessionId", "sequence", "x", "z");
          long seq = safeInteger(json, "sequence"); double x = finite(json,"x"), z = finite(json,"z");
          if (!room.acceptInput(string(json,"sessionId"), seq, x, z)) return true; }
        case "shutdown" -> { fields(json, "v", "roomId", "type", "requestId"); ack(out,roomId,requestId); shutdownRequested = true; }
        default -> throw new ProtocolException("unknown_command", "Unknown command type");
      }
    } catch (OutputQueueOverflow e) { System.err.println("local room worker stopped: stdout queue full"); return false; }
    catch (ProtocolException e) { return emit(out, error(roomId, e.code, e.getMessage(), requestId)); }
    catch (RuntimeException | CharacterCodingException e) {
      return emit(out, error(roomId, "invalid_command", "Malformed or invalid command", requestId));
    }
    return true;
  }
  private static void readFrames(InputStream in, ArrayBlockingQueue<byte[]> q, AtomicBoolean eof, AtomicReference<String> failure) {
    try {
      ByteArrayOutputStream line = new ByteArrayOutputStream(); int b;
      while ((b = in.read()) != -1) {
        if (b == '\n') { byte[] bytes = line.toByteArray(); if (!q.offer(bytes)) { failure.set("input queue full"); return; } line.reset(); }
        else { if (line.size() == MAX_LINE_BYTES) { failure.set("line exceeds 8192 bytes"); return; } line.write(b); }
      }
      if (line.size() > 0 && !q.offer(line.toByteArray())) failure.set("input queue full");
    } catch (IOException e) { failure.set("stdin read failed"); }
    finally { eof.set(true); }
  }
  private static void fields(JsonValue value, String... allowed) {
    Set<String> keys = Set.of(allowed); for (JsonValue child = value.child; child != null; child = child.next)
      if (!keys.contains(child.name)) throw new ProtocolException("extra_field", "Unknown field: " + child.name);
    for (String key : allowed) if (value.get(key) == null) throw new ProtocolException("missing_field", "Missing field: " + key);
  }
  private static String string(JsonValue value, String key) {
    JsonValue v = value.get(key); if (v == null || !v.isString()) throw new ProtocolException("invalid_field", key + " must be a string");
    String s = v.asString(); if (s.isBlank() || s.length() > 128) throw new ProtocolException("invalid_field", key + " is invalid"); return s;
  }
  private static double finite(JsonValue value, String key) {
    JsonValue v=value.get(key); if (v==null || !v.isNumber()) throw new ProtocolException("invalid_field", key + " must be numeric");
    double d=v.asDouble(); if (!Double.isFinite(d) || Math.abs(d)>1) throw new ProtocolException("invalid_field", key + " outside [-1,1]"); return d;
  }
  private static long safeInteger(JsonValue value, String key) {
    JsonValue v=value.get(key); if(v==null || !v.isNumber()) throw new ProtocolException("invalid_field", key + " must be integer");
    double d=v.asDouble(); if(!Double.isFinite(d)||d<0||d>9_007_199_254_740_991d||d!=Math.rint(d)) throw new ProtocolException("invalid_field", key + " outside safe integer range"); return (long)d;
  }
  private static long number(JsonValue value,String key) { return safeInteger(value,key); }
  private static void ack(OutputSink out,String room,String request) { if(!emit(out, message("ack",room,"requestId",request))) throw new OutputQueueOverflow(); }
  private static boolean emit(OutputSink out,String json) { return out.offer(json); }
  interface OutputSink { boolean offer(String line); String take() throws InterruptedException; boolean offerStop(); }
  static final class BoundedOutput implements OutputSink {
    private final ArrayBlockingQueue<String> lines;
    BoundedOutput(int capacity) { lines = new ArrayBlockingQueue<>(capacity); }
    public boolean offer(String line) { return lines.offer(line); }
    public String take() throws InterruptedException { return lines.take(); }
    public boolean offerStop() { return lines.offer(STOP); }
  }
  static final class TickInputBudget {
    private final int limit; private int remaining;
    TickInputBudget(int limit) { if (limit < 1) throw new IllegalArgumentException("limit"); this.limit=limit; this.remaining=limit; }
    int remaining() { return remaining; }
    void consume() { if (remaining == 0) throw new IllegalStateException("tick input budget exhausted"); remaining--; }
    void reset() { remaining=limit; }
  }
  private static final class OutputQueueOverflow extends RuntimeException {}
  private static String quote(String value) { StringBuilder b=new StringBuilder("\""); for (int i=0;i<value.length();i++) { char c=value.charAt(i); switch(c) { case '\\' -> b.append("\\\\"); case '\"' -> b.append("\\\""); case '\n' -> b.append("\\n"); case '\r' -> b.append("\\r"); case '\t' -> b.append("\\t"); default -> { if (c < 0x20) b.append(String.format("\\u%04x", (int)c)); else b.append(c); } } } return b.append('\"').toString(); }
  private static String message(String type,String room,Object... pairs) { StringBuilder b=new StringBuilder("{\"v\":1,\"roomId\":").append(quote(room)).append(",\"type\":").append(quote(type)); for(int i=0;i<pairs.length;i+=2) { b.append(',').append(quote((String)pairs[i])).append(':'); Object value=pairs[i+1]; b.append(value == null ? "null" : quote(value.toString())); } return b.append('}').toString(); }
  private static String error(String room,String code,String message,String request) { return request == null ? message("error",room,"code",code,"message",message) : message("error",room,"code",code,"message",message,"requestId",request); }
  private static String snapshot(LocalRoomSimulation room) { StringBuilder b=new StringBuilder("{\"v\":1,\"roomId\":").append(quote(room.roomId())).append(",\"type\":\"snapshot\",\"tick\":").append(room.tick()).append(",\"players\":["); boolean first=true; for(LocalRoomSimulation.PlayerState p:room.snapshot().players()) { if(!first)b.append(','); first=false; b.append("{\"sessionId\":").append(quote(p.sessionId())).append(",\"x\":").append(p.x()).append(",\"z\":").append(p.z()).append(",\"lastSequence\":").append(p.lastSequence()).append('}'); } return b.append("]}").toString(); }
  private static final class ProtocolException extends RuntimeException { final String code; ProtocolException(String code,String msg){super(msg);this.code=code;} }
}
