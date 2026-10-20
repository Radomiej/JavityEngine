# Independent foliage review

Reviewed dirty implementation based on 9f840ad against AGENTS.md and both foliage specs. Scope: CPU foliage classes, FoliageRenderCache/FoliageStats, GdxRenderer integration, WorldRenderPass owner map and DirectionalShadowPass owner adaptation. No repository source edits or Gradle execution performed by reviewer.

## Finding: P2 — Color-camera culling removes offscreen foliage shadow casters (CLOSED after fix)

Location at review: FoliageRenderCache.java:194–197, GdxRenderer.java:392 and 424–425.

`collect` returns before creating any renderable when the generated patch is outside the color camera frustum. GdxRenderer subsequently passes only `worldOrdinary` to the optional WorldRenderPass. Consequently DirectionalShadowPass never sees an offscreen patch even when its GameObject explicitly has `ShadowPolicy3D(true,true)`. An offscreen bush can cast a visible shadow onto ground inside the color view, especially at low sun angles. Its shadow pops out when the patch leaves the camera frustum. Ordinary ModelRenderer objects do not undergo this new early cull. QualityShowcase now opts foliage into cast/receive, making this a supported path rather than hypothetical custom use.

Deterministic isolated probe: create initialized bush patch, count1, area(.1,.1), ShadowPolicy3D(true,true), world translation(6,0,0). Color camera is PerspectiveCamera(60,100,100), position(0,1,8), lookAt(0,1,0), near .1, far100. Result: `cast=true initialized=true, meshes=1, camera-culled=1, world-pass input renderables=0`. Source: /tmp/sol-foliage-review-src/org/javity/render/FoliageCasterProbe.java; evidence: /tmp/sol-foliage-review-caster.log. With a low directional sun toward negative X and a fitted shadow volume covering the bush and visible ground, the missing caster produces missing visible ground shadow.

Requested fix: retain offscreen eligible foliage in a pass-only candidate list when a WorldRenderPass is installed; expose it to the world pass with owners and the actual selected environment, then submit only color-visible renderables to the color batch. Keep no-pass color culling and existing foliage draw/cull counters. Avoid coupling core to the optional PBR shadow implementation. Root acknowledged finding and renderer worker is implementing this approach.

Suggested regression: a cache-level case must show culled color output remains empty while pass-only output contains the live owned patch descriptor; ordinary visible collection and no-pass culling should remain unchanged. A native shadow case can project a low-sun shadow from an offscreen bush onto visible ground and assert the shadow survives while the bush is color-culled. Verify pass-only arrays clear before pool flush and selected environments and owner identities are assigned for those descriptors.

## Checks with no further actionable findings

- Deterministic seed/snapshot generation; bounded counts; 65536-vertex ushort chunks, including >65535 index counts; immutable geometry/UV/snapshot access; ordinary stock cutout states and private copied material attributes.
- Renderer-owned generated resources and borrowed textures; transactional candidate allocation/upload/metadata failure cleanup; valid old geometry retention; frame/revision retry behavior; cleanup continuation after errors.
- Whole-patch conservative local bounds and transformed culling; presentation matrix path; revision-only rebuilds; disabled/removed transform/patch and scene/no-camera pruning.
- Pooled ordinary descriptors reset shader/environment/bones/userData; generated cached mesh parts avoid per-draw vertex scans; selected scene Environment assignment; ordinary rather than fake instanced patches.
- Managed Mesh construction and existing ManagedMeshBindingsRecovery path. This is a source-level integration check, not actual EGL recovery evidence.

Verification: reran all 16 new foliage CPU/cache tests against integrated Gradle build/classes outputs, JUnit PASS; /tmp/sol-foliage-review-tests.log. Reviewed worker reports and observed root integrated build + qualitySmoke log complete successfully, but those are root/worker evidence, not independent graphical proof. No Android, Wasm foliage runtime, physical GPU performance or EGL-restoration claim follows from this review.

## Narrow fix re-review — CLOSED

Inspected the frozen fix and independently compiled the current FoliageRenderCache, GdxRenderer and FoliageRenderCacheTest sources into /tmp/sol-foliage-review-fixed-classes against integrated dependencies, with no Gradle or repository source edits. All 17 focused foliage tests pass: /tmp/sol-foliage-review-fixed-tests.log. Also read the genuine regression RED log (/tmp/sol-foliage-render-caster-red.log) and worker full-suite GREEN (587 tests).

The fix preserves offscreen generated descriptors in worldPassOnly only when a world pass exists. worldPassInputs combines the ordinary and pass-only descriptors for that pass, while ModelBatch color submission still iterates only worldOrdinary. Both descriptor sets receive the selected scene Environment, all share correct identity owners, visible descriptors are not duplicated, and color counters retain their previous meaning. New arrays are cleared both before collection and in finally before worldPool.flush; the existing pool reset clears shader/environment/bones/userData. The old no-pass collect overload still performs the original color cull. No core dependency on ShadowPolicy3D or the optional PBR module was introduced.

The new regression uses the same concrete offscreen bush setup as the review probe and asserts empty color output, nonempty pass-only output, preserved owner/world transform and cull/draw counters, then verifies visible output is not duplicated. The source path plus executable regression addresses the reported caster-loss mechanism. No remaining actionable finding in this review. Native offscreen-shadow pixel validation remains useful integration evidence but is not required to establish the corrected candidate-flow mechanism; actual platform/EGL limits above remain unchanged.
