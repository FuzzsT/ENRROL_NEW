#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CORE = ROOT / 'apps/dpc/modules/engineering/core/src/main/kotlin/io/dpcaio/engineering'
TEST = ROOT / 'apps/dpc/modules/engineering/core/src/test/kotlin/io/dpcaio/engineering/SignatureCodeRecipeTest.kt'


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
need(TEST, ['SignatureCodeRecipeTest: PASS'])

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

print('test_signature_code_derivation_contract: PASS')
