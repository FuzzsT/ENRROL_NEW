package io.dpcaio.app

import android.content.ComponentName
import android.content.Context
import android.os.Process
import android.os.UserHandle
import io.dpcaio.activity.ComponentControlRequest
import io.dpcaio.activity.ComponentControlRoute
import io.dpcaio.activity.ComponentControlRouter
import io.dpcaio.activity.ComponentOverrideState
import io.dpcaio.activity.android.AndroidActivityInventory
import io.dpcaio.activity.android.AndroidComponentStateGateway
import io.dpcaio.offline.OfflineComponentDesiredState
import io.dpcaio.offline.OfflinePermissionDesiredState
import io.dpcaio.offline.OfflineStage
import io.dpcaio.offline.android.AndroidOfflineBundleReader
import io.dpcaio.offline.android.AndroidOfflinePolicyReader
import io.dpcaio.permission.PermissionControlRoute
import io.dpcaio.permission.PermissionProtection
import io.dpcaio.permission.android.AndroidPermissionCatalog
import io.dpcaio.permission.android.AndroidPermissionManagerGateway
import io.dpcaio.permission.android.GlobalRuntimePermissionPolicy
import io.dpcaio.platform.AndroidUserId
import io.dpcaio.policy.ManagedPermissionState
import io.dpcaio.shizuku.AndroidShizukuRuntime
import io.dpcaio.shizuku.ShizukuComponentStateExecutor
import io.dpcaio.shizuku.ShizukuUserServiceClient
import java.io.File

data class OfflinePolicyApplyItem(
    val category: String,
    val key: String,
    val required: Boolean,
    val verified: Boolean,
    val detail: String
)

data class OfflinePolicyApplyResult(
    val verified: Boolean,
    val items: List<OfflinePolicyApplyItem>,
    val finalStage: OfflineStage
)

class OfflinePolicyApplier(private val context: Context) {
    private val appContext = context.applicationContext
    private val admin = ComponentName(appContext, AioDeviceAdminReceiver::class.java)
    private val permissionGateway = AndroidPermissionManagerGateway(appContext, admin)
    private val componentGateway = AndroidComponentStateGateway(appContext)
    private val componentRouter = ComponentControlRouter()
    private val shizuku = ShizukuUserServiceClient(appContext).also { it.bind() }
    private val store = OfflineDeploymentStore(appContext)

