#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
workflow = (ROOT / '.github/workflows/build-aio-enrollment.yml').read_text('utf-8')
defaults = (ROOT / '.github/dpc-aio-defaults.env').read_text('utf-8')
signing = (ROOT / 'tools/release/prepare_enterprise_signing.sh').read_text('utf-8')

assert "release_signing_password:" in workflow
inputs = workflow.split('workflow_dispatch:',1)[1].split('push:',1)[0]
keys = re.findall(r'^      ([A-Za-z0-9_]+):\s*$', inputs, flags=re.M)
assert keys == ['release_signing_password'], keys

for name in [
    'DPC_AIO_DEFAULT_RELEASE_APK_NAME',
    'DPC_AIO_DEFAULT_CONTINUOUS_RELEASE_TAG',
    'DPC_AIO_DEFAULT_POLICY_PROFILE',
    'DPC_AIO_DEFAULT_PROVISIONING_MODE',
    'DPC_AIO_DEFAULT_ALLOW_OFFLINE',
    'DPC_AIO_DEFAULT_ENROLLMENT_OFFLINE_MODE',
]:
    assert name in defaults

assert ".github/dpc-aio-defaults.env" in workflow
assert 'standalone-default' in workflow
assert 'release_apk_name: ${{ steps.config.outputs.release_apk_name }}' in workflow
assert 'continuous_release_tag: ${{ steps.config.outputs.continuous_release_tag }}' in workflow
assert 'DPC_AIO_RELEASE_APK_NAME: ${{ needs.build.outputs.release_apk_name }}' in workflow
assert 'DPC_AIO_CONTINUOUS_RELEASE_TAG: ${{ needs.build.outputs.continuous_release_tag }}' in workflow
assert 'DPC_AIO_PERSISTED_SIGNING_KEYSTORE_URL' in workflow
assert 'DPC-AIO-signing-keystore.enc' in workflow
assert 'password-release-keystore' in workflow
assert 'partial set and using the password-backed bootstrap signer' in signing
assert 'PERSISTED_SIGNING_KEY_PASSWORD_MISMATCH' in signing
assert 'openssl enc -aes-256-cbc -salt -pbkdf2 -iter 600000' in signing
print('PASS: zero-settings workflow uses repo defaults/built-ins and password-backed persisted release signing')
