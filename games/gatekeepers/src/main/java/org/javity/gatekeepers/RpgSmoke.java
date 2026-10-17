package org.javity.gatekeepers;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.ScreenUtils;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic native acceptance walk: real Game updates, Stage input and framebuffer captures.
 */
final class RpgSmoke implements GameSmoke {
  private final GatekeepersApplication app;
  private final Path output;
  private final List<String> checks = new ArrayList<>();
  private int frame, phase, targetIndex;
  private RpgWorld.Enemy target;
  private int beforeShop;
  private boolean combatCaptured, bossCaptured;

  RpgSmoke(GatekeepersApplication app, Path output) {
    this.app = app;
    this.output = output;
  }

  private void check(boolean condition, String description) {
    if (!condition) throw new AssertionError("RPG SMOKE: " + description);
    checks.add("PASS: " + description);
  }

  public void beforeFrame() {
    frame++;
    var w = app.world;
    if (frame > 4000) throw new AssertionError("RPG smoke timed out, phase " + phase);
    if (frame == 1)
      check(
          w.inSanctuary() && w.profile.coins() == 30, "Fresh profile starts in protected village");
    if (frame == 10) {
      Gdx.graphics.setWindowedMode(1920, 1080);
      app.resize(1920, 1080);
    }
    if (frame == 16) {
      Gdx.graphics.setWindowedMode(1440, 900);
      app.resize(1440, 900);
    }
    if (frame == 29) app.input.keyDown(Input.Keys.D);
    if (frame == 32) {
      check(
          w.player().transform.position.x > RpgContent.SPAWN_X + 5,
          "Native keyboard input moves actual character controller");
      app.input.keyUp(Input.Keys.D);
      w.player().setPosition(RpgContent.SHOP_X, RpgContent.SHOP_Y);
      app.input.keyDown(Input.Keys.E);
    }
    if (frame == 38) {
      check(app.hud.menu() == RpgHud.Menu.SHOP, "E opens actual native merchant menu");
      beforeShop = w.profile.coins();
      clickButton("Chleb");
      check(
          w.profile.coins() == beforeShop - 4 && w.profile.bread() == 4,
          "Stage button purchases bread for four coins");
      clickButton("Złote jabłko");
      check(
          w.profile.coins() == beforeShop - 4 && w.profile.apples() == 0,
          "Native merchant rejects unaffordable apple without charging");
      int hits = w.hits;
      app.input.touchDown(35, 30, 0, Input.Buttons.LEFT);
      app.input.touchUp(35, 30, 0, Input.Buttons.LEFT);
      check(w.hits == hits, "Clicking HUD while merchant is open cannot attack world");
    }
    if (frame == 45) {
      clickButton("Zamknij");
      check(app.hud.menu() == RpgHud.Menu.NONE, "Native close button resumes world");
      w.profile.damage(20);
      app.input.keyDown(Input.Keys.NUM_1);
      app.input.keyUp(Input.Keys.NUM_1);
      check(
          w.profile.hp() == 88 && w.profile.bread() == 3,
          "Food shortcut restores health and consumes one item");
      app.input.keyDown(Input.Keys.D);
      app.focusLost();
      check(app.held.isEmpty(), "Focus loss releases movement");
      app.input.keyDown(Input.Keys.ESCAPE);
    }
    if (frame == 52) {
      check(w.isPaused(), "Escape pauses simulation");
      app.input.keyDown(Input.Keys.ESCAPE);
      w.player().setPosition(RpgContent.PORTAL_X, RpgContent.PORTAL_Y);
      app.input.keyDown(Input.Keys.E);
      check(!w.atFrontier(), "Portal rejects entry before boss victory");
    }
    if (frame == 62) {
      phase = 1;
      nextSlime();
    }
    if (phase == 1 && target != null && target.dead()) {
      targetIndex++;
      if (targetIndex < 6) nextSlime();
      else {
        app.input.keyUp(Input.Keys.SPACE);
        check(w.profile.huntKills() == 6, "Six actual combat kills complete repeatable hunt");
        phase = 2;
      }
    }
    if (phase == 3) {
      w.player().setPosition(RpgContent.BOARD_X, RpgContent.BOARD_Y);
      int coins = w.profile.coins();
      app.input.keyDown(Input.Keys.E);
      check(
          w.profile.huntKills() == 0 && w.profile.coins() == coins + 8,
          "Board pays quest once and resets repeatable counter");
      app.input.keyDown(Input.Keys.E);
      check(w.profile.coins() == coins + 8, "Repeated interaction cannot duplicate reward");
      phase = 4;
    }
    if (phase == 5) {
      target = w.boss();
      w.player()
          .setPosition(target.getTransform().position.x - 85, target.getTransform().position.y);
      w.facing.set(1, 0);
      app.input.keyDown(Input.Keys.SPACE);
      phase = 6;
    }
    if (phase == 6) {
      if (w.profile.hp() < 45 && w.profile.bread() > 0) {
        app.input.keyDown(Input.Keys.NUM_1);
        app.input.keyUp(Input.Keys.NUM_1);
      }
      if (target.dead()) {
        app.input.keyUp(Input.Keys.SPACE);
        check(w.profile.bossDefeated(), "Boss defeated using real attack cooldown and damage");
        phase = 7;
      } else {
        // Circle away from the telegraphed jump, then approach again. This uses actual world
        // positions
        // as a deterministic test setup; boss health/rewards are never assigned by the smoke.
        if (target.windup() > 0)
          w.player()
              .setPosition(
                  target.getTransform().position.x - 120, target.getTransform().position.y - 130);
        else {
          w.player()
              .setPosition(target.getTransform().position.x - 82, target.getTransform().position.y);
          w.facing.set(1, 0);
        }
      }
    }
    if (phase == 9) {
      w.player().setPosition(RpgContent.PORTAL_X - 100, RpgContent.PORTAL_Y);
      phase = 10;
    }
    if (phase == 11) {
      app.input.keyDown(Input.Keys.E);
      check(w.inArena(), "Unlocked boss portal reaches PvE arena");
      phase = 12;
    }
    if (phase == 13) {
      Gdx.graphics.setWindowedMode(1280, 720);
      app.resize(1280, 720);
      phase = 14;
    }
    if (phase == 15) {
      app.input.keyDown(Input.Keys.R);
      check(!w.inArena(), "Arena offers working return interaction");
      w.player().setPosition(RpgContent.BOARD_X, RpgContent.BOARD_Y);
      app.input.keyDown(Input.Keys.E);
      app.input.keyDown(Input.Keys.E);
      check(w.profile.bossClaimed(), "Boss quest reward can be collected at board");
      w.player().setPosition(RpgContent.SHOP_X, RpgContent.SHOP_Y);
      app.input.keyDown(Input.Keys.E);
      int coins = w.profile.coins();
      clickButton("Złote jabłko");
      check(
          w.profile.coins() == coins - 100 && w.profile.apples() == 1,
          "Earned money buys special apple through native merchant");
      clickButton("Zamknij");
      app.input.keyDown(Input.Keys.NUM_2);
      app.input.keyUp(Input.Keys.NUM_2);
      check(
          w.profile.damageMultiplier() > 1 && w.profile.apples() == 0,
          "Special apple shortcut activates strength buff");
      phase = 16;
    }
  }

