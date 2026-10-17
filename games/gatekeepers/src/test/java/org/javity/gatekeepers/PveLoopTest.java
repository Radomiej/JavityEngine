package org.javity.gatekeepers;

import static org.junit.Assert.*;

import org.junit.Test;

public class PveLoopTest {
  @Test
  public void arenaHasThreeFiniteWavesSingleRewardAndCanBeRepeated() {
    try (var w = new RpgWorld(new PlayerProfile())) {
      w.start();
      w.enterArena();
      assertTrue(w.inArena());
      assertTrue(w.startArena());
      for (int wave = 1; wave <= 3; wave++) {
        assertEquals(wave, w.arenaWave());
        for (var enemy : w.arenaEnemies()) if (!enemy.dead()) enemy.takeHit(1000);
        for (int i = 0; i < 20; i++) w.step(.1f);
      }
      assertEquals(RpgWorld.ArenaState.VICTORY, w.arenaState());
      int coins = w.profile.coins();
      assertTrue(w.claimArena());
      assertEquals(coins + 60, w.profile.coins());
      assertFalse(w.claimArena());
      assertEquals(1, w.profile.arenaWins());
      w.leaveArena();
      w.enterArena();
      assertTrue(w.startArena());
      assertEquals(1, w.arenaWave());
    }
  }

  @Test
  public void deathAndAbandonCannotCashUnfinishedArena() {
    try (var w = new RpgWorld(new PlayerProfile())) {
      w.start();
      w.enterArena();
      w.startArena();
      w.leaveArena();
      assertFalse(w.claimArena());
      w.enterArena();
      w.startArena();
      w.profile.damage(100);
      w.step(.1f);
      assertFalse(w.inArena());
      assertTrue(w.inSanctuary());
      assertFalse(w.claimArena());
    }
  }

  @Test
  public void dodgeConsumesCooldownAndPreventsDamageForItsWindow() {
    try (var w = new RpgWorld(new PlayerProfile())) {
      w.start();
      w.player().setPosition(1000, 700);
      assertTrue(w.dodge());
      assertFalse(w.dodge());
      w.receiveHit(15);
      assertEquals(100, w.profile.hp());
      for (int i = 0; i < 4; i++) w.step(.1f);
      w.receiveHit(15);
      assertEquals(85, w.profile.hp());
      w.receiveHit(15);
      assertEquals(85, w.profile.hp());
    }
  }

  @Test
  public void villageBuildingsBlockMovementAndMeleeThroughWalls() {
    var collision = new RpgCollision();
    assertFalse(collision.clearLine(150, 1060, 350, 1060));
    assertTrue(collision.clearLine(720, 700, 850, 700));
    var point = new com.badlogic.gdx.math.Vector2(150, 1060);
    collision.move(point, 350, 1060, 12);
    assertTrue(point.x < 180);
  }
}
