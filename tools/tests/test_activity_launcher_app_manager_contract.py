from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
hub = (ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityLauncherHubActivity.kt').read_text()
permission_manager = (ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/PermissionManagerActivity.kt').read_text()
package_manager = (ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/AppPackageManagerActivity.kt').read_text()
source_reader_path = ROOT / 'apps/dpc/modules/installer/android/src/main/kotlin/io/dpcaio/installer/android/InstallSourceDetails.kt'

for needle in [
    'StateFilter { ALL, ENABLED, DISABLED }',
    'AppSort { NAME, PACKAGE, INSTALL_DATE, UPDATE_DATE }',
    'val lastUpdateTime: Long',
    'val targetSdk: Int',
    'val installerPackage: String?',
    'Open app info',
    'Permissions',
    'Update app',
    'Copy package',
    'Settings.ACTION_APPLICATION_DETAILS_SETTINGS',
    'AppPackageManagerActivity.MODE_UPDATER',
    'formatInstallDate(app.lastUpdateTime)',
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
