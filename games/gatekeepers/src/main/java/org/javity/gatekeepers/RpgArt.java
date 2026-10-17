package org.javity.gatekeepers;

import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.math.MathUtils;
import java.util.*;
import org.javity.core.GameObject;
import org.javity.render.*;

/** Original procedural pixel illustrations. Owns every texture; renderers only borrow. */
public final class RpgArt implements AutoCloseable {
  private final List<Texture> owned = new ArrayList<>();
  private final RpgWorld world;
  public final TextureRegion hero, slime, king, bread, apple, coin, portrait, map;
  private final SpriteRenderer2D playerSprite;
  private final Map<RpgWorld.Enemy, SpriteRenderer2D> mobs = new LinkedHashMap<>();
  private final Map<RpgWorld.Enemy, SpriteRenderer2D> shadows = new LinkedHashMap<>();
  private final Map<RpgWorld.Enemy, ShapeRenderer2D> warnings = new LinkedHashMap<>();
  private final GameObject slash;
  private final SpriteRenderer2D portal;
  private final GameObject playerShadow;
  private final TextureRegion shade;
  private float time;

  public RpgArt(RpgWorld world, BitmapFont font) {
    this.world = world;
    hero = person("55988f", false);
    portrait = hero;
    slime = slime(false);
    king = slime(true);
    bread = food(false);
    apple = food(true);
    coin = coin();
    shade = shadow();
    map = terrain();
    add("ground", map, 1300, 800, 2600, 1600, -10, 0);
    scatter();
    var house = house();
    add("cottage", house, 250, 1110, 240, 225, 1, -1110);
    add("guild", house, 490, 1100, 210, 200, 1, -1100);
    add("home", house, 240, 385, 205, 195, 1, -385);
    add("home", house, 530, 310, 190, 180, 1, -310);
    add("shop", stall(), RpgContent.SHOP_X, RpgContent.SHOP_Y + 45, 155, 140, 2, -875);
    add("merchant", person("c39548", false), RpgContent.SHOP_X, RpgContent.SHOP_Y, 40, 56, 3, -870);
    add(
        "class merchant",
        person("9b688f", true),
        RpgContent.CLASS_X,
        RpgContent.CLASS_Y,
        45,
        63,
        3,
        -530);
    add("board", board(), RpgContent.BOARD_X, RpgContent.BOARD_Y + 10, 66, 88, 3, -830);
    label(font, "KUPIEC", RpgContent.SHOP_X - 27, RpgContent.SHOP_Y + 108, "ead4a2");
    label(font, "KLASY", RpgContent.CLASS_X - 24, RpgContent.CLASS_Y + 62, "dec5e8");
    label(font, "ZLECENIA", RpgContent.BOARD_X - 34, RpgContent.BOARD_Y + 72, "f6de9b");
    var portalObject =
        add("portal", portal(), RpgContent.PORTAL_X, RpgContent.PORTAL_Y, 120, 160, 3, -1160);
    portal = portalObject.getComponent(SpriteRenderer2D.class);
    label(font, "ARENA PvE", RpgContent.PORTAL_X - 78, RpgContent.PORTAL_Y + 110, "d9dab7");
    add(
        "arena entrance",
        portal(),
        RpgContent.ARENA_GATE_X,
        RpgContent.ARENA_GATE_Y,
        65,
        90,
        3,
        -500);
    label(font, "ARENA PvE", RpgContent.ARENA_GATE_X - 40, RpgContent.ARENA_GATE_Y + 65, "e5c88b");
    label(font, "PRÓBA STRAŻNIKA", RpgContent.ARENA_X - 85, RpgContent.ARENA_Y + 270, "e5c88b");
    playerSprite = attach(world.player(), hero, 44, 62, 3);
    playerShadow = add("hero shadow", shade, 0, 0, 42, 20, 2, 0);
    for (var e : world.enemies()) {
      mobs.put(
          e,
          attach(
              e.getGameObject(),
              e.isBoss() ? king : slime,
              e.isBoss() ? 140 : 51,
              e.isBoss() ? 132 : 46,
              3));
      var shadow =
          add("slime shadow", shade, 0, 0, e.isBoss() ? 132 : 48, e.isBoss() ? 43 : 18, 2, 0);
      shadows.put(e, shadow.getComponent(SpriteRenderer2D.class));
      {
        var warning = world.createObject("jump warning");
        var shape =
            warning.addComponent(ShapeRenderer2D.circle(e.isBoss() ? 100 : e.isElite() ? 64 : 52));
        shape.color.set(Color.valueOf("eea35466"));
        shape.strokeWidth = 3;
        shape.layer = 1;
        warnings.put(e, shape);
      }
    }
    slash = add("sword slash", slash(), 0, 0, 130, 130, 4, 0);
    update(0);
  }

