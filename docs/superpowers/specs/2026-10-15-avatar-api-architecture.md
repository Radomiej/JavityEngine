# Avatar API architecture prep — libGDX 1.14.2

Read-only investigation of `/workspace/JavityEngine`, 2026-10-15. No repository edits, builds, native runs, or commits. Read AGENTS.md, the avatar design, NodeMask, ModelPoseAnimator, ModelRenderer, GdxRenderer, Camera3D, GeneratedSkinModel, relevant existing tests. Exact upstream source downloaded from Maven Central to `official libGDX 1.14.2 source archive`.

## Recommendation

Keep one canonical ModelRenderer.instance and ModelPoseAnimator. Add a cached renderable provider per view, with resolved NodePart selectors. For a single skinned mesh, prepare a **geometry sidecar** containing generated MeshPart ranges over an owned mesh, then substitute only Renderable.meshPart when submitting the canonical instance's NodePart. Do not build a replacement skeleton, derived Model, second animator, or second ModelInstance for FPS.

This preserves animation bindings, per-instance material edits, bone matrices and the source Model automatically. ModelRenderer's current final instance and AnimationController do not need replacing. A prepared sidecar is shareable by avatars created from the exact source Model.

## Verified upstream behavior and pitfalls

- `ModelInstance(Model)` copies nodes/NodeParts/materials and animation wrappers; meshes/textures remain shared. Default keyframe sharing is true. Material copies still borrow texture resources.
- `Node.copy()` recursively copies nodes and NodeParts. `NodePart.copy()` copies MeshPart metadata and enabled, shares material, shallow-copies inverse-bind map entries (Node keys AND Matrix4 values), and allocates a fresh bones array initialized to identity. A raw node copy is not a valid independently animated skeleton without remapping.
- `ModelInstance.invalidate(Node)` remaps inverse-bind keys using `getNode(oldKey.id)`. `copyAnimation()` similarly binds tracks by node ID, and silently skips missing nodes. **Both use first matching ID, not full paths.** Full-path masks alone cannot repair duplicate-ID skinning/animation misbinding that already occurred during ModelInstance construction.
- `Node.calculateBoneTransforms()` computes `bones[i] = bindMap.keys[i].globalTransform * bindMap.values[i]`. Index i is the original ordered ArrayMap slot, not node traversal order. It updates even disabled NodeParts.
- `NodePart.setRenderable(out)` copies MeshPart metadata and references the current material and bones array. It does not test enabled.
- `ModelInstance.getRenderable(out,node,part)` supplies userData and worldTransform. If `part.bones == null`, with a nonnull instance transform: `instance.transform * node.globalTransform`. If skinned: instance transform only. Therefore applying node.globalTransform again to a skinned renderable double-transforms it. Its null-transform case is identity; do not accidentally rely on that unusual behavior for rigid parts.
- `ModelInstance.getRenderables()` checks enabled per part and always traverses descendants. Hidden parent geometry must not prune descendants.
- ModelBatch gathers Renderables during render(provider), but draws at flush/end. Renderable.worldTransform and mesh-part metadata are copied; bones/material objects remain shared. Do not update pose/materials or dispose resources between submission and end.
- DefaultShader.Config in 1.14.2 defaults to **12 bones, 4 weight attributes**; shader supports at most 8 weights when configured. Current GdxRenderer constructs the default ModelBatch. A realistic rig can exceed that limit: expose/configure a shader provider or diagnose the limit. Do not claim arbitrary humanoid assets work from the two-bone fixture.

## Minimal API shape (proposals, not existing API)

```java
record PartKey(String nodePath, int partIndex) {} // stable within immutable source structure
enum AvatarPart { HEAD, TORSO, LEFT_ARM, RIGHT_ARM, LEGS }
enum AvatarPreset { FULL_BODY, ARMS_ONLY, BODY_WITHOUT_HEAD }

AvatarDefinition.Builder AvatarDefinition.builder();
Builder part(AvatarPart semantic, PartKey... parts);
Builder region(AvatarPart semantic, String preparedRegion);
Builder slot(String name, PartKey... parts);
Builder socket(String name, String nodePath, Matrix4 localOffset);
AvatarDefinition build(); // immutable defensive snapshots

AvatarBinding AvatarDefinition.bind(ModelRenderer renderer); // validate/resolve once
AvatarView AvatarBinding.view(); // independent visibility state, same canonical pose
AvatarView preset(AvatarPreset preset);
AvatarView slotVisible(String slot, boolean visible);
AvatarView setBaseTransform(Matrix4 base); // copy value; no mutation of instance.transform
void AvatarView.getRenderables(Array<Renderable> out, Pool<Renderable> pool);
Matrix4 AvatarBinding.socketTransform(String socket, Matrix4 base, Matrix4 out);
void AvatarBinding.close(); // release leases, never caller Model
```

