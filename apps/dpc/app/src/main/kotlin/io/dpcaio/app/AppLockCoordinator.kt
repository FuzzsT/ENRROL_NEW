package io.dpcaio.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import io.dpcaio.appmanager.AppPolicyCoordinator
import io.dpcaio.appmanager.AppPolicyVerification
import io.dpcaio.appmanager.PinGuardResult
import io.dpcaio.appmanager.android.PinGuardVerifier
import io.dpcaio.policy.android.AndroidDevicePolicyGateway

enum class AppLockResult {
    SUCCESS,
    PIN_REQUIRED,
    INVALID_PIN,
    DEVICE_OWNER_REQUIRED,
    POLICY_REJECTED,
}

class AppLockCoordinator(
    context: Context,
    private val store: AppLockStore = AppLockStore(context),
    private val pinGuard: PinGuardVerifier = PinGuardVerifier(context),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val app = context.applicationContext
    private val policy = AppPolicyCoordinator(
        AndroidDevicePolicyGateway(app, AioDeviceAdminReceiver.componentName(app))
    )
    private val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun lock(packageName: String): AppLockResult {
        val outcome = policy.setSuspended(packageName, true)
        if (outcome.verification != AppPolicyVerification.VERIFIED) {
            return if (outcome.mutationStatus.name == "NOT_AUTHORIZED") AppLockResult.DEVICE_OWNER_REQUIRED else AppLockResult.POLICY_REJECTED
        }
        cancelRelock(packageName)
        return if (store.write(AppLockRecord(packageName, locked = true, unlockedUntilMillis = 0L))) {
            AppLockResult.SUCCESS
        } else {
            AppLockResult.POLICY_REJECTED
        }
    }

    fun unlock(packageName: String, pin: CharArray, durationMillis: Long? = null): AppLockResult {
        if (!pinGuard.isConfigured()) {
            pin.fill('\u0000')
            return AppLockResult.PIN_REQUIRED
        }
        if (pinGuard.verify(pin) != PinGuardResult.SUCCESS) return AppLockResult.INVALID_PIN

        val outcome = policy.setSuspended(packageName, false)
        if (outcome.verification != AppPolicyVerification.VERIFIED) {
            return if (outcome.mutationStatus.name == "NOT_AUTHORIZED") AppLockResult.DEVICE_OWNER_REQUIRED else AppLockResult.POLICY_REJECTED
        }

        if (durationMillis == null) {
            cancelRelock(packageName)
            return if (store.remove(packageName)) AppLockResult.SUCCESS else AppLockResult.POLICY_REJECTED
        }

        val deadline = clock() + durationMillis.coerceAtLeast(1L)
        val saved = store.write(AppLockRecord(packageName, locked = true, unlockedUntilMillis = deadline))
        if (!saved) return AppLockResult.POLICY_REJECTED
        scheduleRelock(packageName, deadline)
        return AppLockResult.SUCCESS
    }

    fun reconcileExpiredUnlocks(nowMillis: Long = clock()): Int {
        var relocked = 0
        store.all()
            .filter { it.locked && it.unlockedUntilMillis > 0L && it.unlockedUntilMillis <= nowMillis }
            .forEach { record ->
                val outcome = policy.setSuspended(record.packageName, true)
                if (outcome.verification == AppPolicyVerification.VERIFIED) {
                    store.write(record.copy(unlockedUntilMillis = 0L))
                    cancelRelock(record.packageName)
                    relocked++
                }
            }
        return relocked
    }

    fun state(packageName: String): AppLockRecord? = store.read(packageName)

    private fun scheduleRelock(packageName: String, deadline: Long) {
        val pending = relockIntent(packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadline, pending)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, deadline, pending)
        }
    }

    private fun cancelRelock(packageName: String) {
        alarmManager.cancel(relockIntent(packageName))
    }

    private fun relockIntent(packageName: String): PendingIntent = PendingIntent.getBroadcast(
        app,
        packageName.hashCode(),
        Intent(app, AppLockAlarmReceiver::class.java).putExtra(AppLockAlarmReceiver.EXTRA_TARGET_PACKAGE, packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
