package io.dpcaio.permission.android

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import io.dpcaio.permission.AppOpState
import io.dpcaio.permission.PermissionInspection
import io.dpcaio.permission.RawPermissionState
import io.dpcaio.permission.VerifiedRoute

class AndroidPermissionInspector(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager
    private val appOps = appContext.getSystemService(AppOpsManager::class.java)

    fun inspect(
        packageName: String,
        permission: String,
        uid: Int,
        appOp: String?,
        dpcManageable: Boolean,
        userActionAvailable: Boolean,
        verifiedAlternative: VerifiedRoute? = null
    ): PermissionInspection {
        val raw = if (packageManager.checkPermission(permission, packageName) == PackageManager.PERMISSION_GRANTED) {
            RawPermissionState.GRANTED
        } else {
            RawPermissionState.DENIED
        }
        val appOpState = appOp?.let { mapAppOp(appOps.checkOpNoThrow(it, uid, packageName)) }
        return PermissionInspection(
            rawPermission = raw,
            appOpState = appOpState,
            dpcManageable = dpcManageable,
            userActionAvailable = userActionAvailable,
            verifiedAlternative = verifiedAlternative
        )
    }

    private fun mapAppOp(mode: Int): AppOpState = when (mode) {
        AppOpsManager.MODE_ALLOWED -> AppOpState.ALLOWED
        AppOpsManager.MODE_IGNORED -> AppOpState.IGNORED
        AppOpsManager.MODE_ERRORED -> AppOpState.ERRORED
        AppOpsManager.MODE_DEFAULT -> AppOpState.DEFAULT
        AppOpsManager.MODE_FOREGROUND -> AppOpState.FOREGROUND
        else -> AppOpState.UNKNOWN
    }
}
