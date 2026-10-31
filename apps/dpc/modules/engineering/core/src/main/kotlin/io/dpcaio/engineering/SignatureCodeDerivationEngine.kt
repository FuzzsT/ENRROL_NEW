package io.dpcaio.engineering

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.CRC32

enum class SignatureCodeDerivationError {
    INVALID_RECIPE,
    INPUT_TOO_LARGE,
    SLICE_OUT_OF_RANGE,
    NUMERIC_OVERFLOW,
    INVALID_ASCII,
    INVALID_DIGITS,
    LUHN_INVALID,
    INTERNAL_TYPE_MISMATCH,
}

sealed interface SignatureCodeDerivationResult {
    data class Success(val candidate: DerivedCodeCandidate) : SignatureCodeDerivationResult
    data class Failure(
        val error: SignatureCodeDerivationError,
        val message: String,
    ) : SignatureCodeDerivationResult
}

class SignatureCodeDerivationEngine {
    fun derive(
        identity: AppSigningIdentity,
        recipe: SignatureCodeRecipe,
        signer: SigningCertificateIdentity,
        limits: SignatureCodeLimits = SignatureCodeLimits(),
    ): SignatureCodeDerivationResult {
        val validation = SignatureCodeRecipeValidator.validate(recipe, limits)
        if (validation is SignatureCodeRecipeValidation.Invalid) {
            return SignatureCodeDerivationResult.Failure(
                SignatureCodeDerivationError.INVALID_RECIPE,
                "${validation.error}: ${validation.message}",
            )
        }

        val input = composeInput(identity, signer, recipe.input)
        if (input.size > limits.maxInputBytes) {
            return SignatureCodeDerivationResult.Failure(
                SignatureCodeDerivationError.INPUT_TOO_LARGE,
                "Input has ${input.size} bytes; max is ${limits.maxInputBytes}",
            )
        }

        var value: Value = Value.Bytes(input)
        val stages = mutableListOf(
            DerivationStage("INPUT(${recipe.input.name})", render(value)),
        )

        for (step in recipe.steps) {
            val next = applyStep(value, step)
            if (next is StepResult.Failure) {
                return SignatureCodeDerivationResult.Failure(next.error, next.message)
            }
            value = (next as StepResult.Success).value
            stages += DerivationStage(stepName(step), render(value))
        }

        val output = when (value) {
            is Value.Bytes -> value.data.toHex(upper = recipe.outputKind != DerivedCodeKind.TEXT)
            is Value.Text -> value.data
            is Value.Unsigned -> value.data.toString()
        }

        return SignatureCodeDerivationResult.Success(
            DerivedCodeCandidate(
                packageName = identity.packageName,
                signerFingerprintSha256 = signer.sha256Hex,
                recipeId = recipe.id,
                recipeName = recipe.displayName,
                candidate = output,
                candidateKind = recipe.outputKind,
                source = SignatureCodeSource.AUTO,
                stages = stages,
            )
        )
    }

    private fun composeInput(
        identity: AppSigningIdentity,
        signer: SigningCertificateIdentity,
        kind: SignatureCodeInputKind,
    ): ByteArray {
        val packageBytes = identity.packageName.toByteArray(StandardCharsets.UTF_8)
        val lowerPackageBytes = identity.packageName.lowercase(Locale.ROOT).toByteArray(StandardCharsets.UTF_8)
        val upperPackageBytes = identity.packageName.uppercase(Locale.ROOT).toByteArray(StandardCharsets.UTF_8)
        val sha256Bytes = signer.sha256Hex.toByteArray(StandardCharsets.US_ASCII)
        val sha1Bytes = signer.sha1Hex.toByteArray(StandardCharsets.US_ASCII)
        return when (kind) {
            SignatureCodeInputKind.CERT_DER -> signer.derBytes.copyOf()
            SignatureCodeInputKind.CERT_SHA256_HEX -> sha256Bytes
            SignatureCodeInputKind.CERT_SHA1_HEX -> sha1Bytes
            SignatureCodeInputKind.PACKAGE_NAME_UTF8 -> packageBytes
            SignatureCodeInputKind.PACKAGE_NAME_LOWER_UTF8 -> lowerPackageBytes
            SignatureCodeInputKind.PACKAGE_NAME_UPPER_UTF8 -> upperPackageBytes
            SignatureCodeInputKind.CERT_DER_THEN_PACKAGE -> signer.derBytes + packageBytes
            SignatureCodeInputKind.PACKAGE_THEN_CERT_DER -> packageBytes + signer.derBytes
            SignatureCodeInputKind.CERT_SHA256_HEX_THEN_PACKAGE -> sha256Bytes + packageBytes
            SignatureCodeInputKind.PACKAGE_THEN_CERT_SHA256_HEX -> packageBytes + sha256Bytes
        }
    }

