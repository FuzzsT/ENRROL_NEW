package io.dpcaio.protection

class ProtectedTargetRegistry private constructor(
    private val exact: Map<String, ProtectedTargetClass>,
) {
    fun classify(targetId: String): ProtectedTargetClass? = exact[targetId]

    companion object {
        fun default(): ProtectedTargetRegistry = ProtectedTargetRegistry(
            mapOf(
                "io.dpcaio.app" to ProtectedTargetClass.DPC_CRITICAL,
                "io.dpcaio.app.AioDeviceAdminReceiver" to ProtectedTargetClass.DPC_CRITICAL,
                "io.dpcaio.app.ProvisioningModeActivity" to ProtectedTargetClass.DPC_CRITICAL,
                "io.dpcaio.app.PolicyComplianceActivity" to ProtectedTargetClass.DPC_CRITICAL,
                "io.dpcaio.app.OfflineRecoveryReceiver" to ProtectedTargetClass.RECOVERY_CRITICAL,
                "io.dpcaio.app.OfflineRecoveryJobService" to ProtectedTargetClass.RECOVERY_CRITICAL,
                "io.dpcaio.app.EnrollmentRecoveryReceiver" to ProtectedTargetClass.RECOVERY_CRITICAL,
                "io.dpcaio.app.VerificationBridgeService" to ProtectedTargetClass.SECURITY_CRITICAL,
                "io.dpcaio.app.KnoxRuntimeStateStore" to ProtectedTargetClass.SECURITY_CRITICAL,
                "io.dpcaio.app.KnoxRealLicenseStateBridge" to ProtectedTargetClass.SECURITY_CRITICAL,
            )
        )
    }
}
