# Foliage and imported-avatar authoring — next quality increment

Continue the accepted roadmap without further confirmations. Base: `9f840ad`.
Goal: simple Java components, deterministic bounded foliage, and a real imported
avatar workflow using the existing canonical animation/view infrastructure.

## Scope and contracts

1. Implement the static merged-patch approach from
   `../superpowers/specs/2026-10-16-foliage-components-design.md`.
   `FoliagePatch3D` is a CPU-only Component with grass/bush factories, validated
   area/count/seed/scaleRange/cutoff and revision. It borrows an immutable atlas
   descriptor/texture. Optional material template is copied, not owned; the
   default supports ordinary libGDX lighting and cutout. No wind/LOD/terrain
   dependency, hidden per-blade GameObjects or implied PBR instancing.
2. Renderer owns lazy generated mesh/model caches, revision-only transactional
   rebuilds, conservative bounds/culling and cleanup even with no camera.
   Patches participate in the ordinary world path. GL20 remains supported.
   Custom PBR materials must be explicit; no silent material conversion.
3. Imported-avatar authoring builds on AvatarDefinition/Binding/View and existing
   camera/animation APIs. Diagnose actual import metadata and preserve skeleton
   pose when hiding geometry. Do not invent automatic humanoid classification.
   Keep new convenience APIs narrow and verify real glTF data/leases/resources.
4. Ship original licensed foliage atlas/geometry and pleasant composed smoke/demo.
   Real pixel tests prove cutout/depth/front-back/environment/lifecycle. Test
   deterministic generation, caps/index splitting, ownership and rebuild errors.
5. Full reactor, focused old/native regressions, Wasm shared-core regression,
   fresh Sol reviews, docs/Bugs/TODO, commits and ordinary push.

## Execution ownership

Independent CPU foliage, renderer integration and imported-avatar investigations
use Sol workers. Root owns integration, task wiring and final evidence. Shared
interfaces are agreed before consumer edits; production build is serialized.

## Limits

Software GL proves correctness, not physical GPU speed. Android/Wasm support of
new functionality requires separate runtime evidence. No AAA certification.

## Completed and verified

- [x] CPU atlas/component/snapshots and deterministic capped/split geometry.
- [x] Renderer-owned transactional cache, bounded retry and pass-aware culling.
- [x] Original CC0 atlas/branching trees, explicit PBR helper and composed demo.
- [x] Exact imported metadata/profile and original CC0 humanoid GLB proof.
- [x] Independent task reviews and whole-batch review; offscreen shadow casters,
  capture failure cleanup and demo exception-continuing teardown fixed.
- [x] Full reactor1041 tests/129 suites without failures/errors/skips.
- [x] Native GL30 and forced GL2.1 foliage/avatar smokes; cutout/depth/lighting,
  independent animation/bones/socket/leases and resource lifetime checks.
- [x] Old world/avatar/instancing/presentation/Bullet/environment/plugin regression.
- [x] Fresh installDist; foliage and imported-avatar smoke outside checkout from packaged classes/assets.
- [x] Existing Wasm build/tests plus actual Chromium pixels/input/resize/disposal
  and packaged typed synthetic-host regression. This is not new-foliage Wasm proof.

Root final integrated gate:68s/135tasks; post-cleanup fullbuild+installDist and
foliage smokes:20s/121tasks. Source groups: `d600c20`, `7211235`, `7cf25f4`.
[Curated evidence/hash manifest](../artifacts/foliage/manifest.json),
[findings and fixes](../Bugs/2026-10-20-foliage-avatar.md).
Hosted CI remains separate until a fresh run completes on the published source.
