#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
EXPECTED = '1.1.4'
MINIMUM_CODE = 25

def read(path: str) -> str:
    return (ROOT / path).read_text('utf-8', errors='strict')

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
    guard = workflow.index('      - name: Guard continuous release progression')
    publish = workflow.index('      - name: Publish release assets')
    assert guard < publish, 'validate published release progression before mutating assets'
    for token in ['CONTINUOUS_RELEASE_ROLLBACK', 'CONTINUOUS_RELEASE_VERSION_REUSE', 'CONTINUOUS_RELEASE_DIGEST_MISMATCH']:
        assert token in workflow, f'missing release guard: {token}'
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
