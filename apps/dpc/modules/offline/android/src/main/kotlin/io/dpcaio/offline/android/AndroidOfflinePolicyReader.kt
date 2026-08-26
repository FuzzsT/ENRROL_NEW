package io.dpcaio.offline.android

import io.dpcaio.offline.OfflineComponentDesiredState
import io.dpcaio.offline.OfflineComponentRule
import io.dpcaio.offline.OfflineDefaultPermissionPolicy
import io.dpcaio.offline.OfflinePermissionDesiredState
import io.dpcaio.offline.OfflinePermissionRule
import io.dpcaio.offline.OfflinePolicySpec
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

class AndroidOfflinePolicyReader {
    fun read(bundleFile: File, policyPath: String?): OfflinePolicySpec {
        if (policyPath.isNullOrBlank()) return OfflinePolicySpec()
        ZipFile(bundleFile).use { zip ->
            val entry = zip.getEntry(policyPath) ?: error("Offline policy missing: $policyPath")
            val json = JSONObject(zip.getInputStream(entry).use { it.readBytes() }.toString(Charsets.UTF_8))
            return parse(json)
        }
    }

    private fun parse(json: JSONObject): OfflinePolicySpec {
        val permissionsRoot = json.optJSONObject("permissions")
        val defaultPermissionPolicy = permissionsRoot?.optString("defaultPolicy", "")
            ?.takeIf { it.isNotBlank() }
            ?.let { OfflineDefaultPermissionPolicy.valueOf(it) }
        val permissionRules = mutableListOf<OfflinePermissionRule>()
        val permissionPackages = permissionsRoot?.optJSONObject("packages")
        permissionPackages?.keys()?.forEach { packageName ->
            val packageRules = permissionPackages.getJSONObject(packageName)
            packageRules.keys().forEach { permission ->
                val value = packageRules.get(permission)
                val state: OfflinePermissionDesiredState
                val targetUserId: Int?
                val required: Boolean
                if (value is JSONObject) {
                    state = OfflinePermissionDesiredState.valueOf(value.getString("state"))
                    targetUserId = if (value.has("targetUserId")) value.getInt("targetUserId") else null
                    required = value.optBoolean("required", true)
                } else {
                    state = OfflinePermissionDesiredState.valueOf(value.toString())
                    targetUserId = null
                    required = true
                }
                permissionRules += OfflinePermissionRule(packageName, permission, state, targetUserId, required)
            }
        }

        val componentRules = mutableListOf<OfflineComponentRule>()
        val componentsRoot = json.optJSONObject("components")
        componentsRoot?.keys()?.forEach { packageName ->
            val packageRules = componentsRoot.getJSONObject(packageName)
            packageRules.keys().forEach { className ->
                val value = packageRules.get(className)
                val state: OfflineComponentDesiredState
                val targetUserId: Int?
                val required: Boolean
                if (value is JSONObject) {
                    state = OfflineComponentDesiredState.valueOf(value.getString("state"))
                    targetUserId = if (value.has("targetUserId")) value.getInt("targetUserId") else null
                    required = value.optBoolean("required", true)
                } else {
                    state = OfflineComponentDesiredState.valueOf(value.toString())
                    targetUserId = null
                    required = true
                }
                componentRules += OfflineComponentRule(packageName, className, state, targetUserId, required)
            }
        }
        return OfflinePolicySpec(defaultPermissionPolicy, permissionRules, componentRules)
    }
}
