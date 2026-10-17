package org.javity.gatekeepers;

import com.badlogic.gdx.math.Vector2;
import java.util.*;
import org.javity.combat.MeleeAttack2D;
import org.javity.core.*;

/**
 * GL-free first chapter and repeatable arena, composed from Javity movement and melee components.
 */
public final class RpgWorld extends Game {
  public enum ArenaState {
    READY,
    FIGHTING,
    RESTING,
    VICTORY
  }

  public final PlayerProfile profile;
  public final RpgCollision collision = new RpgCollision();
  private final GameObject player;
  private final CharacterController2D controller;
  private final MeleeAttack2D melee;
  private final List<Enemy> enemies = new ArrayList<>(),
      guards = new ArrayList<>(),
      arenaMobs = new ArrayList<>();
  private Enemy boss;
  private boolean inArena, arenaClaimed;
  private ArenaState arenaState = ArenaState.READY;
  private int arenaWave;
  private float restSeconds, dodgeLeft, dodgeCooldown, hitProtection;
  private final Vector2 intent = new Vector2(), dodgeDirection = new Vector2(1, 0);
  public final Vector2 facing = new Vector2(1, 0);
  private String message = "Witaj, Strażniku. Kup zapasy, przyjmij zlecenie i wyrusz na łąki.";
  public int hits, kills;

  public RpgWorld(PlayerProfile profile) {
    this.profile = Objects.requireNonNull(profile);
    player = createObject("Strażnik").setPosition(RpgContent.SPAWN_X, RpgContent.SPAWN_Y);
    controller =
        player.addComponent(
            new CharacterController2D() {
              private final Vector2 last = new Vector2(RpgContent.SPAWN_X, RpgContent.SPAWN_Y);

              @Override
              public void onAfterMove(float delta) {
                var p = getTransform().position;
                if (p.dst2(last) < 60 * 60) {
                  var body = new Vector2(last.x, last.y - 20);
                  collision.move(body, p.x, p.y - 20, 12);
                  p.set(body.x, body.y + 20);
                }
                if (inArena) {
                  p.x =
                      Math.max(
                          RpgContent.ARENA_LEFT + 24,
                          Math.min(RpgContent.ARENA_LEFT + RpgContent.ARENA_WIDTH - 24, p.x));
                  p.y =
                      Math.max(
                          RpgContent.ARENA_BOTTOM + 40,
                          Math.min(RpgContent.ARENA_BOTTOM + RpgContent.ARENA_HEIGHT - 24, p.y));
                }
                last.set(p);
              }
            }.setSpeed(190)
                .setWorldBounds(65, 100, RpgContent.WIDTH - 130, RpgContent.HEIGHT - 200)
                .setHalfExtents(16, 20));
    melee = player.addComponent(new MeleeAttack2D(90, .45f, .18f, 100));
    for (float[] point : RpgContent.SLIMES) spawnEnemy(point[0], point[1], false, false, false);
    boss = spawnEnemy(RpgContent.BOSS_X, RpgContent.BOSS_Y, true, false, false);
    for (int side : new int[] {-1, 1}) {
      Enemy guard =
          spawnEnemy(RpgContent.BOSS_X + side * 100, RpgContent.BOSS_Y + 70, false, false, false);
      guard.dormant = true;
      guards.add(guard);
    }
    for (int i = 0; i < 9; i++) {
      double angle = i * Math.PI * 2 / 9;
      Enemy mob =
          spawnEnemy(
              RpgContent.ARENA_X + (float) Math.cos(angle) * 165,
              RpgContent.ARENA_Y + (float) Math.sin(angle) * 150,
              false,
              i == 8,
              true);
      mob.dormant = true;
      arenaMobs.add(mob);
    }
    if (profile.bossDefeated()) boss.dead = true;
  }

  private Enemy spawnEnemy(float x, float y, boolean boss, boolean elite, boolean arena) {
    var object =
        createObject(boss ? "Król Slajmów" : elite ? "Brutal areny" : "Slajm").setPosition(x, y);
    var e = object.addComponent(new Enemy(x, y, boss, elite, arena));
    enemies.add(e);
    return e;
  }

  public GameObject player() {
    return player;
  }

  public List<Enemy> enemies() {
    return List.copyOf(enemies);
  }

  public List<Enemy> arenaEnemies() {
    return List.copyOf(arenaMobs);
  }

  public Enemy boss() {
    return boss;
  }

