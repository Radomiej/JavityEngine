# Avatar views and prepared geometry

The avatar API separates pose ownership from per-view visibility. `ModelRenderer` keeps one canonical
`ModelInstance`, animation controller, material set, and bone palette. An `AvatarDefinition` resolves
node paths and `NodePart` ordinals once; each `AvatarView` filters draws and owns its own preset,
slot toggles, and base transform. Gathering a view never advances animation or changes source part
enabled flags.

## Visibility and sockets

```java
PartKey head = new PartKey("humanoid/head", 0);
PartKey torso = new PartKey("humanoid/torso", 0);
PartKey leftArm = new PartKey("humanoid/left-arm", 0);
PartKey rightArm = new PartKey("humanoid/right-arm", 0);

AvatarDefinition definition = AvatarDefinition.builder()
    .part(AvatarPart.HEAD, head)
    .part(AvatarPart.TORSO, torso)
    .part(AvatarPart.LEFT_ARM, leftArm)
    .part(AvatarPart.RIGHT_ARM, rightArm)
    .slot("shirt", torso)
    .socket("left-hand", "humanoid/left-arm/hand", new Matrix4())
    .build();

AvatarBinding binding = definition.bind(modelRenderer);
AvatarView worldView = binding.view();
AvatarView localView = binding.view().preset(AvatarPreset.ARMS_ONLY);
localView.slotVisible("shirt", false);
localView.setBaseTransform(viewmodelBase);

renderer.renderWorld(game, worldCamera, worldView);
renderer.renderWorld(game, firstPersonCamera, localView);
```

`FULL_BODY`, `BODY_WITHOUT_HEAD`, and `ARMS_ONLY` select configured body semantics. Custom
`parts(Set<AvatarPart>)` may select none. Unclassified ordinary geometry appears in complete-body
presets and is hidden in `ARMS_ONLY`. A named accessory slot defaults on in full-body presets and
off in `ARMS_ONLY`; `slotVisible` overrides that default. A slot override and a semantic selection
both apply, and neither can show a source `NodePart` whose canonical `enabled` flag is false.
Clothing spanning regions that must hide independently needs separate authored draws.

Sockets remain attached to the canonical animated node when their geometry is hidden. Their world
matrix is `base × node.globalTransform × localOffset`; it is not a skinning matrix. For a socket
attached to a `GdxRenderer` object, pass `objectTransform.worldMatrix() × view.baseTransform()` as
the base.

## Authored skinned mesh regions

For one indexed weighted `GL_TRIANGLES` `NodePart`, prepare a sidecar before rendering begins. Triangle
ordinals are relative to that selected `MeshPart` range. Every triangle must occur exactly once across
the explicit regions.

```java
PartKey skin = new PartKey("humanoid", 0);
PreparedAvatarGeometry geometry = AvatarMeshPreparator.prepare(
    sourceModel,
    skin,
    Map.of(
        "body", new int[] {0, 1, 2, 3},
        "left-arm", new int[] {4, 5, 6, 7}));

AvatarDefinition definition = AvatarDefinition.builder()
    .region(AvatarPart.TORSO, "body")
    .region(AvatarPart.LEFT_ARM, "left-arm")
    .build();
AvatarBinding binding = definition.bind(modelRenderer, geometry);
AvatarView full = binding.view();
AvatarView arms = binding.view().preset(AvatarPreset.ARMS_ONLY);
```

Preparation validates coverage, source ranges, position and weight layout, bone bind-map ordering,
and referenced unsigned-short vertex indices before allocating the sidecar. It copies the complete
source vertex stream once in its existing order and preserves every vertex descriptor and raw float
payload. Region index ranges regroup original triangle triples without changing their winding or
palette indices. The source `Model`, mesh, materials, texture, nodes, bind maps, and animation remain
borrowed and canonical. No skeleton or `ModelInstance` is cloned by the preparator.

The first implementation accepts only one indexed weighted `GL_TRIANGLES` part with float `vec3`
position and contiguous float `vec2` `BoneWeight` attributes. It rejects nonindexed geometry,
strips/fans, instancing, malformed bind maps, incomplete/overlapping regions, invalid weights,
nonfinite referenced positions, and unsupported vertex layouts. Automatic bone-based region
classification is unsupported; author triangle regions explicitly. Partitioning can reorder triangles
between regions, so use opaque skin materials; arbitrary transparent draw-order parity is not
claimed. Shader capacity still applies to the canonical part and must be configured for the rig.

