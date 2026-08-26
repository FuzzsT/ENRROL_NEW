package io.dpcaio.policy

enum class ManagedPermissionState {
    DEFAULT,
    DENIED,
    GRANTED
}

enum class TriStatePolicy {
    NOT_CONTROLLED,
    DISABLED,
    ENABLED,
}

enum class AppFunctionsPolicy {
    NOT_CONTROLLED,
    DISABLED,
    DISABLED_CROSS_PROFILE,
}

enum class DeviceRestriction {
    THREAD_NETWORK,
    NFC_RADIO,
    NFC_RADIO_CHANGES,
}

interface PackagePolicyGateway {
    fun setApplicationHidden(packageName: String, hidden: Boolean): PolicyResult<Unit>
    fun isApplicationHidden(packageName: String): PolicyResult<Boolean>
    fun setPackagesSuspended(packageNames: Set<String>, suspended: Boolean): PolicyResult<Set<String>>
    fun isPackageSuspended(packageName: String): PolicyResult<Boolean>
}

interface PermissionPolicyGateway {
    fun setPermissionGrantState(
        packageName: String,
        permission: String,
        state: ManagedPermissionState
    ): PolicyResult<Unit>

    fun getPermissionGrantState(packageName: String, permission: String): PolicyResult<ManagedPermissionState>
}

interface EnterpriseDevicePolicyGateway {
    fun getUsbDataSignalingEnabled(): PolicyResult<Boolean> = unsupportedEnterprisePolicy()
    fun setUsbDataSignalingEnabled(enabled: Boolean): PolicyResult<Unit> = unsupportedEnterprisePolicy()
    fun getAutoTimePolicy(): PolicyResult<TriStatePolicy> = unsupportedEnterprisePolicy()
    fun setAutoTimePolicy(policy: TriStatePolicy): PolicyResult<Unit> = unsupportedEnterprisePolicy()
    fun getAutoTimeZonePolicy(): PolicyResult<TriStatePolicy> = unsupportedEnterprisePolicy()
    fun setAutoTimeZonePolicy(policy: TriStatePolicy): PolicyResult<Unit> = unsupportedEnterprisePolicy()
    fun getAppFunctionsPolicy(): PolicyResult<AppFunctionsPolicy> = unsupportedEnterprisePolicy()
    fun setAppFunctionsPolicy(policy: AppFunctionsPolicy): PolicyResult<Unit> = unsupportedEnterprisePolicy()
    fun getDeviceRestriction(restriction: DeviceRestriction): PolicyResult<Boolean> = unsupportedEnterprisePolicy()
    fun setDeviceRestriction(restriction: DeviceRestriction, enabled: Boolean): PolicyResult<Unit> = unsupportedEnterprisePolicy()

    fun isSecurityLoggingEnabled(): PolicyResult<Boolean> = unsupportedEnterprisePolicy()
    fun setSecurityLoggingEnabled(enabled: Boolean): PolicyResult<Unit> = unsupportedEnterprisePolicy()
    fun retrieveSecurityLogs(): PolicyResult<EnterpriseLogBatch> = unsupportedEnterprisePolicy()
    fun retrievePreRebootSecurityLogs(): PolicyResult<EnterpriseLogBatch> = unsupportedEnterprisePolicy()
    fun isNetworkLoggingEnabled(): PolicyResult<Boolean> = unsupportedEnterprisePolicy()
    fun setNetworkLoggingEnabled(enabled: Boolean): PolicyResult<Unit> = unsupportedEnterprisePolicy()
    fun retrieveNetworkLogs(batchToken: Long): PolicyResult<EnterpriseLogBatch> = unsupportedEnterprisePolicy()
    fun getSystemUpdatePolicySpec(): PolicyResult<SystemUpdatePolicySpec> = unsupportedEnterprisePolicy()
    fun setSystemUpdatePolicySpec(spec: SystemUpdatePolicySpec): PolicyResult<Unit> = unsupportedEnterprisePolicy()

