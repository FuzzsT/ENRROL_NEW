from pathlib import Path

root = Path(__file__).resolve().parents[2]
app = root / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app'

settings = (app / 'DpcSettingsActivity.kt').read_text()
prefs = (app / 'DpcUiPreferences.kt').read_text()
auth = (app / 'InstallAuthorizationCoordinator.kt').read_text()
native = (app / 'NativeSupportManager.kt').read_text()
jar = (app / 'JarCallManager.kt').read_text()
runner = (app / 'NativeJarRunnerActivity.kt').read_text()
updates = (app / 'SelfUpdateCenterActivity.kt').read_text()
updater = (app / 'SelfUpdateActivity.kt').read_text()
manifest = (root / 'apps/dpc/app/src/main/AndroidManifest.xml').read_text()

for token in [
    'Require PIN before installing packages',
    'installAuthorization.setEnabled(checked)',
    'Enable guarded native JAR calling',
    'Open Native JAR Runner',
    'Open Update Center',
]:
    assert token in settings, token

for token in ['fun isEnabled(', 'fun setEnabled(', 'if (!isEnabled()) return true']:
    assert token in auth, token

for token in ['nativeJarCallingEnabled', 'setNativeJarCallingEnabled', 'showAdvancedHome', 'setShowAdvancedHome']:
    assert token in prefs, token

for token in ['Device Owner', 'Profile Owner', 'Dhizuku', 'Shizuku', 'app_process']:
    assert token in native, token

for token in [
    'expectedSha256',
    'ALLOWED_MAIN_CLASS_PREFIX',
    'io.dpcaio.jar.',
    'ProcessBuilder(command)',
    'environment()["CLASSPATH"]',
    'timeoutSeconds',
    'destroyForcibly',
    'StreamCollector(process.inputStream',
    'StreamCollector(process.errorStream',
    'MAX_OUTPUT_CHARS',
]:
    assert token in jar, token
assert 'Runtime.getRuntime().exec("' not in jar, 'jar caller must not build a shell command string'
assert 'sh -c' not in jar and 'su -c' not in jar, 'jar caller must not route through an arbitrary shell'

for token in [
    'class NativeJarRunnerActivity',
    'Intent.ACTION_OPEN_DOCUMENT',
    'manager.privateJarDirectory()',
    'MAX_JAR_BYTES',
    'expectedSha256 = digest',
    'Arguments — one argument per line',
    'io.dpcaio.jar.*',
]:
    assert token in runner, token
assert 'sh -c' not in runner and 'su -c' not in runner, 'runner must not expose arbitrary shell execution'

for token in [
    'Check now',
    'Retry / re-check',
    'Discard cached candidate',
    'SelfUpdateUiStateStore',
    'SelfUpdateStore',
    'putExtra(SelfUpdateActivity.EXTRA_CHECK_NOW, true)',
]:
    assert token in updates, token
for token in ['EXTRA_CHECK_NOW', 'consumeLaunchAction()', 'checkNow()']:
    assert token in updater, token

for activity in ['DpcSettingsActivity', 'SelfUpdateCenterActivity', 'NativeJarRunnerActivity']:
    assert f'.{activity}' in manifest, activity

print('test_normal_mode_settings_native_jar_contract: PASS')
