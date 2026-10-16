package org.javity.demo;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Standalone real OpenGL particle integration proof. */
public final class ParticleGpuSmokeLauncher {
  private ParticleGpuSmokeLauncher() {}

  public static void main(String[] args) {
    if (args.length > 0 && args[0].equals("--gl20-reject")) {
      new Lwjgl3Application(
          new ParticleGl20UnsupportedProbe(),
          configuration(Lwjgl3ApplicationConfiguration.GLEmulation.GL20));
      return;
    }
    new Lwjgl3Application(
        new ParticleGpuSmokeProbe(),
        configuration(Lwjgl3ApplicationConfiguration.GLEmulation.GL30));
    runGl20ProbeInChild();
  }

  private static void runGl20ProbeInChild() {
    String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    try {
      Process child =
          new ProcessBuilder(
                  java,
                  "-cp",
                  System.getProperty("java.class.path"),
                  ParticleGpuSmokeLauncher.class.getName(),
                  "--gl20-reject")
              .inheritIO()
              .start();
      if (!child.waitFor(90, TimeUnit.SECONDS)) {
        child.destroyForcibly();
        throw new IllegalStateException("Timed out waiting for GL20 capability probe child");
      }
      if (child.exitValue() != 0)
        throw new IllegalStateException("GL20 capability probe child failed: " + child.exitValue());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Interrupted while waiting for GL20 capability probe", interrupted);
    } catch (java.io.IOException failure) {
      throw new IllegalStateException("Could not launch GL20 capability probe child", failure);
    }
  }

  private static Lwjgl3ApplicationConfiguration configuration(
      Lwjgl3ApplicationConfiguration.GLEmulation emulation) {
    Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
    config.setTitle("Javity 3D particle smoke");
    config.setWindowedMode(256, 256);
    config.setForegroundFPS(60);
    config.setInitialVisible(false);
    config.setOpenGLEmulation(
        emulation,
        emulation == Lwjgl3ApplicationConfiguration.GLEmulation.GL30 ? 3 : 2,
        emulation == Lwjgl3ApplicationConfiguration.GLEmulation.GL30 ? 2 : 0);
    return config;
  }
}
