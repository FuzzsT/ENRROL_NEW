package io.dpcaio.policy

fun main() {
    val valid = SystemUpdatePolicyValidator.validate(
        SystemUpdatePolicySpec(
            mode = SystemUpdateMode.WINDOWED,
            windowStartMinute = 23 * 60,
            windowEndMinute = 2 * 60,
            freezePeriods = listOf(
                FreezePeriodSpec(12, 15, 1, 5),
                FreezePeriodSpec(7, 1, 7, 14),
            )
        )
    )
    check(valid.valid) { valid.errors.joinToString() }

    val badWindow = SystemUpdatePolicyValidator.validate(
        SystemUpdatePolicySpec(SystemUpdateMode.WINDOWED, -1, 2000)
    )
    check(!badWindow.valid)
    check("WINDOW_RANGE" in badWindow.errors)

    val tooLong = SystemUpdatePolicyValidator.validate(
        SystemUpdatePolicySpec(
            SystemUpdateMode.AUTOMATIC,
            freezePeriods = listOf(FreezePeriodSpec(1, 1, 4, 15))
        )
    )
    check(!tooLong.valid)
    check("FREEZE_TOO_LONG" in tooLong.errors)

    val overlap = SystemUpdatePolicyValidator.validate(
        SystemUpdatePolicySpec(
            SystemUpdateMode.AUTOMATIC,
            freezePeriods = listOf(
                FreezePeriodSpec(1, 1, 1, 20),
                FreezePeriodSpec(1, 15, 2, 1),
            )
        )
    )
    check(!overlap.valid)
    check("FREEZE_OVERLAP" in overlap.errors)

    val tooClose = SystemUpdatePolicyValidator.validate(
        SystemUpdatePolicySpec(
            SystemUpdateMode.AUTOMATIC,
            freezePeriods = listOf(
                FreezePeriodSpec(1, 1, 1, 10),
                FreezePeriodSpec(2, 1, 2, 10),
            )
        )
    )
    check(!tooClose.valid)
    check("FREEZE_SEPARATION" in tooClose.errors)

    val parsedFreeze = FreezePeriodTextParser.parse("12-15:01-05;07-01:07-14")
    check(parsedFreeze.valid) { parsedFreeze.errors.joinToString() }
    check(parsedFreeze.periods == listOf(
        FreezePeriodSpec(12, 15, 1, 5),
        FreezePeriodSpec(7, 1, 7, 14),
    ))
    val badFreezeText = FreezePeriodTextParser.parse("12/15-01/05")
    check(!badFreezeText.valid)
    check("FREEZE_TEXT_FORMAT" in badFreezeText.errors)

    val batch = EnterpriseLogBatch(
        channel = LoggingChannel.NETWORK,
        batchToken = 42L,
        eventCount = 3,
        capturedAtEpochMs = 1234L,
        payloadJsonLines = listOf("{}", "{}", "{}")
    )
    check(batch.eventCount == batch.payloadJsonLines.size)

    println("EnterpriseOperationsModelsTest: PASS")
}
