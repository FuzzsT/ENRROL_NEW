package io.dpcaio.oem.android

import android.content.Context
import io.dpcaio.model.CapabilityState
import io.dpcaio.oem.OemCircuitBreaker
import io.dpcaio.oem.OemInternalCatalog
import io.dpcaio.oem.OemInternalProbeResult
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class OemSafeInvoker(
    private val context: Context,
    private val circuitBreaker: OemCircuitBreaker = OemCircuitBreaker(),
) {
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "dpc-oem-lab").apply { isDaemon = true } }

    /** Executes only an exact, built-in catalog entry. There is no arbitrary class/method surface. */
    fun invokeCataloged(capabilityId: String, receiver: Any?, args: List<Any?>): OemInternalProbeResult {
        val spec = OemInternalCatalog.byId(capabilityId)
            ?: return OemInternalProbeResult(capabilityId, CapabilityState.UNAVAILABLE, "CATALOG_ENTRY_MISSING")
        if (!spec.readOnly) return OemInternalProbeResult(capabilityId, CapabilityState.CALL_BLOCKED, "WRITE_NOT_ENABLED_IN_LAB_DEFAULT")
        if (circuitBreaker.isOpen(capabilityId)) return OemInternalProbeResult(capabilityId, CapabilityState.CALL_BLOCKED, "CIRCUIT_OPEN")
        val probe = OemMethodProbe(context).probe(spec)
        if (probe.state !in setOf(CapabilityState.READ_ONLY, CapabilityState.AVAILABLE)) return probe

        val future = executor.submit<OemInternalProbeResult> {
            try {
                val clazz = Class.forName(spec.className, false, context.classLoader)
                val types = spec.parameterTypeNames.map { resolveType(it) }.toTypedArray()
                val method = runCatching { clazz.getMethod(spec.methodName, *types) }.getOrElse { clazz.getDeclaredMethod(spec.methodName, *types) }
                val value = method.invoke(receiver, *args.toTypedArray())
                circuitBreaker.recordSuccess(capabilityId)
                OemInternalProbeResult(capabilityId, CapabilityState.AVAILABLE, "CALL_SUCCEEDED", summarize(value, spec.maxResultChars))
            } catch (t: Throwable) {
                val actual = t.cause ?: t
                val key = actual.javaClass.simpleName
                circuitBreaker.recordFailure(capabilityId, key)
                val state = if (key in spec.expectedFailureSimpleNames || actual is SecurityException) CapabilityState.CALL_BLOCKED else CapabilityState.UNAVAILABLE
                OemInternalProbeResult(capabilityId, state, "CALL_FAILED:$key")
            }
        }
        return try {
            future.get(spec.timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            circuitBreaker.recordFailure(capabilityId, "TIMEOUT")
            OemInternalProbeResult(capabilityId, CapabilityState.CALL_BLOCKED, "TIMEOUT")
        }
    }

    private fun resolveType(name: String): Class<*> = when (name) {
        "boolean" -> Boolean::class.javaPrimitiveType!!
        "byte" -> Byte::class.javaPrimitiveType!!
        "short" -> Short::class.javaPrimitiveType!!
        "int" -> Int::class.javaPrimitiveType!!
        "long" -> Long::class.javaPrimitiveType!!
        "float" -> Float::class.javaPrimitiveType!!
        "double" -> Double::class.javaPrimitiveType!!
        "char" -> Char::class.javaPrimitiveType!!
        else -> Class.forName(name, false, context.classLoader)
    }

    private fun summarize(value: Any?, maxChars: Int): String = when (value) {
        null -> "null"
        is ByteArray -> "ByteArray(size=${value.size})"
        is Collection<*> -> "Collection(size=${value.size})"
        is Map<*, *> -> "Map(size=${value.size})"
        else -> value.toString().take(maxChars)
    }
}
