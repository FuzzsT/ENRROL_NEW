#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app'


def need(path: Path, needles):
    assert path.exists(), f'missing {path.relative_to(ROOT)}'
    text = path.read_text(encoding='utf-8')
    for needle in needles:
        assert needle in text, f'{path.name}: missing {needle}'
    return text


activity = need(APP / 'SignatureCodeLabActivity.kt', [
    'Signature Code Lab',
    'SigningIdentityReader',
    'SignatureCodeDerivationEngine',
    'AutoSignatureCodeCatalog',
    'SignatureCodeSampleMatcher',
    'SignatureCodeEvidenceResolver',
    'Run auto derivation',
    'Vendor profile',
    'Custom formula',
    'Sample matcher',
    'Compare with receivers',
    'Copy candidate',
    'Copy formula',
    'Copy derivation report',
    'SHA-256',
    'SHA-1',
    'ExecutorService',
    'onSaveInstanceState',
])

for forbidden in [
    'sendBroadcast(',
    'startService(',
    'startForegroundService(',
    'ACTION_CALL',
    'UssdIntentFactory',
    'Runtime.getRuntime',
    'ProcessBuilder(',
    'Shizuku.newProcess',
]:
    assert forbidden not in activity, f'SignatureCodeLabActivity.kt: forbidden {forbidden}'

manifest = need(ROOT / 'apps/dpc/app/src/main/AndroidManifest.xml', [
    '.SignatureCodeLabActivity',
])

center = need(APP / 'EngineeringIntentCenterActivity.kt', [
    'Signature Code Lab',
    'SignatureCodeLabActivity::class.java',
])

print('test_signature_code_lab_ui_contract: PASS')
