package org.javity.gatekeepers;

import static org.junit.Assert.*;

import org.junit.Test;

public class PlayerProfileTest {
  @Test
  public void arenaRewardsBuyPermanentWeaponUpgradesAtIncreasingPrices() {
    var p = new PlayerProfile();
    assertFalse(p.upgradeWeapon());
    p.rewardArena();
    assertEquals(90, p.coins());
    assertTrue(p.upgradeWeapon());
    assertEquals(30, p.coins());
    assertEquals(16, p.weaponDamage());
    assertEquals(120, p.upgradePrice());
    assertFalse(p.upgradeWeapon());
    assertEquals(2, PlayerProfile.restore(p.snapshot()).weaponLevel());
    assertEquals(1, p.arenaWins());
  }

  @Test
  public void invalidInventoryCannotOverflowSaveValidation() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            PlayerProfile.restore(
                new PlayerProfile.Snapshot(
                    100, 100, 30, 0, Integer.MAX_VALUE, Integer.MAX_VALUE, 0, false, false, 0)));
  }

  @Test
  public void foodCostsMoneyAndRestoresBoundedStats() {
    var p = new PlayerProfile();
    assertEquals(30, p.coins());
    assertEquals(3, p.bread());
    assertTrue(p.buy(PlayerProfile.Food.BREAD));
    assertEquals(26, p.coins());
    p.tick(240, false);
    p.damage(20);
    assertTrue(p.eat(PlayerProfile.Food.BREAD));
    assertEquals(88, p.hp());
    assertEquals(100, p.hunger());
    assertEquals(3, p.bread());
  }

  @Test
  public void unaffordablePurchaseAndEmptySlotLeaveStateIntact() {
    var p = new PlayerProfile();
    assertFalse(p.buy(PlayerProfile.Food.APPLE));
    assertEquals(30, p.coins());
    assertFalse(p.eat(PlayerProfile.Food.APPLE));
    assertEquals(100, p.hp());
  }

  @Test
  public void sanctuaryFreezesHungerAndStrengthExpires() {
    var p = new PlayerProfile();
    p.tick(120, true);
    assertEquals(100, p.hunger());
    for (int i = 0; i < 35; i++) p.rewardMob(false);
    assertTrue(p.buy(PlayerProfile.Food.APPLE));
    assertTrue(p.eat(PlayerProfile.Food.APPLE));
    assertEquals(1.2f, p.damageMultiplier(), .001f);
    p.tick(30.1f, true);
    assertEquals(1, p.damageMultiplier(), .001f);
  }

  @Test
  public void huntCanBeClaimedOnlyOnceAndThenRepeated() {
    var p = new PlayerProfile();
    for (int i = 0; i < 6; i++) p.rewardMob(false);
    assertTrue(p.claimHunt());
    assertEquals(50, p.coins());
    assertEquals(80, p.xp());
    assertFalse(p.claimHunt());
    assertEquals(0, p.huntKills());
    for (int i = 0; i < 6; i++) p.rewardMob(false);
    assertTrue(p.claimHunt());
    assertEquals(70, p.coins());
  }

  @Test
  public void levelThresholdsAndBossRewardAreStable() {
    var p = new PlayerProfile();
    for (int i = 0; i < 10; i++) p.rewardMob(false);
    assertEquals(2, p.level());
    assertEquals(105, p.maxHp());
    p.rewardMob(true);
    assertEquals(3, p.level());
    assertTrue(p.bossDefeated());
    assertTrue(p.claimBoss());
    assertFalse(p.claimBoss());
  }

  @Test
  public void foodCannotBypassDeathAndRespawn() {
    var p = new PlayerProfile();
    p.damage(100);
    assertFalse(p.eat(PlayerProfile.Food.BREAD));
    assertEquals(0, p.hp());
    assertEquals(3, p.bread());
    p.respawn();
    assertTrue(p.eat(PlayerProfile.Food.BREAD));
  }
}
