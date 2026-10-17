# Runtime expansion execution plan

Authority: docs/work-queue.md and the avatar/physics3d specs. Continue on the existing modernization branch and preserve all prior local changes.

## Global constraints

Use typed APIs, explicit ownership, scene identity guards and explicit serialization codecs. No arbitrary reflection, silent skipped components, fake platform support or hiding skeleton bones by zero scale. Integrate stages in queue order. Tests must exercise behavior and native rendering where relevant. User authorizes implementation without additional confirmation. Commits/push remain separate from implementation verification.

## Task 1: Editable runtime inspector

Implement typed transform editing, finite-value validation, undo/redo and stale scene/object/component rejection in the existing inspector. Physics-owned transforms must be rejected unless a correct explicit teleport adapter is available. Integrate native controls and CPU tests; preserve read-only inspection. Own inspector package/tests and runtime-tools documentation.

## Task 2: Transform gizmos

Build on Task 1 commands for translation, rotation, scale, local/world space and snapping. Respect UI input priority; reject singular parent conversions and stale handles. Verify math and real native interaction.

## Task 3: Explicit runtime export

Add full export validation reporting every unsupported component/resource before writing, using SceneJson codecs. Save to an explicit target with atomic replacement; failures leave existing files unchanged. Label runtime export separately from authoring assets. Test round-trip, unsupported aggregation and failures.

## Task 4: Avatar rendering

Implement the avatar design, subtree masks, per-view geometry filtering and FPS/TPP camera integration with native visibility evidence. Keep skinning and animator clocks canonical.

## Task 5: Physics 3D

Implement the Bullet design as an optional module, actual native tests, then chunk adapter. Do not introduce a second movement authority.

## Task 6: Feedback and UI coverage

Close documented concrete Feel/animation/UI gaps with bounded APIs and tests, then update the feature matrix.

## Task 7: Android

Fix confirmed GLES issues, add maintained launcher/platform adapters and verify with an actual device/emulator. Keep hardware/environment blockers explicit.
