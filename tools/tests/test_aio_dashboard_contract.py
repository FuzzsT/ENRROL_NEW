from pathlib import Path
root=Path(__file__).resolve().parents[2]
manifest=(root/'apps/dpc/app/src/main/AndroidManifest.xml').read_text()
texts='\n'.join(p.read_text(errors='ignore') for p in (root/'apps/dpc/app/src/main/kotlin').rglob('*.kt'))
for cls in ['AioDashboardActivity','ActivityExplorerActivity','NetworkControlActivity','ScenarioLabActivity','NfcLabActivity','KnoxZtManagerActivity']:
    assert cls in texts, cls
    assert f'.{cls}' in manifest, cls
assert manifest.count('android.intent.action.MAIN') == 1
for required in ['AndroidActivityInventory','ActivityLaunchCoordinator','ShizukuActivityRouteExecutor','ScenarioOverlayService','NfcTagInspector','DeviceOwnerPrivateDnsController']:
    assert required in texts, required
print('test_aio_dashboard_contract: PASS')

dashboard=(root/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/AioDashboardActivity.kt').read_text()
for token in ['EnterprisePolicyHubActivity', 'DpcDiagnosticsActivity', 'DpcUiPreferences.read', 'developerMode', 'Advanced / Lab']:
    assert token in dashboard, token
scenario_pos=dashboard.index('Scenario Recorder / Replay')
nfc_pos=dashboard.index('NFC Lab')
guard_pos=dashboard.index('developerMode')
assert guard_pos < scenario_pos < nfc_pos, 'lab surfaces must be after developerMode guard'

application=(root/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/DpcAioApplication.kt').read_text()
ui=(root/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/UiSurfaceController.kt').read_text()
design=(root/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ModernUi.kt').read_text()
for token in ['UiSurfaceController.install(this)']:
    assert token in application, token
for token in [
    'ActivityLifecycleCallbacks',
    'setOnApplyWindowInsetsListener',
    'WindowInsets.Type.systemBars()',
    'requestApplyInsets()',
    'WeakHashMap',
    'translationY',
    'DecelerateInterpolator',
]:
    assert token in ui, token

# Android 15+ owns system-bar transparency under enforced edge-to-edge. Keep the
# old color setters only in an explicitly legacy, locally suppressed path.
assert 'Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM' in ui, 'system-bar color setters must be gated below API 35'
assert 'private fun configureLegacySystemBarColors(activity: Activity)' in ui, 'legacy system-bar color helper missing'
assert '@Suppress("DEPRECATION")\n    private fun configureLegacySystemBarColors' in ui, 'legacy bar-color deprecations must be locally suppressed'
configure_start=ui.index('private fun configureSystemBars')
configure_end=ui.index('private fun configureLegacySystemBarColors', configure_start)
configure_body=ui[configure_start:configure_end]
assert 'statusBarColor' not in configure_body, 'modern configureSystemBars path must not call deprecated statusBarColor'
assert 'navigationBarColor' not in configure_body, 'modern configureSystemBars path must not call deprecated navigationBarColor'
legacy_start=configure_end
legacy_end=ui.index('private fun installSafeAreaInsets', legacy_start)
legacy_body=ui[legacy_start:legacy_end]
assert 'window.statusBarColor = Color.TRANSPARENT' in legacy_body, 'legacy status-bar transparency must be preserved'
assert 'window.navigationBarColor = Color.TRANSPARENT' in legacy_body, 'legacy navigation-bar transparency must be preserved'

for token in [
    'addSection(body, "Setup & enrollment")',
    'addSection(body, "Management")',
    'addSection(body, "Tools")',
    'ModernUi.hero',
    'ModernUi.page',
    'ModernUi.compactCard',
    'ModernUi.action',
]:
    assert token in dashboard, token
for token in [
    'data class Palette','fun hero','fun section','fun card','fun chip','fun field','fun action','fun outputCard',
    'RippleDrawable','GradientDrawable','isFillViewport = true'
]:
    assert token in design, token

__import__('runpy').run_path(str(root/'tools/tests/test_engineering_intent_center_contract.py'), run_name='__main__')
