# Static foliage patches

`FoliagePatch3D` adds deterministic grass or shrubs to an ordinary scene without
per-blade objects. Add a `Transform3D` and one patch; `GdxRenderer` creates merged
meshes lazily on its GL thread and renders them with the selected scene lighting.

```java
Texture texture = new Texture(Gdx.files.classpath("foliage/leaves-and-grass.png"), true);
texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
FoliageAtlas atlas = FoliageAtlas.bundled(texture);
GameObject bank = game.createObject("Meadow bank");
bank.addComponent(new Transform3D()).position.set(-4, 0, 0);
bank.addComponent(FoliagePatch3D.grass(atlas).area(5, 6).count(240).seed(42));

GameObject shrubs = game.createObject("Shrubs");
shrubs.addComponent(new Transform3D()).position.set(4, 0, -3);
shrubs.addComponent(FoliagePatch3D.bushes(atlas).area(4, 3).count(7).seed(43));
```

Area is a centered local XZ rectangle with ground at Y=0. Count means tufts or
shrubs, capped at 4096; zero produces an empty patch. Grass defaults to 256 tufts
in 8 × 8 units, seed 1, scale .8–1.2 and cutoff .5. Bushes default to 12 shrubs
and scale .85–1.15. `area`, `count`, `seed`, `scaleRange`, `cutoff` and `material`
validate before changing state. Equal settings preserve the revision; changed
settings trigger one transactional rebuild. Transform edits do not rebuild.
Large geometry splits on the unsigned-short vertex range.

The stock material uses the diffuse RGBA atlas, alpha cutoff, disabled blending,
depth writes and two-sided rasterization. Soft tilted normals keep stylized leaves
readable from both sides; this is not a physical two-sided leaf shader. Ordinary
scene ambient, directional, point and fog affect patches. Wind, LOD, terrain
sampling, collision, streaming and forest instancing are separate features.

## Materials and resource ownership

The patch copies a supplied material template. Textures remain borrowed. The
renderer applies the patch cutoff and explicit cutout/depth/cull settings to its
copy. A PBR scene requires explicit PBR attributes and provider selection:

```java
// Optional javity-gltf dependency; renderer must use this compatible provider.
FoliagePatch3D patch = FoliagePatch3D.grass(atlas).cutoff(.5f)
    .material(PbrFoliage.material(atlas, .5f), PbrShaderProvider.class);
```

`PbrFoliage` uses `PBRTextureAttribute.BaseColorTexture`, white base color,
metallic 0 and roughness .93. It never silently converts ordinary materials. The
provider requirement rejects an incompatible renderer before shader allocation.
PBR range-light semantics and optional shadows remain those of the explicit PBR
provider. The stock demo proves the ordinary lighting/fog path; the PBR helper
has a CPU attribute/defensive-copy regression and separate showcase integration.

Keep the texture or its READY asset lease alive until the patch is removed and
the renderer has reconciled that scene. Close generated renderer resources before
closing source textures. Disabling/removing patches, destroyed owners and switching
to a scene without a camera retire cached geometry. Replacement builds retain the
previous valid model on failure. `foliageStats()` reports cache geometry bytes,
view submissions/visibility/draws and lifetime builds/failures;
`foliageMeshes()` exposes borrowed diagnostics only. Geometry bytes are the raw
vertex/index payload estimate, not total CPU/GPU/driver allocation. Failed first
builds remain empty and report failures; retries back off to at most60 frames.
Without a world pass, camera culling skips collection entirely. With a world pass,
culled patches remain in a separate pass-only descriptor list so their shadows
can reach visible ground; those descriptors never enter the color draw list.


Trees use ordinary shared `Model` resources with `ModelRenderer` and optionally
`InstancedModel`. The demo creates an original branching tree with tapered trunk,
forked branches and many differently tilted textured twig cards. Its three trees
share two mesh parts. Scene lighting currently selects the ordinary fallback for
these six candidates, preserving the same scene environment. Unique generated
patches use ordinary draws and never claim an instance batch.

## Atlas and demo

The bundled 1024 × 1024 RGBA atlas has grass in the left column and leaves in the
right, four variants from top to bottom. `FoliageAtlas.bundled` uses two actual
texture pixels of inset. Original curved blades have dark bases/light tips;
pointed twig leaves leave irregular holes. Each cell has clear margins and four
pixels of RGB bleed beyond alpha coverage. The original atlas and generated tree
geometry are CC0; see [the notice](../javity-core/src/main/resources/foliage/NOTICE.md).
Rebuild with Python 3 and Pillow using `python3 tools/generate-foliage-atlas.py`.
No downloaded asset or image-generation service is needed.

```sh
./gradlew :javity-desktop:foliage
./gradlew :javity-desktop:foliageSmoke
./gradlew :javity-desktop:foliageGl20Smoke
./gradlew :javity-desktop:foliageGallery
```

Interactive keys: 1 wide daylight, 2 close view, 3 dusk/fog. `FoliageDemo.main`
also accepts `--smoke`, `--gl20`, `--output=<directory>` or `--output <directory>`.
Its default capture directory is `build/reports/foliage`. Smoke runs pixel controls,
then captures fixed wide, close and dusk frames. Curated evidence is retained in
[GL30 captures](artifacts/foliage/gl30/day-wide.png) and
[GL20 captures](artifacts/foliage/gl20/day-wide.png).

`--gallery` runs the same deterministic woodland in a hidden 1920×1080 native GL
window and writes clean `day-wide.png`, `day-close.png` and `dusk-wide.png` frames.
HUD is omitted; the world renderer, lighting and fog are unchanged. A lower fixed
wide camera emphasizes the path and crowns. It defaults to
`build/reports/gallery/foliage`, accepts the same `--output`
override, and exits after capture and resource cleanup. `--gallery` and `--smoke`
are mutually exclusive: gallery capture does not replace the pixel controls in
smoke mode or establish performance. Preserve the original CC0 atlas/tree credit
in published captions.

## Verified scope — 2026-10-20

Real Mesa/Xvfb GL30 and GL20 passed the same framebuffer controls. A known RGBA
ring atlas on generated patch geometry leaves 15,304 red background pixels and
1,080 opaque green pixels in a 128 × 128 frame. The red sentinel is drawn *after*
foliage at a greater depth, so surviving green also proves opaque depth writes.
A nearer sentinel occludes foliage. Back views remain visible; zero ambient,
directional light, point light and full blue fog change real foliage pixels.

Repeated frames preserve pixels and build count. Transform-only movement removes
old pixels without rebuilding; changing seed rebuilds exactly once. Removal,
re-addition and transition to a scene with no camera release generated buffers,
checked with native `glIsBuffer`; the borrowed atlas remains a live texture.
GL20 also records exactly one delete event per retired buffer. The full woodland
contains 12 patch meshes, 19,296 vertices, 28,944 indices and 675,360 geometry bytes;
wide view submits 12 ordinary foliage draws. Close-view culling reduces visible draws to nine.

Software GL proves correctness, not physical GPU performance. This slice does
not establish Android/Wasm foliage runtime or real EGL loss/recreation. Managed
Mesh/Texture ownership uses the existing recovery infrastructure; actual context
loss remains a separate platform gate. No cast-shadow claim is made for the stock
demo.
