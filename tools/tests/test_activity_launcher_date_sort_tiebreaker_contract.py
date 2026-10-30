from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
hub = (ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityLauncherHubActivity.kt').read_text()

for needle in [
    'private fun dateSortComparator(',
    'timestamp: (AppEntry) -> Long,',
    'val timestampComparator = if (currentSortDirection == SortDirection.DESCENDING)',
    'compareByDescending<AppEntry> { timestamp(it) }',
    'compareBy<AppEntry> { timestamp(it) }',
    '.thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }',
    '.thenBy(String.CASE_INSENSITIVE_ORDER) { it.packageName }',
    'val sortedValid = valid.sortedWith(dateSortComparator(timestamp))',
    'return sortedValid + unknown',
]:
    assert needle in hub, f'ActivityLauncherHubActivity.kt missing date-sort tie-breaker token: {needle}'

for stale in [
    'val sortedValid = valid.sortedBy(timestamp)',
    'val directed = if (currentSortDirection == SortDirection.DESCENDING) sortedValid.reversed() else sortedValid',
    'return directed + unknown',
]:
    assert stale not in hub, f'ActivityLauncherHubActivity.kt still contains stale whole-list date ordering: {stale}'

print('ACTIVITY_LAUNCHER_DATE_SORT_TIEBREAKER_CONTRACT: PASS')
