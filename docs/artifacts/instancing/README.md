# GPU instancing evidence

Reproduce with JDK 25 and a real display/OpenGL context:

```sh
./gradlew build :javity-desktop:instancingSmoke :javity-desktop:instancingGl20Smoke
```

The launcher requests desktop GL 3.3 for `instancingSmoke`, and the explicit GL20 backend for `instancingGl20Smoke`. The software-driver results are correctness evidence, not hardware performance estimates. Fixtures are procedural boxes; no external model/license dependency is needed.

## Use

Opt in a world object alongside its ordinary model component:

```java
object.addComponent(new Transform3D());
object.addComponent(new ModelRenderer(borrowedModel));
object.addComponent(new InstancedModel());
// Later, after editing source vertices/indices, before the next render and outside batches:
renderer.invalidateGeometry(borrowedModel.meshes.first());
InstanceStats stats = renderer.instanceStats();
Map<String, Integer> reasons = renderer.instanceFallbackReasons();
```

The renderer owns copied geometry, private VAOs, instance buffers and independent programs. Model, source Mesh and textures stay borrowed and must outlive rendering. Never mutate opted-in source geometry without invalidating it. Dispose the renderer on its GL owner thread before disposing the source assets/context.

The direct API is `new StaticInstanceBatch(capacity)`, then `render(candidates, camera, environment)`, which returns ordinary fallback Renderables. Submit fallback through an ordinary ModelBatch **after** the instance call, outside the instance call. The four-argument overload additionally takes unique stable `long[]` owner IDs; use it for persistent owners. The three-argument convenience overload assigns positional IDs for a submission sequence and does not identify gameplay owners. GdxRenderer uses monotonic renderer-owned IDs keyed by actual ModelRenderer and NodePart identity; pooled Renderable identities never escape a view.

Task1 `InstanceSlots` tracks logical owner slots per group. Visible members are packed into bounded draw chunks; dirty CPU slot indices are not GPU offsets. Compact float ranges are compared bitwise and only changed ranges use `glBufferSubData`. Removing/reusing an owner changes the packed order safely. Geometry is copied once per active source Mesh and shared by all its material/chunk draws. A completed view prunes stale groups and source references.

## Historical stage A measured results

| Case | Result |
| --- | --- |
| 256 visible compatible single parts | 256 actual ordinary draws, 1 actual instanced draw |
| First matrix upload | 16,384 bytes, verified at actual dynamic-buffer calls |
| Unchanged compact contents | 0 upload bytes |
| One changed matrix | Positive upload of at most 64 bytes |
| Capacity 257 at draw capacity 256 | 2 actual instanced draws, no lost instance |
| Initial 256 image | Foreground 13,887 pixels in both; coverage difference 0, RGB max difference 0 |
| Explicit alpha-test texture | Foreground 10,423 in both, versus 13,887 without holes; RGB max difference 0 |
| Edited source geometry + invalidation | Foreground 12,584 in both; RGB max difference 0 |
| GL20 | 256 ordinary fallback draws with `GL30_UNAVAILABLE`; exact initial image match |
| Native assertions | GL33: 71; GL20: 7 |
| Reactor | 657 JUnit tests across 76 suites, no failures/errors; full `build` passed |

Draw counters come from both GLProfiler and a delegating GL proxy that counts actual `glDraw*Instanced`/ordinary draw calls. Matrix-upload counts come from intercepted dynamic-buffer `glBufferSubData`, not an assumed count. `InstanceStats.draws` describes instance-path draws; ordinary fallback draws are measured separately. `uploadBytes` describes instance matrices, excluding one-time owned vertex/index preparation.

[GL33 comparison](gl33-comparison.png) and [GL20 comparison](gl20-comparison.png): ordinary left, optimized/fallback right. The two images in each pair use identical camera, final transforms and viewport. [Cutout holes](gl33-alpha-test-holes.png) and [ordinary alpha-test flag without blendedFlag](gl33-alpha-test-without-blended-flag.png) preserve DefaultShader semantics.

The strict main comparison requires identical coverage, substantial foreground in both images, and RGB error ≤2/255 outside a one-pixel silhouette band. The moved hierarchy/presentation integration has an explicit controller-approved exception: at most **one** additional changed pixel, only within one pixel of a baseline color discontinuity >16/255; coverage must still match exactly. On Mesa this is image coordinate (105,91), RGB max difference 24/255, on the translated cube's front/right face crease. [Ordinary](integration-moved-ordinary.png) and [instanced](integration-moved-optimized.png) captures preserve the evidence. All other tested images satisfy the strict comparison. Negative controls prove that an interior color edit or a second changed crease pixel fails. This limited exception is not a general whole-render pixel-equivalence guarantee.

## Compatibility and lifecycle

Supported: rigid triangles, position/normal and optional UV0 float attributes, positive uniform scale, diffuse color/texture, cull/depth state with ordinary opaque depth testing/writes, ambient light and up to two directional lights. Equal material names with unequal values split groups. Per-instance material values are snapshotted, so in-place mutation of a removed representative cannot color surviving members.

