#!/usr/bin/env python3
"""Regression: dense Android Enterprise QR must decode with verifier fallback."""
from __future__ import annotations

import base64
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile

import qrcode

ROOT = Path(__file__).resolve().parents[2]
VERIFIER = ROOT / 'tools' / 'provisioning' / 'verify_provisioning_qr.py'
spec = importlib.util.spec_from_file_location('verify_provisioning_qr', VERIFIER)
module = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(module)


def b64url(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).decode('ascii').rstrip('=')


def main() -> int:
    fake_apk_digest = bytes.fromhex('221f72b80c78dad5f95b4750c7c108a4df988660bdaf100fa8833f55271b6687')
    payload = {
        'android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME': 'io.dpcaio.app/io.dpcaio.app.AioDeviceAdminReceiver',
        'android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION': 'https://github.com/FuzzsT/ENRROL_NEW/releases/download/dpc-aio-continuous/DPC-AIO-enterprise-release.apk',
        'android.app.extra.PROVISIONING_ALLOW_OFFLINE': False,
        'android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM': b64url(fake_apk_digest),
        'android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE': {
            'io.dpcaio.extra.PROVISIONING_MODE': 'fully-managed',
            'io.dpcaio.extra.ENROLLMENT_SOURCE': 'qr',
            'io.dpcaio.extra.ENROLLMENT_OFFLINE_MODE': 'ONLINE',
        },
    }
    compact = json.dumps(payload, separators=(',', ':'), ensure_ascii=False)
    with tempfile.TemporaryDirectory() as td:
        out = Path(td) / 'dense-provisioning-qr.png'
        qr = qrcode.QRCode(
            version=None,
            error_correction=qrcode.constants.ERROR_CORRECT_M,
            box_size=8,
            border=4,
        )
        qr.add_data(compact)
        qr.make(fit=True)
        if qr.version < 20:
            raise AssertionError(f'regression payload unexpectedly sparse: QR version {qr.version}')
        qr.make_image(fill_color='black', back_color='white').save(out)
        decoded = module.decode_qr(out)
        if decoded != payload:
            raise AssertionError('dense QR decoded payload differs from source')
    print(f'PASS: dense QR version {qr.version} decoded ({len(compact)} chars)')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
