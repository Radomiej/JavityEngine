# Whole-goal independent final review — runtime batch

Reviewed immutable range `0c9a236..41518e9a3210e5f7fb05069994ab4619f58d7b8d` in `/workspace/JavityEngine`.

**Spec verdict: PASS with minor corrections below. Quality verdict: PASS with minor corrections below.** No P0/P1/P2 integration defect found. The complete actionable set is three P3 items, including both explicitly deferred findings. They can be addressed in the authorized single final fix wave and then receive one scoped re-review; they do not justify reopening the completed task loops.

## Complete actionable set

### P3 — Bound the demo's obsolete failed retry leases

Location: `javity-desktop/src/main/java/org/javity/demo/AssetStreamingDemo.java:173–175` (`requestMissing`).

Reproduction by deterministic source trace: release any pinned model/prefetch so the missing request can run; press E, let the request become FAILED and finishFrame roll it back, then repeat. Each retry replaces `missing` without closing the prior failed lease. `AssetStreams` retains every open lease in its ownership set, so superseded Entry/Throwable graphs accumulate until scene disposal, even though the demo no longer exposes them. This is Java-side retention, **not a GPU resource leak**. Failure rollback already releases physical ownership correctly.

Fix: retain at most the current request and immediately previous FAILED lease, closing each older retired lease. Preserve the current proof that issuing a retry does not mutate the previous FAILED generation. Prefer obtaining a new request before retiring history so a synchronous rejection does not accidentally discard the inspectable current state. No service API or cache policy change is needed.

Focused verification: run several failure/retry cycles; immediately previous remains FAILED, older handles become CLOSED and leave the service's open-lease ownership set; ordinary retry and scene close still succeed. A native probe extension can observe the existing exact action path without adding public production testing APIs.

Disposition of carried finding: **confirmed, recommend fix in final wave**.

### P3 — Discover the first light added to the current selection

Location: `javity-ui/src/main/java/org/javity/ui/inspector/LightAuthoringPanel.java:112–117` (`update`).

Reproduction by deterministic source trace: select an object without a Light3D and update the overlay. Add its first PointLight3D while retaining the selection. `current == selected` and cached `light == null`, so neither reload branch executes. The panel remains hidden until reselection or an explicit host `load()` call. This undermines runtime component authoring but does not affect the shipped scenes' precreated lights.

Fix: when cached light is null and the same selected object now contains a Light3D, reload once. Preserve non-null drafts rather than reloading every frame or resetting user edits on unrelated component changes.

Focused verification: start with a selected lightless object; add its first light, initialize/update normally, and assert discovery/visibility without reselection. Edit a draft, perform an unrelated update/component insertion, and ensure the draft is retained. Removing the active light and removing/readding the first light should continue to refresh safely. Keep targeted focus release on hide.

Disposition of carried finding: **confirmed, recommend fix in final wave**.

### P3 — Label the preserved FPS checkpoint as historical

Location: `docs/artifacts/voxel-fps/README.md:28–32`.

The sentence “The current entry frame is task2-idle.png” is stale after VoxelDemo adopted the scene environment. The environment README explicitly identifies these Task 2 files as historical and deliberately restored; current scene-lighting captures are separately owned under `docs/artifacts/environment/`. The old image and its pixel metrics are valid evidence for their checkpoint, but the current wording assigns them the wrong provenance.

Fix: identify the Task 2 checkpoint (for example `7a1d475`) and link the later scene-environment FPS capture and README. Preserve every historical PNG/JSON rather than regenerating them to match the wording. Update nearby equivalent “current” wording in `docs/avatars.md` if useful for consistency. This is a documentation correction, not a request for new screenshots or another native run.

Verification: check the relative links and ensure the historical artifact blobs remain unchanged.

## Whole-goal integration findings

