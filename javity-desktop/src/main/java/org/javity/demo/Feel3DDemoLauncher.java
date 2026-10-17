package org.javity.demo;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

/** Standalone visible GL30 entrypoint for the interactive Feel3D demo. */
public final class Feel3DDemoLauncher {
  private Feel3DDemoLauncher() {}

  public static void main(String[] args) {
    Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
    config.setTitle("Javity Feel3D");
    config.setWindowedMode(1280, 800);
    config.setForegroundFPS(120);
    config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 2);
    new Lwjgl3Application(new Feel3DDemo(), config);
  }
}
