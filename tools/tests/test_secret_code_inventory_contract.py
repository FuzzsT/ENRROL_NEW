from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CORE = ROOT / 'apps/dpc/modules/engineering/core'
SRC = CORE / 'src/main/kotlin/io/dpcaio/engineering'
TEST = CORE / 'src/test/kotlin/io/dpcaio/engineering'


def need(path: Path, needles):
    assert path.exists(), f'missing {path.relative_to(ROOT)}'
    text = path.read_text(encoding='utf-8')
    for needle in needles:
        assert needle in text, f'{path.name}: missing {needle}'
    return text

need(CORE / 'build.gradle.kts', ['kotlin.jvm', 'jvmToolchain(21)'])

models = need(SRC / 'SecretCodeModels.kt', [
    'enum class SecretCodeSourceKind',
    'BASE_APK',
    'SPLIT_APK',
    'enum class SecretCodeDeclarationKind',
    'LITERAL_HOST',
    'PATTERN_ONLY',
    'data class SecretCodeDeclaration',
    'data class SecretCodeExtractionLimits',
    'sealed interface SecretCodeExtractionResult',
    'MALFORMED_AXML',
    'LIMIT_EXCEEDED',
])

reader = need(SRC / 'BinaryAndroidManifestReader.kt', [
    'object BinaryAndroidManifestReader',
    'STRING_POOL',
    'START_ELEMENT',
    'END_ELEMENT',
    'maxStringPoolBytes',
    'maxNodes',
])

extractor = need(SRC / 'SecretCodeManifestExtractor.kt', [
    'object SecretCodeManifestExtractor',
    'fun extract',
    'ZipFile',
    'AndroidManifest.xml',
    'android_secret_code',
    'android.telephony.action.SECRET_CODE',
    'android.provider.Telephony.SECRET_CODE',
    'PATTERN_ONLY',
    'maxManifestBytes',
    'maxCandidates',
])

for text in (models, reader, extractor):
    for forbidden in [
        'Runtime.getRuntime',
        'ProcessBuilder(',
        'DexClassLoader',
        'ClassLoader(',
        'Shizuku.newProcess',
        'su -c',
        'am broadcast',
    ]:
        assert forbidden not in text, f'forbidden execution API: {forbidden}'

need(TEST / 'BinaryAndroidManifestReaderTest.kt', [
    'fun main()',
    'malformed',
    'limit',
])
need(TEST / 'SecretCodeManifestExtractorTest.kt', [
    'fun main()',
    'LITERAL_HOST',
    'PATTERN_ONLY',
    'requiredPermission',
])

for fixture in [
    CORE / 'src/test/resources/fixtures/secret-code-manifest.axml.b64',
    CORE / 'src/test/resources/fixtures/pattern-only-manifest.axml.b64',
]:
    assert fixture.exists(), f'missing {fixture.relative_to(ROOT)}'

settings = need(ROOT / 'settings.gradle.kts', [
    '":engineering-core"',
    'apps/dpc/modules/engineering/core',
])
app_build = need(ROOT / 'apps/dpc/app/build.gradle.kts', [
    'implementation(project(":engineering-core"))',
])
host = need(ROOT / 'tools/run_host_tests.sh', [
    'apps/dpc/modules/engineering/core/src/main/kotlin',
    'apps/dpc/modules/engineering/core/src/test/kotlin',
    'io.dpcaio.engineering.BinaryAndroidManifestReaderTestKt',
    'io.dpcaio.engineering.SecretCodeManifestExtractorTestKt',
])

print('test_secret_code_inventory_contract: PASS')
