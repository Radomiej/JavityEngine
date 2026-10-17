# Runtime batch — integration evidence

Whole-goal range starts at `0c9a236`. [Initial final review](review-before-fixes.md)
reviewed through `41518e9`: Spec/Quality PASS with three P3 corrections and no
important integration defect. The [bounded correction wave](fix-report.md) in `d1070a2` and its
[scoped re-review](review-after-fixes.md) address all three findings: Spec/Quality
PASS, no residual issues.

[Native checkpoint excerpt](integration-native-checkpoint.txt) contains exact
selected lines from the root's actual successful build/native run on `41518e9`:
47 seconds, 110 tasks (24 executed, 86 up-to-date). Default-reactor retained
JUnit XML: 805 tests / 91 suites / no failures, errors or skips. This is a fresh
successful incremental command, not a claim that every up-to-date test executed
again. The excerpt covers real desktop GL controls, not headless mocks.

[Explicit restored paths](restored-historical-captures.txt) lists the 45 generated
tracked outputs restored after that verification. Their source checkpoints and
metrics were preserved rather than silently replaced with later frames. Current
environment captures have [separate provenance](../environment/README.md).

The [post-fix root run](postfix-native-checkpoint.txt) on `d1070a2` passed the
full incremental reactor and affected asset-streaming/environment-demo/inspector
native gates in 15 seconds (97 tasks). Retained XML remains 805 tests / 91 suites,
zero failures/errors/skips. [Final streaming metrics](streaming-final-proof.json)
record 211 actual checks including four retries with bounded failure history.
Historical environment source/capture hashes remain their Task 3 checkpoint;
the final smoke-only panel test extension does not relabel those captures.

Final APK debug compilation on `d1070a2` also passed (4 seconds, 84 tasks;
3 executed, 81 up-to-date).
**Android feature runtime/EGL for the new streaming and environment remains
UNVERIFIED.** Desktop managed recreation is not actual context destruction.

[Batch verification and boundaries](../../runtime-batch-verification.md) ·
[Integration decisions](../../runtime-batch-decisions.md).
