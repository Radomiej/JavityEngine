# Changelog

This file records user-facing changes. There are no published release entries here yet. Dates below describe the current unreleased modernization work; they are not release dates.

## Unreleased

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

