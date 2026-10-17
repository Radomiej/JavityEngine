# Reusable minimap and melee components

Added while building the Gatekeepers PvE game. No arena rules, item prices,
quests or player profiles are baked into these shared APIs.

## Minimap — javity-ui

`MinimapModel` is GL-free. World coordinates use bottom-left/Y-up, matching
the Javity2D world. Width/height must be positive and finite. Markers have
stable string IDs, finite world positions, RGBA8888 colors and positive UI sizes.
`put` replaces a marker; `remove` deletes it. `markers()` returns an immutable
snapshot. Coordinates outside the world clamp to the nearest map edge.

```java
var data = new MinimapModel(0, 0, 2600, 1600);
var minimap = new Minimap(theme, data);
minimap.setMap(terrainRegion); // Optional, borrowed texture; region geometry is copied.
minimap.setBounds(20, 20, 208, 130);
stage.addActor(minimap);
data.put("hero", hero.transform.position.x, hero.transform.position.y, 0xffeeaaff, 7);
```

The widget preserves world aspect ratio and letterboxes rather than stretching.
Marker centers are clamped so their visible squares remain inside that rectangle.
It restores the caller's Batch color and does not own a texture, shader or font.
Create/use widgets and UiTheme on the UI/GL thread. Remove the minimap before
disposing a borrowed background or theme. This is a 2D local overview, not a
geographic MapLibre adapter, fog-of-war system or network interest manager.

Gatekeepers supplies the actual terrain texture plus hero, enemy, shop, board
and arena markers. The same widget supports other game-specific data.

## Existing ProgressBar

All Gatekeepers meters now use `org.javity.ui.controls.ProgressBar`:
health, hunger, EXP, hunt, boss, mob health and arena waves. They use its
foreground/trail animation, range clamping and Javity rectangle rendering.
The game retains a small scene-owned cache of bars and advances them using
the HUD's unslowed UI delta. In another app, attaching a bar to Stage advances
it automatically. Do not advance it a second time manually in that case.
Close bars before disposing their borrowed UiTheme.

## MeleeAttack2D — javity-core

```java
var melee = hero.addComponent(new MeleeAttack2D(90, .45f, .18f, 100));
// Parameters: reach, cooldown seconds, active visual window seconds, cone degrees.
game.start();
if (melee.tryBegin(aimX, aimY)) {
    for (var target : targets) {
        if (melee.intersects(target.x(), target.y(), target.radius())) {
            // Your game's obstruction check, damage, hit deduplication and rewards.
        }
    }
}
```

Timing belongs to WORLD and pauses with Game. Disabled, inactive or uninitialized
components cannot begin a strike. Invalid/zero direction returns false without
spending the cooldown. A target is a world-space circle; its radius participates
in cone/reach intersection. Disabled or expired attacks cannot intersect.

`tryBegin` is the one-shot trigger; `intersects` is a query and can return true
throughout the active window. Apply damage once per target per strike, not on
every query/frame. The component intentionally does not own damage, health,
teams, physics, obstruction, targeting order or networking. Gatekeepers adds
line-of-sight checks, three-target cleave, knockback and progression damage.

## Verification

CPU tests cover map projection/bounds/snapshots/invalid data and melee
cooldown/pause/cone/reach/lifecycle, including large finite directions.
The native game smoke exercises the real widgets and combat components;
`pveSmoke` walks two complete arena/shop/upgrade loops using keyboard and
Stage input. Existing engine smoke and whole-reactor tests remain separate.
