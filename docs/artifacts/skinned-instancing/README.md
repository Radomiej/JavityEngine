# Animated GPU instancing — Stage C Task 2

Desktop evidence, 2026-10-16. Requested GL3.3 core; actual Mesa 25.0.7 OpenGL4.5 core, llvmpipe LLVM19.1.7. GL20 fallback uses libGDX's GL20 backend (`Gdx.gl30 == null`) on the same driver's compatibility context; this is not a claim of a GL2-only physical device. Android/GLES runtime, actual context loss/recovery, and the standardized 300-frame benchmark remain Task3.

## Reproduce

With JDK25 and an available desktop display:

```sh
./gradlew build \
  :javity-desktop:skinnedInstancingSmoke \
  :javity-desktop:skinnedInstancingGl20Smoke \
  :javity-desktop:instancingSmoke \
  :javity-desktop:instancingGl20Smoke \
  :javity-desktop:avatarGl30Smoke \
  :javity-desktop:presentation3dSmoke
```

The launcher uses a hidden native window. The small fixed-camera grid is numeric proof, not the final user-facing animation showcase; Task3 will add a clearer perspective showcase separately from the benchmark camera. Its framebuffer is 640×480. [GL33 comparison](gl33-comparison.png) and [GL20 comparison](gl20-comparison.png) show ordinary left, optimized/fallback right. Separate ordinary/instanced PNGs cover fade, masked tail, mixer, alpha test, budget fallback and post-error ordinary rendering. `avatar-*.png` show WORLD prepared-region selection.

Fox fixture: [source and licensing](../../../tools/animation-validation/fox/SOURCE.md), [conversion/attribution notice](../../../tools/animation-validation/fox/NOTICE.md). Model by PixelMannen (CC0); rig/animation by tomkranis and glTF conversion by AsoboStudio/scurest (CC BY4.0). The existing fixture and notices are preserved. Authored avatar geometry is the repository's procedural two-bone fixture.

## Measured results

- Six Foxes: Walk/Run/Survey at .25/.5 seconds, six independently composed canonical skin palettes, **6 actual ordinary draws → 1 actual instanced draw**. GLProfiler and intercepted real GL entry points agree.
- Initial dynamic upload: **9,216 palette bytes + 408 instance bytes**. Unchanged subsequent views: **0 bytes**. These counters cover successful client palette and instance-buffer updates, excluding immutable geometry copying and null GPU storage allocation.
- Production vertex shader transform feedback: **10,368 indexed vertex outputs**, recovered into source coordinates, maximum absolute component difference **0.0001068115234375 source units** against ordinary CPU weighted skinning (limit .01). The probe requests `gl_Position` feedback before shader link; it does not substitute a different skinning shader.
- Original glTF oracle: all 12 samples ×1,728 vertices, maximum CPU palette/vertex error **0.006999969482421875 source units** (limit .01).
- Independent/shared poses, crossfade, tail override, mixer, diffuse texture/directional lighting, alpha test, row-budget fallback, post-error rendering and prepared WORLD presets: **RGB delta0 everywhere** in these captures. Required comparison remains ≤2/255 outside a one-pixel silhouette band. No animated raster tolerance exception was needed.
- Nonempty control: initial Fox image has **6,453 foreground pixels**. Deliberately replacing the second Fox's row with the first Fox's pose changes **1,114 pixels** ([negative control](gl33-negative-wrong-row.png)).
- 257 distinct palettes split into two page groups/draws; capacity2 splits six actors into three draws; total row budget2 renders two actors instanced and four ordinary without loss.
- Prepared avatar FULL_BODY / ARMS_ONLY / BODY_WITHOUT_HEAD WORLD overrides opt in with separate stable source-part/region keys. The separate viewmodel uses ordinary draws. Canonical node/socket matrices and clocks remain unchanged. Tests run both without PoseBatchSystem and with it; measured full local-pose compositions for two equal actors are 2 and1 respectively. This is not a total CPU speedup benchmark.
- Unsupported fog/spot/material, inadequate attributes/texture size/vertex texture units/combined units, malformed ranges, and palette overflow have explicit fallback reasons. CPU tests exercise unsigned index39999, part-range isolation, invalid weights and an animated bound entering the camera while the bind bound remains outside.
- Active texture unit and touched diffuse/palette bindings are restored; injected instance-allocation and palette-upload failures return the undrawn group for ordinary rendering and allow a successful retry. Borrowed Fox mesh/textures survive batch disposal. All tracked owned buffers, programs, shader stages and palette textures are deleted.
- Scene identity boundary: close a 3D Game at frame0 → render a camera-less menu → render another Game at frame0. Both static/skinned instance buffers and palette textures are deleted, stable-owner maps and stats clear, and the next scene uploads fresh rows. `render2D` remains usable for an independent UI overlay without resetting world batches.

