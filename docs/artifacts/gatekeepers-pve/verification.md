# PvE 0.2 — reusable engine components and Windows release

Base: `95b951c249d59d2c7285701e6e2610933c71b75d`; local branch
`feature/gatekeepers-rpg`. No commits or releases pushed to GitHub.

## Results

| Check | Result |
| --- | --- |
| Whole reactor build | PASS |
| Unit tests | **834 tests, 97 suites, zero failures/errors** |
| Game unit tests | 23, including waves, single payouts, death/abandon, dodge, collisions and v1 migration |
| Shared engine additions | 3 melee tests, 3 minimap model tests |
| Existing desktop engine smoke | PASS, 120 frames, Box2D, camera, Yoga, pause/resize |
| Boss/food/shop/portal/resize smoke | PASS, 33 checks, 2038 frames |
| PvE keyboard/Stage walk from source | PASS, 24 checks, 1849 frames, 2 complete arena runs |
| Standalone executable JAR from `/tmp` on Linux | PASS, same 24 checks and 2 runs |
| **Windows ZIP: Start.bat + bundled Windows JRE25 + JAR, via Wine10** | **PASS, 24 checks, 1849 frames, 2 runs, exit0** |
| Windows DLL audit | PASS, PE AMD64 headers for libGDX, FreeType, LWJGL, GLFW, OpenGL, jemalloc, STB and Yoga |
| Fat JAR structure | PASS, Main-Class/native access/Multi-Release, font, merged Yoga SPI and library licenses |
| Windows ZIP integrity | PASS, launcher, Java.exe and server/jvm.dll included |

Windows compatibility was exercised using the extracted release archive's
actual `Start.bat`, private **Temurin25.0.4.1+1 Windows x64 JRE**, native Windows
DLLs and JAR. Wine10 used Xvfb/Mesa llvmpipe (LLVM19.1.7). No physical
Windows10/11 PC or hardware GPU was available; Wine does not establish
compatibility with every Windows driver, display configuration or antivirus.

[Windows log](windows-run.txt) · [Windows checks](windows-checks.txt) ·
[Linux standalone JAR](standalone-jar.txt) · [Build](build.txt) ·
[PvE checks](verification.txt).

Wine setup initially lacked its external zlib DLL; this blocked starting
Windows Java before any game code. The private test prefix was completed,
then Windows Java and the full packaged game passed. No Wine files or
workarounds are shipped in the release. `GetProcessGroupAffinity` warnings
are Wine limitations; LWJGL's deprecated Unsafe warning also appears on Linux.

## What was exercised

The PvE driver starts with the normal new profile, walks from spawn to the
village portal using keyDown/keyUp, starts three waves, attacks through
normal keyboard/mouse handling and uses Shift. It does not set enemy HP,
inject rewards or teleport the player outside the game's own portal rules.

After winning, it claims the reward once, returns, walks to the merchant,
clicks the real Yoga button to buy a sword upgrade, repeats the whole arena,
claims another reward, completes the repeatable hunt, buys the final upgrade
and bread, opens inventory, pauses, and verifies a disk roundtrip.

The older boss smoke retains explicit position setup for deterministic
boss/portal/resize checks; this is separate from the full walking PvE test.
Both use actual rendering and simulation. New smokes use isolated profiles.

## Shared versus game-specific

- `javity-ui`: new MinimapModel/Minimap, with borrowed terrain texture and generic
  world markers. It is not tied to Gatekeepers coordinates or content.
- Existing engine ProgressBar now draws all game meters and damage trails.
- `javity-core`: new MeleeAttack2D handles WORLD timing and cone/circle geometry.
- Game module retains AI, damage values, cleave count, obstacles, dodge policy,
  waves, economy, rewards and profile persistence. It does not claim Box2D use.

[Contracts and examples](../../minimap-and-melee.md).

## Review

Author self-review, no delegation per repository AGENTS.md. Regression tests
were observed RED before implementation. A final reusable-melee review found
overflow when normalizing a very large finite direction and allowed queries
after disabling a strike; a reproducing test failed before the correction and
the full suite passed afterward.

The full-loop test initially pressed movement before the native startup focus
notification, which correctly released held keys. The driver now waits for
startup and reasserts its intended keys after a focus notification. Gameplay
focus-loss behavior remains release/stop. Both source and packaged tests pass.

Deliberate boundaries: local PvE only; one class, one chapter plus arena; no
audio, multiplayer or arbitrary SceneJson codec for the new melee component.
Minimap is a programmatic native widget, not a newly registered TSX tag.

## Captures

Original framebuffer PNGs, copied without edits:

- [Arena ready](01-arena-gotowa.png), [wave1](02-fala-1.png), [wave2](02-fala-2.png), [wave3](02-fala-3.png).
- [Victory](05-zwyciestwo.png), [sword upgrade](06-ulepszenie-miecza.png), [second run](07-druga-proba.png).
- [Second victory](08-drugie-zwyciestwo.png), [maximum sword](09-maksymalny-miecz.png), [inventory](10-ekwipunek.png).
- **Windows package via Wine:** [wave3](windows-fala-3.png), [sword upgrade](windows-ulepszenie.png).
