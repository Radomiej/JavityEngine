# Scene-owned 3D lighting

Register `RenderEnvironment3D` before `Game.start()`. Attach `Transform3D` and
`DirectionalLight3D`, `PointLight3D`, or `SpotLight3D` to scene objects. The renderer
copies active, initialized lights immediately before the world pass. Component
removal, disabling, inactive owners and scene replacement take effect on the next
render. Lights without the scene system do not change legacy rendering.

```java
RenderEnvironment3D environment = game.addSystem(new RenderEnvironment3D());
environment.setAmbient(.12f, .12f, .15f);
GameObject lamp = game.createObject("lamp");
lamp.addComponent(new Transform3D()).position.set(0, 3, 4);
SpotLight3D spot = lamp.addComponent(new SpotLight3D());
spot.setColor(1, .85f, .65f);
spot.setIntensity(2);
spot.setRange(12);
spot.setCutoffDegrees(35);
spot.setExponent(2);
environment.setFog(.35f, .4f, .5f, 10, 60);
```

Directional and spot directions follow transformed local **-Z**, including parent
transforms, and are normalized. Singular direction transforms and nonfinite
values are rejected. Point and spot positions are world-space. Colors and
intensity must be finite and nonnegative; range is positive, cutoff is strictly
between 0 and 90 degrees, and exponent is nonnegative. Defaults are white,
intensity 1, range 10, spot cutoff 30 degrees and exponent 1. Scene ambient
starts black and fog starts disabled; no sun is added automatically.

Point/spot attenuation is `max(0, 1 - distance/range)^2`. Spotlights additionally
multiply by `pow(max(dot(direction, lightToFragment), 0), exponent)` inside the
cutoff cone, and contribute zero outside it. Intensity multiplies the result
once. Fog blends the final model RGB with its color using
`clamp((eyeDistance - near)/(far - near), 0, 1)`, with `0 <= near < far`.
`clearFog()` removes it. Fog applies to ordinary scene models with or without normals. The scene-only prefix and per-draw fog uniforms are independent of lighting. Legacy shader behavior is unchanged; fog does not affect arbitrary custom shaders, Shapes or 2D UI.

Both `GdxRenderer()` and `GdxRenderer(maxBones, maxBoneWeights)` provide scene
capacities **2 directional / 4 point / 2 spot**. The advanced constructor
`GdxRenderer(maxBones, maxBoneWeights, directional, point, spot)` configures
**only scene-mode capacities**, including zero. All constructors retain stock
libGDX capacities and shaders for legacy `renderer.environment`; adding a spot
to that legacy environment does not enable spotlight support. Overflow keeps
the first active lights by object ID, then component order. After a snapshot,
`RenderEnvironment3D.overflow()` reports discarded counts by light type.

The scene environment replaces renderer defaults for world models. Its marker
participates in shader cache matching and currently forces static/skinned
instance candidates through ordinary rendering, preserving scene lighting at
the cost of instancing performance. The snapshot is borrowed until the next
snapshot call; callers must not mutate it or retain its light objects as live
component state.

`renderViewmodel(camera, provider)` deliberately uses legacy renderer lighting
without scene fog or sky, keeping FPS hands readable independently of the world.
This can produce a visible lighting difference between hands and world objects.

The implementation is forward lighting without dynamic shadows or PBR. The integrated `environmentSmoke` now checks measured directional/point/spot/fog, scene-skinned textured poses and alpha-test depth holes, moved uniform recovery, WORLD clock, borrowed sockets and viewmodel/HUD isolation on desktop GL20/GL30. [Day/night and authoring](lighting-and-sky.md), [actual captures and scope](artifacts/environment/README.md). Android feature EGL gates remain **UNVERIFIED**.

## Sky backgrounds

Choose one immutable background on the scene environment:

```java
environment.setSky(SkySettings.gradient(
    new Color(.1f, .3f, .8f, 1),   // zenith (+Y)
    new Color(.7f, .8f, .9f, 1),   // horizon
    new Color(.15f, .12f, .1f, 1) // nadir (-Y)
));
// Or use a cubemap owned by an asset loader/lease:
environment.setSky(SkySettings.cubemap(borrowedCubemap));
environment.clearSky();
```

`SkySettings` copies finite, nonnegative RGB values without clamping and always
renders an opaque background; input alpha is ignored. Gradient and cubemap modes
are exclusive. Colors read through `zenith(out)`, `horizon(out)` and `nadir(out)`
are copied into the supplied color. The cubemap is borrowed: keep its owner or
lease alive until every scene/pass stops using it. Settings, scene closure and
sky-pass disposal do not dispose that cubemap.

`GdxRenderer` lazily owns one `SkyboxRenderer`, drawing it before world batching.
Camera translation causes no parallax; rotation changes the sampled direction.
Both perspective and orthographic cameras are supported. The pass writes no
depth and preserves nearer geometry. World postprocessing can include it by
capturing the world pass. It does not run during viewmodel or 2D/UI rendering.
A scene without sky settings adds no background draw.

Standalone `SkyboxRenderer.render(camera, settings)` also uses the caller's
framebuffer and viewport. Creation, rendering, `contextRestored()` and repeated
`dispose()` belong on its original GL thread, outside all batches. It owns its
shader and mesh. Managed context recovery must recreate libGDX resources first,
then call `GdxRenderer.restoreContextShaderBindings()` (or the standalone sky
hook) before drawing. The sky hook queries actual uniform **and attribute**
locations directly from GL, bypassing pinned libGDX 1.14.2's stale alias maps.
It also replaces its tiny owned mesh so fresh VAO descriptors are configured even
when managed recreation preserves every attribute location.
The existing model-provider alias-refresh requirement still applies to models.

The sky pass restores the actual program, buffers/VAO, texture-unit/cubemap
binding, depth test/function/mask/range, blend, cull, scissor, stencil and color
mask after success or drawing failure. Viewport, framebuffer, blend factors and
cull mode are left untouched. GL30 uses its own VAO. GL20 reserves a disabled
attribute slot with default descriptor and buffer zero, and rejects a configured
reserved slot before drawing. No client-side pointer may be configured at that
slot: it is checked where the backend supports pointer queries; otherwise the
caller must guarantee this precondition. If construction/recovery cannot find
an eligible slot, it fails without drawing. This is bounded GL20 input-state
support, not arbitrary client-array restoration.

Run `./gradlew :javity-desktop:skySmoke` for the dedicated real GL30 and GL20
pixel/state/resource gate. It checks both modes, translation/rotation, depth,
exception cleanup, borrowed lifetime, repeated disposal, actual program relink
with moved locations, viewmodel and SCREEN isolation. Evidence snapshots are
[GL30](sky-pass-task2-gl30.png) and [GL20](sky-pass-task2-gl20.png). The local
GLES300 sources also compile/link on the desktop Mesa compiler; this does not
prove GLES device rendering or actual Android EGL loss/recovery. Desktop shaders
use GLSL120, and no global shader prefixes or pedantic setting are changed.
