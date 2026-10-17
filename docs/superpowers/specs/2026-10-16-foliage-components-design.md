# Roślinność — prosty projekt komponentów

2026-10-16. Projekt do przeglądu, nie opis wdrożonych klas. Użytkownik wymaga gotowych komponentów w stylu Java Lab: dodanie przez kod, rozsądne wartości domyślne i automatyczny rendering. Cztery wcześniej zaakceptowane etapy FPS/narzędzi/streamingu/otoczenia są wdrażane niezależnie.

## Three approaches

| Approach | Good at | Cost / counterexample |
|---|---|---|
| One GameObject + ModelRenderer + InstancedModel per tuft/tree | Already possible for individually interactive trees; shared meshes can instance | Thousands of grass objects/animator containers and expensive ordinary fallback. A new scene lighting attribute can turn 2,000 tuft instances into 2,000 draws. Not the grass default. |
| **Recommended first release: merged small grass/bush patches + ordinary borrowed tree models** | Few ready components, predictable ordinary lighting/fog, bounded draw count, no per-blade objects or novel shader required | Unique seeded patch meshes cannot share an instance group. CPU/GPU geometry is duplicated between different patches; bound patch size and report memory. Repeated shared prebuilt patch meshes can use existing instancing separately. |
| Array-of-placement scatter using shared models and a custom foliage instance shader | Large meadows/forests, efficient per-tuft transforms and future wind | Requires a new renderer producer/ID lifecycle and a scene-lighting-compatible instance shader, ordinary wind fallback, extra culling bounds and more platform proof. Too much machinery for the first easy foliage feature. |

Recommendation: ship the second approach first, with attractive authored assets and demonstrated integration with the approved environment. Treat wind and large scatter as explicit follow-on work. GPU instancing is a useful optimization for compatible shared geometry, not a prerequisite for a nice bounded foliage scene.

## Concrete proposed authoring API

Keep one primary new component, `FoliagePatch3D extends Component`, in `org.javity.render.foliage`. Two named factories provide discoverable presets:

```java
// Existing SceneAssets queue/update/get lifecycle loads a bundled foliage RGBA atlas.
Texture texture = assets.get("foliage/leaves-and-grass.png", Texture.class);
FoliageAtlas atlas = FoliageAtlas.bundled(texture);
GameObject meadow = game.createObject("Meadow");
meadow.addComponent(new Transform3D());
FoliagePatch3D grass = meadow.addComponent(
    FoliagePatch3D.grass(atlas).area(8, 8).count(256).seed(42));

GameObject shrubs = game.createObject("Shrubs");
shrubs.addComponent(new Transform3D());
shrubs.addComponent(FoliagePatch3D.bushes(atlas).area(8, 8).count(12).seed(43));

// Existing API is sufficient for a single tree; no fake TreeRenderer wrapper needed.
GameObject oak = game.createObject("Oak");
oak.addComponent(new Transform3D());
oak.addComponent(new ModelRenderer(borrowedTreeModel));
oak.addComponent(new InstancedModel());
```

Use a small immutable `FoliageAtlas` descriptor: its constructor copies normalized grass/leaf regions and borrows the texture. `FoliageAtlas.bundled(Texture)` supplies the fixed region layout of the shipped atlas so callers never calculate UVs. Do not add a generic material graph, asset registry, reflection-based component factory, or ecosystem framework.

Suggested complete v1 configurable surface:

- `grass(FoliageAtlas)` / `bushes(FoliageAtlas)`; fluent `area(width, depth)`, `count(int)`, `seed(long)`, `scaleRange(min, max)`, `cutoff(float)`; getters for tooling. CPU-only construction and setters. `area` is a centered local XZ rectangle, ground Y=0, units match Transform3D. No hidden terrain lookup/raycast.
- Grass defaults: area 8×8, count 256 **tufts**, seed 1, uniform scale .8–1.2, authored height approximately .35–.65 world units, cutoff .5. A tuft contains several blades/cards; count never means GameObjects.
- Bush defaults: area 8×8, count 12 shrubs, seed 1, scale .85–1.15, approximately .7–1.3 units high, cutoff .5. Leaf clusters have different plane orientations and real depth; avoid a lone flat billboard per bush.
- Deterministic jittered placement, yaw and atlas variant from seed; upward Y preserved. Count cap 4096 per component, checked before allocation. Split mesh chunks on **vertex-index range**, not number of indices, to honor unsigned-short index limits. Size/count/scale/cutoff validate finite values; zero count is a legitimate empty patch, negative counts rejected.
- `setPlacements(List<FoliagePlacement>)` is optional only if the first demo needs authored paths/slopes. Each copied placement holds local x/y/z, yaw and positive uniform scale. Switching back to seeded scatter is explicit. Avoid adding terrain/voxel dependencies just for placement.
- Existing `setEnabled`, GameObject active state, removal and Transform3D are the lifetime/movement interface. Geometry changes increment a revision and rebuild once at a safe renderer boundary; transform-only edits do not rebuild geometry. Do not expose per-frame mutable lists or regenerated random scatter.

