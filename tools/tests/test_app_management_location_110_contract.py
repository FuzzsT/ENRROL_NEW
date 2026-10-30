from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
whole=ROOT/'apps/dpc/modules/app-management/android/src/main/kotlin/io/dpcaio/appmanager/android/AndroidWholeAppStateGateway.kt'
restr=ROOT/'apps/dpc/modules/policy/android/src/main/kotlin/io/dpcaio/policy/android/AndroidApplicationRestrictionsCoordinator.kt'
loc=ROOT/'apps/dpc/modules/policy/android/src/main/kotlin/io/dpcaio/policy/android/AndroidGlobalLocationPolicyGateway.kt'
hub=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityLauncherHubActivity.kt'
runner=ROOT/'tools/run_host_tests.sh'
assert whole.exists() and restr.exists() and loc.exists(), '1.1.0 app-management gateways missing'
assert hub.exists(), 'ActivityLauncherHubActivity.kt missing'
assert runner.exists(), 'tools/run_host_tests.sh missing'
wt=whole.read_text('utf-8'); rt=restr.read_text('utf-8'); lt=loc.read_text('utf-8'); ht=hub.read_text('utf-8'); runner_text=runner.read_text('utf-8')
for needle in ['WholeAppStatePlanner','setApplicationEnabledSetting','setPackageEnabled','protectionDecision','readback']:
    assert needle in wt, needle
for needle in ['setApplicationRestrictions','getApplicationRestrictions','Executors.newSingleThreadExecutor','canonical']:
    assert needle in rt, needle
assert 'bundle.get(key)' not in rt, 'managed-configuration readback must not use deprecated Bundle.get(String)'
assert 'bundle.getString(key).orEmpty()' in rt, 'string-only managed-configuration readback must use typed Bundle.getString(String)'
for needle in ['isDeviceOwnerApp','setLocationEnabled','LocationManager','isLocationEnabled']:
    assert needle in lt, needle
assert 'Settings.Secure' not in lt
for needle in [
    'private var suppressSearchRender = false',
    'if (!suppressSearchRender) renderApps()',
    'suppressSearchRender = true',
    'search.setText("")',
    'suppressSearchRender = false',
]:
    assert needle in ht, f'ActivityLauncher reset single-render contract missing {needle}'
assert 'Installer: ${app.installerPackage ?: "unknown"}' in ht, 'ActivityLauncher must use neutral installer fallback label'
assert 'unknown / preloaded' not in ht, 'ActivityLauncher must not infer preloaded state from missing installer source'
assert 'Search app name, package, version, UID or installer' in ht, 'ActivityLauncher search hint must mention version'
assert 'app.versionName.lowercase().contains(query)' in ht, 'ActivityLauncher search must match versionName'
for test_name in [
    'test_activity_launcher_single_reset_render_contract.py',
    'test_activity_launcher_active_filter_count_contract.py',
    'test_activity_launcher_compact_filter_controls_contract.py',
    'test_activity_launcher_app_manager_contract.py',
    'test_activity_launcher_lazy_icons_contract.py',
]:
    assert f'python3 "$ROOT/tools/tests/{test_name}"' in runner_text, f'run_host_tests.sh must execute {test_name}'
print('test_app_management_location_110_contract: PASS')
