package io.dpcaio.oem

import io.dpcaio.model.CapabilityState

data class OemInternalMethodSpec(
    val id: String,
    val className: String,
    val methodName: String,
    val parameterTypeNames: List<String> = emptyList(),
    val readOnly: Boolean = true,
    val samsungOnly: Boolean = true,
    val timeoutMillis: Long = 750,
    val maxResultChars: Int = 1024,
    val expectedFailureSimpleNames: Set<String> = setOf("SecurityException", "NoSuchMethodException", "IllegalAccessException"),
)

data class OemInternalProbeResult(
    val capabilityId: String,
    val state: CapabilityState,
    val detail: String,
    val resultSummary: String? = null,
)

object OemInternalCatalog {
    val entries: List<OemInternalMethodSpec> = listOf(
        OemInternalMethodSpec(
            id = "oem.service_manager.check_service",
            className = "android.os.ServiceManager",
            methodName = "checkService",
            parameterTypeNames = listOf("java.lang.String"),
            readOnly = true,
            samsungOnly = false,
        ),
        OemInternalMethodSpec(
            id = "samsung.sem_system_properties.get",
            className = "android.os.SemSystemProperties",
            methodName = "get",
            parameterTypeNames = listOf("java.lang.String"),
            readOnly = true,
            samsungOnly = true,
        ),
    )

    fun byId(id: String): OemInternalMethodSpec? = entries.firstOrNull { it.id == id }
}