Use node path + part ordinal as authoritative identity; MeshPart.id can be a validated convenience selector only (duplicates are common and NodePart has no ID). Resolve to Node and NodePart references once; mutations to node trees/part arrays after binding are outside the initial contract. A detached or removed renderer invalidates binding; component owns its binding and releases in onRelease, including failed initialization. Include available paths/part IDs in configuration errors.

Preset and slot semantics must be documented: recommended effective visibility is `sourcePart.enabled && semanticSelection && slotSelection`. Never temporarily toggle part.enabled. FULL_BODY includes all configured body regions and enabled ordinary unclassified parts; ARMS_ONLY includes explicitly tagged arms and explicitly allowed slots. Unclassified geometry must not silently leak into FPS. Reject conflicting semantic ownership unless an explicit union policy is chosen. Custom selection may be empty (hidden avatar still animates).

Provider enumerates cached entries, tests the canonical NodePart's enabled at submission time, calls `instance.getRenderable(out,node,part)`, overrides worldTransform using the view's base (rigid: base * node.globalTransform; skinned: base), and optionally substitutes a prepared MeshPart. It never rewrites bones, bind maps, materials, TRS, or source geometry. Capture enabled/material on the original canonical part so changes apply identically to all its prepared regions. Keep view matrices separate for simultaneous world and FPS providers.

Socket world matrix is `base * socketNode.globalTransform * localOffset`, even when related geometry is hidden. It is **not** a skinning bone matrix (which contains inverse bind). Rigid tool geometry can use that socket matrix and the normal renderer/provider path; no clothing retargeting is implied.

## NodeMask extension

```java
static NodeMask subtreePaths(String... paths);
static NodeMask subtreeIds(String... uniqueIds);
static NodeMask union(NodeMask... masks);
static NodeMask difference(NodeMask included, NodeMask excluded);
```

Keep paths/ids exact. Resolve anchors with existing unknown/ambiguous checks; expand subtree by actual Node identity/descendant traversal rather than string prefix. Compile the expression into boolean[] when configuring/rebinding layers. Differences may resolve to an empty set if deliberately documented; choose that policy explicitly. Preserve current eight-layer and single-pose-owner rules. Duplicate IDs with distinct full paths remain usable for mask tests, but are not evidence that libGDX skinned/animated assets with duplicate IDs are safe. Initial avatar binding should reject duplicate IDs involved in animation/bone remapping, or implement and test a separate identity remapper; the former is the minimal safe contract.

## One-time triangle-region preparation

```java
PreparedAvatarGeometry AvatarMeshPreparator.prepare(
    Model source, PartKey target, TriangleRegions classification);
TriangleRegions TriangleRegions.explicit(Map<String, int[]> triangleOrdinals);
TriangleRegions TriangleRegions.byBoneInfluence(
    Map<String, NodeMask> regions, InfluencePolicy policy);
PreparationReport PreparedAvatarGeometry.report();
GeometryLease PreparedAvatarGeometry.acquire(ModelInstance instance);
void GeometryLease.close();
void PreparedAvatarGeometry.close();
```

Smallest useful first version supports one selected skinned NodePart, GL_TRIANGLES, indexed unsigned-short source mesh. That fully handles GeneratedSkinModel. Other source parts stay on original meshes. Extend to nonindexed only when explicitly tested; no need to build a whole model converter.

