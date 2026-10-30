from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
hub = (ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityLauncherHubActivity.kt').read_text()
permission_manager = (ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/PermissionManagerActivity.kt').read_text()
package_manager = (ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/AppPackageManagerActivity.kt').read_text()
source_reader_path = ROOT / 'apps/dpc/modules/installer/android/src/main/kotlin/io/dpcaio/installer/android/InstallSourceDetails.kt'

for needle in [
    'AppTab { ALL, SYSTEM, USER, FAVORITES, RECENT }',
    'private var currentTab = AppTab.ALL',
    'AppTab.ALL -> installedApps.size',
    'AppTab.ALL -> installedApps',
    'StateFilter { ALL, ENABLED, DISABLED }',
    'InstallSourceFilter { ALL, INSTALLED, PRELOADED }',
    'private var currentInstallSourceFilter = InstallSourceFilter.ALL',
    'renderInstallSourceBar()',
    'SOURCE ${filter.name}',
    'InstallSourceFilter.INSTALLED -> stateFiltered.filter { !it.installerPackage.isNullOrBlank() }',
    'InstallSourceFilter.PRELOADED -> stateFiltered.filter { it.installerPackage.isNullOrBlank() }',
    'TargetSdkFilter { ALL, CURRENT, LEGACY }',
    'private var currentTargetSdkFilter = TargetSdkFilter.ALL',
    'renderTargetSdkBar()',
    'TARGET SDK ${filter.name}',
    'TargetSdkFilter.CURRENT -> sourceFiltered.filter { it.targetSdk >= Build.VERSION.SDK_INT }',
    'TargetSdkFilter.LEGACY -> sourceFiltered.filter { it.targetSdk < Build.VERSION.SDK_INT }',
    'DebuggableFilter { ALL, DEBUGGABLE, RELEASE }',
    'private var currentDebuggableFilter = DebuggableFilter.ALL',
    'renderDebuggableBar()',
    'DEBUG ${filter.name}',
    'val debuggable: Boolean',
    'debuggable = info.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0',
    'DebuggableFilter.DEBUGGABLE -> targetFiltered.filter { it.debuggable }',
    'DebuggableFilter.RELEASE -> targetFiltered.filter { !it.debuggable }',
    'if (app.debuggable) "DEBUGGABLE" else "RELEASE"',
    'Search app name, package, UID or installer',
    'app.installerPackage?.lowercase()?.contains(query) == true',
    'filterSummary',
    'FILTERS: state=${currentStateFilter.name} • source=${currentInstallSourceFilter.name} • target=${currentTargetSdkFilter.name} • debug=${currentDebuggableFilter.name}',
    'Reset filters',
    'resetFilters()',
    'currentStateFilter = StateFilter.ALL',
    'currentInstallSourceFilter = InstallSourceFilter.ALL',
    'currentTargetSdkFilter = TargetSdkFilter.ALL',
    'currentDebuggableFilter = DebuggableFilter.ALL',
    'search.setText("")',
    'tabCandidateCount()',
    'SHOWING ${shown.size} / ${tabCandidateCount()}',
    'AppSort { NAME, PACKAGE, INSTALL_DATE, UPDATE_DATE }',
    'SortDirection { ASCENDING, DESCENDING }',
    'private var currentSortDirection = SortDirection.ASCENDING',
    'sortDirectionLabel()',
    'toggleSortDirection()',
    'A → Z',
    'Z → A',
    'Oldest → newest',
    'Newest → oldest',
    'if (currentSortDirection == SortDirection.DESCENDING) sorted.reversed() else sorted',
    'val uid: Int',
    'uid = info.uid',
    'app.uid.toString().contains(query)',
    'UID ${app.uid}',
    'val firstInstallTime: Long',
    'val lastUpdateTime: Long',
    'val targetSdk: Int',
    'val installerPackage: String?',
    'Open app info',
    'Permissions',
    'Update app',
    'Copy package',
    'Settings.ACTION_APPLICATION_DETAILS_SETTINGS',
    'AppPackageManagerActivity.MODE_UPDATER',
    'formatInstallDate(app.firstInstallTime)',
    'formatInstallDate(app.lastUpdateTime)',
    '"v${app.versionName} • SDK ${app.targetSdk} • Installed ${formatInstallDate(app.firstInstallTime)}"',
    'AndroidInstallSourceReader',
    'currentStateFilter',
    'selectedPackages',
    'Select visible',
    'Clear selection',
    'Copy selected packages',
    'Favorite selected',
    'AppOps',
    'AppOpsVerifiedEditorActivity.EXTRA_TARGET_PACKAGE',
    'filteredApps().take(visibleAppLimit)',
]:
    assert needle in hub, f'ActivityLauncherHubActivity.kt missing {needle}'

for needle in [
    'EXTRA_TARGET_PACKAGE',
    'intent.getStringExtra(EXTRA_TARGET_PACKAGE)',
    'targetPackage.setText',
]:
    assert needle in permission_manager, f'PermissionManagerActivity.kt missing {needle}'

assert source_reader_path.is_file(), 'shared InstallSourceDetails.kt missing'
source_reader = source_reader_path.read_text(encoding='utf-8')
for needle in [
    'data class InstallSourceDetails',
    'class AndroidInstallSourceReader',
    'getInstallSourceInfo',
    'getInstallerPackageName',
    'installingPackageName',
    'installingPackageLabel',
    'initiatingPackageName',
    'originatingPackageName',
    'packageSource',
    'updateOwnerPackageName',
]:
    assert needle in source_reader, f'Install source reader missing {needle}'

for needle in [
    'AndroidInstallSourceReader',
    'Installed by',
    'installingPackageName',
    'installingPackageLabel',
]:
    assert needle in package_manager, f'AppPackageManagerActivity.kt missing install-source UI token {needle}'

print('ACTIVITY_LAUNCHER_APP_MANAGER_CONTRACT: PASS')