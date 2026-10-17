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

print_failure_summary() {
  python3 - "$repo_root" "$artifact_dir" <<'PY'
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

repo = Path(sys.argv[1])
artifacts = Path(sys.argv[2])
result_root = repo / "javity-android-app/build/outputs/androidTest-results"
xml_files = sorted(result_root.rglob("*.xml")) if result_root.exists() else []
print("\n===== Android instrumentation result summary =====")
if not xml_files:
    print(f"No JUnit XML files found under {result_root}")
else:
    cases = []
    for xml_file in xml_files:
        try:
            cases.extend(ET.parse(xml_file).getroot().iter("testcase"))
        except (ET.ParseError, OSError) as error:
            print(f"Could not parse {xml_file.relative_to(repo)}: {error}")
    failures = []
    skipped = 0
    for case in cases:
        if case.find("skipped") is not None:
            skipped += 1
        failure = case.find("failure")
        if failure is None:
            failure = case.find("error")
        if failure is not None:
            failures.append((case, failure))
    print(f"cases={len(cases)} failures={len(failures)} skipped={skipped} passed={len(cases)-len(failures)-skipped}")
    for case, failure in failures[:8]:
        identifier = ".".join(filter(None, (case.get("classname"), case.get("name"))))
        detail = "\n".join(filter(None, (failure.get("message"), (failure.text or "").strip())))
        detail = detail[:2800]
        print(f"\nFAILED {identifier}\n{detail or '(no failure detail in XML)'}")
    if len(failures) > 8:
        print(f"\n... {len(failures)-8} additional failed cases omitted")

logcat = artifacts / "logcat.txt"
pattern = re.compile(r"ANR|not responding|FATAL EXCEPTION|AndroidRuntime|INSTRUMENTATION|TestRunner|ActivityTaskManager|ActivityManager|am_anr|Process .* died|Javity|Exception", re.I)
print("\n===== Recent relevant logcat =====")
if logcat.exists():
    matches = [line.rstrip() for line in logcat.read_text(errors="replace").splitlines() if pattern.search(line)]
    selected = matches[-200:]
    output = []
    remaining = 18000
    for line in reversed(selected):
        clipped = line[:1000]
        if len(clipped) + 1 > remaining:
            break
        output.append(clipped)
        remaining -= len(clipped) + 1
    print("\n".join(reversed(output)) if output else "No matching lines in captured logcat")
    if len(output) < len(selected):
        print(f"(showing {len(output)} of the last {len(selected)} matching lines)")
else:
    print("No logcat file was captured")

anr = artifacts / "last-anr.txt"
print("\n===== Last ANR diagnostic =====")
if anr.exists():
    lines = anr.read_text(errors="replace").splitlines()
    for line in lines[:70]:
        print(line[:400])
    if len(lines) > 70:
        print(f"... {len(lines)-70} additional ANR lines omitted")
else:
    print("No last-ANR file was captured")
PY
}

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
  print_failure_summary
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