  private SpriteRenderer2D attach(
      GameObject object, TextureRegion region, float w, float h, int layer) {
    var s = object.addComponent(new SpriteRenderer2D(region));
    s.width = w;
    s.height = h;
    s.layer = layer;
    return s;
  }

  private GameObject add(
      String name, TextureRegion region, float x, float y, float w, float h, int layer, int order) {
    var object = world.createObject(name).setPosition(x, y);
    attach(object, region, w, h, layer).order = order;
    return object;
  }

  private void label(BitmapFont font, String text, float x, float y, String color) {
    var o = world.createObject(text).setPosition(x, y);
    var r = o.addComponent(new TextRenderer2D(font, text));
    r.color.set(Color.valueOf(color));
    r.layer = 5;
  }

  public void update(float delta) {
    time += delta;
    var position = world.player().transform.position;
    playerSprite.order = -(int) position.y;
    playerSprite.flipX = world.facing.x < 0;
    playerSprite.tint.set(
        world.dodgeSeconds() > 0
            ? Color.valueOf("b5e7ee")
            : world.hitProtection() > 0 ? Color.valueOf("f2c9a2") : Color.WHITE);
    playerShadow.setPosition(position.x, position.y - 25);
    for (var entry : mobs.entrySet()) {
      var e = entry.getKey();
      var s = entry.getValue();
      var p = e.getTransform().position;
      s.setEnabled(!e.dead());
      s.order = -(int) p.y;
      float bob = (float) Math.sin(time * 3 + e.getGameObject().id) * .04f;
      s.height = (e.isBoss() ? 132 : e.isElite() ? 68 : 46) * (1 + bob);
      s.width = (e.isBoss() ? 140 : e.isElite() ? 74 : 51) * (1 - bob);
      s.tint.set(
          e.hurtSeconds() > 0
              ? Color.valueOf("fff4bf")
              : e.isElite() ? Color.valueOf("99bcf5") : Color.WHITE);
      var shadow = shadows.get(e);
      shadow.setEnabled(!e.dead());
      shadow.getGameObject().setPosition(p.x, p.y - (e.isBoss() ? 48 : 17));
      var warning = warnings.get(e);
      if (warning != null) {
        warning.setEnabled(e.windup() > 0 && !e.dead());
        warning
            .getGameObject()
            .setPosition(e.isBoss() ? e.jumpTarget().x : p.x, e.isBoss() ? e.jumpTarget().y : p.y);
      }
    }
    slash.setPosition(position.x + world.facing.x * 24, position.y + world.facing.y * 24);
    slash.transform.rotation = world.facing.angleRad();
    slash.getComponent(SpriteRenderer2D.class).setEnabled(world.slashSeconds() > 0);
    portal.tint.set(
        world.profile.bossDefeated() ? Color.valueOf("a2ffe1") : Color.valueOf("b5a7b7"));
  }

  private TextureRegion texture(Pixmap p) {
    var t = new Texture(p);
    t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
    p.dispose();
    owned.add(t);
    return new TextureRegion(t);
  }

  private static Pixmap image(int w, int h) {
    var p = new Pixmap(w, h, Pixmap.Format.RGBA8888);
    p.setColor(0);
    p.fill();
    return p;
  }

