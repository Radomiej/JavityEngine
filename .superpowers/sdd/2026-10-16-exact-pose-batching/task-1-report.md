# Task 1 report

Implemented immutable primitive SharedPoseData, public exact composition keys and bounded frame-local pose cache. No animator integration or source registry was added. SharedPoseData equality compares source identity, caller revision and every captured rig/bind/clip primitive; equivalent actual-instance snapshots of the same source can share without a canonical registry. Changed primitive channel contents create unequal snapshots even at unchanged source revision.

## Interfaces for Task 2

- `SharedPoseData.capture(ModelInstance, Object sourceIdentity, long revision, float[] bindLocalTrs, String... clipNames)` selects only requested animations. Bind array is owner's original bind T(3), normalized R(4), S(3) per preorder node; never pass current animated output.
- `captureAnimations(ModelInstance, Object, long, float[], List<Animation>)` accepts previously selected animation sources, bypassing lookup of unused/replaced instance animation entries. To preserve existing cached ClipData arrays, Task2 can construct transient Animation/NodeAnimation wrappers around already-resolved tracks, then call this factory. Nothing mutable from those wrappers is retained. Revalidate before every cache reuse to preserve observed key-value edits.
- Immutable `layout()`, `clip(int)`, `clipCount()`, `bindComponent(node, component)`; Clip exposes name/duration/tracks and `track(node)`, nullable sparse tracks; Track exposes nullable translation/rotation/scaling Channel. Channel exposes `size()`, `time(key)`, `component(key, component)` and immutable raw-bit storage. No sampling arithmetic or quaternion preprocessing changed.
- `ExactPoseKey.builder(data).data(otherSnapshot).tag(int).number(double).floats(float[]).mask(boolean[]).build()` captures an ordered typed immutable state stream. Integration must encode mode/presence, selected clips, exact effective double times, mixer thresholds/parameter/phase/blend, ordered layers, enabled/active flags, masks/weights, base/layer fades and frozen TRS plus tc/rc/sc double coverage. Deep equality verifies all state independently of hash. Raw numeric bits preserve signed zero and NaN payloads.
- `ExactPoseCache(int maxEntries)`, `beginFrame(long)`, `resolve(key, Supplier<float[]>)`, `clear()`, `stats()`. Stats records retained entries, frameHits/frameMisses/frameSamplerExecutions and totalHits/totalMisses/totalSamplerExecutions. Capacity overflow samples uncached. Frame synchronization is idempotent; frame changes release entries and reset frame counters; clear releases entries and resets frame state while preserving cumulative metrics. Count sampler execution when supplier is invoked, including failing invocations.

## Evidence

RED: `JAVA_HOME=/tmp/javity-jdk25/jdk-25.0.4.1+1 GRADLE_USER_HOME=/workspace/.gradle-cache ./gradlew :javity-core:test --tests org.javity.animation.ExactPoseCacheTest` failed at compileTestJava with 33 expected missing contract symbols (`/tmp/exact-red.log`).

GREEN: same command passed all 5 tests (`/tmp/exact-green.log`). Coverage includes actual forced hash collision, neighboring double times that round to identical float, signed zero, source/revision differences, frozen fractional coverage, deep copies, bounded overflow, idempotent frame begin, source mutation, unused malformed clip and foreign instance-track rejection. An initial GREEN attempt exposed an incorrectly constructed collision fixture; fixture was corrected to use raw float bits. Google Java Format applied to all four scoped Java files.

CPU contracts only. Full reactor and GL/Android verification belong to later integration; no runtime/rendering claim. No unrelated modified files staged.
