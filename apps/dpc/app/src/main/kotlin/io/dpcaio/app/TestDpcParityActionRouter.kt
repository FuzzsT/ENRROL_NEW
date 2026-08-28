package io.dpcaio.app

import io.dpcaio.policy.PolicyResult
import io.dpcaio.policy.PolicyStatus
import io.dpcaio.policy.parity.ParityActionHandler
import io.dpcaio.policy.parity.ParityActionRequest
import io.dpcaio.policy.parity.ParityActionResult
import io.dpcaio.policy.parity.TestDpcImplementationState
import io.dpcaio.policy.parity.TestDpcParityEntry

class TestDpcParityActionRouter {
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
        }
        return ParityActionResult(success = result.isSuccess, message = message)
    }

    // Task 5 establishes typed routing only. Task 6 binds these IDs to the existing
    // public-API gateways. Until then each registered route remains explicitly fail-closed.
    private fun setProfileName(request: ParityActionRequest): PolicyResult<String> = notBound("profile.set_name", request)
    private fun addCrossProfileIntentFilter(request: ParityActionRequest): PolicyResult<String> =
        notBound("cross_profile.add_intent_filter", request)
    private fun clearCrossProfileIntentFilters(request: ParityActionRequest): PolicyResult<String> =
        notBound("cross_profile.clear_intent_filters", request)
    private fun setAlwaysOnVpn(request: ParityActionRequest): PolicyResult<String> = notBound("network.always_on_vpn", request)
    private fun setLocationEnabled(request: ParityActionRequest): PolicyResult<String> = notBound("location.set_enabled", request)
    private fun setAccountManagementDisabled(request: ParityActionRequest): PolicyResult<String> =
        notBound("account.set_management_disabled", request)
    private fun getAccountManagementDisabled(request: ParityActionRequest): PolicyResult<String> =
        notBound("account.get_management_disabled", request)
    private fun resetPasswordWithToken(request: ParityActionRequest): PolicyResult<String> =
        notBound("credential.reset_with_token", request)
    private fun removeManagedKeyPair(request: ParityActionRequest): PolicyResult<String> =
        notBound("credential.remove_key_pair", request)

    private fun notBound(handlerId: String, request: ParityActionRequest): PolicyResult<String> =
        PolicyResult.failure(
            status = PolicyStatus.UNSUPPORTED,
            message = "HANDLER_NOT_BOUND: $handlerId for ${request.parityId}; backend binding is not part of Task 5",
            errorType = "BACKEND_NOT_BOUND",
        )
}
