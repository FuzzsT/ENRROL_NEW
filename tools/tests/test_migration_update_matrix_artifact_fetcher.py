#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TOOL = ROOT / 'tools' / 'release' / 'fetch_migration_update_matrix_artifact.py'


def main():
    assert TOOL.is_file(), 'missing migration update matrix artifact fetcher'
    text = TOOL.read_text(encoding='utf-8')

    required = [
        'migration-update-matrix.yml',
        'migration-update-matrix-report',
        '--repo',
        '--run-id',
        '--output-dir',
        'gh',
        'run',
        'view',
        'run',
        'download',
        'MIGRATION_UPDATE_MATRIX_ARTIFACT_READY',
    ]
    missing = [item for item in required if item not in text]
    assert not missing, f'missing artifact fetcher markers: {missing}'

    forbidden = [
        'workflow run',
        'adb ',
        'install -r',
        'release create',
        'secret set',
    ]
    lower = text.lower()
    for marker in forbidden:
        assert marker not in lower, f'artifact fetcher contains forbidden marker: {marker}'

    print('MIGRATION_UPDATE_MATRIX_ARTIFACT_FETCHER: PASS')


if __name__ == '__main__':
    main()
