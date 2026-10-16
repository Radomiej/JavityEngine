# C — GPU Instanced Skinning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Preserve the user's requested agents and supervised integration.

**Goal:** Draw compatible animated characters with independent poses using bone palette textures.

**Architecture:** Build on stages A and B. A bounded BonePalette owns floating-point texture pages with four RGBA texels per matrix and one row per unique pose; each draw instance carries its world transform and palette row. Canonical CPU poses continue to drive avatar sockets and events.

**Tech Stack:** Java 25, Gradle 9.8.1, libGDX 1.14.2, JUnit, real desktop OpenGL and opt-in Android GLES tests.

**Spec:** [Approved GPU instancing design](../../gpu-instancing-design.md).

## Global Constraints

- Java 25, libGDX 1.14.2; no new ECS or gameplay object model.
- Borrow Model/Mesh/Texture; own only newly allocated resources. GL work stays on the owner thread outside an active ModelBatch.
- Renderer never advances playback. Unsupported inputs retain ordinary rendering with an explicit fallback reason.
- Desktop OpenGL3.3+ and Android GLES3.0+ need actual capability checks; GL20 remains supported by fallback.
- A checked task requires real evidence. Android and context recovery require separate runs; compilation alone is insufficient.

## Review Focus

1. Distinct poses in one draw must never sample another character’s row (Tasks 1–2).
2. Texture/attribute limits and unsupported materials must split or fall back without loss (Tasks 1–2).
3. Scene changes, slot generations and actual context loss must invalidate old GL references (Tasks 1–3).
4. Avatar region visibility, bind matrices and lighting must match ordinary skinning (Task 2).
5. Performance counters must measure actual sampler calls/draws/uploads rather than submissions (Task 3).

---

### Task 1: Palette layout and lifetime

**Files:**
- Create: `javity-core/src/main/java/org/javity/render/BonePalette.java`
- Test: `javity-core/src/test/java/org/javity/render/BonePaletteTest.java`

**Interfaces:**
- Consumes: immutable exact-pose identity from stage B and canonical skinning matrices.
- Produces: `BonePalette implements Disposable`, constructor `(int boneCount, int maxRows)`; `beginFrame(long frameIndex): void`; `put(ExactPoseKey key, Matrix4[] skinMatrices): BonePalette.Row`; nested immutable `Row` with page/index/generation; `contextRestored(): void`.

- [x] **Step 1: Write failing tests.** Assert width=4*boneCount, column-major matrix reconstruction, identical pose one upload, unequal pose separate rows, page split at maxRows, stale generation rejected and texture-size/vertex-fetch capability refusal. Force partial allocation failure and assert cleanup without source disposal.
- [x] **Step 2: Verify RED.** Run `./gradlew :javity-core:test --tests org.javity.render.BonePaletteTest`; expect failure for the missing API or named behavioral assertion, never an unrelated environment error.
- [x] **Step 3: Implement.** Retain bounded CPU palette backing for recovery. Use RGBA32F nearest/clamp texture and texelFetch, no uniform crowd array. Validate width, rows, vertex texture units and available matrix/tint/row attributes before allocation; overflow produces another bounded page or explicit fallback. Generation-check every row lease.
- [x] **Step 4: Verify GREEN.** Run `./gradlew :javity-core:test --tests org.javity.render.BonePaletteTest`; require all named assertions to pass and preserve the evidence.
- [x] **Step 5: Commit.** Stage only this task's listed files and run `git commit -m "feat(render): add bounded bone palette pages"`.

### Task 2: Animated instance rendering and avatar parity

**Files:**
- Create: `javity-core/src/main/java/org/javity/render/SkinnedInstanceBatch.java`, `SkinnedInstanceShader.java`
- Modify: `javity-core/src/main/java/org/javity/render/GdxRenderer.java`, `javity-desktop/src/main/java/org/javity/demo/InstancingDemo.java`
- Modify: `javity-desktop/build.gradle`

**Interfaces:**
- Consumes: stage A eligibility/stats, stage B exact poses, Task 1 palette rows.
- Produces: `SkinnedInstanceBatch implements Disposable`, constructor `(int capacity, int maxPaletteRows)`; `render(Array<Renderable> candidates, Camera camera, Environment environment): Array<Renderable>`; `stats(): InstanceStats`; `contextRestored(): void`; task `skinnedInstancingSmoke`.

