#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app'
TARGET = APP / 'engineering/SigningIdentityReader.kt'
RESOLVER = APP / 'engineering/SignatureCodeEvidenceResolver.kt'


def need(path: Path, needles):
    if not path.exists():
        raise AssertionError(f'missing {path.relative_to(ROOT)}')
    text = path.read_text(encoding='utf-8')
    for needle in needles:
        if needle not in text:
            raise AssertionError(f'{path.relative_to(ROOT)}: missing {needle!r}')
    return text


text = need(TARGET, [
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
])

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

if 'PackageInfoFlags.of' not in text and 'Build.VERSION.SDK_INT >= 33' not in text:
    raise AssertionError('SigningIdentityReader must handle modern PackageInfo flags explicitly')

resolver = need(RESOLVER, [
    'enum class SignatureCodeEvidence',
    'SIGNATURE_DERIVED',
    'VENDOR_PROFILE_MATCH',
    'SAMPLE_MATCHED',
    'MANIFEST_MATCH',
    'RUNTIME_VERIFIED',
    'data class SignatureCodeEvidenceResult',
    'class SignatureCodeEvidenceResolver',
    'fun resolve(',
    'DerivedCodeCandidate',
    'SecretCodeInventoryRepository',
    'SecretCodeRuntimeVerifier',
    'SamsungSecretCodeCatalog.normalize',
    'queryReceivers(',
    'normalizedCode',
    'rawHost',
])

for forbidden in [
    'sendBroadcast(',
    'startService(',
    'startForegroundService(',
    'ACTION_CALL',
    'UssdIntentFactory',
    'Runtime.getRuntime',
    'ProcessBuilder',
    'Shizuku',
]:
    if forbidden in resolver:
        raise AssertionError(f'{RESOLVER.relative_to(ROOT)}: forbidden {forbidden!r}')

print('test_signature_code_android_contract: PASS')
