from pathlib import Path

root = Path(__file__).resolve().parents[2]
prefs = root / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/DpcUiPreferences.kt'
factory = root / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ManagementContextFactory.kt'
assert prefs.is_file(), 'DpcUiPreferences.kt missing'
assert factory.is_file(), 'ManagementContextFactory.kt missing'
p = prefs.read_text(encoding='utf-8')
f = factory.read_text(encoding='utf-8')
for token in ['createDeviceProtectedStorageContext()', 'show_hidden', 'developer_mode', 'show_experimental', 'selected_filter']:
    assert token in p, f'missing preference contract: {token}'
for token in ['isDeviceOwnerApp', 'isProfileOwnerApp', 'isOrganizationOwnedDeviceWithManagedProfile', 'Build.VERSION.SDK_INT', 'KnoxRuntimeGate.isRealKnoxActive']:
    assert token in f, f'missing management context signal: {token}'
assert 'BuildConfig.FLAVOR' in f and 'BuildConfig.DEBUG' in f
print('test_management_context_contract: PASS')

app_gradle=(root/'apps/dpc/app/build.gradle.kts').read_text()
assert 'buildConfig = true' in app_gradle, 'BuildConfig generation must be explicit'
