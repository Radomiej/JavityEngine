# Independent imported-avatar review

Verdict: **approve current source**, after two findings were corrected during review. No remaining actionable defects found in the reviewed scope. Root retains the full-reactor/integrated-build gate.

Reviewed against base `9f840ad` and `AGENTS.md` / `docs/Spec/2026-10-20-foliage-avatar.md`:

- `javity-gltf/.../ImportedAvatarProfile.java` and its five CPU tests.
- Original `avatar.glb`, generator, adjacent LICENSE, THIRD_PARTY provenance.
- Standalone `ImportedAvatarDemo.java`, Gradle task names, imported-avatar additions in `docs/avatars.md`.
- Existing `AvatarDefinition`, `AvatarBinding`, `AvatarView`, `ModelRenderer`, `GltfAsset`, `GltfInstance`, `GltfAssets`, `CameraRig3D`, and renderer seams to establish ownership/pose behavior.

## Findings resolved

1. `docs/avatars.md` named nonexistent `:javity-desktop:importedAvatarDemo`; corrected to registered task `:javity-desktop:importedAvatar`.
2. `ImportedAvatarDemo.capture()` skipped `Pixmap.dispose()` on PNG-write failure and `framebuffer.end()` on draw/readback failure. Corrected with nested finally blocks. The worker added a real native directory-target PNG-write failure regression that checks framebuffer restoration and then continues successful normal rendering and complete cleanup. Reviewed final implementation and post-fix native logs.

## Contract assessment

The helper snapshots exact imported node paths, NodePart ordinals, material IDs, ordered palette node paths, and original clip IDs/targets. It performs explicit semantic/slot/socket/role selection and delegates binding to the existing avatar API. It neither classifies anatomy nor creates a second pose/animation owner. Duplicate or unrepresentable IDs and detached animation/palette targets fail before authoring. All public lists/maps produced by inspection/profile are immutable snapshots.

The demo uses one shared imported GPU model and two independent canonical ModelInstances. It checks distinct bone arrays and clip target nodes, different sampled socket poses, selected-renderable palette identity, rigid tool transform at the canonical animated hand, and unchanged pose/socket/clock after visibility changes. Actual CameraRig3D third-person world and first-person viewmodel paths are used. Typed lease release marks the asset closing while instance handles pin it; detaching/closing one instance leaves the other drawable. Cleanup closes the scene before handles, then renderer and framebuffer through an exception-continuing ResourceScope. Binding invalidation follows ModelRenderer release.

The small generated GLB is genuine glTF skin/animation data: eight joints, seven explicitly authored skinned parts, one rigid child tool, one linear quaternion clip. Its provenance and limits are stated accurately; no arbitrary production-avatar, retargeting, Android/Wasm, or GPU-speed claims are made.

## Independent verification

- Compiled current helper and tests with isolated JDK25 javac output `/tmp/avatar-review-classes`, using `/tmp/javity-quality-desktop-classpath.txt` plus pinned cached JUnit/Hamcrest. No Gradle invocation or repository edits by reviewer.
- JUnit from module cwd `javity-gltf`: `OK (5 tests)`. The module cwd matters because the fixture preflight test uses its module-relative resources path.
- Regenerated fixture into `/tmp/avatar-review-regenerated.glb` by executing the generator with only its output path redirected: byte-for-byte equal to checked-in `avatar.glb`, 17,064 bytes.
- Inspected actual `arms-and-tool.png` and `first-person.png`: upright complete body beside separately posed arms/tool, and visible camera-local arms/tool.
- Reviewed worker post-fix `/tmp/imported-avatar-native.log` and `/tmp/imported-avatar-gl20.log` plus `/tmp/sol-imported-avatar-report.md`: GL4.5-core / forced GL2.1 both PASS, including write-failure recovery, surviving-instance redraw, and mesh ownership returning to baseline. Native runs were worker-executed, not independently repeated by reviewer.

Remaining limitation: software desktop GL proves this authored fixture and rendering/ownership paths; it does not prove arbitrary imported production rigs or other platforms. Full-reactor verification is owned by root and intentionally was not duplicated.
