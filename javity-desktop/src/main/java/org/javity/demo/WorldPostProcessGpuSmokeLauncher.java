package org.javity.demo;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import java.nio.file.Path;

/** Separate real-context proof for world-only postprocessing output and resource ownership. */
public final class WorldPostProcessGpuSmokeLauncher {
  private WorldPostProcessGpuSmokeLauncher() {}

  public static void main(String[] args) throws Exception {
    if (args.length > 0 && args[0].equals("--gl20-contract")) {
      Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
      configuration.setTitle("Javity world postprocess GL20 contract smoke");
      configuration.setWindowedMode(48, 48);
      configuration.setForegroundFPS(60);
      configuration.setInitialVisible(false);
      configuration.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL20, 2, 0);
      WorldPostProcessGl20ContractProbe probe = new WorldPostProcessGl20ContractProbe();
      new Lwjgl3Application(probe, configuration);
      probe.assertSuccess();
      return;
    }

    Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
    configuration.setTitle("Javity world postprocess GPU smoke");
    configuration.setWindowedMode(96, 96);
    configuration.setForegroundFPS(60);
    configuration.setInitialVisible(false);
    configuration.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 2);
    WorldPostProcessGpuSmokeProbe probe = new WorldPostProcessGpuSmokeProbe();
    new Lwjgl3Application(probe, configuration);
    probe.assertSuccess();

    String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    Process gl20 =
        new ProcessBuilder(
                java,
                "--enable-native-access=ALL-UNNAMED",
                "-cp",
                System.getProperty("java.class.path"),
                WorldPostProcessGpuSmokeLauncher.class.getName(),
                "--gl20-contract")
            .inheritIO()
            .start();
    int exitCode = gl20.waitFor();
    if (exitCode != 0)
      throw new IllegalStateException("GL20 contract probe exited with " + exitCode);
  }
}
