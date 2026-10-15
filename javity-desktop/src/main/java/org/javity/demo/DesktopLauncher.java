package org.javity.demo;

import com.badlogic.gdx.backends.lwjgl3.*;
import java.util.Arrays;

public final class DesktopLauncher {
  private DesktopLauncher() {}

  public static void main(String[] args) {
    var config = new Lwjgl3ApplicationConfiguration();
    config.setTitle("Javity Engine — Java 25 / libGDX / Yoga");
    boolean mm = Arrays.asList(args).contains("--mm");
    boolean gorzow = Arrays.asList(args).contains("--gorzow");
    boolean maplibre = Arrays.asList(args).contains("--maplibre");
    if (maplibre || gorzow) org.javity.maplibre.MapLibreDesktop.configure(config);
    boolean voxels = Arrays.asList(args).contains("--voxels");
    boolean materials = Arrays.asList(args).contains("--materials");
    if (voxels) config.setPauseWhenLostFocus(true);
    String devPath = null;
    for (String argument : args) {
      if (argument.startsWith("--ui-dev=")) devPath = argument.substring("--ui-dev=".length());
    }
    if (devPath != null && (!mm || devPath.isBlank()))
      throw new IllegalArgumentException("--ui-dev=<bundle> requires --mm and a nonempty path");
    config.setWindowedMode(mm || voxels ? 1280 : 1100, 720);
    config.useVsync(true);
    config.setForegroundFPS(60);
    config.disableAudio(!(mm || Arrays.asList(args).contains("--runtime")));
    boolean smoke = Arrays.asList(args).contains("--smoke");
    new Lwjgl3Application(
        gorzow
            ? new GorzowDemo(smoke)
            : maplibre
                ? new MapLibreDemo(smoke)
                : voxels
                    ? new VoxelDemo(smoke)
                    : materials
                        ? new ShapeMaterialsDemo(smoke)
                        : mm
                            ? new MmSystemsDemo(smoke, devPath)
                            : Arrays.asList(args).contains("--legacy")
                                ? new DemoApplication(smoke)
                                : Arrays.asList(args).contains("--map")
                                    ? new MapDemo(smoke)
                                    : Arrays.asList(args).contains("--runtime")
                                        ? new RuntimeDemo(smoke)
                                        : Arrays.asList(args).contains("--shapes")
                                            ? new ShapesDemo(smoke)
                                            : Arrays.asList(args).contains("--plugins")
                                                ? new PluginsDemo(smoke)
                                                : Arrays.asList(args).contains("--ui-parity")
                                                    ? new UiParityDemo(smoke)
                                                    : new ModernDemo(smoke),
        config);
  }
}
