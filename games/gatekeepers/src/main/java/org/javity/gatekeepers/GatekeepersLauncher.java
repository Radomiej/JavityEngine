package org.javity.gatekeepers;

import com.badlogic.gdx.backends.lwjgl3.*;
import java.nio.file.Path;
import java.util.Arrays;

public final class GatekeepersLauncher {
  private GatekeepersLauncher() {}

  public static void main(String[] args) {
    boolean pveSmoke = Arrays.asList(args).contains("--pve-smoke");
    boolean smoke = pveSmoke || Arrays.asList(args).contains("--smoke");
    Path save = Path.of(System.getProperty("user.home"), ".gatekeepers", "profile.properties");
    Path captures = Path.of("artifacts", "gatekeepers");
    for (String arg : args) {
      if (arg.startsWith("--save=")) save = Path.of(arg.substring(7));
      if (arg.startsWith("--captures=")) captures = Path.of(arg.substring(11));
    }
    var config = new Lwjgl3ApplicationConfiguration();
    config.setTitle("Strażnicy Bram — Rozdział I | Javity Engine");
    config.setWindowedMode(1440, 900);
    config.setWindowSizeLimits(1000, 680, -1, -1);
    config.useVsync(!smoke);
    config.setForegroundFPS(smoke ? 0 : 60);
    config.disableAudio(true);
    config.setPauseWhenLostFocus(!smoke);
    var app = new GatekeepersApplication(save, smoke, captures, pveSmoke);
    config.setWindowListener(
        new Lwjgl3WindowAdapter() {
          @Override
          public void focusLost() {
            app.focusLost();
          }
        });
    new Lwjgl3Application(app, config);
  }
}
