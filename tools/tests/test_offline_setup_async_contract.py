from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ACTIVITY = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/OfflineSetupActivity.kt'
POLICY_ACTIVITY = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/PolicyComplianceActivity.kt'
text = ACTIVITY.read_text(encoding='utf-8')
policy_activity = POLICY_ACTIVITY.read_text(encoding='utf-8')

helper_start = text.index('private fun importOfflineBundle')
helper_end = text.index('\n    private fun preview()', helper_start)
helper_body = text[helper_start:helper_end]

assert 'Thread {' in helper_body, \
    'Offline bundle copy/inspection/hash must run off the UI thread'
assert 'val sourceUri = uri' in helper_body, \
    'Offline bundle import must snapshot the Activity-result Uri before worker execution'
assert 'copyToPrivateStorage(sourceUri)' in helper_body, \
    'Offline bundle worker must use the captured Uri snapshot'
assert 'bundleReader.inspect(file, BuildConfig.OFFLINE_SIGNING_PUBLIC_KEY)' in helper_body, \
    'Offline bundle verification must stay inside the import worker'
assert 'sha256(file)' in helper_body, \
    'Offline bundle hashing must stay inside the import worker'
assert 'runOnUiThread' in helper_body, \
    'Offline bundle worker must marshal final state back to the UI thread'
assert 'isFinishing || isDestroyed' in helper_body, \
    'Offline bundle import completion must ignore Activity teardown'
assert 'return@runOnUiThread' in helper_body, \
    'Offline bundle lifecycle guard must exit before touching Activity state/views'
assert 'generation != importGeneration' in helper_body, \
    'Offline bundle import must reject stale completion from an older selection'

assert 'bundleIoInProgress.compareAndSet(false, true)' in helper_body, \
    'Offline bundle import must acquire the shared process-wide bundle I/O gate'
assert 'importedBundle = null' in helper_body and 'bundleInspection = null' in helper_body, \
    'Starting an import must invalidate the previously verified bundle before the shared file is overwritten'
assert 'finally' in helper_body and 'bundleIoInProgress.set(false)' in helper_body, \
    'Offline bundle import gate must always be released after worker completion'

result_start = text.index('override fun onActivityResult')
result_end = helper_start
result_body = text[result_start:result_end]
assert 'importOfflineBundle(sourceUri)' in result_body, \
    'Activity result must hand the captured Uri to the async import helper'
assert 'copyToPrivateStorage(' not in result_body, \
    'onActivityResult must not perform bundle copy on the UI thread'
assert 'bundleReader.inspect(' not in result_body, \
    'onActivityResult must not verify bundle contents on the UI thread'
assert 'sha256(' not in result_body, \
    'onActivityResult must not hash bundle contents on the UI thread'

policy_start = text.index('private fun startPolicyOnlyDeployment')
policy_end = text.index('\n    private fun stageAndCommitBundle', policy_start)
policy_body = text[policy_start:policy_end]
assert 'val persistResult = runCatching {' in policy_body, \
    'Policy-only deployment must catch persistence failures before sending recovery broadcast'
assert 'if (persistResult.isFailure)' in policy_body, \
    'Policy-only deployment must stop when its checkpoint cannot be persisted'
assert 'OFFLINE_DEPLOYMENT_STATE_PERSIST_FAILED' in policy_body, \
    'Policy-only deployment must surface a clear persistence failure diagnostic'
failure_start = policy_body.index('if (persistResult.isFailure)')
package_done = policy_body.index('OfflineInstallStatusReceiver.ACTION_PACKAGE_DONE')
failure_body = policy_body[failure_start:package_done]
assert 'OfflineInstallStatusReceiver.ACTION_PERSISTENCE_RETRY' in failure_body, \
    'Policy-only persistence failure must schedule recovery of the pending durable checkpoint'
assert 'sendBroadcast(' in failure_body, \
    'Policy-only persistence failure must notify OfflineRecoveryReceiver before returning'
assert failure_body.index('sendBroadcast(') < failure_body.index('return'), \
    'Policy-only persistence retry must be scheduled before the failure path returns'

stage_start = text.index('private fun stageAndCommitBundle')
stage_end = text.index('\n    private fun shizukuAvailable()', stage_start)
stage_body = text[stage_start:stage_end]
assert 'workerStore.updateIf(' in stage_body, \
    'Package staging failure must use an atomic persisted ownership check'
for token in [
    'current.bundleId == manifest.bundleId',
    'current.stage == OfflineStage.PACKAGES_STAGED',
    'current.commitSessionId == stagedCommitSessionId',
]:
    assert token in stage_body, f'Package staging failure ownership predicate missing: {token}'
assert 'workerStore.load()?.let { state ->' not in stage_body, \
    'Package staging failure must not use a non-atomic load/save transition that can overwrite receiver success'
assert 'runCatching {\n                        workerStore.updateIf(' in stage_body, \
    'Package staging failure bookkeeping must not let a persistence exception escape the worker'
assert 'workerStore.hasPendingPersistence()' in stage_body, \
    'Package staging failure must detect a pending durable checkpoint after commit failure'
assert 'OfflineInstallStatusReceiver.ACTION_PERSISTENCE_RETRY' in stage_body, \
    'Package staging persistence failure must schedule pending-checkpoint recovery'
pending_check = stage_body.index('workerStore.hasPendingPersistence()')
persistence_retry = stage_body.index('OfflineInstallStatusReceiver.ACTION_PERSISTENCE_RETRY', pending_check)
ui_callback = stage_body.index('runOnUiThread', pending_check)
assert pending_check < persistence_retry < ui_callback, \
    'Package staging persistence retry must be scheduled from the worker before UI completion'

assert 'runCatching { EnrollmentExecutionRouter(this).execute(intent) }' in policy_activity, \
    'Policy compliance worker must catch enrollment execution failures'
assert 'OfflineDeploymentStore(applicationContext)' in policy_activity, \
    'Policy compliance persistence recovery must inspect the device-protected offline store'
assert 'hasPendingPersistence()' in policy_activity, \
    'Policy compliance must distinguish pending checkpoint durability failures'
assert 'OfflineInstallStatusReceiver.ACTION_PERSISTENCE_RETRY' in policy_activity, \
    'Policy compliance pending checkpoint failure must schedule offline persistence recovery'
assert 'EnrollmentExecutionOutcome.Retryable' in policy_activity, \
    'Policy compliance persistence failure must render a retryable enrollment result'

assert 'file.inputStream().buffered().use' in text, \
    'Offline bundle SHA-256 must stream file contents instead of reading the entire bundle in memory'
assert 'file.readBytes()' not in text, \
    'Offline bundle SHA-256 must not allocate the entire bundle in memory'
assert 'private val bundleIoInProgress = java.util.concurrent.atomic.AtomicBoolean(false)' in text, \
    'Shared offline bundle I/O gate must survive Activity recreation without retaining an Activity'

print('OFFLINE_SETUP_ASYNC_CONTRACT: PASS')
