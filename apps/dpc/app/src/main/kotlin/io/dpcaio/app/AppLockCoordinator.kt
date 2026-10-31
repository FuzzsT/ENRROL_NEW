package io.dpcaio.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import io.dpcaio.appmanager.AppPolicyCoordinator
import io.dpcaio.appmanager.AppPolicyVerification
import io.dpcaio.appmanager.PinGuardResult
import io.dpcaio.appmanager.android.PinGuardVerifier
import io.dpcaio.policy.android.AndroidDevicePolicyGateway

enum class AppLockResult {
    SUCCESS,
    PIN_REQUIRED,
    INVALID_PIN,
    NOT_MANAGED,
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

    fun lock(packageName: String): AppLockResult = synchronized(transitionLock) {
        val outcome = policy.setSuspended(packageName, true)
        if (outcome.verification != AppPolicyVerification.VERIFIED) {
            return@synchronized if (outcome.mutationStatus.name == "NOT_AUTHORIZED") {
                AppLockResult.DEVICE_OWNER_REQUIRED
            } else {
                AppLockResult.POLICY_REJECTED
            }
        }
        cancelRelock(packageName)
        if (store.write(AppLockRecord(packageName, locked = true, unlockedUntilMillis = 0L, pendingUnlock = false))) {
            AppLockResult.SUCCESS
        } else {
            AppLockResult.POLICY_REJECTED
        }
    }

    fun unlock(packageName: String, pin: CharArray, durationMillis: Long? = null): AppLockResult = synchronized(transitionLock) {
        val record = store.read(packageName)
        if (record?.locked != true) {
            pin.fill('\u0000')
            return@synchronized AppLockResult.NOT_MANAGED
        }
        if (!pinGuard.isConfigured()) {
            pin.fill('\u0000')
            return@synchronized AppLockResult.PIN_REQUIRED
        }
        if (pinGuard.verify(pin) != PinGuardResult.SUCCESS) return@synchronized AppLockResult.INVALID_PIN

        val deadline = durationMillis?.let { clock() + it.coerceAtLeast(1L) } ?: 0L
        val pendingRecord = record.copy(
            locked = true,
            unlockedUntilMillis = deadline,
            pendingUnlock = true,
        )
        if (!store.write(pendingRecord)) {
            return@synchronized AppLockResult.POLICY_REJECTED
        }

        val outcome = policy.setSuspended(packageName, false)
        if (outcome.verification != AppPolicyVerification.VERIFIED) {
            val rollbackVerified = rollbackSuspension(packageName)
            if (rollbackVerified) {
                store.write(record.copy(unlockedUntilMillis = 0L, pendingUnlock = false))
            } else {
                keepPendingRecovery(pendingRecord)
            }
            return@synchronized if (outcome.mutationStatus.name == "NOT_AUTHORIZED") {
                AppLockResult.DEVICE_OWNER_REQUIRED
            } else {
                AppLockResult.POLICY_REJECTED
            }
        }

        if (durationMillis == null) {
            if (store.remove(packageName)) {
                cancelRelock(packageName)
                return@synchronized AppLockResult.SUCCESS
            }
            val rollbackVerified = rollbackSuspension(packageName)
            if (rollbackVerified) {
                store.write(record.copy(unlockedUntilMillis = 0L, pendingUnlock = false))
            } else {
                keepPendingRecovery(pendingRecord)
            }
            return@synchronized AppLockResult.POLICY_REJECTED
        }

        val scheduled = runCatching { scheduleRelock(packageName, deadline) }.isSuccess
        if (!scheduled) {
            val rollbackVerified = rollbackSuspension(packageName)
            if (rollbackVerified) {
                store.write(record.copy(unlockedUntilMillis = 0L, pendingUnlock = false))
            } else {
                keepPendingRecovery(pendingRecord)
            }
            return@synchronized AppLockResult.POLICY_REJECTED
        }

        if (!store.write(pendingRecord.copy(pendingUnlock = false))) {
            val rollbackVerified = rollbackSuspension(packageName)
            if (rollbackVerified) {
                store.write(record.copy(unlockedUntilMillis = 0L, pendingUnlock = false))
            } else {
                keepPendingRecovery(pendingRecord)
            }
            return@synchronized AppLockResult.POLICY_REJECTED
        }
        AppLockResult.SUCCESS
    }