1. CPU validation first: resolve source part, range and triangle count, inspect layout/bind map, classify all triangles, detect missing regions/ambiguous ownership. Read the selected MeshPart's index interval (offset/size), not the entire mesh indiscriminately. Every target triangle belongs to exactly one region; no omission/duplication. Winding and vertex order inside each triangle stay unchanged.
2. Copy **all source vertices once**, same vertex order/stride. Copy VertexAttribute descriptors in order, preserving usage, type, components, normalized, alias, unit. Copy the complete float buffer verbatim, including packed color/custom payload bits; no attribute-wise rebuilding, weight normalization, vertex welding, or bone palette compaction. Check raw bits for packed attributes. Keeping all vertices is intentionally simpler than reindexing and guarantees bone indices are unchanged.
3. Concatenate original triangle index triples by region into one new index buffer. Read shorts as `s & 0xffff` for validation/classification, write unchanged shorts. Index-count may exceed 65535; maximum **vertex index**, not index-count, is the 16-bit limit. One new Mesh plus MeshPart ranges per nonempty region; update their bounds once. Store metadata mapping each region back to original PartKey. The ordinary original target draw is replaced by these region draws; never emit both.
4. GL allocation/upload occurs only after full CPU validation, on GL thread, outside rendering. Dispose partially allocated meshes on failure. Source arrays, Model, meshes, animations, nodes/materials/inverse binds remain unchanged. Sidecar holds no skeleton/animation copies; provider uses canonical original NodePart bones/material, so no animation remapping occurs.
5. PreparedAvatarGeometry owns only generated meshes. It borrows source Model and texture resources. Bind checks `instance.model == source` plus the expected part/layout/bone-palette signature. Source model must outlive all instances/bindings/sidecars. Owner close prevents new leases and defers actual GPU disposal until all leases close, on the same GL thread; existing leases can remain usable until closed. Last release must happen after ModelBatch.end. Make owner/lease close idempotent. An equally valid simpler policy is fail-fast close while bindings remain; choose and test one, not silent premature disposal. Avoid Model.manageDisposable on borrowed resources.

If a derived Model is demanded later, it needs identity-based Node/animation/bind remapping, ordered palettes, independent inverse-bind matrices if mutable, and precise mesh/texture ownership. Adding copied nodes to a new Model and copying source animations by reference is insufficient. Sidecar avoids this entire failure surface.

### Classification contract and unsupported inputs

Explicit triangle ordinals are relative to the selected MeshPart, stable only for that immutable source. Require total, disjoint coverage and nonempty requested regions. Report selected part/path, triangle count per region, source triangle ordinals for ambiguous/unassigned cases (bounded sample plus total count).

For opt-in bone classification, expand each region NodeMask against **source nodes**, then map membership through each ordered bind-map slot. BoneWeight(unit) is a float vec2 `(paletteIndex, weight)`; unit identifies an influence attribute, not a bone. Recommended strict initial rule: for each vertex accumulate positive weights by region, normalize scores for classification only, require one dominant region with explicit minimum share and winner/runner-up margin, and require all three triangle vertices to choose the same region. Otherwise mark ambiguous. Keep source weights unchanged. Reject negative/nonfinite weights, invalid/nonintegral palette indices (including zero-weight indices because the shader still indexes them), zero-total weights, overlapping bone-region membership, unclassified positive influences unless policy explicitly assigns a fallback region. Missing positive-weight coverage is not success.

Default ambiguous policy: fail with diagnostics before GPU allocation. Optional explicit caller fallback region assigns each ambiguous whole triangle there, reports count, and may produce shoulder seams. Never duplicate triangles across regions or silently discard them. Summing influence over all three vertices and choosing a winner is a different policy and must not be silently substituted. Author-defined regions remain the route to exact arm boundaries.

Reject initially: strips/fans/lines, empty or invalid ranges, nonmultiple-of-three triangle sizes, nonindexed meshes (unless implemented separately), absent or malformed bind maps/bones, instanced meshes, non-float/non-vec2 BoneWeight attributes, unsupported/gapped weight units for the configured shader, stride/offsets incompatible with four-byte float-buffer copying, unreadable/custom external GPU-only data, mutable/morph/deformed vertex streams, mismatched source instances, ambiguous source node paths/IDs needed for remapping. Position should be standard float vec3 for bounds/classification fixtures. Generic aligned attributes may be preserved as opaque payload, but custom shader semantics are not thereby supported by GdxRenderer.

## Camera and renderer integration