Trees should use a supplied attractive, ready model and standard ModelRenderer. Trees needing collision or chopping are individual GameObjects with the separate existing physics/gameplay components. Decorative grass/bush patches have no automatic colliders. A later explicit forest scatter component is justified only by a concrete scene needing many noninteractive trees.

## Rendering and ownership contract

- The component contains copied CPU settings/placements and borrows atlas resources. A small per-component cache owned by GdxRenderer builds the merged patch mesh/model lazily on the GL owner thread, outside active batches. Each cache entry owns only its generated meshes/material copies; the atlas remains borrowed. This is per-patch owned geometry, not a mesh per blade or GL work in a constructor.
- GdxRenderer collects initialized, enabled foliage components with active Transform3D into its world pass. Submit the generated renderables through the same ordinary shader provider and **the actual selected scene Environment**. This automatically benefits from the approved ordinary scene lighting/fog when that implementation lands; verify pixels, not assumptions about flags.
- Shared stock foliage material: diffuse RGBA atlas, opaque output with cutout, blending disabled through the explicit BlendingAttribute flag, depth writes on, cull NONE for cards. Trunk/branch material can keep back-face culling. Copy material configuration rather than mutating a caller's Model.
- Current stock two-sided rasterization does not establish physically correct two-sided leaf lighting. Author soft upward/tilted normals and a readable ambient level for this stylized asset set; verify back/front views. A later leaf-lighting shader is a separate choice.
- Cache local geometry bounds once; transform to conservative world AABB each view and frustum-cull the whole patch **before any ordinary or instanced submission**. Tall bushes and tree crowns must be included. Small patches limit unnecessary geometry near the frustum boundary. No distance fade/alpha blending, terrain streaming or LOD machinery in v1.
- Unique generated patches use ordinary draws, usually one draw per material/chunk. Do not insert InstancedModel and claim it combines unique meshes. Shared prebuilt patch Models and shared tree Models can use existing ModelRenderer+InstancedModel; capability/environment rejection must preserve the ordinary image and expose the existing fallback reason.
- Replacement is transactional: build a complete new geometry entry, then swap outside batches; retain old valid geometry if rebuild fails. Remove/disable/re-enable must never draw stale entries; removal and scene swap release generated resources. Two cameras in one frame must not duplicate rebuilds.
- Borrowed atlas/model lifetime must cover every render using it. With SceneAssets, retain until scene renderer/cache cleanup. With AssetStreams, the owner keeps a READY lease while the component is renderable; remove/detach, finish the active render, invalidate/retire generated cache references, then close the lease. Foliage core need not depend on javity-assets or hide async loading.
- Scene close order must free renderer-generated mesh caches before source assets/leases; invalidation must work even if the new scene has no camera. Context recovery uses managed Mesh/Texture plus existing renderer shader recovery; independently validate new cached resources after actual EGL restoration before an Android claim.

## Wind: explicit second slice

Default v1 is **static cutout foliage**, not a nonfunctional wind toggle. Static foliage with good silhouettes, density, color and environment should already be visually worthwhile.

If wind is requested in the next slice, add an explicit `WindSettings` value on FoliagePatch3D (not a global shader hack). The narrow first wind implementation can keep merged patch draws and extend the ordinary recoverable scene shader variant. It needs known per-vertex root/bend weights (new layout/attribute or a specifically documented encoding), phase from world root position, WORLD-clock time updated once per simulation step, copied direction/amplitude/speed, preserved roots, and conservative maximum displacement bounds. Render calls never advance wind time. Trees remain rigid unless their imported asset carries a documented wind mask.

