package org.javity.wasm;

import com.github.xpenatan.gdx.teavm.backends.web.WebApplication;
import com.github.xpenatan.gdx.teavm.backends.web.WebApplicationConfiguration;

public final class WebLauncher {
  public static void main(String[] args) {
    WebApplicationConfiguration config = new WebApplicationConfiguration();
    config.width = 0;
    config.height = 0;
    config.useGL30 = true;
    new WebApplication(new JavityWasmDemo(), config);
  }
}
