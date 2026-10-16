# Lighting, sky and day/night

Run `./gradlew :javity-desktop:run --args='--environment'` for **The Quiet
Observatory**, an original courtyard built from ordinary GameObjects and shared
models. Keys 1–4 select afternoon (.57), last light (.735), midnight (0) and
morning mist (.43). Buttons toggle point/spot, the WORLD cycle and WORLD pause.
F1 opens scene tools; the light buttons select the corresponding object. There
is no separate editor scene or reflection-based property access.

```java
RenderEnvironment3D environment = game.addSystem(new RenderEnvironment3D());
GameObject sunlight = game.createObject("Sun");
sunlight.addComponent(new Transform3D());
DirectionalLight3D sun = sunlight.addComponent(new DirectionalLight3D());
DayNightCycle3D cycle = sunlight.addComponent(new DayNightCycle3D(sun, environment, 180));
cycle.setPhase(.57);     // midnight 0; sunrise .25; noon .5; sunset .75
cycle.setEnabled(false); // optional held phase
```

Register the environment before `Game.start()`. The cycle, sun and environment
must belong to the same live Game; the sun needs Transform3D. The cycle borrows
both and stops updating after removal of its referenced sun. Its WORLD delta
obeys pause, time scale and freeze. `daySeconds` is finite and positive;
`setPhase` accepts finite [0,1] and applies immediately after initialization.
Updates wrap the clock. Direction is authored in world space, converting through
the sun's parent when present. The cycle owns the sun direction/color/intensity,
ambient and procedural gradient while enabled; a simultaneous editor draft may
be rejected if those values change. Pause/hold the cycle before editing the sun.
A cubemap remains the **same borrowed image** as phase changes; the cycle still
changes sun and ambient. It does not synthesize night variants of cubemaps.

`RenderEnvironment3D` replaces legacy defaults only for the world pass. It has
ambient, optional distance fog, and optional immutable `SkySettings.gradient` or
`SkySettings.cubemap`. Scene lights are components on objects with Transform3D:

| Type | Typed parameters | Direction/position |
| --- | --- | --- |
| DirectionalLight3D | RGB, intensity | world-transformed local -Z |
| PointLight3D | RGB, intensity, range | world position |
| SpotLight3D | RGB, intensity, range, cutoff degrees, exponent | world position and local -Z |

Default scene shader capacities are **2 directional / 4 point / 2 spot**;
advanced GdxRenderer constructors can change them. Overflow is deterministic by
object ID/component order and is reported by `environment.overflow()`. Disabled,
uninitialized, inactive or removed lights are omitted. RGB/intensity are finite
and nonnegative; range is positive, cutoff is strictly (0,90), exponent is
nonnegative. Point/spot falloff is `max(0,1-distance/range)^2`; spot adds its cone
and angular exponent. There is no implicit extra sun.

Fog blends final ordinary model RGB using world eye distance and finite
`0 <= near < far`. It applies to models **with or without normals**, including
the scene skin shader. `clearFog` disables it. Sky is drawn before world
batching, with no camera-translation parallax or depth writes. World effects
follow world drawing; viewmodel and SCREEN/HUD follow separately. The viewmodel
deliberately keeps legacy lighting without scene fog/sky, so hands can differ
from the world's illumination. Shapes, arbitrary custom shaders and MapLibre
are outside this fog path. Scenes without RenderEnvironment3D retain historical
`renderer.environment` behavior.

## Typed authoring and gizmos

RuntimeToolsOverlay shows a light draft for the selected object's lights,
including duplicate components via the type button. Reload captures a fresh
baseline; Apply validates the whole draft and creates **one command in the same
256-entry Undo/Redo history as transforms**. Sessions remember Game/object/
component identity, selection epoch and expected values. Scene replacement,
component removal, selection change or external parameter changes reject stale
commits. Stale history commands invalidate history rather than overwriting
external edits. The immutable API is
`SceneInspection.LightState(red,green,blue,intensity,range,cutoffDegrees,exponent)`;
parameters unused by that light type must be zero. No partial setter writes
occur on validation failure.

Light gizmos show directional arrows, point range circles and spot cones.
Movement/rotation uses the inspector's existing typed transform sessions and
TransformEditPolicy. `LightGizmoAdapter.draw` and `SocketGizmoAdapter.draw`
borrow an already-begun LINE ShapeRenderer with the camera projection. The
socket adapter borrows AvatarBinding/base, reads `socketTransform`, and never
moves bones. Hosts may call `tools.setSocketGizmo(binding, name, baseSupplier,
cameraSupplier)` and must clear it before releasing that binding. VoxelDemo
uses the canonical right-hand socket; closing the tools clears its borrowed
references and releases only its own optional renderer. It also explicitly
selects a held morning scene environment. Scene environment currently forces
ordinary fallback for static/skinned instancing candidates; it does not add
scene lighting to incompatible instancing shaders.

## Ownership, recovery and scope

Cubemaps and source models/textures remain caller/lease owned. Keep them alive
through all draws, and dispose the renderer before releasing a cubemap lease.
Renderer-owned sky shader/mesh are lazy. Managed resource recreation must happen
before `GdxRenderer.restoreContextShaderBindings()` on the GL owner thread,
outside batches. Model ShaderProgram alias maps must first be refreshed; the
sky independently queries actual uniform/attribute locations and rebuilds its
owned mesh descriptors. The exact bounded GL20 reserved-slot contract is in
[scene-lighting.md](scene-lighting.md).

The renderer is classic forward lighting, without PBR/IBL, dynamic shadows,
HDR/bloom, volumetrics or automatic material import. SceneJson requires explicit
codecs; these new components are not silently exported by the standard codec.
`--scene-export` remains ModernDemo-only. Asset loading/lease ownership is a
separate [streaming API](asset-streaming.md).

`./gradlew :javity-desktop:environmentSmoke` composes the real GL20/GL30 surface
controls, dedicated sky state/recovery/cleanup gate and integrated courtyard
captures. [Evidence and limits](artifacts/environment/README.md). Desktop
results and APK compilation do not prove these new features on Android:
**Android scene lights/sky/cubemap/day-night and their EGL recovery are
UNVERIFIED** pending a dedicated real device/EGL feature gate.