Opaque cutouts use `FloatAttribute.AlphaTest` **and** `new BlendingAttribute(false, GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, 1)`, matching DefaultShader's `blendedFlag` variant with blending disabled. AlphaTest alone is intentionally ignored by both ordinary and instanced programs, matching libGDX1.14.2. Actual blending, order-dependent depth states, nonuniform/mirrored/sheared transforms, unsupported attributes, shadows or other environment features retain reasoned ordinary fallback. Supported WORLD AvatarView regions can opt into the skinned path; the separate viewmodel remains ordinary.

Native checks cover an ordinary/instanced mixed world, parent plus presentation motion and culling, material mutation, removal, geometry edits/invalidation, source ordinary rendering before/after owned-batch disposal, foreign VAO/divisor/buffer/depth/blend/cull restoration, injected instance-buffer allocation failure with complete cleanup, idempotent disposal, and pruning an empty scene. The allocation-failure stack in verification output is intentional. Every tracked owned buffer, shader and program is released after the successful probe.

`contextRestored()` (also forwarded by `GdxRenderer.restoreContextShaderBindings()`) is only for an **actually new context after libGDX managed Mesh/ShaderProgram restoration**, on the owner thread, outside ModelBatch. Owned managed geometry retains CPU vertices/indices; raw old instance names are abandoned without deletion in the new namespace; compact CPU matrices reupload and shader objects recreate all locations. Do not invoke this hook as a live-context reset: old raw names would be abandoned rather than released.

**Historical stage-A scope:** these static desktop captures did not prove EGL recovery. Stage C adds the separate benchmark and Android evidence below. Physical ARM64 and device performance remain unverified.

See [verification output](verification.txt) and [full build log](build.log).


## Stage C: animated crowds

[Polish enabling guide](../../gpu-instancing-guide.md), [architectural rulings](../../gpu-instancing-decisions.md), [independent-pose/vertex/pixel oracle](../skinned-instancing/README.md). Enable `InstancedModel` beside the ordinary model component; `GdxRenderer(24,4)` supports this Fox fixture and its ordinary fallback. CPU `PoseBatchSystem` is optional.

```sh
python3 tools/animation-validation/benchmark_instancing.py
python3 tools/animation-validation/benchmark_instancing.py --verify-only
./gradlew build :javity-desktop:skinnedInstancingSmoke
```

The native workload runs64/256/1024 actors with seed42, a fixed640×480 orthographic camera,120 warmup and300 measured frames per variant. Synchronous and independent phases each run ordinary/instanced; the extra256 synchronous cached case measures the optional CPU service separately. All actors are verified visible and eligible. Real native draw dispatch, actual full-pose composition deltas, actual palette/instance client update bytes and per-owner measured-frame marker/loop callbacks are recorded in [benchmark.json](benchmark.json). [verification.json](verification.json) binds the evidence to loaded JAR/workload class and source/fixture hashes; verify-only is read-only and rejects stale source/evidence.

| Visible actors | Ordinary draws/frame | Instanced draws/frame | Shared/independent palette bytes over300 frames |
| --- | --- | --- | --- |
|64|64|1|460,800 /29,491,200|
|256|256|1|460,800 /117,964,800|
|1024|1024|4|460,800 /471,859,200|

These are actual dispatch/uploads, not owner-count estimates. Each palette contains24 complete matrices. Unchanged matrix/world contents avoid instance reuploads after warmup. Ordinary and GPU-only variants still perform the same measured CPU compositions; optional CPU caching reduces256×300 full compositions to300 but adds source-validation/copy costs. Fewer compositions must not be described as a net CPU speedup without the recorded timings.

Step and render-submission wall time are separate. Driver waits and the delegating call counter affect CPU submission time. Each run drains prior GPU work before warmup. Actual `GL_TIME_ELAPSED` queries run only when GL33/ARB_timer_query is available; results are collected after GPU completion outside timed CPU sections. No screenshots/readPixels occur in measured loops. Mesa llvmpipe in a shared4CPU container is a correctness/per-run timing environment, not a forecast for a discrete GPU or phone. Android instrumentation is kept idle during the final benchmark.

[Large perspective comparison](showcase-comparison.png) (ordinary left, instanced right), [ordinary](showcase-ordinary.png), [instanced](showcase-instanced.png) are a separate presentation view. Their PNG capture explicitly flips framebuffer Y for upright display. Fixed-camera numerical captures and the stage-A/C oracle retain their historical raw framebuffer orientation; the showcase does not replace those measurements.

Fox attribution remains PixelMannen (model, CC0), tomkranis (rig/animation, CC BY4.0), AsoboStudio/scurest (glTF conversion, CC BY4.0). [Original source](../../../tools/animation-validation/fox/SOURCE.md), [notices](../../../tools/animation-validation/fox/NOTICE.md), [fixture hashes](../../../tools/animation-validation/fox/hashes.json). The existing imported24-bone oracle remains authoritative; Android uses a small authored two-bone fixture.

