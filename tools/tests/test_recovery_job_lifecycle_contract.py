from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app'

services = {
    'EnrollmentRecoveryJobService.kt': (APP / 'EnrollmentRecoveryJobService.kt').read_text(encoding='utf-8'),
    'OfflineRecoveryJobService.kt': (APP / 'OfflineRecoveryJobService.kt').read_text(encoding='utf-8'),
}
store_text = (APP / 'OfflineDeploymentStore.kt').read_text(encoding='utf-8')

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

enrollment = services['EnrollmentRecoveryJobService.kt']
schedule = enrollment.index('EnrollmentResumeScheduler.schedule')
assert enrollment.rfind('generation != workGeneration.get()', 0, schedule) != -1, \
    'Enrollment retry scheduling must be guarded against stale workers'

assert 'runCatching' in enrollment and 'EnrollmentCoordinator(this).resumeOrCreate' in enrollment, \
    'Enrollment recovery must capture unexpected coordinator failures'
assert 'jobFinished(params, true)' in enrollment, \
    'Enrollment recovery exceptions must finish the active job and request reschedule'

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

staged_start = offline.index('if (state.stage == OfflineStage.PACKAGES_STAGED)')
staged_end = offline.index('val policyRecoveryStage', staged_start)
staged = offline[staged_start:staged_end]
assert 'val generation = synchronized(lifecycleLock)' in staged, \
    'PACKAGES_STAGED recovery must allocate a worker generation before PackageInstaller work'
assert 'val thread = Thread {' in staged, \
    'PACKAGES_STAGED recovery must move PackageInstaller inspection/commit off JobService main thread'
assert staged.index('val thread = Thread {') < staged.index('packageInstaller.isCommitted(commitSessionId)'), \
    'PackageInstaller session inspection must execute inside the staged-session worker'
assert 'generation != workGeneration.get()' in staged, \
    'PACKAGES_STAGED recovery worker must reject stale completion'
assert 'jobFinished(params, false)' in staged, \
    'PACKAGES_STAGED recovery worker must finish the active JobScheduler job'
assert 'return true' in staged, \
    'PACKAGES_STAGED asynchronous recovery must report ongoing work to JobScheduler'

assert 'fun updateIf(' in store_text, \
    'OfflineDeploymentStore must expose an atomic conditional update for recovery ownership checks'
assert 'synchronized(stateLock)' in store_text, \
    'OfflineDeploymentStore conditional updates must share a process-wide state lock with save/load'
assert 'store.updateIf(' in staged, \
    'PACKAGES_STAGED failure transitions must use an atomic persisted ownership check'
for token in [
    'current.bundleId == state.bundleId',
    'current.stage == OfflineStage.PACKAGES_STAGED',
    'current.commitSessionId == commitSessionId',
]:
    assert token in staged, f'PACKAGES_STAGED ownership predicate missing: {token}'

assert '.commit()' in store_text, \
    'OfflineDeploymentStore recovery checkpoints must use synchronous SharedPreferences.commit()'
assert '.apply()' not in store_text, \
    'OfflineDeploymentStore must not use asynchronous apply() for crash-recovery state'

# SharedPreferences.commit() reports persistence failure with false. Recovery state must fail closed
# instead of letting callers continue as if a checkpoint had reached disk.
assert 'private fun commitOrThrow' in store_text, \
    'OfflineDeploymentStore must centralize checked durable commits'
assert 'check(editor.commit())' in store_text, \
    'OfflineDeploymentStore must reject commit() == false'
assert 'OFFLINE_DEPLOYMENT_STATE_PERSIST_FAILED' in store_text, \
    'OfflineDeploymentStore commit failures need an explicit diagnostic'
assert store_text.count('commitOrThrow(') >= 3, \
    'OfflineDeploymentStore save and clear paths must both use checked durable commits'

print('RECOVERY_JOB_LIFECYCLE_CONTRACT: PASS')
