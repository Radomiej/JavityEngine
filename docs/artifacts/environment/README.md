# Environment evidence — 2026-10-16

Source base: `889ef3b1869b772668f6e627c146ccb683153c24`. The scoped Task 3
commit adds the implementation; [verification.json](verification.json) records
source SHA256, capture hashes, phases and measured native controls. All images
are upright actual libGDX/OpenGL captures, not concept art. The stone courtyard,
materials, gradient and procedural rig are original repository fixtures.

| Capture | Scenario |
| --- | --- |
| [Afternoon](afternoon.png) | phase .57, directional + amber point + blue spot |
| [Last light](last-light.png) | phase .735, warm horizon and low sun |
| [Midnight](midnight.png) | phase 0, low ambient and visible local lights |
| [Mist](mist.png) | held morning phase .43, distance fog hides distant stones |
| [Spot only](spot-only.png) | midnight with point disabled |
| [Point only](point-only.png) | midnight with spot disabled |
| [Authoring](authoring.png) | actual selected Spotlight draft, Apply changed intensity to 3.5, cone/transform gizmos |
| [Voxel FPS](voxel-scene-fps.png) / [TPP](voxel-scene-tpp.png) | explicit new scene environment and preserved separate legacy viewmodel |
| [Voxel tools](voxel-scene-tools.png) | same gameplay scene, native F1 tools |

The courtyard has shared primitive models on ordinary GameObjects. It is an
architectural lighting example, not a foliage system, material importer or
production art pack. Demo controls: 1–4 atmosphere presets, point/spot toggles,
WORLD run/pause, F1 tools and typed light selection. The cubemap note correctly
explains that day/night does not replace cubemap images.

Run `./gradlew :javity-desktop:environmentSmoke`. It includes:

- **25 controls on each actual desktop GL30/GL20 profile:** directional red
  255→0, point near/far 143→16, spot inside/outside 143→0, disabled lights,
  lit and unlit fog, fog-disabled control, unlit near/far red 171→41 and unchanged
  legacy unlit models. Scene candidates use ordinary instancing fallback.
- Real model-program relink with forced `u_sceneFogRange` location **4→210**,
  refreshed host alias maps and the public renderer recovery hook: **0 changed
  pixels** after recovery. The local desktop GL extensions only arrange moved
  locations in the test; production does not require those extensions.
- A **scene-skinned textured** two-bone fixture changes **1,470 pixels** during
  its .6-second clip. An independent alpha-test depth control keeps **3,332
  opaque pixels** and reveals a later green background through **3,332
  transparent holes**. Removing alpha-test preserves transparent-fragment depth
  and blocks those later background pixels. Legacy avatar tests do not stand in
  for this scene-shader proof.
- Real viewmodel and yellow HUD pixels under world fog, WORLD pause/resume,
  borrowed cubemap identity during a cycle, and light/socket drawing without
  changing the canonical bone or base matrix.
- The dedicated `skySmoke` GL20/GL30 gate: gradient/cubemap faces, no parallax,
  camera rotation, depth and state sentinels, moved locations, managed mesh
  recreation, failed compile/link cleanup and repeated sky disposal. Its exact
  state/lifetime contract is documented in [scene-lighting.md](../../scene-lighting.md).
- `environmentDemoSmoke`: the captures above, actual typed field Apply,
  light-panel keyboard focus release after hiding, and repeated demo-owner
  disposal. The owner invokes legacy GdxRenderer.dispose exactly once.

The full desktop reactor and required voxel/inspector/avatar GL20+GL30/feel3d/
postprocess GL20+GL30 regressions passed. Final accumulated JUnit reports contain
**805 tests in 91 suites, zero failures/errors** (the full run plus the final
focused editor/cycle input fix). Android debug APK compilation passed; new
lighting/sky/cubemap and real EGL feature recovery remain **UNVERIFIED**.
Desktop managed recreation is not actual context destruction.

The voxel frames were copied into this environment directory from the Task 3
`voxelSmoke` run. All 36 modified generated historical paths were explicitly
restored to HEAD afterward, including the old `voxel-fps/task2-*` frames/metrics,
old inspector/tools captures and Feel preview counts. Their earlier provenance
remains intact. Controller-owned `docs/artifacts/sky/` evidence is separate.

This is forward lighting without PBR/IBL, dynamic shadows, HDR/bloom or
volumetrics. Scene fog does not cover arbitrary shaders, Shapes, MapLibre or UI.
Viewmodel lighting deliberately differs from the scene. Scene-mode instancing
uses the documented ordinary fallback. Full API: [lighting-and-sky.md](../../lighting-and-sky.md).
