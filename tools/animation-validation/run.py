"""Build and run the external-asset animation proof with the project's actual desktop jars."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--interactive', action='store_true', help='Open the keyboard-controlled Fox viewer')
parser.add_argument('--gl30', action='store_true', help='Request a real desktop GL30 core profile')
parser.add_argument('--skip-build', action='store_true', help='Use an existing installDist explicitly')
args = parser.parse_args()
if not args.skip_build:
    subprocess.run([str(root/'gradlew'), ':javity-desktop:installDist'], cwd=root, check=True)
java_root = os.environ.get('JAVA_HOME')
java = str(Path(java_root)/'bin/java') if java_root else 'java'
javac = str(Path(java_root)/'bin/javac') if java_root else 'javac'
classes = root/'tools/animation-validation/.generated/classes'
classes.mkdir(parents=True, exist_ok=True)
libs = root/'javity-desktop/build/install/javity-desktop/lib'
if not libs.is_dir():
    raise SystemExit('Missing installDist: run without --skip-build')
subprocess.run([javac, '-cp', str(libs/'*'), '-d', str(classes),
                str(root/'tools/animation-validation/ImportedAnimationProbe.java')], check=True)
output = root/'docs/artifacts/imported-animation'/('gl30' if args.gl30 else 'gl20')
if args.interactive:
    output = root/'tools/animation-validation/.generated/interactive'
output.mkdir(parents=True, exist_ok=True)
cmd = [java, '--enable-native-access=ALL-UNNAMED', '-cp', os.pathsep.join([str(classes), str(libs/'*')]),
       'ImportedAnimationProbe', str(root/'tools/animation-validation/fox'), str(output)]
if args.gl30:
    cmd.append('--gl30')
if args.interactive:
    cmd.append('--interactive')
subprocess.run(cmd, cwd=root, check=True)
result = json.loads((output/'verification.json').read_text())
if result.get('result') != 'PASS' or not result.get('resourcesClosed'):
    raise SystemExit('Probe did not finish successfully: '+str(result))
result['fixtureRevision'] = (root/'tools/animation-validation/fox/revision.txt').read_text().strip()
result['harnessSha256'] = hashlib.sha256((root/'tools/animation-validation/ImportedAnimationProbe.java').read_bytes()).hexdigest()
result['g3djSha256'] = hashlib.sha256((root/'tools/animation-validation/fox/Fox.g3dj').read_bytes()).hexdigest()
result['productionCheckoutCommit'] = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
(output/'verification.json').write_text(json.dumps(result, indent=2)+'\n')
print('Imported animation verification: PASS; report:', output/'verification.json')