  public boolean inArena() {
    return inArena;
  }

  /** Retained for first-release clients; the former frontier outpost is now the PvE arena. */
  public boolean atFrontier() {
    return inArena;
  }

  public ArenaState arenaState() {
    return arenaState;
  }

  public int arenaWave() {
    return arenaWave;
  }

  public float arenaRestSeconds() {
    return restSeconds;
  }

  public long arenaRemaining() {
    return arenaMobs.stream().filter(e -> !e.dead()).count();
  }

  public boolean inSanctuary() {
    return inArena ? arenaState != ArenaState.FIGHTING : player.transform.position.x < 690;
  }

  public boolean near(float x, float y, float radius) {
    return player.transform.position.dst2(x, y) < radius * radius;
  }

  public boolean nearShop() {
    return !inArena && near(RpgContent.SHOP_X, RpgContent.SHOP_Y, 110);
  }

  public boolean nearClasses() {
    return !inArena && near(RpgContent.CLASS_X, RpgContent.CLASS_Y, 100);
  }

  public boolean nearArenaGate() {
    return !inArena && near(RpgContent.ARENA_GATE_X, RpgContent.ARENA_GATE_Y, 90);
  }

  public String message() {
    return message;
  }

  public void say(String text) {
    message = text;
  }

  public float slashSeconds() {
    return melee.activeSeconds();
  }

  public float dodgeSeconds() {
    return dodgeLeft;
  }

  public float dodgeCooldown() {
    return dodgeCooldown;
  }

  public float hitProtection() {
    return hitProtection;
  }

  public void move(float x, float y) {
    intent.set(x, y).nor();
    if (!intent.isZero()) facing.set(intent);
    controller.move(intent, profile.hunger() == 0 ? 161.5f : 190);
  }

  public boolean dodge() {
    if (isPaused() || dodgeCooldown > 0 || profile.hp() == 0) return false;
    dodgeDirection.set(intent.isZero() ? facing : intent);
    dodgeLeft = .2f;
    dodgeCooldown = 1.8f;
    return true;
  }

  public boolean receiveHit(int damage) {
    if (inSanctuary() || dodgeLeft > 0 || hitProtection > 0 || profile.hp() == 0) return false;
    profile.damage(damage);
    hitProtection = .5f;
    return true;
  }

  public void attack(float x, float y) {
    if (inSanctuary() || profile.hp() == 0 || !melee.tryBegin(x, y)) return;
    facing.set(melee.directionX(), melee.directionY());
    var p = player.transform.position;
    enemies.stream()
        .filter(e -> !e.dead() && e.arena == inArena)
        .filter(
            e ->
                melee.intersects(
                    e.getTransform().position.x, e.getTransform().position.y, e.boss ? 40 : 16))
        .filter(
            e ->
                collision.clearLine(
                    p.x, p.y, e.getTransform().position.x, e.getTransform().position.y))
        .sorted(Comparator.comparingDouble(e -> e.getTransform().position.dst2(p)))
        .limit(3)
        .forEach(
            e -> {
              e.takeHit(Math.round(profile.weaponDamage() * profile.damageMultiplier()));
              hits++;
            });
  }

  public void enterArena() {
    if (inArena) return;
    resetArena();
    inArena = true;
    player.setPosition(RpgContent.ARENA_X, RpgContent.ARENA_Y);
    move(0, 0);
    melee.cancel();
    say("Arena PvE: E rozpoczyna 3 fale. Shift — unik. R — powrót do osady.");
  }

  public boolean startArena() {
    if (!inArena || arenaState != ArenaState.READY || isPaused()) return false;
    arenaWave = 1;
    activateWave();
    return true;
  }

  private void activateWave() {
    int begin = arenaWave == 1 ? 0 : arenaWave == 2 ? 2 : 5;
    int end = arenaWave == 1 ? 2 : arenaWave == 2 ? 5 : 9;
    for (int i = begin; i < end; i++) {
      arenaMobs.get(i).reset();
      arenaMobs.get(i).dormant = false;
    }
    arenaState = ArenaState.FIGHTING;
    say("Arena PvE — fala " + arenaWave + " / 3. Pokonaj wszystkich przeciwników!");
  }

  public boolean claimArena() {
    if (!inArena || arenaState != ArenaState.VICTORY || arenaClaimed) return false;
    profile.rewardArena();
    arenaClaimed = true;
    say("Arena ukończona! +60 monet i +100 EXP. Kup ulepszenie miecza u kupca.");
    return true;
  }

