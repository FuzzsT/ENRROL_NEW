package io.dpcaio.appmanager.android

import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import io.dpcaio.appmanager.WholeAppStatePlan
import io.dpcaio.appmanager.WholeAppStatePlanner
import io.dpcaio.appmanager.WholeAppStateRequest
import io.dpcaio.appmanager.WholeAppStateRoute
import io.dpcaio.platform.AndroidUserId
import io.dpcaio.shizuku.ShizukuUserServiceClient

data class WholeAppStateResult(
    val plan: WholeAppStatePlan,
    val submitted: Boolean,
    val preState: Boolean?,
    val readback: Boolean?,
    val verified: Boolean,
    val detail: String,
) {
    val protectionDecision get() = plan.protectionDecision
}

class AndroidWholeAppStateGateway(context: Context) {
    private val appContext = context.applicationContext
    private val pm = appContext.packageManager
    private val planner = WholeAppStatePlanner()
    private val shizuku by lazy { ShizukuUserServiceClient(appContext).also { it.bind() } }

    fun setEnabled(request: WholeAppStateRequest): WholeAppStateResult {
        val plan = planner.plan(request)
        if (!plan.allowed) return WholeAppStateResult(plan, false, readbackCurrentUser(request), readbackCurrentUser(request), false, plan.detail)
        val pre = readbackCurrentUser(request)
        val submitted = when (plan.route) {
            WholeAppStateRoute.OWN_UID, WholeAppStateRoute.SYSTEM_PRIVILEGED -> runCatching {
                pm.setApplicationEnabledSetting(
                    request.packageName,
                    if (request.enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                    PackageManager.DONT_KILL_APP,
                )
                true
            }.getOrDefault(false)
            WholeAppStateRoute.SHIZUKU -> shizuku.setPackageEnabled(request.packageName, request.enabled, request.targetUserId) == 0
            WholeAppStateRoute.UNAVAILABLE -> false
        }
        val readback = if (submitted) readbackCurrentUser(request) else pre
        val verified = submitted && readback == request.enabled
        return WholeAppStateResult(
            plan = plan,
            submitted = submitted,
            preState = pre,
            readback = readback,
            verified = verified,
            detail = when {
                verified -> "READBACK_VERIFIED"
                submitted && readback == null -> "READBACK_UNAVAILABLE"
                submitted -> "READBACK_MISMATCH"
                else -> "APPLY_FAILED"
            },
        )
    }

    private fun readbackCurrentUser(request: WholeAppStateRequest): Boolean? {
        if (request.targetUserId != AndroidUserId.fromUid(Process.myUid())) return null
        return runCatching {
            val info = pm.getApplicationInfo(request.packageName, 0)
            val override = pm.getApplicationEnabledSetting(request.packageName)
            when (override) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
                else -> info.enabled
            }
        }.getOrNull()
    }
}