    fun installCaCertificate(certBytes: ByteArray): PolicyResult<Unit> = unsupportedEnterprisePolicy()
    fun getInstalledCaCertificates(): PolicyResult<List<ByteArray>> = unsupportedEnterprisePolicy()
    fun uninstallCaCertificate(certBytes: ByteArray): PolicyResult<Unit> = unsupportedEnterprisePolicy()
    fun installManagedKeyPair(
        privateKey: java.security.PrivateKey,
        certificates: List<java.security.cert.Certificate>,
        alias: String,
        requestAccess: Boolean,
    ): PolicyResult<Unit> = unsupportedEnterprisePolicy()
    fun removeManagedKeyPair(alias: String): PolicyResult<Unit> = unsupportedEnterprisePolicy()
    fun grantManagedKeyPairToApp(alias: String, packageName: String): PolicyResult<Unit> = unsupportedEnterprisePolicy()
    fun getManagedKeyPairGrants(alias: String): PolicyResult<KeyPairGrantSummary> = unsupportedEnterprisePolicy()

    fun getLockTaskPolicySpec(): PolicyResult<LockTaskPolicySpec> = unsupportedEnterprisePolicy()
    fun setLockTaskPolicySpec(spec: LockTaskPolicySpec): PolicyResult<Unit> = unsupportedEnterprisePolicy()
    fun setDeviceSecurityPolicySpec(spec: DeviceSecurityPolicySpec): PolicyResult<Unit> = unsupportedEnterprisePolicy()
    fun setUninstallBlockedPolicy(packageName: String, blocked: Boolean): PolicyResult<Unit> = unsupportedEnterprisePolicy()
    fun setManagedApplicationRestrictions(packageName: String, restrictions: Map<String, String>): PolicyResult<Unit> = unsupportedEnterprisePolicy()
    fun clearManagedApplicationData(packageName: String): PolicyResult<Unit> = unsupportedEnterprisePolicy()
    fun setUserControlDisabledPackagesPolicy(packageNames: Set<String>): PolicyResult<Unit> = unsupportedEnterprisePolicy()
    fun setFrpPolicySpec(spec: FrpPolicySpec): PolicyResult<Unit> = unsupportedEnterprisePolicy()
    fun getFrpPolicySpec(): PolicyResult<FrpPolicySpec> = unsupportedEnterprisePolicy()

    fun getCopePolicySnapshot(): PolicyResult<CopePolicySnapshot> = unsupportedEnterprisePolicy()
    fun setCrossProfilePackagesPolicy(packageNames: Set<String>): PolicyResult<Unit> = unsupportedEnterprisePolicy()
    fun setManagedProfileMaximumTimeOffPolicy(timeoutMillis: Long): PolicyResult<Unit> = unsupportedEnterprisePolicy()
    fun setPersonalAppsSuspendedPolicy(suspended: Boolean): PolicyResult<Unit> = unsupportedEnterprisePolicy()
    fun setOrganizationIdentity(enterpriseId: String, organizationName: String?): PolicyResult<Unit> = unsupportedEnterprisePolicy()
    fun setAffiliationIdsPolicy(ids: Set<String>): PolicyResult<Unit> = unsupportedEnterprisePolicy()
    fun setManagedProfileContactsAccessPolicy(spec: ManagedProfilePackagePolicySpec): PolicyResult<Unit> = unsupportedEnterprisePolicy()
    fun setManagedProfileCallerIdAccessPolicy(spec: ManagedProfilePackagePolicySpec): PolicyResult<Unit> = unsupportedEnterprisePolicy()

    private fun <T> unsupportedEnterprisePolicy(): PolicyResult<T> =
        PolicyResult.failure(PolicyStatus.UNSUPPORTED, "Enterprise policy is not implemented by this gateway")
}

interface DevicePolicyGateway : PackagePolicyGateway, PermissionPolicyGateway, EnterpriseDevicePolicyGateway
