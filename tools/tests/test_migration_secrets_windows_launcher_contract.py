#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PS1 = ROOT / 'AUDIT-MIGRATION-SECRETS.ps1'
BAT = ROOT / 'AUDIT-MIGRATION-SECRETS.bat'


def main():
    assert PS1.is_file(), 'missing AUDIT-MIGRATION-SECRETS.ps1'
    assert BAT.is_file(), 'missing AUDIT-MIGRATION-SECRETS.bat'

    ps1 = PS1.read_text(encoding='utf-8')
    bat = BAT.read_text(encoding='utf-8')

    required_ps1 = [
        "[string]$BundleDir",
        "[string]$OldKeystore",
        "[string]$Repo = 'FuzzsT/ENRROL_NEW'",
        "configure_migration_signing.py",
        "--bundle-dir",
        "--old-keystore",
        "--repo",
        "MIGRATION_LAUNCHER_AUDIT",
    ]
    missing = [item for item in required_ps1 if item not in ps1]
    assert not missing, f'missing PowerShell launcher markers: {missing}'

    forbidden = ['--configure-github', 'workflow run', 'release create', 'secret set']
    for marker in forbidden:
        assert marker not in ps1.lower(), f'audit launcher contains forbidden marker: {marker}'

    assert 'AUDIT-MIGRATION-SECRETS.ps1' in bat
    assert 'powershell' in bat.lower()

    print('MIGRATION_SECRETS_WINDOWS_LAUNCHER_CONTRACT: PASS')


if __name__ == '__main__':
    main()
