# Changelog

This file records user-facing changes. There are no published release entries here yet. Dates below describe the current unreleased modernization work; they are not release dates.

## Unreleased

### Engine gallery and demo page — 2026-10-20

- Add deterministic native full-HD `qualityGallery` / `foliageGallery` capture commands without HUD, keeping smoke assertions separate.
- Refresh GitHub Pages with actual engine shots, full-size keyboard-accessible image previews and quality/foliage/imported-avatar launch examples.
- Retain media source hashes/credits and add responsive gallery/browser QA plus native capture CI artifacts.

### Foliage and imported-avatar authoring — 2026-10-20

- Add seeded merged grass/shrub components, bounded unsigned-short geometry, renderer-owned transactional caches and geometry/draw diagnostics.
- Add an original CC0 cutout atlas, branching-tree demo, explicit optional PBR foliage materials and native alpha/depth/lighting/lifetime controls.
- Keep offscreen foliage available to world passes while culling its color draws; generalize world-pass ownership to Component.
- Inspect imported avatar node/part/palette/clip metadata and build explicit semantic profiles; add an original CC0 GLB humanoid with independent animation, arms/tool views and socket tests.
- Integrate four foliage patches into Fox Garden; add GL20/GL30 examples and CI gates.

### Engine quality and modern 3D — 2026-10-20

- Enforce Transform3D scene ownership, detach removed parents and deliver committed component changes despite failed observers.
- Reuse transactional lighting snapshots; correct value equality and add an opt-in CPU allocation benchmark.
- Add optional pinned glTF/GLB import, managed texture/lease ownership, PBR, explicit material-profile validation and finite point-light ranges.
- Add directional shadow policy, RGBA16F HDR/exposure/ACES/Reinhard and an integrated Fox Garden runtime-tools showcase.
- Add translation-only root-motion extraction with an explicit movement sink, exact loop handling and reusable callback-mutation snapshots.
- Preserve fractional native font advances/bearings without enlarging atlases; bound borrowed-font cache history.
- Share managed VAO recovery between desktop/core and Android; use one failure-safe framebuffer allocator with idempotent cleanup.
- Add real GL context-replacement, allocation/draw failure, rollback and negative-control tests. Platform limits and commands are in the new guides.

### Game authoring APIs — 2026-10-20

- Added typed platform services with a local provider and a browser ORBIT adapter, bounded owner-thread callback delivery, cancellation and versioned saves.
- Added GameObject UI panels, activation notifications and owned resource scopes; hidden panels release Stage focus.
- Added prioritized input contexts, source consumption, analog/button feeds, touch support and atomic rebinding.
- Added versioned prefab catalogs, explicit asset dependencies, migrations and validated scalar overrides.
- Added authority/replication schemas, stable network IDs, bounded snapshots/interpolation and a versioned wire codec tested over TCP. Live Colyseus transport and prediction/reconciliation remain separate work.
- Added font-layout diagnostics without changing the open native/HTML parity gate. See the [implementation and limits](docs/Spec/2026-10-20-authoring-api-batch.md).

### Core API reliability — 2026-10-19

- Remove the exact component instance even when components override equality; preserve scene indexes and removal notifications.
- Continue cleanup when callbacks reuse the same exception, and preserve the original update failure if deferred cleanup also fails.
- Added eight lifecycle regressions and a scoped [API review and prioritized backlog](docs/Spec/2026-10-19-engine-api-review.md).

### Launcher integration — 2026-10-19

- Integrated ORBIT `.nekko` packaging, browser API bridge and cloud-save example with the current engine tree.
- Build and validate the Wasm package on master and pull requests; publishing remains a separate manual action.
- Requires the matching launcher API with explicit TeaVM JSO script policy; source updates do not establish production deployment.

### Modernization — 2026-10-17

- Updated the build baseline to a full JDK 25 and Gradle 9.8.1 wrapper, and aligned the engine with libGDX 1.14.2.
- Added the new `javity-core` GameObject and Component API, with optional modules for Box2D, Bullet, Yoga UI, scripting, maps, asset streaming, Artemis, and local Colyseus rooms.
- Kept the historical `javity-engine` and Galaxy Rapid APIs in the repository for migration. The new core does not depend on Artemis; applications that use Artemis can add `javity-ecs-artemis` explicitly.
- Added desktop demos for the new core and retained a legacy demo. See [the README](README.md) and [documentation index](docs/README.md) for setup and module guides.
- Added an opt-in Android app and Yoga/QuickJS providers. Emulator API 35 x86_64 checks cover selected UI, scripting, rendering, input, and lifecycle paths. The complete unfiltered five-test suite passes on the hosted KVM emulator; evidence and remaining limits are documented in [Android status](docs/android-status.md).
- Added a separate TeaVM/WebAssembly rendering experiment with a verified Chromium/SwiftShader WebGL2 scene. It is a rendering spike, not a full engine or platform port; see [the spike notes](docs/javity-wasm-rendering-spike.md).
- Documented deliberate compatibility limits: the UI utility classes are not full CSS/Tailwind, and Unity-inspired animation/feedback/React integrations are Javity implementations, not ports of Unity or proprietary plugins.

### Quality and documentation — 2026-10-17

- Fixed Wasm scene cleanup after failures and rollback after partial initialization; added four cleanup regressions.
- Fixed Yoga/Ammo host startup/dispose/recreate races, partial construction cleanup, inactive ticks and cancelled Promise error handling. Seven real-browser regression scenarios cover ownership and failure paths.
- Fixed the rendering proof to inspect canvas pixels without the HTML overlay, reject disabled draw calls and overwrite stale PASS evidence after failures.
- Added a responsive GitHub Pages showcase with real demo captures, example commands and explicit platform limits; publication source is `site/`.
- Fixed CI setup of Android SDK by avoiding the retired `tools` package, and made the MapLibre fixture check portable across SQLite versions while preserving data/hash checks.
- Made the multiplayer ack-backpressure regression deterministic instead of relying on process scheduling and microsecond sleeps; real-process protocol tests remain.
- Added a manually triggered full Android instrumentation workflow with logcat/ANR artifacts. Corrected the compact-viewport emulator setup, input injection ownership, and fresh-emulator immersive onboarding; the complete five-test suite now passes in one hosted KVM run. Historical startup ANR cause remains unproven.
- Added [Bugs](docs/Bugs/README.md), [Spec](docs/Spec/README.md) and contributor guidance updates.

### Compatibility notes

- Building the modernized tree requires Java 25 and the Gradle 9.8.1 wrapper. Projects using the older Java/build setup may need build configuration changes.
- The new component API is separate from historical Javity APIs. Existing games may need code and dependency changes to migrate; the legacy modules remain available during that work.
- Optional modules have separate runtime and platform requirements. A desktop build does not imply Android or browser support; consult each module's guide and status notes.