  private static void rect(Pixmap p, String c, int x, int y, int w, int h) {
    p.setColor(Color.valueOf(c));
    p.fillRectangle(x, y, w, h);
  }

  private static void circle(Pixmap p, String c, int x, int y, int r) {
    p.setColor(Color.valueOf(c));
    p.fillCircle(x, y, r);
  }

  private static void ellipse(Pixmap p, String c, int x, int y, int w, int h) {
    p.setColor(Color.valueOf(c));
    for (int dy = -h; dy <= h; dy++) {
      int extent = (int) (w * Math.sqrt(Math.max(0, 1 - (dy * (double) dy) / (h * h))));
      p.fillRectangle(x - extent, y + dy, extent * 2 + 1, 1);
    }
  }

  private TextureRegion terrain() {
    int w = RpgContent.WIDTH, h = RpgContent.HEIGHT;
    var p = image(w, h);
    Random r = new Random(42);
    String[] greens = {"547345", "567648", "587949", "5a7b4b", "577547", "527144"};
    for (int x = 0; x < w; x += 24)
      for (int y = 0; y < h; y += 24) rect(p, greens[r.nextInt(greens.length)], x, y, 24, 24);
    // Mossy sanctuary stonework and its earthen border.
    rect(p, "66804e", 100, 350, 565, 810);
    for (int y = 360; y < 1160; y += 28)
      for (int x = 110 + (y % 56 == 24 ? 14 : 0); x < 650; x += 35) {
        rect(p, r.nextBoolean() ? "838c66" : "7d8863", x, y, 32, 25);
        rect(p, "929978", x + 2, y + 1, 27, 2);
      }
    // Winding main route uses stamped disks, with irregular worn edges.
    for (int x = 580; x < 2110; x += 6) {
      int worldY = (int) (700 + 360 * MathUtils.sin((x - 580) / 1530f * MathUtils.PI * .5f));
      circle(p, "8c8a55", x, h - worldY, 48 + r.nextInt(5));
      circle(p, "b6a572", x, h - worldY, 39 + r.nextInt(4));
    }
    for (int i = 0; i < 700; i++) {
      int x = 690 + r.nextInt(1390);
      int y = h - (int) (700 + 360 * MathUtils.sin((x - 580) / 1530f * MathUtils.PI * .5f));
      rect(
          p, r.nextBoolean() ? "c7b784" : "a29564", x, y - 32 + r.nextInt(65), 3 + r.nextInt(6), 2);
    }
    // Crown arena, concentric carved flagstones.
    ellipse(p, "465e42", 1950, h - 1130, 230, 195);
    ellipse(p, "71826a", 1950, h - 1130, 210, 180);
    ellipse(p, "829079", 1950, h - 1130, 195, 165);
    for (int i = 0; i < 32; i++) {
      double a = i * Math.PI / 16;
      int x = 1950 + (int) (201 * Math.cos(a)), y = h - 1130 + (int) (171 * Math.sin(a));
      rect(p, "adb198", x - 9, y - 7, 18, 14);
      rect(p, "c0c2a7", x - 7, y - 6, 13, 3);
    }
    for (int y = h - 1250; y < h - 1010; y += 36)
      for (int x = 1800; x < 2100; x += 45) {
        if (Math.pow((x - 1950) / 190f, 2) + Math.pow((y - (h - 1130)) / 160f, 2) < 1)
          rect(p, "75866f", x, y, 41, 2);
      }
    // Pond, reed banks and reflected strips.
    ellipse(p, "3e644e", 390, h - 1300, 155, 112);
    ellipse(p, "63977c", 390, h - 1300, 143, 101);
    ellipse(p, "417e81", 390, h - 1300, 134, 91);
    ellipse(p, "4a9090", 370, h - 1320, 106, 56);
    for (int i = 0; i < 30; i++)
      rect(p, "80b9a4", 285 + r.nextInt(185), h - 1360 + r.nextInt(110), 12 + r.nextInt(20), 2);
    // Repeatable PvE arena: all actors stay inside the authored bounds.
    int ax = (int) RpgContent.ARENA_LEFT,
        ay = h - (int) (RpgContent.ARENA_BOTTOM + RpgContent.ARENA_HEIGHT);
    int aw = (int) RpgContent.ARENA_WIDTH, ah = (int) RpgContent.ARENA_HEIGHT;
    rect(p, "3c5148", ax - 20, ay - 20, aw + 40, ah + 40);
    rect(p, "aca68b", ax - 8, ay - 8, aw + 16, ah + 16);
    rect(p, "768570", ax, ay, aw, ah);
    for (int y = ay; y < ay + ah; y += 36)
      for (int x = ax; x < ax + aw; x += 45) {
        rect(p, "667c68", x, y, 42, 2);
        rect(p, "81917b", x, y + 3, 2, 30);
      }
    for (int x = ax; x < ax + aw; x += 45) {
      rect(p, "d0c49c", x, ay - 8, 20, 8);
      rect(p, "d0c49c", x, ay + ah, 20, 8);
    }
    for (int y = ay; y < ay + ah; y += 45) {
      rect(p, "d0c49c", ax - 8, y, 8, 20);
      rect(p, "d0c49c", ax + aw, y, 8, 20);
    }
    ellipse(p, "8d9c7b", (int) RpgContent.ARENA_X, h - (int) RpgContent.ARENA_Y, 70, 65);
    for (int i = 0; i < 18000; i++) {
      int x = r.nextInt(w), y = r.nextInt(h);
      if (x > RpgContent.ARENA_LEFT - 25
          && x < RpgContent.ARENA_LEFT + RpgContent.ARENA_WIDTH + 25
          && h - y > RpgContent.ARENA_BOTTOM - 25
          && h - y < RpgContent.ARENA_BOTTOM + RpgContent.ARENA_HEIGHT + 25) continue;
      if (x < 660 && y > 350 && y < 1160) continue;
      if (Math.pow((x - 1950) / 240f, 2) + Math.pow((y - (h - 1130)) / 205f, 2) < 1) continue;
      if (Math.pow((x - 390) / 160f, 2) + Math.pow((y - (h - 1300)) / 120f, 2) < 1) continue;
      int route = (int) (700 + 360 * MathUtils.sin((x - 580) / 1530f * MathUtils.PI * .5f));
      if (x > 580 && x < 2110 && Math.abs(h - y - route) < 52) continue;
      rect(p, i % 4 == 0 ? "85a462" : "66874f", x, y, 2, 4);
      if (i % 27 == 0) {
        rect(p, "dbd6a1", x, y, 3, 3);
        rect(p, "bba86f", x + 1, y + 1, 1, 1);
      }
      if (i % 49 == 0) rect(p, "b3acbb", x, y, 3, 3);
    }
    // Safe boundary: a low timber fence with a broad opening at the route.
    for (int y = 250; y < 1390; y += 38) {
      if (Math.abs(y - (h - 700)) < 80) continue;
      rect(p, "5a5b3e", 680, y, 8, 40);
      rect(p, "a18c57", 679, y, 10, 6);
      rect(p, "a18c57", 680, y + 10, 8, 24);
    }
    return texture(p);
  }

