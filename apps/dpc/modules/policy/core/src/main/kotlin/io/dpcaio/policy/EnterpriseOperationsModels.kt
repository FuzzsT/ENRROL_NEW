package io.dpcaio.policy

import java.time.LocalDate

enum class LoggingChannel { SECURITY, NETWORK }

enum class LoggingState {
    DISABLED,
    ENABLED,
    WAITING_FOR_CALLBACK,
    BATCH_AVAILABLE,
    AFFILIATION_REQUIRED,
    DELEGATION_REQUIRED,
    RATE_LIMITED,
}

data class EnterpriseLogBatch(
    val channel: LoggingChannel,
    val batchToken: Long? = null,
    val eventCount: Int,
    val capturedAtEpochMs: Long,
    val payloadJsonLines: List<String>,
) {
    init {
        require(eventCount == payloadJsonLines.size) { "eventCount must match payloadJsonLines size" }
    }
}

enum class SystemUpdateMode { SYSTEM_DEFAULT, AUTOMATIC, WINDOWED, POSTPONE }

data class FreezePeriodSpec(
    val startMonth: Int,
    val startDay: Int,
    val endMonth: Int,
    val endDay: Int,
)

data class SystemUpdatePolicySpec(
    val mode: SystemUpdateMode,
    val windowStartMinute: Int? = null,
    val windowEndMinute: Int? = null,
    val freezePeriods: List<FreezePeriodSpec> = emptyList(),
)

data class SystemUpdateValidation(
    val valid: Boolean,
    val errors: Set<String> = emptySet(),
)

object SystemUpdatePolicyValidator {
    private const val YEAR_DAYS = 365

    fun validate(spec: SystemUpdatePolicySpec): SystemUpdateValidation {
        val errors = linkedSetOf<String>()
        if (spec.mode == SystemUpdateMode.WINDOWED) {
            if (spec.windowStartMinute !in 0..1439 || spec.windowEndMinute !in 0..1439) {
                errors += "WINDOW_RANGE"
            }
        }

        val intervals = mutableListOf<Pair<Int, Int>>()
        for (period in spec.freezePeriods) {
            val start = ordinal(period.startMonth, period.startDay)
            val end = ordinal(period.endMonth, period.endDay)
            if (start == null || end == null) {
                errors += "FREEZE_DATE_INVALID"
                continue
            }
            val length = circularDistanceInclusive(start, end)
            if (length > 90) errors += "FREEZE_TOO_LONG"
            intervals += start to end
        }

        for (i in intervals.indices) {
            for (j in i + 1 until intervals.size) {
                val a = intervals[i]
                val b = intervals[j]
                if (overlaps(a, b)) {
                    errors += "FREEZE_OVERLAP"
                } else if (gapDays(a, b) < 60 || gapDays(b, a) < 60) {
                    errors += "FREEZE_SEPARATION"
                }
            }
        }
        return SystemUpdateValidation(errors.isEmpty(), errors)
    }

    private fun ordinal(month: Int, day: Int): Int? = runCatching {
        LocalDate.of(2001, month, day).dayOfYear
    }.getOrNull()

    private fun circularDistanceInclusive(start: Int, end: Int): Int =
        if (end >= start) end - start + 1 else (YEAR_DAYS - start + 1) + end

    private fun expand(interval: Pair<Int, Int>): List<IntRange> {
        val (start, end) = interval
        return if (end >= start) listOf(start..end) else listOf(start..YEAR_DAYS, 1..end)
    }

    private fun overlaps(a: Pair<Int, Int>, b: Pair<Int, Int>): Boolean =
        expand(a).any { ar -> expand(b).any { br -> ar.first <= br.last && br.first <= ar.last } }

    private fun gapDays(a: Pair<Int, Int>, b: Pair<Int, Int>): Int {
        val endA = a.second
        val startB = b.first
        val raw = if (startB > endA) startB - endA - 1 else YEAR_DAYS - endA + startB - 1
        return raw.coerceAtLeast(0)
    }
}

data class FreezePeriodParseResult(
    val periods: List<FreezePeriodSpec> = emptyList(),
    val errors: Set<String> = emptySet(),
) {
    val valid: Boolean get() = errors.isEmpty()
}

object FreezePeriodTextParser {
    private val pattern = Regex("^(\\d{2})-(\\d{2}):(\\d{2})-(\\d{2})$")

    fun parse(text: String): FreezePeriodParseResult {
        val normalized = text.trim()
        if (normalized.isEmpty()) return FreezePeriodParseResult()
        val periods = mutableListOf<FreezePeriodSpec>()
        for (raw in normalized.split(';')) {
            val match = pattern.matchEntire(raw.trim())
                ?: return FreezePeriodParseResult(errors = setOf("FREEZE_TEXT_FORMAT"))
            periods += FreezePeriodSpec(
                startMonth = match.groupValues[1].toInt(),
                startDay = match.groupValues[2].toInt(),
                endMonth = match.groupValues[3].toInt(),
                endDay = match.groupValues[4].toInt(),
            )
        }
        val validation = SystemUpdatePolicyValidator.validate(
            SystemUpdatePolicySpec(SystemUpdateMode.AUTOMATIC, freezePeriods = periods)
        )
        return FreezePeriodParseResult(periods, validation.errors)
    }
}