Custom wind materials/layouts must **not** be accepted by existing InstanceGroupKey without a matching instance shader. GL20 ordinary wind support requires a real variant and proof; if absent, the chosen policy is an explicit static fallback with diagnostics, not silently dropping fog or pretending wind rendered. Extending instanced wind later must share deformation/lighting formulas with the ordinary path and prove parity. Setting amplitude=0 must recover static geometry/pixels within a stated tolerance.

## Beauty is an asset and scene deliverable

Bundle an original/authored or clearly licensed local atlas and tree model. Keep source/license/author/version notices in THIRD_PARTY.md and an asset notice. Kenney/other CC0 packs are candidates only after inspecting the actual selected files and license; no assets were downloaded or vetted in this audit. Use formats/loaders actually supported by this repo, such as g3dj/g3db, rather than assuming a glTF pipeline.

Provide grass clusters with several distinct silhouettes and tip/base colors, irregular shrub masses with gaps, and at least one convincing branching tree with a textured leafy crown. Avoid uniform green rectangles, spheres-on-sticks, repeated identical orientation, and alpha fringes. Use alpha-aware/padded atlas edges and check mip/minification behavior against the .5 cutoff. Variation should be authored into texture regions/geometry; current instancing does not supply arbitrary per-instance tint.

A small composed demo should show an open grass path, dense meadow edge, shrubs beside a tree, close and distant views, warm daylight and dusk/fog using the approved environment. Authored ground color helps root contact; do not advertise cast shadows without a shadow implementation. Show ordinary merged patch cost separately from shared-tree instancing stats. The demo should be attractive without an editor or per-blade setup.

## Small ordered tasks and proof criteria

1. **Approve a bounded static visual target and API.** Choose authored asset style, primary FoliagePatch3D presets, defaults/caps and loaded-asset example. Check selected license and actual source assets. Document that wind/forest streaming are later work.
2. **Build CPU patch generation + material helper.** Prove same seed/settings gives identical placement, count caps and unsigned-short splitting, bounds include full plants, setters validate before state mutation, and borrowed textures never become generated-model owned disposables. One component owns many tufts; no hidden GameObjects.
3. **Integrate generated geometry into the existing world pass.** Verify revision-only rebuild, transform-only movement, patch culling on both supported and fallback environments, transactional rebuild failure, remove/re-add and scene swap/no-camera cleanup. Keep FPS viewmodel and UI pass unchanged.
4. **Native cutout/environment proof.** Holes reveal known background; opaque leaf pixels write depth and occlude a sentinel behind them; front/back cards remain visible; foreground geometry occludes foliage. Compare actual approved ambient/sun/point/spot/fog scene outputs. Include GL20 fallback, static repeated-tree instancing, and scene-lighting rejection reason. Do not force a single-draw claim for unique generated patches.
5. **Ship a pleasant demo and concise guide.** Fixed-seed wide/close/day/dusk screenshots, count/draw/memory/rebuild stats, removal/scene-transition resources, old world/viewmodel/HUD regression smoke; full reactor and appropriate native checks when implementation is complete. Android build and real EGL loss are separate gates. Wind adds its own phase/pause/root/bounds/ordinary-vs-instance proofs only if implemented.

### Counterexamples worth putting directly into the eventual spec

- AlphaTest without the nonblending BlendingAttribute: solid rectangular cards.
- Two-sided cards with depthMask=false: broken overlap and rejected instancing.
- Shader/SceneLightingAttribute stripped to force instancing: grass ignores scene fog/point/spot or uses different lighting from its trees.
- InstancedModel on a distinct generated mesh for every patch: no shared batch, misleading draw-count promises.
- A grass-sized AABB reused for shrubs/wind: plants disappear while their tops are still visible.
- Rebuilding/reseeding every frame or view: shimmering locations, duplicate allocation and time-dependent multi-camera output.
- Borrowed atlas passed to manageDisposable or lease closed before render removal: another patch's texture becomes invalid.
- Removing a component before it was initialized, or swapping to a scene without a camera: generated cache must still be cleaned.
- Attractive static screenshot but no close/backlit/backface view: hides flat cards, alpha halos and dark leaf backs.
