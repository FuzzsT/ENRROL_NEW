from pathlib import Path

root = Path(__file__).resolve().parents[2]
app = root / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app'
shizuku = root / 'apps/dpc/integrations/shizuku/src/main/kotlin/io/dpcaio/shizuku'

launcher = (app / 'ActivityLauncherHubActivity.kt').read_text()
control = (app / 'ControlCenterActivity.kt').read_text()
runtime = (shizuku / 'AndroidShizukuRuntime.kt').read_text()

for token in [
    'ImageView',
    'getApplicationIcon',
    'enum class AppSort',
    'NAME',
    'PACKAGE',
    'tabLabel(',
    'renderTabs()',
]:
    assert token in launcher, token

for token in [
    'fun observe(',
    'addBinderReceivedListenerSticky',
    'addBinderDeadListener',
    'addRequestPermissionResultListener',
    'removeBinderReceivedListener',
    'removeBinderDeadListener',
    'removeRequestPermissionResultListener',
]:
    assert token in runtime, token

for token in [
    'shizukuObserver',
    'override fun onStart()',
    'override fun onStop()',
    'shizukuRuntime.observe',
]:
    assert token in control, token

print('SHIZUKU_REACTIVE_LAUNCHER_ICONS_CONTRACT: PASS')
