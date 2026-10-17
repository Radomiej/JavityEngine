# PvE, reusable widgets and Windows JAR

User authorization: extract the reusable parts, finish mob combat and repeatable PvE, ship a runnable Windows JAR; no PvP/multiplayer changes. Continue in the existing local branch, preserve prior work, no delegation or remote pushes.

## Design

- javity-ui: reusable MinimapModel (finite world bounds, projection, copied markers) and Minimap widget (borrowed UiTheme/background TextureRegion). Replace hand-drawn meters with existing ProgressBar, including mob/boss/quest meters.
- javity-core: reusable MeleeAttack2D component: configured cooldown, range, cone, active slash; WORLD clock and no GL. The game owns damage/rewards.
- Game: collision-aware movement and line of sight; melee cleave/knockback; Shift dodge and short hit protection. No Box2D claims: reuse CharacterController2D with explicit game collision resolution.
- Repeatable PvE arena: entrance in village, READY -> ACTIVE waves1/2/3 -> WON -> collect reward and return. Abandon/death resets pending reward. Reward60coins/100XP; buy weapon upgrades60/120; persist upgrade and arena wins, migrate v1 saves. Existing boss and quests remain playable.
- Windows executable fat JAR: manifest Main-Class and native-access attribute, merge service providers, preserve library licenses, bundle Windows x64 Yoga/LWJGL/libGDX DLLs and Linux natives for same-artifact runtime test. Include Start.bat and Polish instructions. No Windows success claim without Windows runtime evidence.

## Tasks

- [x] Tests RED/GREEN: reusable minimap coordinates/markers and melee timing/geometry; integrate native widgets.
- [x] Tests RED/GREEN: collisions/LOS, dodge, arena wave transitions, single reward, abandonment, death reset, weapon purchases and save migration.
- [x] Integrate arena art, markers, HUD, native controls and repeatable run; ensure walkable routes.
- [x] Native tests: actual keyboard/mouse gameplay, arena, return/shop/upgrade/replay; pixel captures, resize, pause/focus, persistence.
- [x] Whole reactor build; package and inspect executable JAR, run java -jar from outside checkout; export Windows JAR/ZIP and updated source/docs.

## Validation

Unit tests run before implementation and again after each task. GL tests use Xvfb/Mesa. Existing native engine smoke remains mandatory after shared UI/core additions. Run whole reactor before completion. Review package contents for DLLs, font resources, service providers, licenses and executable manifest. Retain source and logs locally.
