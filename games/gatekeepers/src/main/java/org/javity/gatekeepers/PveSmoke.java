package org.javity.gatekeepers;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.ScreenUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Two complete PvE runs driven through keyboard/Stage input. Never teleports or edits rewards/HP.
 */
final class PveSmoke implements GameSmoke {
  private final GatekeepersApplication app;
  private final Path output;
  private final List<String> checks = new ArrayList<>();
  private final Set<Integer> pressed = new HashSet<>();
  private int frame, phase, rounds, lastWave, pauseFrame;
  private String capture;
  private boolean dodged;
  private PlayerProfile.Snapshot paused;

  PveSmoke(GatekeepersApplication app, Path output) {
    this.app = app;
    this.output = output;
  }

  private void check(boolean result, String message) {
    if (!result)
      throw new AssertionError("PVE: " + message + " (phase " + phase + ", frame " + frame + ")");
    checks.add("PASS: " + message);
  }

  private void key(int code, boolean down) {
    if (down && (pressed.add(code) || !app.held.contains(code))) app.input.keyDown(code);
    else if (!down && pressed.remove(code)) app.input.keyUp(code);
  }

  private void tap(int code) {
    app.input.keyDown(code);
    app.input.keyUp(code);
  }

  private void stop() {
    for (int code : new ArrayList<>(pressed)) key(code, false);
  }

  private boolean walk(float x, float y, float tolerance) {
    var p = app.world.player().transform.position;
    if (p.dst2(x, y) < tolerance * tolerance) {
      stop();
      return true;
    }
    key(Input.Keys.D, x - p.x > 7);
    key(Input.Keys.A, x - p.x < -7);
    key(Input.Keys.W, y - p.y > 7);
    key(Input.Keys.S, y - p.y < -7);
    return false;
  }

  public void beforeFrame() {
    frame++;
    var w = app.world;
    var p = w.profile;
    if (frame < 30) return; // Let the initial native focus notification settle before holding keys.
    if (frame % 500 == 0)
      System.out.println(
          "[PvE walk] frame="
              + frame
              + " phase="
              + phase
              + " hero="
              + w.player().transform.position
              + " held="
              + app.held
              + " wave="
              + w.arenaWave()
              + " alive="
              + w.arenaRemaining());
    if (frame > 10000)
      throw new AssertionError(
          "PvE walk timed out: phase "
              + phase
              + ", hero "
              + w.player().transform.position
              + ", hp "
              + p.hp()
              + ", arena "
              + w.arenaState());
    switch (phase) {
      case 0 -> {
        if (walk(RpgContent.ARENA_GATE_X + 20, RpgContent.ARENA_GATE_Y, 20)) {
          tap(Input.Keys.E);
          check(w.inArena(), "Walked from village spawn to actual arena entrance");
          capture = rounds == 0 ? "01-arena-gotowa" : "07-druga-proba";
          phase = 1;
        }
      }
      case 1 -> {
        tap(Input.Keys.SHIFT_LEFT);
        check(w.dodgeSeconds() > 0, "Shift activates actual dodge action");
        dodged = true;
        tap(Input.Keys.E);
        check(
            w.arenaState() == RpgWorld.ArenaState.FIGHTING, "E starts repeatable arena from READY");
        lastWave = 0;
        phase = 2;
      }
      case 2 -> {
        checkStillInArena();
        if (w.arenaState() == RpgWorld.ArenaState.VICTORY) {
          stop();
          capture = rounds == 0 ? "05-zwyciestwo" : "08-drugie-zwyciestwo";
          phase = 3;
          break;
        }
        if (w.arenaState() == RpgWorld.ArenaState.RESTING) {
          stop();
          break;
        }
        if (w.arenaWave() != lastWave) {
          lastWave = w.arenaWave();
          check(lastWave <= 3, "Arena advanced to wave " + lastWave);
          if (rounds == 0) capture = "02-fala-" + lastWave;
        }
        var point = w.player().transform.position;
        var target =
            w.arenaEnemies().stream()
                .filter(e -> !e.dead())
                .min(Comparator.comparingDouble(e -> e.getTransform().position.dst2(point)))
                .orElse(null);
        if (target != null) {
          var t = target.getTransform().position;
          walk(t.x, t.y, 75);
          key(Input.Keys.SPACE, true);
          var screen = app.camera.worldToScreen(t.x, t.y);
          if (!app.hud.blocksWorldAt((int) screen.x, (int) screen.y)) {
            app.input.touchDown((int) screen.x, (int) screen.y, 0, Input.Buttons.LEFT);
            app.input.touchUp((int) screen.x, (int) screen.y, 0, Input.Buttons.LEFT);
          }
          if (!dodged && target.windup() > 0 && point.dst(t) < 100) {
            tap(Input.Keys.SHIFT_LEFT);
            dodged = true;
          }
        }
        if (p.hp() < p.maxHp() - 10 && p.bread() > 0) tap(Input.Keys.NUM_1);
      }
      case 3 -> {
        int coins = p.coins();
        tap(Input.Keys.E);
        rounds++;
        check(
            !w.inArena() && p.arenaWins() == rounds,
            "Victory reward collected once and returned to village");
        check(p.coins() == coins + 60, "Arena payout is exactly sixty coins");
        phase = rounds == 1 ? 4 : 7;
      }
      case 4 -> {
        if (walk(RpgContent.SHOP_X, RpgContent.SHOP_Y - 40, 20)) {
          tap(Input.Keys.E);
          check(app.hud.menu() == RpgHud.Menu.SHOP, "Walked back to merchant after PvE run");
          phase = 5;
        }
      }
      case 5 -> {
        int coins = p.coins();
        click("Ulepsz miecz");
        check(
            p.weaponLevel() == 2 && p.coins() == coins - 60,
            "Earned arena money purchases permanent level-two sword");
        capture = "06-ulepszenie-miecza";
        phase = 6;
      }
      case 6 -> {
        click("Zamknij");
        phase = 0;
      }
      case 7 -> {
        if (walk(RpgContent.BOARD_X, RpgContent.BOARD_Y - 40, 20)) {
          int coins = p.coins();
          tap(Input.Keys.E);
          check(
              p.coins() == coins + 8, "Arena kills also complete repeatable hunt at village board");
          phase = 8;
        }
      }
      case 8 -> {
        if (walk(RpgContent.SHOP_X, RpgContent.SHOP_Y - 40, 20)) {
          tap(Input.Keys.E);
          phase = 9;
        }
      }
      case 9 -> {
        int coins = p.coins();
        click("Ulepsz miecz");
        check(
            p.weaponLevel() == 3 && p.coins() == coins - 120,
            "Second run buys final sword upgrade at increased price");
        click("Chleb");
        capture = "09-maksymalny-miecz";
        phase = 10;
      }
      case 10 -> {
        click("Zamknij");
        tap(Input.Keys.I);
        check(
            app.hud.menu() == RpgHud.Menu.INVENTORY, "Inventory opens after full repeatable loop");
        capture = "10-ekwipunek";
        phase = 11;
      }
      case 11 -> {
        click("Zamknij");
        tap(Input.Keys.ESCAPE);
        paused = p.snapshot();
        pauseFrame = frame;
        phase = 12;
      }
      case 12 -> {
        if (frame - pauseFrame > 30) {
          check(paused.equals(p.snapshot()), "Pause freezes hunger, health and buff clocks");
          tap(Input.Keys.ESCAPE);
          phase = 13;
        }
      }
      case 13 -> {
        finish();
        phase = 14;
      }
    }
  }

