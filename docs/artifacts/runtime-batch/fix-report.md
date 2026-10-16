# Final bounded runtime fix wave

Base: `41518e9a3210e5f7fb05069994ab4619f58d7b8d`.
Final scoped commit: `d1070a293e57531bd2c5a78ac8bd4dff7bbd50f0` (`fix(runtime): bound retry history and discover added lights`).
Branch: `modernization/java25-gradle-yoga`. No push. Ready for root's independent scoped re-review.

Exactly three P3 issues addressed in five files (105 insertions / 10 deletions):

1. `AssetStreamingDemo.requestMissing` now retains the current request and immediately previous FAILED lease, closing older failure history. It obtains the new request before retiring history, so synchronous request rejection preserves existing inspectable state. AssetStreams ownership/cache policy and borrowed-consumer release order are unchanged. Scene close still closes all service-owned leases. The existing native probe now performs four actual retries (five real missing-file failures), verifies a new independent lease with no initial diagnostic, checks that immediately previous FAILED survives both request issuance and retry completion, and checks older CLOSED handles leave the private ownership set. Ownership contains exactly current + previous after each retry and is empty on scene close. This uses existing probe reflection, with no new public service API.
2. `LightAuthoringPanel.update` discovers a first Light3D on the same selected object when cached light is null. Existing non-null drafts retain their identity and text across unrelated updates/component insertion. Existing removal/replacement reload semantics remain. `EnvironmentDemo`'s smoke-only path exercises the actual Stage/RuntimeToolsOverlay panel after the preserved captures: select lightless object, add/init/update first light without reselection, verify visible, enter dirty draft, insert unrelated component and second light, update repeatedly, Apply to the original exact light, remove original and Apply remaining light, remove final light/hide/release focus, readd and Apply replacement. It verifies the same panel root survives the sequence. Existing courtyard authoring/Apply/hide-focus proof is retained.
3. `docs/artifacts/voxel-fps/README.md` labels Task 2 captures as historical checkpoint `7a1d475`, links current `../environment/voxel-scene-fps.png` and the environment README, and preserves old captures/metrics. Both relative links resolve. `docs/avatars.md` has no equivalent stale capture wording and was left untouched.

## RED evidence

Tests/probe assertions were added before the production fixes. Command under the environment below:

```sh
./gradlew :javity-desktop:assetStreamingSmoke :javity-desktop:environmentDemoSmoke --continue
```

`/tmp/javity-runtime-final-fix-red.log`: expected **BUILD FAILED**, both regression tasks failed for the reviewed reasons:

- Asset streaming, on the second actual retry: `Retry retained obsolete failed lease ownership`.
- Dynamic authoring: `First added light was not discovered on the same selection`.

An initial sandbox-only attempt could not initialize Gradle's local wildcard IP/socket. The logged RED execution used the authorized sandbox escalation and reached both actual native regression assertions. No display/emulator restart.

## GREEN evidence

```sh
./gradlew :javity-assets:test :javity-ui:test :javity-desktop:assetStreamingSmoke :javity-desktop:environmentDemoSmoke :javity-desktop:inspectorSmoke --continue
```

`/tmp/javity-runtime-final-fix-green.log`: **BUILD SUCCESSFUL in 10s**, 46 tasks (7 executed / 39 up-to-date). All three native gates PASS. `LightAuthoringPanel: PASS dynamic first-light discovery, dirty draft identity, removal/readd and focus` appears explicitly. Existing inspector native selection, gizmo/undo/redo, failure paths, supplier replacement, transform editing, F1/focus, resize and 120-frame smoke also PASS.

Affected retained JUnit XML inventory: **121 tests / 17 suites / zero failures, errors, skips**. UI's 87 tests / 14 suites freshly executed; assets' 34 tests / 3 suites were UP-TO-DATE. This is not a fresh full-reactor claim. Root owns the final fresh full reactor after scoped re-review.

After strengthening the probe to check that previous FAILED also survives each retry's completion, reran only the affected native gate:

```sh
./gradlew :javity-desktop:assetStreamingSmoke
```

`/tmp/javity-runtime-final-fix-retry-green.log`: **BUILD SUCCESSFUL in 2s**, 40 tasks (2 executed / 38 up-to-date). Final generated proof saved separately at `/tmp/javity-runtime-final-fix-streaming-proof.json`: **211 checks**, four failed retries, bounded failure history true; default model and texture dependency each loaded once; 12 buffer / 8 texture creation-deletion totals matched, detached model pixels zero. Existing pressure/cache/default-loader/paused sentinel/close-during-load and native owner-thread cleanup assertions remain active.

Native runs emitted the existing LWJGL `sun.misc.Unsafe::objectFieldOffset` deprecation warning. No new failures were observed. No broad duplicate suites or renderer/sky/cycle/API expansion.

Environment: `JAVA_HOME=/tmp/javity-jdk25/jdk-25.0.4.1+1`, PATH prepended with its bin, `GRADLE_USER_HOME=/workspace/.gradle-cache`, `DISPLAY=:105`, `XDG_RUNTIME_DIR=/tmp/javity-xdg`, `LIBGL_ALWAYS_SOFTWARE=1`, `ALSOFT_DRIVERS=null`.

## Provenance, final checks, and limits

Inspected the generated path inventory, sizes and hashes and copied generated outputs to `/tmp/javity-runtime-final-fix-generated/` before restoring exactly these tracked historical paths:

- `docs/artifacts/asset-streaming/task3-explicit-failure.png`
- `docs/artifacts/asset-streaming/task3-loading.png`
- `docs/artifacts/asset-streaming/task3-one-lease.png`
- `docs/artifacts/asset-streaming/task3-prefetch-cache.png`
- `docs/artifacts/asset-streaming/task3-proof.json`
- `docs/artifacts/asset-streaming/task3-ready-two-leases.png`
- `docs/artifacts/asset-streaming/task3-released-cache.png`
- `docs/demo-runtime-inspector.png`

All voxel-fps historical PNG/JSON blobs are unchanged. Environment captures were unchanged. Final `git diff --check 41518e9..HEAD` passed. Tracked working tree is clean; the sole untracked directory is root-owned `docs/artifacts/runtime-batch/`, left untouched. Root's runtime verification/work queue/decision ledger were not modified or staged. No subagents or push.

Desktop native evidence only. New Android streaming/environment runtime and actual per-feature EGL recovery remain UNVERIFIED. No new Android build, device, cloud runtime or performance claim.