    fun reconcileExpiredUnlocks(nowMillis: Long = clock()): Int = synchronized(transitionLock) {
        var relocked = 0
        store.all()
            .filter {
                it.locked && (
                    it.pendingUnlock ||
                        (it.unlockedUntilMillis > 0L && it.unlockedUntilMillis <= nowMillis)
                    )
            }
            .forEach { record ->
                val outcome = policy.setSuspended(record.packageName, true)
                if (outcome.verification == AppPolicyVerification.VERIFIED) {
                    store.write(record.copy(unlockedUntilMillis = 0L, pendingUnlock = false))
                    cancelRelock(record.packageName)
                    relocked++
                } else if (record.pendingUnlock) {
                    keepPendingRecovery(record)
                }
            }
        relocked
    }

    fun restorePendingRelocks(nowMillis: Long = clock()): Int = synchronized(transitionLock) {
        var restored = 0
        store.all()
            .filter { it.locked && (it.pendingUnlock || it.unlockedUntilMillis > 0L) }
            .forEach { record ->
                if (record.pendingUnlock || record.unlockedUntilMillis <= nowMillis) {
                    val outcome = policy.setSuspended(record.packageName, true)
                    if (outcome.verification == AppPolicyVerification.VERIFIED) {
                        store.write(record.copy(unlockedUntilMillis = 0L, pendingUnlock = false))
                        cancelRelock(record.packageName)
                        restored++
                    } else {
                        keepPendingRecovery(record.copy(pendingUnlock = true))
                    }
                } else {
                    val scheduled = runCatching {
                        scheduleRelock(record.packageName, record.unlockedUntilMillis)
                    }.isSuccess
                    if (scheduled) {
                        restored++
                    } else {
                        val rollbackVerified = rollbackSuspension(record.packageName)
                        if (rollbackVerified) {
                            store.write(record.copy(unlockedUntilMillis = 0L, pendingUnlock = false))
                        } else {
                            keepPendingRecovery(record.copy(pendingUnlock = true))
                        }
                    }
                }
            }
        restored
    }

    fun state(packageName: String): AppLockRecord? = synchronized(transitionLock) {
        store.read(packageName)
    }

    private fun rollbackSuspension(packageName: String): Boolean {
        val outcome = policy.setSuspended(packageName, true)
        val rollbackVerified = outcome.verification == AppPolicyVerification.VERIFIED
        if (rollbackVerified) {
            cancelRelock(packageName)
        }
        return rollbackVerified
    }

    private fun keepPendingRecovery(record: AppLockRecord) {
        store.write(record.copy(locked = true, pendingUnlock = true))
        scheduleRecoveryRetry(record.packageName)
    }

    private fun scheduleRecoveryRetry(packageName: String) {
        val pending = relockIntent(packageName)
        val wallClockDeadline = clock() + RECOVERY_RETRY_MS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + RECOVERY_RETRY_MS,
                pending,
            )
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wallClockDeadline, pending)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, wallClockDeadline, pending)
        }
    }

    private fun scheduleRelock(packageName: String, deadline: Long) {
        val pending = relockIntent(packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            throw SecurityException("Exact alarms are not available for bounded App Lock relock")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadline, pending)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, deadline, pending)
        }
    }

    private fun cancelRelock(packageName: String) {
        alarmManager.cancel(relockIntent(packageName))
    }

    private fun relockIntent(packageName: String): PendingIntent {
        val intent = Intent(app, AppLockAlarmReceiver::class.java)
            .setAction(AppLockAlarmReceiver.ACTION_RELOCK)
            .setData(Uri.parse("dpcaio://app-lock/relock/${Uri.encode(packageName)}"))
            .putExtra(AppLockAlarmReceiver.EXTRA_TARGET_PACKAGE, packageName)
        return PendingIntent.getBroadcast(
            app,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val RECOVERY_RETRY_MS = 15_000L
        private val transitionLock = Any()
    }
}
