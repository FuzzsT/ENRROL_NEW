package io.dpcaio.scenario

import java.nio.charset.StandardCharsets
import java.util.Base64

enum class ScenarioEventType {
    ACTIVITY,
    INTENT,
    BROADCAST,
    POLICY,
    PERMISSION,
    NETWORK,
    NFC,
    NATIVE,
    CUSTOM,
    UNSUPPORTED
}

data class ScenarioEvent(
    val sequence: Long,
    val monotonicNanos: Long,
    val type: ScenarioEventType,
    val name: String,
    val payload: String
)

class ScenarioRecorder {
    private val events = mutableListOf<ScenarioEvent>()

    @Synchronized
    fun record(event: ScenarioEvent) {
        require(event.type != ScenarioEventType.UNSUPPORTED) { "unsupported event type" }
        require(events.isEmpty() || event.monotonicNanos >= events.last().monotonicNanos) { "timestamps must be monotonic" }
        require(events.none { it.sequence == event.sequence }) { "sequence must be unique" }
        events += event
    }

    @Synchronized
    fun snapshot(): List<ScenarioEvent> = events.toList()

    @Synchronized
    fun clear() = events.clear()
}

enum class ReplayMode { REALTIME, DETERMINISTIC, STEP_BY_STEP }

data class ReplayStep(val event: ScenarioEvent, val delayNanos: Long)
data class ReplayPlan(val mode: ReplayMode, val speed: Double, val steps: List<ReplayStep>)

class ReplayPlanner {
    fun plan(trace: List<ScenarioEvent>, mode: ReplayMode, speed: Double): ReplayPlan {
        require(speed > 0.0) { "speed must be > 0" }
        require(trace.none { it.type == ScenarioEventType.UNSUPPORTED }) { "unsupported event in trace" }
        val ordered = trace.sortedBy { it.sequence }
        var previousTime: Long? = null
        val steps = ordered.map { event ->
            val raw = previousTime?.let { (event.monotonicNanos - it).coerceAtLeast(0L) } ?: 0L
            previousTime = event.monotonicNanos
            val delay = when (mode) {
                ReplayMode.STEP_BY_STEP -> 0L
                ReplayMode.REALTIME, ReplayMode.DETERMINISTIC -> (raw / speed).toLong()
            }
            ReplayStep(event, delay)
        }
        return ReplayPlan(mode, speed, steps)
    }
}

object ScenarioArchiveCodec {
    private const val HEADER = "DPC-AIO-SCENARIO/1"
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(events: List<ScenarioEvent>): String = buildString {
        appendLine(HEADER)
        events.forEach { event ->
            append(event.sequence).append('\t')
            append(event.monotonicNanos).append('\t')
            append(event.type.name).append('\t')
            append(b64(event.name)).append('\t')
            append(b64(event.payload)).append('\n')
        }
    }

    fun decode(text: String): List<ScenarioEvent> {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        require(lines.firstOrNull() == HEADER) { "unsupported scenario archive" }
        return lines.drop(1).map { line ->
            val parts = line.split('\t')
            require(parts.size == 5) { "invalid scenario row" }
            ScenarioEvent(
                sequence = parts[0].toLong(),
                monotonicNanos = parts[1].toLong(),
                type = ScenarioEventType.valueOf(parts[2]),
                name = unb64(parts[3]),
                payload = unb64(parts[4])
            )
        }
    }

    private fun b64(value: String): String = encoder.encodeToString(value.toByteArray(StandardCharsets.UTF_8))
    private fun unb64(value: String): String = String(decoder.decode(value), StandardCharsets.UTF_8)
}
