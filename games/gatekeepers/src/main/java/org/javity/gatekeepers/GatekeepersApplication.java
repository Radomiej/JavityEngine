package org.javity.gatekeepers;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Vector2;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.javity.core.GameObject;
import org.javity.render.*;
import org.javity.ui.flex.UiTheme;

/** Native desktop host; input, simulation, rendering and save lifetimes are explicit. */
public final class GatekeepersApplication extends ApplicationAdapter {
  private final Path savePath, captures;
  private final boolean smokeMode;
  private final boolean pveSmokeMode;
  private boolean saveEnabled = true, mouseHeld;
  private float saveClock;
  final Set<Integer> held = new HashSet<>();
  RpgWorld world;
  RpgHud hud;
  RpgArt art;
  Camera2D camera;
  GameObject cameraObject;
  InputMultiplexer input;
  private GdxRenderer renderer;
  private UiTheme worldTheme;
  private GameSmoke smoke;
  private boolean disposed;

  GatekeepersApplication(Path savePath, boolean smokeMode, Path captures) {
    this(savePath, smokeMode, captures, false);
  }

  GatekeepersApplication(Path savePath, boolean smokeMode, Path captures, boolean pveSmokeMode) {
    this.savePath = savePath;
    this.smokeMode = smokeMode;
    this.captures = captures;
    this.pveSmokeMode = pveSmokeMode;
  }

  @Override
  public void create() {
    PlayerProfile profile;
    String loadWarning = null;
    try {
      profile = smokeMode ? new PlayerProfile() : ProfileStore.load(savePath);
    } catch (IOException bad) {
      profile = new PlayerProfile();
      saveEnabled = false;
      loadWarning = "BŁĄD ZAPISU: uszkodzony plik zachowano. Ta sesja nie nadpisze go.";
    }
    world = new RpgWorld(profile);
    renderer = new GdxRenderer();
    worldTheme = new UiTheme();
    cameraObject =
        world.createObject("camera").setPosition(RpgContent.SPAWN_X + 80, RpgContent.SPAWN_Y);
    camera = cameraObject.addComponent(new Camera2D());
    camera.setZoom(.9f);
    art = new RpgArt(world, worldTheme.getFont());
    hud =
        new RpgHud(
            world,
            art,
            camera,
            this::buy,
            this::eat,
            () -> show(RpgHud.Menu.NONE),
            this::upgradeWeapon);
    if (loadWarning != null) {
      world.say(loadWarning);
      hud.saveStatus = loadWarning;
    }
    input =
        new InputMultiplexer(
            hud.stage,
            new InputAdapter() {
              @Override
              public boolean keyDown(int key) {
                if (key == Input.Keys.ESCAPE) {
                  show(hud.menu() == RpgHud.Menu.NONE ? RpgHud.Menu.PAUSE : RpgHud.Menu.NONE);
                  return true;
                }
                if (hud.menu() != RpgHud.Menu.NONE) return false;
                if (hud.stage.getKeyboardFocus() != null) return false;
                held.add(key);
                if (key == Input.Keys.E) {
                  interact();
                  return true;
                }
                if (key == Input.Keys.NUM_1) {
                  eat(PlayerProfile.Food.BREAD);
                  return true;
                }
                if (key == Input.Keys.NUM_2) {
                  eat(PlayerProfile.Food.APPLE);
                  return true;
                }
                if (key == Input.Keys.I) {
                  show(RpgHud.Menu.INVENTORY);
                  return true;
                }
                if (key == Input.Keys.SHIFT_LEFT || key == Input.Keys.SHIFT_RIGHT) {
                  world.dodge();
                  return true;
                }
                if (key == Input.Keys.R && world.inArena()) {
                  world.leaveArena();
                  held.clear();
                  return true;
                }
                return key == Input.Keys.SPACE || isMovement(key);
              }

              @Override
              public boolean keyUp(int key) {
                return held.remove(key);
              }

              @Override
              public boolean touchDown(int x, int y, int pointer, int button) {
                if (button != Input.Buttons.LEFT || hud.blocksWorldAt(x, y)) return false;
                hud.stage.setKeyboardFocus(null);
                mouseHeld = true;
                attackAt(x, y);
                return true;
              }

              @Override
              public boolean touchUp(int x, int y, int pointer, int button) {
                if (button == Input.Buttons.LEFT) {
                  mouseHeld = false;
                  return true;
                }
                return false;
              }
            });
    Gdx.input.setInputProcessor(input);
    world.start();
    resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    if (smokeMode)
      smoke = pveSmokeMode ? new PveSmoke(this, captures) : new RpgSmoke(this, captures);
  }

  private static boolean isMovement(int k) {
    return k == Input.Keys.W
        || k == Input.Keys.A
        || k == Input.Keys.S
        || k == Input.Keys.D
        || k == Input.Keys.UP
        || k == Input.Keys.DOWN
        || k == Input.Keys.LEFT
        || k == Input.Keys.RIGHT;
  }

