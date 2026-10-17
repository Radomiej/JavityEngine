package org.javity.rooms;

import static org.junit.Assert.*;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Predicate;
import org.junit.Test;

public class LocalRoomWorkerProcessTest {
  private Process launch(String roomId) throws Exception {
    String cp = System.getProperty("java.class.path");
    return new ProcessBuilder(System.getProperty("java.home") + "/bin/java", "-cp", cp,
        "org.javity.rooms.LocalRoomWorker", roomId).start();
  }
  private static String command(String room, String type, String request, String fields) {
    return "{\"v\":1,\"roomId\":\"" + room + "\",\"type\":\"" + type
        + "\",\"requestId\":\"" + request + "\"" + fields + "}\n";
  }
  private static String join(String room, String request, String session) {
    return command(room, "join", request, ",\"sessionId\":\"" + session + "\"");
  }
  private static String leave(String room, String request, String session) {
    return command(room, "leave", request, ",\"sessionId\":\"" + session + "\"");
  }
  private static String shutdown(String room, String request) {
    return command(room, "shutdown", request, "");
  }
  private static JsonValue parse(String line) { return new JsonReader().parse(line); }
  private static final class RunningWorker implements AutoCloseable {
    final Process process;
    final BufferedWriter stdin;
    final BlockingQueue<String> stdout = new LinkedBlockingQueue<>();
    final List<JsonValue> observedSnapshots = new CopyOnWriteArrayList<>();
    final Thread reader;
    RunningWorker(String room) throws Exception {
      process = new LocalRoomWorkerProcessTest().launch(room);
      stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
      reader = new Thread(() -> {
        try (BufferedReader lines = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
          String line; while ((line = lines.readLine()) != null) { JsonValue value = parse(line); if ("snapshot".equals(value.getString("type"))) observedSnapshots.add(value); stdout.offer(line); }
        } catch (IOException ignored) { }
      }, "test-worker-stdout");
      reader.setDaemon(true); reader.start();
    }
    void send(String line) throws IOException { stdin.write(line); stdin.flush(); }
    JsonValue await(Predicate<JsonValue> predicate, String description) throws Exception {
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
      while (System.nanoTime() < deadline) {
        String line = stdout.poll(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        if (line == null) break;
        JsonValue message = parse(line);
        if (predicate.test(message)) return message;
      }
      fail("timed out waiting for " + description + "; process alive=" + process.isAlive());
      return null;
    }
    JsonValue await(String type, String requestId) throws Exception {
      return await(m -> type.equals(m.getString("type"))
          && (requestId == null || requestId.equals(m.getString("requestId", null))), type + "/" + requestId);
    }
    @Override public void close() throws Exception {
      try { stdin.close(); } catch (IOException ignored) { }
      if (process.isAlive() && !process.waitFor(1, TimeUnit.SECONDS)) process.destroy();
      if (process.isAlive() && !process.waitFor(1, TimeUnit.SECONDS)) process.destroyForcibly();
      process.waitFor(1, TimeUnit.SECONDS);
      reader.join(500);
    }
  }
  @Test public void tickBudgetPersistsAcrossPollBatchesAndResetsOnlyAfterStep() {
    LocalRoomWorker.TickInputBudget budget = new LocalRoomWorker.TickInputBudget(64);
    for (int i = 0; i < 40; i++) budget.consume();
    assertEquals(24, budget.remaining()); // Another outer-loop poll cannot replenish it.
    for (int i = 0; i < 24; i++) budget.consume();
    assertEquals(0, budget.remaining());
    try { budget.consume(); fail("budget must stop the 65th command"); }
    catch (IllegalStateException expected) { }
    budget.reset();
    assertEquals(64, budget.remaining());
  }
  @Test public void ackOverflowRemainsFatalWhenWriterProgressesBeforeErrorHandling() {
    final class WriterProgressFixture implements LocalRoomWorker.OutputSink {
      int offerCalls;
      boolean freedAfterFullOffer;
      final ArrayDeque<String> oneSlot = new ArrayDeque<>(1);
      @Override public boolean offer(String line) {
        offerCalls++;
        if (!oneSlot.isEmpty()) {
          oneSlot.removeFirst(); // Model the writer freeing the full slot just after offer failed.
          freedAfterFullOffer = true;
          return false;
        }
        oneSlot.addLast(line);
        return true;
      }
      @Override public String take() { return oneSlot.pollFirst(); }
      @Override public boolean offerStop() { return true; }
    }
    LocalRoomWorker worker = new LocalRoomWorker();
    WriterProgressFixture output = new WriterProgressFixture();
    assertTrue(output.offer("ready"));
    try (LocalRoomSimulation room = new LocalRoomSimulation("room-F")) {
      byte[] join = join("room-F", "join-1", "s").stripTrailing().getBytes(StandardCharsets.UTF_8);
      assertFalse(worker.handle(join, "room-F", room, output));
      assertTrue(output.freedAfterFullOffer);
      assertEquals("Only ready and the failed ack offer are attempted; no invalid-command response is queued.", 2, output.offerCalls);
      assertEquals(1, room.snapshot().players().size()); // Join took effect before the required ack overflowed.
      assertNull("no recovery error may be enqueued after the writer frees space", output.take());
    }
  }
  @Test public void realWorkerProcessesMovementAndCorrelatesResponsesDespiteSnapshots() throws Exception {
    try (RunningWorker worker = new RunningWorker("room-A")) {
      assertEquals("ready", worker.await("ready", null).getString("type"));
      worker.send(join("room-A", "join-1", "s"));
      worker.await("ack", "join-1");
      worker.send("{\"v\":1,\"roomId\":\"room-A\",\"type\":\"input\",\"requestId\":\"i1\",\"sessionId\":\"s\",\"sequence\":0,\"x\":1,\"z\":0}\n");
      JsonValue snapshot = worker.await(m -> "snapshot".equals(m.getString("type"))
          && m.get("players") != null && m.get("players").size > 0, "player snapshot");
      assertTrue(snapshot.get("players").get(0).getDouble("x") > 0);
      worker.send(leave("room-A", "leave-1", "s"));
      worker.await("ack", "leave-1");
      worker.send(shutdown("room-A", "shutdown-1"));
      worker.await("ack", "shutdown-1");
      assertTrue("worker did not terminate", worker.process.waitFor(3, TimeUnit.SECONDS));
      assertEquals(0, worker.process.exitValue());
    }
  }
  @Test public void sustainedValidInputStillProducesSnapshotsWhileStdoutIsDrained() throws Exception {
    try (RunningWorker worker = new RunningWorker("room-live")) {
      worker.await("ready", null);
      worker.send(join("room-live", "join", "s")); worker.await("ack", "join");
      int batches = 90;
      for (int batch = 0; batch < batches; batch++) {
        StringBuilder frames = new StringBuilder();
        for (int i = 0; i < 48; i++) {
          long sequence = (long) batch * 48 + i;
          frames.append("{\"v\":1,\"roomId\":\"room-live\",\"type\":\"input\",\"requestId\":\"i")
              .append(sequence).append("\",\"sessionId\":\"s\",\"sequence\":").append(sequence)
              .append(",\"x\":1,\"z\":0}\n");
        }
        worker.stdin.write(frames.toString()); worker.stdin.flush();
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(18));
      }
      worker.send(shutdown("room-live", "done"));
      worker.await("ack", "done");
      List<Long> ticks = worker.observedSnapshots.stream().map(message -> message.getLong("tick")).toList();
      assertTrue("sustained valid input starved snapshots: " + ticks, ticks.size() >= 20);
      for (int i = 1; i < ticks.size(); i++) assertEquals(3, ticks.get(i) - ticks.get(i - 1));
      assertTrue(worker.process.waitFor(3, TimeUnit.SECONDS));
    }
  }
  @Test public void rejectsWrongRoomAndExtraFieldsThenCanShutdown() throws Exception {
    try (RunningWorker worker = new RunningWorker("room-C")) {
      worker.await("ready", null);
      worker.send(join("elsewhere", "wrong", "s"));
      JsonValue mismatch = worker.await(m -> "error".equals(m.getString("type")) && "wrong".equals(m.getString("requestId")), "wrong-room error");
      assertEquals("room_mismatch", mismatch.getString("code"));
      worker.send(command("room-C", "join", "extra", ",\"sessionId\":\"s\",\"targetId\":4"));
      JsonValue extra = worker.await(m -> "error".equals(m.getString("type")) && "extra".equals(m.getString("requestId")), "extra-field error");
      assertEquals("extra_field", extra.getString("code"));
      worker.send(shutdown("room-C", "stop")); worker.await("ack", "stop");
      assertTrue(worker.process.waitFor(3, TimeUnit.SECONDS));
    }
  }
  @Test public void malformedFrameReturnsErrorAndEofClosesWithinBound() throws Exception {
    try (RunningWorker worker = new RunningWorker("room-B")) {
      worker.await("ready", null);
      worker.send("bad json\n");
      worker.await("error", null);
      worker.stdin.close();
      assertTrue("EOF shutdown exceeded bound", worker.process.waitFor(3, TimeUnit.SECONDS));
    }
  }
  @Test public void oversizedUnterminatedFrameKillsWorkerWithinBound() throws Exception {
    Process p = launch("room-D");
    try {
      BufferedReader out = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
      assertTrue(parse(out.readLine()).getString("type").equals("ready"));
      byte[] oversized = new byte[8193]; Arrays.fill(oversized, (byte)'x');
      p.getOutputStream().write(oversized); p.getOutputStream().flush(); p.getOutputStream().close();
      assertTrue("oversized frame did not stop worker", p.waitFor(3, TimeUnit.SECONDS));
      assertNotEquals(0, p.exitValue());
    } finally {
      if (p.isAlive()) { p.destroy(); if (!p.waitFor(1, TimeUnit.SECONDS)) p.destroyForcibly(); }
    }
  }
  @Test public void pacedValidAckSaturationIsFatalWithoutInboundQueueOverflow() throws Exception {
    Process p = launch("room-E");
    try {
      BufferedReader stdout = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
      assertEquals("ready", parse(stdout.readLine()).getString("type"));
      Thread sender = new Thread(() -> {
        try {
          BufferedWriter input = new BufferedWriter(new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8));
          for (int pair = 0; pair < 2000 && p.isAlive(); pair++) {
            input.write(join("room-E", "j" + pair, "s"));
            input.write(leave("room-E", "l" + pair, "s"));
            input.flush();
            LockSupport.parkNanos(750_000L); // ~2667 commands/s, below the 64/tick service ceiling.
          }
        } catch (IOException ignored) { }
      }, "paced-ack-saturation-sender");
      sender.setDaemon(true); sender.start();
      assertTrue("writer queue saturation did not stop worker", p.waitFor(5, TimeUnit.SECONDS));
      sender.join(500);
      assertEquals(1, p.exitValue());
      String stderr = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
      assertTrue("worker did not report ack-queue overflow; stderr=" + stderr, stderr.contains("stdout queue full"));
    } finally {
      if (p.isAlive()) { p.destroy(); if (!p.waitFor(1, TimeUnit.SECONDS)) p.destroyForcibly(); }
      try { p.getOutputStream().close(); } catch (IOException ignored) { }
    }
  }
  @Test public void oversizedUnconsumedMalformedFloodTerminatesBoundedly() throws Exception {
    Process p = launch("room-G");
    try {
      BufferedReader out = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
      assertTrue(parse(out.readLine()).getString("type").equals("ready"));
      Thread sender = new Thread(() -> {
        try { OutputStream stdin = p.getOutputStream(); byte[] bad = "not-json\n".getBytes(StandardCharsets.UTF_8);
          for (int i=0;i<20_000 && p.isAlive();i++) { stdin.write(bad); if (i % 32 == 0) stdin.flush(); }
          stdin.flush();
        } catch (IOException ignored) { }
      }, "worker-inbound-flood-sender");
      sender.setDaemon(true); sender.start();
      assertTrue("inbound queue overflow did not stop worker", p.waitFor(4, TimeUnit.SECONDS));
      assertEquals(1, p.exitValue());
    } finally {
      if (p.isAlive()) { p.destroy(); if (!p.waitFor(1, TimeUnit.SECONDS)) p.destroyForcibly(); }
      try { p.getOutputStream().close(); } catch (IOException ignored) { }
    }
  }
}
