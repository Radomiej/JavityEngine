# Static GPU instancing desktop evidence

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

## Measured results

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

Opaque cutouts use `FloatAttribute.AlphaTest` **and** `new BlendingAttribute(false, GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, 1)`, matching DefaultShader's `blendedFlag` variant with blending disabled. AlphaTest alone is intentionally ignored by both ordinary and instanced programs, matching libGDX1.14.2. Actual blending, order-dependent depth states, nonuniform/mirrored/sheared transforms, skinning, unsupported attributes, shadows or other environment features retain reasoned ordinary fallback. Avatar overrides and viewmodels stay ordinary.

Native checks cover an ordinary/instanced mixed world, parent plus presentation motion and culling, material mutation, removal, geometry edits/invalidation, source ordinary rendering before/after owned-batch disposal, foreign VAO/divisor/buffer/depth/blend/cull restoration, injected instance-buffer allocation failure with complete cleanup, idempotent disposal, and pruning an empty scene. The allocation-failure stack in verification output is intentional. Every tracked owned buffer, shader and program is released after the successful probe.

`contextRestored()` (also forwarded by `GdxRenderer.restoreContextShaderBindings()`) is only for an **actually new context after libGDX managed Mesh/ShaderProgram restoration**, on the owner thread, outside ModelBatch. Owned managed geometry retains CPU vertices/indices; raw old instance names are abandoned without deletion in the new namespace; compact CPU matrices reupload and shader objects recreate all locations. Do not invoke this hook as a live-context reset: old raw names would be abandoned rather than released.

**Open C gate:** this task did not run a new-context/EGL-loss recovery test, Android GLES300 shader runtime, or Android device performance. Compilation and calling a hook in a live context are not recovery evidence. Actual Android context-loss/recovery and GLES proof remain separate stage-C requirements.

See [verification output](verification.txt) and [full build log](build.log).