- [ ] **Step 1: Write failing tests.** Fox Walk/Run/Survey with at least two independent times in the same compatible draw must match ordinary vertex positions≤.01 source units and image RGB≤2/255 off silhouette band. Cover fade, tail mask, mixer, shared pose, FPS/TPP prepared regions, sockets, diffuse textures, directional light, alpha-test and unsupported fog/spot/custom material fallback. Assert two renders do not advance clocks and ordinary rendering still works after instanced pass.
- [ ] **Step 2: Verify RED.** Run `./gradlew :javity-desktop:skinnedInstancingSmoke`; expect failure for the missing API or named behavioral assertion, never an unrelated environment error.
- [ ] **Step 3: Implement.** Group by rig/palette layout and prepared MeshPart/material/pass; preserve source bone inverse-bind conventions and weight layout. Fetch four texels per bone matrix, blend weights, transform normals consistently with ordinary supported shader. Limit transforms to supported positive uniform scale. Keep viewmodel ordinary, separate region groups, own shader programs and restore state at boundaries and on errors.
- [ ] **Step 4: Verify GREEN.** Run `./gradlew build :javity-desktop:skinnedInstancingSmoke :javity-desktop:instancingGl20Smoke`; require all named assertions to pass and preserve the evidence.
- [ ] **Step 5: Commit.** Stage only this task's listed files and run `git commit -m "feat(render): add instanced skeletal skinning"`.

### Task 3: Evidence, benchmark and Android recovery gate

**Files:**
- Modify: `javity-desktop/src/main/java/org/javity/demo/InstancingDemo.java`, `javity-desktop/build.gradle`, `docs/artifacts/instancing/README.md`, `docs/gpu-instancing-design.md`, `docs/TODO.md`
- Create: `tools/animation-validation/benchmark_instancing.py`
- Modify: `javity-android-app/src/main/java/org/javity/android/AndroidGameFixture.java`, `javity-android-app/src/main/java/org/javity/android/AndroidManagedContextProbe.java`, `javity-android-app/src/androidTest/java/org/javity/android/AndroidManagedContextLossInstrumentationTest.java`.

**Interfaces:**
- Consumes: all stages and existing Android owner-thread EGL-loss harness.
- Produces: desktop `instancingBenchmark` task, `docs/artifacts/instancing/verification.json` and `benchmark.json`, Android capability/recovery evidence or explicit unverified status.

- [ ] **Step 1: Write failing tests.** Benchmark64/256/1024 with seed42, fixed camera and120 warmup+300 measured frames. Assert real draws reduce, shared poses reduce sampler calls/palette uploads, independent events remain correct, and report upload bytes/CPU timing; GPU timing only if query supported. Trigger genuine EGL context loss and assert palette/instance pixels recover, resources close, and a subsequent ordinary model draws correctly.
- [ ] **Step 2: Verify RED.** Run `./gradlew :javity-desktop:instancingBenchmark`; expect failure for the missing API or named behavioral assertion, never an unrelated environment error.
- [ ] **Step 3: Implement.** Capture ordinary/instanced screenshots, GL identity, fixture/source hashes, pose/image tolerances and measured counters. Reuse licensed Fox attribution. Extend Android capability checks and recovery hooks only on the actual GLES path; never infer a passed Android gate from desktop or APK build. Keep untested physical ARM/performance marked unverified. Document enabling components/systems and fallback reasons.
- [ ] **Step 4: Verify GREEN.** Run `./gradlew build :javity-desktop:instancingBenchmark :javity-desktop:skinnedInstancingSmoke`; require all named desktop assertions to pass and preserve the evidence. Run Android separately with `./gradlew -PjavityAndroid=true :javity-android-app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.javity.android.AndroidManagedContextLossInstrumentationTest`; require the named test to pass, otherwise record Android as unverified and leave that gate open.
- [ ] **Step 5: Commit.** Stage only this task's listed files and run `git commit -m "test(render): verify crowd batching and context recovery"`.

