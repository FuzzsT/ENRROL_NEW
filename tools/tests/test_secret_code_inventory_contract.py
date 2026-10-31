from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CORE = ROOT / 'apps/dpc/modules/engineering/core'
SRC = CORE / 'src/main/kotlin/io/dpcaio/engineering'
TEST = CORE / 'src/test/kotlin/io/dpcaio/engineering'
APP_ENG = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/engineering'


def need(path: Path, needles):
    assert path.exists(), f'missing {path.relative_to(ROOT)}'
    text = path.read_text(encoding='utf-8')
    for needle in needles:
        assert needle in text, f'{path.name}: missing {needle}'
    return text

need(CORE / 'build.gradle.kts', ['kotlin.jvm', 'jvmToolchain(21)'])

models = need(SRC / 'SecretCodeModels.kt', [
    'enum class SecretCodeSourceKind', 'BASE_APK', 'SPLIT_APK',
    'enum class SecretCodeDeclarationKind', 'LITERAL_HOST', 'PATTERN_ONLY',
    'data class SecretCodeDeclaration', 'data class SecretCodeExtractionLimits',
    'sealed interface SecretCodeExtractionResult', 'MALFORMED_AXML', 'LIMIT_EXCEEDED',
])
reader = need(SRC / 'BinaryAndroidManifestReader.kt', [
    'object BinaryAndroidManifestReader', 'STRING_POOL', 'START_ELEMENT', 'END_ELEMENT',
    'maxStringPoolBytes', 'maxNodes',
])
extractor = need(SRC / 'SecretCodeManifestExtractor.kt', [
    'object SecretCodeManifestExtractor', 'fun extract', 'ZipFile', 'AndroidManifest.xml',
    'android_secret_code', 'android.telephony.action.SECRET_CODE',
    'android.provider.Telephony.SECRET_CODE', 'PATTERN_ONLY', 'maxManifestBytes', 'maxCandidates',
])

scanner = need(APP_ENG / 'InstalledPackageSecretCodeScanner.kt', [
    'data class SecretCodeInventoryItem',
    'data class SecretCodeScanProgress',
    'sealed interface SecretCodeScanEvent',
    'class InstalledPackageSecretCodeScanner',
    'getInstalledApplications',
    'sourceDir',
    'splitSourceDirs',
    'SecretCodeManifestExtractor.extract',
    'cancelled()',
])
verifier = need(APP_ENG / 'SecretCodeRuntimeVerifier.kt', [
    'enum class SecretCodeVerification',
    'VERIFIED', 'DECLARED_ONLY', 'DISABLED', 'PERMISSION_GATED', 'NOT_EXPORTED',
    'NOT_VISIBLE', 'PATTERN_ONLY', 'PACKAGE_GONE', 'ERROR',
    'class SecretCodeRuntimeVerifier',
    'queryBroadcastReceivers',
    'TelephonyManager.ACTION_SECRET_CODE',
    'android.provider.Telephony.SECRET_CODE',
    'MATCH_DISABLED_COMPONENTS',
])
repository = need(APP_ENG / 'SecretCodeInventoryRepository.kt', [
    'class SecretCodeInventoryRepository',
    'fun scan(',
    'fun invalidate()',
    'distinctBy',
])
discovery = need(APP_ENG / 'SamsungSecretCodeDiscovery.kt', [
    'SamsungSecretCodeCatalog.normalize',
    'SecretCodeRuntimeVerifier',
])

for text in (models, reader, extractor, scanner, verifier, repository, discovery):
    for forbidden in [
        'Runtime.getRuntime', 'ProcessBuilder(', 'DexClassLoader', 'ClassLoader(',
        'Shizuku.newProcess', 'su -c', 'am broadcast', 'sendBroadcast(',
        'startService(', 'startForegroundService(',
    ]:
        assert forbidden not in text, f'forbidden execution API: {forbidden}'

for needle in ['packageName', 'receiverClassName', 'normalizedCode', 'actions', 'sourceKind']:
    assert needle in repository, f'SecretCodeInventoryRepository.kt: dedupe key missing {needle}'

need(TEST / 'BinaryAndroidManifestReaderTest.kt', ['fun main()', 'malformed', 'limit'])
need(TEST / 'SecretCodeManifestExtractorTest.kt', [
    'fun main()', 'LITERAL_HOST', 'PATTERN_ONLY', 'requiredPermission',
])
for fixture in [
    CORE / 'src/test/resources/fixtures/secret-code-manifest.axml.b64',
    CORE / 'src/test/resources/fixtures/pattern-only-manifest.axml.b64',
]:
    assert fixture.exists(), f'missing {fixture.relative_to(ROOT)}'

need(ROOT / 'settings.gradle.kts', ['\":engineering-core\"', 'apps/dpc/modules/engineering/core'])
need(ROOT / 'apps/dpc/app/build.gradle.kts', ['implementation(project(\":engineering-core\"))'])
need(ROOT / 'tools/run_host_tests.sh', [
    'apps/dpc/modules/engineering/core/src/main/kotlin',
    'apps/dpc/modules/engineering/core/src/test/kotlin',
    'io.dpcaio.engineering.BinaryAndroidManifestReaderTestKt',
    'io.dpcaio.engineering.SecretCodeManifestExtractorTestKt',
])

print('test_secret_code_inventory_contract: PASS')
