package io.dpcaio.samsung.sem.android

import android.content.Context
import android.os.Build
import io.dpcaio.model.CapabilityState
import io.dpcaio.samsung.sem.SemCapabilitySpec
import io.dpcaio.samsung.sem.SemProbeResult
import io.dpcaio.samsung.sem.SemProbeStage

class SemRuntimeProbe(private val context: Context) {
    fun probe(spec: SemCapabilitySpec): SemProbeResult {
        if (spec.samsungOnly && !Build.MANUFACTURER.equals("samsung", ignoreCase = true)) {
            return SemProbeResult(spec, SemProbeStage.DISCOVERED_IN_REFERENCE_APK, CapabilityState.UNSUPPORTED_FIRMWARE, "UNSUPPORTED_DEVICE")
        }
        val clazz = runCatching { Class.forName(spec.className, false, context.classLoader) }.getOrNull()
            ?: return SemProbeResult(spec, SemProbeStage.DISCOVERED_IN_REFERENCE_APK, CapabilityState.CLASS_MISSING, "CLASS_MISSING")
        val parameterTypes = runCatching { spec.parameterTypeNames.map(::resolveType).toTypedArray() }.getOrElse {
            return SemProbeResult(spec, SemProbeStage.CLASS_PRESENT, CapabilityState.METHOD_MISSING, "PARAMETER_TYPE_MISSING:${it.javaClass.simpleName}")
        }
        val method = runCatching { clazz.getMethod(spec.methodName, *parameterTypes) }.getOrNull()
            ?: runCatching { clazz.getDeclaredMethod(spec.methodName, *parameterTypes) }.getOrNull()
            ?: return SemProbeResult(spec, SemProbeStage.CLASS_PRESENT, CapabilityState.METHOD_MISSING, "METHOD_MISSING")
        return SemProbeResult(
            spec = spec,
            stage = SemProbeStage.METHOD_PRESENT,
            state = if (spec.readOnly) CapabilityState.READ_ONLY else CapabilityState.UNVERIFIED_PLATFORM_MAPPING,
            detail = "METHOD_PRESENT; PERMISSION_SATISFIED/CALL_SUCCEEDED/READBACK_VERIFIED require bounded invocation",
            valueSummary = method.toGenericString().take(512),
        )
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
}
