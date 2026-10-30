from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
hub = (ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityLauncherHubActivity.kt').read_text(encoding='utf-8')

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

print('ACTIVITY_LAUNCHER_SINGLE_RESET_RENDER_CONTRACT: PASS')
