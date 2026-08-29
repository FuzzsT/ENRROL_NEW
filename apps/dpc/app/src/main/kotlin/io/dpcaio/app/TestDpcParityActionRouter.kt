package io.dpcaio.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import io.dpcaio.account.android.AndroidAccountReorderGateway
import io.dpcaio.network.android.AlwaysOnVpnController
import io.dpcaio.policy.CrossProfileDirection
import io.dpcaio.policy.CrossProfileIntentRule
import io.dpcaio.policy.PolicyResult
import io.dpcaio.policy.PolicyStatus
import io.dpcaio.policy.android.AndroidCredentialRecoveryGateway
import io.dpcaio.policy.android.AndroidDevicePolicyGateway
import io.dpcaio.policy.android.AndroidGlobalLocationPolicyGateway
import io.dpcaio.policy.android.AndroidWorkProfileLifecycleGateway
import io.dpcaio.policy.android.parity.AndroidAppParityGateway
import io.dpcaio.policy.android.parity.AndroidNetworkParityGateway
import io.dpcaio.policy.android.parity.AndroidUserParityGateway
import io.dpcaio.policy.parity.ParityActionHandler
import io.dpcaio.policy.parity.ParityActionRequest
import io.dpcaio.policy.parity.ParityActionResult
import io.dpcaio.policy.parity.TestDpcImplementationState
import io.dpcaio.policy.parity.TestDpcParityEntry

class TestDpcParityActionRouter(context: Context) {
    private val appContext = context.applicationContext
    private val admin = ComponentName(appContext, AioDeviceAdminReceiver::class.java)
    private val workProfileGateway = AndroidWorkProfileLifecycleGateway(appContext, admin)
    private val alwaysOnVpnController = AlwaysOnVpnController(appContext, admin)
    private val locationGateway = AndroidGlobalLocationPolicyGateway(appContext, admin)
    private val accountGateway = AndroidAccountReorderGateway(appContext, admin)
    private val credentialRecoveryGateway = AndroidCredentialRecoveryGateway(appContext, admin)
    private val devicePolicyGateway = AndroidDevicePolicyGateway(appContext, admin)
    private val appParityGateway = AndroidAppParityGateway(appContext, admin)
    private val networkParityGateway = AndroidNetworkParityGateway(appContext, admin)
    private val userParityGateway = AndroidUserParityGateway(appContext, admin)

