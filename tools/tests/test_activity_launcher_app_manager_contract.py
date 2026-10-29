from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
hub = (ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityLauncherHubActivity.kt').read_text()
permission_manager = (ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/PermissionManagerActivity.kt').read_text()

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
    'packageManager.getInstallSourceInfo',
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

print('ACTIVITY_LAUNCHER_APP_MANAGER_CONTRACT: PASS')
