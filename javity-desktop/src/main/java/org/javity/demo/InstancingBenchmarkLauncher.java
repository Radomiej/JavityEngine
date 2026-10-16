package org.javity.demo;

import com.badlogic.gdx.backends.lwjgl3.*;

/** Fixed, opt-in native benchmark; a missing workload must fail the verification task. */
public final class InstancingBenchmarkLauncher {
  public static void main(String[] args) {
    var config = new Lwjgl3ApplicationConfiguration();
    config.setTitle("Instancing benchmark seed42");
    config.setWindowedMode(640, 480);
    config.setInitialVisible(false);
    config.useVsync(false);
    config.setForegroundFPS(0);
    config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 3);
    new Lwjgl3Application(new InstancingBenchmark(), config);
  }
}
