package io.dpcaio.nfc

fun main() {
    val trace = NfcTrace(
        NfcTraceOwnership.OWNED_TEST,
        NfcTechnology.MIFARE_ULTRALIGHT,
        false,
        listOf(NfcExchange("3004", "01020304", 12))
    )
    val encoded = NfcTraceCodec.encode(trace)
    check(NfcTraceCodec.decode(encoded) == trace)
    println("NfcTraceCodecTest: PASS")
}
