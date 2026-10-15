# Plugin API Ports Implementation Plan

> For agentic workers: superpowers:subagent-driven-development, Luna implementers/reviewers selected by user. No commits/push. Sequential implementation; root integrates and verifies.

**Goal:** Working Javity equivalents of OneJS UI/scripting, Animancer playback and Feel feedback.
**Architecture:** Existing component lifecycle, native Scene2D/Yoga host, optional JavaScript module, root-owned demo integration.
**Tech Stack:** Java25, libGDX1.14.2, LWJGL3.4.3/Yoga, optional GraalJS, JS JSX bundler.
**Spec:** ../specs/2026-10-14-plugin-ports-design.md

## Global Constraints
Preserve all prior uncommitted modernization. No commit/push. Core optional JS-free. WORLD/UI clocks. Per-instance animations. No invented Unity compatibility. Report actual verification.

## Review Focus
- Reentrant callbacks and stale handles: owner destruction/replacement stops writes.
- Existing API and assets: no animation double tick or shared mutable pose.
- UI reload/parser failure: no leaked Yoga nodes/listeners or old callbacks.
- JS event/timer cleanup: no callbacks after context close, single-thread access.
- Native tests: GL pixel/pose/input evidence, not compile-only claims.

## Tasks
- [x] A: org.javity.effects foundation, targets/leases/tweens/feedback + focused lifecycle tests.
- [x] B: org.javity.animation clip/state controls + ModelRenderer/SpriteAnimator compatibility + generated pose/tests.
- [x] C: org.javity.ui.declarative native host, markup/state/events/controls + nativeYoga tests.
- [x] D: optional javity-script runtime, JSX/React host adapter and bundled sample + scripting tests.
- [x] E: PluginsDemo integration, animation/effect/UI/JS native smoke, launcher/Gradle/CI, docs/licenses.
- [x] Root: per-task review fixes and final review, clean reactor and six native smokes, preview/distribution.

Each worker reads its own brief in .superpowers/sdd/2026-10-14-plugin-ports/briefs. Each adds meaningful tests first, verifies targeted tests under coordinated Gradle use, supplies report then independent reviewer verdict. Root keeps shared build/docs/launcher files. Interfaces frozen before dependent task dispatch.
