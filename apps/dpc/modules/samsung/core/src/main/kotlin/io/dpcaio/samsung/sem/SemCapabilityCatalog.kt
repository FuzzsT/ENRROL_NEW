package io.dpcaio.samsung.sem

object SemCapabilityCatalog {
    val entries: List<SemCapabilitySpec> = listOf(
        SemCapabilitySpec(
            id = "sem.floating_feature.get_string",
            className = "com.samsung.android.feature.SemFloatingFeature",
            methodName = "getString",
            parameterTypeNames = listOf("java.lang.String"),
            readOnly = true,
        ),
        SemCapabilitySpec(
            id = "sem.carrier_feature.get_string",
            className = "com.samsung.android.feature.SemCarrierFeature",
            methodName = "getString",
            parameterTypeNames = listOf("int", "java.lang.String", "java.lang.String", "boolean"),
            readOnly = true,
        ),
        SemCapabilitySpec(
            id = "sem.system_properties.get",
            className = "android.os.SemSystemProperties",
            methodName = "get",
            parameterTypeNames = listOf("java.lang.String"),
            readOnly = true,
        ),
        SemCapabilitySpec(
            id = "sem.persona.exists",
            className = "com.samsung.android.knox.SemPersonaManager",
            methodName = "isKnoxId",
            parameterTypeNames = listOf("int"),
            readOnly = true,
        ),
        SemCapabilitySpec(
            id = "sem.device_health.snapshot",
            className = "com.samsung.android.sdhms.SemDeviceHealthManager",
            methodName = "getRemainingUsageTime",
            parameterTypeNames = listOf("int"),
            readOnly = true,
        ),
        SemCapabilitySpec(
            id = "sem.temperature.snapshot",
            className = "com.samsung.android.os.SemTemperatureManager",
            methodName = "getThermistorList",
            readOnly = true,
        ),
        SemCapabilitySpec(
            id = "sem.role.snapshot",
            className = "com.samsung.android.app.SemRoleManager",
            methodName = "getRoleHolders",
            parameterTypeNames = listOf("java.lang.String"),
            readOnly = true,
        ),
    )
}
