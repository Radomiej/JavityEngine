# Android instrumentation runner follow-up — 2026-10-17

## BUG-ANDROID-001 / P2 — combined instrumentation startup ANR

**Status: open; this checkout could not reproduce the failure.** The available evidence is
insufficient to attribute the recorded system startup timeout to app code or to make a safe
source change. Do not count isolated method passes as a full runner pass.

### Evidence inspected

- `javity-android-app/src/androidTest` contains five instrumentation methods across three test
  classes. The archived [verification inventory](../artifacts/android/app-verification.json)
  records five individually passing API 35 x86_64 TCG runs on the final APK: touch/resource
  recreation, landscape, foreground Activity recreation, paused Activity recreation, and actual
  EGL context loss.
- The same inventory describes the unfiltered suite failure as a **system process-start
  timeout** and the two-test Activity attempt as a **focus timeout**. Each Activity scenario passed
  independently. It contains no combined-runner output, logcat, ANR trace, or per-test progress
  showing where the unfiltered run stopped.
- The archived instrumentation outputs for the five passing cases end in `OK (1 test)`. They do
  not contain evidence about combined discovery, process startup, or test ordering.
- `JavityGameActivity.onCreate` initializes the Yoga Android backend and hands the fixture to
  libGDX; fixture rendering and resource setup then run on libGDX's GL thread. The activity does
  not contain a synchronous wait for fixture readiness in `onCreate`. Existing instrumentation
  readiness waits are bounded and happen in the test process. This source inspection alone does
  not establish the cause of the system timeout.

### Reproduction attempt in this workspace

The container has no `ANDROID_*` environment variables and `adb` is not installed or on `PATH`
(`adb: command not found`). `/workspace/.javity-android-runtime` contains an API 35 AVD data
directory but no `adb` or emulator executable; no Android SDK was present in the conventional
locations checked. Therefore neither `connectedDebugAndroidTest` nor an adb-level reproduction
could be run here.

### Hosted reproduction gate

Added [Android full instrumentation](../../.github/workflows/android-runtime.yml), a
`workflow_dispatch`-only workflow on `ubuntu-24.04`. It installs compile SDK 36/build-tools 36,
builds the debug app and test APKs once, then invokes the unfiltered
`:javity-android-app:connectedDebugAndroidTest` task once on an API 35 Google APIs x86_64 Pixel 2
emulator. The hosted runner supplies hardware acceleration. The emulator display density is set
to 320 to match the final individual flows. The job is capped at 30 minutes. It does not split
methods, override runner timeouts, or turn a timeout into success.

The workflow uploads Gradle's connected instrumentation XML/reports, the complete test command
output, device/display properties, logcat, `dumpsys activity lastanr`, Activity process state, and
the `data_app_anr` DropBox diagnostic after the test step exits, including a failing test result.
These artifacts should identify whether process startup, focus acquisition, or an app test body is
the first failure. The emulator action invokes its script through `sh`; the workflow therefore
calls the single standalone Bash entry point [android-full-suite.sh](../../tools/android-full-suite.sh)
so traps, pipeline status handling, and cleanup use one Bash process. The workflow was run on the merged tree; see the result below. It does not itself close BUG-ANDROID-001.

### Conclusion and closure gate

No app or test source was changed: a source fix would be speculative without the failing runner
trace. Keep BUG-ANDROID-001 open. Dispatch the hosted workflow, inspect its uploaded traces, and
identify which process failed to start or gain focus. Then rerun the same unfiltered suite after any
evidence-backed fix. A green list of separately invoked test methods does not satisfy this gate.

## Hosted attempt on the merged tree

[Run39043544780](https://github.com/Radomiej/JavityEngine/actions/runs/39043544780), source `c954815`, successfully installed SDK, built APKs, enabled KVM and booted the API35 emulator. The unfiltered connected task actually ran but failed after about2m43s. Its log only reported “There were failing tests”; this is not enough to classify it as another ANR. Artifact download through the current identity returned403 at the signed artifact endpoint, so no test-case conclusion is inferred from unavailable XML.

The harness now prints bounded JUnit failure summaries, relevant logcat and last-ANR text directly into the job log while preserving the original test exit code. The success/failure harness checks passed with fakes. A second full hosted attempt uses this diagnostic version; its outcome must be inspected before assigning a source fix or closing this issue. SDK build jobs themselves passed: [branch](https://github.com/Radomiej/JavityEngine/actions/runs/39043396417) and [master](https://github.com/Radomiej/JavityEngine/actions/runs/39043514515).

## Second run diagnosis and test-harness changes

[Run39044153545](https://github.com/Radomiej/JavityEngine/actions/runs/39044153545), source `c4efca1`, reported5 cases:3 passed and2 failed. Touch/resource lifecycle failed at `Instrumentation.sendPointerSync` with a targeted-input-window exception. The compact-layout test timed out waiting for logical height<360. The recorded ANR says the application had no focused window and names Launcher as top-resumed; this does not alone prove an application-code cause or that one failure caused the other.

The display assumption was independently invalid: Pixel2 default1080x1920 at density320 produces logical960 portrait/540 landscape; the test’s compact predicate cannot be reached. The harness now pins640x1120/density320 and asserts both overrides, yielding560/320 logical heights without weakening the test. Touches now go through UiAutomation with explicit Activity/decor focus, visibility/lifecycle and per-pointer bounds checks plus injection success. Existing gameplay/input/pixel assertions remain. These are test-harness changes, not a claimed engine-side fix. A third unfiltered hosted run determines the result.

## Third hosted result

[Run39044986493](https://github.com/Radomiej/JavityEngine/actions/runs/39044986493), source `29e95e2`, reported5 cases:4 passed,1 failed, no skipped cases and no ANR since boot. The compact-viewport test passes with the pinned display, confirming the display-assumption fix. The only failure is the first packaged-runtime touch: Activity/decor is visible and attached, not finishing/destroyed, but has no window focus. The focus guard correctly refuses input instead of targeting another window.

A focus-readiness wait now shares the existing60-second startup deadline; it does not increase the timeout or weaken the injection guard. Fresh-emulator system onboarding/immersive confirmation is being investigated separately. Until the same full suite passes, BUG-ANDROID-001 remains open.

## Fourth attempt — focus and fresh-emulator onboarding

Source `5d2a206` retains the existing60-second startup deadline and all input assertions, but waits for the Activity/decor to actually own focus. The disposable-emulator runner sets and reads back `settings put secure immersive_mode_confirmations confirmed` before the app starts. AOSP's immersive confirmation loader recognizes the exact value `confirmed`; this removes first-use system onboarding as a test precondition, without changing app behavior. Failure diagnostics include only bounded focused/obscuring-window fields.

Hosted [run39046532187](https://github.com/Radomiej/JavityEngine/actions/runs/39046532187) is the verification gate. Fake-adb success and failure checks passed, including the setting readback and original Gradle exit code37. These do not establish the hosted runtime result or prove the cause of the historical TCG ANR.
