#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
EXPECTED = '1.2.0'
EXPECTED_CODE = '26'

def read(path: str) -> str:
    return (ROOT / path).read_text('utf-8', errors='strict')

def main() -> None:
    gradle = read('apps/dpc/app/build.gradle.kts')
    version_name = re.search(r'versionName\s*=\s*"([^"]+)"', gradle)
    version_code = re.search(r'versionCode\s*=\s*(\d+)', gradle)
    if not version_name or version_name.group(1) != EXPECTED:
        raise AssertionError(f'versionName must be {EXPECTED}, got {version_name.group(1) if version_name else None}')
    if not version_code or version_code.group(1) != EXPECTED_CODE:
        raise AssertionError(f'versionCode must be {EXPECTED_CODE}, got {version_code.group(1) if version_code else None}')
    dashboard = read('apps/dpc/app/src/main/kotlin/io/dpcaio/app/AioDashboardActivity.kt')
    for marker in ['packageManager.getPackageInfo(packageName, 0).versionName', 'DPC-AIO $versionName']:
        if marker not in dashboard:
            raise AssertionError(f'dashboard must render package version dynamically: missing {marker}')
    for path in ['docs/releases/PACKAGE-INFO.txt', 'docs/publishing/GITHUB-READY.md', 'docs/history/checkpoints/CHECKPOINT.md']:
        if EXPECTED not in read(path):
            raise AssertionError(f'{path} does not identify release {EXPECTED}')
    print('test_release_version_contract: PASS')

if __name__ == '__main__':
    main()
