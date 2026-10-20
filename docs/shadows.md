# Optional directional shadows

`javity-gltf` provides `DirectionalShadowPass` for the ordinary engine PBR world.
Objects remain `GameObject` + `Transform3D` + `ModelRenderer`; the existing camera,
scene light snapshot, presentation transforms and local `AvatarView` collection
feed both the depth and color passes. There is no separate gameplay scene graph.

```java
PbrShaderProvider pbr = new PbrShaderProvider(64, 2, 4, 0);
GdxRenderer renderer = pbr.createRenderer();
DirectionalShadowPass shadows = new DirectionalShadowPass(
    pbr, sun, // the first eligible DirectionalLight3D in this scene
    new DirectionalShadowPass.Settings(
        1024, 30, 30, 0, 60, // resolution, orthographic width/height, near/far
        0, 0, 0, .001f),    // fixed world center and normalized depth bias
    64, 4);                // bone/bone-weight capacities for the depth pass
renderer.setWorldRenderPass(shadows); // transfers exclusive ownership

ground.addComponent(new ShadowPolicy3D(false, true));
character.addComponent(new ShadowPolicy3D(true, true));
```

The pass requires an active GL30 context and its exact borrowed `PbrShaderProvider`
token. Installing with another provider fails before ownership transfer; a pass
cannot be transferred twice. One pass can be installed during a renderer's lifetime.
Close the renderer before borrowed model assets and before the GL context. The
pass is tied to its selected scene sun; create a new renderer/pass when replacing
that scene. Close the old renderer before releasing its scene assets. The
renderer owns and closes the installed pass, including after another cleanup fails.
Construction, configure, draw, recovery and disposal use the creating GL thread.

## Policy and light selection

An absent, disabled, pending or removed `ShadowPolicy3D` means neither casting nor
receiving. The first attached policy controls every `ModelRenderer` on that object.
Its public `cast` and `receive` flags are independent. Object/component visibility
and lifetimes still apply. Viewmodels and 2D/UI are outside this world pass.

The selected sun must be the first eligible scene directional and be included in
the published light capacity. The pinned PBR shader shadows directional index
zero; selecting another sun is an explicit error. Other directionals, point
lights and IBL remain unshadowed. This first increment supports no point or spot
shadow maps. Core DefaultShader scene lighting is outside this PBR pass contract.

The orthographic volume has an explicit fixed world center. Choose width/height,
near/far and center for the level's relevant region. The camera uses the published
sun direction and a safe up vector for vertical sunlight. Geometry outside the
volume is not guaranteed a useful shadow. There is no automatic frustum fitting,
texel stabilization, CSM, contact shadow, or tunable soft-shadow kernel. Filtering
uses upstream's four fixed packed-depth PCF taps; bias is an explicit depth-space
value, not a world-distance setting.

Opaque and UV0 texture alpha-tested MASK casters are supported. MASK casters
require unit base-color factor alpha and opacity, no vertex-color attribute,
and the upstream `BlendingAttribute` marker alongside `AlphaTest`. Nonzero
effective base-color UV indices (including texture-transform overrides) and
other MASK variants reject before the depth draw, because the pinned upstream
depth shader cannot reproduce their color-pass alpha. These materials remain
usable with `cast=false`; receiving is independent. Transparent BLEND
casters are rejected before drawing; disable `cast` or use an explicit MASK
material. Skinning uses the configured capacities and current engine pose. The
optional importer currently rejects morph content, so this guide does not claim
morph asset support. Custom world passes disable instancing and submit ordinary
draws to preserve policy and material behavior.

## Targets, state and recovery

Resolution is bounded to 16–2048 pixels per side. The target uses RGBA8888 packed
depth plus a 16-bit depth attachment, six known attachment bytes per pixel. The
old + candidate configure peak is bounded to 64 MiB. A replacement is allocated
before the previous target is released; incomplete targets leave the previous
settings, target and caller state available. GL20 contexts fail explicitly before
allocation; there is no hidden unshadowed fallback.

The depth pass owns its framebuffer and depth `ModelBatch`. It copies renderable
descriptors so a depth shader never leaks into color submission. Receivers and
nonreceivers use separate stable environments through the color batch. Shared
model materials, meshes, animation state, lights, IBL and gameplay transforms
remain borrowed. A failed depth draw retires the interrupted batch and preserves
the original failure so a subsequent frame can render again.

The pass restores split FBO bindings, renderbuffer, viewport/scissor, VAO/VBO/index
bindings, program, all combined texture-unit 2D/cubemap/sampler bindings, depth,
blend, cull/front-face, stencil/raster-discard/polygon-offset enable flags, masks,
clear values and depth range. On configure, a caller binding to a retired
pass-owned target is replaced with the corresponding new owned name; unrelated
borrowed bindings remain unchanged. Scene mutations are outside the active pass.

After a genuine context change, run libGDX's managed texture/program/mesh/FBO
recovery in the host, then `renderer.restoreContextShaderBindings()` before any
draw. The pass uses `RecoverableFrameBuffer` and recreates retained depth shader
caches. Its `contextRestored()` hook alone does not rebuild the host's managed
resources. Do not simulate context loss by rebuilding live FBOs in the same
context: the lost-context path intentionally drops destroyed names without
deleting potentially reused names in the replacement context.

`GdxRenderer.restoreContextShaderBindings()` also calls
`ManagedMeshBindingsRecovery.contextRestored(Gdx.app)`. This version-checked
libGDX1.14.2 adapter clears stale VAO attribute-location caches and bound flags
for all managed meshes of the current application, including borrowed models and
receive-only geometry. It retains no mesh list and changes no geometry or
ownership. Hosts calling the helper directly must do so after managed mesh
invalidation, outside rendering on the GL owner thread. Android R8 keep rules
retain the pinned fields.

## Evidence

`DirectionalShadowTest` covers CPU configuration bounds, opt-in policy lifetime
and first-directional selection. The desktop `directionalShadowSmoke` launcher
uses actual scene components and numeric framebuffer comparisons for caster
movement, independent cast/receive, fully discarded and opaque MASK textures,
rejection of MASK factor/opacity/UV1/vertex-color variants,
imported skinned pose changes, unsupported BLEND rejection, failed draw recovery,
incomplete target rollback, resize and resource deletion. It also creates a second
unshared GLFW context, runs managed recovery and compares the entire first
unchanged-policy world frame byte-for-byte against the preloss frame, including
the receive-only floor. It then checks recovered shadow pixels and an imported
skinned caster against same-context receive-off/on pairs. Per-pass caller-state fingerprints and GL errors are
checked. PNG evidence is written to `build/reports/shadows/`.

This establishes the tested desktop software-GL path. Android/device performance,
WebGL/TeaVM, automatic shadow fitting and broader visual quality remain separate
validation work. HDR requires its own linear floating render-target and tone-map
contract; these packed-depth shadow targets do not implement HDR.
