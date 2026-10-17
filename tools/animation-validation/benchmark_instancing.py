#!/usr/bin/env python3
"""Run the native crowd benchmark, validate measured evidence, and export its provenance.

Requires JDK25 and a real GL display (e.g. DISPLAY=:105 LIBGL_ALWAYS_SOFTWARE=1).
Use --verify-only to inspect an existing export without rerunning the workload.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'docs/artifacts/instancing'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def plain(value):
    # libGDX Json includes runtime class tags for Object-valued maps and boxed scalars.
    if isinstance(value, dict):
        if set(value) == {'class', 'items'}:
            return plain(value['items'])
        if set(value) == {'class', 'value'}:
            return plain(value['value'])
        return {k: plain(v) for k, v in value.items() if k != 'class'}
    if isinstance(value, list):
        return [plain(v) for v in value]
    return value


def verify(provenance=None):
    benchmark = plain(json.loads((OUT / 'benchmark.json').read_text()))
    assert benchmark['status'] == 'PASS'
    assert (benchmark['seed'], benchmark['warmupFrames'], benchmark['measuredFrames']) == (42, 120, 300)
    rows = benchmark['results']
    assert len(rows) == 13, '12 mandatory variants + separate optional CPU cache variant'
    for count in (64, 256, 1024):
        for phase in ('synchronous', 'independent'):
            variants = [r for r in rows if r['count'] == count and r['phase'] == phase and not r['cpuPoseCache']]
            assert len(variants) == 2
            normal = next(r for r in variants if r['mode'] == 'ordinary')
            optimized = next(r for r in variants if r['mode'] == 'instanced')
            assert normal['actualDraws'] == count * 300
            assert optimized['actualDraws'] == ((count + 255) // 256) * 300
            assert normal['fullPoseCompositions'] > 0
            assert normal['fullPoseCompositions'] == optimized['fullPoseCompositions']
            assert normal['events'] == optimized['events'] and len(normal['events']) == count
            assert optimized['uniquePalettesFinalFrame'] == (count if phase == 'independent' else 1)
            assert optimized['paletteUploadBytes'] > 0
            if benchmark['gpuTimerSupported']:
                assert optimized['gpuElapsedMsPerFrame'] > 0
    normal = next(r for r in rows if r['count'] == 256 and r['phase'] == 'synchronous' and r['mode'] == 'instanced' and not r['cpuPoseCache'])
    cached = next(r for r in rows if r['cpuPoseCache'])
    assert cached['fullPoseCompositions'] < normal['fullPoseCompositions']
    assert cached['events'] == normal['events']
    manifest_path = OUT / 'verification.json'
    if provenance is None:
        verification = json.loads(manifest_path.read_text())
        assert verification['benchmarkSha256'] == digest(OUT / 'benchmark.json'), 'benchmark changed since captured run'
        for name, expected in verification['sourceSha256'].items():
            assert digest(ROOT / name) == expected, f'source changed since captured run: {name}'
        for name, expected in verification['fixtureSha256'].items():
            assert digest(ROOT / 'tools/animation-validation/fox' / name) == expected, f'fixture changed: {name}'
        assert verification['workloadClassSha256'] == benchmark['workloadClassSha256'], 'workload class provenance mismatch'
        assert verification['loadedJarSha256'] == benchmark['loadedJarSha256'], 'loaded JAR provenance mismatch'
        assert verification['fixtureSha256']['Fox.g3dj'] == benchmark['fixtureSha256'], 'loaded fixture provenance mismatch'
    else:
        verification = {
            'status': 'PASS',
            'benchmark': 'benchmark.json',
            'benchmarkSha256': digest(OUT / 'benchmark.json'),
            **provenance,
            'workloadClassSha256': benchmark['workloadClassSha256'],
            'loadedJarSha256': benchmark['loadedJarSha256'],
            'sourceNotice': '../../../tools/animation-validation/fox/NOTICE.md',
            'poseAndImageOracle': '../skinned-instancing/README.md',
            'tolerances': {'poseSourceUnits': .01, 'interiorRgb8': 2, 'silhouetteBandPixels': 1},
            'limits': ['Mesa llvmpipe timings describe this run only', 'CPU full composition count excludes scalar channel interpolation and callback contribution costs', 'Physical ARM64 and Android device performance remain unverified'],
        }
        manifest_path.write_text(json.dumps(verification, indent=2) + '\n')
    print('instancing evidence: PASS,13 variants, actual native draws/compositions/uploads/events verified')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--verify-only', action='store_true')
    parser.add_argument('--environment', default='unspecified co-resident workloads', help='Describe other workloads during this run')
    args = parser.parse_args()
    if args.verify_only:
        verify()  # read-only: never relabel old evidence with current sources
        return
    fixtures = ROOT / 'tools/animation-validation/fox'
    sources = [ROOT / 'javity-desktop/src/main/java/org/javity/demo/InstancingBenchmark.java',
               ROOT / 'javity-desktop/src/main/java/org/javity/demo/InstancingBenchmarkLauncher.java',
               ROOT / 'javity-core/src/main/java/org/javity/render/InstanceShader.java',
               Path(__file__).resolve()]
    captured = {str(p.relative_to(ROOT)): digest(p) for p in sources}
    fixture_hashes = {p.name: digest(p) for p in fixtures.iterdir() if p.is_file()}
    commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    dirty = bool(subprocess.check_output(['git', 'status', '--porcelain', '--', *captured], cwd=ROOT, text=True).strip())
    subprocess.run(['./gradlew', ':javity-desktop:instancingBenchmark', '-PbenchmarkEnvironment=' + args.environment], cwd=ROOT, check=True)
    assert captured == {str(p.relative_to(ROOT)): digest(p) for p in sources}, 'sources changed while benchmark ran'
    assert fixture_hashes == {p.name: digest(p) for p in fixtures.iterdir() if p.is_file()}, 'fixture changed while benchmark ran'
    verify({'sourceCommitAtRun': commit, 'sourceWorktreeDirtyAtRun': dirty,
            'provenanceScope': 'Checkout commit plus listed source hashes, including uncommitted source changes; actual loaded class/JAR hashes identify the executed bytecode',
            'sourceSha256': captured, 'fixtureSha256': fixture_hashes})


if __name__ == '__main__':
    main()