    private fun applyStep(value: Value, step: SignatureCodeStep): StepResult = when (step) {
        SignatureCodeStep.Sha256 -> bytes(value) { Value.Bytes(MessageDigest.getInstance("SHA-256").digest(it)) }
        SignatureCodeStep.Sha1 -> bytes(value) { Value.Bytes(MessageDigest.getInstance("SHA-1").digest(it)) }
        SignatureCodeStep.Crc8 -> bytes(value) { Value.Unsigned(crc8Smbus(it).toULong()) }
        SignatureCodeStep.Crc16 -> bytes(value) { Value.Unsigned(crc16CcittFalse(it).toULong()) }
        SignatureCodeStep.Crc32 -> bytes(value) {
            val crc = CRC32().apply { update(it) }
            Value.Unsigned(crc.value.toULong())
        }
        SignatureCodeStep.Xor8 -> bytes(value) { data ->
            var result = 0
            data.forEach { result = result xor it.toUByte().toInt() }
            Value.Unsigned(result.toULong())
        }
        SignatureCodeStep.Sum8 -> bytes(value) { data ->
            val sum = data.fold(0L) { acc, byte -> acc + byte.toUByte().toLong() }
            Value.Unsigned((sum and 0xFFL).toULong())
        }
        SignatureCodeStep.Sum16 -> bytes(value) { data ->
            val sum = data.fold(0L) { acc, byte -> acc + byte.toUByte().toLong() }
            Value.Unsigned((sum and 0xFFFFL).toULong())
        }
        SignatureCodeStep.HexUpper -> bytes(value) { Value.Text(it.toHex(upper = true)) }
        SignatureCodeStep.HexLower -> bytes(value) { Value.Text(it.toHex(upper = false)) }
        SignatureCodeStep.Ascii -> {
            val data = (value as? Value.Bytes)?.data ?: return typeFailure(step, value)
            if (data.any { it.toUByte().toInt() > 0x7F }) {
                StepResult.Failure(
                    SignatureCodeDerivationError.INVALID_ASCII,
                    "ASCII conversion requires bytes in the 0x00..0x7F range",
                )
            } else {
                StepResult.Success(Value.Text(String(data, StandardCharsets.US_ASCII)))
            }
        }
        SignatureCodeStep.UIntBe -> bytesToUnsigned(value, littleEndian = false)
        SignatureCodeStep.UIntLe -> bytesToUnsigned(value, littleEndian = true)
        is SignatureCodeStep.PrefixBytes -> sliceBytes(value, 0, step.count)
        is SignatureCodeStep.SuffixBytes -> {
            val data = (value as? Value.Bytes)?.data ?: return typeFailure(step, value)
            if (step.count > data.size) sliceFailure(step.count, data.size)
            else StepResult.Success(Value.Bytes(data.copyOfRange(data.size - step.count, data.size)))
        }
        is SignatureCodeStep.SliceBytes -> sliceBytes(value, step.offset, step.length)
        is SignatureCodeStep.PrefixChars -> sliceText(value, 0, step.count)
        is SignatureCodeStep.SuffixChars -> {
            val text = (value as? Value.Text)?.data ?: return typeFailure(step, value)
            if (step.count > text.length) sliceFailure(step.count, text.length)
            else StepResult.Success(Value.Text(text.substring(text.length - step.count)))
        }
        is SignatureCodeStep.SliceChars -> sliceText(value, step.offset, step.length)
        is SignatureCodeStep.Mod -> {
            val number = (value as? Value.Unsigned)?.data ?: return typeFailure(step, value)
            StepResult.Success(Value.Unsigned(number % step.modulus))
        }
        SignatureCodeStep.Decimal -> {
            val number = (value as? Value.Unsigned)?.data ?: return typeFailure(step, value)
            StepResult.Success(Value.Text(number.toString()))
        }
        is SignatureCodeStep.PadLeft -> {
            val text = (value as? Value.Text)?.data ?: return typeFailure(step, value)
            StepResult.Success(Value.Text(text.padStart(step.width, step.pad)))
        }
        SignatureCodeStep.LuhnAppend -> {
            val text = (value as? Value.Text)?.data ?: return typeFailure(step, value)
            if (!text.isDigits()) {
                StepResult.Failure(SignatureCodeDerivationError.INVALID_DIGITS, "Luhn requires decimal digits")
            } else {
                StepResult.Success(Value.Text(text + luhnCheckDigit(text)))
            }
        }
        SignatureCodeStep.LuhnValidate -> {
            val text = (value as? Value.Text)?.data ?: return typeFailure(step, value)
            if (!text.isDigits()) {
                StepResult.Failure(SignatureCodeDerivationError.INVALID_DIGITS, "Luhn requires decimal digits")
            } else if (!luhnValid(text)) {
                StepResult.Failure(SignatureCodeDerivationError.LUHN_INVALID, "Luhn validation failed")
            } else {
                StepResult.Success(value)
            }
        }
    }

