#!/usr/bin/env python3
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app'
manifest = (ROOT / 'apps/dpc/app/src/main/AndroidManifest.xml').read_text('utf-8')
receiver = APP / 'EnrollmentRecoveryReceiver.kt'
scheduler = APP / 'EnrollmentResumeScheduler.kt'
service = APP / 'EnrollmentRecoveryJobService.kt'
for p in [receiver, scheduler, service]: assert p.is_file(), f'{p.name} missing'
r = receiver.read_text('utf-8')
sc = scheduler.read_text('utf-8')
sv = service.read_text('utf-8')
for marker in ['BOOT_COMPLETED', 'LOCKED_BOOT_COMPLETED', 'MY_PACKAGE_REPLACED']:
    assert marker in manifest, f'manifest missing {marker}'
assert 'EnrollmentRecoveryReceiver' in manifest
assert 'EnrollmentRecoveryJobService' in manifest
assert 'android.permission.BIND_JOB_SERVICE' in manifest
assert 'scheduleResume' in r
for forbidden in ['HttpURLConnection', 'HttpsURLConnection', 'EnrollmentHttpClient']:
    assert forbidden not in r, f'receiver must not do network work: {forbidden}'
for marker in ['JobScheduler', 'setMinimumLatency', 'setPersisted(true)', 'EnrollmentRetryPolicy']:
    assert marker in sc + sv, f'recovery missing {marker}'
assert 'scheduleResume' in (APP / 'AioDeviceAdminReceiver.kt').read_text('utf-8')
print('test_enrollment_recovery_contract: PASS')
