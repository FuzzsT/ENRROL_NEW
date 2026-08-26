package io.dpcaio.policy.android

import android.app.admin.DevicePolicyManager
import android.app.admin.FreezePeriod
import android.app.admin.FactoryResetProtectionPolicy
import android.app.admin.PackagePolicy
import android.app.admin.SystemUpdatePolicy
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import android.os.Bundle
import java.time.MonthDay
import java.security.PrivateKey
import java.security.cert.Certificate
import io.dpcaio.policy.AppFunctionsPolicy
import io.dpcaio.policy.DelegationPolicyGateway
import io.dpcaio.policy.DevicePolicyGateway
import io.dpcaio.policy.DeviceRestriction
import io.dpcaio.policy.EnterpriseLogBatch
import io.dpcaio.policy.LoggingChannel
import io.dpcaio.policy.KeyPairGrantSummary
import io.dpcaio.policy.LockTaskPolicySpec
import io.dpcaio.policy.DeviceSecurityPolicySpec
import io.dpcaio.policy.FrpPolicySpec
import io.dpcaio.policy.CopePolicySnapshot
import io.dpcaio.policy.CopePolicyValidator
import io.dpcaio.policy.ManagedProfilePackagePolicySpec
import io.dpcaio.policy.PackageAccessPolicyType
import io.dpcaio.policy.ManagedPermissionState
import io.dpcaio.policy.PolicyResult
import io.dpcaio.policy.PolicyStatus
import io.dpcaio.policy.TriStatePolicy
import io.dpcaio.policy.FreezePeriodSpec
import io.dpcaio.policy.SystemUpdateMode
import io.dpcaio.policy.SystemUpdatePolicySpec
import io.dpcaio.policy.SystemUpdatePolicyValidator

