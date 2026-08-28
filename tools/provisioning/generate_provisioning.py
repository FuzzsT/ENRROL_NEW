#!/usr/bin/env python3
import argparse
import base64
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys

COMPONENT = 'android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME'
DOWNLOAD = 'android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION'
PACKAGE_SUM = 'android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM'
SIGNATURE_SUM = 'android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM'
EXTRAS = 'android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE'
ALLOW_OFFLINE = 'android.app.extra.PROVISIONING_ALLOW_OFFLINE'
REQUESTED_MODE = 'io.dpcaio.extra.PROVISIONING_MODE'
ENROLLMENT_ENDPOINT = 'io.dpcaio.extra.ENROLLMENT_ENDPOINT'
ENROLLMENT_SOURCE = 'io.dpcaio.extra.ENROLLMENT_SOURCE'
ENROLLMENT_OFFLINE_MODE = 'io.dpcaio.extra.ENROLLMENT_OFFLINE_MODE'
OFFLINE_BUNDLE_ID = 'io.dpcaio.extra.OFFLINE_BUNDLE_ID'
OFFLINE_MODES = ('ONLINE', 'ONLINE_PREFERRED', 'FULL_OFFLINE', 'OFFLINE_THEN_SYNC')


def b64url(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).decode('ascii').rstrip('=')


def sha256_file(path: Path) -> tuple[str, str]:
    digest = hashlib.sha256(path.read_bytes()).digest()
    return digest.hex(), b64url(digest)


