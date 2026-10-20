package org.javity.demo;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

public final class GltfDemoLauncher {
  private GltfDemoLauncher() {}

  public static void main(String[] args) {
    Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
    config.setTitle("Javity glTF | static + independent skins | Space light | Esc exit");
    config.setWindowedMode(960, 640);
    config.disableAudio(true);
    new Lwjgl3Application(new GltfDemo(false), config);
  }
}