  private void scatter() {
    TextureRegion tree = tree(false), pine = tree(true), rock = rock(), bush = bush();
    Random r = new Random(9);
    for (var t : RpgContent.trees())
      add(
          "oak",
          t.pine() ? pine : tree,
          t.x(),
          t.y(),
          104 * t.scale(),
          128 * t.scale(),
          3,
          -(int) t.y() - 30);
    for (int i = 0; i < 80; i++) {
      int x = 760 + r.nextInt(1600), y = 240 + r.nextInt(1100);
      if (Math.hypot(x - 1950, y - 1130) < 250 || (x > 1930 && y < 740)) continue;
      add(
          "undergrowth",
          i % 4 == 0 ? rock : bush,
          x,
          y,
          i % 4 == 0 ? 28 : 38,
          i % 4 == 0 ? 24 : 34,
          2,
          -y);
    }
    var lamp = lamp();
    for (float[] pos :
        new float[][] {
          {640, 760}, {640, 620}, {1800, 1010}, {2120, 1010}, {1810, 1270}, {2120, 1270}
        }) add("lantern", lamp, pos[0], pos[1], 36, 74, 3, -(int) pos[1]);
  }

  private TextureRegion tree(boolean pine) {
    var p = image(96, 120);
    ellipse(p, "263e3155", 48, 104, 44, 12);
    rect(p, "443e2d", 43, 62, 12, 45);
    rect(p, "7c6440", 46, 65, 5, 38);
    if (pine) {
      p.setColor(Color.valueOf("254a38"));
      p.fillTriangle(48, 4, 7, 88, 89, 88);
      p.setColor(Color.valueOf("37644a"));
      p.fillTriangle(48, 5, 12, 73, 83, 73);
      p.setColor(Color.valueOf("4a8056"));
      p.fillTriangle(46, 9, 20, 56, 72, 56);
      p.setColor(Color.valueOf("71965d"));
      p.fillTriangle(44, 12, 27, 40, 48, 37);
    } else {
      circle(p, "294b36", 48, 60, 39);
      circle(p, "345c3c", 26, 51, 23);
      circle(p, "386c42", 65, 43, 27);
      circle(p, "4b7b46", 42, 32, 28);
      circle(p, "568b4b", 28, 29, 19);
      circle(p, "66994e", 49, 19, 17);
      circle(p, "83aa5f", 38, 17, 10);
      circle(p, "6c9a50", 19, 44, 12);
      circle(p, "4c8047", 73, 61, 13);
      rect(p, "98b868", 31, 12, 9, 3);
      rect(p, "7fa95b", 13, 39, 8, 3);
      rect(p, "82a85f", 51, 25, 10, 3);
    }
    return texture(p);
  }