def cert_sha256_from_apksigner(apk: Path, apksigner: str | None) -> tuple[str, str] | None:
    tool = apksigner or shutil.which('apksigner')
    if not tool:
        return None
    proc = subprocess.run([tool, 'verify', '--print-certs', str(apk)], text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if proc.returncode != 0:
        return None
    match = re.search(r'certificate SHA-256 digest:\s*([0-9a-fA-F]{64})', proc.stdout)
    if not match:
        return None
    hex_digest = match.group(1).lower()
    return hex_digest, b64url(bytes.fromhex(hex_digest))


def render_png(text: str, output: Path) -> None:
    try:
        import qrcode
    except ImportError as exc:
        raise RuntimeError('Python package qrcode[pil] is required; install tools/provisioning/requirements.txt') from exc
    # Four modules is the QR minimum quiet zone, but dense Android Enterprise
    # payloads at our canonical GitHub Releases URL are not decoded reliably by
    # OpenCV with only that minimum. Six modules remains standards-compliant
    # and gives setup/CI scanners enough separation from the image boundary.
    qr = qrcode.QRCode(version=None, error_correction=qrcode.constants.ERROR_CORRECT_M, box_size=8, border=6)
    qr.add_data(text)
    qr.make(fit=True)
    image = qr.make_image(fill_color='black', back_color='white')
    image.save(output)


def main() -> int:
    p = argparse.ArgumentParser(description='Generate Android Enterprise DPC provisioning JSON + QR PNG')
    p.add_argument('--apk', required=True, type=Path)
    p.add_argument('--apk-url', required=True)
    p.add_argument('--out-dir', required=True, type=Path)
    p.add_argument('--package', default='io.dpcaio.app')
    p.add_argument('--receiver', default='io.dpcaio.app.AioDeviceAdminReceiver')
    p.add_argument('--checksum-mode', choices=('auto', 'signature', 'package'), default='auto')
    p.add_argument('--apksigner')
    p.add_argument('--enrollment-token', default='')
    p.add_argument('--policy-profile', default='default')
    p.add_argument('--enrollment-endpoint', default='')
    p.add_argument('--enrollment-source', default='qr', choices=('qr', 'kme', 'zero-touch', 'nfc', 'manual-token', 'byod-work-profile'))
    p.add_argument('--provisioning-mode', choices=('auto', 'work-profile', 'fully-managed'), default='work-profile')
    p.add_argument('--allow-offline', action='store_true')
    p.add_argument('--offline-mode', choices=OFFLINE_MODES, default='ONLINE')
    p.add_argument('--offline-bundle-id', default='')
    args = p.parse_args()

    if not args.apk.is_file():
        p.error(f'APK not found: {args.apk}')
    if not args.apk_url.lower().startswith('https://'):
        p.error('--apk-url must use https:// for Android setup provisioning')
    if args.enrollment_endpoint and not args.enrollment_endpoint.lower().startswith('https://'):
        p.error('--enrollment-endpoint must use https://')

    args.out_dir.mkdir(parents=True, exist_ok=True)
    apk_hex, apk_b64 = sha256_file(args.apk)
    cert = cert_sha256_from_apksigner(args.apk, args.apksigner) if args.checksum_mode != 'package' else None

    if args.checksum_mode == 'signature' and cert is None:
        raise SystemExit('signature checksum requested but apksigner certificate SHA-256 could not be read')

    mode = 'signature' if cert is not None else 'package'
    allow_offline = bool(args.allow_offline or args.offline_mode in ('FULL_OFFLINE', 'OFFLINE_THEN_SYNC'))
    payload = {
        COMPONENT: f'{args.package}/{args.receiver}',
        DOWNLOAD: args.apk_url,
    }
    # Android 13+ defines PROVISIONING_ALLOW_OFFLINE=false as the framework
    # default. Omit the false value to keep setup-wizard QR codes smaller and
    # easier to scan; include the extra only when offline provisioning is
    # intentionally requested.
    if allow_offline:
        payload[ALLOW_OFFLINE] = True
    metadata = {
        'schema': 1,
        'packageName': args.package,
        'receiverClass': args.receiver,
        'apkUrl': args.apk_url,
        'apkFile': args.apk.name,
        'apkSha256Hex': apk_hex,
        'checksumMode': mode,
        'provisioningMode': args.provisioning_mode,
        'enrollmentEndpoint': args.enrollment_endpoint or None,
        'enrollmentSource': args.enrollment_source,
        'offlineMode': args.offline_mode,
        'offlineBundleId': args.offline_bundle_id or None,
    }
    if mode == 'signature':
        cert_hex, cert_b64 = cert
        payload[SIGNATURE_SUM] = cert_b64
        metadata['certificateSha256Hex'] = cert_hex
    else:
        payload[PACKAGE_SUM] = apk_b64

    admin_extras = {
        REQUESTED_MODE: args.provisioning_mode,
        ENROLLMENT_SOURCE: args.enrollment_source,
    }
    # EnrollmentConfigParser already defaults to ONLINE when this custom extra
    # is absent. Avoid serializing the redundant default into every QR.
    if args.offline_mode != 'ONLINE':
        admin_extras[ENROLLMENT_OFFLINE_MODE] = args.offline_mode
    if args.enrollment_endpoint:
        admin_extras[ENROLLMENT_ENDPOINT] = args.enrollment_endpoint
    if args.offline_bundle_id:
        admin_extras[OFFLINE_BUNDLE_ID] = args.offline_bundle_id
    if args.offline_mode in ('FULL_OFFLINE', 'OFFLINE_THEN_SYNC') and not args.offline_bundle_id:
        p.error('--offline-bundle-id is required for FULL_OFFLINE/OFFLINE_THEN_SYNC')
    if args.enrollment_token:
        admin_extras.update({
            'enrollmentToken': args.enrollment_token,
            'policyProfile': args.policy_profile,
        })
    payload[EXTRAS] = admin_extras

    compact = json.dumps(payload, separators=(',', ':'), ensure_ascii=False)
    (args.out_dir / 'provisioning.json').write_text(json.dumps(payload, indent=2, ensure_ascii=False) + '\n', 'utf-8')
    (args.out_dir / 'provisioning-payload.txt').write_text(compact + '\n', 'utf-8')
    (args.out_dir / 'provisioning-metadata.json').write_text(json.dumps(metadata, indent=2) + '\n', 'utf-8')
    render_png(compact, args.out_dir / 'provisioning-qr.png')
    if args.provisioning_mode == 'work-profile':
        render_png(compact, args.out_dir / 'work-profile-qr.png')
    elif args.provisioning_mode == 'fully-managed':
        render_png(compact, args.out_dir / 'device-owner-qr.png')
        render_png(compact, args.out_dir / 'fully-managed-qr.png')
    print(json.dumps(metadata, sort_keys=True))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
