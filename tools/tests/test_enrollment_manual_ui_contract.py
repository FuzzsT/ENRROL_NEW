#!/usr/bin/env python3
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app'
p = APP / 'EnrollmentManualActivity.kt'
assert p.is_file(), 'EnrollmentManualActivity.kt missing'
s = p.read_text('utf-8')
for marker in ['MANUAL_TOKEN', 'EnrollmentSessionStore', 'EnrollmentSecretStore', 'EnrollmentSessionStore.tokenFingerprint', 'EnrollmentCoordinator.scheduleResume', 'https://']:
    assert marker in s, f'manual enrollment missing {marker}'
assert 'Enrollment Engine' in (APP / 'AioDashboardActivity.kt').read_text('utf-8')
assert 'EnrollmentManualActivity' in (ROOT / 'apps/dpc/app/src/main/AndroidManifest.xml').read_text('utf-8')
for forbidden in ['Log.d', 'println(token', 'appendLine(token']:
    assert forbidden not in s, f'token logging forbidden: {forbidden}'
print('test_enrollment_manual_ui_contract: PASS')
