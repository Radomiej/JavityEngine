# A — Static GPU Instancing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Preserve the user's requested agents and supervised integration.

**Goal:** Render compatible repeated static objects in fewer draws without changing their appearance.

**Architecture:** Add an opt-in InstancedModel component alongside ModelRenderer. An owned StaticInstanceBatch prepares independent geometry once, groups supported material values, and returns rejected renderables to the existing ModelBatch. New shaders have their own restoration path; do not insert them into RecoverableDefaultShaderProvider’s private shader collection.

**Tech Stack:** Java 25, Gradle 9.8.1, libGDX 1.14.2, JUnit, real desktop OpenGL and opt-in Android GLES tests.

**Spec:** [Approved GPU instancing design](../../gpu-instancing-design.md).

## Global Constraints

- Java 25, libGDX 1.14.2; no new ECS or gameplay object model.
- Borrow Model/Mesh/Texture; own only newly allocated resources. GL work stays on the owner thread outside an active ModelBatch.
- Renderer never advances playback. Unsupported inputs retain ordinary rendering with an explicit fallback reason.
- Desktop OpenGL3.3+ and Android GLES3.0+ need actual capability checks; GL20 remains supported by fallback.
- A checked task requires real evidence. Android and context recovery require separate runs; compilation alone is insufficient.

## Review Focus

1. Equal material names with unequal values must not merge (Task 1).
2. Removal/reuse, capacity overflow and hierarchy/presentation culling must neither lose nor duplicate instances (Task 1).
3. Negative/nonuniform/sheared transforms and unsupported material/environment features must use fallback (Tasks 1–2).
4. Borrowed meshes and ordinary GL state must remain intact, including failed allocation (Task 2).
5. Context recreation must rebuild buffers, attribute locations and divisors (Task 2).

---

### Task 1: Eligibility, grouping and bounded slots

**Files:**
- Create: `javity-core/src/main/java/org/javity/render/InstancedModel.java`, `InstanceCapabilities.java`, `InstanceStats.java`, `InstanceGroupKey.java`, `InstanceSlots.java`
- Test: `javity-core/src/test/java/org/javity/render/InstanceGroupingTest.java`

**Interfaces:**
- Consumes: libGDX `Renderable`, `Matrix4`, `Environment`.
- Produces: `InstancedModel extends Component`; `InstanceCapabilities.detect(): InstanceCapabilities`; immutable `InstanceStats` counters submitted/visible/culled/instanced/fallback/groups/draws/uploadBytes; package-private exact-value `InstanceGroupKey.from(Renderable, Environment): InstanceGroupKey`; bounded `InstanceSlots(int capacity)`.

- [x] **Step 1: Write failing tests.** `sameNameDifferentMaterialDoesNotMerge` asserts two keys differ; equivalent copied supported materials compare equal, including texture identity. `slotReuseAndCapacity` submits 257 objects to capacity256 and asserts two chunks, removes/reuses one slot without ghost IDs. `unsupportedTransformFallsBack` covers mirrored, nonuniform and shear transforms. `presentedHierarchyBounds` asserts per-view visible IDs match forced ordinary culling.
- [x] **Step 2: Verify RED.** Run `./gradlew :javity-core:test --tests org.javity.render.InstanceGroupingTest`; expect failure for the missing API or named behavioral assertion, never an unrelated environment error.
- [x] **Step 3: Implement.** Snapshot material values, geometry range/layout, shader variant, environment and render-state equality; never key by material name or hash alone. Initially support opaque and alpha-test diffuse/color materials with ambient plus directional lights; reject every unsupported attribute/light/fog/custom shader/viewmodel with a reason. Pack visible slots into bounded draws while retaining stable owner slots and dirty CPU ranges.
- [x] **Step 4: Verify GREEN.** Run `./gradlew :javity-core:test --tests org.javity.render.InstanceGroupingTest`; require all named assertions to pass and preserve the evidence.
- [x] **Step 5: Commit.** Stage only this task's listed files and run `git commit -m "feat(render): define static instance eligibility and slots"`.

### Task 2: Owned GPU path and visible comparison

**Files:**
- Create: `javity-core/src/main/java/org/javity/render/StaticInstanceBatch.java`, `InstanceShader.java`
- Modify: `javity-core/src/main/java/org/javity/render/GdxRenderer.java`
- Create: `javity-desktop/src/main/java/org/javity/demo/InstancingDemo.java`
- Modify: `javity-desktop/build.gradle`
- Create: `docs/artifacts/instancing/README.md`

**Interfaces:**
- Consumes: Task 1 keys, capabilities, slots, stats.
- Produces: `StaticInstanceBatch implements Disposable`, constructor `(int capacity)`; `render(Array<Renderable> candidates, Camera camera, Environment environment): Array<Renderable>` returns fallback; `stats(): InstanceStats`; `contextRestored(): void`; desktop tasks `instancingSmoke` and `instancingGl20Smoke`.

- [x] **Step 1: Write failing tests.** Add native assertions: 256 visible compatible single-MeshPart objects produce one instanced draw versus256 ordinary draws; image geometry coverage matches and RGB max difference≤2/255 away from a one-pixel silhouette band. Test capacity257, changed material, alpha-test holes, parent motion, source Mesh still ordinary-renderable, mixed ordinary/instanced order, forced allocation failure cleanup and GL20 reasoned fallback. A new actual context must reupload identical geometry and restore ordinary draws afterward.
- [x] **Step 2: Verify RED.** Run `./gradlew :javity-desktop:instancingSmoke`; expect failure for the missing API or named behavioral assertion, never an unrelated environment error.
- [x] **Step 3: Implement.** Copy source geometry once per prepared mesh, never mutate source instancing flags. Upload matrix and tint attributes only for dirty packed ranges; positive uniform scales only. Flush ordinary batches at path boundaries, restore GL bindings/divisors/state on success and failure, and dispose owned resources exactly once. Integrate opt-in world objects only. Use independent GLSL330/GLES300 shaders and restore method; maintain old shader provider unchanged. Demo uses identical cameras/culling for both paths and outputs images plus measured counters.
- [x] **Step 4: Verify GREEN.** Run `./gradlew build :javity-desktop:instancingSmoke :javity-desktop:instancingGl20Smoke`; require all named assertions to pass and preserve the evidence.
- [x] **Step 5: Commit.** Stage only this task's listed files and run `git commit -m "feat(render): add verified static GPU instancing"`.

