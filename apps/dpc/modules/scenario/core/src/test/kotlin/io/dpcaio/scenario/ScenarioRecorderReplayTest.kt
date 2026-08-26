package io.dpcaio.scenario

fun main() {
    val recorder = ScenarioRecorder()
    recorder.record(ScenarioEvent(1, 1_000, ScenarioEventType.ACTIVITY, "A", "resume"))
    recorder.record(ScenarioEvent(2, 1_250, ScenarioEventType.INTENT, "open", "x"))
    check(recorder.snapshot().map { it.sequence } == listOf(1L, 2L))

    var rejected = false
    try { recorder.record(ScenarioEvent(3, 1_200, ScenarioEventType.BROADCAST, "late", "")) }
    catch (_: IllegalArgumentException) { rejected = true }
    check(rejected)

    val deterministic = ReplayPlanner().plan(recorder.snapshot(), ReplayMode.DETERMINISTIC, 2.0)
    check(deterministic.steps[0].delayNanos == 0L)
    check(deterministic.steps[1].delayNanos == 125L)

    val encoded = ScenarioArchiveCodec.encode(recorder.snapshot())
    val decoded = ScenarioArchiveCodec.decode(encoded)
    check(decoded == recorder.snapshot())

    var unsupported = false
    try {
        ReplayPlanner().plan(listOf(ScenarioEvent(1, 1, ScenarioEventType.UNSUPPORTED, "x", "")), ReplayMode.REALTIME, 1.0)
    } catch (_: IllegalArgumentException) { unsupported = true }
    check(unsupported)

    println("ScenarioRecorderReplayTest: PASS")
}
