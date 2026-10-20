# Engine quality defects and closure gates — 2026-10-20

Scope: the quality working tree based on `51cebd9`, Task1 commit `123bce6`,
lighting commit `6efbf8d`, and subsequent integrated changes based on `6efbf8d`.
Original audit: [engine quality review](../engine-quality-review.md).
Implementation: [plan and gate status](../Spec/2026-10-20-quality-implementation.md).
Checked entries mean the described bounded defect is fixed and verified; they do
not certify the overall roadmap, platform support or AAA readiness.

## Core ownership and events

- [x] **Q-001 / P2 — cross-scene Transform3D hierarchy and stale parent links.**
  Before `123bce6`, parent scene B at X42 could influence a child owned by scene A;
  attaching endpoints later, including through standalone intermediates, bypassed
  scene ownership. Reproduce by linking unowned transforms, attaching the endpoints
  to different games, or removing an attached parent while keeping its child.
  Expected: valid standalone hierarchies remain usable; attachment/reparenting
  rejects incompatible scene ownership before mutation, and removal detaches
  retained links while preserving local TRS. `Transform3DOwnershipTest` covers both
  attachment orders, intermediates, failed-attachment reuse and cleanup. SceneJson
  now verifies a removed parent's child exports/restores with no dangling parent
  and unchanged local/world translation. Focused core tests, fresh scoped review
  and integrated reactor pass. This intentionally changes removed-parent export
  from a dangling-parent error to successful detached-child serialization.

- [x] **Q-002 / P2 — a throwing change receiver skips other committed-event recipients.**
  Before `123bce6`, register a throwing listener then a recording listener and add
  a component: membership/index commit succeeds but the second listener sees no
  event. Expected: all entry-snapshot eligible recipients receive the committed
  change in deterministic order, then distinct failures are aggregated without
  self-suppression. Current delivery also honors removal, cancellation, close and
  reentrancy. Fresh review caught pending components initialized by a listener
  becoming eligible during the same delivery; the regression now proves they and
  the newly added component stay excluded until the next event. Dedicated event
  tests, fresh review and integrated reactor pass. Callback failure still does
  not roll back committed component membership.

## Lighting storage

- [x] **Q-003 / P3 — repeated snapshot allocations.**
  Before `6efbf8d`, each light snapshot constructed light/attribute/color/range
  storage. Reproduce with `LightSnapshotBenchmark`, including query result copies.
  Current snapshots stage into alternating high-water buffers and publish only
  after full validation; repeated validation failure preserves the previous
  environment values. Capacity/count shrink, overflow, mutations and independent
  copies have regressions. Root repeated before/after JDK25.0.4.1 allocation
  measurements with identical workloads; median default snapshot allocation
  decreased from 3,679.202 to 1,552.027 bytes. [Raw samples and contract](../scene-lighting.md).
  Fresh review and integrated reactor pass. Query copies and growth still
  allocate; no measured GC-latency or render-performance claim follows.

- [x] **QUALITY-LIGHT-001 / P3 — scene-light attributes compare equal despite different values.**
  Pre-fix libGDX `Attribute` equality used the inherited type-only hash path.
  Reproduce by changing an active point range or fog field and comparing markers.
  Value equality/hash now follow active fields, Float ordering and canonical NaNs,
  while ignoring unused capacity. Five focused cases include deliberate hash
  collisions, signed zero and copies; focused lighting22/22, fresh review and
  integrated tests pass.

## Optional glTF/PBR and managed graphics recovery

- [x] **QUALITY-GLTF-001 / P2 — accepted imports lose resources or authored meaning.**
  Initial Task3 review found upstream2.3.0 unmanaged imported textures, incomplete
  failed-import texture evidence, conflicting per-channel transforms on a shared
  UV set, and collisions between unnamed `animation0` and explicit clip names.
  Reproduce with managed-context reload, a failing import after allocations, two
  differently transformed UV0 maps, or an unnamed first clip plus `animation0`.
  Managed Pixmap-backed texture ownership/rollback and strict preflight now cover
  these cases. CPU tests, fresh review and real glTF GL30/GL20 gates pass, including
  exact pixels after an actual second unshared context. The profile deliberately
  rejects unsupported compression/material/morph/sparse/non-LINEAR content; it is
  not full Khronos validation or an untrusted-file sandbox. [Contract](../gltf-assets.md).

