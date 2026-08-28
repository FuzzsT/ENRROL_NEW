#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import zipfile

COMPATIBILITY_ASSETS = (
    'provisioning-qr.png',
    'provisioning.json',
    'provisioning-payload.txt',
    'provisioning-metadata.json',
    'provisioning-validation.json',
)
MODE_ASSETS = {
    'work-profile': (
        'work-profile-provisioning.json',
        'work-profile-provisioning-payload.txt',
        'work-profile-provisioning-metadata.json',
        'work-profile-qr.png',
        'work-profile-validation.json',
    ),
    'fully-managed': (
        'device-owner-provisioning.json',
        'device-owner-provisioning-payload.txt',
        'device-owner-provisioning-metadata.json',
        'device-owner-qr.png',
        'device-owner-validation.json',
    ),
}
EVIDENCE_ASSETS = (
    'android-runtime-smoke.json',
    'build-environment.json',
)

README_NAME = 'QR-README.md'
INDEX_NAME = 'RELEASE-INDEX.json'
SUMS_NAME = 'SHA256SUMS.txt'
FIXED_ZIP_DT = (2020, 1, 1, 0, 0, 0)


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open('rb') as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()


def require_valid_validation(path: Path) -> None:
    try:
        data = json.loads(path.read_text('utf-8'))
    except Exception as exc:
        raise SystemExit(f'invalid validation JSON {path.name}: {exc}')
    if data.get('ok') is not True:
        raise SystemExit(f'validation is not PASS: {path.name}')


def selected_modes(qr_type: str) -> tuple[str, ...]:
    if qr_type == 'both':
        return ('work-profile', 'fully-managed')
    return (qr_type,)


def write_readme(dist: Path, version: str, apk_url: str, apk_name: str, qr_type: str) -> None:
    modes = selected_modes(qr_type)
    mode_lines = []
    if 'work-profile' in modes:
        mode_lines.append('- `work-profile-qr.png` — BYOD / Profile Owner enrollment. Use the Android work-profile enrollment flow.')
    if 'fully-managed' in modes:
        mode_lines.append('- `device-owner-qr.png` — Fully Managed / Device Owner enrollment. Scan from Android Setup Wizard on a factory-reset device.')
    mode_lines.append('- `provisioning-qr.png` — compatibility QR matching the workflow compatibility provisioning mode for this release run.')
    text = f'''# DPC-AIO {version} — QR Release Bundle

This bundle is generated only after the enterprise release APK has been built, signed, QR-validated, and AOSP Device Owner runtime smoke has completed in CI.

Selected QR type: `{qr_type}`.

## Which QR should I scan?

{chr(10).join(mode_lines)}

## APK binding

All included QR payloads point to:

`{apk_url}`

The payloads are bound to `{apk_name}` by the generated package/signing checksum. CI validates QR-to-JSON equality, APK checksum/signature binding, and the expected public APK URL before this bundle is created.

## Integrity

- `SHA256SUMS.txt` covers every file inside this bundle except itself.
- The outer release also contains `DPC-AIO-{version}-QR-RELEASE-BUNDLE.zip.sha256`, which covers the bundle ZIP itself.
- `RELEASE-INDEX.json` records `qrType`, the APK, included QR modes, validation reports, and bundle metadata.

Do not reuse a QR from another APK build or release URL. Regenerate QR assets whenever the signed APK bytes, certificate, release tag, public APK URL, or selected QR type changes.
'''
    (dist / README_NAME).write_text(text, 'utf-8')


