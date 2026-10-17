package org.javity.demo;

import com.badlogic.gdx.backends.lwjgl3.*;
import java.util.Arrays;

/** Opt-in native animated palette proof. */
public final class SkinnedInstancingSmokeLauncher {
  public static void main(String[] args) {
    boolean gl20 = Arrays.asList(args).contains("--gl20");
    var config = new Lwjgl3ApplicationConfiguration();
    config.setTitle("Skinned instancing proof");
    config.setWindowedMode(640, 480);
    config.setInitialVisible(false);
    config.setOpenGLEmulation(
        gl20
            ? Lwjgl3ApplicationConfiguration.GLEmulation.GL20
            : Lwjgl3ApplicationConfiguration.GLEmulation.GL30,
        gl20 ? 2 : 3,
        gl20 ? 0 : 3);
    new Lwjgl3Application(new SkinnedInstancingDemo(gl20), config);
  }
}