    private val handlers: Map<String, ParityActionHandler> = mapOf(
        "profile.set_name" to ParityActionHandler(::setProfileName),
        "cross_profile.add_intent_filter" to ParityActionHandler(::addCrossProfileIntentFilter),
        "cross_profile.clear_intent_filters" to ParityActionHandler(::clearCrossProfileIntentFilters),
        "network.always_on_vpn" to ParityActionHandler(::setAlwaysOnVpn),
        "location.set_enabled" to ParityActionHandler(::setLocationEnabled),
        "account.set_management_disabled" to ParityActionHandler(::setAccountManagementDisabled),
        "account.get_management_disabled" to ParityActionHandler(::getAccountManagementDisabled),
        "credential.reset_with_token" to ParityActionHandler(::resetPasswordWithToken),
        "credential.remove_key_pair" to ParityActionHandler(::removeManagedKeyPair),
        "app.enable_system_package" to ParityActionHandler(::enableSystemPackage),
        "app.enable_system_intent" to ParityActionHandler(::enableSystemAppsByIntent),
        "app.install_existing" to ParityActionHandler(::installExistingPackage),
        "app.uninstall" to ParityActionHandler(::uninstallPackage),
        "app.hide" to ParityActionHandler { request -> setApplicationHidden(request, true) },
        "app.unhide" to ParityActionHandler { request -> setApplicationHidden(request, false) },
        "app.suspend" to ParityActionHandler { request -> setPackagesSuspended(request, true) },
        "app.unsuspend" to ParityActionHandler { request -> setPackagesSuspended(request, false) },
        "app.clear_data" to ParityActionHandler(::clearApplicationData),
        "app.keep_uninstalled" to ParityActionHandler(::setKeepUninstalledPackages),
        "app.managed_configurations" to ParityActionHandler(::setManagedConfigurations),
        "app.disable_metered_data" to ParityActionHandler(::setMeteredDataDisabledPackages),
        "app.restrictions_manager" to ParityActionHandler(::setRestrictionsManagingPackage),
        "delegation.set_scopes" to ParityActionHandler(::setDelegatedScopes),
        "app.block_uninstall" to ParityActionHandler(::setUninstallBlocked),
        "app.block_uninstall_list" to ParityActionHandler(::setUninstallBlockedList),
        "network.preferential" to ParityActionHandler(::setPreferentialNetworkService),
        "network.set_global_proxy" to ParityActionHandler(::setGlobalProxy),
        "network.clear_global_proxy" to ParityActionHandler(::clearGlobalProxy),
        "network.wifi_lockdown" to ParityActionHandler(::setWifiLockdown),
        "network.wifi_remove_non_caller" to ParityActionHandler(::removeNonCallerWifiNetworks),
        "network.wifi_mac" to ParityActionHandler(::getWifiMac),
        "network.wifi_min_security" to ParityActionHandler(::setWifiMinimumSecurity),
        "network.wifi_ssid_policy" to ParityActionHandler(::setWifiSsidPolicy),
        "user.create" to ParityActionHandler(::createManagedUser),
        "user.remove" to ParityActionHandler(::removeManagedUser),
        "user.switch" to ParityActionHandler(::switchManagedUser),
        "user.start_background" to ParityActionHandler(::startManagedUserInBackground),
        "user.stop" to ParityActionHandler(::stopManagedUser),
        "user.logout" to ParityActionHandler(::logoutManagedUser),
        "user.logout_enabled" to ParityActionHandler(::setLogoutEnabled),
        "user.session_messages" to ParityActionHandler(::setUserSessionMessages),
        "user.is_affiliated" to ParityActionHandler(::queryAffiliatedUser),
        "user.is_ephemeral" to ParityActionHandler(::queryEphemeralUser),
        "user.restriction" to ParityActionHandler { request -> setUserRestriction(request, false) },
        "user.restriction_parent" to ParityActionHandler { request -> setUserRestriction(request, true) },
        "user.short_support" to ParityActionHandler(::setShortSupportMessage),
        "user.long_support" to ParityActionHandler(::setLongSupportMessage),
        "device.request_bugreport" to ParityActionHandler(::requestBugreport),
        "device.backup_service" to ParityActionHandler(::setBackupServiceEnabled),
        "device.common_criteria" to ParityActionHandler(::setCommonCriteriaModeEnabled),
        "device.reboot" to ParityActionHandler(::rebootDevice),
        "device.wipe_profile" to ParityActionHandler(::wipeManagedProfile),
        "device.factory_reset" to ParityActionHandler(::factoryResetDevice),
        "device.transfer_ownership" to ParityActionHandler(::transferOwnership),
    )

    fun isRegistered(handlerId: String): Boolean = handlers.containsKey(handlerId)

    fun execute(entry: TestDpcParityEntry, request: ParityActionRequest): ParityActionResult {
        if (request.parityId != entry.id) {
            return ParityActionResult(false, "PARITY_ID_MISMATCH: expected ${entry.id}, got ${request.parityId}")
        }
        if (entry.deprecated || entry.implementationState == TestDpcImplementationState.DEPRECATED_UNAVAILABLE) {
            return ParityActionResult(false, "DEPRECATED_UNAVAILABLE: ${entry.unavailableReason ?: entry.testDpcKey}")
        }
        val handlerId = entry.handlerId
            ?: return ParityActionResult(false, "NO_HANDLER: ${entry.testDpcKey}")
        val handler = handlers[handlerId]
            ?: return ParityActionResult(false, "UNKNOWN_HANDLER: $handlerId")

        val result = try {
            handler.execute(request)
        } catch (error: SecurityException) {
            PolicyResult.failure(
                status = PolicyStatus.SECURITY_EXCEPTION,
                message = error.message ?: "Platform rejected parity action",
                errorType = error::class.java.simpleName,
            )
        } catch (error: UnsupportedOperationException) {
            PolicyResult.failure(
                status = PolicyStatus.UNSUPPORTED,
                message = error.message ?: "Parity action is unsupported on this platform",
                errorType = error::class.java.simpleName,
            )
        } catch (error: Exception) {
            PolicyResult.failure(
                status = PolicyStatus.FAILED,
                message = error.message ?: "Parity action failed",
                errorType = error::class.java.simpleName,
            )
        }

        val message = buildString {
            append(result.status.name)
            result.message?.takeIf { it.isNotBlank() }?.let { append(": ").append(it) }
            result.errorType?.takeIf { it.isNotBlank() }?.let { append(" [").append(it).append(']') }
            result.value?.takeIf { it.isNotBlank() }?.let { append(" => ").append(it) }
        }
        return ParityActionResult(success = result.isSuccess, message = message)
    }

