from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app'

services = {
    'EnrollmentRecoveryJobService.kt': (APP / 'EnrollmentRecoveryJobService.kt').read_text(encoding='utf-8'),
    'OfflineRecoveryJobService.kt': (APP / 'OfflineRecoveryJobService.kt').read_text(encoding='utf-8'),
}

for name, text in services.items():
    assert 'AtomicLong' in text, f'{name}: recovery job needs a generation token'
    assert 'workGeneration.incrementAndGet()' in text, f'{name}: each start/stop must invalidate stale workers'
    assert 'worker?.interrupt()' in text, f'{name}: onStopJob must interrupt the active worker'
    assert 'generation != workGeneration.get()' in text, f'{name}: stale workers must exit before completion side effects'

    stop = text[text.index('override fun onStopJob'):]
    assert 'workGeneration.incrementAndGet()' in stop, f'{name}: onStopJob must invalidate the worker generation'
    assert 'worker?.interrupt()' in stop, f'{name}: onStopJob must interrupt worker'

    finished = text.index('jobFinished(')
    stale_guard = text.rfind('generation != workGeneration.get()', 0, finished)
    assert stale_guard != -1, f'{name}: jobFinished must be preceded by a stale-generation guard'

# Enrollment retry scheduling must not be emitted by a worker that JobScheduler already stopped/rescheduled.
enrollment = services['EnrollmentRecoveryJobService.kt']
schedule = enrollment.index('EnrollmentResumeScheduler.schedule')
assert enrollment.rfind('generation != workGeneration.get()', 0, schedule) != -1, \
    'Enrollment retry scheduling must be guarded against stale workers'

# An unexpected coordinator exception must not strand JobScheduler in a permanently-running job.
assert 'runCatching' in enrollment and 'EnrollmentCoordinator(this).resumeOrCreate' in enrollment, \
    'Enrollment recovery must capture unexpected coordinator failures'
assert 'jobFinished(params, true)' in enrollment, \
    'Enrollment recovery exceptions must finish the active job and request reschedule'

# Offline policy application persists intermediate stages as it progresses. A process death at
# any of these points must remain recoverable instead of permanently blocking both Apply and Import.
offline = services['OfflineRecoveryJobService.kt']
for stage in [
    'OfflineStage.PACKAGES_INSTALLED',
    'OfflineStage.PERMISSIONS_APPLIED',
    'OfflineStage.COMPONENTS_APPLIED',
    'OfflineStage.POLICIES_APPLIED',
    'OfflineStage.READBACK_VERIFIED',
]:
    assert stage in offline, f'Offline recovery must resume persisted intermediate stage: {stage}'
assert 'OfflinePolicyApplier(this).apply(File(path))' in offline, \
    'Offline recovery must resume policy application from persisted intermediate stages'

# A crash after PACKAGES_STAGED but before Session.commit() must not permanently strand the bundle.
for token in [
    'OfflineStage.PACKAGES_STAGED',
    'state.commitSessionId',
    'AndroidOfflinePackageInstaller(this)',
    'packageInstaller.isCommitted(commitSessionId)',
    'packageInstaller.commitSession(commitSessionId, callback.intentSender)',
]:
    assert token in offline, f'Offline staged-session recovery missing required behavior: {token}'
assert 'PACKAGE_SESSION_RECOVERY_UNAVAILABLE' in offline, \
    'Legacy/missing staged session state must fail explicitly instead of blocking forever'
assert 'PACKAGE_SESSION_MISSING' in offline, \
    'Missing PackageInstaller session must fail explicitly instead of blocking forever'

print('RECOVERY_JOB_LIFECYCLE_CONTRACT: PASS')