## Android recovery evidence

The named test extends the existing forced, non-preserved GLSurfaceView pause/resume harness. It retains the same probe and static/skinned batch owners; managed libGDX restoration is followed by explicit raw-owner `contextRestored()` forwarding. The test records actual EGL native handles without requiring numerical inequality: EGL names/handles may be reused (ABA). It compares independent poses with ordinary pixels, checks a wrong-row negative control, raw palette/instance reuploads, static instances, UI/font/SDF, ordinary models after the pass and tracked owned GL-name deletion while borrowed sources remain drawable.

```sh
./gradlew -PjavityAndroid=true :javity-android-app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=org.javity.android.AndroidManagedContextLossInstrumentationTest
```

**PASS on the API35 x86_64 TCG emulator, GLES3.0 SwiftShader.** [Structured evidence and APK/source hashes](android-recovery.json), [named test XML](android-final.xml), [actual owner/pixel logcat](android-recovery-logcat.txt), [build/test log](android-final.log). The final named test passed in60.779s; app CPU tests13/13 and R8 release assembly also passed. This task executed the debug APK; it does not claim a new R8 runtime test.

The same helper/child owner identity1 survives forced context replacement; managed TextureData uploads increase1→2, child recovery hooks0→1. The EGL native handle is137758160961248 both before and after (valid ABA reuse). Skin/static batches each issue one actual native instanced draw. Two distinct palettes reupload256B; dynamic instance buffers reupload264B after replacement. Before/after observations are identical:528 red skin pixels,432 green static pixels,216 subsequent ordinary blue pixels, RGB delta0; the wrong-row negative control changes258 pixels. Parent UI/font/material/SDF and the main ordinary world model also recover. All13 tracked owned buffers/programs/stages/palette textures are deleted; ordinary rendering afterward still produces528/432/216 pixels from both borrowed mesh sources, without GL errors.

This runtime gate exposed a GLES translator compatibility issue: an inline four-`texelFetch` matrix constructor produced no skin pixels despite correct palette data. [Controlled diagnosis](android-gles-diagnosis.txt) confirms valid GPU texels and restores both poses with named high precision column fetches. The production shader now uses that mathematically equivalent form; desktop GL33/GL20, imported Fox transform feedback, static, avatar and presentation regressions pass with unchanged tolerances. This is evidence about the tested translator, not a claim that every GLES driver has this behavior.

Physical ARM64 and Android device-performance certification remain unverified. [Initial missing-helper/native pixel RED](android-initial-failure-logcat.txt), [pixel-failure XML](android-initial-failure.xml), [benchmark RED](benchmark-red.log) preserve the test-first gates.


## Canonical per-frame timings

Final run2026-10-16,15:05:22–15:12:08UTC; actual Mesa4.5 core/llvmpipeLLVM19.1.7, JDK25.0.4.1+1, shared4CPU quota. Values are milliseconds/frame, ordinary / instanced, averaged over300 measured frames. GPU values are actual elapsed queries on this software driver. These are descriptive observations with driver/counter overhead, not hardware forecasts.

|Actors|Phase|CPU step O / I|CPU submission O / I|GPU elapsed O / I|
|---|---|---|---|---|
|64|synchronous|0.735 / 0.884|11.349 / 8.905|12.632 / 11.036|
|64|independent|0.562 / 0.815|10.218 / 8.817|11.886 / 10.818|
|256|synchronous|4.005 / 4.073|45.041 / 34.254|51.435 / 40.179|
|256|independent|2.826 / 3.965|42.743 / 33.180|46.359 / 38.698|
|1024|synchronous|16.535 / 16.056|173.798 / 129.950|197.856 / 152.813|
|1024|independent|15.894 / 16.742|169.207 / 138.787|191.669 / 162.872|

The separate256 synchronous CPU-cache case reduces actual full compositions76,800→300, but CPU step increases4.073→5.201ms. Submission is34.254→33.444ms; GPU elapsed40.179→40.995ms. This does not support a CPU-cache speedup claim. Dynamic instance uploads are actually0B in the measured stable-transform frames after warmup; bone palette uploads remain measured separately above.

[Provenance negative control](provenance-negative-control.txt) rejects a changed recorded source digest and proves the verifier leaves the rejected manifest untouched. Restoring the original manifest passes. The manifest explicitly records the dirty source checkout and source hashes; actual executed class/JAR bytes identify the run.

Final reactor:733tests/81suites,0failures/errors/skips. Animated GL33/GL20, static GL33/GL20, avatarGL30 and presentation regressions pass ([log](task3-desktop-final.log)); additional physics3d/voxel/postprocess regressions pass ([log](task3-extra-regressions.log)). Numerical crowd captures and the upright showcase have measured RGBdelta0 between ordinary/instanced PNGs.