## Verification logs

- [RED missing API](red-missing-api.log): real native startup reaches ClassNotFoundException for the absent SkinnedInstanceBatch.
- [RED scene transition](red-scene-boundary.log): named assertion `camera-less new scene clears previous stats` before the boundary fix.
- [Full reactor and native regressions](full-reactor-and-regressions.log): 733 tests /81 suites, zero failures; core447 tests /45 suites. Static native71 GL33 /7 GL20 assertions, avatarGl30Smoke and presentation3dSmoke pass.
- [Final animated native](final-native.log): **126 GL33 assertions /7 GL20 assertions**, including final owned-resource checks.
- [Final build](final-build.log): final source/probe build.

The static regression retains its previously documented crease-pixel exception (max RGB24 in one comparison); the policy and original static probe were not widened by this work.

## API and supported boundary

`new SkinnedInstanceBatch(capacity, maxPaletteRows)` accepts canonical `Renderable.bones` with no CPU cache dependency. `render(candidates, camera, environment)` returns unchanged fallback identities; its stable-ID overload supports renderer-owned component/part keys. `stats()`, `fallbackReasons()`, `invalidateGeometry(mesh)`, `contextRestored()`, and `dispose()` follow the static batch lifecycle. Calls belong to the GL owner thread, outside active ModelBatch. Geometry is borrowed and assumed immutable until explicit invalidation.

The source MeshPart, ordered vertex/weight layout and actual ordered skin matrices define the GPU slot contract. Each matrix already includes its source inverse bind. Same bone count, Model identity, pose-cache key or hash never deduplicates matrix rows; the palette compares all matrix bits. Different compatible rigs bound to the same geometry can therefore use distinct rows without reinterpreting their bone ordering.

Supported geometry is triangles with standard float position/normal, optional UV0, and contiguous float bone-weight attributes0..N−1 (up to8), nonnegative finite weights with positive totals and valid indices. World transforms require positive uniform scale; skin matrices must be finite affine matrices for conservative bounds. Material/light support follows the static DefaultShader subset: diffuse color/texture, alpha-test with its original DefaultShader flag semantics, cull/depth settings, ambient light and up to two directional lights. Other active features fall back explicitly.

The shader retains libGDX's original material, lighting, weighted-position and weighted-normal formulas. Shared transport owns independent geometry/VAO and sparse 17-float instance records (world matrix + row). The material binder is restricted to texture unit0; palette owns unit1. GLES300 vertex matrix/row arithmetic and palette sampler use explicit high precision. Actual GLES capability/runtime and loss/recovery remain unverified here.

`contextRestored()` abandons old raw texture/buffer names and recreates shader locations after the platform's managed restoration. Do not call it as a substitute for an actual context-loss test. `ModelPoseAnimator.fullPoseCompositions()` is a monotonic hook counting actual full local-pose composition calls, including uncached compatibility fallback; it does not count every scalar channel interpolation or callback contribution.
