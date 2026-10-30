from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
hub = (ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityLauncherHubActivity.kt').read_text(encoding='utf-8')

required = [
    'if (sort != currentSort) {',
    'currentSort = sort',
    'currentSortDirection = when (sort)',
    'AppSort.NAME, AppSort.PACKAGE -> SortDirection.ASCENDING',
    'AppSort.INSTALL_DATE, AppSort.UPDATE_DATE -> SortDirection.DESCENDING',
]
for needle in required:
    assert needle in hub, f'ActivityLauncherHubActivity.kt missing active-sort direction-preservation guard token: {needle}'

stale = '''                currentSort = sort\n                currentSortDirection = when (sort) {\n                    AppSort.NAME, AppSort.PACKAGE -> SortDirection.ASCENDING\n                    AppSort.INSTALL_DATE, AppSort.UPDATE_DATE -> SortDirection.DESCENDING\n                }\n                renderSortBar()\n'''
assert stale not in hub, 'ActivityLauncherHubActivity.kt still resets direction unconditionally when active sort is clicked'

assert 'private fun toggleSortDirection()' in hub, 'dedicated direction toggle must remain present'
assert 'Recent keeps most-recent-first order.' in hub, 'RECENT ordering contract must remain unchanged'

print('ACTIVITY_LAUNCHER_PRESERVE_SORT_DIRECTION_CONTRACT: PASS')
