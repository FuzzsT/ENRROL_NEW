from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app'

control = (APP / 'ControlCenterActivity.kt').read_text(encoding='utf-8') if (APP / 'ControlCenterActivity.kt').exists() else ''
launcher = (APP / 'ActivityLauncherHubActivity.kt').read_text(encoding='utf-8') if (APP / 'ActivityLauncherHubActivity.kt').exists() else ''
dashboard = (APP / 'AioDashboardActivity.kt').read_text(encoding='utf-8')
manifest = (ROOT / 'apps/dpc/app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
workflow = (ROOT / '.github/workflows/pr-android-ci.yml').read_text(encoding='utf-8')

for token in [
    'enum class ControlTab',
    'FUNCTIONS',
    'MODULES',
    'SHIZUKU',
    'AndroidShizukuRuntime',
    'ShizukuUserServiceClient',
    'requestPermission',
    'binderAlive',
    'permissionGranted',
    'identity()',
    'DpcModuleRegistry.modules',
]:
    assert token in control, f'Control Center contract missing: {token}'

for token in [
    'enum class AppTab',
    'SYSTEM',
    'USER',
    'FAVORITES',
    'RECENT',
    'getInstalledApplications',
    'ApplicationInfo.FLAG_SYSTEM',
    'SharedPreferences',
    'favoritePackages',
    'recentPackages',
    'ActivityExplorerActivity',
    'putExtra("package"',
    'AndroidShizukuRuntime',
]:
    assert token in launcher, f'Activity Launcher Hub contract missing: {token}'

assert 'ControlCenterActivity::class.java' in dashboard
assert 'ActivityLauncherHubActivity::class.java' in dashboard
assert '.ControlCenterActivity' in manifest
assert '.ActivityLauncherHubActivity' in manifest
assert 'test_control_center_shizuku_launcher_contract.py' in workflow

print('CONTROL_CENTER_SHIZUKU_LAUNCHER_CONTRACT: PASS')
