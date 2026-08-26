package io.dpcaio.app

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import io.dpcaio.activity.ComponentOverrideState
import io.dpcaio.activity.android.AndroidComponentStateGateway
import io.dpcaio.permission.android.AndroidPermissionManagerGateway
import io.dpcaio.policy.ManagedPermissionState
import org.json.JSONObject
import android.os.Process
import io.dpcaio.platform.AndroidUserId

/**
 * Shell/system-only verification bridge. The manifest protects this receiver with
 * android.permission.DUMP so ordinary third-party applications cannot invoke it.
 */
class VerificationCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val response = when (intent.action) {
            ACTION_VERIFY_PERMISSION -> verifyPermission(context, intent)
            ACTION_VERIFY_COMPONENT -> verifyComponent(context, intent)
            ACTION_VERIFY_DIAGNOSTICS -> verifyDiagnostics(context)
            else -> JSONObject().put("status", "UNSUPPORTED_ACTION")
        }
        setResultCode(if (response.optString("status") == "VERIFIED") 0 else 1)
        setResultData(response.toString())
    }

    private fun verifyDiagnostics(context: Context): JSONObject =
        JSONObject(DpcDiagnosticsSnapshot.capture(context).toJson())
            .put("status", "VERIFIED")

    private fun verifyPermission(context: Context, intent: Intent): JSONObject {
        val packageName = intent.getStringExtra("packageName") ?: TEST_TARGET_PACKAGE
        if (packageName != TEST_TARGET_PACKAGE) {
            return JSONObject().put("status", "TARGET_NOT_ALLOWED")
        }
        val permission = intent.getStringExtra("permission") ?: return JSONObject().put("status", "PERMISSION_REQUIRED")
        val desired = runCatching { ManagedPermissionState.valueOf(intent.getStringExtra("desired") ?: "") }
            .getOrElse { return JSONObject().put("status", "INVALID_DESIRED_STATE") }
        val targetUserId = intent.getIntExtra("targetUserId", AndroidUserId.fromUid(Process.myUid()))
        val admin = ComponentName(context, AioDeviceAdminReceiver::class.java)
        val result = AndroidPermissionManagerGateway(context, admin)
            .setDpcPermissionState(packageName, permission, targetUserId, desired)
        return JSONObject()
            .put("status", if (result.accepted) "VERIFIED" else result.detail)
            .put("packageName", packageName)
            .put("permission", permission)
            .put("requestedState", desired.name)
            .put("observedDpcState", result.observedDpcState?.name ?: JSONObject.NULL)
            .put("actualGranted", result.actualGranted ?: JSONObject.NULL)
    }

    private fun verifyComponent(context: Context, intent: Intent): JSONObject {
        val desired = runCatching { ComponentOverrideState.valueOf(intent.getStringExtra("desired") ?: "") }
            .getOrElse { return JSONObject().put("status", "INVALID_DESIRED_STATE") }
        val component = ComponentName(context, VerificationToggleActivity::class.java)
        val result = AndroidComponentStateGateway(context).setState(component, manifestEnabled = true, requested = desired)
        return JSONObject()
            .put("status", if (result.accepted) "VERIFIED" else result.detail)
            .put("component", component.flattenToShortString())
            .put("requestedState", desired.name)
            .put("observedState", result.observedState?.name ?: JSONObject.NULL)
            .put("effectiveEnabled", result.effectiveEnabled ?: JSONObject.NULL)
    }

    companion object {
        const val ACTION_VERIFY_PERMISSION = "io.dpcaio.action.VERIFY_PERMISSION"
        const val ACTION_VERIFY_COMPONENT = "io.dpcaio.action.VERIFY_COMPONENT"
        const val ACTION_VERIFY_DIAGNOSTICS = "io.dpcaio.action.VERIFY_DIAGNOSTICS"
        const val TEST_TARGET_PACKAGE = "io.dpcaio.testtarget"
        const val REQUIRED_CALLER_PERMISSION = "android.permission.DUMP"
    }
}
