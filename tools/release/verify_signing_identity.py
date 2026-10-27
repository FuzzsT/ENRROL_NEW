#!/usr/bin/env python3
from __future__ import annotations
import argparse
import getpass
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

HEX64 = re.compile(r'^[0-9A-F]{64}$')


def normalized_sha256(value: str) -> str:
    out = ''.join(value.split()).replace(':', '').upper()
    if not HEX64.fullmatch(out):
        raise ValueError('expected a 64-hex SHA-256 fingerprint')
    return out


def parse_apksigner_sha256(text: str) -> str:
    for line in text.splitlines():
        if 'certificate SHA-256 digest:' not in line:
            continue
        value = line.split('certificate SHA-256 digest:', 1)[1].strip()
        try:
            return normalized_sha256(value)
        except ValueError:
            continue
    raise ValueError('APK signer SHA-256 digest not found in apksigner output')


def compare_fingerprints(left: str, right: str) -> bool:
    return normalized_sha256(left) == normalized_sha256(right)


def require_tool(name: str) -> str:
    path = shutil.which(name)
    if not path:
        raise SystemExit(f'ERROR: required tool not found: {name}')
    return path


def apk_fingerprint(apksigner: str, apk: Path) -> str:
    cp = subprocess.run(
        [apksigner, 'verify', '--print-certs', str(apk)],
        text=True,
        capture_output=True,
        check=True,
    )
    return parse_apksigner_sha256(cp.stdout + '\n' + cp.stderr)


def keystore_fingerprint(keytool: str, keystore: Path, alias: str, password: str) -> str:
    env = os.environ.copy()
    env['DPC_AIO_EFFECTIVE_STORE_PASSWORD'] = password
    with tempfile.NamedTemporaryFile(prefix='dpc-aio-cert-', suffix='.der', delete=False) as tf:
        cert = Path(tf.name)
    try:
        subprocess.run(
            [
                keytool, '-exportcert', '-keystore', str(keystore),
                '-alias', alias, '-storepass:env', 'DPC_AIO_EFFECTIVE_STORE_PASSWORD',
                '-file', str(cert),
            ],
            env=env,
            text=True,
            capture_output=True,
            check=True,
        )
        return hashlib.sha256(cert.read_bytes()).hexdigest().upper()
    finally:
        cert.unlink(missing_ok=True)


def main() -> int:
    p = argparse.ArgumentParser(description='Compare historical APK signer with a recovered release keystore.')
    p.add_argument('--apk', type=Path, required=True)
    p.add_argument('--keystore', type=Path, required=True)
    p.add_argument('--alias', default='dpc-aio-enterprise')
    p.add_argument('--expected-sha256')
    args = p.parse_args()

    for path in (args.apk, args.keystore):
        if not path.is_file() or path.stat().st_size == 0:
            raise SystemExit(f'ERROR: file not found or empty: {path}')

    password = os.environ.get('DPC_AIO_RELEASE_STORE_PASSWORD') or getpass.getpass('Keystore password: ')
    apksigner = require_tool('apksigner')
    keytool = require_tool('keytool')

    apk_sha = apk_fingerprint(apksigner, args.apk)
    key_sha = keystore_fingerprint(keytool, args.keystore, args.alias, password)
    if args.expected_sha256 and not compare_fingerprints(apk_sha, args.expected_sha256):
        raise SystemExit(f'SIGNER_MISMATCH: APK={apk_sha} expected={normalized_sha256(args.expected_sha256)}')
    if not compare_fingerprints(apk_sha, key_sha):
        raise SystemExit(f'SIGNER_MISMATCH: APK={apk_sha} KEYSTORE={key_sha}')

    print('SIGNER_MATCH: PASS')
    print(f'Certificate SHA-256: {apk_sha}')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