    private fun createManagedUser(request: ParityActionRequest): PolicyResult<String> {
        val name = requiredValue(request, "name")
            ?: return invalidInput("name", "user name is required")
        val rawFlags = request.values["flags"]?.trim().orEmpty()
        val flags = if (rawFlags.isBlank()) 0 else rawFlags.toIntOrNull()
            ?: return invalidInput("flags", "expected integer flags")
        return userParityGateway.createAndManageUser(name, flags).asText { serial ->
            "userSerial=${serial ?: "unknown"}"
        }
    }

    private fun removeManagedUser(request: ParityActionRequest): PolicyResult<String> {
        val serial = requiredValue(request, "user_serial")
            ?: return invalidInput("user_serial", "user serial is required")
        return userParityGateway.removeUser(serial).asText { "removedSerial=$serial" }
    }

    private fun switchManagedUser(request: ParityActionRequest): PolicyResult<String> {
        val serial = requiredValue(request, "user_serial")
            ?: return invalidInput("user_serial", "user serial is required")
        return userParityGateway.switchUser(serial).asText { "switchSerial=$serial" }
    }

    private fun startManagedUserInBackground(request: ParityActionRequest): PolicyResult<String> {
        val serial = requiredValue(request, "user_serial")
            ?: return invalidInput("user_serial", "user serial is required")
        return userParityGateway.startUserInBackground(serial).asText { result -> "result=${result ?: -1},serial=$serial" }
    }

    private fun stopManagedUser(request: ParityActionRequest): PolicyResult<String> {
        val serial = requiredValue(request, "user_serial")
            ?: return invalidInput("user_serial", "user serial is required")
        return userParityGateway.stopUser(serial).asText { result -> "result=${result ?: -1},serial=$serial" }
    }

    private fun logoutManagedUser(request: ParityActionRequest): PolicyResult<String> =
        userParityGateway.logoutUser().asText { result -> "result=${result ?: -1}" }

    private fun setLogoutEnabled(request: ParityActionRequest): PolicyResult<String> {
        val enabled = parseBoolean(request.values["enabled"])
            ?: return invalidInput("enabled", "expected true/false")
        return userParityGateway.setLogoutEnabled(enabled).asText { "enabled=$enabled" }
    }

    private fun setUserSessionMessages(request: ParityActionRequest): PolicyResult<String> {
        val start = request.values["start_message"]?.takeIf { it.isNotBlank() }
        val end = request.values["end_message"]?.takeIf { it.isNotBlank() }
        return userParityGateway.setUserSessionMessages(start, end).asText {
            "start=${if (start == null) "<cleared>" else "set"},end=${if (end == null) "<cleared>" else "set"}"
        }
    }

    private fun queryAffiliatedUser(request: ParityActionRequest): PolicyResult<String> =
        userParityGateway.isAffiliatedUser().asText { affiliated -> "affiliated=${affiliated == true}" }

    private fun queryEphemeralUser(request: ParityActionRequest): PolicyResult<String> =
        userParityGateway.isEphemeralUser().asText { ephemeral -> "ephemeral=${ephemeral == true}" }

    private fun setUserRestriction(request: ParityActionRequest, parent: Boolean): PolicyResult<String> {
        val key = requiredValue(request, "restriction_key")
            ?: return invalidInput("restriction_key", "UserManager restriction key is required")
        val enabled = parseBoolean(request.values["enabled"])
            ?: return invalidInput("enabled", "expected true/false")
        return userParityGateway.setUserRestriction(key, enabled, parent).asText {
            "restriction=$key,enabled=$enabled,parent=$parent"
        }
    }

    private fun setShortSupportMessage(request: ParityActionRequest): PolicyResult<String> {
        val message = request.values["message"]?.takeIf { it.isNotBlank() }
        return userParityGateway.setShortSupportMessage(message).asText { "shortSupport=${if (message == null) "cleared" else "set"}" }
    }

    private fun setLongSupportMessage(request: ParityActionRequest): PolicyResult<String> {
        val message = request.values["message"]?.takeIf { it.isNotBlank() }
        return userParityGateway.setLongSupportMessage(message).asText { "longSupport=${if (message == null) "cleared" else "set"}" }
    }

