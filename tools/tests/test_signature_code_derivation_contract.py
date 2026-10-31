#!/usr/bin/env python3
from pathlib import Path
import json
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
CORE_ROOT = ROOT / 'apps/dpc/modules/engineering/core'
CORE = CORE_ROOT / 'src/main/kotlin/io/dpcaio/engineering'
RECIPE_TEST = CORE_ROOT / 'src/test/kotlin/io/dpcaio/engineering/SignatureCodeRecipeTest.kt'
ENGINE_TEST = CORE_ROOT / 'src/test/kotlin/io/dpcaio/engineering/SignatureCodeDerivationEngineTest.kt'
ENDIAN_TEST = CORE_ROOT / 'src/test/kotlin/io/dpcaio/engineering/SignatureCodeEndianTest.kt'
VENDOR_TEST = CORE_ROOT / 'src/test/kotlin/io/dpcaio/engineering/SignatureCodeVendorProfileMatcherTest.kt'
SAMPLE_TEST = CORE_ROOT / 'src/test/kotlin/io/dpcaio/engineering/SignatureCodeSampleMatcherTest.kt'
VENDOR_ASSET = ROOT / 'apps/dpc/app/src/main/assets/signature_code_vendor_profiles.json'


def need(path: Path, needles):
    if not path.exists():
        raise AssertionError(f'missing {path.relative_to(ROOT)}')
    text = path.read_text(encoding='utf-8')
    for needle in needles:
        if needle not in text:
            raise AssertionError(f'{path.relative_to(ROOT)}: missing {needle!r}')
    return text

models = need(CORE / 'SignatureCodeModels.kt', [
    'data class SigningCertificateIdentity',
    'data class AppSigningIdentity',
    'enum class DerivedCodeKind',
    'enum class SignatureCodeSource',
    'data class DerivationStage',
    'data class DerivedCodeCandidate',
    'data class SignatureCodeLimits',
])
recipe = need(CORE / 'SignatureCodeRecipe.kt', [
    'enum class SignatureCodeInputKind',
    'sealed interface SignatureCodeStep',
    'data class SignatureCodeRecipe',
    'object SignatureCodeRecipeValidator',
    'PIPELINE_TOO_LONG',
    'INVALID_MODULUS',
    'SLICE_OUT_OF_RANGE',
    'TYPE_MISMATCH',
    'OUTPUT_KIND_MISMATCH',
    'Sha256',
    'Sha1',
    'Crc8',
    'Crc16',
    'Crc32',
    'Xor8',
    'Sum8',
    'Sum16',
    'HexUpper',
    'HexLower',
    'Ascii',
    'UIntBe',
    'UIntLe',
    'PrefixBytes',
    'SuffixBytes',
    'SliceBytes',
    'PrefixChars',
    'SuffixChars',
    'SliceChars',
    'Mod',
    'Decimal',
    'PadLeft',
    'LuhnAppend',
    'LuhnValidate',
])
engine = need(CORE / 'SignatureCodeDerivationEngine.kt', [
    'class SignatureCodeDerivationEngine',
    'sealed interface SignatureCodeDerivationResult',
    'enum class SignatureCodeDerivationError',
    'INPUT_TOO_LARGE',
    'NUMERIC_OVERFLOW',
    'LUHN_INVALID',
    'MessageDigest',
    'CRC32',
])
catalog = need(CORE / 'AutoSignatureCodeCatalog.kt', [
    'object AutoSignatureCodeCatalog',
    'fun recipes()',
    'sha256',
    'crc32',
])
vendor_models = need(CORE / 'SignatureCodeVendorProfile.kt', [
    'data class SignatureCodeVendorProfile',
    'enum class VendorProfileConfidence',
    'enum class VendorProfileMatchReason',
    'data class VendorProfileMatch',
])
vendor_matcher = need(CORE / 'SignatureCodeVendorProfileMatcher.kt', [
    'object SignatureCodeVendorProfileMatcher',
    'fun match(',
    'EXACT_PACKAGE_EXACT_CERT',
    'PACKAGE_PREFIX_EXACT_CERT',
    'EXACT_PACKAGE',
    'PACKAGE_PREFIX',
    'SignatureCodeRecipeValidator.validate',
])
sample_matcher = need(CORE / 'SignatureCodeSampleMatcher.kt', [
    'data class SignatureCodeSample',
    'data class SignatureCodeSampleMatch',
    'enum class SampleMatchConfidence',
    'data class SampleMatcherLimits',
    'enum class SignatureCodeSampleMatcherError',
    'sealed interface SignatureCodeSampleMatcherResult',
    'object SignatureCodeSampleMatcher',
    'fun match(',
    'SAMPLE_LIMIT_EXCEEDED',
    'SEARCH_LIMIT_REACHED',
    'maxSamples: Int = 32',
    'maxRecipesEvaluated: Int = 5_000',
    'setOf(2, 3, 4, 6, 8)',
    'setOf(4, 5, 6, 7, 8)',
    'SignatureCodeDerivationEngine',
])
need(RECIPE_TEST, ['SignatureCodeRecipeTest: PASS', 'SignatureCodeStep.Ascii', 'OUTPUT_KIND_MISMATCH'])
need(ENGINE_TEST, [
    'SignatureCodeDerivationEngineTest: PASS',
    '799182',
    '49918566',
    '47457659',
    'LUHN_INVALID',
    'NUMERIC_OVERFLOW',
])
need(ENDIAN_TEST, [
    'SignatureCodeEndianTest: PASS',
    '19088743',
    '1732584193',
    'SignatureCodeStep.UIntBe',
    'SignatureCodeStep.UIntLe',
])
need(VENDOR_TEST, [
    'SignatureCodeVendorProfileMatcherTest: PASS',
    'subject-only',
    'cert-only',
    'invalid-recipe',
    'disabled-profile',
])
need(SAMPLE_TEST, [
    'SignatureCodeSampleMatcherTest: PASS',
    'SampleMatchConfidence.HIGH',
    'SampleMatchConfidence.UNVERIFIED',
    'SAMPLE_LIMIT_EXCEEDED',
    'SearchLimitReached',
    'ambiguityCount',
])