- [x] **QUALITY-PBR-001 / P2 — finite point range ignored by upstream shader.**
  Upstream gdx-gltf2.3.0 stores `PointLightEx.range` but does not use it in GLSL.
  Merely copying that field accepted a range with no corresponding visual cutoff.
  Reproduce the same scene with range4 versus0.25. Version-pinned `RangePbrShader`
  now uploads actual range uniforms and applies Javity's finite attenuation;
  legacy unlimited lights retain upstream falloff. Exact-once source-seam tests
  reject upstream drift. Real GL30/GL20 pixel gates and integrated reactor pass.
  Javity spot/fog semantics still reject explicitly in PBR.

- [x] **QUALITY-GL-001 / P1 — first frame loses ordinary meshes after context replacement.**
  Pinned libGDX1.14.2 `VertexBufferObjectWithVAO.invalidate()` creates a fresh VAO
  while retaining `cachedLocations`. Reproduce with an actual unshared replacement
  context, managed mesh invalidation, then the same shader locations and unchanged
  receive-only floor. Initial shadow evidence lost bright pixels from24,016 to1,277;
  a darkening-only assertion falsely counted missing floor as shadow. Shared
  `ManagedMeshBindingsRecovery`, called by renderer recovery, now clears only the
  current application's pinned CPU binding cache. The permanent shadow gate
  compares the FIRST unchanged-policy recovered frame byte-for-byte before any
  variant toggle. A no-op-helper negative control fails it; production restores
  all24,016 bright pixels. Plain PBR GL30/GL20 and HDR also compare unchanged
  borrowed models after real context replacement. CPU tests, fresh review and
  final integrated native gates pass. Host managed resource recreation remains
  required before the renderer recovery hook; this is desktop GLFW evidence,
  not Android EGL validation of the new features.

- [x] **QUALITY-FBO-001 / P2 — allocation/disposal failures leak or retain owned GL names.**
  Review reproduced allocation exceptions escaping inherited libGDX cleanup and
  disposal callbacks observing still-live ownership. Reproduce a thrown framebuffer
  status check after texture/depth/FBO generation, or a throwing delete callback.
  Shared `RecoverableColorFrameBuffer` owns explicit generated tokens, retires
  ownership/registry state before callbacks, attempts all cleanup, preserves the
  primary throwable, and prevents repeat/ABA deletion. Failed rebuild can retry;
  explicitly disposed owners reject rebuild. CPU regressions include seven
  allocation stages and failure during each delete; regular/HDR combined10/10
  tests pass. Native HDR checks failed-allocation rollback, real recovery,
  repeated disposal and actual owned-name deletion; fresh review and final
  integrated build pass.

- [x] **QUALITY-HDR-001 / P2 — callback sampler poisoning breaks tone-map composition.**
  The first state helper reset only samplers nonzero at entry. A callback binding
  a mipmap sampler to the nonmipped color texture produced black composition.
  Permanent native regression starts sampler0, installs the hostile sampler in
  the callback, and requires reference188/231 pixels plus restored entry state.
  Previous helper is RED0/0; current all-unit reset is GREEN. Final integrated
  `hdrSmoke` passes. [HDR scope](../hdr-rendering.md) is desktop GL30 RGBA16F,
  explicit exposure and ACES/Reinhard, with no GL20/Android/web support claim.

## Root motion and UI

- [x] **QUALITY-ANIM-001 / P2 — callback root-curve edits deliver a stale movement interval.**
  Initial translation extractor preplanned against retained keys; callbacks could
  mutate them before pose publication. Reproduce endpoint value, key replacement,
  key time or inserted key edits in a marker callback, including nested update.
  Exact retained key-time/XYZ bit-and-size comparison now cancels the stale
  interval, with subsequent ticks using the live curve. Reused high-water scratch
  leases retain reentrant isolation and release on success, early return and
  exceptions. All140 animation tests pass, including17 root-motion tests; fresh
  review and integrated reactor pass. [Root motion](../root-motion.md) is named-base
  translation only through an application-owned sink, with no implicit physics,
  rotation, IK or retargeting authority.

