#!/usr/bin/env python3
from pathlib import Path
import os
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'tools/release/prepare_enterprise_signing.sh'
WORKFLOW = ROOT / '.github/workflows/build-aio-enrollment.yml'
PASSWORD = 'RunOnly-Strong-Pass-114!'


def run_prepare(event_name: str, password: str = '', persisted_url: str = ''):
    td_obj = tempfile.TemporaryDirectory()
    td = Path(td_obj.name)
    env_file = td / 'github-env'
    out_file = td / 'github-output'
    env = os.environ.copy()
    for name in (
        'DPC_AIO_RELEASE_KEYSTORE_B64',
        'DPC_AIO_RELEASE_STORE_PASSWORD',
        'DPC_AIO_RELEASE_KEY_ALIAS',
        'DPC_AIO_RELEASE_KEY_PASSWORD',
        'DPC_AIO_EXPECTED_SIGNING_CERT_SHA256',
        'DPC_AIO_PERSISTED_SIGNING_KEYSTORE_URL',
    ):
        env.pop(name, None)
    env.update({
        'GITHUB_EVENT_NAME': event_name,
        'RUNNER_TEMP': str(td),
        'GITHUB_ENV': str(env_file),
        'GITHUB_OUTPUT': str(out_file),
        'DPC_AIO_MANUAL_SIGNING_PASSWORD': password,
    })
    if persisted_url:
        env['DPC_AIO_PERSISTED_SIGNING_KEYSTORE_URL'] = persisted_url
    cp = subprocess.run(['bash', str(SCRIPT)], cwd=ROOT, env=env, text=True, capture_output=True)
    env_text = env_file.read_text('utf-8') if env_file.exists() else ''
    out_text = out_file.read_text('utf-8') if out_file.exists() else ''
    files = {p.name: p.read_bytes() for p in td.iterdir() if p.is_file()}
    return td_obj, cp, env_text, out_text, files


workflow = WORKFLOW.read_text('utf-8')
assert 'release_signing_password:' in workflow
assert 'GITHUB_EVENT_PATH' in workflow
assert 'tools/release/prepare_enterprise_signing.sh' in workflow
assert '${{ inputs.release_signing_password }}' not in workflow
assert 'signing_mode: ${{ steps.signing.outputs.mode }}' in workflow
assert "github.ref_type == 'tag' && needs.build.outputs.signing_mode == 'stable-secrets'" in workflow
assert "needs.build.outputs.signing_mode == 'password-release-keystore'" in workflow
assert 'DPC-AIO-signing-keystore.enc' in workflow
assert 'Reuse the same Run workflow password' in workflow

run1, cp, env_text, out_text, files = run_prepare('workflow_dispatch', PASSWORD)
try:
    assert cp.returncode == 0, cp.stderr + cp.stdout
    assert PASSWORD not in cp.stdout
    assert PASSWORD not in cp.stderr
    assert 'DPC_AIO_RELEASE_KEYSTORE_PATH=' in env_text
    assert f'DPC_AIO_RELEASE_STORE_PASSWORD={PASSWORD}' in env_text
    assert 'DPC_AIO_RELEASE_KEY_ALIAS=dpc-aio-enterprise' in env_text
    assert f'DPC_AIO_RELEASE_KEY_PASSWORD={PASSWORD}' in env_text
    assert 'DPC_AIO_EXPECTED_SIGNING_CERT_SHA256=' in env_text
    assert 'DPC_AIO_SIGNING_EXPORT_ENCRYPTED_PATH=' in env_text
    assert 'mode=password-release-keystore' in out_text
    assert 'persisted_signing_key=created' in out_text
    assert any(name.endswith('.p12') for name in files), files.keys()
    assert 'DPC-AIO-signing-keystore.enc' in files
    cert1 = [line.split('=',1)[1] for line in out_text.splitlines() if line.startswith('signing_cert_sha256=')][-1]
    persisted = Path(run1.name) / 'DPC-AIO-signing-keystore.enc'
    carry = Path(tempfile.mkdtemp()) / 'persisted.enc'
    shutil.copy2(persisted, carry)
finally:
    run1.cleanup()

run2, cp2, _, out2, _ = run_prepare('workflow_dispatch', PASSWORD, carry.as_uri())
try:
    assert cp2.returncode == 0, cp2.stderr + cp2.stdout
    assert 'persisted_signing_key=restored' in out2
    cert2 = [line.split('=',1)[1] for line in out2.splitlines() if line.startswith('signing_cert_sha256=')][-1]
    assert cert1 == cert2, (cert1, cert2)
finally:
    run2.cleanup()

run3, cp3, _, _, _ = run_prepare('workflow_dispatch', 'Wrong-Password-114-Long!', carry.as_uri())
try:
    assert cp3.returncode != 0
    assert 'PERSISTED_SIGNING_KEY_PASSWORD_MISMATCH' in cp3.stderr or 'PERSISTED_SIGNING_KEY_INVALID' in cp3.stderr
finally:
    run3.cleanup()
    shutil.rmtree(carry.parent, ignore_errors=True)

run4, cp4, _, _, _ = run_prepare('workflow_dispatch', '')
try:
    assert cp4.returncode != 0
    assert 'MANUAL_SIGNING_PASSWORD_REQUIRED' in cp4.stderr
finally:
    run4.cleanup()

run5, cp5, _, _, _ = run_prepare('push', PASSWORD)
try:
    assert cp5.returncode != 0
    assert 'STABLE_SIGNING_KEY_REQUIRED' in cp5.stderr
finally:
    run5.cleanup()

print('MANUAL_SIGNING_BOOTSTRAP_114: PASS (password-only persisted release signer)')
