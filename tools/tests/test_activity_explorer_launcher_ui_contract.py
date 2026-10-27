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
