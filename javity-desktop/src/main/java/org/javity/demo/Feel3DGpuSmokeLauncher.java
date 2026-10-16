package org.javity.demo;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

/** Standalone hidden-window GL30 acceptance run for the integrated Feel3D gallery. */
public final class Feel3DGpuSmokeLauncher {
  private Feel3DGpuSmokeLauncher() {}

  public static void main(String[] args) {
    Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
    config.setTitle("Javity Feel3D integrated smoke");
    config.setWindowedMode(256, 256);
    config.setForegroundFPS(60);
    config.setInitialVisible(false);
    config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 2);
    Feel3DGpuSmokeProbe probe = new Feel3DGpuSmokeProbe();
    new Lwjgl3Application(probe, config);
    if (probe.failure() != null)
      throw new IllegalStateException("Integrated Feel3D smoke failed", probe.failure());
  }
}
