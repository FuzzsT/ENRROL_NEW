#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TARGET = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/engineering/SigningIdentityReader.kt'

if not TARGET.exists():
    raise AssertionError(f'missing {TARGET.relative_to(ROOT)}')

text = TARGET.read_text(encoding='utf-8')

required = [
    'class SigningIdentityReader',
    'sealed interface SigningIdentityReadResult',
    'fun readInstalled(',
    'PackageManager.GET_SIGNING_CERTIFICATES',
    'signingInfo',
    'apkContentsSigners',
    'signingCertificateHistory',
    'MessageDigest.getInstance("SHA-256")',
    'MessageDigest.getInstance("SHA-1")',
    'SigningCertificateIdentity',
    'AppSigningIdentity',
    'PACKAGE_NOT_FOUND',
    'SIGNING_INFO_UNAVAILABLE',
    'NO_CURRENT_SIGNER',
]
for needle in required:
    if needle not in text:
        raise AssertionError(f'{TARGET.relative_to(ROOT)}: missing {needle!r}')

for forbidden in [
    'PrivateKey',
    'KeyStore.getInstance',
    'Runtime.getRuntime',
    'ProcessBuilder',
    'Shizuku',
    'sendBroadcast(',
    'startService(',
    'ACTION_CALL',
]:
    if forbidden in text:
        raise AssertionError(f'{TARGET.relative_to(ROOT)}: forbidden {forbidden!r}')

# Require compatibility handling for modern and legacy PackageManager signatures.
if 'PackageInfoFlags.of' not in text and 'Build.VERSION.SDK_INT >= 33' not in text:
    raise AssertionError('SigningIdentityReader must handle modern PackageInfo flags explicitly')

print('test_signature_code_android_contract: PASS')
