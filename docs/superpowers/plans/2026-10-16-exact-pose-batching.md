# B — Exact Pose Batching Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Preserve the user's requested agents and supervised integration.

**Goal:** Share identical animation sampling results while retaining independent playback and events.

**Architecture:** Separate immutable rig/clip sample data from each ModelInstance binding. A scene-owned bounded ExactPoseCache stores immutable composed local poses for an exact state key, while each animator advances its own playback and copies results into its own nodes. This stage works without stage A or GPU instancing.

**Tech Stack:** Java 25, Gradle 9.8.1, libGDX 1.14.2, JUnit, real desktop OpenGL and opt-in Android GLES tests.

**Spec:** [Approved GPU instancing design](../../gpu-instancing-design.md).

## Global Constraints

- Java 25, libGDX 1.14.2; no new ECS or gameplay object model.
- Borrow Model/Mesh/Texture; own only newly allocated resources. GL work stays on the owner thread outside an active ModelBatch.
- Renderer never advances playback. Unsupported inputs retain ordinary rendering with an explicit fallback reason.
- Desktop OpenGL3.3+ and Android GLES3.0+ need actual capability checks; GL20 remains supported by fallback.
- A checked task requires real evidence. Android and context recovery require separate runs; compilation alone is insufficient.

## Review Focus

1. Equal hashes with unequal clip/time/mask/fade state must miss (Task 1).
2. Seek or layer edits after update must immediately invalidate composition (Task 2).
3. Shared pose must preserve independent markers, loops and completion (Task 2).
4. Different rigs, rebinds and scene disposal must not reuse stale poses (Tasks 1–2).
5. Two views and shared immutable results must not couple nodes, sockets or clocks (Task 2).

---

### Task 1: Immutable samples and bounded exact keys

**Files:**
- Create: `javity-core/src/main/java/org/javity/animation/SharedPoseData.java`, `ExactPoseKey.java`, `ExactPoseCache.java`
- Test: `javity-core/src/test/java/org/javity/animation/ExactPoseCacheTest.java`

**Interfaces:**
- Consumes: source rig/node paths and animation channels currently sampled by `ModelPoseAnimator`.
- Produces: immutable `SharedPoseData`; immutable `ExactPoseKey`; `ExactPoseCache(int maxEntries)`, `beginFrame(long frameIndex): void`, `resolve(ExactPoseKey key, Supplier<float[]> sampler): float[]`, `clear(): void`; resolve returns a defensive local-TRS copy, never cached mutable storage.

- [x] **Step 1: Write failing tests.** `collisionDoesNotShare` forces equal hashes but unequal sample states; `exactTimeDoesNotQuantize` distinguishes neighboring float times. `rigRevisionAndFrozenFade` separates rigs/revisions/outgoing poses. `boundedFrameLifetime` asserts entries≤capacity, old frames evicted and clear releases data.
- [x] **Step 2: Verify RED.** Run `./gradlew :javity-core:test --tests org.javity.animation.ExactPoseCacheTest`; expect failure for the missing API or named behavioral assertion, never an unrelated environment error.
- [x] **Step 3: Implement.** Move immutable clip indexing into SharedPoseData without changing interpolation. Key exact sample times, rig/clip identity and revision, mixer parameters, layers/masks/weights and frozen outgoing local TRS with content equality. Keep a frame-local bounded insertion-order cache; overflow computes uncached rather than changing pose. Expose sampler-hit counters for verification.
- [x] **Step 4: Verify GREEN.** Run `./gradlew :javity-core:test --tests org.javity.animation.ExactPoseCacheTest`; require all named assertions to pass and preserve the evidence.
- [x] **Step 5: Commit.** Stage only this task's listed files and run `git commit -m "feat(animation): add bounded exact pose cache"`.

### Task 2: Animator integration with independent gameplay

**Files:**
- Modify: `javity-core/src/main/java/org/javity/animation/ModelPoseAnimator.java`
- Create: `javity-core/src/main/java/org/javity/animation/PoseBatchSystem.java`
- Test: `javity-core/src/test/java/org/javity/animation/PoseBatchingTest.java`
- Modify: `tools/animation-validation/ImportedAnimationProbe.java`, `docs/artifacts/imported-animation/README.md`

**Interfaces:**
- Consumes: Task 1 `ExactPoseCache.resolve` and existing per-owner playback callbacks.
- Produces: opt-in `PoseBatchSystem extends GameSystem`, constructor `(int maxEntries)`; animator observes same-scene system without a required gameplay API change.

- [x] **Step 1: Write failing tests.** Assert256 identical characters cause one composition per frame,256 per-owner events, and equal but independently owned node/socket matrices. Cover pause/WORLD pause, speed, seek, masked edit after step, fade interruption, mixer, renderer remove/rebind, scene replacement and two renders without update. Compare Fox local/skinned poses against uncached path and existing original glTF oracle; no tolerance increase.
- [x] **Step 2: Verify RED.** Run `./gradlew :javity-core:test --tests org.javity.animation.PoseBatchingTest`; expect failure for the missing API or named behavioral assertion, never an unrelated environment error.
- [x] **Step 3: Implement.** Advance clocks/callbacks per owner before cache lookup; route all immediate compose paths through a fresh exact key. Copy pose into each own binding and calculate its transforms; avatar visibility is excluded from pose key. Reset cache by scene frame, invalidate binding/data revisions on rebind and dispose with GameSystem. Avoid global cache/scene ownership changes.
- [x] **Step 4: Verify GREEN.** Run `./gradlew build :javity-core:test --tests org.javity.animation.PoseBatchingTest`; require all named assertions to pass and preserve the evidence.
- [x] **Step 5: Commit.** Stage only this task's listed files and run `git commit -m "feat(animation): integrate independent playback with shared poses"`.


## Execution evidence

Completed through98d7d8c and independently reviewed, including a narrow uncached fallback for post-validation source data that cannot be snapshotted. Full reactor714 tests/79 suites;123 animation tests independently repeated; four realGL20/GL30 Fox proofs retain original tolerances and actual loaded JAR hashes. [Evidence and measured CPU/allocation cost](../../artifacts/imported-animation/pose-cache-fix1/README.md). CPU sharing is opt-in and was slower overall for this fixture despite256→1 compositions; do not infer total speedup.