    private fun requestBugreport(request: ParityActionRequest): PolicyResult<String> =
        userParityGateway.requestBugreport().asText { "bugreport=requested" }

    private fun setBackupServiceEnabled(request: ParityActionRequest): PolicyResult<String> {
        val enabled = parseBoolean(request.values["enabled"])
            ?: return invalidInput("enabled", "expected true/false")
        return userParityGateway.setBackupServiceEnabled(enabled).asText { "backupService=$enabled" }
    }

    private fun setCommonCriteriaModeEnabled(request: ParityActionRequest): PolicyResult<String> {
        val enabled = parseBoolean(request.values["enabled"])
            ?: return invalidInput("enabled", "expected true/false")
        return userParityGateway.setCommonCriteriaModeEnabled(enabled).asText { "commonCriteria=$enabled" }
    }

    private fun rebootDevice(request: ParityActionRequest): PolicyResult<String> =
        userParityGateway.reboot().asText { "reboot=requested" }

    private fun wipeManagedProfile(request: ParityActionRequest): PolicyResult<String> {
        val flags = parseOptionalFlags(request) ?: return invalidInput("flags", "expected integer flags")
        return userParityGateway.wipeManagedProfile(flags).asText { "profileWipe=requested,flags=$flags" }
    }

    private fun factoryResetDevice(request: ParityActionRequest): PolicyResult<String> {
        val flags = parseOptionalFlags(request) ?: return invalidInput("flags", "expected integer flags")
        return userParityGateway.factoryResetDevice(flags).asText { "factoryReset=requested,flags=$flags" }
    }

    private fun transferOwnership(request: ParityActionRequest): PolicyResult<String> {
        val raw = requiredValue(request, "target_component")
            ?: return invalidInput("target_component", "target admin component is required")
        val target = ComponentName.unflattenFromString(raw)
            ?: return invalidInput("target_component", "expected package/class component")
        return userParityGateway.transferOwnership(target).asText { "target=${target.flattenToShortString()}" }
    }

    private fun parseOptionalFlags(request: ParityActionRequest): Int? {
        val raw = request.values["flags"]?.trim().orEmpty()
        return if (raw.isBlank()) 0 else raw.toIntOrNull()
    }

    private fun setProfileName(request: ParityActionRequest): PolicyResult<String> {
        val name = request.values["name"]?.trim().orEmpty()
        if (name.isBlank()) return invalidInput("name", "profile name is required")
        return workProfileGateway.setProfileName(name).asText { it.orEmpty() }
    }

    private fun addCrossProfileIntentFilter(request: ParityActionRequest): PolicyResult<String> {
        val action = request.values["action"]?.trim().orEmpty()
        if (action.isBlank()) return invalidInput("action", "intent action is required")
        val direction = parseDirection(request.values["direction"])
            ?: return invalidInput("direction", "expected MANAGED_TO_PARENT, PARENT_TO_MANAGED, or BIDIRECTIONAL")
        val categories = parseCsv(request.values["categories"]).toSet()
        val rule = CrossProfileIntentRule(
            id = "parity:${direction.name}:${Integer.toHexString(action.hashCode())}",
            action = action,
            categories = categories,
            direction = direction,
        )
        return workProfileGateway.upsertCrossProfileRule(rule).asText { inventory ->
            "desiredRules=${inventory?.rules?.size ?: 0}"
        }
    }

    private fun clearCrossProfileIntentFilters(request: ParityActionRequest): PolicyResult<String> =
        workProfileGateway.clearDpcRules().asText { inventory ->
            "desiredRules=${inventory?.rules?.size ?: 0}"
        }

    private fun setAlwaysOnVpn(request: ParityActionRequest): PolicyResult<String> {
        val packageName = requiredValue(request, "package_name")
            ?: return invalidInput("package_name", "VPN package is required")
        val lockdown = parseBoolean(request.values["lockdown"])
            ?: return invalidInput("lockdown", "expected true/false")
        if (!alwaysOnVpnController.set(packageName, lockdown)) {
            return PolicyResult.failure(
                PolicyStatus.PLATFORM_REJECTED,
                "Always-on VPN set/readback failed for $packageName",
            )
        }
        val observed = alwaysOnVpnController.currentPackage()
        return if (observed == packageName) {
            PolicyResult.success("package=$observed,lockdown=$lockdown", "READBACK_VERIFIED")
        } else {
            PolicyResult.failure(PolicyStatus.PLATFORM_REJECTED, "Always-on VPN readback mismatch")
        }
    }

