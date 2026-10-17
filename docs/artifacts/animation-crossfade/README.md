# Pose clip crossfade GPU proof

The images and `j-pose-crossfade-proof.json` come from the native `:javity-desktop:pluginsSmoke` run using the procedural two-bone skinned fixture in `GeneratedSkinModel`. The fixed viewport compares start and midpoint of the first base fade, then start, midpoint, and endpoint of an interrupted destination fade. A same-state capture with the second renderer disabled supplies the background negative control before its visible region is compared. The fixture then checks a masked upper layer while the lower body and second skinned instance remain stable.

- `j-pose-base-fade-start.png` and `j-pose-base-fade-mid.png` show the first base transition.
- `j-pose-base-fade-interrupt-start.png` and `j-pose-base-fade-interrupt-mid.png` show continuity and continued progress after replacement; `j-pose-base-fade-end.png` is the interrupted destination's endpoint and matches immediate playback of that exact target phase.
- `j-pose-second-instance-disabled.png` shows the same-scene negative control with the second renderer disabled. The proof JSON records zero changed pixels between repeated disabled captures and the positive foreground pixel difference after re-enabling it.
- `j-pose-layer-fade-start.png`, `j-pose-layer-fade-mid.png`, `j-pose-layer-fade-paused.png`, and `j-pose-layer-fade-end.png` show the masked transition and its WORLD pause.
- `j-pose-crossfade-proof.json` records the pixel deltas, positive/negative second-instance counts, endpoint comparison, paused step duration, and lifetime/canonical-transform assertions.

The smoke asserts the authored local TRS midpoint and endpoint in addition to changed skin pixels and bone matrices. It verifies a 0.3-second `game.step` while WORLD is paused, stable same-viewport pixels for the untouched instance, and restoration of bind pose after owner removal. This fixture is procedural; it does not claim imported production-rig validation or root-motion, retargeting, additive, IK, or motion-matching support.
