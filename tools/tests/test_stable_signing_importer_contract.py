from pathlib import Path

root = Path(__file__).resolve().parents[2]
required = [
    root / 'tools/release/import-existing-stable-signing.sh',
    root / 'tools/release/import-existing-stable-signing.ps1',
]
for path in required:
    if not path.is_file():
        raise SystemExit(f'MISSING: {path.relative_to(root)}')

sh = required[0].read_text(encoding='utf-8')
ps = required[1].read_text(encoding='utf-8')

markers = [
    'DPC_AIO_RELEASE_KEYSTORE_B64',
    'DPC_AIO_RELEASE_STORE_PASSWORD',
    'DPC_AIO_RELEASE_KEY_ALIAS',
    'DPC_AIO_RELEASE_KEY_PASSWORD',
    'DPC_AIO_EXPECTED_SIGNING_CERT_SHA256',
    'signing-cert-sha256.txt',
    'build-aio-enrollment.yml',
]
for marker in markers:
    if marker not in sh or marker not in ps:
        raise SystemExit(f'MISSING_MARKER: {marker}')

for forbidden in [
    'set -x',
    'echo "$store_password"',
    'echo "$key_password"',
    'Write-Host $StorePassword',
    'Write-Host $KeyPassword',
]:
    if forbidden in sh or forbidden in ps:
        raise SystemExit(f'FORBIDDEN_SECRET_LOGGING: {forbidden}')

if 'gh secret set' not in sh or '| gh secret set' not in sh:
    raise SystemExit('SHELL_MUST_PIPE_SECRETS_TO_GH')
if 'Set-GhSecretFromSecureString' not in ps:
    raise SystemExit('POWERSHELL_MUST_USE_SECURESTRING_HELPER')
if '--dispatch' not in sh or '-Dispatch' not in ps:
    raise SystemExit('DISPATCH_OPTION_REQUIRED')
if '--expected-sha256' not in sh or 'ExpectedSha256' not in ps:
    raise SystemExit('EXPECTED_FINGERPRINT_OPTION_REQUIRED')
if 'SIGNER_MISMATCH' not in sh or 'SIGNER_MISMATCH' not in ps:
    raise SystemExit('MISMATCH_MUST_FAIL_CLOSED')

print('STABLE_SIGNING_IMPORTER_CONTRACT: PASS')
