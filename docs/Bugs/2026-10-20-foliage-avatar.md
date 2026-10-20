# Foliage / imported-avatar integration review — 2026-10-20

Scope: next quality batch based on `9f840ad`, not blanket AAA/platform certification.
[Specification](../Spec/2026-10-20-foliage-avatar.md).

- [x] **FOLIAGE-001 / P2 — offscreen plants stop casting visible shadows.**
  Initial cache used color-camera culling before the world pass. A patch outside
  the camera could cast onto visible ground, but its descriptor never reached
  the directional shadow pass. Independent reviewer supplied a deterministic
  caster-list probe. The renderer now keeps separate reusable pass-only inputs,
  with the same selected environment and component ownership, and still omits
  them from color draws. The runtime regression failed with zero pass-only
  descriptors before the fix, then passed. Independent narrow review compiled
  current code and passed17 focused tests; full core suite587 tests passed.
  Source fix is included in `d600c20`. This tests submission correctness, not a
  claimed native offscreen-shadow pixel benchmark.

- [x] **FOLIAGE-002 / P2 — demo teardown stops after a component cleanup error.**
  Start a Game with an onDestroy callback that throws, inject a tracking model
  into FoliageDemo, then call dispose twice. Original independent probe recorded
  zero model releases both times because disposed was already set. Cleanup now
  retires ownership once and attempts renderer, models, source texture and UI
  resources even after earlier failure; original errors remain primary and
  application exit runs in finally. Three fault regressions cover distinct/same
  throwable identities, repeated disposal and failed-create cleanup. Corrected
  probe records one model release across both calls. Source: `7cf25f4`;
  independent review approved and fullreactor/native regression passed.

- [x] **AVATAR-001 / P2 — failed capture leaks temporary pixels / framebuffer state.**
  The initial imported-avatar proof lacked finally around Pixmap release and
  caller framebuffer restoration. A deliberately invalid PNG target now throws
  through nested cleanup, preserves the caller FBO, and allows later captures and
  resource release. Both native GL30 and forced GL2.1 runs passed. Included in
  `7211235`; CPU inspection/profile tests also pass.

The review also corrected an invalid documented Gradle task name. No native
Android/Wasm foliage runtime, actual new-foliage EGL restoration, automatic
humanoid classification, wind/LOD or physical-GPU performance is implied.
