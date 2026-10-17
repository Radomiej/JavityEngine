package org.javity.maplibre;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import java.util.Locale;

/** Explicit desktop bootstrap for the optional system-EGL MapLibre renderer. */
public final class MapLibreDesktop {
  private MapLibreDesktop() {}

  /** Call before creating Lwjgl3Application. Requires system EGL and GLES libraries. */
  public static void configure(Lwjgl3ApplicationConfiguration config) {
    String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    String arch = System.getProperty("os.arch");
    if (!os.contains("linux") || !(arch.equals("amd64") || arch.equals("x86_64"))) {
      throw new UnsupportedOperationException(
          "MapLibre desktop currently supports Linux x64 EGL only");
    }
    // The enum selects EGL + the official GLES adapter. The optional module omits
    // ANGLELoader so GLFW and Native both use the host's system EGL implementation.
    config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.ANGLE_GLES20, 2, 0);
  }
}