- **Readable FPS and input:** actual authored hand, wooden shaft and cyan pickaxe head use one source model/pose/socket. The camera-local base and recoil do not mutate gameplay TRS or introduce another animator. E/LMB held guards, release handling before UI consumption, first-click capture, UI/WORLD/native-focus guards and F5 shared-pose behavior survive tools and scene-environment integration. Free-fly focus remains detached from player feet/look; returning preserves the controller state except the documented obstructed-body recovery. Final voxel/Bullet native gates cover their actual input chains and projected resize bounds.
- **Tools and Feel ownership:** inspector/Stage/gizmo/gameplay ordering and both processor removals remain consistent. Search/collapse/history bind to Game identity. Feel owns its exact Playback, uses existing managers and Game clocks, and hiding its separate root releases only its descendant focus. Light drafts use component identity, selection epoch and expected state, validate before writes, and share chronological undo/redo with transforms. Socket/light diagnostics borrow canonical transforms and resources. The first-light discovery edge is the only confirmed new authoring defect.
- **Streaming:** typed leases preserve an exclusive configured-empty manager, one admitted root transaction, deterministic priority/FIFO, conservative resident reservations, unpinned LRU cache and finishFrame-only eviction. Cancelled generations cannot republish into fresh leases; rollback freezes and restores shared dependency identity/refcounts, clears known abandoned metadata/ModelData, and drains outside the manager monitor. GameSystem's default no-op disposal precondition preserves existing behavior while rejecting direct wrong-thread AssetStreams disposal before state mutation. Demo renderer detach precedes final lease release. The retention finding is confined to demo failure-handle history.
- **Scene rendering and recovery:** selected scene Environment reaches ordinary draws and both instance fallback paths. SceneLightingAttribute remains present and is rejected by the old instance eligibility rules, so new light/fog semantics are retained at the documented performance cost. Legacy scenes and separately lit viewmodels remain isolated. The retained shader provider registers the new uniforms and keeps scene/legacy cache variants separate. Fog is dispatched outside the normal-dependent lighting path, so unlit ordinary models receive it. Actual scene-skinned textured pose and alpha-test depth-hole controls exist in the final native probe, rather than being inferred from legacy avatar tests.
- **Sky and cycle:** sky runs before world batching, preserves caller GL state within the explicitly accepted GL20 pristine-slot contract, owns only its shader/tiny mesh and borrows cubemaps. Recovery refreshes actual locations and rebuilds its owned mesh even when locations do not change, fixing the previously reported VAO failure. WORLD cycle and typed light edits remain compatible with pause, scene ownership and borrowed cubemap identity. Viewmodel/HUD isolation and unchanged/moved-location recovery are actually covered.
- **Authoring and scope:** the new functionality remains ordinary Java/GameObject/Component plus scene systems and explicit owner handles. No extra ECS, global scene, reflection editing, automatic live ModelInstance replacement or implicit second scheduler was introduced. Foliage is clearly an investigated proposal awaiting approval, with no implemented classes/wind promised. Documentation retains local-only streaming, estimates, ordinary instancing fallback, forward-lighting limitations and explicit Android feature caveats.

## Review method and evidence

Read AGENTS.md, accepted next-runtime-batch specification, all four implementation plans and four ignored SDD ledgers/reports/reviews, including scoped fix reviews; inspected the actual full-range change inventory/package and changed production source, affected tests/native probes, integration call sites and public documentation. Examined the final upright scene-environment FPS, light-authoring and streaming READY images directly. Prior task reviewers' wider image inspections remain checkpoint evidence; this review does not claim to have reopened every historical PNG.

Root independently ran the fresh final build/native gate on reviewed HEAD. Inspected `/tmp/javity-runtime-final-green.log`: **BUILD SUCCESSFUL in 47s, 110 tasks (24 executed, 86 up-to-date)**. It includes environment/sky GL20+GL30, asset streaming, voxel, inspector, Bullet, avatar GL20+GL30, Feel, postprocess GL20+GL30, Shapes and static/skinned instancing supported/fallback native PASS. Independently recounted retained default-reactor XML: **805 tests / 91 suites / zero failures, errors or skips**. This is the test inventory accompanying an incremental successful build, not a claim of a fresh execution of every up-to-date suite.

`git diff --check 0c9a236..41518e9` passed; inspected working tree clean and HEAD correct after root restored the explicitly enumerated generated historical outputs. No duplicate broad build/native execution was necessary for the source-proven minor issues. This reviewer made no product changes, commits or subagent calls; only this `/tmp` report was written.

**New Android streaming/environment runtime and real per-feature EGL recovery remain UNVERIFIED.** Root reports the separate final APK compilation on reviewed HEAD passed (4s / 84 tasks); that compilation cannot establish feature runtime. Desktop managed recreation is not actual Android EGL loss. Historical Android and instancing evidence retains its original scope; no new device, cloud-runtime, physical ARM or performance claim is made.
