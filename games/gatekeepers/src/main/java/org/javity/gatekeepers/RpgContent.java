package org.javity.gatekeepers;

import java.util.*;

/** Authored first-chapter positions; world units are pixels at camera zoom 1. */
public final class RpgContent {
  private RpgContent() {}

  public static final int WIDTH = 2600, HEIGHT = 1600;
  public static final float SPAWN_X = 380, SPAWN_Y = 680;
  public static final float SHOP_X = 310, SHOP_Y = 870, CLASS_X = 460, CLASS_Y = 530;
  public static final float BOARD_X = 580, BOARD_Y = 830;
  public static final float BOSS_X = 1950, BOSS_Y = 1130;
  public static final float PORTAL_X = 2310, PORTAL_Y = 1160;
  public static final float ARENA_GATE_X = 610, ARENA_GATE_Y = 500;
  public static final float ARENA_X = 2240, ARENA_Y = 425;
  public static final float ARENA_LEFT = 1980,
      ARENA_BOTTOM = 175,
      ARENA_WIDTH = 540,
      ARENA_HEIGHT = 500;
  public static final float[][] SLIMES = {
    {900, 720}, {1040, 600}, {1130, 830}, {1280, 690}, {1380, 920}, {1480, 600},
    {940, 1040}, {1160, 1120}, {1430, 1180}, {1610, 1020}, {1660, 760}, {1250, 440},
    {970, 390}, {1550, 380}
  };

  public record Tree(float x, float y, float scale, boolean pine) {}

  private static final List<Tree> TREES = buildTrees();

  public static List<Tree> trees() {
    return TREES;
  }

  private static List<Tree> buildTrees() {
    var result = new ArrayList<Tree>();
    var r = new Random(9);
    for (int i = 0; i < 245; i++) {
      int x = 45 + r.nextInt(2510), y = 65 + r.nextInt(1470);
      if (x > 1930 && y < 740) continue;
      boolean border = x < 105 || x > 2470 || y < 180 || y > 1460;
      if (!border) {
        if (x < 700) continue;
        int route = (int) (700 + 360 * Math.sin((x - 580) / 1530f * Math.PI * .5));
        if (x < 2180 && Math.abs(y - route) < 155) continue;
        if (Math.hypot(x - BOSS_X, y - BOSS_Y) < 320
            || Math.hypot(x - PORTAL_X, y - PORTAL_Y) < 180) continue;
        boolean near = false;
        for (float[] point : SLIMES) if (Math.hypot(x - point[0], y - point[1]) < 90) near = true;
        if (near) continue;
      }
      result.add(new Tree(x, y, .75f + r.nextFloat() * .65f, i % 3 == 0));
    }
    return List.copyOf(result);
  }
}
