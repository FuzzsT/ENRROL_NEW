#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PS1 = ROOT / 'MIGRATION-UPDATE-MATRIX.ps1'
BAT = ROOT / 'MIGRATION-UPDATE-MATRIX.bat'


def main():
    assert PS1.is_file(), 'missing MIGRATION-UPDATE-MATRIX.ps1'
    assert BAT.is_file(), 'missing MIGRATION-UPDATE-MATRIX.bat'

    ps1 = PS1.read_text(encoding='utf-8')
    bat = BAT.read_text(encoding='utf-8')

    required_ps1 = [
        '[string]$ReportLe27',
        '[string]$Report28_32',
        '[string]$Report33Plus',
        "[string]$Repo = 'FuzzsT/ENRROL_NEW'",
        "[string]$Ref = 'main'",
        '[switch]$Dispatch',
        'dispatch_migration_update_matrix.py',
        '--report-le27',
        '--report-28-32',
        '--report-33-plus',
        '--repo',
        '--ref',
        'MIGRATION_UPDATE_MATRIX_WINDOWS_LAUNCHER',
    ]
    missing = [item for item in required_ps1 if item not in ps1]
    assert not missing, f'missing PowerShell launcher markers: {missing}'

    assert "if ($Dispatch)" in ps1, 'dispatch must require explicit switch'
    assert "$argsList += '--dispatch'" in ps1, 'dispatch switch must be forwarded explicitly'

    forbidden = ['gh workflow run', 'gh release create', 'adb install', 'install -r']
    for marker in forbidden:
        assert marker not in ps1.lower(), f'launcher bypasses helper or performs forbidden action: {marker}'

    assert 'MIGRATION-UPDATE-MATRIX.ps1' in bat
    assert 'powershell' in bat.lower()
    assert '%*' in bat, 'batch launcher must forward user arguments'

    print('MIGRATION_UPDATE_MATRIX_WINDOWS_LAUNCHER_CONTRACT: PASS')


if __name__ == '__main__':
    main()