    private inline fun bytes(value: Value, transform: (ByteArray) -> Value): StepResult {
        val data = (value as? Value.Bytes)?.data
            ?: return StepResult.Failure(SignatureCodeDerivationError.INTERNAL_TYPE_MISMATCH, "Expected bytes")
        return StepResult.Success(transform(data))
    }

    private fun bytesToUnsigned(value: Value, littleEndian: Boolean): StepResult {
        val data = (value as? Value.Bytes)?.data
            ?: return StepResult.Failure(SignatureCodeDerivationError.INTERNAL_TYPE_MISMATCH, "Expected bytes")
        if (data.size > ULong.SIZE_BYTES) {
            return StepResult.Failure(
                SignatureCodeDerivationError.NUMERIC_OVERFLOW,
                "Unsigned integer conversion supports at most ${ULong.SIZE_BYTES} bytes; got ${data.size}",
            )
        }
        var result = 0uL
        if (littleEndian) {
            data.indices.reversed().forEach { index ->
                result = (result shl 8) or data[index].toUByte().toULong()
            }
        } else {
            data.forEach { byte -> result = (result shl 8) or byte.toUByte().toULong() }
        }
        return StepResult.Success(Value.Unsigned(result))
    }

    private fun sliceBytes(value: Value, offset: Int, length: Int): StepResult {
        val data = (value as? Value.Bytes)?.data
            ?: return StepResult.Failure(SignatureCodeDerivationError.INTERNAL_TYPE_MISMATCH, "Expected bytes")
        val end = offset.toLong() + length.toLong()
        if (offset < 0 || length <= 0 || end > data.size.toLong()) {
            return StepResult.Failure(
                SignatureCodeDerivationError.SLICE_OUT_OF_RANGE,
                "Byte slice offset=$offset length=$length exceeds size=${data.size}",
            )
        }
        return StepResult.Success(Value.Bytes(data.copyOfRange(offset, end.toInt())))
    }

    private fun sliceText(value: Value, offset: Int, length: Int): StepResult {
        val text = (value as? Value.Text)?.data
            ?: return StepResult.Failure(SignatureCodeDerivationError.INTERNAL_TYPE_MISMATCH, "Expected text")
        val end = offset.toLong() + length.toLong()
        if (offset < 0 || length <= 0 || end > text.length.toLong()) {
            return StepResult.Failure(
                SignatureCodeDerivationError.SLICE_OUT_OF_RANGE,
                "Text slice offset=$offset length=$length exceeds length=${text.length}",
            )
        }
        return StepResult.Success(Value.Text(text.substring(offset, end.toInt())))
    }

    private fun sliceFailure(requested: Int, available: Int) = StepResult.Failure(
        SignatureCodeDerivationError.SLICE_OUT_OF_RANGE,
        "Requested $requested elements; available=$available",
    )

    private fun typeFailure(step: SignatureCodeStep, value: Value) = StepResult.Failure(
        SignatureCodeDerivationError.INTERNAL_TYPE_MISMATCH,
        "${stepName(step)} cannot consume ${value::class.simpleName}",
    )

    private fun crc8Smbus(data: ByteArray): Int {
        var crc = 0
        data.forEach { byte ->
            crc = crc xor byte.toUByte().toInt()
            repeat(8) {
                crc = if ((crc and 0x80) != 0) ((crc shl 1) xor 0x07) and 0xFF else (crc shl 1) and 0xFF
            }
        }
        return crc
    }

