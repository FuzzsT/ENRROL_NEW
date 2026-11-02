from pathlib import Path

root = Path(__file__).resolve().parents[2]
app = root / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app'

settings = (app / 'DpcSettingsActivity.kt').read_text()
prefs = (app / 'DpcUiPreferences.kt').read_text()
auth = (app / 'InstallAuthorizationCoordinator.kt').read_text()
native = (app / 'NativeSupportManager.kt').read_text()
jar = (app / 'JarCallManager.kt').read_text()
runner = (app / 'NativeJarRunnerActivity.kt').read_text()
coordinator = (app / 'NativeJarOperationCoordinator.kt').read_text()
updates = (app / 'SelfUpdateCenterActivity.kt').read_text()
updater = (app / 'SelfUpdateActivity.kt').read_text()
manifest = (root / 'apps/dpc/app/src/main/AndroidManifest.xml').read_text()

for token in [
    'Require PIN before installing packages',
    'requestDisableWithPin',
    'Enable guarded native JAR calling',
    'DpcUiPreferences.setNativeJarCallingEnabled',
    'render()',
    'Open Native JAR Runner',
    'Open Update Center',
]:
    assert token in settings, token

for token in [
    'fun isEnabled(',
    'fun setEnabled(',
    'fun requestDisableWithPin(',
    'pinGuard.isConfigured()',
    'pinGuard.verify(pin)',
    'setEnabled(false)',
    'if (!isEnabled()) return true',
]:
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
    'NativeJarOperationCoordinator.tryStartExecution()',
    'NativeJarOperationCoordinator.finishExecution(',
    'executeReserved(',
]:
    assert token in jar, token
assert 'Runtime.getRuntime().exec("' not in jar, 'jar caller must not build a shell command string'
assert 'sh -c' not in jar and 'su -c' not in jar, 'jar caller must not route through an arbitrary shell'

for token in [
    'object NativeJarOperationCoordinator',
    'tryStartImport',
    'finishImport',
    'tryStartExecution',
    'finishExecution',
    'isExecutionReservation',
    '@Synchronized',
]:
    assert token in coordinator, token

for token in [
    'class NativeJarRunnerActivity',
    'Intent.ACTION_OPEN_DOCUMENT',
    'manager.privateJarDirectory()',
    'MAX_JAR_BYTES',
    'importInProgress',
    'executionInProgress',
    'if (importInProgress || executionInProgress)',
    'executionInProgress = true',
    'executionInProgress = false',
    'NativeJarOperationCoordinator.tryStartImport()',
    'NativeJarOperationCoordinator.finishImport(',
    'NativeJarOperationCoordinator.tryStartExecution()',
    'manager.executeReserved(',
    'setSingleLine(false)',
    'InputType.TYPE_TEXT_FLAG_MULTI_LINE',
    'Trusted SHA-256 from release/source (64 hex)',
    'trustedDigest != observedDigest',
    'expectedSha256 = trustedDigest',
    'Arguments — one argument per line',
    'io.dpcaio.jar.*',
]:
    assert token in runner, token
assert 'expectedSha256 = digest' not in runner, 'runner must not auto-trust the digest of the just-imported JAR'
assert 'sh -c' not in runner and 'su -c' not in runner, 'runner must not expose arbitrary shell execution'

for token in [
    'Check now',
    'Retry / re-check',
    'Discard cached candidate',
    'SelfUpdateUiStateStore',
    'SelfUpdateStore',
    'SelfUpdateOperationCoordinator',
    'operations.isBusy()',
    'SelfUpdateStatusReceiver.ACTION_STATE_CHANGED',
    'registerReceiver(stateChangedReceiver',
    'unregisterReceiver(stateChangedReceiver)',
    'candidate != null && !busy',
    'putExtra(SelfUpdateActivity.EXTRA_CHECK_NOW, true)',
    'if (checkNow && operations.isBusy()) return',
]:
    assert token in updates, token
assert updates.count('isEnabled = !busy') >= 3, 'auto-check entry points and automation must be disabled while self-update is busy'
for token in [
    'EXTRA_CHECK_NOW',
    'consumeLaunchAction()',
    'STATE_LAUNCH_ACTION_CONSUMED',
    'savedInstanceState?.getBoolean',
    'outState.putBoolean',
    'intent?.removeExtra(EXTRA_CHECK_NOW)',
    'checkNow()',
]:
    assert token in updater, token

for activity in ['DpcSettingsActivity', 'SelfUpdateCenterActivity', 'NativeJarRunnerActivity']:
    assert f'.{activity}' in manifest, activity

print('test_normal_mode_settings_native_jar_contract: PASS')
