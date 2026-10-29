from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
hub = (ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityLauncherHubActivity.kt').read_text()
perm = (ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/PermissionManagerActivity.kt').read_text()
installer = (ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/AppPackageManagerActivity.kt').read_text()

for needle in [
    'StateFilter { ALL, ENABLED, DISABLED }',
    'AppSort { NAME, PACKAGE, INSTALL_DATE, UPDATE_DATE }',
    'val lastUpdateTime: Long',
    'val targetSdk: Int',
    'val installerPackage: String?',
    'Open app info',
    'Permissions',
    'Update app',
    'Settings.ACTION_APPLICATION_DETAILS_SETTINGS',
    'PermissionManagerActivity.EXTRA_TARGET_PACKAGE',
    'AppPackageManagerActivity.EXTRA_TARGET_PACKAGE',
    'formatInstallDate(app.lastUpdateTime)',
]:
    assert needle in hub, f'ActivityLauncherHubActivity.kt missing {needle}'

for needle in [
    'EXTRA_TARGET_PACKAGE',
    'intent.getStringExtra(EXTRA_TARGET_PACKAGE)',
    'targetPackage.setText',
]:
    assert needle in perm, f'PermissionManagerActivity.kt missing {needle}'

for needle in [
    'EXTRA_TARGET_PACKAGE',
    'requestedTargetPackage',
    'indexOfFirst { it.packageName == requestedTargetPackage }',
]:
    assert needle in installer, f'AppPackageManagerActivity.kt missing {needle}'

print('ACTIVITY_LAUNCHER_APP_MANAGER_CONTRACT: PASS')
