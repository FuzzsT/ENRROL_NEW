#!/usr/bin/env python3
from __future__ import annotations

import json
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
GENERATOR = ROOT / 'tools' / 'provisioning' / 'generate_provisioning.py'
STORE = ROOT / 'apps' / 'dpc' / 'app' / 'src' / 'main' / 'kotlin' / 'io' / 'dpcaio' / 'app' / 'EnrollmentSecretStore.kt'
CAPTURE = ROOT / 'apps' / 'dpc' / 'app' / 'src' / 'main' / 'kotlin' / 'io' / 'dpcaio' / 'app' / 'KnoxProvisioningSecretCapture.kt'
ROUTER = ROOT / 'apps' / 'dpc' / 'app' / 'src' / 'main' / 'kotlin' / 'io' / 'dpcaio' / 'app' / 'EnrollmentExecutionRouter.kt'

KPE = 'KPE_TEST_SECRET_1234567890'
EXTRAS = 'android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE'


def main() -> None:
    assert GENERATOR.is_file(), 'missing provisioning generator'
    assert STORE.is_file(), 'missing EnrollmentSecretStore'

    with tempfile.TemporaryDirectory() as td:
        root = Path(td)
        apk = root / 'app.apk'
        out = root / 'out'
        apk.write_bytes(b'not-a-real-apk-but-package-checksum-is-enough')

        cp = subprocess.run(
            [
                sys.executable,
                str(GENERATOR),
                '--apk', str(apk),
                '--apk-url', 'https://example.invalid/app.apk',
                '--out-dir', str(out),
                '--checksum-mode', 'package',
                '--knox-license-key', KPE,
                '--knox-auto-activate',
            ],
            text=True,
            capture_output=True,
        )
        assert cp.returncode == 0, cp.stdout + cp.stderr

        payload = json.loads((out / 'provisioning.json').read_text('utf-8'))
        extras = payload[EXTRAS]
        assert extras['knox.licenseKey'] == KPE
        assert extras['knox.autoActivate'] is True

        metadata_text = (out / 'provisioning-metadata.json').read_text('utf-8')
        metadata = json.loads(metadata_text)
        assert KPE not in metadata_text, 'KPE secret leaked into metadata'
        assert metadata.get('knoxLicenseConfigured') is True
        assert metadata.get('knoxAutoActivate') is True
        assert 'knoxLicenseKey' not in metadata

    assert CAPTURE.is_file(), 'missing Knox provisioning secret capture'
    capture = CAPTURE.read_text('utf-8')
    store = STORE.read_text('utf-8')
    router = ROUTER.read_text('utf-8')

    for marker in [
        'knox.licenseKey',
        'knox.autoActivate',
        'EnrollmentSecretStore(context)',
        'KNOX_SECRET_REF',
        'EnrollmentSecrets(knoxLicenseKey = key)',
    ]:
        assert marker in capture, f'missing KPE capture marker: {marker}'

    for marker in [
        'val knoxLicenseKey: String? = null',
        'put("knoxLicenseKey"',
        'knoxLicenseKey = json.optString("knoxLicenseKey")',
    ]:
        assert marker in store, f'missing encrypted KPE storage marker: {marker}'

    assert 'KnoxProvisioningSecretCapture.capture(context, extras)' in router
    assert router.index('KnoxProvisioningSecretCapture.capture(context, extras)') < router.index('EnrollmentConfigParser.parse(extras)')

    forbidden = [
        'println(key)',
        'Log.d("KPE"',
        'Log.i("KPE"',
        'Log.v("KPE"',
    ]
    combined = capture + '\n' + store + '\n' + router
    for marker in forbidden:
        assert marker not in combined, f'KPE secret logging marker present: {marker}'

    print('KPE_PROVISIONING_SECRET_CONTRACT: PASS')


if __name__ == '__main__':
    main()
