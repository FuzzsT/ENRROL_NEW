#!/usr/bin/env python3
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
P = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/EnrollmentSecretStore.kt'
assert P.is_file(), 'EnrollmentSecretStore.kt missing'
s = P.read_text('utf-8')
for marker in ['AndroidKeyStore', 'AES/GCM/NoPadding', 'KeyGenParameterSpec', 'createDeviceProtectedStorageContext', 'Base64']:
    assert marker in s, f'missing secure storage marker {marker}'
assert 'putString(KEY_TOKEN' not in s
assert 'putString(KEY_PASSWORD' not in s
print('test_enrollment_secret_store_contract: PASS')
