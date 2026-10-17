# Decyzje integracji FPS, narzędzi, streamingu i otoczenia

2026-10-16. Pełny zapis decyzji z dzienników realizacji, w kolejności etapów. Powtarzające się dosłownie wpisy zebrano raz; podobne wpisy zachowano, aby nie zgubić uzasadnień. Każdy wpis podaje wybór i koszt. To uzasadnienia integracji, nie lista ukończonych funkcji. Aktualny zakres i dowody opisują dokumentacje modułów oraz work-queue.md.

- Ruling: continue current non-main modernization checkout with established owner-thread resources — user requests this branch and prior session prefers this checkout — isolation rests on one implementer at a time.

- Ruling: new scene-lighting attribute must explicitly force ordinary fallback in existing instance batches until an equivalent shader is implemented — old instancing supports stock forward subset only — costs instancing performance when new lights/fog are active, preserves correctness.

- Ruling: Task2 may set a slight initial downward controller/rig look using its typed look API, to make terrain reachable on startup. Camera-local viewmodel framing stays unchanged; annotate changed world composition. Existing canonical walking animation may provide bob; any extra presentation playback must have one owner and no second scheduler. Native proofs include walking, sprint and jump.

- Ruling: collapse/filter viewstate belongs RuntimeInspector, resets on supplied Game identity changes; SceneInspection.visibleRows query/set remains a pure projection and does not mutate caller sets — avoids surprising caller state edits — costs testing reset at inspector/session layer rather than attributing caller-owned state to SceneInspection. See preflight.md.

- Ruling: detach VoxelDemo free-fly debug camera from player Transform3D: focus/movement never copies camera position into player feet, and return to FPS restores camera from preserved controller state with existing safe-body recovery if terrain obstructs it — focus must not indirectly teleport gameplay on next frame or exit — costs changing the old debug-fly-to-player teleport convenience; document and test focus+hide+step+return with unchanged feet.

- Ruling: Task2 package-private FeelPreviewSession isolates exact playback ownership from native Stage without creating another scheduler/model — allows real CPU lifetime tests — costs one small source file beyond initial plan list.

- Ruling: dedicated hidden Feel GPU smoke window grows from256x256 to1000x760 while its world framebuffer remains256x256 — real unobscured panel-click/image evidence — costs a larger smoke window, preserves existing renderer tolerances.

- Ruling: use one physically admitted root transaction per owned AssetManager, wrapper queued requests retain priority/FIFO; maxInflight is an upper bound and stats report actual 0/1 — precise attribution and rollback preserve shared dependencies — costs parallel admission/latency, not supported loader algorithms.

- Ruling: isolate exact abandoned dependency metadata and default ModelLoader pending ModelData cleanup in tiny version-pinned same-package adapters for libGDX 1.14.2, with real failure/retry/refcount tests — public API cannot erase orphan metadata or pending ModelData without harming pinned roots — costs split-package/internal coupling on future libGDX/JPMS upgrades, Android requires separate gate. No reflection or replacement loader algorithms; arbitrary custom loader-private allocation cleanup remains its responsibility.

- Ruling: reject request estimates exceeding the total resident-byte budget synchronously with IllegalArgumentException — otherwise a request can remain queued forever — oversized assets require an explicitly larger configured budget.

- Ruling: Task1 may introduce finishFrame earlier than Task2 so failure cleanup and final root release have a real safe boundary from the start — lifetime correctness is load-bearing for Task2 — costs a little overlap between task file scopes, reviewed sequentially.

- Ruling: allow Task1 a default-no-op protected GameSystem.validateDispose() invoked before disposed guard/state mutation; AssetStreams overrides only owner-thread validation — checking thread in onRelease poisons final disposal before rejecting — costs a narrow compatible core lifecycle extension, not general Game thread safety. Test wrong-thread rejection leaves state/resources intact then owner close releases exactly once, preserve existing cleanup aggregation/idempotence.

- Ruling: AssetStreams accepts an exclusively transferred, configured but empty AssetManager (no loaded assets or unfinished queue); callers cannot subsequently mutate that manager and cannot co-own it through SceneAssets — otherwise resident accounting and transaction rollback begin with untracked ownership — costs rejecting transfer of an already populated manager, while loaders/resolver remain configurable before transfer.

- Ruling: configured-empty transfer rejects abandoned dependency metadata and pending default ModelData before any ownership mutation — prior failure can leave these despite zero loaded assets/finished queue — costs requiring genuinely clean configured managers/loaders; root agreed. Focused 2-test RED→GREEN included.

