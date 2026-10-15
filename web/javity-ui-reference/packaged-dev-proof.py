"""Exercise the installed native MM host using the actual compiled development bundle."""
import argparse
import json
import os
from pathlib import Path
import queue
import shutil
import subprocess
import tempfile
import threading
import time

parser = argparse.ArgumentParser()
parser.add_argument('--launcher', required=True, type=Path)
parser.add_argument('--bundle', required=True, type=Path)
parser.add_argument('--artifacts', required=True, type=Path)
args = parser.parse_args()
launcher = args.launcher.resolve()
original = args.bundle.resolve().read_text()
if 'Health: ' not in original:
    raise SystemExit('Expected actual dev.tsx Health label in the compiled bundle')
modified = original.replace('Health: ', 'Reloaded health: ', 1)
artifacts = args.artifacts.resolve()
artifacts.mkdir(parents=True, exist_ok=True)
working = Path(tempfile.mkdtemp(prefix='javity-packaged-dev-'))
bundle = working / 'hud.js'
bundle.write_text(original)
process = subprocess.Popen([str(launcher), '--mm', '--smoke', '--ui-dev=' + str(bundle)],
                           cwd=working, env=os.environ.copy(), stdout=subprocess.PIPE,
                           stderr=subprocess.STDOUT, text=True, bufsize=1)
lines = queue.Queue()
def read_output():
    for line in process.stdout:
        lines.put(line)
    lines.put(None)
threading.Thread(target=read_output, daemon=True).start()
records = []
phase = 0
deadline = time.monotonic() + 120
try:
    with (artifacts / 'packaged-dev.log').open('w') as log:
        while time.monotonic() < deadline:
            try:
                line = lines.get(timeout=1)
            except queue.Empty:
                if process.poll() is not None:
                    break
                continue
            if line is None:
                break
            log.write(line)
            log.flush()
            if not line.startswith('MM_UI_DEV '):
                continue
            record = json.loads(line[len('MM_UI_DEV '):])
            records.append(record)
            if phase == 0 and record['poll'] == 'RELOADED':
                assert record['rootAfter'] != 'none', record
                assert 'Health: ' in record['renderedHealth'], record
                bundle.write_text(modified)
                phase = 1
            elif phase == 1 and record['poll'] == 'RELOADED':
                assert 'Reloaded health: ' in record['renderedHealth'], record
                bundle.write_text(modified + '\nfunction {\n')
                phase = 2
            elif phase == 2 and record['poll'] == 'ERROR':
                assert record['retainedRoot'] and record['rootBefore'] != 'none', record
                assert record['rootBefore'] == record['rootAfter'], record
                assert 'Reloaded health: ' in record['renderedHealth'], record
                bundle.write_text(original)
                phase = 3
            elif phase == 3 and record['poll'] == 'RELOADED':
                assert 'Health: ' in record['renderedHealth'], record
                assert 'Reloaded health: ' not in record['renderedHealth'], record
                phase = 4
        code = process.wait(timeout=max(1, deadline - time.monotonic()))
    assert code == 0, 'Installed app failed; inspect packaged-dev.log'
    assert phase == 4, 'Missing valid reload / syntax retention / recovery proof'
    native_report = working / 'artifacts/mm-systems/native-smoke.json'
    assert native_report.is_file(), 'Native MM proof report missing'
    native = json.loads(native_report.read_text())
    assert native['status'] == 'PASS', native
    assert native['signals']['sceneGeneration'] >= 2, native
    result = {'status': 'PASS', 'workingDirectory': str(working),
              'outsideCheckout': True, 'events': records, 'nativeSmoke': native}
    (artifacts / 'packaged-dev-proof.json').write_text(json.dumps(result, indent=2) + '\n')
    shutil.copyfile(native_report, artifacts / 'packaged-dev-native-smoke.json')
    print(json.dumps({'status': 'PASS', 'events': len(records), 'phase': phase,
                      'sceneGeneration': native['signals']['sceneGeneration']}))
finally:
    if process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
