from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
hub = (ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityLauncherHubActivity.kt').read_text(encoding='utf-8')

for needle in [
    'activeFilterCount()',
    'if (currentStateFilter != StateFilter.ALL) count++',
    'if (currentInstallSourceFilter != InstallSourceFilter.ALL) count++',
    'if (currentTargetSdkFilter != TargetSdkFilter.ALL) count++',
    'if (currentDebuggableFilter != DebuggableFilter.ALL) count++',
    'if (::search.isInitialized && search.text.toString().isNotBlank()) count++',
    'ACTIVE FILTERS ${activeFilterCount()}',
]:
    assert needle in hub, f'ActivityLauncherHubActivity.kt missing {needle}'

print('ACTIVITY_LAUNCHER_ACTIVE_FILTER_COUNT_CONTRACT: PASS')
