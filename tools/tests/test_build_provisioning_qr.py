#!/usr/bin/env python3
import base64
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'tools' / 'provisioning' / 'generate_provisioning.py'

COMPONENT = 'android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME'
DOWNLOAD = 'android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION'
PACKAGE_SUM = 'android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM'
SIGNATURE_SUM = 'android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM'


def b64url(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).decode('ascii').rstrip('=')


def run(*args, env=None):
    return subprocess.run([sys.executable, str(SCRIPT), *map(str, args)], cwd=ROOT, env=env,
                          text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)


def test_package_checksum_and_png():
    with tempfile.TemporaryDirectory() as td:
        td = Path(td)
        apk = td / 'aio.apk'
        apk.write_bytes(b'fake-signed-apk-for-package-checksum')
        out = td / 'out'
        result = run('--apk', apk, '--apk-url', 'https://mdm.example.test/dpc.apk', '--out-dir', out,
                     '--checksum-mode', 'package', '--enrollment-token', 'lab-token', '--policy-profile', 'lab')
        assert result.returncode == 0, result.stderr
        payload = json.loads((out / 'provisioning.json').read_text('utf-8'))
        expected = b64url(hashlib.sha256(apk.read_bytes()).digest())
        assert payload[COMPONENT] == 'io.dpcaio.app/io.dpcaio.app.AioDeviceAdminReceiver'
        assert payload[DOWNLOAD] == 'https://mdm.example.test/dpc.apk'
        assert payload[PACKAGE_SUM] == expected
        assert SIGNATURE_SUM not in payload
        png = (out / 'provisioning-qr.png').read_bytes()
        assert png.startswith(b'\x89PNG\r\n\x1a\n')
        metadata = json.loads((out / 'provisioning-metadata.json').read_text('utf-8'))
        assert metadata['checksumMode'] == 'package'
        assert metadata['apkSha256Hex'] == hashlib.sha256(apk.read_bytes()).hexdigest()


def test_signature_checksum_from_apksigner():
    with tempfile.TemporaryDirectory() as td:
        td = Path(td)
        apk = td / 'aio.apk'
        apk.write_bytes(b'fake-signed-apk')
        cert_digest = bytes(range(32))
        fake = td / 'apksigner'
        fake.write_text('#!/bin/sh\necho "Signer #1 certificate SHA-256 digest: %s"\n' % cert_digest.hex(), 'utf-8')
        fake.chmod(0o755)
        out = td / 'out'
        result = run('--apk', apk, '--apk-url', 'https://mdm.example.test/dpc.apk', '--out-dir', out,
                     '--checksum-mode', 'signature', '--apksigner', fake)
        assert result.returncode == 0, result.stderr
        payload = json.loads((out / 'provisioning.json').read_text('utf-8'))
        assert payload[SIGNATURE_SUM] == b64url(cert_digest)
        assert PACKAGE_SUM not in payload
        metadata = json.loads((out / 'provisioning-metadata.json').read_text('utf-8'))
        assert metadata['checksumMode'] == 'signature'
        assert metadata['certificateSha256Hex'] == cert_digest.hex()


if __name__ == '__main__':
    test_package_checksum_and_png()
    test_signature_checksum_from_apksigner()
    print('test_build_provisioning_qr: PASS')
