package org.javity.teavm;

import org.teavm.jso.JSBody;

/** Narrow Java-to-host callbacks; actual browser packages stay on the JS side. */
final class BrowserBridge {
  private BrowserBridge() {}

  @JSBody(script = "globalThis.javityHost.create()")
  static native void create();

  @JSBody(params = {"delta"}, script = "globalThis.javityHost.tick(delta)")
  static native void tick(float delta);

  @JSBody(params = {"key", "down"}, script = "globalThis.javityHost.input(key, down)")
  static native void input(int key, int down);

  @JSBody(params = {"width", "height"}, script = "globalThis.javityHost.resize(width, height)")
  static native void resize(int width, int height);

  @JSBody(script = "globalThis.javityHost.dispose()")
  static native void dispose();
}
