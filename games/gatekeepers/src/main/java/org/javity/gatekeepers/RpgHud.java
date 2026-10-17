package org.javity.gatekeepers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import java.util.*;
import java.util.function.Consumer;
import org.javity.render.Camera2D;
import org.javity.ui.controls.Minimap;
import org.javity.ui.controls.MinimapModel;
import org.javity.ui.controls.ProgressBar;
import org.javity.ui.flex.*;

/** Native Scene2D HUD; modal menus use real Javity Yoga layout and buttons. */
public final class RpgHud implements AutoCloseable {
  public enum Menu {
    NONE,
    SHOP,
    CLASSES,
    INVENTORY,
    PAUSE
  }

  public final Stage stage = new Stage(new ScreenViewport());
  public final UiTheme theme = new UiTheme();
  private final UiTheme tinyTheme = font(12), largeTheme = font(24);
  private final Texture pixel;
  private final RpgWorld world;
  private final RpgArt art;
  private final Camera2D camera;
  private final Consumer<PlayerProfile.Food> buy, eat;
  private final Runnable closeMenu;
  private final Map<String, Color> colors = new HashMap<>();
  private Flex modal;
  private Label moneyLabel, stockLabel;
  private Menu menu = Menu.NONE;
  private final Map<String, ProgressBar> meters = new LinkedHashMap<>();
  private final MinimapModel minimapModel =
      new MinimapModel(0, 0, RpgContent.WIDTH, RpgContent.HEIGHT);
  private final Minimap minimap;
  private TextButton upgradeButton;
  private final Runnable upgrade;
  private final Canvas canvas = new Canvas();
  public String saveStatus = "POSTĘP ZAPISYWANY AUTOMATYCZNIE";

  private static UiTheme font(int size) {
    return UiTheme.fromTrueType(
        Gdx.files.classpath("org/javity/ui/fonts/OpenSans-Regular.ttf"), size);
  }

  public RpgHud(
      RpgWorld world,
      RpgArt art,
      Camera2D camera,
      Consumer<PlayerProfile.Food> buy,
      Consumer<PlayerProfile.Food> eat,
      Runnable closeMenu,
      Runnable upgrade) {
    this.world = world;
    this.art = art;
    this.camera = camera;
    this.buy = buy;
    this.eat = eat;
    this.closeMenu = closeMenu;
    this.upgrade = upgrade;
    minimap = new Minimap(theme, minimapModel);
    minimap.setMap(art.map);
    minimapModel.put("shop", RpgContent.SHOP_X, RpgContent.SHOP_Y, 0xe8c36cff, 5);
    minimapModel.put("board", RpgContent.BOARD_X, RpgContent.BOARD_Y, 0xe8c36cff, 5);
    minimapModel.put("arena", RpgContent.ARENA_GATE_X, RpgContent.ARENA_GATE_Y, 0xc1a0e0ff, 5);
    Pixmap p = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
    p.setColor(Color.WHITE);
    p.fill();
    pixel = new Texture(p);
    p.dispose();
    canvas.setTouchable(Touchable.disabled);
    stage.addActor(canvas);
  }

  private Color color(String hex) {
    return colors.computeIfAbsent(hex, Color::valueOf);
  }

  public Menu menu() {
    return menu;
  }

  public boolean blocksWorldAt(int x, int y) {
    float w = stage.getWidth(), h = stage.getHeight();
    return menu != Menu.NONE
        || y < 80
        || (x < 300 && y < 320)
        || (x > w - 305 && y < 440)
        || (x > w - 265 && y > h - 285)
        || y > h - 105
        || (x < Math.min(w - 330, 850) && y > h - 146);
  }

  public void resize(int w, int h) {
    stage.getViewport().update(w, h, true);
    canvas.setSize(w, h);
    positionModal();
  }

