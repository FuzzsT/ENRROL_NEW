package io.dpcaio.nfc

enum class NfcTraceOwnership { SYNTHETIC_TEST, OWNED_TEST, EXTERNAL }
enum class NfcTechnology {
    NFCA, NFCB, NFCF, NFCV, ISO_DEP, NDEF, NDEF_FORMATABLE, NFC_BARCODE, MIFARE_CLASSIC, MIFARE_ULTRALIGHT, OTHER
}

data class NfcExchange(val commandHex: String, val responseHex: String, val delayMs: Long)
data class NfcTrace(
    val ownership: NfcTraceOwnership,
    val technology: NfcTechnology,
    val credentialBearing: Boolean,
    val exchanges: List<NfcExchange>
)

data class NfcValidationResult(val allowed: Boolean, val reason: String)

class NfcReplayValidator {
    fun validate(trace: NfcTrace): NfcValidationResult {
        if (trace.ownership !in setOf(NfcTraceOwnership.SYNTHETIC_TEST, NfcTraceOwnership.OWNED_TEST)) {
            return NfcValidationResult(false, "TRACE_NOT_OWNED")
        }
        if (trace.credentialBearing) return NfcValidationResult(false, "CREDENTIAL_BEARING_TRACE")
        return NfcValidationResult(true, "OK")
    }

    fun responseFor(trace: NfcTrace, commandHex: String): String? {
        if (!validate(trace).allowed) return null
        return trace.exchanges.firstOrNull { it.commandHex.equals(commandHex, ignoreCase = true) }?.responseHex
    }
}

object NfcTraceCodec {
    private const val HEADER = "DPC-AIO-NFC/1"

    fun encode(trace: NfcTrace): String = buildString {
        appendLine(HEADER)
        appendLine(trace.ownership.name)
        appendLine(trace.technology.name)
        appendLine(trace.credentialBearing)
        trace.exchanges.forEach { exchange ->
            append(exchange.delayMs).append('\t')
                .append(exchange.commandHex).append('\t')
                .append(exchange.responseHex).append('\n')
        }
    }

    fun decode(text: String): NfcTrace {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        require(lines.size >= 4 && lines[0] == HEADER) { "unsupported NFC trace" }
        return NfcTrace(
            ownership = NfcTraceOwnership.valueOf(lines[1]),
            technology = NfcTechnology.valueOf(lines[2]),
            credentialBearing = lines[3].toBooleanStrict(),
            exchanges = lines.drop(4).map { line ->
                val parts = line.split('\t')
                require(parts.size == 3) { "invalid NFC trace row" }
                NfcExchange(parts[1], parts[2], parts[0].toLong())
            }
        )
    }
}
