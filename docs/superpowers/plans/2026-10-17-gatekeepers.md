# Strażnicy Bram — Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Zbudować lokalny, grywalny pierwszy rozdział RPG na zaakceptowanym Javity.

**Architecture:** Osobny moduł games/gatekeepers. Logika profilu i zapis nie zależą od GL; świat jest sceną Game z komponentami postaci i mobów. Renderer Javity wyświetla proceduralne sprite'y, a Scene2D/Yoga polski HUD.

**Tech Stack:** JDK25, Gradle9.8.1, libGDX1.14.2, LWJGL3.4.3, javity-core, javity-ui, desktopowy provider Yoga.

**Spec:** ../specs/2026-10-17-rpg-gdd.md, zatwierdzony przez użytkownika.

## Global Constraints

- Baza 95b951c249d59d2c7285701e6e2610933c71b75d; nie używać historycznego master.
- Desktop 2D, polski interfejs, wojownik, osada, slajmy, boss, sklep, dwie misje, portal, zapis.
- Bez symulowania gotowego multiplayer; późniejsze krainy oznaczać jako kolejne etapy.
- Jeden właściciel każdego zasobu; cleanup przed zamknięciem GL.
- Bez wysyłania commitów, publikacji lub delegowania pracy.

## Review Focus

- Ponowne trafienie martwego moba nie powiela monet ani EXP — test task2.
- Zakup bez pieniędzy i użycie pustego slotu nie zmieniają stanu — test task1.
- Pauza i osada nie zużywają głodu — test task1/task2.
- Uszkodzony zapis nie jest nadpisywany automatycznie — test task3.
- UI i utrata fokusu nie wywołują ataków/ruchu pod panelem — GPU smoke task4.

### Task 1: Profil, jedzenie, misje

Files: games/gatekeepers/build.gradle; src/main/java/org/javity/gatekeepers/PlayerProfile.java; src/test/java/org/javity/gatekeepers/PlayerProfileTest.java; settings.gradle.

Interfaces: PlayerProfile exposes hp(), maxHp(), hunger(), coins(), xp(), level(), bread(), apples(), bossDefeated(); buy(Food), eat(Food), damage(int), tick(float,boolean), rewardMob(boolean), claimHunt(), claimBoss(). Food=BREAD/APPLE.

- [x] Write tests for 30 starting coins, 3 breads, purchase4/use20hunger8HP, apple100, strength1.2 for30s, XP thresholds100/300, no duplicate quest claim.
- [x] Run :gatekeepers:test; observe missing behavior.
- [x] Implement clamped stats, cooldown-independent buffs and repeatable six-slime quest8coins20XP; boss quest50coins100XP once.
- [x] Run :gatekeepers:test; expected all profile tests pass.

### Task 2: Scéna a boj

Files: RpgWorld.java, RpgWorldTest.java, RpgContent.java in the same source roots.

Interfaces: RpgWorld extends Game; constructor(PlayerProfile); player(), enemies(), attack(float,float), interact(), inSanctuary(), nearShop(), message(). Enemy extends Component; takeHit(int), hp(), isBoss(). Player movement uses CharacterController2D. World accepts a normalized move intent via move(float,float).

- [x] Test one reward for mob death, attack range/cooldown, sanctuary protection, six kills/claim/reset, boss unlock and death reset.
- [x] Run tests and observe failures, implement simulation using Game.step and owned components, rerun.
- [x] Add deterministic respawns and telegraphed boss jump/obstacle-free arena; do not initialize GL in simulation.

### Task 3: Zapis profilu

Files: ProfileStore.java and ProfileStoreTest.java.

Interfaces: ProfileStore.load(Path) returns PlayerProfile; ProfileStore.save(Path,PlayerProfile) atomically writes a versioned profile. Invalid data throws IOException; host disables automatic saving after invalid-load failure.

- [x] Test roundtrip, missing file, corrupt numbers/version, unfinished quest progress and boss unlock.
- [x] Observe failures, implement bounded explicit fields using Properties and sibling atomic move, rerun.

### Task 4: Grafika, HUD i launcher

Files: RpgArt.java, RpgHud.java, GatekeepersApplication.java, GatekeepersLauncher.java, RpgSmoke.java; module Gradle run/smoke/installDist tasks.

Interfaces: RpgArt owns textures and attaches SpriteRenderer2D to existing gameplay objects. RpgHud owns Stage/Flex/UiTheme, renders profile and receives actions from app. App owns world/renderer/art/HUD and closes in lifetime order.

- [x] Build authored procedural forest/village/meadow/arena visuals; never generate one GL texture per frame.
- [x] Implement WASD/arrows, LPM/Space attack, E interaction,1/2food,I inventory,Esc pause; Stage gets input first.
- [x] Render native HUD, missions, shops, map, portal state and feedback; resize at1280×720/1440×900.
- [x] Native smoke drives actual input/actions: buy/eat, kill/claim, boss/portal, pause/focus, screenshots and framebuffer checks; exits with nonzero failure.

### Task 5: Odbiór i dystrybucja

Files: docs/gatekeepers.md; docs/artifacts/gatekeepers/*.png and verification.md; docs/TODO.md; README.md.

- [x] Run whole reactor build, new unit tests and existing desktop smoke.
- [x] Run :gatekeepers:smoke under Xvfb/Mesa; inspect real PNGs, fix clipping or unreadable elements.
- [x] Build :gatekeepers:installDist and portable ZIP; document controls, startup and limits.
- [x] Review whole diff, record actual test counts/results and remaining risks. No claim of multiplayer/Android readiness.

## Execution note

Użytkownik zaakceptował GDD i plan etapów oraz polecił „Rób”; wykonanie własne w tej sesji. Szczegółowy plan rozwija zaakceptowaną kolejność bez zmiany zakresu. Brak delegowania zgodnie z AGENTS.md. Praca na lokalnej gałęzi feature/gatekeepers-rpg w świeżym checkout; dokumenty nie wymagają zdalnego commita.
