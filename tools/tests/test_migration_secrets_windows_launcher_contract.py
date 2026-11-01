#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PS1 = ROOT / 'CONFIGURE-MIGRATION-SECRETS.ps1'
BAT = ROOT / 'CONFIGURE-MIGRATION-SECRETS.bat'


def main():
    assert PS1.is_file(), 'missing CONFIGURE-MIGRATION-SECRETS.ps1'
    assert BAT.is_file(), 'missing CONFIGURE-MIGRATION-SECRETS.bat'

    ps1 = PS1.read_text(encoding='utf-8')
    bat = BAT.read_text(encoding='utf-8')

    required_ps1 = [
        "[switch]$ConfigureGitHub",
        "[string]$BundleDir",
        "[string]$OldKeystore",
        "[string]$Repo = 'FuzzsT/ENRROL_NEW'",
        "configure_migration_signing.py",
        "--bundle-dir",
        "--old-keystore",
        "--repo",
        "--configure-github",
        "MIGRATION_LAUNCHER_AUDIT",
        "MIGRATION_LAUNCHER_CONFIGURE",
    ]
    missing = [item for item in required_ps1 if item not in ps1]
    assert not missing, f'missing PowerShell launcher markers: {missing}'

    assert 'workflow run' not in ps1.lower(), 'launcher must not dispatch migration workflow'
    assert 'release create' not in ps1.lower(), 'launcher must not publish releases'
    assert '--configure-github' in ps1, 'configure mode must be explicit'

    assert 'CONFIGURE-MIGRATION-SECRETS.ps1' in bat
    assert 'powershell' in bat.lower()

    print('MIGRATION_SECRETS_WINDOWS_LAUNCHER_CONTRACT: PASS')


if __name__ == '__main__':
    main()
