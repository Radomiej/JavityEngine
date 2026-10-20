# Authoring API verification — 2026-10-20

Exact source/file hashes, commands and scoped results: [verification.json](verification.json).

- Default reactor, optional Artemis usage and genuine native UI smoke pass. XML inventory:928 cases in109 suites, zero failures/errors/skips, including cached unchanged results. New authoring regressions account for49 cases.
- The separate Wasm build/package and12 experiment tests pass. [Standalone browser proof](wasm-runtime.json).
- [Typed platform proof](typed-platform.json) runs the actual `.nekko` archive under TeaVM JSO CSP against a two-origin synthetic MessagePort host: Java profile, keyboard P save, existing panel save, shared CAS revision, invalid save and dispose. It uses the real vendored SDK; it is not a live launcher/API/Postgres/S3 test.
- [Negative control](typed-platform-negative.json) disables GL draws and correctly fails at the DOM-free empty/flat framebuffer assertion; it writes the current success:false rather than retaining a previous PASS.
- Replication tests transfer the production JSON v1 codec over loopback TCP, validate authority/session identity and exercise reconnect/gaps. This is not a live Colyseus/ticket adapter.
- [Native/HTML comparison](ui-comparison.json), [font probes](font-diagnostics.json). Native panel activation/hide/restore also passes. BUG-UI-001 remains open for hosted font parity; its tolerance was not changed.

Screenshots inspected during review: [native panel](native-ui.png), [packaged Wasm canvas without DOM overlays](wasm-framebuffer.png). These captures use software rendering; they provide no hardware performance or physical-device certification.

CI builds a separate local-origin test package for the synthetic host. That fixture does not replace or modify the package intended for manual publication. No private game publication or new Android runtime validation occurred in this batch.

Three deterministic Node VM cases cover delayed/null/stale save reads and monotonic shared revisions. Java regressions also cover JSON payload canonicalization, stored null/string values, and request-envelope escape rejection.
