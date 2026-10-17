# Local asset streaming — desktop proof

2026-10-16, Java 25, libGDX 1.14.2, existing Xorg display, Mesa software OpenGL.
`./gradlew build :javity-desktop:assetStreamingSmoke :javity-desktop:voxelSmoke`
passed after the final clock fix. A preceding `clean build` plus both smokes also
passed. Full reactor inventory: **782 tests, 87 suites, no failures/errors/skips**.

[Machine-readable proof](task3-proof.json) records 183 native assertions, twelve
actual loading frames, and **34.65 px** of sentinel movement while WORLD was
paused and the texture worker was gated. WORLD model rotation stayed fixed.
The gate and parser counter delegate to the standard libGDX loaders; normal
interactive operation has no gate or replacement loading algorithm.

| Capture | Observed result |
| --- | --- |
| [Loading start](task3-loading-start.png), [loading paused](task3-loading.png) | Both model requests LOADING, actual inflight root 1, moving UI sentinel, empty model area |
| [Two leases READY](task3-ready-two-leases.png) | Original local model and texture rendered; one model parse and one texture dependency load; 40,159 cyan/gold pixels |
| [First lease closed](task3-one-lease.png) | Second lease READY, same resource, 40,132 cyan/gold pixels |
| [Detached, unused cache](task3-released-cache.png) | Both leases CLOSED; root ready 1 / pinned 0; zero model pixels, native resources still alive |
| [Released prefetch cache](task3-prefetch-cache.png) | Different texture READY then released; root ready 1 / pinned 0; resident estimate 16 KiB |
| [Explicit failure](task3-explicit-failure.png) | Current batch one FAILED request, no automatic retry; a subsequent explicit request leaves the old FAILED lease unchanged |

Captures are upright framebuffer PNGs at 1100×720. Loading, READY, unused-cache
and failure captures were visually inspected. The panel remains readable and the
crate texture is visible. Counts use the central right half of the framebuffer,
excluding controls and the sentinel. A threshold of more than 500 colored pixels
for the model and exactly zero after detach supplies a rendered negative control.
This does not measure GPU memory or bytes downloaded.

The native lifetime control observes the model's real VBO, IBO and dependency
texture: all three are alive during rendering and after final lease close. A
queued different prefetch root creates real entry pressure; immediately after
`finishFrame`, all three old objects are absent according to glIsBuffer/glIsTexture
and each has exactly one deletion. This check happens before a replacement upload
can reuse a numeric GL name. Cache reacquisition before that pressure returns the
identical Model without another parse.

Across the scene, UI/renderer and separate close-during-load fixture, the proxy
counts **12 buffer and 8 texture allocations and matching deletions**, including
reused numeric names. After disposal, every tracked name is absent and GL reports
NO_ERROR. The second fixture closes a real Game while the texture dependency is
still LOADING, releases the CPU gate from a helper after close begins, and verifies
owner-thread upload/disposal, CLOSED publication and zero resident roots.

Failure snapshots can show a FAILED lease with inflight 1 and a reserved estimate:
rollback intentionally waits for that frame's safe cleanup point. Ready/failed
counts describe the current request batch; root counts and resident estimates have
the separate AssetStreams meanings documented in [the API guide](../../asset-streaming.md).

`voxelSmoke` also passed GPU and scene controls. Its generated historical FPS Task 2
captures and the two runtime-tools voxel screenshots were restored by explicit
filename after the regression run; these streaming artifacts are independent.

**Android streaming: UNVERIFIED.** No per-feature Android packaging, local model/
texture load, close-during-load or context-loss instrumentation was run for this
module. Existing Android results do not establish this feature's readiness.