    private fun setLocationEnabled(request: ParityActionRequest): PolicyResult<String> {
        val enabled = parseBoolean(request.values["enabled"])
            ?: return invalidInput("enabled", "expected true/false")
        return locationGateway.set(enabled).asText { observed -> "enabled=${observed ?: false}" }
    }

    private fun setAccountManagementDisabled(request: ParityActionRequest): PolicyResult<String> {
        val disabled = parseBoolean(request.values["disabled"])
            ?: return invalidInput("disabled", "expected true/false")
        accountGateway.setGoogleAccountManagementDisabled(disabled)
        val observed = accountGateway.isAccountManagementDisabled()
        return if (observed == disabled) {
            PolicyResult.success("disabled=$observed", "READBACK_VERIFIED")
        } else {
            PolicyResult.failure(PolicyStatus.PLATFORM_REJECTED, "Account-management readback mismatch")
        }
    }

    private fun getAccountManagementDisabled(request: ParityActionRequest): PolicyResult<String> =
        PolicyResult.success("disabled=${accountGateway.isAccountManagementDisabled()}")

    private fun resetPasswordWithToken(request: ParityActionRequest): PolicyResult<String> {
        val tokenReference = request.values["token"]?.trim().orEmpty()
        if (!tokenReference.equals("STORED", ignoreCase = true)) {
            return invalidInput(
                "token",
                "enter STORED to use the encrypted reset token managed by Credential Recovery; raw tokens are not accepted",
            )
        }
        val credential = request.values["new_credential"]
            ?: return invalidInput("new_credential", "new credential is required")
        if (credential.isEmpty()) return invalidInput("new_credential", "new credential cannot be empty")
        return credentialRecoveryGateway.resetCredential(credential.toCharArray()).asText { ok ->
            "reset=${ok == true}"
        }
    }

    private fun removeManagedKeyPair(request: ParityActionRequest): PolicyResult<String> {
        val alias = requiredValue(request, "alias")
            ?: return invalidInput("alias", "key-pair alias is required")
        return devicePolicyGateway.removeManagedKeyPair(alias).asText { "removed=$alias" }
    }

    private fun enableSystemPackage(request: ParityActionRequest): PolicyResult<String> {
        val packageName = requiredValue(request, "package_name")
            ?: return invalidInput("package_name", "package name is required")
        return appParityGateway.enableSystemApp(packageName).asText { "enabled=$packageName" }
    }

