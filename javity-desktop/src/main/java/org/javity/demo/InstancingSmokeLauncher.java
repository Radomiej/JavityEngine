package org.javity.demo;

import com.badlogic.gdx.backends.lwjgl3.*;
import java.util.Arrays;

/** Real native contexts; GL20 explicitly uses the fallback backend. */
public final class InstancingSmokeLauncher {
  public static void main(String[] args) {
    boolean gl20 = Arrays.asList(args).contains("--gl20");
    var config = new Lwjgl3ApplicationConfiguration();
    config.setTitle("Static instancing proof");
    config.setWindowedMode(256, 256);
    config.setInitialVisible(false);
    config.setOpenGLEmulation(
        gl20
            ? Lwjgl3ApplicationConfiguration.GLEmulation.GL20
            : Lwjgl3ApplicationConfiguration.GLEmulation.GL30,
        gl20 ? 2 : 3,
        gl20 ? 0 : 3);
    new Lwjgl3Application(new InstancingDemo(gl20), config);
  }
}