  private void nextSlime() {
    target = app.world.enemies().get(targetIndex);
    app.world
        .player()
        .setPosition(target.getTransform().position.x - 80, target.getTransform().position.y);
    app.world.facing.set(1, 0);
    app.input.keyDown(Input.Keys.SPACE);
  }

  private void clickButton(String prefix) {
    for (Actor actor : app.hud.stage.getActors())
      if (actor instanceof com.badlogic.gdx.scenes.scene2d.utils.Layout layout) layout.validate();
    TextButton button = find(app.hud.stage.getRoot(), prefix);
    if (button == null) throw new AssertionError("Missing native button " + prefix);
    Vector2 screen =
        button.localToStageCoordinates(new Vector2(button.getWidth() / 2, button.getHeight() / 2));
    app.hud.stage.stageToScreenCoordinates(screen);
    app.input.touchDown(Math.round(screen.x), Math.round(screen.y), 0, Input.Buttons.LEFT);
    app.input.touchUp(Math.round(screen.x), Math.round(screen.y), 0, Input.Buttons.LEFT);
  }

  private TextButton find(Group parent, String prefix) {
    for (Actor actor : parent.getChildren()) {
      if (actor instanceof TextButton button && button.getText().toString().startsWith(prefix))
        return button;
      if (actor instanceof Group group) {
        var found = find(group, prefix);
        if (found != null) return found;
      }
    }
    return null;
  }

