package io.dpcaio.knoxzt.android

import android.content.ComponentName
import android.content.Context
import android.os.UserHandle
import io.dpcaio.activity.DiscoveredActivity
import io.dpcaio.activity.android.AndroidActivityInventory
import io.dpcaio.knoxzt.KNOXZT_PACKAGE
import io.dpcaio.shizuku.ShizukuUserServiceClient

class KnoxZtActivitySupport(
    context: Context,
    admin: ComponentName
) {
    private val appContext = context.applicationContext
    private val recovery = KnoxZtRecoveryManager(appContext, admin, shizuku = ShizukuUserServiceClient(appContext))

    fun prepareAndList(user: UserHandle): Pair<KnoxZtRecoveryResult, List<DiscoveredActivity>> {
        val result = recovery.ensureReady()
        val activities = if (result.status in setOf(KnoxZtRuntimeStatus.READY, KnoxZtRuntimeStatus.ENABLED, KnoxZtRuntimeStatus.RESTORED_EXISTING)) {
            runCatching { AndroidActivityInventory(appContext).list(KNOXZT_PACKAGE, user) }.getOrElse { emptyList() }
        } else emptyList()
        return result to activities
    }
}