    private fun crc16CcittFalse(data: ByteArray): Int {
        var crc = 0xFFFF
        data.forEach { byte ->
            crc = crc xor (byte.toUByte().toInt() shl 8)
            repeat(8) {
                crc = if ((crc and 0x8000) != 0) ((crc shl 1) xor 0x1021) and 0xFFFF else (crc shl 1) and 0xFFFF
            }
        }
        return crc
    }

    private fun luhnCheckDigit(text: String): Int {
        var sum = 0
        text.reversed().forEachIndexed { index, char ->
            var digit = char.digitToInt()
            if (index % 2 == 0) {
                digit *= 2
                if (digit > 9) digit -= 9
            }
            sum += digit
        }
        return (10 - (sum % 10)) % 10
    }

    private fun luhnValid(text: String): Boolean {
        var sum = 0
        text.reversed().forEachIndexed { index, char ->
            var digit = char.digitToInt()
            if (index % 2 == 1) {
                digit *= 2
                if (digit > 9) digit -= 9
            }
            sum += digit
        }
        return sum % 10 == 0
    }

    private fun String.isDigits(): Boolean = isNotEmpty() && all { it in '0'..'9' }

    private fun ByteArray.toHex(upper: Boolean): String {
        val digits = if (upper) "0123456789ABCDEF" else "0123456789abcdef"
        return buildString(size * 2) {
            for (byte in this@toHex) {
                val value = byte.toUByte().toInt()
                append(digits[value ushr 4])
                append(digits[value and 0x0F])
            }
        }
    }

    private fun render(value: Value): String = when (value) {
        is Value.Bytes -> {
            val limit = minOf(value.data.size, 64)
            val prefix = value.data.copyOfRange(0, limit).toHex(upper = true)
            if (limit == value.data.size) prefix else "$prefix…(${value.data.size} bytes)"
        }
        is Value.Text -> if (value.data.length <= 256) value.data else value.data.take(256) + "…(${value.data.length} chars)"
        is Value.Unsigned -> value.data.toString()
    }

    private fun stepName(step: SignatureCodeStep): String = when (step) {
        SignatureCodeStep.Sha256 -> "SHA256"
        SignatureCodeStep.Sha1 -> "SHA1"
        SignatureCodeStep.Crc8 -> "CRC8/SMBUS"
        SignatureCodeStep.Crc16 -> "CRC16/CCITT-FALSE"
        SignatureCodeStep.Crc32 -> "CRC32"
        SignatureCodeStep.Xor8 -> "XOR8"
        SignatureCodeStep.Sum8 -> "SUM8"
        SignatureCodeStep.Sum16 -> "SUM16"
        SignatureCodeStep.HexUpper -> "HEX_UPPER"
        SignatureCodeStep.HexLower -> "HEX_LOWER"
        SignatureCodeStep.Ascii -> "ASCII"
        SignatureCodeStep.UIntBe -> "UINT_BE"
        SignatureCodeStep.UIntLe -> "UINT_LE"
        is SignatureCodeStep.PrefixBytes -> "PREFIX_BYTES(${step.count})"
        is SignatureCodeStep.SuffixBytes -> "SUFFIX_BYTES(${step.count})"
        is SignatureCodeStep.SliceBytes -> "SLICE_BYTES(${step.offset},${step.length})"
        is SignatureCodeStep.PrefixChars -> "PREFIX_CHARS(${step.count})"
        is SignatureCodeStep.SuffixChars -> "SUFFIX_CHARS(${step.count})"
        is SignatureCodeStep.SliceChars -> "SLICE_CHARS(${step.offset},${step.length})"
        is SignatureCodeStep.Mod -> "MOD(${step.modulus})"
        SignatureCodeStep.Decimal -> "DECIMAL"
        is SignatureCodeStep.PadLeft -> "PAD_LEFT(${step.width},${step.pad})"
        SignatureCodeStep.LuhnAppend -> "LUHN_APPEND"
        SignatureCodeStep.LuhnValidate -> "LUHN_VALIDATE"
    }

    private sealed interface Value {
        data class Bytes(val data: ByteArray) : Value
        data class Text(val data: String) : Value
        data class Unsigned(val data: ULong) : Value
    }

    private sealed interface StepResult {
        data class Success(val value: Value) : StepResult
        data class Failure(
            val error: SignatureCodeDerivationError,
            val message: String,
        ) : StepResult
    }
}