`PreparedAvatarGeometry` owns only its generated mesh. Create, acquire, bind-close, and owner-close it
on the same GL thread. The source model and its texture resources must outlive the geometry and all
bindings. Closing the owner blocks new bindings and defers generated buffer deletion while bindings
remain. Close the final binding after `ModelBatch.end()`; closing it releases the generated buffers.
Closing a renderer also closes its binding. Both close operations are idempotent. Presets and views
do not allocate meshes.

The native regression can be run with:

```sh
./gradlew :javity-desktop:avatarSmoke
./gradlew :javity-desktop:avatarGl30Smoke
```

It uses an original procedural fixture with a textured two-bone rig, nonidentity bind transforms,
two influences, and authored body/arm regions. It compares source and partitioned full-body pixels
at multiple poses, probes hidden-region color and depth, injects a partial buffer-upload failure,
checks shared leases and generated-buffer deletion, and renders from the borrowed source after the
sidecar closes. `avatarSmoke` explicitly requests GL20; `avatarGl30Smoke` requests a GL30 context and
requires a tracked candidate VAO for success and each upload failure path. Each run records its
requested profile, actual GL profile/version, VAO counts, and framebuffer artifacts under the
matching `docs/artifacts/avatar/gl20/` or `gl30/` directory. The fixture contains no Mojang assets.

## FPS/TPP voxel demo (Task 4C)

`--voxels` uses an original procedural block character. Its indexed head, torso, arms and legs form
one weighted source `NodePart`; authored triangle regions are prepared once by
`AvatarMeshPreparator` and rendered through the same canonical `ModelPoseAnimator` instance. The
idle/walk/run mixer is driven by measured player speed. A right-arm masked use clip and leg jump
clip share that animator and world clock. A rigid shirt and held tool are ordinary rigged `NodePart`s
with independent `shirt`/`tool` slots and a `right-hand` socket. `C` and `T` toggle the slots.
`BODY_WITHOUT_HEAD` and `ARMS_ONLY` change draw selection only; they do not alter the skeleton,
socket, or animation. The original pixel atlas is generated in code with no Mojang or third-party
character assets.

F5 toggles the first-person player view and third-person full body on a rising edge. F remains the
separate debug-flight control. The visual avatar has its own yaw-only transform; `VoxelFpsController`
remains the sole movement authority. Interaction casts continue from the player eye and look
direction in either mode. In first person the full body is hidden and hands/tool use a camera-local
base in a separate depth-cleared 3D pass. World outline drawing precedes that pass, while 2D/HUD
content follows it. An observer camera renders the full avatar in the same frame without sampling or
advancing another pose clock.

`CameraRig3D` is renderer-side and accepts a typed obstruction query, not a voxel dependency. Its
radius encloses the current near-plane corners, including near depth, and is recomputed on FOV or
viewport changes. The voxel adapter continuously sweeps the camera segment against radius-expanded
solid unit AABBs; this is a conservative sphere-volume approximation, so it may stop early near a
corner. Unloaded and out-of-range terrain blocks the sweep. The query intentionally considers only
terrain, so the ignored owner cannot be mistaken for a terrain obstacle. A start-overlap result is
zero and keeps the camera at the safe pivot fallback; the rig does not enforce a minimum boom through
an obstacle. A copied rigid presentation offset is limited to 64 world units and its translated-eye
segment is swept before the boom segment (also in FPS), so presentation translation cannot bypass
clearance. The exposed clearance is the minimum returned fraction across these segments.

The FPS view selects the right arm and the `tool` slot. Its authored wooden shaft and stepped cyan
pickaxe head share one owned mesh attached to the existing right-hand bone/socket. The fixed
camera-local `VoxelViewmodelPresentation` base leaves the gameplay transform and TPP composition
independent. Walking/sprinting already drive a gentle arm bob through the canonical locomotion mixer.

LMB removes one raycast block and starts the masked swing on its rising edge; E starts only the swing.
Holding either input does not restart the playback. The first uncaptured click captures the cursor.
Scene2D receives UI events first; keyboard focus, WORLD pause/freeze and native window focus gate tool
use. Key/button releases clear the held guards even if a widget consumes the release. Native focus
loss releases cursor capture, clears the guards and cancels the owned recoil.

The demo explicitly attaches `PresentationEffects3D` and `CameraPresentation3D(VIEWMODEL)` to its
camera owner. A new use replaces its one finite recoil handle, sampled once by the ordinary WORLD
component update. `CameraRig3D.update(..., null, toolCameraPresentation)` composes it only into the
viewmodel camera. F5 changes view selection while preserving the same pose, clock and socket;
WORLD pause freezes both animation and recoil. Closing the demo cancels its playback before the
scene and GPU resources close. No secondary animator or scheduler is involved.

