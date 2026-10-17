package org.javity.demo;

import com.badlogic.gdx.backends.lwjgl3.*;
import java.nio.file.Path;
import java.util.Arrays;
import org.javity.serialization.SceneJson;

public final class DesktopLauncher {
  private DesktopLauncher() {}

  public static void main(String[] args) {
    var config = new Lwjgl3ApplicationConfiguration();
    config.setTitle("Javity Engine — Java 25 / libGDX / Yoga");
    boolean mm = Arrays.asList(args).contains("--mm");
    boolean gorzow = Arrays.asList(args).contains("--gorzow");
    boolean maplibre = Arrays.asList(args).contains("--maplibre");
    if (maplibre || gorzow) org.javity.maplibre.MapLibreDesktop.configure(config);
    boolean environment = Arrays.asList(args).contains("--environment");
    boolean assetStreaming = Arrays.asList(args).contains("--asset-streaming");
    boolean voxels = Arrays.asList(args).contains("--voxels");
    boolean physics3d = Arrays.asList(args).contains("--physics3d");
    boolean materials = Arrays.asList(args).contains("--materials");
    if (voxels || physics3d) config.setPauseWhenLostFocus(true);
    String devPath = null;
    Path sceneExport = null;
    for (String argument : args) {
      if (argument.startsWith("--ui-dev=")) devPath = argument.substring("--ui-dev=".length());
      if (argument.startsWith("--scene-export=")) {
        String value = argument.substring("--scene-export=".length());
        sceneExport = Path.of(value);
        if (value.isBlank() || !sceneExport.isAbsolute())
          throw new IllegalArgumentException("--scene-export requires an absolute destination path");
      }
    }
    boolean otherDemo =
        Arrays.stream(args)
            .anyMatch(
                argument ->
                    Arrays.asList(
                            "--legacy", "--map", "--runtime", "--shapes", "--plugins", "--ui-parity")
                        .contains(argument));
    if (sceneExport != null && (mm || gorzow || maplibre || voxels || physics3d || materials || assetStreaming || environment || otherDemo))
      throw new IllegalArgumentException("--scene-export is available with the ModernDemo runtime inspector");
    if (devPath != null && (!mm || devPath.isBlank()))
      throw new IllegalArgumentException("--ui-dev=<bundle> requires --mm and a nonempty path");
    boolean inspector = Arrays.asList(args).contains("--inspector");
    config.setWindowedMode(mm || voxels || physics3d ? 1280 : inspector ? 1000 : 1100, inspector ? 680 : 720);
    config.useVsync(true);
    config.setForegroundFPS(60);
    config.disableAudio(!(mm || Arrays.asList(args).contains("--runtime")));
    boolean smoke = Arrays.asList(args).contains("--smoke");
    var application =
        environment
            ? new EnvironmentDemo(smoke)
            : assetStreaming
            ? new AssetStreamingDemo(smoke)
            : gorzow
            ? new GorzowDemo(smoke)
            : maplibre
                ? new MapLibreDemo(smoke)
                : physics3d
                    ? new Physics3DDemo(smoke)
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
                                                    : new ModernDemo(
                                                        smoke,
                                                        inspector || sceneExport != null,
                                                        sceneExport == null ? null : SceneJson.standard(),
                                                        sceneExport);
    if (application instanceof ModernDemo demo)
      config.setWindowListener(
          new Lwjgl3WindowAdapter() {
            @Override
            public void focusLost() {
              demo.onFocusLost();
            }
          });
    if (application instanceof Physics3DDemo demo)
      config.setWindowListener(
          new Lwjgl3WindowAdapter() {
            @Override
            public void focusLost() {
              demo.onFocusLost();
            }
          });
    new Lwjgl3Application(application, config);
  }
}
