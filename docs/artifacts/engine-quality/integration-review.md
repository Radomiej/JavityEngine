# Final cross-feature integration review — 2026-10-20

Review base: 51cebd9; inspected committed core/UI/animation plus dirty optional-3D sources, at HEAD fb278749f4454d0a478d7e549a8601c05cffccbf before root final integration commit. Read AGENTS.md and all five scoped reviewer reports. Applied systematic-debugging, test-driven-development and verification-before-completion guidance for the new integration defect. No subagents or Gradle processes launched by this reviewer.

## Verdict

APPROVE current bounded production integration. Fresh final full build plus directionalShadowSmoke passed after both MASK corrections below. No remaining actionable blocking finding found. This is a code/integration review, not a new Android, Wasm, hardware GPU, root-motion/Bullet or hosted browser-parity certification.

## Resolved P2: imported MASK semantics exceeded the depth shader profile

DirectionalShadowPass.validateCaster accepted any AlphaTest material. Pinned gdx-gltf 2.3.0 PBR depth samples only base-color texture alpha from UV0, while PBR color applies factor/vertex alpha and the chosen texture UV channel. An invisible MASK material could therefore cast a visible shadow. Source inspection of upstream depth.fs.glsl/depth.vs.glsl, PBRDepthShader and PBRMaterialLoader identified the exact material/shader boundary.

Independent native reproduction: copied the current smoke launcher under /tmp, set its opaque MASK's PBR base-color factor alpha to zero, and compared receiving disabled/enabled. The invisible textured caster still darkened 766 pixels; removing the texture left the same 766 false-shadow pixels. Evidence: /tmp/sol-shadow-mask-integration/before-native.log and ShadowMaskIntegrationProbe.java.

Root authorized narrow truthful rejection. Production validation now rejects MASK casting with non-unit base-color factor alpha, non-unit blending opacity, vertex colors, nonzero base-color UV indices, or the missing upstream BlendingAttribute marker. It executes during complete input preflight before changing GL or receiver environments. Opaque materials and existing unit-alpha UV0 texture MASK remain supported; these unsupported materials can still receive with casting disabled. Limits are explicit in docs/shadows.md.

Permanent regression in DirectionalShadowSmokeLauncher.proveMask verifies each unsupported variant while preserving actual transparent/opaque MASK pixel comparisons. The temporary vertex-colored model is disposed after object destruction/scene flush. Expected exceptions also execute through the existing per-pass GL fingerprint assertion. The test ran before production changes and failed with `unsupported MASK caster accepted: base-color alpha` (red.log). After the fix, the complete source-compiled shadow native gate passed (green.log): darkPixels=250, movedMaskPixels=659, stateCheckedPasses=21, including policy, movement, skinning, failure recovery, configure/resize, actual unshared context replacement and disposal.

Changed files owned by this reviewer:
- javity-gltf/src/main/java/org/javity/gltf/DirectionalShadowPass.java
- javity-desktop/src/main/java/org/javity/demo/DirectionalShadowSmokeLauncher.java
- docs/shadows.md

Root was asked to include this resolved defect in its already-owned docs/Bugs/2026-10-20-engine-quality.md record. No additional repository files edited.

## Other cross-feature checks

- Provider ownership transfers after capacity validation; constructor failure closes the provider through one owner. PBR factory transfer and shadow exact-provider/claimed validation have coherent boundaries. Renderer cleanup continues through pass, model batch/provider and remaining resources after failures.
- Custom providers/world passes disable static and skinned instancing, including AvatarView overrides. The owner identity map covers ordinary descriptors in the shadow path; depth copies cannot leak depth shaders into color. Receiver/nonreceiver environments stay distinct through deferred batch flush.
- PBR adaptation pools by source Environment identity, copies shadowMap explicitly and resets between completed views. Extras overlay borrowed upstream additions. The documented authoring rule against overriding direct scene lights remains a caller contract; normal IBL extras preserve light/range and shadow selection.
- LINEAR output disables output gamma and retains accurate sRGB input decoding. No upstream hidden tone-map is present. HDR surrounds linear world/depth/color and returns to display output before UI. Sky/custom/legacy/display-processed material limits remain explicit.
- Managed VAO repair is application-scoped/version-checked, preserves geometry/ownership, and follows host mesh invalidation. Shared FBO allocation owns raw names before upload/wrapping, preserves registry identity on failed recovery/retry, drops lost-context names without deletion, and retires ownership before terminal GL callbacks.
- Root-motion is explicit application authority handoff. Immutable model-space deltas are not silently applied to Transform3D/Bullet. Imported ModelInstance and avatar paths share the canonical stripped pose. Scoped sampler/lease and UI borrowed-font corrections remain intact; no new integration issue found.

## Fresh evidence and limits

This reviewer ran direct JDK25 compilation against local build dependencies, not a new full reactor. Native execution used Xvfb :219/Mesa software OpenGL after escalation for local X11 access. Native exit statuses and logs were inspected (red 1, green 0). git diff --check passed. Root owns fresh reactor/new-GPU gates for final source and independently reported older native/Wasm/typed-platform gates. The supplied prior final-integrated PASS predates this MASK correction and is not represented as its integrated proof.


## Final equality-boundary correction and release gate

Fresh shadow rereviewer found a second related P2: pinned PBR depth discarded alpha < cutoff while the color shader discarded alpha <= cutoff. Independent prepatch native cutoff-zero RED is recorded in /tmp/sol-shadow-cutoff-review/red.log. My own first equality-red attempt failed at GLFW initialization after the exec server killed Xvfb219; it is explicitly not behavioral RED evidence.

Root authorized the bounded shader correction. DirectionalShadowPass.makeBatch now rewrites one exact release-specific alpha-discard seam from < to <=, throwing on missing/duplicate source seams. The change covers original construction, interrupted-batch replacement and context-restored shader construction. All other upstream shader code remains borrowed unchanged. The additional untextured MASK cutoff>=1 edge is rejected before GL because upstream emits no texture alpha test there; finite/nonnegative cutoff is enforced. Ordinary untextured unit-alpha MASK cutoff<1 remains permitted. Docs describe these exact bounds.

Permanent native regressions compare receive-off/on pixels for fully transparent alpha0/cutoff0 and exact normalized alpha128/255 equal to cutoff128/255. Additional preflight regressions cover untextured cutoff1 and NaN cutoff. The fresh reviewer approved the source change and these bounded limitations.

FINAL FRESH GATE: JDK25, local Gradle cache, temporary Xvfb224/Mesa software OpenGL, command `./gradlew build :javity-desktop:directionalShadowSmoke --no-daemon`. Process exit0; BUILD SUCCESSFUL in17s,119 actionable tasks. Directional shadow PASS: darkPixels250, movedMaskPixels659, stateCheckedPasses25, including equality regressions, existing policy/movement/MASK/skinning/draw-failure/resize/actual unshared-context recovery/disposal. Full log /tmp/javity-quality-cutoff-final.log. The temporary display was stopped via EXIT trap. git diff --check passed on final source. Root retains publication/commit ownership and prior broader native/Wasm evidence; this final gate is the complete reactor build plus changed shadow runtime scope, not another blanket platform certification.
