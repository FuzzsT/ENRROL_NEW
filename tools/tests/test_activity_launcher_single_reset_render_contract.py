from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
hub = (ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityLauncherHubActivity.kt').read_text(encoding='utf-8')
explorer = (ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityExplorerActivity.kt').read_text(encoding='utf-8')

needles = [
    'private var suppressSearchRender = false',
    'if (!suppressSearchRender) renderApps()',
    'suppressSearchRender = true',
    'search.setText("")',
    'suppressSearchRender = false',
]

for needle in needles:
    assert needle in hub, f'ActivityLauncherHubActivity.kt missing single-reset-render contract token: {needle}'

reset_start = hub.index('private fun resetFilters()')
reset_end = hub.index('private fun installedPackageNames()', reset_start)
reset_body = hub[reset_start:reset_end]
assert reset_body.count('renderApps()') == 1, 'resetFilters() must perform one explicit final renderApps() call'

assert 'private var operationGeneration = 0L' in explorer, \
    'ActivityExplorerActivity.kt missing shared async operation generation guard'
assert 'private var mutationGeneration = 0L' not in explorer, \
    'ActivityExplorerActivity.kt must use one shared operation generation instead of mutation-only generation'

change_start = explorer.index('private fun changeState(')
change_end = explorer.index('\n    private fun batchPreviewFiltered(', change_start)
change_body = explorer[change_start:change_end]
for needle in [
    'val generation = ++operationGeneration',
    'if (generation != operationGeneration) return@runOnUiThread',
    'if (operationUserId != targetUserId()) return@runOnUiThread',
]:
    assert needle in change_body, f'Activity Explorer single mutation missing shared-operation stale-result guard: {needle}'

batch_start = explorer.index('private fun applyBatch()')
batch_end = explorer.index('\n    private fun restoreSnapshot()', batch_start)
batch_body = explorer[batch_start:batch_end]
for needle in [
    'if (operationUserId != targetUserId()) {',
    'Batch target changed. Run Batch Preview again',
    'val generation = ++operationGeneration',
    'if (generation != operationGeneration) return@runOnUiThread',
    'if (operationUserId != targetUserId()) return@runOnUiThread',
]:
    assert needle in batch_body, f'Activity Explorer batch mutation missing shared-operation guard: {needle}'

launch_start = explorer.index('private fun launch(')
launch_body = explorer[launch_start:]
for needle in [
    'val operationUserId = requestedUserId',
    'val generation = ++operationGeneration',
    'if (generation != operationGeneration) return@runOnUiThread',
    'if (operationUserId != targetUserId()) return@runOnUiThread',
    'AndroidUserId.representativeUid(operationUserId)',
    'ShizukuActivityRouteExecutor(shizuku, operationUserId)',
]:
    assert needle in launch_body, f'Activity Explorer launch missing shared-operation stale-result guard: {needle}'

print('ACTIVITY_LAUNCHER_SINGLE_RESET_RENDER_CONTRACT: PASS')