  public void draw(float delta) {
    for (var meter : meters.values()) meter.act(delta);
    minimapModel.put(
        "hero",
        world.player().transform.position.x,
        world.player().transform.position.y,
        0xfff0b5ff,
        7);
    for (var enemy : world.enemies()) {
      String id = "mob-" + enemy.getGameObject().id;
      if (enemy.dead()) minimapModel.remove(id);
      else
        minimapModel.put(
            id,
            enemy.getTransform().position.x,
            enemy.getTransform().position.y,
            enemy.isBoss() ? 0xe9a15fff : 0xdb8277ff,
            enemy.isBoss() ? 5 : 3);
    }
    refreshMenu();
    stage.act(delta);
    stage.draw();
  }

  public void show(Menu value) {
    if (modal != null) {
      modal.remove();
      modal.close();
      modal = null;
    }
    moneyLabel = null;
    upgradeButton = null;
    stockLabel = null;
    menu = value;
    stage.setKeyboardFocus(null);
    if (value == Menu.NONE) return;
    modal = new Flex(theme, "flex-col p-6 gap-3 bg-[#12291f] rounded-lg border border-[#728464]");
    modal.setTouchable(Touchable.enabled);
    // A modal's whole rectangle consumes clicks, including gaps between controls.
    modal.addListener(
        new InputListener() {
          @Override
          public boolean touchDown(InputEvent e, float x, float y, int p, int b) {
            return true;
          }
        });
    String heading =
        switch (value) {
          case SHOP -> "Zapasy na wyprawę";
          case CLASSES -> "Gildia Strażników";
          case INVENTORY -> "Twój ekwipunek";
          case PAUSE -> "Chwila wytchnienia";
          default -> "";
        };
    modal.add(theme.label(heading, "text-2xl text-[#ebe7cd]"), "h-10");
    String subtitle =
        switch (value) {
          case SHOP -> "Jedzenie przywraca zdrowie i sytość.";
          case CLASSES -> "Wybierz swoją drogę.";
          case INVENTORY ->
              "Wojownik · Miecz "
                  + world.profile.weaponLevel()
                  + " / 3 · Obrażenia "
                  + world.profile.weaponDamage();
          case PAUSE -> "Świat jest zatrzymany. Twoje postępy są bezpieczne.";
          default -> "";
        };
    modal.add(theme.label(subtitle, "text-sm text-slate-400", true), "h-12 w-full");
    moneyLabel = modal.add(theme.label("", "text-[#e8cd84]"), "h-8");
    stockLabel = modal.add(theme.label("", "text-[#b6cda5]"), "h-8");
    if (value == Menu.SHOP) {
      button("Chleb  ·  +8 HP, +20 głodu  ·  4 monety", () -> buy.accept(PlayerProfile.Food.BREAD));
      button(
          "Złote jabłko  ·  leczenie + siła I  ·  100 monet",
          () -> buy.accept(PlayerProfile.Food.APPLE));
      upgradeButton =
          theme.button(
              "Ulepsz miecz",
              "text-[#e9e4c7] bg-[#314e3a] rounded border border-[#728464]",
              upgrade);
      modal.add(upgradeButton, "h-12 w-full");
    } else if (value == Menu.INVENTORY) {
      button("Zjedz chleb  ·  skrót 1", () -> eat.accept(PlayerProfile.Food.BREAD));
      button("Zjedz złote jabłko  ·  skrót 2", () -> eat.accept(PlayerProfile.Food.APPLE));
    } else if (value == Menu.CLASSES) {
      button("Wojownik  ·  darmowy, wybrany", () -> world.say("Wojownik gotowy do wyprawy."));
      modal.add(
          theme.label(
              "Łucznik i Strażnik — w kolejnych rozdziałach.", "text-sm text-slate-400", true),
          "h-12 w-full");
    } else {
      modal.add(
          theme.label(
              "WASD: ruch  ·  LPM / Spacja: atak\nE: interakcja  ·  1 / 2: jedzenie  ·  I: plecak",
              "text-slate-200",
              true),
          "h-16 w-full");
    }
    button(value == Menu.PAUSE ? "Wróć do wyprawy  ·  Esc" : "Zamknij  ·  Esc", closeMenu);
    stage.addActor(modal);
    positionModal();
    refreshMenu();
  }

