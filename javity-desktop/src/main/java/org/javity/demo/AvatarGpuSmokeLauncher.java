package org.javity.demo;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import java.util.Arrays;

/** Standalone real OpenGL avatar sidecar proof. */
public final class AvatarGpuSmokeLauncher {
  private AvatarGpuSmokeLauncher() {}

  public static void main(String[] args) {
    boolean gl30 = Arrays.asList(args).contains("--gl30");
    Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
    configuration.setTitle("Javity avatar geometry smoke");
    configuration.setWindowedMode(256, 256);
    configuration.setForegroundFPS(60);
    configuration.setInitialVisible(false);
    if (gl30)
      configuration.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 2);
    else configuration.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL20, 2, 0);
    new Lwjgl3Application(new AvatarGpuSmokeProbe(gl30 ? "GL30" : "GL20"), configuration);
  }
}