def write_index(
    dist: Path,
    version: str,
    apk_url: str,
    apk_name: str,
    qr_type: str,
    bundle_name: str,
    sidecar_name: str,
) -> None:
    qr = []
    if qr_type in ('both', 'work-profile'):
        qr.append({
            'id': 'work-profile',
            'mode': 'work-profile',
            'qr': 'work-profile-qr.png',
            'json': 'work-profile-provisioning.json',
            'payload': 'work-profile-provisioning-payload.txt',
            'metadata': 'work-profile-provisioning-metadata.json',
            'validation': 'work-profile-validation.json',
        })
    if qr_type in ('both', 'fully-managed'):
        qr.append({
            'id': 'device-owner',
            'mode': 'fully-managed',
            'qr': 'device-owner-qr.png',
            'json': 'device-owner-provisioning.json',
            'payload': 'device-owner-provisioning-payload.txt',
            'metadata': 'device-owner-provisioning-metadata.json',
            'validation': 'device-owner-validation.json',
        })
    qr.append({
        'id': 'compatibility',
        'mode': 'selected-at-workflow-runtime',
        'qr': 'provisioning-qr.png',
        'json': 'provisioning.json',
        'payload': 'provisioning-payload.txt',
        'metadata': 'provisioning-metadata.json',
        'validation': 'provisioning-validation.json',
    })
    obj = {
        'schema': 1,
        'version': version,
        'apk': apk_name,
        'apkUrl': apk_url,
        "qrType": qr_type,
        'qr': qr,
        'runtimeEvidence': 'android-runtime-smoke.json',
        'buildEnvironment': 'build-environment.json',
        'checksums': SUMS_NAME,
        'guide': README_NAME,
        'bundle': {
            'file': bundle_name,
            'sha256Sidecar': sidecar_name,
        },
    }
    (dist / INDEX_NAME).write_text(json.dumps(obj, indent=2, sort_keys=True) + '\n', 'utf-8')


def write_sums(dist: Path, names: list[str]) -> None:
    lines = [f'{sha256(dist / name)}  {name}' for name in sorted(names)]
    (dist / SUMS_NAME).write_text('\n'.join(lines) + '\n', 'utf-8')


def build_zip(dist: Path, bundle: Path, names: list[str]) -> None:
    with zipfile.ZipFile(bundle, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
        for name in sorted(names):
            data = (dist / name).read_bytes()
            zi = zipfile.ZipInfo(name, FIXED_ZIP_DT)
            zi.compress_type = zipfile.ZIP_DEFLATED
            zi.external_attr = 0o100644 << 16
            zf.writestr(zi, data, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)


def main() -> int:
    ap = argparse.ArgumentParser(description='Build verified DPC-AIO APK + Android Enterprise QR release bundle')
    ap.add_argument('--dist', type=Path, required=True)
    ap.add_argument('--version', required=True)
    ap.add_argument('--apk-url', required=True)
    ap.add_argument('--apk-name', default='DPC-AIO-enterprise-release.apk')
    ap.add_argument(
        '--qr-type',
        required=True,
        choices=('both', 'work-profile', 'fully-managed'),
    )
    args = ap.parse_args()

    dist = args.dist.resolve()
    if not dist.is_dir():
        raise SystemExit(f'dist directory not found: {dist}')
    if not args.apk_url.lower().startswith('https://'):
        raise SystemExit('--apk-url must use HTTPS')
    apk_name = args.apk_name.strip()
    if not apk_name or Path(apk_name).name != apk_name or apk_name in {'.', '..'}:
        raise SystemExit('--apk-name must be a safe file name without path separators')

    requested_mode_assets: list[str] = []
    for mode in selected_modes(args.qr_type):
        requested_mode_assets.extend(MODE_ASSETS[mode])
    primary_assets = [apk_name, *COMPATIBILITY_ASSETS, *requested_mode_assets, *EVIDENCE_ASSETS]
    missing = [name for name in primary_assets if not (dist / name).is_file()]
    if missing:
        raise SystemExit('missing release assets: ' + ', '.join(missing))

    require_valid_validation(dist / 'provisioning-validation.json')
    for mode in selected_modes(args.qr_type):
        require_valid_validation(dist / MODE_ASSETS[mode][-1])

    bundle_name = f'DPC-AIO-{args.version}-QR-RELEASE-BUNDLE.zip'
    sidecar_name = bundle_name + '.sha256'
    bundle = dist / bundle_name
    sidecar = dist / sidecar_name

    write_readme(dist, args.version, args.apk_url, apk_name, args.qr_type)
    write_index(dist, args.version, args.apk_url, apk_name, args.qr_type, bundle_name, sidecar_name)
    checksum_names = primary_assets + [README_NAME, INDEX_NAME]
    write_sums(dist, checksum_names)
    zip_names = checksum_names + [SUMS_NAME]
    build_zip(dist, bundle, zip_names)

    bundleSha256 = sha256(bundle)
    sidecar.write_text(f'{bundleSha256}  {bundle_name}\n', 'utf-8')
    print(json.dumps({
        'ok': True,
        'version': args.version,
        "qrType": args.qr_type,
        'bundle': bundle_name,
        'bundleSha256': bundleSha256,
        'sha256Sidecar': sidecar_name,
        'fileCount': len(zip_names),
    }, sort_keys=True))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
