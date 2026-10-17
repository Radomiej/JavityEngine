# Artemis ECS CPU verification

This report records a CPU-only usage proof, the optional-module dependency/JAR guard, and a fixed workload benchmark from source commit `14ea1b7e98932e1cb13b4828cf78b927aca89744`. SHA-256 hashes of the benchmark, usage runner, Gradle boundary task, bridge APIs, and core serialization/game inputs are in both JSON files.

The usage run processed its native World three times, queried nine matching entities across those steps, created and closed two links, loaded one prefab instance, and exercised synchronization off and back on. It used a pure native archetype alongside linked GameObjects. It did not create a GL context or claim a screenshot.

The benchmark used seed `5359695226845349187`, 10,000 entities, `1/60` second steps, 100 warm-up steps, 300 measured steps, and three repetitions on OpenJDK 25.0.4.1+1, Linux amd64 (4 reported processors). Each case performed 4,000,000 measured-plus-warm-up updates and ended with the same checksum. Median measurements were:

| Case | Linked entities | Median wall time | Owner-thread allocated bytes |
| --- | ---: | ---: | ---: |
| Native ECS | 0 | 9,168,084 ns | 638,400 |
| GameObject | 0 | 280,491,140 ns | 498,513,600 |
| Mixed, sync on | 1,000 | 447,565,442 ns | 707,692,800 |
| Mixed, sync off | 1,000 | 276,289,193 ns | 510,892,800 |

These results describe this code, workload, and JVM run. They do not claim a universal speed ranking or target improvement; the mixed path includes bridge reconciliation and bookkeeping overhead.

`verification.json` records the resolved runtime graphs, the production JAR entries, the empty JMX-reference scan, and the negative graph proof. A temporary Gradle init script added `javity-ecs-artemis` to desktop `runtimeOnly`; `verifyEcsBoundary` rejected that graph. Removing the script restored a clean positive result. The guard reports legacy Artemis already present in desktop separately.

Verified scope is JDK 25 desktop CPU. Android/ART, browser/Wasm, and graphics/runtime rendering are unverified by this artifact.
