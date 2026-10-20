package org.javity.demo;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

/** Real importer, texture, independent skin pose, PBR light and GPU cleanup proof. */
public final class GltfGpuSmokeLauncher {
  private GltfGpuSmokeLauncher() {}

  public static void main(String[] args) {
    Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
    config.setTitle("Javity glTF/PBR smoke");
    config.setWindowedMode(384, 256);
    config.setInitialVisible(false);
    config.disableAudio(true);
    config.setOpenGLEmulation(
        args.length > 0 && args[0].equals("--gl20")
            ? Lwjgl3ApplicationConfiguration.GLEmulation.GL20
            : Lwjgl3ApplicationConfiguration.GLEmulation.GL30,
        3,
        2);
    new Lwjgl3Application(new GltfDemo(true), config);
  }
}