  private TextureRegion rock() {
    var p = image(40, 32);
    ellipse(p, "344b3a55", 20, 26, 18, 5);
    ellipse(p, "59695e", 20, 16, 16, 12);
    rect(p, "8a9986", 9, 8, 21, 11);
    rect(p, "acb2a0", 12, 6, 15, 4);
    return texture(p);
  }

  private TextureRegion bush() {
    var p = image(48, 40);
    ellipse(p, "2b4e35", 24, 28, 22, 10);
    circle(p, "497c43", 15, 20, 12);
    circle(p, "5d8c4d", 29, 16, 14);
    circle(p, "75a05a", 24, 11, 8);
    rect(p, "d1c894", 17, 10, 3, 3);
    rect(p, "cbbf8c", 32, 18, 3, 3);
    return texture(p);
  }

  private TextureRegion shadow() {
    var p = image(64, 24);
    ellipse(p, "173b2b45", 32, 12, 30, 10);
    return texture(p);
  }

  private TextureRegion person(String coat, boolean helmet) {
    var p = image(32, 48);
    rect(p, "273f39", 8, 30, 7, 14);
    rect(p, "273f39", 18, 30, 7, 14);
    rect(p, "332d27", 6, 42, 10, 5);
    rect(p, "332d27", 18, 42, 10, 5);
    rect(p, "263e3b", 5, 19, 23, 17);
    rect(p, coat, 7, 20, 19, 14);
    rect(p, "a9c3a3", 8, 21, 5, 12);
    rect(p, "355b52", 20, 22, 4, 12);
    rect(p, "d9b58a", 2, 23, 5, 11);
    rect(p, "d9b58a", 26, 23, 4, 9);
    rect(p, "2c3930", 8, 3, 18, 18);
    rect(p, "d1a879", 10, 8, 14, 12);
    rect(p, "edc799", 11, 8, 10, 9);
    rect(p, "332f26", 13, 11, 2, 3);
    rect(p, "332f26", 21, 11, 2, 3);
    rect(p, "6e6447", 7, 3, 20, 6);
    rect(p, "9ba990", 9, 1, 15, 4);
    if (helmet) {
      rect(p, "b5bea9", 7, 4, 20, 9);
      rect(p, "6d8279", 9, 11, 3, 7);
    }
    rect(p, "e5cf8a", 7, 32, 18, 3);
    rect(p, "876f44", 16, 32, 4, 4);
    rect(p, "bdcfc3", 27, 25, 3, 16);
    rect(p, "f1ebcf", 28, 23, 2, 14);
    rect(p, "c5a965", 24, 35, 8, 3);
    return texture(p);
  }

