package org.javity.rooms;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Owns the workspace Colyseus process, its control pipe, and bounded shutdown. */
public final class LocalDevServer implements AutoCloseable {
  private final Process process;
  private final CompletableFuture<String> ready = new CompletableFuture<>();
  private String readyJson;
  private boolean closed;

  private LocalDevServer(Process process) {
    this.process = process;
    Thread.ofVirtual().name("colyseus-output").start(() -> {
      try (Reader reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
        StringBuilder line = new StringBuilder();
        int c;
        while ((c = reader.read()) != -1) {
          if (c == '\n') {
            String value = line.toString(); line.setLength(0);
            if (value.startsWith("JAVITY_DEV_READY ")) ready.complete(value.substring(17));
            else System.out.println(value);
          } else if (line.length() < 8192) line.append((char) c);
          else throw new IOException("Colyseus output line exceeds 8192 characters");
        }
        ready.completeExceptionally(new IOException("Colyseus exited before readiness"));
      } catch (IOException failure) { ready.completeExceptionally(failure); process.destroy(); }
    });
  }

  public static LocalDevServer start(Path workspace, int port, boolean deployment) throws IOException {
    if (port < 0 || port > 65535) throw new IllegalArgumentException("Port must be 0..65535");
    Path root = workspace.toRealPath();
    Path script = root.resolve("web/local-multiplayer/src/cli.js");
    if (!Files.isRegularFile(script)) throw new IOException("Workspace has no local Colyseus package: " + root);
    ProcessBuilder builder = new ProcessBuilder(System.getenv().getOrDefault("JAVITY_NODE_BINARY", "node"), script.toString());
    builder.directory(root.toFile()).redirectError(ProcessBuilder.Redirect.INHERIT);
    builder.environment().put("PORT", Integer.toString(port));
    builder.environment().put("JAVITY_DEV_SUPERVISED", "1");
    builder.environment().put("JAVITY_CONTAINER", deployment ? "1" : "0");
    builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
    LocalDevServer server = new LocalDevServer(builder.start());
    try { server.readyJson = server.ready.get(15, TimeUnit.SECONDS); return server; }
    catch (InterruptedException failure) {
      server.close(); Thread.currentThread().interrupt(); throw new IOException("Interrupted during Colyseus startup", failure);
    } catch (ExecutionException | TimeoutException failure) {
      server.close(); throw new IOException("Colyseus did not start; check Node 24, npm ci and the worker installDist", failure);
    }
  }

  public String readinessJson() { return readyJson; }
  public long pid() { return process.pid(); }
  public int awaitExit() throws InterruptedException { return process.waitFor(); }

  @Override public synchronized void close() {
    if (closed) return;
    closed = true;
    // Capture owned descendants before graceful exit can reparent them.
    List<ProcessHandle> descendants = process.descendants().toList();
    boolean interrupted = Thread.interrupted();
    try {
      try {
        process.getOutputStream().write("shutdown\n".getBytes(StandardCharsets.UTF_8));
        process.getOutputStream().flush();
        process.getOutputStream().close();
      } catch (IOException ignored) { /* EOF/exit already closes ownership. */ }
      if (!process.waitFor(6, TimeUnit.SECONDS)) {
        process.destroy();
        if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
      }
    } catch (InterruptedException failure) { interrupted = true; process.destroyForcibly(); }
    finally {
      // Also cover abrupt Node failure and a stuck gateway that could not reap its workers.
      for (ProcessHandle child : descendants) if (child.isAlive()) child.destroy();
      long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
      while (descendants.stream().anyMatch(ProcessHandle::isAlive) && System.nanoTime() < deadline) {
        try { Thread.sleep(20); } catch (InterruptedException failure) { interrupted = true; }
      }
      for (ProcessHandle child : descendants) if (child.isAlive()) child.destroyForcibly();
      if (interrupted) Thread.currentThread().interrupt();
    }
  }
}
