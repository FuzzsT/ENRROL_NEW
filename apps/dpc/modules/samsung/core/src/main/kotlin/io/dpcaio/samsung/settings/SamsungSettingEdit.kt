package io.dpcaio.samsung.settings

enum class SettingNamespace { SYSTEM, SECURE, GLOBAL, KNOX_DEEP_SETTING, SAMSUNG_CUSTOM }

enum class SettingWriteRoute {
    PUBLIC_SETTINGS,
    WRITE_SETTINGS_USER_APPROVAL,
    DEVICE_OWNER,
    KNOX_DEEP_SETTINGS,
    SHIZUKU_SETTINGS,
    SYSTEM_PRIVILEGED,
    LAB_ONLY
}

enum class SettingEditStatus { VERIFIED, REVERTED, DENIED, UNSUPPORTED }

data class SettingEditRequest(
    val namespace: SettingNamespace,
    val key: String,
    val value: String,
    val routes: List<SettingWriteRoute>,
    val verificationDelaysMs: List<Long> = listOf(0L, 250L, 1000L)
)

data class SettingEditAttempt(
    val route: SettingWriteRoute,
    val writeAccepted: Boolean,
    val readBack: String?
)

data class SettingEditResult(
    val status: SettingEditStatus,
    val verifiedRoute: SettingWriteRoute?,
    val before: String?,
    val after: String?,
    val attempts: List<SettingEditAttempt>
)

fun interface SettingDelay { fun await(ms: Long) }

object NoOpSettingDelay : SettingDelay { override fun await(ms: Long) = Unit }

interface SettingGateway {
    fun read(namespace: SettingNamespace, key: String): String?
    fun write(route: SettingWriteRoute, namespace: SettingNamespace, key: String, value: String): Boolean
}

class SamsungSettingEditCoordinator(
    private val gateway: SettingGateway,
    private val delay: SettingDelay = NoOpSettingDelay
) {
    fun apply(request: SettingEditRequest): SettingEditResult {
        val before = gateway.read(request.namespace, request.key)
        val attempts = mutableListOf<SettingEditAttempt>()
        val acceptedRoutes = mutableListOf<SettingWriteRoute>()

        for (route in request.routes.distinct()) {
            val accepted = gateway.write(route, request.namespace, request.key, request.value)
            if (accepted) acceptedRoutes += route
            val verification = verifyStableValue(request, request.value, accepted)
            attempts += SettingEditAttempt(route, accepted, verification.readBack)
            if (accepted && verification.stable) {
                return SettingEditResult(SettingEditStatus.VERIFIED, route, before, verification.readBack, attempts)
            }
        }

        val afterFailedWrite = gateway.read(request.namespace, request.key)
        if (acceptedRoutes.isEmpty()) {
            return SettingEditResult(
                status = SettingEditStatus.DENIED,
                verifiedRoute = null,
                before = before,
                after = afterFailedWrite,
                attempts = attempts
            )
        }

        if (afterFailedWrite == before) {
            return SettingEditResult(
                status = SettingEditStatus.REVERTED,
                verifiedRoute = null,
                before = before,
                after = afterFailedWrite,
                attempts = attempts
            )
        }

        if (before != null) {
            for (route in acceptedRoutes.asReversed().distinct()) {
                val rollbackAccepted = gateway.write(route, request.namespace, request.key, before)
                val rollbackVerification = verifyStableValue(request, before, rollbackAccepted)
                if (rollbackAccepted && rollbackVerification.stable) {
                    return SettingEditResult(
                        status = SettingEditStatus.REVERTED,
                        verifiedRoute = null,
                        before = before,
                        after = rollbackVerification.readBack,
                        attempts = attempts
                    )
                }
            }
        }

        return SettingEditResult(
            status = SettingEditStatus.UNSUPPORTED,
            verifiedRoute = null,
            before = before,
            after = gateway.read(request.namespace, request.key),
            attempts = attempts
        )
    }

    private fun verifyStableValue(
        request: SettingEditRequest,
        expected: String,
        writeAccepted: Boolean,
    ): Verification {
        var readBack: String? = null
        var stable = writeAccepted
        for (waitMs in request.verificationDelaysMs) {
            if (waitMs > 0) delay.await(waitMs)
            readBack = gateway.read(request.namespace, request.key)
            if (readBack != expected) stable = false
        }
        return Verification(readBack, stable)
    }

    private data class Verification(val readBack: String?, val stable: Boolean)
}