  private TextureRegion slime(boolean boss) {
    var p = image(64, 60);
    ellipse(p, "244b37", 32, 37, 28, 20);
    ellipse(p, boss ? "64954a" : "6eaa60", 32, 31, 26, 23);
    ellipse(p, boss ? "8fba58" : "99ca74", 29, 24, 21, 17);
    ellipse(p, "c5dfa0", 23, 17, 10, 6);
    rect(p, "244c38", 21, 31, 5, 7);
    rect(p, "244c38", 41, 31, 5, 7);
    rect(p, "eef3d4", 22, 31, 2, 2);
    rect(p, "eef3d4", 42, 31, 2, 2);
    rect(p, "446d44", 29, 40, 8, 2);
    rect(p, "c8dc91", 9, 39, 6, 3);
    rect(p, "c8dc91", 49, 38, 6, 3);
    if (boss) {
      rect(p, "6e582d", 18, 3, 30, 12);
      rect(p, "e4c16a", 20, 3, 26, 9);
      rect(p, "f8df8b", 18, 0, 5, 8);
      rect(p, "f8df8b", 30, 0, 5, 7);
      rect(p, "f8df8b", 43, 0, 5, 8);
      rect(p, "d66b59", 30, 7, 5, 4);
    }
    return texture(p);
  }

  private TextureRegion house() {
    var p = image(160, 150);
    ellipse(p, "223d3155", 80, 135, 72, 12);
    rect(p, "514b38", 25, 62, 113, 72);
    rect(p, "c9b888", 28, 64, 106, 64);
    rect(p, "8f9268", 29, 119, 105, 13);
    rect(p, "6f6245", 76, 91, 25, 42);
    rect(p, "8d7751", 80, 94, 17, 35);
    rect(p, "e4c281", 91, 112, 3, 3);
    rect(p, "5b785e", 39, 85, 23, 24);
    rect(p, "efda9b", 42, 88, 17, 17);
    rect(p, "786e48", 49, 86, 3, 22);
    rect(p, "786e48", 40, 96, 21, 3);
    rect(p, "5d765d", 110, 85, 16, 24);
    rect(p, "ead89c", 113, 88, 10, 17);
    rect(p, "66563e", 26, 65, 6, 64);
    rect(p, "66563e", 129, 65, 6, 64);
    p.setColor(Color.valueOf("443e30"));
    p.fillTriangle(80, 6, 7, 73, 153, 73);
    p.setColor(Color.valueOf("666d51"));
    p.fillTriangle(80, 10, 11, 68, 149, 68);
    for (int y = 24; y < 68; y += 9) {
      int extent = (y - 10) * 69 / 58;
      rect(p, y % 18 == 6 ? "8c9561" : "7c875a", 80 - extent, y, extent * 2, 6);
    }
    rect(p, "b6b487", 28, 67, 109, 5);
    rect(p, "7d8060", 119, 17, 14, 28);
    rect(p, "b3ac80", 117, 14, 18, 6);
    rect(p, "dbc99a", 74, 133, 31, 5);
    rect(p, "938760", 70, 139, 39, 4);
    return texture(p);
  }

  private TextureRegion stall() {
    var p = image(112, 104);
    rect(p, "514733", 12, 34, 5, 62);
    rect(p, "514733", 94, 34, 5, 62);
    rect(p, "443f2e", 9, 59, 93, 31);
    rect(p, "9d774a", 12, 62, 87, 25);
    for (int x = 15; x < 97; x += 15) rect(p, "be9259", x, 64, 12, 19);
    rect(p, "e0c18a", 8, 58, 96, 7);
    for (int x = 8; x < 104; x += 16) {
      rect(p, (x / 16) % 2 == 0 ? "e0cc98" : "638b72", x, 19, 16, 24);
      rect(p, (x / 16) % 2 == 0 ? "c8b383" : "456e58", x, 43, 16, 7);
    }
    rect(p, "9eaa77", 8, 14, 96, 7);
    for (int i = 0; i < 5; i++) {
      circle(p, "bc8c4a", 22 + i * 10, 55, 5);
      rect(p, "edc17b", 20 + i * 10, 52, 3, 2);
    }
    return texture(p);
  }

