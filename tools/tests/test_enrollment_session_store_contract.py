#!/usr/bin/env python3
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
P = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/EnrollmentSessionStore.kt'
assert P.is_file(), 'EnrollmentSessionStore.kt missing'
s = P.read_text('utf-8')
for marker in ['createDeviceProtectedStorageContext', 'tokenFingerprint', 'MessageDigest', 'EnrollmentSession', 'PREFS']:
    assert marker in s, f'missing {marker}'
for forbidden in ['putString("enrollmentToken"', 'putString(KEY_ENROLLMENT_TOKEN', 'KEY_PASSWORD', 'putString("password"']:
    assert forbidden not in s, f'plaintext secret persistence forbidden: {forbidden}'
assert 'SHA-256' in s
print('test_enrollment_session_store_contract: PASS')
