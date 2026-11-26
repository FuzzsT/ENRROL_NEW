#!/usr/bin/env python3
from pathlib import Path
import re
import hashlib
import json
import subprocess
import sys
import tempfile
import textwrap

ROOT = Path(__file__).resolve().parents[2]
EXPECTED = '1.3.1'
MINIMUM_CODE = 274

def read(path: str) -> str:
    return (ROOT / path).read_text('utf-8', errors='strict')

def test_progression_guard(workflow: str) -> None:
    guard = workflow.split('      - name: Guard continuous release progression', 1)[1]
    guard = guard.split('      - name: Publish release assets', 1)[0]
    script = guard.split("<<'PY'\n", 1)[1].split('\n          PY', 1)[0]
    script = textwrap.dedent(script)
    with tempfile.TemporaryDirectory() as temp:
        root = Path(temp)
        apk = root / 'DPC-AIO-enterprise-release.apk'
        old_bytes = b'old release fixture'
        new_bytes = b'new release fixture'
        old_sha = hashlib.sha256(old_bytes).hexdigest()
        def manifest(code, data):
            return {'schemaVersion': 1, 'channel': 'continuous', 'packageName': 'io.dpcaio.app',
                    'versionCode': code, 'apkSha256': hashlib.sha256(data).hexdigest(),
                    'apkSize': len(data)}
        def case(code, data, expected, remote_sha=old_sha, corrupt_candidate=False):
            apk.write_bytes(data)
            release = {'assets': [{'name': apk.name, 'digest': 'sha256:' + remote_sha}]}
            candidate = manifest(code, b'wrong data' if corrupt_candidate else data)
            paths = [root / 'release.json', root / 'old.json', root / 'new.json']
            for path, obj in zip(paths, [release, manifest(27, old_bytes), candidate]):
                path.write_text(json.dumps(obj), encoding='utf-8')
            result = subprocess.run([sys.executable, '-c', script, *map(str, paths), str(apk)],
                                    capture_output=True, text=True, check=False)
            output = result.stdout + result.stderr
            assert expected in output, output
            assert (result.returncode == 0) == (expected == 'CONTINUOUS_RELEASE_PROGRESSION_OK'), output
        case(28, new_bytes, 'CONTINUOUS_RELEASE_PROGRESSION_OK')
        case(27, old_bytes, 'CONTINUOUS_RELEASE_PROGRESSION_OK')
        case(26, new_bytes, 'CONTINUOUS_RELEASE_ROLLBACK')
        case(27, new_bytes, 'CONTINUOUS_RELEASE_VERSION_REUSE')
        case(28, new_bytes, 'CONTINUOUS_RELEASE_DIGEST_MISMATCH', remote_sha='0' * 64)
        case(28, new_bytes, 'CONTINUOUS_RELEASE_DIGEST_MISMATCH', corrupt_candidate=True)

def main() -> None:
    gradle = read('apps/dpc/app/build.gradle.kts')
    version_name = re.search(r'versionName\s*=\s*"([^"]+)"', gradle)
    version_code = re.search(r'versionCode\s*=\s*(\d+)', gradle)
    if not version_name or version_name.group(1) != EXPECTED:
        raise AssertionError(f'versionName must be {EXPECTED}, got {version_name.group(1) if version_name else None}')
    if not version_code or int(version_code.group(1)) < MINIMUM_CODE:
        raise AssertionError(f'versionCode must be >= {MINIMUM_CODE}, got {version_code.group(1) if version_code else None}')
    if 'DPC_AIO_CI_VERSION_CODE' not in gradle or 'versionCode = parsed' not in gradle:
        raise AssertionError('Gradle must accept the CI release version code')
    workflow = read('.github/workflows/build-aio-enrollment.yml')
    for marker in ['export DPC_AIO_CI_VERSION_CODE=', 'GITHUB_RUN_NUMBER', 'CI_VERSION_CODE_MISMATCH']:
        if marker not in workflow:
            raise AssertionError(f'release workflow missing {marker}')
    assert workflow.count('274 + GITHUB_RUN_NUMBER') == 2, 'build and manifest versionCode must share the release floor'
    assert '1.1.4' not in workflow, 'active release workflow must not advertise a stale version'
    guard = workflow.index('      - name: Guard continuous release progression')
    publish = workflow.index('      - name: Publish release assets')
    assert guard < publish, 'validate published release progression before mutating assets'
    for token in ['CONTINUOUS_RELEASE_ROLLBACK', 'CONTINUOUS_RELEASE_VERSION_REUSE', 'CONTINUOUS_RELEASE_DIGEST_MISMATCH']:
        assert token in workflow, f'missing release guard: {token}'
    test_progression_guard(workflow)
    dashboard = read('apps/dpc/app/src/main/kotlin/io/dpcaio/app/AioDashboardActivity.kt')
    for marker in ['packageManager.getPackageInfo(packageName, 0).versionName', 'v$versionName • Device Owner / policy / research toolkit']:
        if marker not in dashboard:
            raise AssertionError(f'dashboard must render package version dynamically: missing {marker}')
    for path in ['docs/releases/PACKAGE-INFO.txt', 'docs/publishing/GITHUB-READY.md', 'docs/history/checkpoints/CHECKPOINT.md']:
        if EXPECTED not in read(path):
            raise AssertionError(f'{path} does not identify release {EXPECTED}')
    print('test_release_version_contract: PASS')

if __name__ == '__main__':
    main()
