# Authored voxel hand and pickaxe: Task 1

`before.png` preserves the FPS capture from commit `0c9a236`.
`idle.png`, `tool-hidden.png`, and `tpp.png` are real 1280×720 native captures
from the updated voxel smoke. All four PNGs are upright: framebuffer pixels
are flipped vertically by `PixmapIO.writePNG(..., true)` when originally saved.

The FPS view selects RIGHT_ARM and the existing tool slot. A single owned mesh
contains the wooden handle and stepped cyan head; TPP uses the same canonical
avatar, bone, socket, and animation clock. The fixed camera-local transform is
independent of the player's world transform.

`composition-proof.json` records a matched visible/hidden capture: no game step
or camera change occurs between images. The counted cyan head and wooden shaft
pixels change when the tool slot is hidden, while the hand pixels remain.
Counts use the required lower-right region (x .55–.95, y .05–.65, bottom-left).
The CPU projection test checks every authored hand/tool cuboid corner at both
1280×720 and 960×720 with FOV 72 and near .03.

Validation: `./gradlew test :javity-desktop:avatarSmoke
:javity-desktop:avatarGl30Smoke :javity-desktop:voxelSmoke` passed, including the
existing wall-depth, shared-clock, F5, socket, resize, and cleanup controls.
The avatar sidecar smoke explicitly exercised GL20 and GL30. These are desktop
proofs; Android needs its separate runtime gate. Tool-use feedback is Task 2.

## Task 2: mining, swing, and viewmodel recoil

Run `./gradlew :javity-desktop:run --args='--voxels'`. The current entry frame is
`task2-idle.png`: a right hand, wooden shaft, and stepped cyan pickaxe head with
an unobtrusive 380×168 HUD. The initial controller pitch is now −22° so the
crosshair reaches terrain. The Task 1 captures above are historical and remain
unchanged; their camera-local hand/tool base and source geometry are still used.

The first LMB click captures the cursor. A subsequent press removes one actual
raycast block and starts the canonical right-arm swing plus a finite viewmodel
camera recoil; E starts just the swing. Holding either input does not restart
it. WORLD pause/freeze, focused UI, and native focus loss gate use. Focus loss
also cancels the owned recoil. Walking/sprinting use the existing canonical arm
motion; no extra animation clock or scheduler was added.

`task2-proof.json` records the native controls and per-capture measurements:

| Capture | Evidence |
| --- | --- |
| `task2-action-start.png`, `task2-action-mid.png`, `task2-action-end.png` | LMB changes a real target cell; visible swing and return |
| `task2-action-paused.png` | Same pose and pixel counts while WORLD is paused; resumes once |
| `task2-wall.png` | Near world wall covers the center while hand, shaft and head remain visible |
| `task2-wall-tool-hidden-control.png` | Matched shaft/head removal with the hand retained |
| `task2-wall-no-viewmodel-control.png` | Matched terrain-only negative control |
| `task2-recoil-disabled-control.png` | Same canonical pose with the viewmodel contribution omitted; tool pixels change while world camera and clocks stay fixed |
| `task2-tool-hidden.png` | T hides shaft and head together |
| `task2-f5-fps-during-use.png`, `task2-f5-tpp-during-use.png` | F5 preserves canonical pose, time and shared tool/socket |
| `task2-resize-960x720.png` | Actual native window resize, with matching framebuffer dimensions |
| `task2-walking.png`, `task2-sprint.png`, `task2-jump.png` | Hand/tool remain visible and beyond the viewmodel near plane |

Coordinates below use bottom-left normalized framebuffer space. Separate hand,
shaft and head masks all require >100 isolated matching pixels. Hand pixels
must differ between tool-hidden and no-viewmodel draws inside x .55–.99,
y 0–.50. Shaft pixels must change when the tool slot is hidden inside x .55–.99,
y .05–.75; head pixels use x .45–.99, y .15–.75. Their unchanged material-color
tests identify skin, wood and cyan. The smoke independently asserts the actual mined cell change, then restores
the wall fixture before matched captures. All comparisons share one game step and
camera; no animation clock advances between captures. Head/shaft counts become
zero when hidden. The native posed hand and every tool cuboid corner are also
transformed through the canonical bone and actual viewmodel camera, requiring
camera depth > near + .10 throughout the action and in the captured locomotion
states (near is .03). All PNGs are written upright with explicit flipY=true.

The close-during-swing control starts a live recoil, confirms its lease, and
closes the scene twice. The source pickaxe VBO and IBO must each be deleted
exactly once. The chunk adapter's close must preserve its borrowed atlas; the
host's subsequent close must delete its owned atlas and avatar texture. The
presentation manager and camera provider must retain zero active contributions.
These exact observations are recorded in `task2-proof.json`; they do not claim
tracking of every renderer resource. `task2-scene-proof.json` preserves the
broader voxel controller, seam-edit, camera and cleanup checks.

Verification: full `./gradlew build :javity-desktop:voxelSmoke
:javity-desktop:avatarSmoke :javity-desktop:avatarGl30Smoke`, real Xorg/Mesa.
The avatar sidecar probes confirm actual GL20 and GL30; these remain desktop
proofs, with no new Android runtime claim.
