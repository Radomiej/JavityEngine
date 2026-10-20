# Optional Fox Garden quality showcase

`org.javity.demo.QualityShowcase` is an interactive desktop integration scene.
It puts original authored garden geometry, two directly imported original glTF
Fox instances, typed scene lights, directional shadows, HDR and existing runtime
tools into one `Game`. It loads only files already in this repository; there is no
HTTP or asset download during launch. The default lookup first uses the checkout
asset directory, then bundled classpath resources at
`org/javity/demo/quality-assets/fox` for installed distributions. An explicit
`--asset-root` is strict and never falls back.

From the repository root:

```sh
./gradlew :javity-desktop:qualityDemo
./gradlew :javity-desktop:qualityDemo --args='--small'
./gradlew :javity-desktop:qualitySmoke
./gradlew :javity-desktop:qualityGallery
```

The normal window is 1280×800; `--small` and `--smoke` use 800×600. Launch requires
an actual desktop GL30 context and a complete RGBA16F/depth16 target. Unsupported
HDR/shadow targets fail explicitly. `--no-shadows` omits the directional shadow
pass while retaining linear PBR/HDR. Other arguments are `--asset-root=<directory>`
(default `tools/animation-validation/fox`) and `--output=<directory>` (default
`build/reports/quality-showcase`). Unknown arguments fail rather than being ignored.

`--gallery` is a separate, hidden-window native GL30 capture mode at exactly
1920×1080. It uses fixed animation poses and cameras and writes afternoon,
last-light, lantern and close Fox compositions without HUD, gizmos or Stage.
`fox-garden-tools.png` deliberately includes the existing runtime tools and HUD.
The default directory is `build/reports/gallery/quality`; override it with
`--output=<directory>`. Gallery mode exits after capture and resource cleanup;
it cannot be combined with `--smoke` and does not replace smoke assertions or
establish performance. The scene's normal PBR, shadow and HDR passes produce the
images directly. No screenshot effects or background replacement are applied.
Retain the Fox credit below in captions when publishing clean images.

| Input | Effect |
| --- | --- |
| 1 / 2 / 3 | Afternoon / last light / lantern hour: sun, ambient, background and exposure presets |
| L | Toggle the amber finite-range point light |
| - / = | Decrease / increase linear HDR exposure |
| C | Switch orbit / free-fly camera |
| Hold right mouse / arrow keys | Look; orbit stays focused on the court |
| W / S; mouse wheel | Orbit zoom; W/S move forward/backward in free-fly |
| A / D; Q / E | Free-fly left/right; down/up |
| Home | Reset the camera to the garden composition |
| Space | Pause / resume the scene's WORLD simulation |
| F1 | Toggle the existing runtime scene tree, inspector and transform gizmos |
| Esc | Exit while scene navigation owns input |

Tools take input ownership while open; close them with F1 or their X button to
resume navigation. Select objects in the scene tree, use the inspector's gizmo
mode/axis controls and Undo/Redo, or select the sun/lantern for the existing light
authoring panel. The inspector's Focus action is enabled in explicit free-fly
mode. The showcase clears held actions and discards the first mouse delta on
input handoff or focus loss. Camera controls use `InputActions` and an explicit
navigation context after the tools/Stage processors.

## What is integrated

Stones, metallic boxes and tree canopies are normal `GameObject`s with
`Transform3D`, `ModelRenderer` and explicit `ShadowPolicy3D`. Their geometry shares
a small set of application-owned PBR models. Grass and shrubs use four
`FoliagePatch3D` objects:192 tufts and6 shrubs are merged into four ordinary patch
draws. They borrow one managed CC0 atlas and explicitly select `PbrFoliage`
materials/provider; no object is created per blade. The renderer owns generated
patch geometry, with stable revisions across camera, light and exposure changes.
Neither patches nor trees demonstrate PBR foliage instancing. Materials use actual
base color, metallic and roughness attributes; the lantern also emits linear
radiance above one. See [foliage contracts](foliage.md).

Two glTF asset leases share the complete original Fox model/texture asset. Each
`GltfInstance` has its own nodes, skin palette and `ModelPoseAnimator`: one plays
`Walk`, the other `Survey`. They remain ordinary scene objects visible to the
inspector. There is no alternate scene graph or animation transport.

The renderer uses `PbrShaderProvider.OutputColorSpace.LINEAR`. Its selected first
`DirectionalLight3D` feeds the optional 1024² shadow pass with a fixed 25×25 world
volume, explicit cast/receive policies and skinned casters. `HdrWorldPass` captures
the resulting world into RGBA16F, applies exposure and fitted ACES, and encodes
sRGB. HUD, gizmos and Scene2D tools draw afterward. Ambient and the finite-range
point light are unshadowed; there are no fog or spot lights in this PBR scene.
See [shadows](shadows.md), [HDR](hdr-rendering.md) and [glTF/PBR](gltf-assets.md)
for their exact support and ownership contracts.

Cleanup closes tools, Stage/theme and HUD resources, removes the scene consumers,
then closes HDR and the renderer, instance handles/leases and authored models.
All cleanup attempts continue after a failure; borrowed model assets survive until
their consumers and renderer have stopped.

## Bounded real-runtime proof

`--smoke` draws six real simulation frames, then uses the same scene and input
multiplexer to exercise a Fox pose change, independent animator clocks, model
movement, the point-light toggle, exposure, free-fly movement and F1 tools/input
handoff. It compares actual framebuffer pixels, requires no GL error, asserts
ordinary PBR rendering and closes owned resources before recording PASS. Failures
produce a report and nonzero launcher exit. No DOM, HTTP or headless substitute is
used. The bounded smoke does not prove sustained frame rate or physical GPU quality.

Output files:

- `verification.json`: GL version, pipeline, numeric changed-pixel counts and cleanup/result.
- `fox-garden.png`: afternoon composition.
- `fox-garden-last-light.png`: warm directional/ambient preset.
- `fox-garden-lantern.png`: finite-range local-light preset.
- `fox-garden-tools.png`: existing runtime tools over the same scene.

The validated path is Linux desktop Mesa software OpenGL. This is a bounded
integration example, not a claim of production/AAA rendering, general streaming,
multiplayer, automatic shadow fitting, bloom, Android/device performance or a web
runtime.

## Asset credit

The garden geometry is authored in this launcher. Fox is the existing original
`Fox.gltf` + `Fox.bin` + `Texture.png`, without conversion or copied assets.
Fox model: PixelMannen (CC0); rig/animation: tomkranis; glTF conversion:
AsoboStudio/scurest (CC BY 4.0). Retain
[NOTICE](../tools/animation-validation/fox/NOTICE.md) and
[SOURCE](../tools/animation-validation/fox/SOURCE.md) when redistributing.
