from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ACTIVITY = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/PermissionManagerActivity.kt'
text = ACTIVITY.read_text(encoding='utf-8')

assert 'private fun postUiIfAlive' in text, 'Permission Manager must centralize lifecycle-safe UI callbacks'
assert 'isFinishing || isDestroyed' in text, 'Permission Manager UI callback helper must ignore teardown'
assert 'return@runOnUiThread' in text, 'Permission Manager lifecycle helper must exit before touching views'

for token in [
    'postUiIfAlive { output.text = text }',
    'postUiIfAlive { output.text = "Global policy requested=',
    'postUiIfAlive { output.text = "Apply supported',
    'postUiIfAlive { output.text = "Restore previous DPC states',
]:
    assert token in text, f'Permission Manager async callback missing lifecycle-safe helper: {token}'


def function_body(name: str, next_name: str) -> str:
    start = text.index(f'private fun {name}')
    end = text.index(f'private fun {next_name}', start)
    return text[start:end]


scan = function_body('scanAppPermissions', 'mutateSelected')
assert scan.index('val q = query.text.toString().trim().lowercase()') < scan.index('Thread {'), \
    'Permission scan must snapshot query text on the UI thread before starting worker work'
assert scan.index('val targetUserId = targetUserId()') < scan.index('Thread {'), \
    'Permission scan must snapshot target user on the UI thread before starting worker work'
assert 'permissionScanGeneration' in text, \
    'Permission Manager must track permission-scan generations to reject stale results'
assert 'val generation = ++permissionScanGeneration' in scan, \
    'Each permission scan must capture a new generation before starting worker work'
assert 'if (generation != permissionScanGeneration) return@postUiIfAlive' in scan, \
    'Stale permission scan completion must exit before replacing the visible result'

mutate = function_body('mutateSelected', 'setGlobalPolicy')
assert mutate.index('val targetUserId = targetUserId()') < mutate.index('Thread {'), \
    'Permission mutation must snapshot target user on the UI thread before starting worker work'

batch = function_body('batchPreview', 'applyPendingBatch')
assert batch.index('val userId = targetUserId()') < batch.index('Thread {'), \
    'Batch preview must snapshot target user on the UI thread before starting worker work'
assert 'batchPreviewGeneration' in text, \
    'Permission Manager must track batch preview generations to reject stale results'
assert 'val generation = ++batchPreviewGeneration' in batch, \
    'Each batch preview must capture a new generation before starting worker work'
assert 'if (generation != batchPreviewGeneration) return@postUiIfAlive' in batch, \
    'Stale batch preview completion must exit before publishing pending plan state'
assert batch.index('if (generation != batchPreviewGeneration) return@postUiIfAlive') < batch.index('pendingBatchChanges = changes'), \
    'Pending batch state must only be published after stale-generation rejection'
assert batch.index('pendingBatchChanges = changes') < batch.index('output.text = text'), \
    'Pending batch state and visible preview must be published together on the UI thread'

apply_batch = function_body('applyPendingBatch', 'restorePreviousDpcStates')
assert 'targetPackage.text.toString().trim()' not in apply_batch, \
    'Batch apply must not re-read the package field after preview'
assert 'val pkg = plan.supported.firstOrNull()?.packageName ?: pendingBatchChanges.firstOrNull()?.packageName' in apply_batch, \
    'Batch apply must bind package routing to the package captured by the preview plan'
assert 'val previewChanges = pendingBatchChanges' in apply_batch, \
    'Batch apply must snapshot preview changes before worker execution'
assert 'val appliedRestorable = mutableListOf<PermissionBatchChange>()' in apply_batch, \
    'Batch apply must track verified DPC changes that can be restored'
assert 'verified && item.route in setOf(' in apply_batch, \
    'Only verified DPC/delegated-DPC writes may become restore candidates'
assert 'lastAppliedBatchChanges = appliedRestorable.toList()' in apply_batch, \
    'Batch apply must publish the verified restorable batch after execution'

assert 'lastAppliedBatchChanges' in text, \
    'Permission Manager must retain the last applied DPC batch independently from pending preview state'
companion_start = text.index('companion object')
companion_end = text.index('private lateinit var output', companion_start)
companion = text[companion_start:companion_end]
assert '@Volatile' in companion and 'lastAppliedBatchChanges' in companion, \
    'Applied restore state must survive Activity recreation and be visible across worker/main threads'
assert 'AtomicBoolean' in text and 'permissionMutationInProgress' in companion, \
    'All permission-state mutations must share one process-wide gate across Activity recreation'
assert 'batchMutationInProgress' not in companion, \
    'Batch-only gate must be replaced by the shared permission mutation gate'

for name, body in [
    ('single permission mutation', mutate),
    ('batch apply', apply_batch),
]:
    assert 'permissionMutationInProgress.compareAndSet(false, true)' in body, \
        f'{name} must reject overlap with any other permission-state mutation'
    assert 'permissionMutationInProgress.set(false)' in body, \
        f'{name} must release the shared permission mutation gate after worker completion'

restore = function_body('restorePreviousDpcStates', 'scanCatalog')
assert 'restorePlan(lastAppliedBatchChanges)' in restore, \
    'Restore must target the last verified applied DPC batch, not the latest preview'
assert 'restorePlan(pendingBatchChanges)' not in restore, \
    'Restore must not be redirected by a newer preview'
assert 'permissionMutationInProgress.compareAndSet(false, true)' in restore, \
    'Restore must reject overlap with any other permission-state mutation'
assert 'permissionMutationInProgress.set(false)' in restore, \
    'Restore must release the shared permission mutation gate after worker completion'
assert 'val remainingRestoreChanges = mutableListOf<PermissionBatchChange>()' in restore, \
    'Restore must track only changes that still need retry after execution'
assert 'if (result.observedDpcState != change.requestedState)' in restore, \
    'Restore must keep a candidate only when readback did not reach the previous state'
assert 'remainingRestoreChanges += change' in restore, \
    'Failed restore candidates must remain retryable'
assert 'lastAppliedBatchChanges = remainingRestoreChanges.toList()' in restore, \
    'Successful restore candidates must be consumed so stale rollback cannot be applied again later'

catalog = function_body('scanCatalog', 'autoRequestedPermissions')
assert catalog.index('val q = query.text.toString().trim().lowercase()') < catalog.index('Thread {'), \
    'Catalog scan must snapshot query text on the UI thread before starting worker work'
assert 'catalogScanGeneration' in text, \
    'Permission Manager must track catalog-scan generations to reject stale results'
assert 'val generation = ++catalogScanGeneration' in catalog, \
    'Each catalog scan must capture a new generation before starting worker work'
assert 'if (generation != catalogScanGeneration) return@postUiIfAlive' in catalog, \
    'Stale catalog scan completion must exit before replacing the visible result'

auto = function_body('autoRequestedPermissions', 'scanSystemConfigs')
assert auto.index('val userId = targetUserId()') < auto.index('Thread {'), \
    'AUTO permission flow must snapshot target user on the UI thread before starting worker work'
assert 'grantAllAuto(pkg, userId, entries' in auto, \
    'AUTO permission worker must use the snapshotted target user instead of touching EditText state'
assert 'permissionMutationInProgress.compareAndSet(false, true)' in auto, \
    'AUTO permission flow must reject overlap with any other permission-state mutation'
assert 'permissionMutationInProgress.set(false)' in auto, \
    'AUTO permission flow must release the shared permission mutation gate after worker completion'

print('PERMISSION_MANAGER_LIFECYCLE_CONTRACT: PASS')