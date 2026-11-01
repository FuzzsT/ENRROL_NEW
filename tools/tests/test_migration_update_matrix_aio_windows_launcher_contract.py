#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PS1 = ROOT / 'MIGRATION-UPDATE-MATRIX-AIO.ps1'
BAT = ROOT / 'MIGRATION-UPDATE-MATRIX-AIO.bat'


def main():
    assert PS1.is_file(), 'missing MIGRATION-UPDATE-MATRIX-AIO.ps1'
    assert BAT.is_file(), 'missing MIGRATION-UPDATE-MATRIX-AIO.bat'

    ps1 = PS1.read_text(encoding='utf-8')
    bat = BAT.read_text(encoding='utf-8')

    required_ps1 = [
        '[string]$ReportLe27',
        '[string]$Report28_32',
        '[string]$Report33Plus',
        "[string]$Repo = 'FuzzsT/ENRROL_NEW'",
        "[string]$Ref = 'main'",
        '[switch]$Dispatch',
        '[switch]$Fetch',
        '[long]$RunId',
        '[string]$OutputDir',
        '[switch]$NoWait',
        'MIGRATION-UPDATE-MATRIX.ps1',
        'WAIT-DOWNLOAD-MIGRATION-MATRIX.ps1',
        'MIGRATION_UPDATE_MATRIX_AIO_WINDOWS_LAUNCHER',
    ]
    missing = [item for item in required_ps1 if item not in ps1]
    assert not missing, f'missing AIO PowerShell markers: {missing}'

    required_bat = [
        'MIGRATION-UPDATE-MATRIX-AIO.ps1',
        '%*',
        'exit /b %ERRORLEVEL%',
    ]
    missing_bat = [item for item in required_bat if item not in bat]
    assert not missing_bat, f'missing AIO BAT markers: {missing_bat}'

    # AIO may orchestrate the two trusted wrappers, but must not bypass them.
    forbidden = [
        'gh run',
        'workflow run',
        'adb ',
        'install -r',
        'release create',
        'secret set',
        'fetch_migration_update_matrix_artifact.py',
        'dispatch_migration_update_matrix.py',
    ]
    lower = ps1.lower() + '\n' + bat.lower()
    for marker in forbidden:
        assert marker not in lower, f'AIO launcher contains forbidden direct marker: {marker}'

    print('MIGRATION_UPDATE_MATRIX_AIO_WINDOWS_LAUNCHER_CONTRACT: PASS')


if __name__ == '__main__':
    main()
