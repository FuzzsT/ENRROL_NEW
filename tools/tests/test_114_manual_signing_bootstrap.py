#!/usr/bin/env python3
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'tools/release/prepare_enterprise_signing.sh'
WORKFLOW = ROOT / '.github/workflows/build-aio-enrollment.yml'
PASSWORD = 'RunOnly-Strong-Pass-114!'


def run_prepare(event_name: str, password: str = ''):
    with tempfile.TemporaryDirectory() as td:
        td = Path(td)
        env_file = td / 'github-env'
        out_file = td / 'github-output'
        env = os.environ.copy()
        for name in (
            'DPC_AIO_RELEASE_KEYSTORE_B64',
            'DPC_AIO_RELEASE_STORE_PASSWORD',
            'DPC_AIO_RELEASE_KEY_ALIAS',
            'DPC_AIO_RELEASE_KEY_PASSWORD',
            'DPC_AIO_EXPECTED_SIGNING_CERT_SHA256',
        ):
            env.pop(name, None)
        env.update({
            'GITHUB_EVENT_NAME': event_name,
            'RUNNER_TEMP': str(td),
            'GITHUB_ENV': str(env_file),
            'GITHUB_OUTPUT': str(out_file),
            'DPC_AIO_MANUAL_SIGNING_PASSWORD': password,
        })
        cp = subprocess.run(['bash', str(SCRIPT)], cwd=ROOT, env=env, text=True, capture_output=True)
        env_text = env_file.read_text('utf-8') if env_file.exists() else ''
        out_text = out_file.read_text('utf-8') if out_file.exists() else ''
        files = {p.name: p.read_bytes() for p in td.iterdir() if p.is_file()}
        return cp, env_text, out_text, files


workflow = WORKFLOW.read_text('utf-8')
assert 'release_signing_password:' in workflow
assert 'GITHUB_EVENT_PATH' in workflow
assert 'tools/release/prepare_enterprise_signing.sh' in workflow
assert '${{ inputs.release_signing_password }}' not in workflow, 'manual password must not be interpolated into logged run/env YAML'
assert 'signing_mode: ${{ steps.signing.outputs.mode }}' in workflow, 'build job must export signing mode'
assert "github.ref_type == 'tag' && needs.build.outputs.signing_mode == 'stable-secrets'" in workflow, 'versioned releases must require stable signing'
assert "needs.build.outputs.signing_mode == 'generated-bootstrap'" in workflow, 'manual bootstrap builds must be able to populate the continuous prerelease'
assert 'Existing installations cannot update to a later build signed with a different generated key' in workflow, 'continuous generated-signing releases must warn about update incompatibility'

cp, env_text, out_text, files = run_prepare('workflow_dispatch', PASSWORD)
assert cp.returncode == 0, cp.stderr + cp.stdout
assert PASSWORD not in cp.stdout
assert PASSWORD not in cp.stderr
assert 'DPC_AIO_RELEASE_KEYSTORE_PATH=' in env_text
assert f'DPC_AIO_RELEASE_STORE_PASSWORD={PASSWORD}' in env_text
assert 'DPC_AIO_RELEASE_KEY_ALIAS=dpc-aio-enterprise' in env_text
assert f'DPC_AIO_RELEASE_KEY_PASSWORD={PASSWORD}' in env_text
assert 'DPC_AIO_EXPECTED_SIGNING_CERT_SHA256=' in env_text
assert 'mode=generated-bootstrap' in out_text
assert any(name.endswith('.jks') for name in files), files.keys()

cp, _, _, _ = run_prepare('workflow_dispatch', '')
assert cp.returncode != 0
assert 'MANUAL_SIGNING_PASSWORD_REQUIRED' in cp.stderr

cp, _, _, _ = run_prepare('push', PASSWORD)
assert cp.returncode != 0
assert 'STABLE_SIGNING_KEY_REQUIRED' in cp.stderr

print('MANUAL_SIGNING_BOOTSTRAP_114: PASS')
