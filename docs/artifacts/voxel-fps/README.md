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
