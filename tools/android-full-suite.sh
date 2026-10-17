#!/usr/bin/env bash
set -euo pipefail

repo_root=$(git rev-parse --show-toplevel)
cd "$repo_root"

artifact_dir=artifacts/android-full-suite
mkdir -p "$artifact_dir"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is required to capture emulator diagnostics" >&2
  exit 127
fi

logcat_pid=

collect_diagnostics() {
  local status=$?
  trap - EXIT
  set +e
  adb shell dumpsys activity lastanr > "$artifact_dir/last-anr.txt" 2>&1
  adb shell dumpsys dropbox --print data_app_anr > "$artifact_dir/data-app-anr-dropbox.txt" 2>&1
  adb shell dumpsys activity processes > "$artifact_dir/activity-processes.txt" 2>&1
  if [[ -n "$logcat_pid" ]]; then
    kill "$logcat_pid" 2>/dev/null
    wait "$logcat_pid" 2>/dev/null
  fi
  return "$status"
}
trap collect_diagnostics EXIT

adb logcat -c
adb shell wm density 320
adb shell svc power stayon true
adb shell getprop > "$artifact_dir/device-properties.txt"
adb shell wm size > "$artifact_dir/display-size.txt"
adb shell wm density > "$artifact_dir/display-density.txt"
adb logcat -v threadtime > "$artifact_dir/logcat.txt" 2>&1 &
logcat_pid=$!

set +e
./gradlew --no-daemon --console=plain -PjavityAndroid=true \
  :javity-android-app:connectedDebugAndroidTest \
  2>&1 | tee "$artifact_dir/connectedDebugAndroidTest.log"
pipeline_status=("${PIPESTATUS[@]}")
set -e

gradle_status=${pipeline_status[0]}
tee_status=${pipeline_status[1]}
if (( gradle_status != 0 )); then
  exit "$gradle_status"
fi
exit "$tee_status"
