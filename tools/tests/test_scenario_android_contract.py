from pathlib import Path

root = Path(__file__).resolve().parents[2]
module = root / 'apps/dpc/modules/scenario/android'
assert module.exists(), 'missing scenario-android'
texts = '\n'.join(p.read_text(errors='ignore') for p in module.rglob('*') if p.is_file())
for required in [
    'ActivityLifecycleCallbacks',
    'TYPE_APPLICATION_OVERLAY',
    'Settings.canDrawOverlays',
    'ScenarioArchiveCodec',
    'ScenarioOverlayService',
    'recordIntent',
    'recordBroadcast',
]:
    assert required in texts, required

overlay = (module / 'src/main/kotlin/io/dpcaio/scenario/android/ScenarioOverlayService.kt').read_text(encoding='utf-8')
destroy_start = overlay.index('override fun onDestroy()')
destroy_end = overlay.index('\n    private fun showOverlay()', destroy_start)
destroy = overlay[destroy_start:destroy_end]
assert 'runCatching' in destroy and 'removeView' in destroy, \
    'Scenario overlay teardown must tolerate a view that was already detached'
assert destroy.index('root = null') < destroy.index('bridge.uninstall(application)'), \
    'Scenario overlay root reference must be cleared before bridge uninstall'
assert 'super.onDestroy()' in destroy, 'Scenario overlay must complete Service teardown'

print('test_scenario_android_contract: PASS')