    private fun enableSystemAppsByIntent(request: ParityActionRequest): PolicyResult<String> {
        val intentUri = requiredValue(request, "intent_uri")
            ?: return invalidInput("intent_uri", "intent URI is required")
        val intent = runCatching { Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME) }
            .getOrElse { return invalidInput("intent_uri", "intent URI parse failed: ${it.javaClass.simpleName}") }
        return appParityGateway.enableSystemAppsByIntent(intent).asText { count -> "enabled=${count ?: 0}" }
    }

    private fun installExistingPackage(request: ParityActionRequest): PolicyResult<String> {
        val packageName = requiredValue(request, "package_name")
            ?: return invalidInput("package_name", "package name is required")
        return appParityGateway.installExistingPackage(packageName).asText { it ?: packageName }
    }

    private fun uninstallPackage(request: ParityActionRequest): PolicyResult<String> {
        val packageName = requiredValue(request, "package_name")
            ?: return invalidInput("package_name", "package name is required")
        return appParityGateway.uninstallPackage(packageName).asText { "submitted=$packageName" }
    }

    private fun setApplicationHidden(request: ParityActionRequest, hidden: Boolean): PolicyResult<String> {
        val packageName = requiredValue(request, "package_name")
            ?: return invalidInput("package_name", "package name is required")
        return devicePolicyGateway.setApplicationHidden(packageName, hidden).asText {
            "package=$packageName,hidden=$hidden"
        }
    }

    private fun setPackagesSuspended(request: ParityActionRequest, suspended: Boolean): PolicyResult<String> {
        val packages = requiredPackages(request, "packages")
            ?: return invalidInput("packages", "at least one package is required")
        return devicePolicyGateway.setPackagesSuspended(packages.toSet(), suspended).asText { failures ->
            val failed = failures.orEmpty().sorted().joinToString()
            "requested=${packages.size},suspended=$suspended,failures=${if (failed.isBlank()) "none" else failed}"
        }
    }

    private fun clearApplicationData(request: ParityActionRequest): PolicyResult<String> {
        val packageName = requiredValue(request, "package_name")
            ?: return invalidInput("package_name", "package name is required")
        return devicePolicyGateway.clearManagedApplicationData(packageName).asText { "submitted=$packageName" }
    }

    private fun setKeepUninstalledPackages(request: ParityActionRequest): PolicyResult<String> {
        val packages = requiredPackages(request, "packages")
            ?: return invalidInput("packages", "at least one package is required")
        return appParityGateway.setKeepUninstalledPackages(packages).asText { "packages=${packages.size}" }
    }

    private fun setManagedConfigurations(request: ParityActionRequest): PolicyResult<String> {
        val packageName = requiredValue(request, "package_name")
            ?: return invalidInput("package_name", "package name is required")
        val restrictions = parseRestrictions(request.values["restrictions"])
            ?: return invalidInput("restrictions", "expected CSV key=value pairs")
        return devicePolicyGateway.setManagedApplicationRestrictions(packageName, restrictions).asText {
            "package=$packageName,restrictions=${restrictions.size}"
        }
    }

    private fun setMeteredDataDisabledPackages(request: ParityActionRequest): PolicyResult<String> {
        val packages = requiredPackages(request, "packages")
            ?: return invalidInput("packages", "at least one package is required")
        return appParityGateway.setMeteredDataDisabledPackages(packages).asText { failures ->
            val failed = failures.orEmpty().sorted().joinToString()
            "requested=${packages.size},failures=${if (failed.isBlank()) "none" else failed}"
        }
    }

    private fun setRestrictionsManagingPackage(request: ParityActionRequest): PolicyResult<String> {
        val raw = request.values["package_name"]?.trim().orEmpty()
        val packageName = raw.takeIf(String::isNotEmpty)
        return appParityGateway.setApplicationRestrictionsManagingPackage(packageName).asText {
            "package=${packageName ?: "<cleared>"}"
        }
    }

    private fun setDelegatedScopes(request: ParityActionRequest): PolicyResult<String> {
        val packageName = requiredValue(request, "package_name")
            ?: return invalidInput("package_name", "package name is required")
        val scopes = requiredPackages(request, "scopes")
            ?: return invalidInput("scopes", "at least one delegated scope is required")
        return devicePolicyGateway.setDelegatedScopes(packageName, scopes.toSet()).asText {
            "package=$packageName,scopes=${scopes.size}"
        }
    }

    private fun setUninstallBlocked(request: ParityActionRequest): PolicyResult<String> {
        val packageName = requiredValue(request, "package_name")
            ?: return invalidInput("package_name", "package name is required")
        val blocked = parseBoolean(request.values["blocked"])
            ?: return invalidInput("blocked", "expected true/false")
        return devicePolicyGateway.setUninstallBlockedPolicy(packageName, blocked).asText {
            "package=$packageName,blocked=$blocked"
        }
    }

    private fun setUninstallBlockedList(request: ParityActionRequest): PolicyResult<String> {
        val packages = requiredPackages(request, "packages")
            ?: return invalidInput("packages", "at least one package is required")
        val blocked = parseBoolean(request.values["blocked"])
            ?: return invalidInput("blocked", "expected true/false")
        packages.forEach { packageName ->
            val result = devicePolicyGateway.setUninstallBlockedPolicy(packageName, blocked)
            if (!result.isSuccess) return result.asText { "package=$packageName" }
        }
        return PolicyResult.success("packages=${packages.size},blocked=$blocked", "READBACK_NOT_AVAILABLE")
    }

    private fun setPreferentialNetworkService(request: ParityActionRequest): PolicyResult<String> {
        val enabled = parseBoolean(request.values["enabled"])
            ?: return invalidInput("enabled", "expected true/false")
        return networkParityGateway.setPreferentialNetworkServiceEnabled(enabled).asText { observed ->
            "enabled=${observed == true}"
        }
    }

    private fun setGlobalProxy(request: ParityActionRequest): PolicyResult<String> {
        val host = requiredValue(request, "host")
            ?: return invalidInput("host", "proxy host is required")
        val port = request.values["port"]?.trim()?.toIntOrNull()
            ?: return invalidInput("port", "integer proxy port is required")
        val exclusionList = parseCsv(request.values["exclusion_list"])
        return networkParityGateway.setRecommendedGlobalProxy(host, port, exclusionList).asText {
            "host=$host,port=$port,exclusions=${exclusionList.size}"
        }
    }

    private fun clearGlobalProxy(request: ParityActionRequest): PolicyResult<String> =
        networkParityGateway.clearRecommendedGlobalProxy().asText { "cleared=true" }

    private fun setWifiLockdown(request: ParityActionRequest): PolicyResult<String> {
        val lockdown = parseBoolean(request.values["lockdown"])
            ?: return invalidInput("lockdown", "expected true/false")
        return networkParityGateway.setConfiguredNetworksLockdownState(lockdown).asText { observed ->
            "lockdown=${observed == true}"
        }
    }

    private fun removeNonCallerWifiNetworks(request: ParityActionRequest): PolicyResult<String> =
        networkParityGateway.removeNonCallerConfiguredNetworks().asText { removed ->
            "removedAny=${removed == true}"
        }

    private fun getWifiMac(request: ParityActionRequest): PolicyResult<String> =
        networkParityGateway.getWifiMacAddress().asText { mac -> "mac=${mac.orEmpty()}" }

    private fun setWifiMinimumSecurity(request: ParityActionRequest): PolicyResult<String> {
        val raw = requiredValue(request, "level")
            ?: return invalidInput("level", "minimum Wi-Fi security level is required")
        val level = networkParityGateway.parseWifiSecurityLevel(raw)
            ?: return invalidInput("level", "expected OPEN, PERSONAL, ENTERPRISE_EAP, ENTERPRISE_192, or matching integer")
        return networkParityGateway.setMinimumRequiredWifiSecurityLevel(level).asText { observed ->
            "level=${observed ?: -1}"
        }
    }

    private fun setWifiSsidPolicy(request: ParityActionRequest): PolicyResult<String> {
        val rawType = requiredValue(request, "policy_type")
            ?: return invalidInput("policy_type", "ALLOWLIST or DENYLIST is required")
        val policyType = networkParityGateway.parseWifiSsidPolicyType(rawType)
            ?: return invalidInput("policy_type", "expected ALLOWLIST or DENYLIST")
        val ssids = requiredPackages(request, "ssids")
            ?: return invalidInput("ssids", "at least one SSID is required")
        return networkParityGateway.setWifiSsidPolicy(policyType, ssids.toSet())
    }
    private fun parseBoolean(raw: String?): Boolean? = when (raw?.trim()?.lowercase()) {
        "true", "1", "yes", "on" -> true
        "false", "0", "no", "off" -> false
        else -> null
    }

    private fun parseDirection(raw: String?): CrossProfileDirection? {
        val normalized = raw?.trim()?.uppercase()?.replace('-', '_')?.replace(' ', '_') ?: return null
        return runCatching { CrossProfileDirection.valueOf(normalized) }.getOrNull()
    }

    private fun parseCsv(raw: String?): List<String> = raw.orEmpty()
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()

    private fun requiredPackages(request: ParityActionRequest, key: String): List<String>? =
        parseCsv(request.values[key]).takeIf(List<String>::isNotEmpty)

    private fun requiredValue(request: ParityActionRequest, key: String): String? =
        request.values[key]?.trim()?.takeIf(String::isNotEmpty)

    private fun parseRestrictions(raw: String?): Map<String, String>? {
        if (raw.isNullOrBlank()) return emptyMap()
        val result = linkedMapOf<String, String>()
        raw.split(',').forEach { item ->
            val normalized = item.trim()
            val separator = normalized.indexOf('=')
            if (separator <= 0) return null
            val key = normalized.substring(0, separator).trim()
            if (key.isBlank()) return null
            result[key] = normalized.substring(separator + 1).trim()
        }
        return result
    }

    private fun invalidInput(key: String, detail: String): PolicyResult<String> =
        PolicyResult.failure(
            status = PolicyStatus.FAILED,
            message = "INVALID_INPUT:$key:$detail",
            errorType = "INVALID_INPUT",
        )

    private fun <T> PolicyResult<T>.asText(render: (T?) -> String): PolicyResult<String> =
        if (isSuccess) {
            PolicyResult.success(render(value), message)
        } else {
            PolicyResult.failure(status, message ?: status.name, errorType)
        }
}