- [x] **BUG-UI-001 / P2 — hosted cross-rasterizer parity regression fixed.**
  CI[39375230181](https://github.com/Radomiej/JavityEngine/actions/runs/39375230181),
  job118471773977, source`af73022c9dafeaa14b9bfff7e597ec935e8c9cb2`, reproduced
  native Card title ink79px versus browser74px against the unchanged4px limit.
  Recovered hosted logs confirm common bundled TTF SHA and loaded/applied font;
  browser range76.34/canvas76.33 versus native layout79. The cause-level fix retains
  fractional FreeType advances/kerning and first bearing at original atlas size.
  Local replay against that exact hosted JSON and UI/K2/K4 gates passes without
  changed fixtures, goldens or tolerances; current title ink74px. Fresh font review
  also resolved borrowed source-scale refresh and unbounded historical clone
  retention, preserving a current cache entry per style and borrowed ownership.
  **Closed by fresh [Desktop39391819541](https://github.com/Radomiej/JavityEngine/actions/runs/39391819541) on source`f2fafce`**:
  job118524909757 passed native UI parity and browser/native screenshot gates,
  with unchanged fixtures/goldens/tolerances. Entire Desktop workflow succeeded;
  Android, Wasm and Pages also passed on that exact source. This closes the scoped
  hosted rasterizer defect, not full browser/CSS/shaping equivalence.
  [Historical entry](2026-10-17-publication-review.md),
  [font contract and limits](../ui-html-parity.md).

## Integrated evidence and bounds

Root's final integrated run completed `build`, ECS `usageSmoke`, glTF and original
Fox GL30/GL20, directional shadows, HDR, Fox Garden and UI parity successfully.
A later full build plus `installDist`/`qualitySmoke` passed; launching the installed
distribution outside the checkout also passed using packaged original Fox assets
and notices. Session logs: `/tmp/javity-quality-final-integrated.log`,
`/tmp/javity-quality-package-build.log`, `/tmp/javity-quality-installed.log`.
Fresh separate Wasm `orbitPackage`/`test`, actual Chromium framebuffer/input/resize/
dispose and typed packaged Java/Wasm plus synthetic MessagePort proofs also pass
(`/tmp/javity-quality-wasm-browser.log`, `/tmp/javity-quality-platform-browser.log`).
These preserve the existing experimental backend; they do not prove the new
optional PBR/shadow/HDR features on Wasm. These paths are session evidence,
not durable published artifacts.

Verified native context: Linux Xvfb, Mesa25.0.7 OpenGL4.5 core/compatibility via
libGDX GL30/GL20 paths. Software GL establishes the tested correctness only.
Shadows/HDR are desktop GL30; optional `GltfAssets` is synchronous, distinct from
budgeted `AssetStreams`; Fox proves one real imported24-joint rig against12 oracle
samples, not arbitrary production rigs. New Android/GLES/Wasm features, physical
GPU performance, broader streaming/foliage/authority integration and AAA readiness
remain separate work. Fresh hosted BUG-UI-001 evidence is now green; see closure above.

## Final cross-feature review

- [x] **QUALITY-SHADOW-002 / P2 — invisible MASK material casts an opaque shadow.**
  Final Sol integration review reproduced a glTF MASK caster with
  `baseColorFactor.a=0` darkening766 receiver pixels although the color pass
  correctly hid it. Pinned upstream PBR depth shading samples base-color texture
  alpha only on UV0 and ignores factor alpha, vertex color and material opacity.
  `DirectionalShadowPass` now rejects unsupported MASK caster variants before
  GL submission: non-unit factor alpha/opacity, vertex colors, nonzero base-color
  UV and missing required blending marker. Ordinary imported UV0 texture masks
  remain supported. Permanent native red/green regressions cover each rejected
  case along with existing masking, skinning, failure and context replacement.
  Source: `d4792b7`; [supported contract](../shadows.md).

- [x] **QUALITY-SHADOW-003 / P2 — alpha cutoff equality differs between passes.**
  Fresh narrow review found upstream depth uses `< cutoff`, while PBR color uses
  `<= cutoff`. A transparent texel at cutoff0 can therefore write an invisible
  shadow. `cbe61b3` version-checks the exact pinned shader seam and aligns
  depth to `<=`; unsupported untextured cutoff>=1 and invalid cutoff inputs fail
  before submission. Native regressions cover cutoff0 and alpha128/255 equal to
  cutoff128/255 while preserving ordinary0.5 masks. Fresh Sol rereview approved;
  full reactor plus shadow native25 state-checked views passed in17s (119 tasks).
  [Retained final log](../artifacts/engine-quality/shadow-cutoff-build.txt).