```java
enum AvatarCameraMode { FIRST_PERSON, THIRD_PERSON }
void CameraRig3D.setMode(AvatarCameraMode mode);
void CameraRig3D.syncAfterSimulation(float unscaledDelta);
void GdxRenderer.render(Game game, Camera3D camera, AvatarRenderView avatarView);
```

Preserve existing render(game) as a compatibility entrypoint choosing its current first camera. Add explicit view/camera input for per-view filtering; no global main camera or global FPS flag. AvatarRenderView identifies the local avatar and its world/FPS providers. Other cameras render full world avatar from another provider and same pose. CameraRig owns mode, camera offsets and explicit world/viewmodel projection parameters; it borrows the target and avatar. F5 toggles mode, distinct from existing F debug flight. Poll action once per edge; toggling does not touch animation play/seek.

Sequence after Game.step: sync camera to post-physics player; update rig collision/camera matrices; call renderer.prepareForRender once per live renderer; submit world providers; models.end(); clear **only depth**, with depth writes enabled; models.begin(viewmodelCamera); render local arms/tools providers; models.end(); existing sprites/UI after that. World color remains. Empty/non-FPS viewmodel means no depth clear. Clearing depth is acceptable at this final 3D pass; future world overlays needing original depth require a separate framebuffer/composition contract. Separate camera FOV/near values are explicit. Size both cameras on resize.

For camera-local viewmodel geometry, either use a camera at origin facing the documented local forward direction and an avatar-to-camera-local base transform, or use a world-space camera and matching camera-world base. Do not mix spaces or mutate canonical instance.transform to make the second pass work. Bob/recoil affect only presentation base/camera offset. World collider/player Transform3D and sampled bone TRS remain unchanged.

TPP obstruction needs a typed query seam, e.g. `float maxCameraDistance(Vector3 pivot, Vector3 desired, float radius, GameObject ignoredOwner)`, returning allowed distance on that segment. The gameplay/voxel/physics adapter supplies it; ignore own body, honor near-plane clearance, and clamp rather than lerp through obstacles. A ray-only implementation must not claim general volume collision. No global full physics implementation is required by the Avatar API.

## Staged evidence required

1. CPU mask/provider behavior: exact masks unchanged; subtree includes descendants only; duplicate/unknown selectors fail; union/difference deterministic. Two avatars from one Model with independent materials/presets; two views of one avatar with different base transforms. Assert renderable count/identity, original enabled respected even across preset changes, source/instance TRS and bone matrices unchanged by filtering, descendants and socket matrices retained. Renderable pool tests use no GPU mesh allocation.
2. CPU partition planning: explicit coverage and ambiguity errors, strongest-bone boundaries and multi-influence cases, nonzero MeshPart offsets, attribute descriptors and raw payload bit equality, shorts above 32767, index-count above 65535, unsupported layout rejection before upload. Inject uploader allocation failure or separate a CPU plan from GPU upload to verify cleanup paths meaningfully.
3. Real GL fixture: extend GeneratedSkinModel (currently one material, one part, eight vertices, 12 indices, root/tip weights 0/1). Split its two disjoint quads into body/arm regions and compare full-source versus full-partition render at multiple animation times. Add nonidentity root/bind transform fixture; current identity inverse binds cannot catch double transforms or palette bugs. Include normals/UV/packed color and two influences for preservation coverage.
4. Real GL per-view/FPS: pixel probes prove arms present, head/torso absent from both color AND depth (draw a known occlusion probe behind hidden geometry); wall cannot clip overlay hands; full body reappears in TPP. Same frame second camera sees full avatar. F5 during locomotion/action/pause preserves playhead and callback counts. Inspect socket-attached tool under hidden geometry. Camera obstacle test includes own-body exclusion and near-plane clearance. Screenshots supplement numeric assertions.
5. Lifetimes: remove renderer/animator/avatar in each order, late removal before render, failed setup/upload, owner close with active leases, double close, last-lease GPU disposal after batch end. Confirm generated buffer handles deleted and caller mesh/texture still usable. No per-preset mesh allocation, no per-pass animation advancement, no rebuild inside render loop.
6. Run existing pose/animation tests, appropriate desktop smoke and full reactor per AGENTS before claiming completion. Report CPU/build evidence separately from actual GPU/native evidence. This document executes none of those tests.
