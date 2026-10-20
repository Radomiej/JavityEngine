# Javity quality implementation plan

> Execution: supervised Sol agents; root integrates and verifies. User explicitly
> requested execution without further confirmations on2026-10-20.

**Goal:** implement the quality roadmap with working, independently verifiable
increments, preserving simple Java GameObject/Component authoring.

**Spec:** [review and roadmap](../engine-quality-review.md).
**Stack:** JDK25, libGDX1.14.2, optional Artemis2.3.0, pinned optional gdx-gltf.

## Constraints

- Preserve GameObject/Component and existing default rendering behavior.
- No new mandatory native/platform dependencies in core; optional glTF adapter.
- Preserve snapshot failure atomicity, cleanup ordering and context recovery.
- Never weaken screenshot tolerances, replace goldens to hide a failure or infer
  physical-device performance from software GL.
- Existing exported assets and current component APIs remain usable.
- Host-specific shader features require explicit capabilities/fallbacks.
- Root runs integrated Gradle; workers use isolated javac/JUnit for red/green.

## Task1 — scene ownership and committed events

- [x] Add regressions for cross-scene parenting in both attachment orders,
  standalone hierarchies, failed attachment reuse and parent removal.
- [x] Validate hierarchy attachment before mutating ownership; detach stale links
  while preserving local TRS and avoiding retention after cleanup.
- [x] Add regressions for failed listener/component callbacks, duplicate throwable,
  reentrancy, cancellation and removed/closed receiver eligibility.
- [x] Deliver each committed change to entry-snapshot eligible recipients;
  preserve deterministic order and aggregate errors after delivery.
- [x] Fresh review plus focused tests, then include in full reactor.

Files: core Component/Transform3D/GameObject and dedicated JUnit4 tests.

## Task2 — lighting allocation and failing desktop gate

- [x] Capture CPU snapshot allocation baseline with explicit JDK/workload/warmup.
- [x] Reuse staged light attributes/buffers, preserving borrowed Environment
  identity, capacity/overflow semantics and old snapshot after validation error.
- [x] Test count/capacity shrink, mutations, missing lights and invalid transforms.
- [x] Repeat identical benchmark, report residual allocations and measured scope.
- [x] Obtain/reproduce desktop native smoke failure; fix actual cause without
  relaxing assertions. Record blocked hosted evidence separately if applicable.

Files: lighting environment/attribute/tests, opt-in desktop benchmark; diagnosed
native-demo file only after cause established.

## Task3 — optional glTF/GLB and PBR

- [x] Pin and verify upstream gdx-gltf artifact/license and retain gdx1.14.2.
- [x] Add optional asset owner, independent borrowed instances, stable IDs/leases
  and validation/cleanup tests. Reject unsupported content explicitly.
- [x] Add narrow ModelRenderer ModelInstance and shader-provider extension seams;
  preserve default shader lifecycle/context recovery and safe instancing fallback.
- [x] Integrate PBR with actual GameObjects/transforms/camera/lights, not a second
  gameplay scene model. IBL and provider ownership must be explicit.
- [x] Import static/skinned fixtures with license; real GL screenshots, actual
  material/animation assertions and lifecycle checks.
- [x] Validate default reactor remains usable without the optional adapter.

Files: new javity-gltf module, renderer constructor seams, desktop demo/tasks and
licensed fixtures. New adapters do not imply TeaVM support until separately proven.

## Subsequent increments

- [x] Directional shadow pass, cast/receive policy and state/context recovery.
- [x] HDR/tone mapping after explicit render-target capability checks.
- [x] Reuse animation events and add translation-only root motion with an explicit
  movement sink; validate original exported Fox and existing avatar regression.
- [ ] Broader imported-character FPS/TPP/hand/tool and physical movement workflow.
- [x] Common reference scene: Fox Garden integrates PBR, directional shadow, HDR,
  independent original Fox instances, InputActions and existing runtime tools.
- [ ] Streaming/foliage budgets and general authoring/authority integration; the
  showcase uses ordinary grass/tree meshes and synchronous local glTF loading.

