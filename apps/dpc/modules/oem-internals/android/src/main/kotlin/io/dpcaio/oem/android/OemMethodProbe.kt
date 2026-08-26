package io.dpcaio.oem.android

import android.content.Context
import android.os.Build
import io.dpcaio.model.CapabilityState
import io.dpcaio.oem.OemInternalMethodSpec
import io.dpcaio.oem.OemInternalProbeResult

class OemMethodProbe(private val context: Context) {
    fun probe(spec: OemInternalMethodSpec): OemInternalProbeResult {
        if (spec.samsungOnly && !Build.MANUFACTURER.equals("samsung", ignoreCase = true)) {
            return OemInternalProbeResult(spec.id, CapabilityState.UNSUPPORTED_FIRMWARE, "UNSUPPORTED_DEVICE")
        }
        val clazz = runCatching { Class.forName(spec.className, false, context.classLoader) }.getOrNull()
            ?: return OemInternalProbeResult(spec.id, CapabilityState.CLASS_MISSING, "CLASS_MISSING")
        val types = runCatching { spec.parameterTypeNames.map(::resolveType).toTypedArray() }.getOrElse {
            return OemInternalProbeResult(spec.id, CapabilityState.METHOD_MISSING, "PARAMETER_TYPE_MISSING:${it.javaClass.simpleName}")
        }
        val method = runCatching { clazz.getMethod(spec.methodName, *types) }.getOrNull()
            ?: runCatching { clazz.getDeclaredMethod(spec.methodName, *types) }.getOrNull()
            ?: return OemInternalProbeResult(spec.id, CapabilityState.METHOD_MISSING, "METHOD_MISSING")
        return OemInternalProbeResult(spec.id, CapabilityState.READ_ONLY, "METHOD_PRESENT", method.toGenericString().take(spec.maxResultChars))
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
