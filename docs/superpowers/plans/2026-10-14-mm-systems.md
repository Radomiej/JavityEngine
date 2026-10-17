# MM systems implementation plan
Spec: ../specs/2026-10-14-mm-systems-design.md. Supervised Luna agents, sequential implementation and independent reviews. Root integration/docs/build; preserve uncommitted work, no commits/push.
- [x] A: typed EventBus/Radio and FSM.
- [x] B: time-scale/hit-stop manager, Game clocks and regressions.
- [x] C1: browser HTML/Tailwind comparison and native utility/control polish.
- [x] C2: nested/controlled React and signal subscriptions bridge.
- [x] C3: animated/delayed native ProgressBar.
- [x] D: incremental scene loading/transactional transitions/fade.
- [x] E: audio buses/voice handles and floating-text pool.
- [x] F: tempo/track sequencer.
- [x] H: deterministic additive presentation shake/punch/springs, flash and extensible feedback cleanup (native combined proof in G).
- [x] I: animation seek, named markers and reusable transitions (native proof in J/G).
- [x] J: actual model linear mixers and masked override layers with pose/skin tests.
- [x] K: native keyboard/focus events, controlled Toggle/Slider, bounded paragraph wrapping and development reload workflow.
- [x] K4: explicitly registered borrowed images/icons in native/React UI, with real HTML image-fit/rounded comparison.
- [x] G: combined native demo, full build/GL/browser verification, docs and final review.
Shared interfaces: A Signal/Subscription consumed by C; B clocks by F; D loading progress by C/G; E resources by G. Detailed task briefs freeze each API. Snapshots used for uncommitted review diffs; maintain ledger. No FFT/haptic stub implementation claims.
