package org.javity.rooms;

import java.nio.file.Path;

/** One workspace entry point for development and container supervision. */
public final class LocalDevServerMain {
  private LocalDevServerMain() {}
  public static void main(String[] args) throws Exception {
    Path workspace = Path.of(".");
    int port = 2567;
    boolean deployment = false;
    for (String arg : args) {
      if (arg.startsWith("--workspace=")) workspace = Path.of(arg.substring(12));
      else if (arg.startsWith("--port=")) port = Integer.parseInt(arg.substring(7));
      else if (arg.equals("--container")) deployment = true;
      else throw new IllegalArgumentException("Unknown argument: " + arg);
    }
    try (LocalDevServer server = LocalDevServer.start(workspace, port, deployment)) {
      Thread shutdown = new Thread(server::close, "colyseus-shutdown");
      Runtime.getRuntime().addShutdownHook(shutdown);
      try {
        System.out.println("JAVITY_DEV_READY " + server.readinessJson());
        int code = server.awaitExit();
        if (code != 0) throw new IllegalStateException("Colyseus exited with code " + code);
      } finally {
        try { Runtime.getRuntime().removeShutdownHook(shutdown); }
        catch (IllegalStateException ignored) { /* JVM shutdown already owns the hook. */ }
      }
    }
  }
}
