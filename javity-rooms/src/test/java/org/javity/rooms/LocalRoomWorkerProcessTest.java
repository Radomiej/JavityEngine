package org.javity.rooms;

import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import org.junit.Test;

public class LocalRoomWorkerProcessTest {
  private static final ExecutorService READERS = Executors.newCachedThreadPool(r -> {
    Thread thread = new Thread(r, "bounded-worker-test-reader"); thread.setDaemon(true); return thread;
  });
  private Process launch(String roomId) throws Exception {
    String cp = System.getProperty("java.class.path");
    return new ProcessBuilder(System.getProperty("java.home") + "/bin/java", "-cp", cp,
        "org.javity.rooms.LocalRoomWorker", roomId).redirectError(ProcessBuilder.Redirect.INHERIT).start();
  }
  private static String readLine(BufferedReader r) throws Exception {
    String line = READERS.submit(r::readLine).get(3, TimeUnit.SECONDS);
    assertNotNull("worker closed stdout early", line); return line;
  }
  private static String command(String room, String type, String extras) {
    return "{\"v\":1,\"roomId\":\"" + room + "\",\"type\":\"" + type + "\",\"requestId\":\"r\"" + extras + "}\n";
  }
  @Test public void realProcessRunsCommandsAndShutsDown() throws Exception {
    Process p = launch("room-A");
    try (BufferedReader out = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
         BufferedWriter in = new BufferedWriter(new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8))) {
      assertTrue(readLine(out).contains("\"type\":\"ready\""));
      in.write(command("room-A", "join", ",\"sessionId\":\"s\"")); in.flush();
      assertTrue(readLine(out).contains("\"requestId\":\"r\""));
      in.write("{\"v\":1,\"roomId\":\"room-A\",\"type\":\"input\",\"requestId\":\"i\",\"sessionId\":\"s\",\"sequence\":0,\"x\":1,\"z\":0}\n"); in.flush();
      String got = ""; long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
      while (System.nanoTime() < deadline && !got.contains("\"type\":\"snapshot\"")) got = readLine(out);
      assertTrue(got, got.contains("\"type\":\"snapshot\""));
      java.util.regex.Matcher position = java.util.regex.Pattern.compile("\"x\":([0-9.eE+-]+)").matcher(got);
      assertTrue("snapshot missing player position: " + got, position.find());
      assertTrue("Java authority did not move the player: " + got, Double.parseDouble(position.group(1)) > 0);
      in.write(command("room-A", "leave", ",\"sessionId\":\"s\"")); in.flush();
      assertTrue(readLine(out).contains("\"requestId\":\"r\""));
      in.write(command("room-A", "shutdown", "")); in.flush();
      assertTrue(readLine(out).contains("\"requestId\":\"r\""));
    } finally { p.getOutputStream().close(); }
    assertTrue("worker did not terminate", p.waitFor(3, TimeUnit.SECONDS));
    assertEquals(0, p.exitValue());
  }
  @Test public void rejectsWrongRoomAndExtraFieldsThenCanShutdown() throws Exception {
    Process p = launch("room-C");
    try (BufferedReader out = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
         BufferedWriter in = new BufferedWriter(new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8))) {
      assertTrue(readLine(out).contains("ready"));
      in.write(command("elsewhere", "join", ",\"sessionId\":\"s\"")); in.flush();
      assertTrue(readLine(out).contains("room_mismatch"));
      in.write(command("room-C", "join", ",\"sessionId\":\"s\",\"targetId\":4")); in.flush();
      assertTrue(readLine(out).contains("extra_field"));
      in.write(command("room-C", "shutdown", "")); in.flush();
      assertTrue(readLine(out).contains("\"type\":\"ack\""));
    } finally { p.getOutputStream().close(); }
    assertTrue(p.waitFor(3, TimeUnit.SECONDS));
  }
  @Test public void eofAndMalformedFramesHaveBoundedLifecycle() throws Exception {
    Process p = launch("room-B");
    try (BufferedReader out = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
      assertTrue(readLine(out).contains("ready"));
      p.getOutputStream().write("bad json\n".getBytes(StandardCharsets.UTF_8)); p.getOutputStream().flush();
      assertTrue(readLine(out).contains("\"type\":\"error\""));
    } finally { p.getOutputStream().close(); }
    assertTrue("EOF shutdown exceeded bound", p.waitFor(3, TimeUnit.SECONDS));
  }
  @Test public void oversizedUnterminatedFrameKillsWorkerWithinBound() throws Exception {
    Process p = launch("room-D");
    assertTrue(readLine(new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))).contains("ready"));
    byte[] oversized = new byte[8193]; java.util.Arrays.fill(oversized, (byte)'x');
    p.getOutputStream().write(oversized); p.getOutputStream().flush(); p.getOutputStream().close();
    assertTrue("oversized frame did not stop worker", p.waitFor(3, TimeUnit.SECONDS));
    assertNotEquals(0, p.exitValue());
  }
  @Test public void undrainedStdoutFloodCannotKeepWorkerAlive() throws Exception {
    Process p = launch("room-E");
    Thread sender = new Thread(() -> {
      try { OutputStream stdin = p.getOutputStream(); byte[] bad = "not-json\n".getBytes(StandardCharsets.UTF_8);
        for (int i=0;i<20_000;i++) stdin.write(bad); stdin.flush();
      } catch (IOException ignored) { }
    }, "worker-flood-sender"); sender.setDaemon(true); sender.start();
    boolean ended = p.waitFor(4, TimeUnit.SECONDS);
    if (!ended) p.destroyForcibly();
    assertTrue("undrained stdout left the worker alive", ended);
    sender.join(500);
  }
}
