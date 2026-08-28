package io.dpcaio.policy.android.parity

import android.app.admin.DevicePolicyManager
import android.app.admin.WifiSsidPolicy
import android.content.ComponentName
import android.content.Context
import android.net.ProxyInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiSsid
import android.os.Build
import io.dpcaio.policy.PolicyResult
import io.dpcaio.policy.PolicyStatus

class AndroidNetworkParityGateway(
    context: Context,
    private val admin: ComponentName,
) {
    private val appContext = context.applicationContext
    private val dpm = appContext.getSystemService(DevicePolicyManager::class.java)
    private val wifi = appContext.getSystemService(WifiManager::class.java)

    fun setRecommendedGlobalProxy(
        host: String,
        port: Int,
        exclusionList: List<String>,
    ): PolicyResult<Unit> = policyCall {
        require(host.isNotBlank()) { "Proxy host is required" }
        require(port in 1..65535) { "Proxy port must be in 1..65535" }
        val proxy = ProxyInfo.buildDirectProxy(host, port, exclusionList)
        dpm.setRecommendedGlobalProxy(admin, proxy)
        PolicyResult.success(message = "Recommended global proxy configured")
    }

    fun clearRecommendedGlobalProxy(): PolicyResult<Unit> = policyCall {
        dpm.setRecommendedGlobalProxy(admin, null)
        PolicyResult.success(message = "Recommended global proxy cleared")
    }

    fun setPreferentialNetworkServiceEnabled(enabled: Boolean): PolicyResult<Boolean> {
        if (Build.VERSION.SDK_INT < 31) return unsupported("Preferential network service requires API 31+")
        return policyCall {
            dpm.setPreferentialNetworkServiceEnabled(enabled)
            val observed = dpm.isPreferentialNetworkServiceEnabled
            if (observed == enabled) {
                PolicyResult.success(observed, "READBACK_VERIFIED")
            } else {
                PolicyResult.failure(
                    PolicyStatus.PLATFORM_REJECTED,
                    "Preferential-network readback mismatch: requested=$enabled observed=$observed",
                )
            }
        }
    }

    fun setConfiguredNetworksLockdownState(lockdown: Boolean): PolicyResult<Boolean> {
        if (Build.VERSION.SDK_INT < 30) return unsupported("Wi-Fi configured-network lockdown requires API 30+")
        return policyCall {
            dpm.setConfiguredNetworksLockdownState(admin, lockdown)
            val observed = dpm.hasLockdownAdminConfiguredNetworks(admin)
            if (observed == lockdown) {
                PolicyResult.success(observed, "READBACK_VERIFIED")
            } else {
                PolicyResult.failure(
                    PolicyStatus.PLATFORM_REJECTED,
                    "Wi-Fi lockdown readback mismatch: requested=$lockdown observed=$observed",
                )
            }
        }
    }

    fun removeNonCallerConfiguredNetworks(): PolicyResult<Boolean> {
        if (Build.VERSION.SDK_INT < 31) return unsupported("Removing non-caller Wi-Fi networks requires API 31+")
        return policyCall {
            PolicyResult.success(
                wifi.removeNonCallerConfiguredNetworks(),
                "Wi-Fi non-caller network removal request completed",
            )
        }
    }

    fun getWifiMacAddress(): PolicyResult<String> {
        if (Build.VERSION.SDK_INT < 24) return unsupported("Wi-Fi MAC inventory requires API 24+")
        return policyCall {
            val mac = dpm.getWifiMacAddress(admin)
                ?: return@policyCall PolicyResult.failure(
                    PolicyStatus.FAILED,
                    "Wi-Fi MAC address is unavailable",
                )
            PolicyResult.success(mac)
        }
    }

    fun parseWifiSecurityLevel(raw: String): Int? = when (raw.trim().uppercase()) {
        "OPEN", DevicePolicyManager.WIFI_SECURITY_OPEN.toString() -> DevicePolicyManager.WIFI_SECURITY_OPEN
        "PERSONAL", DevicePolicyManager.WIFI_SECURITY_PERSONAL.toString() -> DevicePolicyManager.WIFI_SECURITY_PERSONAL
        "ENTERPRISE_EAP", DevicePolicyManager.WIFI_SECURITY_ENTERPRISE_EAP.toString() -> DevicePolicyManager.WIFI_SECURITY_ENTERPRISE_EAP
        "ENTERPRISE_192", DevicePolicyManager.WIFI_SECURITY_ENTERPRISE_192.toString() -> DevicePolicyManager.WIFI_SECURITY_ENTERPRISE_192
        else -> null
    }

    fun parseWifiSsidPolicyType(raw: String): Int? = when (raw.trim().uppercase()) {
        "ALLOWLIST", WifiSsidPolicy.WIFI_SSID_POLICY_TYPE_ALLOWLIST.toString() -> WifiSsidPolicy.WIFI_SSID_POLICY_TYPE_ALLOWLIST
        "DENYLIST", WifiSsidPolicy.WIFI_SSID_POLICY_TYPE_DENYLIST.toString() -> WifiSsidPolicy.WIFI_SSID_POLICY_TYPE_DENYLIST
        else -> null
    }
    fun setMinimumRequiredWifiSecurityLevel(level: Int): PolicyResult<Int> {
        if (Build.VERSION.SDK_INT < 33) return unsupported("Wi-Fi minimum security policy requires API 33+")
        return policyCall {
            val allowed = setOf(
                DevicePolicyManager.WIFI_SECURITY_OPEN,
                DevicePolicyManager.WIFI_SECURITY_PERSONAL,
                DevicePolicyManager.WIFI_SECURITY_ENTERPRISE_EAP,
                DevicePolicyManager.WIFI_SECURITY_ENTERPRISE_192,
            )
            require(level in allowed) { "Unsupported Wi-Fi minimum security level: $level" }
            dpm.setMinimumRequiredWifiSecurityLevel(level)
            val observed = dpm.minimumRequiredWifiSecurityLevel
            if (observed == level) {
                PolicyResult.success(observed, "READBACK_VERIFIED")
            } else {
                PolicyResult.failure(
                    PolicyStatus.PLATFORM_REJECTED,
                    "Wi-Fi security-level readback mismatch: requested=$level observed=$observed",
                )
            }
        }
    }

    fun setWifiSsidPolicy(policyType: Int, ssids: Set<String>): PolicyResult<String> {
        if (Build.VERSION.SDK_INT < 33) return unsupported("Wi-Fi SSID policy requires API 33+")
        return policyCall {
            require(policyType == WifiSsidPolicy.WIFI_SSID_POLICY_TYPE_ALLOWLIST ||
                policyType == WifiSsidPolicy.WIFI_SSID_POLICY_TYPE_DENYLIST) {
                "Unsupported Wi-Fi SSID policy type: $policyType"
            }
            require(ssids.isNotEmpty()) { "At least one SSID is required" }
            val wifiSsids = ssids.map { value ->
                require(value.isNotBlank()) { "SSID cannot be blank" }
                WifiSsid.fromBytes(value.toByteArray(Charsets.UTF_8))
            }.toSet()
            dpm.setWifiSsidPolicy(WifiSsidPolicy(policyType, wifiSsids))
            val observed = dpm.wifiSsidPolicy
                ?: return@policyCall PolicyResult.failure(
                    PolicyStatus.PLATFORM_REJECTED,
                    "Wi-Fi SSID policy readback is empty",
                )
            PolicyResult.success(
                "type=${observed.policyType},ssids=${observed.ssids.size}",
                "READBACK_VERIFIED",
            )
        }
    }

    private inline fun <T> policyCall(block: () -> PolicyResult<T>): PolicyResult<T> = try {
        block()
    } catch (error: SecurityException) {
        PolicyResult.failure(
            PolicyStatus.SECURITY_EXCEPTION,
            error.message ?: "SECURITY_EXCEPTION",
            error.javaClass.name,
        )
    } catch (error: IllegalArgumentException) {
        PolicyResult.failure(
            PolicyStatus.FAILED,
            error.message ?: "INVALID_ARGUMENT",
            error.javaClass.name,
        )
    } catch (error: UnsupportedOperationException) {
        PolicyResult.failure(
            PolicyStatus.UNSUPPORTED,
            error.message ?: "UNSUPPORTED",
            error.javaClass.name,
        )
    } catch (error: RuntimeException) {
        PolicyResult.failure(
            PolicyStatus.FAILED,
            error.message ?: error.javaClass.simpleName,
            error.javaClass.name,
        )
    }

    private fun <T> unsupported(message: String): PolicyResult<T> =
        PolicyResult.failure(PolicyStatus.UNSUPPORTED, message)
}