  private TextureRegion board() {
    var p = image(48, 64);
    rect(p, "4c4432", 8, 13, 32, 34);
    rect(p, "947448", 11, 16, 26, 28);
    rect(p, "5d4f36", 21, 44, 7, 19);
    rect(p, "f1e2b2", 16, 22, 18, 17);
    rect(p, "a99667", 19, 26, 12, 2);
    rect(p, "a99667", 19, 31, 9, 2);
    rect(p, "e4b95a", 22, 2, 5, 8);
    return texture(p);
  }

  private TextureRegion lamp() {
    var p = image(28, 64);
    rect(p, "4b4d39", 12, 15, 5, 48);
    rect(p, "343e31", 8, 9, 14, 18);
    rect(p, "e2bd66", 10, 12, 10, 13);
    rect(p, "f7df95", 13, 13, 5, 9);
    rect(p, "5d6548", 7, 7, 16, 4);
    return texture(p);
  }

  private TextureRegion portal() {
    var p = image(96, 128);
    ellipse(p, "283d3b", 48, 67, 40, 57);
    ellipse(p, "7a8780", 48, 64, 36, 54);
    ellipse(p, "b1b49a", 48, 63, 31, 49);
    ellipse(p, "355d58", 48, 63, 26, 45);
    ellipse(p, "517f73", 48, 62, 22, 41);
    ellipse(p, "83bba0", 48, 62, 15, 34);
    ellipse(p, "b2d5ad", 48, 62, 8, 28);
    for (int i = 0; i < 9; i++) {
      double a = i * Math.PI / 4.5;
      int x = 48 + (int) (33 * Math.sin(a)), y = 64 + (int) (50 * Math.cos(a));
      rect(p, "dbd4a4", x - 2, y - 2, 4, 4);
    }
    rect(p, "646f5f", 12, 116, 72, 7);
    rect(p, "969d80", 17, 113, 62, 5);
    return texture(p);
  }

  private TextureRegion food(boolean apple) {
    var p = image(32, 32);
    if (apple) {
      ellipse(p, "b88338", 16, 19, 12, 11);
      ellipse(p, "dfb950", 15, 17, 11, 10);
      ellipse(p, "f2d877", 12, 14, 6, 5);
      rect(p, "716342", 16, 2, 3, 7);
      rect(p, "90ad5b", 19, 4, 7, 4);
    } else {
      ellipse(p, "795033", 16, 18, 14, 10);
      ellipse(p, "be8f54", 16, 15, 13, 9);
      ellipse(p, "e1b978", 13, 12, 10, 6);
      for (int i = 0; i < 3; i++) rect(p, "f0d49a", 8 + i * 6, 8 + i % 2 * 2, 3, 6);
    }
    return texture(p);
  }

  private TextureRegion coin() {
    var p = image(24, 24);
    circle(p, "8c6e38", 12, 13, 10);
    circle(p, "e0bb65", 12, 11, 10);
    circle(p, "f4d887", 12, 10, 7);
    rect(p, "b88e43", 11, 5, 3, 11);
    return texture(p);
  }

  private TextureRegion slash() {
    var p = image(128, 128);
    for (int a = -62; a < 63; a++) {
      double radians = Math.toRadians(a);
      int x = 64 + (int) (53 * Math.cos(radians)), y = 64 + (int) (53 * Math.sin(radians));
      circle(p, "f5e8b3bb", x, y, 3);
      circle(p, "fff8dcee", x - 2, y, 1);
    }
    return texture(p);
  }

  @Override
  public void close() {
    for (Texture texture : owned) texture.dispose();
    owned.clear();
  }
}
