package org.javity.demo;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

/** Standalone real OpenGL proof for typed render-only model and per-view camera presentation. */
public final class Presentation3DGpuSmokeLauncher {
  private Presentation3DGpuSmokeLauncher() {}

  public static void main(String[] args) {
    Lwjgl3ApplicationConfiguration c = new Lwjgl3ApplicationConfiguration();
    c.setTitle("Javity Presentation3D smoke");
    c.setWindowedMode(256, 256);
    c.setForegroundFPS(60);
    c.setInitialVisible(false);
    new Lwjgl3Application(new Presentation3DGpuSmokeProbe(), c);
  }
}
