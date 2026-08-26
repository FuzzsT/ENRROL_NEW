#!/usr/bin/env python3
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

COMPONENT = 'android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME'
DOWNLOAD = 'android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION'
PACKAGE_SUM = 'android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM'
SIGNATURE_SUM = 'android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM'
ADMIN_EXTRAS = 'android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE'
ALLOW_OFFLINE = 'android.app.extra.PROVISIONING_ALLOW_OFFLINE'
MODE_KEY = 'io.dpcaio.extra.PROVISIONING_MODE'
VALID_MODES = ('auto', 'work-profile', 'fully-managed')
CHECKSUM_RE = re.compile(r'^[A-Za-z0-9_-]{43}$')
CERT_RE = re.compile(r'certificate SHA-256 digest:\s*([0-9a-fA-F]{64})')


def b64url(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).decode('ascii').rstrip('=')


def find_apksigner(explicit: str | None) -> str | None:
    if explicit:
        return explicit
    found = shutil.which('apksigner')
    if found:
        return found
    sdk = os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME')
    if sdk:
        candidates = sorted((Path(sdk) / 'build-tools').glob('*/apksigner'), reverse=True)
        if candidates:
            return str(candidates[0])
    return None


def signature_checksum(apk: Path, apksigner: str | None) -> str | None:
    tool = find_apksigner(apksigner)
    if not tool:
        return None
    proc = subprocess.run(
        [tool, 'verify', '--print-certs', str(apk)],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    )
    if proc.returncode != 0:
        return None
    match = CERT_RE.search(proc.stdout)
    return b64url(bytes.fromhex(match.group(1))) if match else None


def decode_qr(path: Path) -> dict:
    import cv2
    image = cv2.imread(str(path))
    if image is None:
        raise ValueError('QR image is unreadable')

    # OpenCV's classic QRCodeDetector can fail to detect otherwise valid,
    # dense Android Enterprise provisioning QR codes (for example version 20
    # produced by a long GitHub Releases URL with error correction M).
    # QRCodeDetectorAruco in OpenCV 4.14+ decodes those payloads reliably.
    decoded = ''
    points = None

    decoded, points, _ = cv2.QRCodeDetector().detectAndDecode(image)
    if points is None or not decoded:
        aruco_cls = getattr(cv2, 'QRCodeDetectorAruco', None)
        if aruco_cls is not None:
            decoded, points, _ = aruco_cls().detectAndDecode(image)

    if points is None or not decoded:
        height, width = image.shape[:2]
        tried = 'QRCodeDetector'
        if getattr(cv2, 'QRCodeDetectorAruco', None) is not None:
            tried += ' + QRCodeDetectorAruco'
        raise ValueError(
            f'QR image could not be decoded by OpenCV detectors '
            f'(image={width}x{height}; tried {tried})'
        )

    value = json.loads(decoded)
    if not isinstance(value, dict):
        raise ValueError('QR payload must decode to a JSON object')
    return value


def validate(payload: dict, expected_mode: str, qr: Path | None, apk: Path | None,
             apksigner: str | None, expected_component: str, expected_apk_url: str | None = None) -> dict:
    errors: list[str] = []
    component = payload.get(COMPONENT)
    if component != expected_component:
        errors.append(f'DPC component mismatch: {component!r}')

    url = payload.get(DOWNLOAD)
    if not isinstance(url, str) or not url.lower().startswith('https://'):
        errors.append('APK download URL must use HTTPS')
    if expected_apk_url is not None and url != expected_apk_url:
        errors.append(f'APK download URL mismatch: expected {expected_apk_url!r}, got {url!r}')

    package_sum = payload.get(PACKAGE_SUM)
    signature_sum = payload.get(SIGNATURE_SUM)
    valid_package = isinstance(package_sum, str) and bool(CHECKSUM_RE.fullmatch(package_sum))
    valid_signature = isinstance(signature_sum, str) and bool(CHECKSUM_RE.fullmatch(signature_sum))
    if valid_package == valid_signature:
        errors.append('payload must contain exactly one valid SHA-256 package or signature checksum')

    extras = payload.get(ADMIN_EXTRAS)
    mode = extras.get(MODE_KEY) if isinstance(extras, dict) else None
    if mode != expected_mode:
        errors.append(f'provisioning mode must be {expected_mode!r}, got {mode!r}')

    offline = payload.get(ALLOW_OFFLINE, False)
    if not isinstance(offline, bool):
        errors.append('PROVISIONING_ALLOW_OFFLINE must be boolean when present')

    qr_matches = None
    if qr is not None:
        if not qr.is_file():
            errors.append(f'QR PNG not found: {qr}')
            qr_matches = False
        else:
            try:
                qr_payload = decode_qr(qr)
                qr_matches = qr_payload == payload
                if not qr_matches:
                    errors.append('QR-decoded payload does not match provisioning JSON')
            except Exception as exc:
                qr_matches = False
                errors.append(f'QR decode failed: {exc}')

    apk_matches = None
    if apk is not None:
        if not apk.is_file():
            errors.append(f'APK not found: {apk}')
            apk_matches = False
        elif valid_package:
            actual = b64url(hashlib.sha256(apk.read_bytes()).digest())
            apk_matches = actual == package_sum
            if not apk_matches:
                errors.append('APK SHA-256 does not match provisioning package checksum')
        elif valid_signature:
            actual = signature_checksum(apk, apksigner)
            apk_matches = actual == signature_sum if actual is not None else False
            if actual is None:
                errors.append('could not read APK signing certificate SHA-256 with apksigner')
            elif not apk_matches:
                errors.append('APK signing certificate SHA-256 does not match provisioning signature checksum')

    return {
        'ok': not errors,
        'provisioningMode': mode,
        'component': component,
        'apkUrl': url,
        'checksumMode': 'signature' if valid_signature else 'package' if valid_package else 'invalid',
        'allowOffline': offline if isinstance(offline, bool) else None,
        'qrMatchesPayload': qr_matches,
        'apkChecksumMatches': apk_matches,
        'errors': errors,
    }


def main(default_expected_mode: str | None = None) -> int:
    parser = argparse.ArgumentParser(description='Validate a DPC-AIO Android Enterprise provisioning QR')
    parser.add_argument('--json', required=True, type=Path, dest='json_path')
    parser.add_argument('--qr', type=Path)
    parser.add_argument('--apk', type=Path)
    parser.add_argument('--apksigner')
    parser.add_argument('--expected-mode', choices=VALID_MODES, default=default_expected_mode)
    parser.add_argument('--expected-component', default='io.dpcaio.app/io.dpcaio.app.AioDeviceAdminReceiver')
    parser.add_argument('--expected-apk-url')
    args = parser.parse_args()
    if args.expected_mode is None:
        parser.error('--expected-mode is required')
    try:
        payload = json.loads(args.json_path.read_text('utf-8'))
        if not isinstance(payload, dict):
            raise ValueError('provisioning JSON must contain an object')
        report = validate(payload, args.expected_mode, args.qr, args.apk, args.apksigner, args.expected_component, args.expected_apk_url)
    except Exception as exc:
        report = {'ok': False, 'errors': [str(exc)]}
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0 if report.get('ok') else 1


if __name__ == '__main__':
    raise SystemExit(main())
