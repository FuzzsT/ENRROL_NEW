#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PS1 = ROOT / 'WAIT-DOWNLOAD-MIGRATION-MATRIX.ps1'
BAT = ROOT / 'WAIT-DOWNLOAD-MIGRATION-MATRIX.bat'


def main():
    assert PS1.is_file(), 'missing WAIT-DOWNLOAD-MIGRATION-MATRIX.ps1'
    assert BAT.is_file(), 'missing WAIT-DOWNLOAD-MIGRATION-MATRIX.bat'

    ps1 = PS1.read_text(encoding='utf-8')
    bat = BAT.read_text(encoding='utf-8')

    required_ps1 = [
        '[long]$RunId',
        '[string]$OutputDir',
        "[string]$Repo = 'FuzzsT/ENRROL_NEW'",
        '[switch]$NoWait',
        'fetch_migration_update_matrix_artifact.py',
        '--repo',
        '--run-id',
        '--output-dir',
        '--no-wait',
        'MIGRATION_UPDATE_MATRIX_ARTIFACT_WINDOWS_LAUNCHER',
    ]
    missing = [item for item in required_ps1 if item not in ps1]
    assert not missing, f'missing PowerShell launcher markers: {missing}'

    required_bat = [
        'WAIT-DOWNLOAD-MIGRATION-MATRIX.ps1',
        '%*',
        'exit /b %ERRORLEVEL%',
    ]
    missing_bat = [item for item in required_bat if item not in bat]
    assert not missing_bat, f'missing BAT launcher markers: {missing_bat}'

    forbidden = [
        'gh run',
        'workflow run',
        'adb ',
        'install -r',
        'release create',
        'secret set',
    ]
    lower = ps1.lower() + '\n' + bat.lower()
    for marker in forbidden:
        assert marker not in lower, f'Windows artifact launcher contains forbidden marker: {marker}'

    print('MIGRATION_UPDATE_MATRIX_ARTIFACT_WINDOWS_LAUNCHER_CONTRACT: PASS')


if __name__ == '__main__':
    main()
