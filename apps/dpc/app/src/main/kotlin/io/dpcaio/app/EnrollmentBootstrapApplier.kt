package io.dpcaio.app

import android.content.ComponentName
import android.content.Context
import io.dpcaio.core.model.BootstrapBaseline
import io.dpcaio.core.model.BootstrapPolicy
import io.dpcaio.policy.TriStatePolicy
import io.dpcaio.policy.android.AndroidDevicePolicyGateway
import org.json.JSONObject

class EnrollmentBootstrapApplier(context: Context) {
    private val gateway = AndroidDevicePolicyGateway(
        context,
        ComponentName(context, AioDeviceAdminReceiver::class.java),
    )

    fun parsePolicy(payload: JSONObject): BootstrapPolicy {
        val allowed = payload.optJSONArray("allowedModes")
        val capabilities = payload.optJSONArray("requiredCapabilities")
        val baselineJson = payload.optJSONObject("bootstrap") ?: JSONObject()
        return BootstrapPolicy(
            schemaVersion = payload.optInt("schemaVersion", -1),
            profileId = payload.optString("profileId"),
            allowedModes = buildSet { if (allowed != null) for (i in 0 until allowed.length()) add(allowed.getString(i)) },
            minimumAndroidApi = payload.optInt("minimumAndroidApi", 29),
            minimumDpcVersion = payload.optString("minimumDpcVersion", "0.0.0"),
            requiredCapabilities = buildSet { if (capabilities != null) for (i in 0 until capabilities.length()) add(capabilities.getString(i)) },
            baseline = BootstrapBaseline(
                autoTime = if (baselineJson.has("autoTime") && !baselineJson.isNull("autoTime")) baselineJson.getBoolean("autoTime") else null,
                networkLogging = if (baselineJson.has("networkLogging") && !baselineJson.isNull("networkLogging")) baselineJson.getBoolean("networkLogging") else null,
                securityLogging = if (baselineJson.has("securityLogging") && !baselineJson.isNull("securityLogging")) baselineJson.getBoolean("securityLogging") else null,
            ),
        )
    }

    fun applyAndVerify(policy: BootstrapPolicy): BootstrapApplyResult {
        policy.baseline.autoTime?.let { enabled ->
            val desired = if (enabled) TriStatePolicy.ENABLED else TriStatePolicy.DISABLED
            val set = gateway.setAutoTimePolicy(desired)
            if (!set.isSuccess) return BootstrapApplyResult(false, "AUTO_TIME_APPLY_FAILED")
            val readback = gateway.getAutoTimePolicy()
            if (!readback.isSuccess || readback.value != desired) return BootstrapApplyResult(false, "AUTO_TIME_READBACK_FAILED")
        }
        policy.baseline.networkLogging?.let { enabled ->
            val set = gateway.setNetworkLoggingEnabled(enabled)
            if (!set.isSuccess) return BootstrapApplyResult(false, "NETWORK_LOGGING_APPLY_FAILED")
            val readback = gateway.isNetworkLoggingEnabled()
            if (!readback.isSuccess || readback.value != enabled) return BootstrapApplyResult(false, "NETWORK_LOGGING_READBACK_FAILED")
        }
        policy.baseline.securityLogging?.let { enabled ->
            val set = gateway.setSecurityLoggingEnabled(enabled)
            if (!set.isSuccess) return BootstrapApplyResult(false, "SECURITY_LOGGING_APPLY_FAILED")
            val readback = gateway.isSecurityLoggingEnabled()
            if (!readback.isSuccess || readback.value != enabled) return BootstrapApplyResult(false, "SECURITY_LOGGING_READBACK_FAILED")
        }
        return BootstrapApplyResult(true, null)
    }
}

data class BootstrapApplyResult(val verified: Boolean, val errorCode: String?)
