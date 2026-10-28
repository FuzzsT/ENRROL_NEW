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

assert 'AtomicBoolean' in s, 'manual enrollment must prevent duplicate persistence submissions'
assert 'Thread {' in s, 'AndroidKeyStore/session persistence must not run on the UI thread'
assert 'isFinishing || isDestroyed' in s and 'return@runOnUiThread' in s, \
    'manual enrollment completion must ignore Activity teardown'

thread_index = s.index('Thread {')
for snapshot in [
    'val endpointValue = endpoint.text.toString().trim()',
    'val tokenValue = token.text.toString().trim()',
    'val profileValue = profile.text.toString().trim()',
    'val organizationValue = organization.text.toString().trim()',
    'val modeValue = mode.selectedItem.toString()',
    'val allowOfflineValue = allowOffline.isChecked',
]:
    assert snapshot in s and s.index(snapshot) < thread_index, f'UI value must be snapshotted before worker: {snapshot}'

for persistence in [
    'EnrollmentSecretStore(applicationContext).put(',
    'EnrollmentSessionStore(applicationContext).write(session)',
    'EnrollmentCoordinator.scheduleResume(applicationContext, "MANUAL_TOKEN")',
]:
    assert persistence in s and s.index(persistence) > thread_index, f'persistence must run off UI thread: {persistence}'

print('test_enrollment_manual_ui_contract: PASS')
