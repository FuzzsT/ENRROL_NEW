from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ps1 = (ROOT / 'RECOVER-STABLE-SIGNER.ps1').read_text(encoding='utf-8')
bat = (ROOT / 'RECOVER-STABLE-SIGNER.bat').read_text(encoding='utf-8')

assert 'recover_persisted_signing_keystore.py' in ps1, 'PowerShell launcher must use historical signer recovery tool'
assert 'release_signing_password' in ps1, 'launcher should remind the operator which historical workflow input supplied the password'
assert '--configure-github' in ps1, '-Apply must map to GitHub stable-signing configuration'
assert '--dispatch' in ps1, '-Dispatch must map to the verified stable workflow dispatch path'
assert 'DPC_AIO_RECOVERY_PASSWORD' not in ps1, 'launcher must not collect or echo the recovery password itself'
assert '--password' not in ps1.lower(), 'launcher must never place signing password on command line'
assert 'Read-Host' not in ps1, 'password prompt should remain inside the Python recovery tool/getpass path'
assert 'RECOVER-STABLE-SIGNER.ps1' in bat, 'BAT wrapper must delegate to PowerShell launcher'
assert '--password' not in bat.lower(), 'BAT wrapper must never accept a password argument'

print('STABLE_SIGNER_RECOVERY_LAUNCHER_CONTRACT: PASS')