  public void afterFrame() {
    if (frame == 14) capture("00-resize-1920");
    if (frame == 28) capture("01-osada");
    if (frame == 40) capture("02-kupiec");
    if (phase == 1 && targetIndex == 2 && app.world.slashSeconds() > 0 && !combatCaptured) {
      capture("03-walka");
      combatCaptured = true;
    }
    if (phase == 2) {
      capture("04-misja");
      phase = 3;
    }
    if (phase == 4) {
      capture("05-nagroda");
      phase = 5;
    }
    if (phase == 6 && app.world.boss().hp() < 400 && app.world.boss().hp() > 350 && !bossCaptured) {
      capture("06-boss");
      bossCaptured = true;
    }
    if (phase == 7) {
      capture("07-boss-pokonany");
      phase = 9;
    }
    if (phase == 10) {
      capture("08-portal");
      phase = 11;
    }
    if (phase == 12) {
      capture("09-ostepy");
      phase = 13;
    }
    if (phase == 14) {
      capture("10-resize-1280");
      phase = 15;
    }
    if (phase == 16) {
      capture("11-zlote-jablko");
      try {
        Path save = output.resolve("smoke-profile.properties");
        ProfileStore.save(save, app.world.profile);
        check(ProfileStore.load(save).bossDefeated(), "Actual progress survives disk save/load");
        Files.writeString(
            output.resolve("verification.txt"),
            "Base: 95b951c249d59d2c7285701e6e2610933c71b75d\n"
                + "Renderer: "
                + Gdx.gl.glGetString(com.badlogic.gdx.graphics.GL20.GL_RENDERER)
                + "\n"
                + String.join("\n", checks)
                + "\nFrames: "
                + frame
                + "\n");
      } catch (IOException failure) {
        throw new IllegalStateException(failure);
      }
      System.out.println(
          "GATEKEEPERS SMOKE PASS: "
              + checks.size()
              + " checks, "
              + frame
              + " frames; captures "
              + output);
      phase = 17;
      Gdx.app.exit();
    }
  }

  private void capture(String name) {
    try {
      Files.createDirectories(output);
    } catch (IOException failure) {
      throw new IllegalStateException(failure);
    }
    Pixmap frame =
        ScreenUtils.getFrameBufferPixmap(
            0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    try {
      int distinct = 0;
      int previous = 0;
      for (int y = 0; y < frame.getHeight(); y += 17)
        for (int x = 0; x < frame.getWidth(); x += 17) {
          int current = frame.getPixel(x, y);
          if (current != previous) distinct++;
          previous = current;
        }
      check(distinct > 300, "Nonempty native framebuffer: " + name);
      PixmapIO.writePNG(
          Gdx.files.absolute(output.resolve(name + ".png").toAbsolutePath().toString()),
          frame,
          6,
          true);
    } finally {
      frame.dispose();
    }
  }
}
