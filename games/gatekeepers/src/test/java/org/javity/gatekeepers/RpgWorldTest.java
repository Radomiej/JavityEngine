package org.javity.gatekeepers;

import static org.junit.Assert.*;

import org.junit.Test;

public class RpgWorldTest {
  @Test
  public void deathRewardsOnceEvenIfStruckAgain() {
    try (var w = new RpgWorld(new PlayerProfile())) {
      w.start();
      var e = w.enemies().get(0);
      e.takeHit(30);
      e.takeHit(30);
      assertEquals(10, w.profile.xp());
      assertEquals(32, w.profile.coins());
    }
  }

  @Test
  public void attackRespectsReachAndCooldown() {
    try (var w = new RpgWorld(new PlayerProfile())) {
      w.start();
      var e = w.enemies().get(0);
      w.player().setPosition(1000, 200);
      e.getGameObject().setPosition(1050, 200);
      w.attack(1, 0);
      assertEquals(18, e.hp());
      w.attack(1, 0);
      assertEquals(18, e.hp());
      for (int i = 0; i < 5; i++) w.step(.1f);
      e.getGameObject().setPosition(1250, 200);
      w.attack(1, 0);
      assertEquals(18, e.hp());
    }
  }

  @Test
  public void sanctuaryAndPauseProtectThePlayer() {
    try (var w = new RpgWorld(new PlayerProfile())) {
      w.start();
      var e = w.enemies().get(0);
      e.getGameObject().setPosition(390, 680);
      for (int i = 0; i < 150; i++) w.step(.1f);
      assertEquals(100, w.profile.hp());
      assertEquals(100, w.profile.hunger());
      w.player().setPosition(1000, 300);
      w.pause();
      for (int i = 0; i < 150; i++) w.step(.1f);
      assertEquals(100, w.profile.hunger());
    }
  }

  @Test
  public void bossUnlocksPortalAndDeathResetsFight() {
    try (var w = new RpgWorld(new PlayerProfile())) {
      w.start();
      w.player().setPosition(RpgContent.PORTAL_X, RpgContent.PORTAL_Y);
      w.interact();
      assertFalse(w.atFrontier());
      w.boss().takeHit(20);
      w.profile.damage(100);
      w.step(.1f);
      assertEquals(450, w.boss().hp());
      assertTrue(w.inSanctuary());
      w.boss().takeHit(450);
      w.player().setPosition(RpgContent.PORTAL_X, RpgContent.PORTAL_Y);
      w.interact();
      assertTrue(w.atFrontier());
      assertTrue(w.profile.bossDefeated());
    }
  }

  @Test
  public void bossStaysBoundedToArenaAndResetsWhenPlayerLeaves() {
    try (var w = new RpgWorld(new PlayerProfile())) {
      w.start();
      var boss = w.boss();
      w.player().setPosition(RpgContent.BOSS_X - 300, RpgContent.BOSS_Y);
      for (int i = 0; i < 100; i++) w.step(.1f);
      assertTrue(boss.getTransform().position.dst(RpgContent.BOSS_X, RpgContent.BOSS_Y) <= 185);
      boss.takeHit(12);
      w.player().setPosition(800, 800);
      w.step(.1f);
      assertEquals(450, boss.hp());
      assertEquals(RpgContent.BOSS_X, boss.getTransform().position.x, .001f);
    }
  }

  @Test
  public void bossSummonsExactlyTwoGuardsAndSanctuaryBlocksAttacks() {
    try (var w = new RpgWorld(new PlayerProfile())) {
      w.start();
      long alive = w.enemies().stream().filter(e -> !e.dead()).count();
      w.boss().takeHit(230);
      assertEquals(alive + 2, w.enemies().stream().filter(e -> !e.dead()).count());
      w.boss().takeHit(12);
      assertEquals(alive + 2, w.enemies().stream().filter(e -> !e.dead()).count());
      var e = w.enemies().get(0);
      e.getGameObject().setPosition(430, 680);
      w.attack(1, 0);
      assertEquals(30, e.hp());
    }
  }
}