  public void leaveArena() {
    if (!inArena) return;
    boolean collected = arenaClaimed;
    inArena = false;
    resetArena();
    player.setPosition(RpgContent.SPAWN_X, RpgContent.SPAWN_Y);
    move(0, 0);
    melee.cancel();
    say(
        collected
            ? "Wróciłeś z nagrodą. Ulepsz miecz u kupca i podejmij kolejną próbę."
            : "Powrót do osady. Nieukończona próba nie przyznaje nagrody areny.");
  }

  private void resetArena() {
    arenaState = ArenaState.READY;
    arenaWave = 0;
    arenaClaimed = false;
    restSeconds = 0;
    for (var e : arenaMobs) {
      e.reset();
      e.dormant = true;
    }
  }

  public void interact() {
    if (isPaused()) return;
    if (inArena) {
      if (arenaState == ArenaState.READY) startArena();
      else if (arenaState == ArenaState.VICTORY) {
        claimArena();
        leaveArena();
      } else say("Dokończ falę. R pozwala przerwać próbę bez nagrody areny.");
    } else if (nearArenaGate()) enterArena();
    else if (near(RpgContent.BOARD_X, RpgContent.BOARD_Y, 110)) {
      if (profile.claimHunt())
        say("Zlecenie odebrane: +8 monet, +20 EXP. Możesz polować ponownie.");
      else if (profile.claimBoss()) say("Brama przełamana: +50 monet, +100 EXP!");
      else say("Zlecenie: pokonaj 6 slajmów. Postęp: " + profile.huntKills() + " / 6.");
    } else if (near(RpgContent.PORTAL_X, RpgContent.PORTAL_Y, 110)) {
      if (profile.bossDefeated()) enterArena();
      else
        say("Portal zamknięty. Najpierw pokonaj Króla Slajmów. Arena treningowa jest w osadzie.");
    } else if (nearClasses())
      say("Wojownik — klasa odblokowana. Łucznik i Strażnik pojawią się później.");
    else if (nearShop()) say("Kupiec: jedzenie i trwałe ulepszenia miecza.");
    else say("E — rozmowa lub interakcja. Shift — unik. Wejście na arenę znajdziesz w osadzie.");
  }

  @Override
  public void onUpdate(float delta) {
    hitProtection = Math.max(0, hitProtection - delta);
    dodgeCooldown = Math.max(0, dodgeCooldown - delta);
    if (dodgeLeft > 0) controller.move(dodgeDirection, 440);
    else controller.move(intent, profile.hunger() == 0 ? 161.5f : 190);
    dodgeLeft = Math.max(0, dodgeLeft - delta);
    if (profile.hp() == 0) {
      if (inArena) {
        inArena = false;
        resetArena();
      }
      profile.respawn();
      player.setPosition(RpgContent.SPAWN_X, RpgContent.SPAWN_Y);
      move(0, 0);
      melee.cancel();
      dodgeLeft = 0;
      hitProtection = 1;
      for (var e : enemies) if (!e.arena && (!e.boss || !profile.bossDefeated())) e.reset();
      say("Wróciłeś do osady. Zachowujesz monety i EXP, ale próbę areny zaczynasz od nowa.");
    }
    if (inArena && arenaState == ArenaState.FIGHTING && arenaRemaining() == 0) {
      if (arenaWave == 3) {
        arenaState = ArenaState.VICTORY;
        say("Wszystkie fale pokonane! E — odbierz 60 monet i 100 EXP, wróć do osady.");
      } else {
        arenaState = ArenaState.RESTING;
        restSeconds = 1.5f;
        say("Fala pokonana. Następna za chwilę — użyj jedzenia, jeśli potrzebujesz.");
      }
    } else if (inArena && arenaState == ArenaState.RESTING) {
      restSeconds -= delta;
      if (restSeconds <= 0) {
        arenaWave++;
        activateWave();
      }
    }
    profile.tick(delta, inSanctuary());
  }

  public final class Enemy extends Component {
    private final float spawnX, spawnY;
    private final boolean boss, elite, arena;
    private int hp;
    private boolean dead, dormant, summoned;
    private float respawnClock, attackClock, windup, hurtSeconds;
    private final Vector2 jumpTarget = new Vector2();

    private Enemy(float x, float y, boolean boss, boolean elite, boolean arena) {
      spawnX = x;
      spawnY = y;
      this.boss = boss;
      this.elite = elite;
      this.arena = arena;
      hp = maxHp();
    }

