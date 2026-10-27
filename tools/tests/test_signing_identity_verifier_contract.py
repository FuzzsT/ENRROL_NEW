#!/usr/bin/env python3
from __future__ import annotations
import importlib.util
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HELPER = ROOT / 'tools' / 'release' / 'verify_signing_identity.py'
assert HELPER.is_file(), f'MISSING: {HELPER}'
source = HELPER.read_text(encoding='utf-8')
assert 'add_argument("--password"' not in source
assert 'DPC_AIO_RELEASE_STORE_PASSWORD' in source
assert 'SIGNER_MISMATCH' in source
assert 'apksigner' in source
assert 'keytool' in source

spec = importlib.util.spec_from_file_location('verifier', HELPER)
mod = importlib.util.module_from_spec(spec)
assert spec and spec.loader
spec.loader.exec_module(mod)

expected = 'AA' * 32
for sample in (
    f'Signer #1 certificate SHA-256 digest: {expected.lower()}\n',
    f'V2 Signer: certificate SHA-256 digest: {expected}\n',
    f'Signer (minSdkVersion=24, maxSdkVersion=35) certificate SHA-256 digest: {expected}\n',
):
    assert mod.parse_apksigner_sha256(sample) == expected

assert mod.normalized_sha256(':'.join([expected[i:i+2] for i in range(0, len(expected), 2)])) == expected
assert mod.compare_fingerprints(expected, expected) is True
assert mod.compare_fingerprints(expected, 'BB' * 32) is False
print('SIGNING_IDENTITY_VERIFIER_CONTRACT: PASS')