  private void button(String text, Runnable action) {
    modal.add(
        theme.button(
            text,
            "text-[#e9e4c7] bg-[#314e3a] hover:bg-#44674a rounded border border-[#728464]",
            action),
        "h-12 w-full");
  }

  private void positionModal() {
    if (modal == null) return;
    float w = stage.getWidth(), h = stage.getHeight();
    float height = menu == Menu.SHOP ? 500 : 440;
    modal.setSize(Math.min(520, w - 48), height);
    modal.setPosition((w - modal.getWidth()) / 2, (h - height) / 2);
  }

  private void refreshMenu() {
    if (moneyLabel != null)
      moneyLabel.setText(
          "Sakiewka: "
              + world.profile.coins()
              + " monet  ·  Miecz "
              + world.profile.weaponLevel()
              + " / 3");
    if (upgradeButton != null) {
      upgradeButton.setText(
          world.profile.upgradePrice() == 0
              ? "Miecz ulepszony do maksimum"
              : "Ulepsz miecz  ·  +4 obrażeń  ·  " + world.profile.upgradePrice() + " monet");
      upgradeButton.setDisabled(world.profile.upgradePrice() == 0);
    }
    if (stockLabel != null)
      stockLabel.setText(
          "W plecaku: "
              + world.profile.bread()
              + " × chleb  ·  "
              + world.profile.apples()
              + " × złote jabłko");
  }

  private void box(Batch b, String c, float x, float y, float w, float h) {
    b.setColor(color(c));
    b.draw(pixel, x, y, w, h);
  }

  private void panel(Batch b, float x, float y, float w, float h) {
    box(b, "081b17bb", x + 4, y - 5, w, h);
    box(b, "52654c", x, y, w, h);
    box(b, "12291ff0", x + 1, y + 1, w - 2, h - 2);
    box(b, "8d997050", x + 1, y + h - 2, w - 2, 1);
  }

  private void text(Batch b, String s, float x, float y, String c) {
    text(b, theme.getFont(), s, x, y, c);
  }

  private void small(Batch b, String s, float x, float y, String c) {
    text(b, tinyTheme.getFont(), s, x, y, c);
  }

  private void text(Batch b, BitmapFont font, String s, float x, float y, String c) {
    font.setColor(color(c));
    font.draw(b, s, x, y);
  }

  private void icon(Batch b, TextureRegion region, float x, float y, float w, float h) {
    b.setColor(Color.WHITE);
    b.draw(region, x, y, w, h);
  }

  private void bar(
      Batch b, String id, float x, float y, float w, float h, float fraction, String fill) {
    var meter =
        meters.computeIfAbsent(
            id,
            key ->
                new ProgressBar(theme, "bg-[#071b16] text-[#" + fill + "] border border-[#354d37]")
                    .setDuration(.12f)
                    .setDelay(.15f));
    meter.setBounds(x, y, w, h);
    meter.setValue(fraction);
    meter.draw(b, 1);
  }

