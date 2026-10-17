# Lokalny Colyseus implementation plan

Spec: docs/superpowers/specs/2026-10-17-local-colyseus-design.md. User explicitly ordered implementation against rooms roadmap. No additional approval needed. Sequential Luna implementers + independent review.

## Global Constraints
- Optional module, no core/desktop reverse dependency; Java25 and same Game/Component lifecycle.
- Real Colyseus Rooms and official SDK; Java sole gameplay authority. No mock proof or claim browser/Wasm engine works.
- Owner-thread Game mutation, bounded input and process lifetime, loopback binding, process cleanup, npm lockfile and exact versions.
- No legacy removal, shared renderer rewrite, physics claims or untrusted scripts.
- Read spec for exact protocol/constants and tests. No subagents for implementers.

### Task 1: Headless Java room simulation and worker
Create javity-rooms/build.gradle (application plugin/main org.javity.rooms.LocalRoomWorker), source/test package org.javity.rooms, add settings include. Implement spec simulation and JSONL worker with bounded input, safe validation, timing and close. Expose testable simulation with explicit owner thread and injected monotonic time for input expiration. Worker accepts roomId as command line argument set by gateway; rejects mismatched protocol/room and bad commands, request IDs for join/leave/shutdown replies. ready only after scene started. Process input queued and serviced on owner thread; daemon reader exits EOF, shuts room down, never mutates Game. installDist task generates portable launch script used by Node.
Write failing meaningful tests first, verify RED, implement GREEN including actual child Java process lifecycle (test JVM may launch worker using java.home and java.class.path, no nested Gradle). Save logs and report, self-review, commit scoped changes; don't push.
### Task 2: Colyseus gateway, local preview, real integration and docs
Consume Task1 actual worker contract without bypass; minor protocol refinements if needed must retain Java regression coverage. Create web/local-multiplayer package/lock, src gateway/worker supervisor/schema, public preview and node:test integration. Read upstream packages in /tmp/javity-colyseus-upstream for actual API, not invented older examples. Use own server startup function with ephemeral port and stop handle for tests, no auto-listen on import. Build Java worker before tests with documented root command; npm test runs only integration, npm start starts dev. No global service stop/killall. SDK dist can be served from installed official package via exact route, no CDN/build tool required.
Integration exercises all spec cases with real worker and clients plus startup/crash cleanup and no leaked children. Browser preview manual instructions, classify browser interaction as UNVERIFIED unless actually run. Add docs/local-multiplayer.md and link docs index/guide, update TODO/work queue/AGENTS status and roadmap carefully. CI CPU gate includes Java test/installDist + npm ci/test under Java25/Node24. THIRD_PARTY records Colyseus MIT lineage and npm license inventory if needed. Produce docs/artifacts/local-multiplayer/verification.json with commands, source hash and proof scope. Run full reactor after final graph changes. Commit and independent task review; final whole-goal review across ECS+rooms; push existing branch after passing checks.
