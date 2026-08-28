#!/usr/bin/env python3
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app'
manifest = (ROOT / 'apps/dpc/app/src/main/AndroidManifest.xml').read_text('utf-8')
activity = APP / 'EnrollmentStatusActivity.kt'
snapshot = APP / 'EnrollmentDiagnosticsSnapshot.kt'
assert activity.is_file(), 'EnrollmentStatusActivity.kt missing'
assert snapshot.is_file(), 'EnrollmentDiagnosticsSnapshot.kt missing'
a = activity.read_text('utf-8')
s = snapshot.read_text('utf-8')
assert 'EnrollmentStatusActivity' in manifest
for marker in ['Stage:', 'Source:', 'Retry', 'Export enrollment-diagnostics.json', 'EnrollmentDiagnosticsSnapshot.capture']:
    assert marker in a, f'status UI missing {marker}'
for marker in ['tokenFingerprint', 'serverUri', 'retryCount', 'lastError', 'toJson']:
    assert marker in s, f'diagnostics snapshot missing {marker}'
for forbidden in ['enrollmentToken', 'password', 'authHeader', 'kpeKey']:
    assert f'put("{forbidden}"' not in s, f'diagnostics must not export {forbidden}'
assert 'Ignore and finish' not in a
print('test_enrollment_ui_contract: PASS')
