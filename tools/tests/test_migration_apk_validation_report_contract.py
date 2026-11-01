#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TOOL = ROOT / 'tools' / 'release' / 'validate_migration_apk.py'


def main():
    assert TOOL.is_file(), 'missing migration APK validation report tool'
    src = TOOL.read_text(encoding='utf-8')

    required = [
        '--apk',
        '--signing-metadata',
        '--report',
        'apksigner',
        'verify',
        '--verbose',
        '--print-certs',
        'apkSha256',
        'rotationMinSdkVersion',
        'oldCertificateSha256',
        'newCertificateSha256',
        'MIGRATION_VALIDATION_PASS',
        'MIGRATION_APK_SHA256_MISMATCH',
        'MIGRATION_METADATA_INVALID',
        'schemaVersion',
    ]
    missing = [item for item in required if item not in src]
    assert not missing, f'missing validation markers: {missing}'

    lowered = src.lower()
    forbidden = [
        'gh secret set',
        'workflow run',
        'release create',
        '--ks-pass',
        '--key-pass',
    ]
    bad = [item for item in forbidden if item in lowered]
    assert not bad, f'validator must remain read-only: {bad}'

    print('MIGRATION_APK_VALIDATION_REPORT_CONTRACT: PASS')


if __name__ == '__main__':
    main()