- Ruling: configured-empty manager validation also rejects abandoned dependency metadata and pending default ModelData before takingownership — zeroassets/finishedqueue alone canhidefailedloadresidue — costs requiringclean configuredmanager/loaders, preservesexclusiveownershipaccounting.

- Ruling: Task2's approved unpinned-cache policy supersedes Task1's temporary immediate-final-release tests. Adapt those tests to force real cache pressure (or system close where the assertion is total cleanup), retaining exact shared dependency identities/refcounts and exactly-once disposal assertions — last close no longer means immediate unload — costs changing test setup, not weakening ownership guarantees; do not add a public flush API solely to make old tests pass.

- Ruling: StreamStats.estimatedResidentBytes includes active transaction reservations as conservative demand accounting; ready includes unpinned cache, pinned counts READY roots with users, inflight actual0/1. overBudget means byte/entry demand pressure for live queued requests, not mere loader occupancy or measured GPU overuse — makes backpressure visible even though admission never exceeds limits — costs requiring precise documentation of estimates/reservations.

- Ruling: retain strict priority/FIFO queue head, no lower-priority bypass. finishFrame evicts LRU unpinned cache only enough to fit that head when pinned roots permit it; avoid pointless eviction if pinned occupancy alone blocks it, and always clean abandoned generations — simple deterministic order — costs head-of-line waiting for smaller lower-priority requests.

- Ruling: Task3 checks disposed GL handles immediately after finishFrame before replacement upload and tracks allocation/deletion generations — Mesa legitimately reuses numeric texture names — costs a probe callback at the real boundary, no portable API/behavior change.

- Ruling: use a smoke-only TextureLoader gate delegating unchanged default algorithms, while interactive demo uses defaults — reproducible actual rendered frames during loading — costs controlled test latency, not a disk-speed benchmark.

- Ruling: assert native pressure disposal immediately after finishFrame before replacement upload and count GL creation/deletion generations — driver can reuse numeric names — costs an extra probe callback, preserves actual safe-boundary proof.

- Ruling: existing renderer constructors keep stock legacy capacities/appearance, but the new scene-lighting variant has usable default capacities2 directional/4 point/2 spot; explicit five-argument constructor remains advanced override — latest user requires add-component-and-render simplicity, while legacy must stay unchanged — costs separate legacy/scene shader configurations, tested for no cross-mode reuse.

- Ruling: renderViewmodel deliberately retains legacy renderer lighting, with no scene fog/sky; new scene Environment governs world draws only — the existing API has no Game and FPS hands must remain readable independently of world fog — costs world/viewmodel lighting mismatch, document explicitly rather than infer scene inheritance.

- Ruling: standalone sky resources must participate in renderer contextRestored and refresh actual uniform AND attribute locations after managed relink, without trusting stale ShaderProgram alias maps — pinned 1.14.2 does not refresh those maps on invalidation — costs a narrow owner-thread recovery hook and real regression proof; no Android claim before actual EGL gate.

- Ruling: Task1 must minimally compile/render the actual scene shader variant on GL20 and GL30, beyond legacy-only native regressions; full measured light/fog pixel controls remain Task3 — do not build sky on an uncompiled shader foundation — costs a small scoped native probe extension before the complete environment demo.

- Ruling: Task2 owns a real GL20/GL30 sky-pass probe before review (a scoped launcher/Gradle task may be added), rather than waiting for the not-yet-created Task3 environment demo — its specified no-parallax/depth/state/partial-failure contract must be proved before day/night integration — costs a small dedicated native test entry point; Task3 retains combined environment and new-light pixel controls.

- Ruling: sky GL30 uses its own VAO; GL20 reserves only a disabled, buffer-zero/default attribute slot under an explicit no-client-pointer precondition. Query/reject nonzero pointers where supported; unsupported pointer queries require that documented pristine-slot contract, not universal GL20 rejection. Restore queryable descriptors/offset0 and all other touched state; reject before draw if no eligible slot — libGDX GL20 cannot portably inspect arbitrary pointers — costs bounded GL20 caller-input support, with normal-success/rejection and GL30 caller-VAO native tests.

- Ruling: new scene fog applies to ordinary world models even without normals; lighting can be unlit while distance fog still applies. Legacy prefix behavior stays unchanged — the specified final distance mix is independent of surface lighting — costs a scene-only fog prefix path and a native unlit-model fog regression. Pinned DefaultShader.createPrefix currently enables fogFlag only inside its lighting/normal branch, so Task3 must explicitly test and resolve this boundary.

- Ruling: repeated-close proof is scoped to new EnvironmentDemo/overlay/sky owners; keep their disposal idempotent without broadening the old GdxRenderer.dispose contract — existing renderer is closed once by its owner — costs leaving historical direct renderer repeated-dispose behavior unchanged.