  private class Canvas extends Actor {
    @Override
    public void draw(Batch b, float alpha) {
      float w = stage.getWidth(), h = stage.getHeight();
      var p = world.profile;
      // World-space feedback is projected through the same actual Javity camera.
      for (var e : world.enemies())
        if (!e.dead() && !e.isBoss()) {
          var pos = e.getTransform().position;
          var s = camera.worldToScreen(pos.x, pos.y);
          if (s.x < 0 || s.x > w || s.y < 80 || s.y > h - 80) continue;
          if (e.hp() < e.maxHp() || world.near(pos.x, pos.y, 140)) {
            bar(
                b,
                "mob-" + e.getGameObject().id,
                s.x - 23,
                h - s.y + 28,
                46,
                5,
                e.hp() / (float) e.maxHp(),
                "b7cb7c");
            small(b, e.name(), s.x - 26, h - s.y + 47, "edf0d5");
          }
          if (e.hurtSeconds() > 0)
            text(
                b,
                "-" + Math.round(p.weaponDamage() * p.damageMultiplier()),
                s.x + 15,
                h - s.y + 65,
                "fff1b0");
        }
      box(b, "0d211bf5", 0, h - 78, w, 78);
      box(b, "728464", 0, h - 79, w, 1);
      box(b, "c9b477", 26, h - 53, 28, 28);
      box(b, "183d30", 31, h - 48, 18, 18);
      small(b, "S", 35, h - 32, "efdca1");
      text(b, largeTheme.getFont(), "STRAŻNICY BRAM", 70, h - 24, "ebe7cd");
      small(b, "ROZDZIAŁ I  /  KRÓLESTWO ZIELONYCH ŁĄK", 71, h - 54, "9baf99");
      float zoneX = Math.max(400, w / 2 - 105);
      String zone =
          world.atFrontier()
              ? "ARENA PvE"
              : world.inSanctuary() ? "OSADA POCZĄTKU" : "ŁĄKI SLAJMÓW";
      text(b, zone, zoneX, h - 23, "dbe1c4");
      small(
          b,
          world.inSanctuary() ? "BEZPIECZNA PRZYSTAŃ" : "PvE  ·  WALKA Z POTWORAMI",
          zoneX,
          h - 49,
          "9cc7a0");
      icon(b, art.coin, w - 181, h - 49, 24, 24);
      text(b, "" + p.coins(), w - 148, h - 27, "ead295");
      small(b, "MONETY", w - 147, h - 49, "8fa58e");
      box(b, "50614b", w - 72, h - 53, 38, 31);
      small(b, "Esc", w - 64, h - 32, "d1d6bb");
      // Character panel.
      float cy = h - 231;
      panel(b, 24, cy, 274, 130);
      box(b, "304e3a", 37, cy + 68, 47, 48);
      icon(b, art.hero, 42, cy + 70, 35, 47);
      text(b, "Wojownik", 96, cy + 111, "e8e6ca");
      small(b, "POZIOM " + p.level() + "  ·  STRAŻNIK", 96, cy + 88, "a9bf97");
      small(b, "ZDROWIE", 38, cy + 61, "bacbb2");
      small(b, p.hp() + " / " + p.maxHp(), 219, cy + 61, "e2ddc0");
      bar(b, "hp", 38, cy + 36, 246, 12, p.hp() / (float) p.maxHp(), "bd7769");
      small(b, "GŁÓD", 38, cy + 27, "bacbb2");
      small(b, p.hunger() + " / 100", 224, cy + 27, "e2ddc0");
      bar(b, "hunger", 38, cy + 7, 246, 10, p.hunger() / 100f, "d0b271");
      panel(b, 24, cy - 43, 274, 34);
      small(b, "EXP", 37, cy - 22, "9fb798");
      bar(
          b,
          "xp",
          74,
          cy - 31,
          140,
          9,
          (p.xp() - p.previousLevelXp()) / (float) (p.nextLevelXp() - p.previousLevelXp()),
          "91bfa0");
      small(b, p.xp() + " / " + p.nextLevelXp(), 223, cy - 22, "becdb0");
      if (p.strengthSeconds() > 0) {
        panel(b, 24, cy - 87, 274, 33);
        small(
            b,
            "SIŁA I  ·  +20% obrażeń  ·  " + (int) p.strengthSeconds() + " s",
            37,
            cy - 65,
            "e8cd84");
      }
      // Right-hand quest journal.
      float qx = w - 300, qy = h - 432;
      panel(b, qx, qy, 276, 331);
      if (world.inArena()) {
        small(b, "ARENA PvE  /  POWTARZALNA", qx + 18, qy + 309, "d4bd8b");
        text(b, largeTheme.getFont(), "Próba Strażnika", qx + 18, qy + 281, "e6e4c9");
        small(b, "FALA " + world.arenaWave() + " / 3", qx + 18, qy + 226, "c9dab2");
        bar(b, "arena-wave", qx + 18, qy + 199, 240, 10, world.arenaWave() / 3f, "b6c18a");
        String status =
            switch (world.arenaState()) {
              case READY -> "E — rozpocznij próbę";
              case FIGHTING -> "Pozostało wrogów: " + world.arenaRemaining();
              case RESTING ->
                  "Kolejna fala za "
                      + Math.max(1, (int) Math.ceil(world.arenaRestSeconds()))
                      + " s";
              case VICTORY -> "ZWYCIĘSTWO! E — odbierz łup";
            };
        text(b, status, qx + 18, qy + 176, "e8e3c5");
        small(b, "Nagroda: 60 monet + 100 EXP", qx + 18, qy + 132, "d7c18b");
        small(b, "Shift — unik  ·  R — przerwij / wróć", qx + 18, qy + 97, "b4c7a9");
        small(
            b,
            "Miecz: " + p.weaponDamage() + " obrażeń  ·  poziom " + p.weaponLevel(),
            qx + 18,
            qy + 67,
            "b4c7a9");
        small(b, "Wygrane próby: " + p.arenaWins(), qx + 18, qy + 36, "b4c7a9");
      } else {
        small(b, "DZIENNIK WYPRAWY", qx + 18, qy + 309, "95ae91");
        text(b, largeTheme.getFont(), "Twoje zlecenia", qx + 18, qy + 281, "e6e4c9");
        box(b, "4d644a", qx + 18, qy + 243, 240, 1);
        small(b, "01  /  POWTARZALNE", qx + 18, qy + 224, "a1b999");
        text(b, "Porządek na łące", qx + 18, qy + 202, "e8e3c5");
        small(b, "Pokonaj 6 slajmów", qx + 18, qy + 174, "a9bea2");
        small(b, p.huntKills() + " / 6", qx + 221, qy + 174, "d0d5ad");
        bar(b, "hunt", qx + 18, qy + 148, 240, 7, p.huntKills() / 6f, "b6c18a");
        small(b, "+8 monet     +20 EXP", qx + 18, qy + 134, "d7c18b");
        box(b, "425a42", qx + 18, qy + 109, 240, 1);
        small(b, "02  /  BRAMA KRÓLESTWA", qx + 18, qy + 91, "a1b999");
        text(
            b,
            p.bossDefeated() ? "Król został pokonany" : "Pokonaj Króla Slajmów",
            qx + 18,
            qy + 69,
            p.bossDefeated() ? "b9dbab" : "e8e3c5");
        small(
            b,
            p.bossDefeated()
                ? "Portal do Ostępów jest otwarty."
                : "Odblokuj portal do kolejnej krainy.",
            qx + 18,
            qy + 42,
            "a9bea2");
        small(b, "+50 monet     +100 EXP", qx + 18, qy + 19, "d7c18b");
      }
      // Reusable engine minimap, using the actual terrain and world-space markers.
      float mx = w - 258, my = 130;
      panel(b, mx, my, 234, 150);
      small(b, "MAPA OKOLICY", mx + 13, my + 132, "b4c5a8");
      minimap.setBounds(mx + 13, my + 15, 208, 103);
      minimap.draw(b, 1);
      // Hotbar, with actual sprite assets reused from the world.
      float bx = w / 2 - 200, by = 32;
      panel(b, bx, by, 400, 70);
      box(b, "456e59", bx + 8, by + 8, 55, 54);
      icon(b, art.hero, bx + 18, by + 12, 34, 46);
      small(b, "LPM", bx + 15, by + 20, "f0e4b7");
      slot(b, bx + 72, by, art.bread, "1", p.bread(), "CHLEB");
      slot(b, bx + 145, by, art.apple, "2", p.apples(), "JABŁKO");
      box(b, "4b6048", bx + 225, by + 12, 1, 45);
      small(b, "I  PLECAK", bx + 242, by + 45, "d0d5b6");
      small(
          b,
          world.dodgeCooldown() > 0
              ? "UNIK  " + String.format(java.util.Locale.ROOT, "%.1f s", world.dodgeCooldown())
              : "SHIFT  UNIK",
          bx + 242,
          by + 24,
          "98ae94");
      // Interaction callout and a message ledger kept above the hotbar.
      String prompt =
          world.inArena()
              ? (world.arenaState() == RpgWorld.ArenaState.READY
                  ? "E  ·  Rozpocznij 3 fale PvE"
                  : world.arenaState() == RpgWorld.ArenaState.VICTORY
                      ? "E  ·  Odbierz nagrodę i wróć"
                      : "R  ·  Przerwij próbę i wróć")
              : world.nearArenaGate()
                  ? "E  ·  Wejdź na arenę PvE"
                  : world.nearShop()
                      ? "E  ·  Porozmawiaj z kupcem"
                      : world.nearClasses()
                          ? "E  ·  Wybierz klasę"
                          : world.near(RpgContent.BOARD_X, RpgContent.BOARD_Y, 110)
                              ? "E  ·  Tablica zleceń"
                              : world.near(RpgContent.PORTAL_X, RpgContent.PORTAL_Y, 110)
                                  ? (p.bossDefeated()
                                      ? "E  ·  Wejdź do portalu"
                                      : "E  ·  Sprawdź zamknięty portal")
                                  : world.atFrontier() ? "E  ·  Wróć na Łąki Slajmów" : "";
      if (!prompt.isEmpty()) {
        var point =
            camera.worldToScreen(
                world.player().transform.position.x, world.player().transform.position.y);
        float cx = Math.max(310, Math.min(w - 458, point.x));
        panel(b, cx - 144, h - point.y + 52, 288, 34);
        small(b, prompt, cx - 130, h - point.y + 74, "e8dcab");
      }
      String msg = world.message();
      if (msg.length() > 100) msg = msg.substring(0, 97) + "...";
      panel(b, 24, 112, Math.min(w - 330, Math.max(400, msg.length() * 7.1f + 28)), 31);
      small(b, msg, 37, 133, "d1dab9");
      small(b, "WASD  RUCH   LPM / SPACJA  ATAK   SHIFT  UNIK   E  AKCJA", 26, 20, "a2b298");
      small(
          b,
          saveStatus.startsWith("BŁĄD")
              ? "BŁĄD ZAPISU  ·  SZCZEGÓŁY W KOMUNIKACIE"
              : "ZAPIS LOKALNY  ·  ROZDZIAŁ I",
          w - 295,
          20,
          "8fa58e");
      // Boss is announced only near the authored arena.
      if (!world.boss().dead()
          && world.near(
              world.boss().getTransform().position.x,
              world.boss().getTransform().position.y,
              430)) {
        float bb = w / 2 - 205, yy = h - 159;
        panel(b, bb, yy, 410, 60);
        small(b, "STRAŻNIK PIERWSZEJ BRAMY", bb + 15, yy + 46, "d6bd81");
        text(b, "KRÓL SLAJMÓW", bb + 15, yy + 28, "e9e0bd");
        bar(b, "boss", bb + 173, yy + 17, 219, 10, world.boss().hp() / 450f, "c29b61");
      }
      if (menu != Menu.NONE) box(b, "07150fd9", 0, 0, w, h);
      b.setColor(Color.WHITE);
    }

    private float MathUtilsSin(float x) {
      return (float) Math.sin(x);
    }

    private void slot(
        Batch b, float x, float y, TextureRegion image, String key, int count, String name) {
      box(b, "2d4835", x, y + 9, 65, 52);
      icon(b, image, x + 19, y + 23, 29, 29);
      small(b, key, x + 5, y + 57, "d5d7b5");
      small(b, "" + count, x + 47, y + 22, "e8ddae");
    }
  }

  @Override
  public void close() {
    if (modal != null) {
      modal.close();
      modal = null;
    }
    for (var meter : meters.values()) meter.close();
    meters.clear();
    stage.dispose();
    largeTheme.dispose();
    tinyTheme.dispose();
    theme.dispose();
    pixel.dispose();
  }
}