Current real Xorg/Mesa captures and matched ROI metrics live under
[`docs/artifacts/voxel-fps`](artifacts/voxel-fps/README.md). `task2-proof.json` covers separate hand,
shaft and head masks during action start/mid/end, wall, tool hiding, walking, sprint, jump, F5 during
use and actual 960×720 native resize. Tool-hidden and no-viewmodel controls isolate geometry from
similarly colored terrain. `task2-scene-proof.json` retains the camera-volume, seam-edit and controller
checks; historical Task 1 captures remain unchanged. Run
`./gradlew :javity-desktop:voxelSmoke :javity-desktop:avatarSmoke :javity-desktop:avatarGl30Smoke`.
CPU rig and voxel sweep coverage runs with
`./gradlew :javity-core:test --tests org.javity.render.CameraRig3DTest :javity-voxel:test --tests org.javity.voxel.controller.VoxelCameraObstructionTest`.

This is procedural-rig integration evidence. It does not claim imported production-avatar asset
compatibility, weighted region auto-classification, arbitrary transparent triangle reordering, or a
general physics-body obstruction adapter.

## Imported glTF authoring

The optional `javity-gltf` module provides `ImportedAvatarProfile.inspect(instance.modelInstance())`.
Its immutable CPU catalog lists the **actual imported** full node paths, `PartKey` ordinals,
material IDs, canonical bone palette paths, and clip IDs/durations/channel target paths. Inspect the
catalog before writing a profile; mesh IDs and material names are not assumed to identify anatomy.
Missing selectors, socket paths and clip IDs fail during authoring with available values.

```java
var catalog = ImportedAvatarProfile.inspect(instance.modelInstance());
var profile = catalog.profile()
    .part(AvatarPart.RIGHT_ARM, new PartKey("Avatar/RightArm", 0))
    .slot("tool", new PartKey("Avatar/Hips/Spine/RightArmBone/RightHand/Tool", 0))
    .socket("grip", "Avatar/Hips/Spine/RightArmBone/RightHand", new Matrix4())
    .clip("use", "tool-salute")
    .build();
var renderer = object.addComponent(instance.createRenderer());
var animator = object.addComponent(new ModelPoseAnimator());
var binding = profile.definition().bind(renderer);
var hands = binding.view().preset(AvatarPreset.ARMS_ONLY).slotVisible("tool", true);
// After game.start():
animator.play(profile.clip("use"), PlayMode.LOOP);
```

Tag every intended body region explicitly; the example above shows only one arm for brevity.
This helper adds no pose owner or animation controller. `ModelPoseAnimator`, `AvatarView` and
`CameraRig3D` retain their existing responsibilities. Views hide draws while preserving the full
canonical skeleton, palette arrays, animations and child sockets. An ordinary rigid tool can remain
attached below an animated hand even when its ancestor's geometry is hidden. Meshes authored as one
skinned part still require the explicit `PreparedAvatarGeometry` triangle-region workflow above;
there is no automatic humanoid recognition, skin-weight classifier, IK or retargeting.

The catalog and profile own no resources. Keep the `GltfInstance` handle alive until its renderer
has been detached (or its scene closed). Closing a typed `GltfAssets` lease can mark the source asset
closing, but existing instance handles keep the shared meshes/textures alive; the final handle close
releases them. `AvatarBinding.close()` only releases its own binding, not the imported instance.

`./gradlew :javity-desktop:importedAvatar` shows an original imported CC0 block humanoid next to
an independent arms/tool instance; Space switches to the camera-local FPS pass and T toggles its
tool. The fixture is generated by `javity-gltf/tools/generate-avatar-fixture.py`: eight joints,
seven skinned body/visor parts, one rigid hand tool and the authored `tool-salute` clip. It is a small
explicit authoring fixture, not evidence of arbitrary production humanoid compatibility.

`importedAvatarSmoke` and `importedAvatarGl20Smoke` import that GLB through the real loader and draw
with the ordinary PBR renderer. They prove exact catalog values, independent animation times and
palette arrays, canonical palette identity in selected renderables, arms/tool versus full-body pixel
differences, camera-local viewmodel pixels, stable hidden-body sockets/poses, deferred lease disposal,
a surviving instance redraw after its sibling closes, and zero remaining managed meshes. PNGs are
written to `build/imported-avatar/`. Xvfb/Mesa GL30 and GL20 runs passed; these checks do not establish
physical GPU performance or Android/Wasm imported-avatar runtime support.
