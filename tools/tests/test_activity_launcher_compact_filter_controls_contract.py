from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
hub = (ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityLauncherHubActivity.kt').read_text()

for needle in [
    'private lateinit var resetFiltersButton: Button',
    'Reset filters (${activeFilterCount()})',
    'resetFiltersButton.isEnabled = activeFilterCount() > 0',
    'renderFilterControls()',
]:
    assert needle in hub, f'ActivityLauncherHubActivity.kt missing {needle}'

print('ACTIVITY_LAUNCHER_COMPACT_FILTER_CONTROLS_CONTRACT: PASS')
