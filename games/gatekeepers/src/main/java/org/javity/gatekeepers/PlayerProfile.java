package org.javity.gatekeepers;

/** Persistent, GL-free character and quest state. All rewards enter here. */
public final class PlayerProfile {
  public enum Food {
    BREAD(4, 20, 8),
    APPLE(100, 40, 40);
    public final int price, satiety, healing;

    Food(int price, int satiety, int healing) {
      this.price = price;
      this.satiety = satiety;
      this.healing = healing;
    }
  }

  public record Snapshot(
      int hp,
      int hunger,
      int coins,
      int xp,
      int bread,
      int apples,
      int huntKills,
      boolean bossDefeated,
      boolean bossClaimed,
      float strengthSeconds,
      int weaponLevel,
      int arenaWins) {
    public Snapshot(
        int hp,
        int hunger,
        int coins,
        int xp,
        int bread,
        int apples,
        int huntKills,
        boolean bossDefeated,
        boolean bossClaimed,
        float strengthSeconds) {
      this(
          hp,
          hunger,
          coins,
          xp,
          bread,
          apples,
          huntKills,
          bossDefeated,
          bossClaimed,
          strengthSeconds,
          1,
          0);
    }
  }

  private int hp = 100, hunger = 100, coins = 30, xp, bread = 3, apples, huntKills;
  private boolean bossDefeated, bossClaimed;
  private float strengthSeconds, hungerClock, regenClock, sinceDamage;
  private int weaponLevel = 1, arenaWins;

  public int weaponLevel() {
    return weaponLevel;
  }

  public int weaponDamage() {
    return 12 + 4 * (weaponLevel - 1);
  }

  public int upgradePrice() {
    return weaponLevel >= 3 ? 0 : 60 * weaponLevel;
  }

  public int arenaWins() {
    return arenaWins;
  }

  public boolean upgradeWeapon() {
    int price = upgradePrice();
    if (price == 0 || coins < price) return false;
    coins -= price;
    weaponLevel++;
    return true;
  }

  public void rewardArena() {
    coins += 60;
    xp += 100;
    arenaWins++;
  }

  public void rewardElite() {
    xp += 25;
    coins += 5;
    huntKills = Math.min(6, huntKills + 1);
  }

  public int hp() {
    return hp;
  }

  public int hunger() {
    return hunger;
  }

  public int coins() {
    return coins;
  }

  public int xp() {
    return xp;
  }

  public int bread() {
    return bread;
  }

  public int apples() {
    return apples;
  }

  public int huntKills() {
    return huntKills;
  }

  public boolean bossDefeated() {
    return bossDefeated;
  }

  public boolean bossClaimed() {
    return bossClaimed;
  }

  public float strengthSeconds() {
    return strengthSeconds;
  }

  public float damageMultiplier() {
    return strengthSeconds > 0 ? 1.2f : 1f;
  }

  public int level() {
    return (int) ((1 + Math.sqrt(1 + xp / 12.5)) / 2);
  }

  public int maxHp() {
    return 100 + 5 * (level() - 1);
  }

  public int nextLevelXp() {
    return 50 * level() * (level() + 1);
  }

  public int previousLevelXp() {
    return 50 * (level() - 1) * level();
  }

  public boolean buy(Food food) {
    if (coins < food.price || bread + apples >= 99) return false;
    coins -= food.price;
    if (food == Food.BREAD) bread++;
    else apples++;
    return true;
  }

  public boolean eat(Food food) {
    if (hp == 0) return false;
    if (food == Food.BREAD ? bread == 0 : apples == 0) return false;
    if (food == Food.BREAD) bread--;
    else {
      apples--;
      strengthSeconds = 30;
    }
    hp = Math.min(maxHp(), hp + food.healing);
    hunger = Math.min(100, hunger + food.satiety);
    return true;
  }

  public void damage(int amount) {
    if (amount < 0) throw new IllegalArgumentException("Negative damage");
    hp = Math.max(0, hp - amount);
    sinceDamage = 0;
    regenClock = 0;
  }

  public void tick(float delta, boolean sanctuary) {
    if (!Float.isFinite(delta) || delta < 0) throw new IllegalArgumentException("Invalid time");
    strengthSeconds = Math.max(0, strengthSeconds - delta);
    sinceDamage += delta;
    if (!sanctuary) {
      hungerClock += delta;
      int points = (int) (hungerClock / 12);
      hungerClock -= points * 12;
      hunger = Math.max(0, hunger - points);
    }
    if (hunger >= 70 && sinceDamage >= 5) {
      regenClock += delta;
      int healing = (int) (regenClock / 2);
      regenClock -= healing * 2;
      hp = Math.min(maxHp(), hp + healing);
    } else regenClock = 0;
  }

  public void rewardMob(boolean boss) {
    xp += boss ? 200 : 10;
    coins += boss ? 50 : 2;
    if (boss) bossDefeated = true;
    else huntKills = Math.min(6, huntKills + 1);
  }

  public boolean claimHunt() {
    if (huntKills < 6) return false;
    huntKills = 0;
    coins += 8;
    xp += 20;
    return true;
  }

  public boolean claimBoss() {
    if (!bossDefeated || bossClaimed) return false;
    bossClaimed = true;
    coins += 50;
    xp += 100;
    return true;
  }

  public void respawn() {
    hp = maxHp();
    hunger = Math.max(30, hunger);
    sinceDamage = 0;
    regenClock = 0;
  }

  public Snapshot snapshot() {
    return new Snapshot(
        hp,
        hunger,
        coins,
        xp,
        bread,
        apples,
        huntKills,
        bossDefeated,
        bossClaimed,
        strengthSeconds,
        weaponLevel,
        arenaWins);
  }

  public static PlayerProfile restore(Snapshot s) {
    if (s.coins < 0
        || s.coins > 100000000
        || s.xp < 0
        || s.xp > 100000000
        || s.hunger < 0
        || s.hunger > 100
        || s.bread < 0
        || s.bread > 99
        || s.apples < 0
        || s.apples > 99
        || s.bread + s.apples > 99
        || s.huntKills < 0
        || s.huntKills > 6
        || !Float.isFinite(s.strengthSeconds)
        || s.strengthSeconds < 0
        || s.strengthSeconds > 30
        || s.weaponLevel < 1
        || s.weaponLevel > 3
        || s.arenaWins < 0
        || s.arenaWins > 1000000
        || (s.bossClaimed && !s.bossDefeated))
      throw new IllegalArgumentException("Invalid profile");
    var p = new PlayerProfile();
    p.xp = s.xp;
    if (s.hp < 0 || s.hp > p.maxHp()) throw new IllegalArgumentException("Invalid health");
    p.hp = s.hp;
    p.hunger = s.hunger;
    p.coins = s.coins;
    p.bread = s.bread;
    p.apples = s.apples;
    p.huntKills = s.huntKills;
    p.bossDefeated = s.bossDefeated;
    p.bossClaimed = s.bossClaimed;
    p.strengthSeconds = s.strengthSeconds;
    p.weaponLevel = s.weaponLevel;
    p.arenaWins = s.arenaWins;
    return p;
  }
}
