# Optional HDR world rendering

`javity-gltf` provides an opt-in `HdrWorldPass`. It captures linear world radiance
in an actual RGBA16F texture with a depth16 renderbuffer, applies exposure and
ACES or Reinhard tone mapping, then explicitly encodes linear RGB to sRGB.
The existing default renderer and RGBA8 `WorldPostProcess` remain separate APIs.

```java
PbrShaderProvider pbr = new PbrShaderProvider(32, 2, 4, 0,
    PbrShaderProvider.OutputColorSpace.LINEAR);
GdxRenderer renderer = pbr.createRenderer(); // owns the provider
HdrWorldPass hdr = new HdrWorldPass(physicalWidth, physicalHeight);
hdr.setSettings(new HdrSettings(1f, HdrSettings.ToneMapper.ACES));

// After simulation and camera setup, before display-space UI:
hdr.renderWorld(() -> renderer.renderWorld(game, camera, null));
// Render viewmodel/HUD/Stage here; these retain their own display-space policy.
```

The callback must produce linear radiance. The default four-argument PBR provider
outputs display RGB; choose `LINEAR` explicitly for HDR. The upstream pinned
`gdx-gltf 2.3.0` PBR path has its manual output gamma correction disabled only
in this mode; accurate sRGB input texture decoding remains enabled. It does not
apply an upstream tone operator. Standard renderer materials, custom shaders,
skies and already processed RGB are not automatically converted to linear.
Do not include display-space HUD or the existing RGBA8 postprocess inside the
capture callback. Calling `renderWorld` without a linear source cannot repair
values previously clipped into an RGBA8 target.

`HdrSettings.exposure` is a positive linear multiplier in `(0,64]`, not stops.
ACES is the bounded fitted ACES curve, not a complete film color-management
pipeline. Reinhard maps each channel through `x/(1+x)`. Negative radiance is
clamped to zero. The result is explicitly sRGB encoded; desktop framebuffer
sRGB conversion is disabled during capture/composition and its prior state is
restored. This pass replaces the current viewport's color, including alpha,
with the captured result. It does not composite transparent world color over
the prior framebuffer's background. Bloom, auto exposure, local tone mapping,
MSAA and wide-gamut output are outside this increment.

Construction, resize, capture, recovery and disposal require the owning live
GL30 thread. GL30 availability alone is insufficient: construction allocates
the requested RGBA16F/depth16 combination and requires actual framebuffer
completeness. Unsupported contexts throw; the host chooses a regular renderer
or another presentation path. There is no silent RGBA8 substitution and no GL20
HDR path. Verified runtime scope is desktop Linux software OpenGL; Android,
GLES hardware, physical GPU performance and Wasm remain separately unverified.

The pass owns its target, shader and quad. The callback, renderer, models,
textures and output framebuffer remain borrowed. Capture returns to the caller's
original draw framebuffer and viewport before UI. It restores split read/draw
bindings, VAO/buffer/program bindings, all combined texture-unit 2D/cubemap and
sampler bindings, masks, depth/blend/cull/front-face/scissor state, clear values,
depth range, stencil/raster-discard/polygon-offset enables and desktop sRGB
conversion even when the callback throws. The first callback failure is retained.
The callback should not mutate unrelated GL state outside that list.
Reentrant capture, resize, settings mutation and disposal during capture reject.

The known attachment estimate is 10 bytes per physical pixel: eight for RGBA16F
and two for depth16. Default limits are 4096 pixels per dimension and 128 MiB.
Driver bookkeeping and shader/mesh resources are outside that attachment
estimate. Resize allocates and verifies a candidate before publishing it, and
requires old plus candidate to fit the budget. Failure retains the old target.
When callers previously bound an owned target's names, successful resize remaps
those saved bindings to the replacement before deleting retired names.

After a real context replacement, perform the host's libGDX managed texture, shader,
mesh and framebuffer invalidation, then call `hdr.contextRestored()` and
`renderer.restoreContextShaderBindings()` before drawing. The renderer hook also repairs
pinned libGDX managed VAO binding bookkeeping for the current app's borrowed/imported
meshes without changing their geometry or ownership. The HDR FBO uses core
`RecoverableColorFrameBuffer`, a shared owned allocator for one GPU-only color
attachment plus optional depth16. It keeps exactly one managed registry entry and drops
lost-context bookkeeping without deleting stale names. The recovery hook recreates the
owned program and quad: pinned libGDX VAO invalidation retains its attribute-location
cache after creating a fresh VAO, so a retained fullscreen quad can otherwise skip
pointer setup. Do not call global invalidation to simulate loss while old GPU resources
are still live: it intentionally does not delete names belonging to the lost context.
Close HDR, renderer and borrowed-resource owners before closing the GL context.

`RecoverableColorFrameBufferDisposeTest` verifies that ownership is retired before
GL callbacks, cleanup continues after each delete failure, first and suppressed
errors retain identity, and repeated disposal cannot delete reused resource names.

`HdrSettingsTest` checks limits and peak attachment accounting.
`RecoverableHdrFrameBufferTest` executes the owned allocator, libGDX texture
construction and managed framebuffer invalidation against deterministic GL calls,
including half-float/depth formats, unsupported initial targets, seven unexpected
allocation-failure stages, multi-owner registry stability, failed recovery/retry and ABA
name reuse. Raw texture handles are owned before upload construction starts, so even an
upload exception cleans the incomplete candidate exactly once without guessing whether
libGDX deleted it. `HdrSmokeLauncher` proves real pixels: an RGBA8 negative control
clips radiance 1 and 4 to `[255,255]`, while RGBA16F plus Reinhard/sRGB produces
`[188,231]`; exposure 0.25 gives `[124,188]`, and ACES gives `[232,252]`. It also
exercises linear PBR emissive intensity 1/4, UI ordering, hostile caller state, callback
failure, a callback introducing a mipmap sampler on previously unbound unit zero,
incomplete-target and thrown-allocation rollback, budget rejection, resized capture,
bound-owned-name remapping, an actual unshared GLFW context replacement, managed
recovery including the unchanged borrowed PBR model, and GL object deletion. PNGs go to
`build/reports/hdr/`.