from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ACTIVITY = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityExplorerActivity.kt'
text = ACTIVITY.read_text(encoding='utf-8')

# Activity Explorer should behave like a compact launcher/browser rather than a
# form followed by oversized action cards.
for token in [
    'Activity Launcher',
    'enum class ActivityQuickFilter',
    'ALL',
    'ENABLED',
    'DISABLED',
    'EXPORTED',
    'PRIVATE',
    'applyActivityFilter',
    'renderFilterBar',
    'renderActivityRow',
    'shortActivityName',
    'FILTERED',
]:
    assert token in text, f'Activity Explorer launcher UI contract missing: {token}'

# Search should re-render live rather than requiring another package scan.
assert 'addTextChangedListener' in text, 'Activity search must update live'
assert 'renderActivities(currentActivities)' in text, 'Live search must re-render cached inventory'

# Async package scans must not try to render after the Activity has finished or
# been destroyed, and an older slower scan must not overwrite a newer result.
scan_start = text.index('private fun scan()')
scan_end = text.index('\n    private fun renderFilterBar()', scan_start)
scan = text[scan_start:scan_end]
assert 'isFinishing || isDestroyed' in scan, \
    'Activity Explorer scan callback must ignore results after Activity teardown'
assert 'return@runOnUiThread' in scan, \
    'Activity Explorer lifecycle guard must exit the UI callback before rendering'
assert 'scanGeneration' in text, \
    'Activity Explorer must track scan generations to reject stale async results'
assert 'val generation = ++scanGeneration' in scan, \
    'Each Activity Explorer scan must capture a new generation before starting work'
assert 'generation != scanGeneration' in scan, \
    'Activity Explorer must discard results from superseded scans'

# Component mutations run on worker threads. They must snapshot View-backed
# inputs before starting work and must never update a torn-down Activity.
change_start = text.index('private fun changeState(')
change_end = text.index('\n    private fun batchPreviewFiltered(', change_start)
change = text[change_start:change_end]
assert 'val operationUserId = targetUserId()' in change, \
    'Activity Explorer changeState must snapshot target user before its worker thread'
change_worker = change[change.index('Thread {'):]
assert 'targetUserId()' not in change_worker, \
    'Activity Explorer changeState worker must not read EditText-backed target user state'
assert 'isFinishing || isDestroyed' in change_worker, \
    'Activity Explorer changeState callback must ignore Activity teardown'

# Batch decisions and execution must stay bound to the same target user even if
# the user edits the target-user field after Preview but before Apply.
assert 'pendingBatchTargetUserId' in text, \
    'Activity Explorer must remember the target user used to build a batch preview'
preview_start = text.index('private fun batchPreviewFiltered(')
preview_end = text.index('\n    private fun applyBatch()', preview_start)
preview = text[preview_start:preview_end]
assert 'val previewUserId = targetUserId()' in preview, \
    'Batch Preview must snapshot target user before computing decisions'
assert 'pendingBatchTargetUserId = previewUserId' in preview, \
    'Batch Preview must persist the target user associated with its decisions'
assert 'decision(it, desired, previewUserId)' in preview, \
    'Batch Preview decisions must be computed for the captured preview user'

apply_start = text.index('private fun applyBatch()')
apply_end = text.index('\n    private fun restoreSnapshot()', apply_start)
apply_batch = text[apply_start:apply_end]
assert 'val batch = pendingBatch.toList()' in apply_batch, \
    'Activity Explorer applyBatch must snapshot pendingBatch before worker execution'
assert 'val operationUserId = pendingBatchTargetUserId' in apply_batch, \
    'Activity Explorer applyBatch must execute against the user captured by Batch Preview'
assert 'val operationUserId = targetUserId()' not in apply_batch, \
    'Activity Explorer applyBatch must not retarget a preview using a newly edited user ID'
apply_worker = apply_batch[apply_batch.index('Thread {'):]
assert 'targetUserId()' not in apply_worker, \
    'Activity Explorer applyBatch worker must not read EditText-backed target user state'
assert 'pendingBatch.filterNot' not in apply_worker, \
    'Activity Explorer applyBatch worker must use its immutable batch snapshot'
assert 'batch.filterNot' in apply_worker, \
    'Activity Explorer applyBatch skipped results must come from the captured batch'
assert 'isFinishing || isDestroyed' in apply_worker, \
    'Activity Explorer applyBatch callback must ignore Activity teardown'

# Compact rows must keep launch dominant while component-state changes stay
# available as secondary/advanced actions.
for token in [
    'ModernUi.action(this@ActivityExplorerActivity, "Launch", primary = true)',
    'Advanced controls',
    'Enable & Launch',
    'Restore default',
]:
    assert token in text, f'Activity row action hierarchy missing: {token}'

# Security routes and protected-component safeguards must remain intact.
for token in [
    'ComponentControlRoute.OWN_UID',
    'ComponentControlRoute.SHIZUKU',
    'ComponentControlRoute.SYSTEM_PRIVILEGED',
    'ComponentControlRoute.UNAVAILABLE',
    'isCriticalSystemComponent(activity)',
]:
    assert token in text, f'Activity safety route missing: {token}'

print('ACTIVITY_EXPLORER_LAUNCHER_UI_CONTRACT: PASS')