class AndroidDevicePolicyGateway(
    context: Context,
    private val admin: ComponentName
) : DevicePolicyGateway, DelegationPolicyGateway {
    private val appContext = context.applicationContext
    private val dpm = appContext.getSystemService(DevicePolicyManager::class.java)

    override fun setApplicationHidden(packageName: String, hidden: Boolean): PolicyResult<Unit> =
        policyCall {
            if (!dpm.setApplicationHidden(admin, packageName, hidden)) {
                PolicyResult.failure(
                    status = PolicyStatus.PLATFORM_REJECTED,
                    message = "DevicePolicyManager rejected application hidden state for $packageName"
                )
            } else {
                PolicyResult.success()
            }
        }

    override fun isApplicationHidden(packageName: String): PolicyResult<Boolean> =
        try {
            PolicyResult.success(dpm.isApplicationHidden(admin, packageName))
        } catch (e: PackageManager.NameNotFoundException) {
            PolicyResult.failure(
                status = PolicyStatus.PACKAGE_NOT_FOUND,
                message = "Package not found: $packageName",
                errorType = e.javaClass.name
            )
        } catch (e: SecurityException) {
            securityFailure(e)
        } catch (e: RuntimeException) {
            runtimeFailure(e)
        }

    override fun setPackagesSuspended(
        packageNames: Set<String>,
        suspended: Boolean
    ): PolicyResult<Set<String>> = policyCall {
        val failures = dpm.setPackagesSuspended(admin, packageNames.toTypedArray(), suspended).toSet()
        PolicyResult.success(
            value = failures,
            message = if (failures.isEmpty()) null else "Some packages could not be changed"
        )
    }

    override fun isPackageSuspended(packageName: String): PolicyResult<Boolean> =
        try {
            PolicyResult.success(dpm.isPackageSuspended(admin, packageName))
        } catch (e: PackageManager.NameNotFoundException) {
            PolicyResult.failure(
                status = PolicyStatus.PACKAGE_NOT_FOUND,
                message = "Package not found: $packageName",
                errorType = e.javaClass.name
            )
        } catch (e: SecurityException) {
            securityFailure(e)
        } catch (e: RuntimeException) {
            runtimeFailure(e)
        }

    override fun getDelegatedScopes(packageName: String): PolicyResult<Set<String>> = policyCall {
        PolicyResult.success(dpm.getDelegatedScopes(admin, packageName).toSet())
    }

    override fun setDelegatedScopes(packageName: String, scopes: Set<String>): PolicyResult<Unit> = policyCall {
        dpm.setDelegatedScopes(admin, packageName, scopes.toList())
        PolicyResult.success()
    }

    override fun setPermissionGrantState(
        packageName: String,
        permission: String,
        state: ManagedPermissionState
    ): PolicyResult<Unit> = policyCall {
        val platformState = when (state) {
            ManagedPermissionState.DEFAULT -> DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT
            ManagedPermissionState.DENIED -> DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED
            ManagedPermissionState.GRANTED -> DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
        }
        if (!dpm.setPermissionGrantState(admin, packageName, permission, platformState)) {
            PolicyResult.failure(
                status = PolicyStatus.PLATFORM_REJECTED,
                message = "DevicePolicyManager rejected permission state for $packageName/$permission"
            )
        } else {
            PolicyResult.success()
        }
    }

    override fun getPermissionGrantState(
        packageName: String,
        permission: String
    ): PolicyResult<ManagedPermissionState> = policyCall {
        val state = when (dpm.getPermissionGrantState(admin, packageName, permission)) {
            DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED -> ManagedPermissionState.GRANTED
            DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED -> ManagedPermissionState.DENIED
            else -> ManagedPermissionState.DEFAULT
        }
        PolicyResult.success(state)
    }

    override fun getUsbDataSignalingEnabled(): PolicyResult<Boolean> {
        if (Build.VERSION.SDK_INT < 31) return unsupported("USB data signaling requires API 31+")
        return policyCall { PolicyResult.success(dpm.isUsbDataSignalingEnabled) }
    }

    override fun setUsbDataSignalingEnabled(enabled: Boolean): PolicyResult<Unit> {
        if (Build.VERSION.SDK_INT < 31) return unsupported("USB data signaling requires API 31+")
        if (!enabled && !dpm.canUsbDataSignalingBeDisabled()) {
            return unsupported("USB data signaling cannot be disabled on this device")
        }
        return policyCall {
            dpm.setUsbDataSignalingEnabled(enabled)
            PolicyResult.success()
        }
    }

    override fun getAutoTimePolicy(): PolicyResult<TriStatePolicy> {
        if (Build.VERSION.SDK_INT < 36) return unsupported("Auto time policy requires API 36+")
        return policyCall {
            val value = when (dpm.autoTimePolicy) {
                DevicePolicyManager.AUTO_TIME_ENABLED -> TriStatePolicy.ENABLED
                DevicePolicyManager.AUTO_TIME_DISABLED -> TriStatePolicy.DISABLED
                else -> TriStatePolicy.NOT_CONTROLLED
            }
            PolicyResult.success(value)
        }
    }

    override fun setAutoTimePolicy(policy: TriStatePolicy): PolicyResult<Unit> {
        if (Build.VERSION.SDK_INT < 36) return unsupported("Auto time policy requires API 36+")
        return policyCall {
            dpm.setAutoTimePolicy(
                when (policy) {
                    TriStatePolicy.NOT_CONTROLLED -> DevicePolicyManager.AUTO_TIME_NOT_CONTROLLED_BY_POLICY
                    TriStatePolicy.DISABLED -> DevicePolicyManager.AUTO_TIME_DISABLED
                    TriStatePolicy.ENABLED -> DevicePolicyManager.AUTO_TIME_ENABLED
                }
            )
            PolicyResult.success()
        }
    }

    override fun getAutoTimeZonePolicy(): PolicyResult<TriStatePolicy> {
        if (Build.VERSION.SDK_INT < 36) return unsupported("Auto time zone policy requires API 36+")
        return policyCall {
            val value = when (dpm.autoTimeZonePolicy) {
                DevicePolicyManager.AUTO_TIME_ZONE_ENABLED -> TriStatePolicy.ENABLED
                DevicePolicyManager.AUTO_TIME_ZONE_DISABLED -> TriStatePolicy.DISABLED
                else -> TriStatePolicy.NOT_CONTROLLED
            }
            PolicyResult.success(value)
        }
    }

    override fun setAutoTimeZonePolicy(policy: TriStatePolicy): PolicyResult<Unit> {
        if (Build.VERSION.SDK_INT < 36) return unsupported("Auto time zone policy requires API 36+")
        return policyCall {
            dpm.setAutoTimeZonePolicy(
                when (policy) {
                    TriStatePolicy.NOT_CONTROLLED -> DevicePolicyManager.AUTO_TIME_ZONE_NOT_CONTROLLED_BY_POLICY
                    TriStatePolicy.DISABLED -> DevicePolicyManager.AUTO_TIME_ZONE_DISABLED
                    TriStatePolicy.ENABLED -> DevicePolicyManager.AUTO_TIME_ZONE_ENABLED
                }
            )
            PolicyResult.success()
        }
    }

    override fun getAppFunctionsPolicy(): PolicyResult<AppFunctionsPolicy> {
        if (Build.VERSION.SDK_INT < 36) return unsupported("App Functions policy requires API 36+")
        return policyCall {
            val value = when (dpm.appFunctionsPolicy) {
                DevicePolicyManager.APP_FUNCTIONS_DISABLED -> AppFunctionsPolicy.DISABLED
                DevicePolicyManager.APP_FUNCTIONS_DISABLED_CROSS_PROFILE -> AppFunctionsPolicy.DISABLED_CROSS_PROFILE
                else -> AppFunctionsPolicy.NOT_CONTROLLED
            }
            PolicyResult.success(value)
        }
    }

    override fun setAppFunctionsPolicy(policy: AppFunctionsPolicy): PolicyResult<Unit> {
        if (Build.VERSION.SDK_INT < 36) return unsupported("App Functions policy requires API 36+")
        return policyCall {
            dpm.setAppFunctionsPolicy(
                when (policy) {
                    AppFunctionsPolicy.NOT_CONTROLLED -> DevicePolicyManager.APP_FUNCTIONS_NOT_CONTROLLED_BY_POLICY
                    AppFunctionsPolicy.DISABLED -> DevicePolicyManager.APP_FUNCTIONS_DISABLED
                    AppFunctionsPolicy.DISABLED_CROSS_PROFILE -> DevicePolicyManager.APP_FUNCTIONS_DISABLED_CROSS_PROFILE
                }
            )
            PolicyResult.success()
        }
    }

    override fun getDeviceRestriction(restriction: DeviceRestriction): PolicyResult<Boolean> {
        val key = restrictionKey(restriction) ?: return unsupported("Restriction unavailable on API ${Build.VERSION.SDK_INT}")
        return policyCall { PolicyResult.success(deviceWideDpm().getUserRestrictions(admin).getBoolean(key, false)) }
    }

    override fun setDeviceRestriction(restriction: DeviceRestriction, enabled: Boolean): PolicyResult<Unit> {
        val key = restrictionKey(restriction) ?: return unsupported("Restriction unavailable on API ${Build.VERSION.SDK_INT}")
        return policyCall {
            val target = deviceWideDpm()
            if (enabled) target.addUserRestriction(admin, key) else target.clearUserRestriction(admin, key)
            PolicyResult.success()
        }
    }

    override fun isSecurityLoggingEnabled(): PolicyResult<Boolean> {
        if (Build.VERSION.SDK_INT < 24) return unsupported("Security logging requires API 24+")
        return policyCall { PolicyResult.success(dpm.isSecurityLoggingEnabled(admin)) }
    }

    override fun setSecurityLoggingEnabled(enabled: Boolean): PolicyResult<Unit> {
        if (Build.VERSION.SDK_INT < 24) return unsupported("Security logging requires API 24+")
        return policyCall {
            dpm.setSecurityLoggingEnabled(admin, enabled)
            PolicyResult.success()
        }
    }

    override fun retrieveSecurityLogs(): PolicyResult<EnterpriseLogBatch> {
        if (Build.VERSION.SDK_INT < 24) return unsupported("Security logging requires API 24+")
        return policyCall {
            val events = dpm.retrieveSecurityLogs(admin) ?: emptyList()
            PolicyResult.success(events.toEnterpriseLogBatch(LoggingChannel.SECURITY, null))
        }
    }

    override fun retrievePreRebootSecurityLogs(): PolicyResult<EnterpriseLogBatch> {
        if (Build.VERSION.SDK_INT < 24) return unsupported("Pre-reboot security logs require API 24+")
        return policyCall {
            val events = dpm.retrievePreRebootSecurityLogs(admin) ?: emptyList()
            PolicyResult.success(events.toEnterpriseLogBatch(LoggingChannel.SECURITY, null))
        }
    }

    override fun isNetworkLoggingEnabled(): PolicyResult<Boolean> {
        if (Build.VERSION.SDK_INT < 26) return unsupported("Network logging requires API 26+")
        return policyCall { PolicyResult.success(dpm.isNetworkLoggingEnabled(admin)) }
    }

    override fun setNetworkLoggingEnabled(enabled: Boolean): PolicyResult<Unit> {
        if (Build.VERSION.SDK_INT < 26) return unsupported("Network logging requires API 26+")
        return policyCall {
            dpm.setNetworkLoggingEnabled(admin, enabled)
            PolicyResult.success()
        }
    }

    override fun retrieveNetworkLogs(batchToken: Long): PolicyResult<EnterpriseLogBatch> {
        if (Build.VERSION.SDK_INT < 26) return unsupported("Network logging requires API 26+")
        return policyCall {
            val events = dpm.retrieveNetworkLogs(admin, batchToken) ?: emptyList()
            PolicyResult.success(events.toEnterpriseLogBatch(LoggingChannel.NETWORK, batchToken))
        }
    }

    override fun getSystemUpdatePolicySpec(): PolicyResult<SystemUpdatePolicySpec> {
        if (Build.VERSION.SDK_INT < 23) return unsupported("System update policy requires API 23+")
        return policyCall {
            val policy = dpm.systemUpdatePolicy
                ?: return@policyCall PolicyResult.success(SystemUpdatePolicySpec(SystemUpdateMode.SYSTEM_DEFAULT))
            val mode = when (policy.policyType) {
                SystemUpdatePolicy.TYPE_INSTALL_AUTOMATIC -> SystemUpdateMode.AUTOMATIC
                SystemUpdatePolicy.TYPE_INSTALL_WINDOWED -> SystemUpdateMode.WINDOWED
                SystemUpdatePolicy.TYPE_POSTPONE -> SystemUpdateMode.POSTPONE
                else -> SystemUpdateMode.SYSTEM_DEFAULT
            }
            val freezes = if (Build.VERSION.SDK_INT >= 28) {
                policy.freezePeriods.map {
                    FreezePeriodSpec(it.start.monthValue, it.start.dayOfMonth, it.end.monthValue, it.end.dayOfMonth)
                }
            } else emptyList()
            PolicyResult.success(
                SystemUpdatePolicySpec(
                    mode = mode,
                    windowStartMinute = policy.installWindowStart.takeIf { it >= 0 },
                    windowEndMinute = policy.installWindowEnd.takeIf { it >= 0 },
                    freezePeriods = freezes,
                )
            )
        }
    }

    override fun setSystemUpdatePolicySpec(spec: SystemUpdatePolicySpec): PolicyResult<Unit> {
        if (Build.VERSION.SDK_INT < 23) return unsupported("System update policy requires API 23+")
        val validation = SystemUpdatePolicyValidator.validate(spec)
        if (!validation.valid) {
            return PolicyResult.failure(
                PolicyStatus.PLATFORM_REJECTED,
                "System update policy validation failed: ${validation.errors.joinToString(",")}",
            )
        }
        return policyCall {
            if (spec.mode == SystemUpdateMode.SYSTEM_DEFAULT) {
                dpm.setSystemUpdatePolicy(admin, null)
                return@policyCall PolicyResult.success()
            }
            val policy = when (spec.mode) {
                SystemUpdateMode.AUTOMATIC -> SystemUpdatePolicy.createAutomaticInstallPolicy()
                SystemUpdateMode.WINDOWED -> SystemUpdatePolicy.createWindowedInstallPolicy(
                    requireNotNull(spec.windowStartMinute),
                    requireNotNull(spec.windowEndMinute),
                )
                SystemUpdateMode.POSTPONE -> SystemUpdatePolicy.createPostponeInstallPolicy()
                SystemUpdateMode.SYSTEM_DEFAULT -> error("handled above")
            }
            if (spec.freezePeriods.isNotEmpty()) {
                if (Build.VERSION.SDK_INT < 28) return@policyCall unsupported("Freeze periods require API 28+")
                policy.setFreezePeriods(spec.freezePeriods.map {
                    FreezePeriod(MonthDay.of(it.startMonth, it.startDay), MonthDay.of(it.endMonth, it.endDay))
                })
            }
            dpm.setSystemUpdatePolicy(admin, policy)
            PolicyResult.success()
        }
    }

    private fun <T> List<T>.toEnterpriseLogBatch(channel: LoggingChannel, token: Long?): EnterpriseLogBatch {
        val lines = map { event ->
            val escaped = event.toString().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
            "{\"event\":\"$escaped\"}"
        }
        return EnterpriseLogBatch(
            channel = channel,
            batchToken = token,
            eventCount = lines.size,
            capturedAtEpochMs = System.currentTimeMillis(),
            payloadJsonLines = lines,
        )
    }

    override fun installCaCertificate(certBytes: ByteArray): PolicyResult<Unit> = policyCall {
        if (!dpm.installCaCert(admin, certBytes)) {
            PolicyResult.failure(PolicyStatus.PLATFORM_REJECTED, "DevicePolicyManager rejected CA certificate")
        } else PolicyResult.success()
    }

    override fun getInstalledCaCertificates(): PolicyResult<List<ByteArray>> = policyCall {
        PolicyResult.success(dpm.getInstalledCaCerts(admin))
    }

    override fun uninstallCaCertificate(certBytes: ByteArray): PolicyResult<Unit> = policyCall {
        dpm.uninstallCaCert(admin, certBytes)
        PolicyResult.success()
    }

    override fun installManagedKeyPair(
        privateKey: PrivateKey,
        certificates: List<Certificate>,
        alias: String,
        requestAccess: Boolean,
    ): PolicyResult<Unit> {
        if (Build.VERSION.SDK_INT < 24) return unsupported("Certificate-chain key install requires API 24+")
        if (certificates.isEmpty()) return PolicyResult.failure(PolicyStatus.PLATFORM_REJECTED, "Certificate chain is empty")
        return policyCall {
            val ok = dpm.installKeyPair(admin, privateKey, certificates.toTypedArray(), alias, requestAccess)
            if (ok) PolicyResult.success() else PolicyResult.failure(PolicyStatus.PLATFORM_REJECTED, "Key pair install rejected")
        }
    }

    override fun removeManagedKeyPair(alias: String): PolicyResult<Unit> = policyCall {
        if (dpm.removeKeyPair(admin, alias)) PolicyResult.success()
        else PolicyResult.failure(PolicyStatus.PLATFORM_REJECTED, "Key pair remove rejected")
    }

    override fun grantManagedKeyPairToApp(alias: String, packageName: String): PolicyResult<Unit> {
        if (Build.VERSION.SDK_INT < 30) return unsupported("Key-pair app grants require API 30+")
        return policyCall {
            val ok = dpm.grantKeyPairToApp(admin, alias, packageName)
            if (ok) PolicyResult.success() else PolicyResult.failure(PolicyStatus.PLATFORM_REJECTED, "Key-pair grant rejected")
        }
    }

    override fun getManagedKeyPairGrants(alias: String): PolicyResult<KeyPairGrantSummary> {
        if (Build.VERSION.SDK_INT < 31) return unsupported("Key-pair grant inventory requires API 31+")
        return policyCall {
            val grants = dpm.getKeyPairGrants(alias).mapValues { it.value.toSet() }
            PolicyResult.success(KeyPairGrantSummary(alias, grants))
        }
    }

    private val certificateDelegationScopes: Set<String>
        get() = setOf(DevicePolicyManager.DELEGATION_CERT_INSTALL, DevicePolicyManager.DELEGATION_CERT_SELECTION)

    override fun getLockTaskPolicySpec(): PolicyResult<LockTaskPolicySpec> = policyCall {
        val packages = dpm.getLockTaskPackages(admin).toSet()
        val features = if (Build.VERSION.SDK_INT >= 28) dpm.getLockTaskFeatures(admin) else 0
        PolicyResult.success(LockTaskPolicySpec(packages, features))
    }

    override fun setLockTaskPolicySpec(spec: LockTaskPolicySpec): PolicyResult<Unit> {
        if (!spec.valid()) return PolicyResult.failure(PolicyStatus.PLATFORM_REJECTED, "Invalid lock-task package name")
        return policyCall {
            dpm.setLockTaskPackages(admin, spec.packages.toTypedArray())
            if (Build.VERSION.SDK_INT >= 28) dpm.setLockTaskFeatures(admin, spec.featureMask)
            PolicyResult.success()
        }
    }

    override fun setDeviceSecurityPolicySpec(spec: DeviceSecurityPolicySpec): PolicyResult<Unit> = policyCall {
        val passwordComplexity = spec.passwordComplexity
        if (passwordComplexity != null) {
            if (Build.VERSION.SDK_INT < 31) return@policyCall unsupported("Password complexity requires API 31+")
            dpm.setRequiredPasswordComplexity(passwordComplexity)
        }
        if (spec.maxFailedPasswordsForWipe < 0) {
            return@policyCall PolicyResult.failure(PolicyStatus.PLATFORM_REJECTED, "Wipe threshold cannot be negative")
        }
        dpm.setMaximumFailedPasswordsForWipe(admin, spec.maxFailedPasswordsForWipe)
        spec.keyguardDisabledFeatures?.let { dpm.setKeyguardDisabledFeatures(admin, it) }
        spec.cameraDisabled?.let { dpm.setCameraDisabled(admin, it) }
        spec.screenCaptureDisabled?.let { dpm.setScreenCaptureDisabled(admin, it) }
        PolicyResult.success()
    }

    override fun setUninstallBlockedPolicy(packageName: String, blocked: Boolean): PolicyResult<Unit> = policyCall {
        dpm.setUninstallBlocked(admin, packageName, blocked)
        PolicyResult.success()
    }

    override fun setManagedApplicationRestrictions(
        packageName: String,
        restrictions: Map<String, String>,
    ): PolicyResult<Unit> = policyCall {
        val bundle = Bundle().apply { restrictions.forEach { (key, value) -> putString(key, value) } }
        dpm.setApplicationRestrictions(admin, packageName, bundle)
        PolicyResult.success()
    }

    override fun clearManagedApplicationData(packageName: String): PolicyResult<Unit> {
        if (Build.VERSION.SDK_INT < 28) return unsupported("Clear application user data requires API 28+")
        return policyCall {
            dpm.clearApplicationUserData(admin, packageName, appContext.mainExecutor) { _, _ -> Unit }
            PolicyResult.success(message = "Clear application data request submitted")
        }
    }

    override fun setUserControlDisabledPackagesPolicy(packageNames: Set<String>): PolicyResult<Unit> {
        if (Build.VERSION.SDK_INT < 30) return unsupported("User-control-disabled packages require API 30+")
        return policyCall {
            dpm.setUserControlDisabledPackages(admin, packageNames.toList())
            PolicyResult.success()
        }
    }

    override fun setFrpPolicySpec(spec: FrpPolicySpec): PolicyResult<Unit> {
        if (Build.VERSION.SDK_INT < 30) return unsupported("FRP policy requires API 30+")
        if (!spec.valid()) return PolicyResult.failure(PolicyStatus.PLATFORM_REJECTED, "Invalid FRP account identifier")
        return policyCall {
            val policy = FactoryResetProtectionPolicy.Builder()
                .setFactoryResetProtectionEnabled(spec.enabled)
                .setFactoryResetProtectionAccounts(spec.accountIds)
                .build()
            dpm.setFactoryResetProtectionPolicy(admin, policy)
            PolicyResult.success()
        }
    }

    override fun getFrpPolicySpec(): PolicyResult<FrpPolicySpec> {
        if (Build.VERSION.SDK_INT < 30) return unsupported("FRP policy requires API 30+")
        return policyCall {
            val policy = dpm.getFactoryResetProtectionPolicy(admin)
                ?: return@policyCall PolicyResult.success(FrpPolicySpec(enabled = true, accountIds = emptyList()))
            PolicyResult.success(
                FrpPolicySpec(
                    enabled = policy.isFactoryResetProtectionEnabled,
                    accountIds = policy.factoryResetProtectionAccounts,
                )
            )
        }
    }

    override fun getCopePolicySnapshot(): PolicyResult<CopePolicySnapshot> {
        if (Build.VERSION.SDK_INT < 30) return unsupported("COPE operations require API 30+")
        return policyCall {
            val suspendedReasons = dpm.getPersonalAppsSuspendedReasons(admin)
            PolicyResult.success(
                CopePolicySnapshot(
                    crossProfilePackages = dpm.getCrossProfilePackages(admin).toSet(),
                    maximumTimeOffMillis = dpm.getManagedProfileMaximumTimeOff(admin),
                    personalAppsSuspended = suspendedReasons != DevicePolicyManager.PERSONAL_APPS_NOT_SUSPENDED,
                    organizationName = dpm.getOrganizationName(admin)?.toString(),
                    affiliationIds = if (Build.VERSION.SDK_INT >= 26) dpm.getAffiliationIds(admin).toSet() else emptySet(),
                )
            )
        }
    }

    override fun setCrossProfilePackagesPolicy(packageNames: Set<String>): PolicyResult<Unit> {
        if (Build.VERSION.SDK_INT < 30) return unsupported("Cross-profile packages require API 30+")
        return policyCall {
            dpm.setCrossProfilePackages(admin, packageNames)
            PolicyResult.success()
        }
    }

    override fun setManagedProfileMaximumTimeOffPolicy(timeoutMillis: Long): PolicyResult<Unit> {
        if (Build.VERSION.SDK_INT < 30) return unsupported("Managed-profile maximum time off requires API 30+")
        if (!CopePolicyValidator.validMaximumTimeOff(timeoutMillis)) {
            return PolicyResult.failure(PolicyStatus.PLATFORM_REJECTED, "Non-zero maximum time off must be at least 72 hours")
        }
        return policyCall {
            dpm.setManagedProfileMaximumTimeOff(admin, timeoutMillis)
            PolicyResult.success()
        }
    }

    override fun setPersonalAppsSuspendedPolicy(suspended: Boolean): PolicyResult<Unit> {
        if (Build.VERSION.SDK_INT < 30) return unsupported("Personal-app suspension requires API 30+")
        return policyCall {
            dpm.setPersonalAppsSuspended(admin, suspended)
            PolicyResult.success()
        }
    }

    override fun setOrganizationIdentity(enterpriseId: String, organizationName: String?): PolicyResult<Unit> {
        if (Build.VERSION.SDK_INT < 31) return unsupported("Organization ID requires API 31+")
        if (!CopePolicyValidator.validOrganizationId(enterpriseId)) {
            return PolicyResult.failure(PolicyStatus.PLATFORM_REJECTED, "Organization ID must be 6..64 characters")
        }
        return policyCall {
            dpm.setOrganizationId(enterpriseId)
            dpm.setOrganizationName(admin, organizationName)
            PolicyResult.success()
        }
    }

    override fun setAffiliationIdsPolicy(ids: Set<String>): PolicyResult<Unit> {
        if (Build.VERSION.SDK_INT < 26) return unsupported("Affiliation IDs require API 26+")
        val normalized = CopePolicyValidator.normalizeAffiliationIds(ids)
        return policyCall {
            dpm.setAffiliationIds(admin, normalized)
            PolicyResult.success()
        }
    }

    override fun setManagedProfileContactsAccessPolicy(spec: ManagedProfilePackagePolicySpec): PolicyResult<Unit> {
        if (Build.VERSION.SDK_INT < 34) return unsupported("Managed-profile contacts PackagePolicy requires API 34+")
        if (!spec.valid()) return PolicyResult.failure(PolicyStatus.PLATFORM_REJECTED, "Invalid managed-profile contacts package policy")
        return policyCall {
            dpm.setManagedProfileContactsAccessPolicy(spec.toPlatformPackagePolicy())
            PolicyResult.success()
        }
    }

    override fun setManagedProfileCallerIdAccessPolicy(spec: ManagedProfilePackagePolicySpec): PolicyResult<Unit> {
        if (Build.VERSION.SDK_INT < 34) return unsupported("Managed-profile caller-ID PackagePolicy requires API 34+")
        if (!spec.valid()) return PolicyResult.failure(PolicyStatus.PLATFORM_REJECTED, "Invalid managed-profile caller-ID package policy")
        return policyCall {
            dpm.setManagedProfileCallerIdAccessPolicy(spec.toPlatformPackagePolicy())
            PolicyResult.success()
        }
    }

    private fun ManagedProfilePackagePolicySpec.toPlatformPackagePolicy(): PackagePolicy? = when (type) {
        PackageAccessPolicyType.UNRESTRICTED -> null
        PackageAccessPolicyType.ALLOWLIST -> PackagePolicy(PackagePolicy.PACKAGE_POLICY_ALLOWLIST, packageNames)
        PackageAccessPolicyType.ALLOWLIST_AND_SYSTEM -> PackagePolicy(PackagePolicy.PACKAGE_POLICY_ALLOWLIST_AND_SYSTEM, packageNames)
        PackageAccessPolicyType.BLOCKLIST -> PackagePolicy(PackagePolicy.PACKAGE_POLICY_BLOCKLIST, packageNames)
    }

    private fun restrictionKey(restriction: DeviceRestriction): String? = when (restriction) {
        DeviceRestriction.THREAD_NETWORK -> if (Build.VERSION.SDK_INT >= 36) UserManager.DISALLOW_THREAD_NETWORK else null
        DeviceRestriction.NFC_RADIO -> if (Build.VERSION.SDK_INT >= 35) UserManager.DISALLOW_NEAR_FIELD_COMMUNICATION_RADIO else null
        DeviceRestriction.NFC_RADIO_CHANGES -> if (Build.VERSION.SDK_INT >= 36) UserManager.DISALLOW_CHANGE_NEAR_FIELD_COMMUNICATION_RADIO else null
    }

    private fun deviceWideDpm(): DevicePolicyManager {
        val profileOwner = dpm.isProfileOwnerApp(appContext.packageName)
        val organizationOwned = Build.VERSION.SDK_INT >= 30 && profileOwner &&
            runCatching { dpm.isOrganizationOwnedDeviceWithManagedProfile }.getOrDefault(false)
        return if (organizationOwned) dpm.getParentProfileInstance(admin) else dpm
    }

    private inline fun <T> policyCall(block: () -> PolicyResult<T>): PolicyResult<T> = try {
        block()
    } catch (e: SecurityException) {
        securityFailure(e)
    } catch (e: RuntimeException) {
        runtimeFailure(e)
    }

    private fun <T> unsupported(message: String): PolicyResult<T> =
        PolicyResult.failure(status = PolicyStatus.UNSUPPORTED, message = message)

    private fun <T> securityFailure(e: SecurityException): PolicyResult<T> =
        PolicyResult.failure(
            status = PolicyStatus.SECURITY_EXCEPTION,
            message = e.message ?: "Device policy operation was denied",
            errorType = e.javaClass.name
        )

    private fun <T> runtimeFailure(e: RuntimeException): PolicyResult<T> =
        PolicyResult.failure(
            status = PolicyStatus.FAILED,
            message = e.message ?: "Device policy operation failed",
            errorType = e.javaClass.name
        )
}
