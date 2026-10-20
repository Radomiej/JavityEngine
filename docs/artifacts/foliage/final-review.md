# Fresh whole-branch integration review

Reviewed dirty source based on `9f840ad` against `AGENTS.md`, `docs/Spec/2026-10-20-foliage-avatar.md` and both completed task reviews. Reviewer made no repository edits and invoked no Gradle tasks.

## P2 — Foliage demo permanently skips owned cleanup after a scene callback fails (CLOSED)

Location: `javity-desktop/src/main/java/org/javity/demo/FoliageDemo.java:410–419`, with related failure propagation at `401–406`.

`dispose()` sets `disposed = true`, then invokes `game.close()` before releasing the renderer, authored Models, borrowed-atlas owner texture, SpriteBatch and font. A throwing component `onDestroy`/`onRelease` or system-close callback makes `Game.close()` report that failure; the demo consequently skips every later resource. Its guard prevents subsequent `dispose()` calls from recovering them. `fail(Throwable)` also invokes this cleanup before `Gdx.app.exit()`, so a cleanup failure masks the original error and skips that exit request.

Concrete isolated reproduction, requiring no GL: instantiate `FoliageDemo(false)`, set its `game` field to a started Game containing a Component whose `onDestroy()` throws `IllegalStateException("injected onDestroy failure")`, and add a tracking Model to its `models` list. Invoke `dispose()` twice. Executed probe `/tmp/FoliageCleanupProbe.java`; evidence `/tmp/foliage-final-review-cleanup.log`:

```
first dispose: injected onDestroy failure
model releases after first dispose=0
model releases after second dispose=0
```

The tracking model proves skipped later ownership cleanup; in the real composed scene these later resources include all generated foliage buffers through GdxRenderer and the atlas. This conflicts with the repository's explicit exception-continuing cleanup contract. Use `ResourceScope` or equivalent continuation (as the new ImportedAvatarDemo does), aggregate distinct errors, and retain the original failure plus an unconditional exit request from the failure path. Add a narrow executable failure-cleanup regression.

## Integration checks without further findings

- Foliage geometry uses deterministic bounded generation, unsigned-short vertex splitting, immutable public snapshots and copied material attributes. Borrowed atlas/material textures are not claimed by renderer ownership. Cached MeshPart metadata prevents per-view bounds scans.
- Revision replacement keeps a valid entry on recoverable candidate build failures, disposes partially allocated candidates, and delays retry per frame. Reconcile prunes disabled/removed owners/transforms and scene switches before camera selection. Existing managed Mesh registration and ManagedMeshBindingsRecovery cover the new retained meshes at the source level; no actual EGL loss claim follows.
- Offscreen foliage descriptors enter `worldPassOnly`, then the actual WorldRenderPass input and identity owner map. Both visible and pass-only descriptors receive the selected scene environment; DirectionalShadowPass accepts live Component owners, consults the same GameObject policy, copies casters to its depth pool, and publishes receiver/unshadowed environment copies. Only color-visible descriptors enter ModelBatch. All reused arrays/maps clear before the shared pool flush, including a throwing pass callback. Existing task-level executable regression establishes the former caster-loss mechanism is closed.
- PbrFoliage supplies explicit BaseColorTexture/roughness/metallic attributes and supported alpha/depth/cull states. QualityShowcase explicitly selects the PBR provider and cast/receive policy. Its source teardown already continues across individual errors.
- ImportedAvatarProfile uses canonical instance paths and NodePart ordinals, validates detached palettes/channels and ambiguous IDs, preserves exact imported clip IDs and delegates to AvatarDefinition. It creates no competing pose owner or hidden shared instance state. The demo uses two pinned imported instances with independent canonical animation and bone arrays and closes scene consumers before their instance handles.
- ImportedAvatarDemo capture now releases its Pixmap and ends its recoverable framebuffer under nested finally blocks; reviewed prior native PNG-write-failure recovery evidence. Its ResourceScope teardown continues after callbacks fail.
- Gradle tasks and CI names/output paths match source. Both atlas and GLB are classpath resources supplied by existing dependent-module resource processing. Independently opened current built jars: core contains atlas PNG (465560 bytes), NOTICE and FoliagePatch3D; gltf contains avatar.glb (17064 bytes), updated LICENSE and ImportedAvatarProfile. Existing installDist tree predates this increment, so an outside-checkout installed-distribution proof must regenerate installDist first; this is stale output, not a source defect.
- Reviewed the task reports and root's currently running integrated log. Full reactor and native smoke outcomes remain root-owned evidence and were not repeated by reviewer. No hardware GPU, Android/Wasm foliage/avatar runtime or actual context-loss claim is established by this review.

## Narrow fix re-review — CLOSED

Reviewed the frozen `FoliageDemo` fix and new `FoliageDemoCleanupTest`. The demo now attempts scene, renderer, every model, texture, batch and font cleanup in ownership order even when an earlier step throws RuntimeException or Error. It clears the model list, preserves idempotence, reports the first failure and attaches distinct subsequent failures without self-suppression. `fail(Throwable)` retains the original error, adds a cleanup error if distinct, and requests application exit in finally, including a finally around diagnostic printing.

The three new executable tests cover a started scene callback failure followed by multiple models (two sharing the same thrown exception), later texture/font cleanup, exactly-once repeated disposal, original create/render failure preservation plus exit invocation, and identical original/cleanup throwable handling. These tests directly cover the reported failure mechanism instead of only mirroring helpers. Source inspection confirms the renderer and batch use the same continuation helper; they do not need a fake GL context in this narrow CPU regression.

Independently compiled current FoliageDemo and the new tests into `/tmp/foliage-final-review-classes` with JDK25 against existing dependencies, then executed JUnit: **OK (3 tests)**, `/tmp/foliage-final-review-cleanup-fixed-tests.log`. Expected injected exceptions are printed by the tested failure path. Recompiled and reran the original unchanged callback probe: model releases after first disposal = 1, after second = 1, `/tmp/foliage-final-review-cleanup-fixed-probe.log`. No Gradle or repository edits by reviewer.

Verdict: **approve current reviewed source; no remaining actionable blocking findings.** Root retains full-reactor, native smoke and refreshed installed-distribution final gates. Platform and hardware limits above remain unchanged.