Each increment needs implementation, code review, real runtime evidence and a
scope-specific guide before its checkbox is closed. The overall AAA goal is not
closed by completion of the first three tasks.

## Final gates

- [x] Full reactor, optional ECS usage and focused real native smoke.
- [x] Separate Wasm build/runtime regression after shared core changes: fresh
  `orbitPackage`/`test`, Chromium framebuffer/input/resize/dispose, typed packaged
  Java/Wasm and synthetic MessagePort proofs.
- [x] Fresh scoped Sol reviews; resolve findings and update Bugs/TODO/guides.
- [x] Final Sol branch review and README/changelog integration; resolved MASK
  material-profile and alpha-cutoff integration findings with native regressions.
- [x] Commit independently reviewable increments; fetch, ordinary push and verify
  clean working tree and remote hash. Report remaining platform limits explicitly.

## Verified increment and remaining gates — 2026-10-20

Task1 was committed as `123bce6`; lighting work as `6efbf8d`. GPU ownership is `19e796a`, root motion `8ab0bf6`, fonts `fb27874` and
optional glTF/PBR/HDR/showcase `d4792b7`; a follow-up aligns shadow alpha-cutoff
semantics. The final evidence manifest identifies the complete source revision. Fresh scoped reviews resolved entry-time event eligibility, managed glTF
textures, UV transform conflicts, VAO recovery, point-range shader behavior,
framebuffer failure/disposal cleanup, retained root-curve edits and font-cache
retention. [Defects and closure evidence](../Bugs/2026-10-20-engine-quality.md).

The root's final integrated `build`, ECS `usageSmoke`, glTF GL30/GL20, original Fox
GL30/GL20, directional shadows, HDR, quality showcase and UI parity completed
successfully (`/tmp/javity-quality-final-integrated.log`). The subsequent full
`build`, `installDist` and `qualitySmoke` also passed; launching the installed
showcase outside the checkout loaded the packaged original Fox and passed. These
are Linux Mesa/Xvfb correctness gates, not a physical GPU performance result.

Fresh separate Wasm `orbitPackage`/`test` and actual Chromium framebuffer, input,
resize and disposal regression passed, together with the typed packaged Java/Wasm
and synthetic MessagePort proof. This preserves the existing experimental backend
gates; it does not validate the optional new PBR/shadow/HDR features on Wasm.

The desktop failure in CI39375230181 was diagnosed from recovered hosted logs:
native card-title ink79px versus browser74px. Fractional font metrics fix the
local replay against that exact reference without changed tolerances or goldens.
**BUG-UI-001 closed by fresh [Desktop39391819541](https://github.com/Radomiej/JavityEngine/actions/runs/39391819541), source`f2fafce`.**

Shadow and HDR support here requires desktop GL30; plain PBR/import proof also
ran on the libGDX GL20 path. The deliberate glTF profile rejects unsupported
features, spot/fog semantics and differing texture transforms on a shared UV set.
`GltfAssets` is synchronous and separate from budgeted `AssetStreams`. Root
motion extracts translation only through an explicit application-owned sink; no
rotational root motion, IK, retargeting or automatic physics movement is implied.
See [glTF](../gltf-assets.md), [shadows](../shadows.md), [HDR](../hdr-rendering.md),
[root motion](../root-motion.md) and [showcase](../quality-showcase.md).

Android/EGL, new-feature Wasm, production authority, large-scene budgets and
physical-device performance require separate evidence. This batch does not
certify AAA readiness or close the overall roadmap.

Final source is `cbe61b3`. Full older native regressions, legacy smoke and fresh
MM HTML comparison passed. The final cutoff fix passed full reactor plus native
shadow25-view proof; all1015 JVM tests in123 suites have no failures or skips.
The [curated evidence manifest](../artifacts/engine-quality/manifest.json)
records source and artifact hashes. Hosted workflow status remains separate.

Hosted final evidence on `f2fafce`: Desktop39391819541 (all native/imported GPU,
screenshot, installed-distribution and multiplayer gates), Android39391819590,
Wasm39391819573 and Pages39391818848 all completed SUCCESS. This closes the scoped
font gate. Broader platform/feature/performance limits above remain unchanged.
