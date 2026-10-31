#!/usr/bin/env python3
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
CORE_ROOT = ROOT / 'apps/dpc/modules/engineering/core'
CORE = CORE_ROOT / 'src/main/kotlin/io/dpcaio/engineering'
TEST = CORE_ROOT / 'src/test/kotlin/io/dpcaio/engineering/SignatureCodeRecipeTest.kt'


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
need(TEST, ['SignatureCodeRecipeTest: PASS', 'SignatureCodeStep.Ascii'])

combined = models + '\n' + recipe
for forbidden in [
    'ScriptEngine',
    'Runtime.getRuntime',
    'ProcessBuilder',
    'Shizuku.newProcess',
    'eval(',
    'bruteForce',
    'masterCode',
    'unlockCodeGenerator',
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
    result = subprocess.run(
        ['kotlin', '-classpath', str(classes), 'io.dpcaio.engineering.SignatureCodeRecipeTestKt'],
        cwd=ROOT,
        check=True,
        text=True,
        capture_output=True,
    )
    if 'SignatureCodeRecipeTest: PASS' not in result.stdout:
        raise AssertionError(f'unexpected Kotlin test output: {result.stdout!r} {result.stderr!r}')

print('test_signature_code_derivation_contract: PASS')
