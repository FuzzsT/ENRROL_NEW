package io.dpcaio.nfc

fun main() {
    val validator = NfcReplayValidator()
    val safe = NfcTrace(
        ownership = NfcTraceOwnership.OWNED_TEST,
        technology = NfcTechnology.MIFARE_CLASSIC,
        credentialBearing = false,
        exchanges = listOf(NfcExchange("A0", "9000", 5))
    )
    check(validator.validate(safe).allowed)
    check(validator.validate(safe.copy(ownership = NfcTraceOwnership.SYNTHETIC_TEST)).allowed)
    check(!validator.validate(safe.copy(ownership = NfcTraceOwnership.EXTERNAL)).allowed)
    check(!validator.validate(safe.copy(credentialBearing = true)).allowed)

    val apdu = NfcTrace(
        ownership = NfcTraceOwnership.SYNTHETIC_TEST,
        technology = NfcTechnology.ISO_DEP,
        credentialBearing = false,
        exchanges = listOf(NfcExchange("00A4040000", "9000", 0))
    )
    check(validator.responseFor(apdu, "00A4040000") == "9000")
    check(validator.responseFor(apdu, "00B0000000") == null)
    println("NfcReplayValidatorTest: PASS")
}
