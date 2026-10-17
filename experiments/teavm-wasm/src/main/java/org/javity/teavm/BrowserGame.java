package org.javity.teavm;

import org.javity.core.Game;
import org.javity.core.GameObject;
import org.teavm.interop.Export;

/** Small exported lifecycle facade for the TeaVM WasmGC experiment. */
public final class BrowserGame {
  private static Game game;
  private static GameObject player;
  private static int resizeWidth;
  private static int resizeHeight;
  private static long inputEvents;

  private BrowserGame() {}

  public static void main(String[] args) {
    create();
  }

  @Export(name = "create")
  public static void create() {
    dispose();
    game = new Game();
    player = game.createObject("wasm-spike-player").setPosition(1, 2);
    game.start();
    BrowserBridge.create();
  }

  @Export(name = "tick")
  public static void tick(float delta) {
    if (game != null) {
      game.step(delta);
      BrowserBridge.tick(delta);
    }
  }

  @Export(name = "input")
  public static void input(int keyCode, int down) {
    if (game != null && player != null) {
      inputEvents++;
      player.setActive(down != 0);
      BrowserBridge.input(keyCode, down);
    }
  }

  @Export(name = "frame")
  public static int frame() {
    return game == null ? -1 : (int) game.getFrameIndex();
  }

  @Export(name = "resize")
  public static void resize(int width, int height) {
    resizeWidth = width;
    resizeHeight = height;
    BrowserBridge.resize(width, height);
  }

  @Export(name = "state")
  public static int state() {
    return (game == null ? 0 : 1)
        | ((player != null && player.isActive()) ? 2 : 0)
        | ((resizeWidth > 0 && resizeHeight > 0) ? 4 : 0)
        | ((inputEvents > 0) ? 8 : 0);
  }

  @Export(name = "dispose")
  public static void dispose() {
    if (game != null) {
      game.dispose();
      BrowserBridge.dispose();
    }
    game = null;
    player = null;
    resizeWidth = resizeHeight = 0;
    inputEvents = 0;
  }
}