    fun apply(bundleFile: File): OfflinePolicyApplyResult {
        val inspected = AndroidOfflineBundleReader(appContext).inspect(bundleFile, BuildConfig.OFFLINE_SIGNING_PUBLIC_KEY)
        if (!inspected.ready || inspected.manifest == null) {
            store.load()?.let { store.save(it.copy(stage = OfflineStage.FAILED, lastError = "OFFLINE_BUNDLE_INVALID:${inspected.detail}")) }
            return OfflinePolicyApplyResult(false, listOf(OfflinePolicyApplyItem("bundle", bundleFile.name, true, false, inspected.detail)), OfflineStage.FAILED)
        }
        val spec = AndroidOfflinePolicyReader().read(bundleFile, inspected.manifest.policyPath)
        val items = mutableListOf<OfflinePolicyApplyItem>()
        val currentUser = AndroidUserId.fromUid(Process.myUid())
        val shizukuState = AndroidShizukuRuntime().probe()
        val shizukuAvailable = shizukuState.binderAlive && shizukuState.permissionGranted

        spec.defaultPermissionPolicy?.let { policy ->
            val mapped = when (policy.name) {
                "AUTO_GRANT" -> GlobalRuntimePermissionPolicy.AUTO_GRANT
                "AUTO_DENY" -> GlobalRuntimePermissionPolicy.AUTO_DENY
                else -> GlobalRuntimePermissionPolicy.PROMPT
            }
            val ok = permissionGateway.setGlobalPermissionPolicy(mapped) && runCatching { permissionGateway.getGlobalPermissionPolicy() == mapped }.getOrDefault(false)
            items += OfflinePolicyApplyItem("permission-policy", "defaultPermissionPolicy", true, ok, if (ok) "VERIFIED" else "POLICY_READBACK_MISMATCH")
        }

        val catalog = AndroidPermissionCatalog(appContext).scan().permissions.associateBy { it.name }
        for (rule in spec.permissions) {
            val targetUserId = rule.targetUserId ?: currentUser
            val desired = when (rule.state) {
                OfflinePermissionDesiredState.DEFAULT -> ManagedPermissionState.DEFAULT
                OfflinePermissionDesiredState.GRANTED -> ManagedPermissionState.GRANTED
                OfflinePermissionDesiredState.DENIED -> ManagedPermissionState.DENIED
            }
            val entry = catalog[rule.permission]
            val decision = permissionGateway.resolveDecision(
                rule.permission,
                targetUserId,
                desired,
                entry?.protection ?: PermissionProtection.UNKNOWN,
                sensorGrantOptOut = false,
                shizukuAvailable = shizukuAvailable,
                systemPrivilegedAvailable = false,
                userActionAvailable = false,
                packageName = rule.packageName,
                automated = true,
            )
            val applyDetail: String
            val submitted = when (decision.route) {
                PermissionControlRoute.DPC, PermissionControlRoute.DELEGATED_DPC -> {
                    val result = permissionGateway.setDpcPermissionState(rule.packageName, rule.permission, targetUserId, desired)
                    applyDetail = result.detail
                    result.accepted
                }
                PermissionControlRoute.SHIZUKU -> {
                    val exit = when (desired) {
                        ManagedPermissionState.GRANTED -> shizuku.grantRuntimePermission(rule.packageName, rule.permission, targetUserId)
                        ManagedPermissionState.DENIED -> shizuku.revokeRuntimePermission(rule.packageName, rule.permission, targetUserId)
                        ManagedPermissionState.DEFAULT -> null
                    }
                    applyDetail = if (exit == 0) "SHIZUKU_SUBMITTED" else "SHIZUKU_EXIT:$exit"
                    exit == 0
                }
                else -> {
                    applyDetail = decision.reason
                    false
                }
            }
            val readback = if (targetUserId == currentUser) runCatching {
                permissionGateway.inspectPermission(rule.packageName, rule.permission, targetUserId, entry?.group, entry?.protection ?: PermissionProtection.UNKNOWN, shizukuAvailable = shizukuAvailable)
            }.getOrNull() else null
            val verified = when {
                !submitted -> false
                decision.route in setOf(PermissionControlRoute.DPC, PermissionControlRoute.DELEGATED_DPC) -> readback?.dpcState == desired
                targetUserId != currentUser -> false
                desired == ManagedPermissionState.GRANTED -> readback?.actualGranted == true
                desired == ManagedPermissionState.DENIED -> readback?.actualGranted == false
                else -> false
            }
            items += OfflinePolicyApplyItem("permission", "${rule.packageName}:${rule.permission}", rule.required, verified, if (verified) "VERIFIED" else "$applyDetail; POLICY_READBACK_MISMATCH")
        }
        store.load()?.let { store.save(it.copy(stage = OfflineStage.PERMISSIONS_APPLIED)) }

        for (rule in spec.components) {
            val targetUserId = rule.targetUserId ?: currentUser
            val desired = when (rule.state) {
                OfflineComponentDesiredState.DEFAULT -> ComponentOverrideState.DEFAULT
                OfflineComponentDesiredState.ENABLED -> ComponentOverrideState.ENABLED
                OfflineComponentDesiredState.DISABLED -> ComponentOverrideState.DISABLED
            }
            val activity = runCatching { AndroidActivityInventory(appContext).list(rule.packageName, UserHandle.of(targetUserId)) }
                .getOrElse { emptyList() }
                .firstOrNull { it.className == rule.normalizedClassName }
            if (activity == null) {
                items += OfflinePolicyApplyItem("component", "${rule.packageName}:${rule.normalizedClassName}", rule.required, false, "COMPONENT_NOT_FOUND")
                continue
            }
            val decision = componentRouter.resolve(
                ComponentControlRequest(
                    activity.packageName,
                    activity.className,
                    targetUserId,
                    activity.sameUid && targetUserId == currentUser,
                    shizukuAvailable,
                    false,
                    criticalSystemComponent = activity.packageName in CRITICAL_SYSTEM_PACKAGES,
                    developerLab = false,
                    desiredState = desired,
                    automated = true,
                )
            )
            val submitted = when (decision.route) {
                ComponentControlRoute.OWN_UID -> componentGateway.setState(ComponentName(activity.packageName, activity.className), activity.manifestEnabled, desired).accepted
                ComponentControlRoute.SHIZUKU -> ShizukuComponentStateExecutor(shizuku).setComponentEnabledState(activity.packageName, activity.className, targetUserId, desired).submitted
                else -> false
            }
            val observed = if (submitted && targetUserId == currentUser) runCatching {
                componentGateway.readback(ComponentName(activity.packageName, activity.className), activity.manifestEnabled).overrideState
            }.getOrNull() else null
            val verified = submitted && observed == desired
            items += OfflinePolicyApplyItem("component", "${rule.packageName}:${rule.normalizedClassName}", rule.required, verified, if (verified) "VERIFIED" else "${decision.detail}; COMPONENT_STATE_MISMATCH")
        }
        store.load()?.let { store.save(it.copy(stage = OfflineStage.COMPONENTS_APPLIED)) }
        store.load()?.let { store.save(it.copy(stage = OfflineStage.POLICIES_APPLIED)) }

        val requiredFailed = items.any { it.required && !it.verified }
        val state = store.load()
        val finalStage = if (requiredFailed) OfflineStage.FAILED else if (state?.syncPending == true) OfflineStage.SYNC_PENDING else OfflineStage.OFFLINE_VERIFIED
        if (!requiredFailed) store.load()?.let { store.save(it.copy(stage = OfflineStage.READBACK_VERIFIED)) }
        store.load()?.let { store.save(it.copy(stage = finalStage, lastError = if (requiredFailed) "OFFLINE_POLICY_PARTIAL" else null)) }
        return OfflinePolicyApplyResult(!requiredFailed, items, finalStage)
    }

    companion object {
        private val CRITICAL_SYSTEM_PACKAGES = setOf("com.android.settings", "com.android.systemui", "com.android.packageinstaller", "com.google.android.packageinstaller")
    }
}