  private void checkStillInArena() {
    if (!app.world.inArena())
      throw new AssertionError(
          "PvE autopilot died before completing run, hp=" + app.world.profile.hp());
  }

  private void click(String prefix) {
    for (var actor : app.hud.stage.getActors())
      if (actor instanceof com.badlogic.gdx.scenes.scene2d.utils.Layout l) l.validate();
    var button = find(app.hud.stage.getRoot(), prefix);
    if (button == null) throw new AssertionError("Missing native button " + prefix);
    var point =
        button.localToStageCoordinates(new Vector2(button.getWidth() / 2, button.getHeight() / 2));
    app.hud.stage.stageToScreenCoordinates(point);
    app.input.touchDown((int) point.x, (int) point.y, 0, Input.Buttons.LEFT);
    app.input.touchUp((int) point.x, (int) point.y, 0, Input.Buttons.LEFT);
  }

  private TextButton find(Group group, String prefix) {
    for (var a : group.getChildren()) {
      if (a instanceof TextButton b && b.getText().toString().startsWith(prefix)) return b;
      if (a instanceof Group g) {
        var b = find(g, prefix);
        if (b != null) return b;
      }
    }
    return null;
  }

  public void afterFrame() {
    if (capture == null) return;
    try {
      Files.createDirectories(output);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    var pixmap =
        ScreenUtils.getFrameBufferPixmap(
            0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    try {
      PixmapIO.writePNG(
          Gdx.files.absolute(output.resolve(capture + ".png").toAbsolutePath().toString()),
          pixmap,
          6,
          true);
    } finally {
      pixmap.dispose();
    }
    capture = null;
  }

  private void finish() {
    stop();
    try {
      Files.createDirectories(output);
      Path path = output.resolve("profile-v2.properties");
      ProfileStore.save(path, app.world.profile);
      check(
          ProfileStore.load(path).snapshot().equals(app.world.profile.snapshot()),
          "Sword upgrades, arena wins and inventory survive save/load");
      check(
          app.world.hits > 20,
          "Native input produced actual melee hits, not assigned enemy health");
      Files.writeString(
          output.resolve("verification.txt"),
          "PVE FULL LOOP PASS\n"
              + String.join("\n", checks)
              + "\nFrames: "
              + frame
              + "\nArena wins: "
              + rounds
              + "\nRenderer: "
              + Gdx.gl.glGetString(GL20.GL_RENDERER)
              + "\n");
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    System.out.println(
        "PVE FULL LOOP PASS: "
            + checks.size()
            + " checks, "
            + frame
            + " frames, "
            + rounds
            + " arena wins; "
            + output);
    Gdx.app.exit();
  }
}
