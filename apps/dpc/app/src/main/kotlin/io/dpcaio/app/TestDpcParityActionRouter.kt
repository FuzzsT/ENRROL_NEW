package io.dpcaio.app

import android.content.ComponentName
import android.content.Context
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
        val categories = request.values["categories"].orEmpty()
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()
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
        val packageName = request.values["package_name"]?.trim().orEmpty()
        if (packageName.isBlank()) return invalidInput("package_name", "VPN package is required")
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
        val alias = request.values["alias"]?.trim().orEmpty()
        if (alias.isBlank()) return invalidInput("alias", "key-pair alias is required")
        return devicePolicyGateway.removeManagedKeyPair(alias).asText { "removed=$alias" }
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
