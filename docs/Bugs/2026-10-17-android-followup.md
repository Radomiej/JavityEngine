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
so traps, pipeline status handling, and cleanup use one Bash process. The workflow has not run yet,
so it adds a reproducible collection path but does not itself close BUG-ANDROID-001.

### Conclusion and closure gate

No app or test source was changed: a source fix would be speculative without the failing runner
trace. Keep BUG-ANDROID-001 open. Dispatch the hosted workflow, inspect its uploaded traces, and
identify which process failed to start or gain focus. Then rerun the same unfiltered suite after any
evidence-backed fix. A green list of separately invoked test methods does not satisfy this gate.
