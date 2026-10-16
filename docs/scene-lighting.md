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
`clearFog()` removes it. Fog does not affect arbitrary custom shaders or 2D UI.

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

The implementation is forward lighting without dynamic shadows or PBR. Task 1
checks CPU snapshots, retained-uniform recovery and actual scene shader draws on
desktop GL20/GL30. Full measured lighting/fog pixel controls, sky/day-night and
Android EGL feature gates belong to later tasks; they are not implied by these
foundation checks.
