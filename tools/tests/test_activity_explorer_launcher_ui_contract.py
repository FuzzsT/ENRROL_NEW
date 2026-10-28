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
