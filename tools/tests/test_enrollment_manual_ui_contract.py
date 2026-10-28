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
assert 'companion object' in s and 'private val persistenceInProgress = AtomicBoolean(false)' in s[s.index('companion object'):], \
    'manual enrollment persistence gate must survive Activity recreation while a worker is running'
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
    'val appContext = applicationContext',
]:
    assert snapshot in s and s.index(snapshot) < thread_index, f'UI/context value must be snapshotted before worker: {snapshot}'

for persistence in [
    'EnrollmentSecretStore(appContext).put(',
    'EnrollmentSessionStore(appContext).write(session)',
    'EnrollmentCoordinator.scheduleResume(appContext, "MANUAL_TOKEN")',
]:
    assert persistence in s and s.index(persistence) > thread_index, f'persistence must run off UI thread: {persistence}'

ui_callback = s.index('runOnUiThread {', thread_index)
release = s.index('persistenceInProgress.set(false)')
assert release > ui_callback, 'manual enrollment gate must stay held until main-thread completion begins'
completion_tail = s[ui_callback:]
assert 'finally {' in completion_tail and completion_tail.index('finally {') < completion_tail.index('persistenceInProgress.set(false)'), \
    'manual enrollment gate release must run in UI completion finally, including Activity teardown'

print('test_enrollment_manual_ui_contract: PASS')