  void show(RpgHud.Menu menu) {
    held.clear();
    mouseHeld = false;
    world.move(0, 0);
    hud.show(menu);
    if (menu == RpgHud.Menu.NONE) world.resume();
    else world.pause();
  }

  void interact() {
    if (world.nearShop()) show(RpgHud.Menu.SHOP);
    else if (world.nearClasses()) show(RpgHud.Menu.CLASSES);
    else world.interact();
  }

  void buy(PlayerProfile.Food food) {
    if (!world.nearShop()) {
      world.say("Podejdź do kupca, żeby zrobić zakupy.");
      return;
    }
    world.say(
        world.profile.buy(food)
            ? "Kupiono "
                + (food == PlayerProfile.Food.BREAD ? "chleb" : "złote jabłko")
                + ". Przedmiot jest w plecaku."
            : "Za mało monet albo pełny plecak.");
  }

  void eat(PlayerProfile.Food food) {
    world.say(
        world.profile.eat(food)
            ? food == PlayerProfile.Food.BREAD
                ? "Chleb: +8 HP, +20 głodu."
                : "Złote jabłko: leczenie i Siła I przez 30 sekund."
            : "Nie masz tego przedmiotu w plecaku.");
  }

  void upgradeWeapon() {
    if (!world.nearShop()) return;
    world.say(
        world.profile.upgradeWeapon()
            ? "Miecz ulepszony! Obrażenia: "
                + world.profile.weaponDamage()
                + ". Wracaj na arenę po kolejną nagrodę."
            : "Za mało monet albo miecz jest już ulepszony do maksimum.");
  }

  private void attackAt(int x, int y) {
    Vector2 point = camera.screenToWorld(x, y).sub(world.player().transform.position);
    world.attack(point.x, point.y);
  }

  public void focusLost() {
    held.clear();
    mouseHeld = false;
    if (world != null) world.move(0, 0);
  }

  @Override
  public void pause() {
    focusLost();
  }

  @Override
  public void render() {
    float delta = smokeMode ? 1f / 60f : Math.min(Gdx.graphics.getDeltaTime(), .1f);
    if (smoke != null) smoke.beforeFrame();
    if (hud.menu() == RpgHud.Menu.NONE) {
      float x =
          (held.contains(Input.Keys.D) || held.contains(Input.Keys.RIGHT) ? 1 : 0)
              - (held.contains(Input.Keys.A) || held.contains(Input.Keys.LEFT) ? 1 : 0);
      float y =
          (held.contains(Input.Keys.W) || held.contains(Input.Keys.UP) ? 1 : 0)
              - (held.contains(Input.Keys.S) || held.contains(Input.Keys.DOWN) ? 1 : 0);
      world.move(x, y);
      if (held.contains(Input.Keys.SPACE)) world.attack(world.facing.x, world.facing.y);
      if (mouseHeld && !hud.blocksWorldAt(Gdx.input.getX(), Gdx.input.getY()))
        attackAt(Gdx.input.getX(), Gdx.input.getY());
    }
    world.step(delta);
    // Clamp the camera inside the authored terrain; reserve breathing room for the quest journal.
    float zoom = camera.getZoom(),
        halfW = Gdx.graphics.getWidth() * zoom / 2,
        halfH = Gdx.graphics.getHeight() * zoom / 2;
    var player = world.player().transform.position;
    float tx = Math.max(halfW, Math.min(RpgContent.WIDTH - halfW, player.x + 90));
    float ty = Math.max(halfH, Math.min(RpgContent.HEIGHT - halfH, player.y + 20));
    if (world.inArena()) {
      tx = RpgContent.ARENA_X + 80;
      ty = RpgContent.ARENA_Y + 30;
    }
    var cp = cameraObject.transform.position;
    cp.lerp(new Vector2(tx, ty), smokeMode ? 1 : Math.min(1, delta * 6));
    art.update(world.isPaused() ? 0 : delta);
    Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    Gdx.gl.glClearColor(.08f, .16f, .12f, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    renderer.render(world);
    hud.draw(delta);
    if (!smokeMode && saveEnabled) {
      saveClock += delta;
      if (saveClock >= 10) {
        saveClock = 0;
        save();
      }
    }
    if (smoke != null) smoke.afterFrame();
  }

  @Override
  public void resize(int width, int height) {
    if (width <= 0 || height <= 0 || camera == null) return;
    camera.resize(width, height);
    renderer.resize(width, height);
    hud.resize(width, height);
  }

  private void save() {
    try {
      ProfileStore.save(savePath, world.profile);
    } catch (IOException failure) {
      hud.saveStatus = "BŁĄD ZAPISU";
      world.say("BŁĄD ZAPISU: nie udało się zapisać postępu. Sprawdź dostęp do katalogu.");
    }
  }

  @Override
  public void dispose() {
    if (disposed) return;
    disposed = true;
    focusLost();
    if (!smokeMode && saveEnabled && world != null) save();
    Gdx.input.setInputProcessor(null);
    if (world != null) world.close();
    if (hud != null) hud.close();
    if (art != null) art.close();
    if (worldTheme != null) worldTheme.dispose();
    if (renderer != null) renderer.dispose();
  }
}
