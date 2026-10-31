from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
CORE = ROOT / 'apps/dpc/modules/engineering/core/src/main/kotlin/io/dpcaio/engineering'
CORE_TEST = ROOT / 'apps/dpc/modules/engineering/core/src/test/kotlin/io/dpcaio/engineering'
APP = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app'
ENG = APP / 'engineering'


def need(path: Path, needles):
    assert path.exists(), f'missing {path.relative_to(ROOT)}'
    text = path.read_text(encoding='utf-8')
    for needle in needles:
        assert needle in text, f'{path.name}: missing {needle}'
    return text


models = need(CORE / 'SecretCodeInventoryModels.kt', [
    'enum class SecretCodeInventoryStatus',
    'VERIFIED',
    'PERMISSION_GATED',
    'DISABLED',
    'PATTERN_ONLY',
    'data class SecretCodeLiveReceiver',
    'data class SecretCodeInventoryRecord',
    'sourceKinds',
    'sourceApkNames',
])

reducer = need(CORE / 'SecretCodeInventoryReducer.kt', [
    'object SecretCodeInventoryReducer',
    'fun reduce(',
    'SecretCodeInventoryStatus.VERIFIED',
    'SecretCodeInventoryStatus.PERMISSION_GATED',
    'SecretCodeInventoryStatus.DISABLED',
    'SecretCodeInventoryStatus.PATTERN_ONLY',
])

scanner = need(ENG / 'SecretCodeInventoryScanner.kt', [
    'getInstalledApplications',
    'PackageManager.MATCH_ALL',
    'sourceDir',
    'splitSourceDirs',
    'SecretCodeManifestExtractor.extract',
    'SecretCodeSourceKind.BASE_APK',
    'SecretCodeSourceKind.SPLIT_APK',
    'queryBroadcastReceivers',
    'PackageManager.MATCH_DISABLED_COMPONENTS',
    'android_secret_code://',
    'SecretCodeInventoryReducer.reduce',
])
for forbidden in ['Runtime.getRuntime', 'Shizuku.newProcess', 'am broadcast', 'sendBroadcast(']:
    assert forbidden not in scanner, f'SecretCodeInventoryScanner.kt: forbidden {forbidden}'

activity = need(APP / 'SecretCodeInventoryActivity.kt', [
    'Secret Code Inventory',
    'Scan installed apps',
    'Cancel scan',
    'Search package / component / code',
    'Verified',
    'Declared only',
    'Protected',
    'Disabled',
    'Pattern only',
    'Sort package',
    'Sort code',
    'Sort status',
    'Copy code',
    'Copy component',
    'Copy record',
    'Re-probe selected',
    'SecretCodeInventoryRepository',
    'SecretCodeRuntimeVerifier',
    'ExecutorService',
    'runOnUiThread',
    'savedInstanceState',
    'onSaveInstanceState',
    'registerReceiver',
    'unregisterReceiver',
    'Intent.ACTION_PACKAGE_ADDED',
    'Intent.ACTION_PACKAGE_REMOVED',
    'Intent.ACTION_PACKAGE_CHANGED',
    'repository.invalidate()',
    'requiredPermission',
    'sourceApkName',
    'verification',
])
for forbidden in ['sendBroadcast(', 'ACTION_CALL', 'UssdIntentFactory', 'startService(', 'startForegroundService(']:
    assert forbidden not in activity, f'SecretCodeInventoryActivity.kt: forbidden {forbidden}'

center = need(APP / 'EngineeringIntentCenterActivity.kt', [
    'Secret Code Inventory',
    'SecretCodeInventoryActivity::class.java',
])
manifest = need(ROOT / 'apps/dpc/app/src/main/AndroidManifest.xml', [
    '.SecretCodeInventoryActivity',
])

with tempfile.TemporaryDirectory() as tmp:
    classes = Path(tmp) / 'classes'
    classes.mkdir()
    sources = [
        CORE / 'SecretCodeModels.kt',
        CORE / 'SecretCodeManifestExtractor.kt',
        CORE / 'BinaryAndroidManifestReader.kt',
        CORE / 'SecretCodeInventoryModels.kt',
        CORE / 'SecretCodeInventoryReducer.kt',
        CORE_TEST / 'SecretCodeInventoryReducerTest.kt',
    ]
    subprocess.run(['kotlinc', *map(str, sources), '-d', str(classes)], check=True)
    subprocess.run([
        'kotlin', '-classpath', str(classes),
        'io.dpcaio.engineering.SecretCodeInventoryReducerTestKt',
    ], check=True)

print('test_secret_code_inventory_runtime_contract: PASS')