if not VENDOR_ASSET.exists():
    raise AssertionError(f'missing {VENDOR_ASSET.relative_to(ROOT)}')
asset = json.loads(VENDOR_ASSET.read_text(encoding='utf-8'))
if asset.get('schemaVersion') != 1:
    raise AssertionError('signature_code_vendor_profiles.json: schemaVersion must be 1')
profiles = asset.get('profiles')
if not isinstance(profiles, list):
    raise AssertionError('signature_code_vendor_profiles.json: profiles must be a list')
for index, profile in enumerate(profiles):
    if not isinstance(profile, dict):
        raise AssertionError(f'vendor profile #{index}: must be an object')
    if profile.get('enabled', True) and profile.get('verified') is not True:
        raise AssertionError(f'vendor profile #{index}: enabled production profile must be verified')
    if profile.get('enabled', True) and not str(profile.get('provenance', '')).strip():
        raise AssertionError(f'vendor profile #{index}: enabled production profile requires provenance')

combined = models + '\n' + recipe + '\n' + engine + '\n' + catalog + '\n' + vendor_models + '\n' + vendor_matcher + '\n' + sample_matcher
for forbidden in [
    'ScriptEngine',
    'Runtime.getRuntime',
    'ProcessBuilder',
    'Shizuku.newProcess',
    'eval(',
    'bruteForce',
    'masterCode',
    'unlockCodeGenerator',
    'wordlist',
    'dictionaryAttack',
    'candidateKey',
]:
    if forbidden in combined:
        raise AssertionError(f'forbidden execution/search primitive present: {forbidden}')

sources = sorted(
    str(path)
    for source_root in [CORE_ROOT / 'src/main/kotlin', CORE_ROOT / 'src/test/kotlin']
    for path in source_root.rglob('*.kt')
)
with tempfile.TemporaryDirectory(prefix='signature-code-contract-') as tmp:
    classes = Path(tmp) / 'classes'
    classes.mkdir()
    subprocess.run(['kotlinc', *sources, '-d', str(classes)], cwd=ROOT, check=True)
    for main, marker in [
        ('io.dpcaio.engineering.SignatureCodeRecipeTestKt', 'SignatureCodeRecipeTest: PASS'),
        ('io.dpcaio.engineering.SignatureCodeDerivationEngineTestKt', 'SignatureCodeDerivationEngineTest: PASS'),
        ('io.dpcaio.engineering.SignatureCodeEndianTestKt', 'SignatureCodeEndianTest: PASS'),
        ('io.dpcaio.engineering.SignatureCodeVendorProfileMatcherTestKt', 'SignatureCodeVendorProfileMatcherTest: PASS'),
        ('io.dpcaio.engineering.SignatureCodeSampleMatcherTestKt', 'SignatureCodeSampleMatcherTest: PASS'),
    ]:
        result = subprocess.run(
            ['kotlin', '-classpath', str(classes), main],
            cwd=ROOT,
            check=True,
            text=True,
            capture_output=True,
        )
        if marker not in result.stdout:
            raise AssertionError(f'{main}: unexpected output: {result.stdout!r} {result.stderr!r}')

print('test_signature_code_derivation_contract: PASS')