    public int hp() {
      return hp;
    }

    public int maxHp() {
      return boss ? 450 : elite ? 75 : 30;
    }

    public boolean isBoss() {
      return boss;
    }

    public boolean isElite() {
      return elite;
    }

    public boolean isArena() {
      return arena;
    }

    public String name() {
      return boss ? "Król Slajmów" : elite ? "Brutal areny" : "Slajm";
    }

    public boolean dead() {
      return dead || dormant;
    }

    public float windup() {
      return windup;
    }

    public Vector2 jumpTarget() {
      return jumpTarget;
    }

    public float hurtSeconds() {
      return hurtSeconds;
    }

    public void takeHit(int damage) {
      if (dead() || damage <= 0) return;
      hp = Math.max(0, hp - damage);
      hurtSeconds = .15f;
      if (!boss) {
        var p = getTransform().position;
        var away = p.cpy().sub(player.transform.position);
        if (!away.isZero()) {
          away.nor().scl(elite ? 12 : 22);
          collision.move(p, p.x + away.x, p.y + away.y, 14);
        }
      }
      if (boss && hp > 0 && hp <= 225 && !summoned) {
        summoned = true;
        for (var guard : guards) {
          guard.reset();
          guard.dormant = false;
        }
        say("Król przywołuje dwa slajmy! Unikaj oznaczonego skoku.");
      }
      if (hp == 0) {
        dead = true;
        respawnClock = 10;
        if (elite) profile.rewardElite();
        else profile.rewardMob(boss);
        kills++;
        if (boss) say("Król pokonany! Portal otwarty. Odbierz nagrodę przy tablicy.");
        else if (profile.huntKills() == 6)
          say("Zlecenie gotowe! Wróć do tablicy w osadzie po nagrodę.");
      }
    }

    private void reset() {
      hp = maxHp();
      dead = false;
      windup = 0;
      attackClock = 0;
      respawnClock = 0;
      hurtSeconds = 0;
      getGameObject().setPosition(spawnX, spawnY);
      if (boss) {
        summoned = false;
        for (var guard : guards) guard.dormant = true;
      }
    }

    @Override
    public void onUpdate(float delta) {
      hurtSeconds = Math.max(0, hurtSeconds - delta);
      if (dormant) return;
      if (dead) {
        if (!boss && !arena) {
          respawnClock -= delta;
          if (respawnClock <= 0) reset();
        }
        return;
      }
      if (arena != inArena) {
        windup = 0;
        return;
      }
      if (boss && player.transform.position.dst2(spawnX, spawnY) > 400 * 400) {
        reset();
        return;
      }
      if (inSanctuary() || profile.hp() == 0) {
        windup = 0;
        return;
      }
      var position = getTransform().position;
      var target = player.transform.position;
      float distance = position.dst(target);
      attackClock = Math.max(0, attackClock - delta);
      if (windup > 0) {
        windup = Math.max(0, windup - delta);
        if (windup == 0) {
          if (boss) position.set(jumpTarget);
          if (position.dst(target) < (boss ? 100 : elite ? 64 : 52)
              && collision.clearLine(position.x, position.y, target.x, target.y))
            receiveHit(boss ? 14 : elite ? 10 : 5);
          attackClock = boss ? 1.8f : elite ? 1.3f : 1.1f;
        }
        return;
      }
      float aggro = arena ? 1000 : boss ? 310 : 185;
      if (distance > aggro) {
        var home = new Vector2(spawnX, spawnY).sub(position);
        if (home.len() > 8) {
          home.nor().scl(35 * delta);
          collision.move(position, position.x + home.x, position.y + home.y, 14);
        }
        return;
      }
      if (attackClock <= 0 && distance < (boss ? 260 : elite ? 54 : 43)) {
        jumpTarget.set(target);
        if (boss) jumpTarget.sub(spawnX, spawnY).limit(180).add(spawnX, spawnY);
        windup = boss ? .85f : elite ? .55f : .38f;
        return;
      }
      if (distance > (boss ? 130 : elite ? 47 : 36)) {
        var velocity = target.cpy().sub(position).nor().scl(delta * (boss ? 40 : elite ? 72 : 65));
        collision.move(position, position.x + velocity.x, position.y + velocity.y, 14);
      }
      if (!arena) position.x = Math.max(715, position.x);
    }
  }
}
