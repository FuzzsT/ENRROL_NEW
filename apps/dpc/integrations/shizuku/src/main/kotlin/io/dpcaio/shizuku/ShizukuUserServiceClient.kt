package io.dpcaio.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import rikka.shizuku.Shizuku

class ShizukuUserServiceClient(context: Context) {
    private val appContext = context.applicationContext
    private val connectionLock = Any()

    @Volatile private var remote: IAioShizukuUserService? = null
    @Volatile private var serviceBinder: IBinder? = null
    @Volatile private var autoRebindEnabled = false
    @Volatile private var bindInFlight = false
    @Volatile private var readinessListening = false

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        retryBindIfReady()
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        if (result == PackageManager.PERMISSION_GRANTED) {
            retryBindIfReady()
        } else {
            stopReadinessListeners()
        }
    }

    private val deathRecipient = IBinder.DeathRecipient {
        clearRemote()
        if (autoRebindEnabled) startReadinessListeners()
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            var accepted = false
            synchronized(connectionLock) {
                clearRemoteLocked()
                bindInFlight = false
                if (!service.isBinderAlive) return
                if (runCatching { service.linkToDeath(deathRecipient, 0) }.isFailure) return
                serviceBinder = service
                remote = IAioShizukuUserService.Stub.asInterface(service)
                accepted = true
            }
            if (accepted) stopReadinessListeners()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            clearRemote()
            if (autoRebindEnabled) startReadinessListeners()
        }
    }

    private val args = Shizuku.UserServiceArgs(
        ComponentName(appContext.packageName, AioShizukuUserService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("aio")
        .debuggable(false)
        .version(2)

    fun bind(): Boolean {
        autoRebindEnabled = true
        startReadinessListeners()
        return retryBindIfReady()
    }

    /**
     * Wait briefly for the typed UserService only from a worker thread.
     * Main-thread callers never block; they only arm the normal async bind/rebind path.
     */
    fun awaitConnected(timeoutMs: Long = 1500L): Boolean {
        bind()
        if (isConnected()) return true
        if (Looper.myLooper() == Looper.getMainLooper()) return false

        val boundedTimeout = timeoutMs.coerceIn(0L, 5000L)
        val deadline = SystemClock.elapsedRealtime() + boundedTimeout
        while (!isConnected()) {
            val remaining = deadline - SystemClock.elapsedRealtime()
            if (remaining <= 0L) break
            Thread.sleep(minOf(50L, remaining))
        }
        return isConnected()
    }

    fun unbind(): Boolean {
        autoRebindEnabled = false
        stopReadinessListeners()
        synchronized(connectionLock) { bindInFlight = false }
        val unbound = runCatching {
            Shizuku.unbindUserService(args, connection, true)
            true
        }.getOrDefault(false)
        clearRemote()
        return unbound
    }

    fun isConnected(): Boolean {
        val binder = serviceBinder ?: return false
        if (!binder.isBinderAlive) {
            clearRemote()
            return false
        }
        return remote?.asBinder()?.isBinderAlive == true
    }

    private fun startReadinessListeners() {
        synchronized(connectionLock) {
            if (readinessListening || !autoRebindEnabled) return
            readinessListening = true
        }
        val binderAdded = runCatching {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            true
        }.getOrDefault(false)
        val permissionAdded = runCatching {
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
            true
        }.getOrDefault(false)
        if (!binderAdded || !permissionAdded) {
            stopReadinessListeners()
        }
    }

    private fun stopReadinessListeners() {
        val shouldRemove = synchronized(connectionLock) {
            if (!readinessListening) false else {
                readinessListening = false
                true
            }
        }
        if (!shouldRemove) return
        runCatching { Shizuku.removeBinderReceivedListener(binderReceivedListener) }
        runCatching { Shizuku.removeRequestPermissionResultListener(permissionResultListener) }
    }

    private fun retryBindIfReady(): Boolean {
        if (!autoRebindEnabled) return false
        if (isConnected()) {
            stopReadinessListeners()
            return true
        }

        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val permissionGranted = binderAlive && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        if (!binderAlive || !permissionGranted) {
            startReadinessListeners()
            return true
        }

        synchronized(connectionLock) {
            if (bindInFlight) return true
            bindInFlight = true
        }
        val submitted = runCatching {
            Shizuku.bindUserService(args, connection)
            true
        }.getOrDefault(false)
        if (!submitted) {
            synchronized(connectionLock) { bindInFlight = false }
            startReadinessListeners()
        }
        return submitted
    }

    private fun clearRemote() {
        synchronized(connectionLock) {
            clearRemoteLocked()
            bindInFlight = false
        }
    }

    private fun clearRemoteLocked() {
        val binder = serviceBinder
        serviceBinder = null
        remote = null
        if (binder != null) {
            runCatching { binder.unlinkToDeath(deathRecipient, 0) }
        }
    }

    private fun connectedRemote(): IAioShizukuUserService? {
        if (!isConnected()) return null
        return remote
    }

    fun identity(): String? = runCatching { connectedRemote()?.identity() }.getOrNull()

    fun startActivity(packageName: String, className: String, userId: Int): Int? =
        runCatching { connectedRemote()?.startActivity(packageName, className, userId) }.getOrNull()

    fun sendBroadcast(action: String, packageName: String, userId: Int): Int? =
        runCatching { connectedRemote()?.sendBroadcast(action, packageName, userId) }.getOrNull()

    fun listPermissions(): String? = runCatching { connectedRemote()?.listPermissions() }.getOrNull()

    fun grantRuntimePermission(packageName: String, permissionName: String, userId: Int): Int? =
        runCatching { connectedRemote()?.grantRuntimePermission(packageName, permissionName, userId) }.getOrNull()

    fun revokeRuntimePermission(packageName: String, permissionName: String, userId: Int): Int? =
        runCatching { connectedRemote()?.revokeRuntimePermission(packageName, permissionName, userId) }.getOrNull()

    fun setAppOp(packageName: String, op: String, mode: String, userId: Int): Int? =
        runCatching { connectedRemote()?.setAppOp(packageName, op, mode, userId) }.getOrNull()

    fun getAppOps(packageName: String, userId: Int): String? =
        runCatching { connectedRemote()?.getAppOps(packageName, userId) }.getOrNull()

    fun getAppOp(packageName: String, op: String, userId: Int): String? =
        runCatching { connectedRemote()?.getAppOp(packageName, op, userId) }.getOrNull()

    fun resolveAppOp(op: String): String? =
        runCatching { connectedRemote()?.resolveAppOp(op) }.getOrNull()

    fun setPackageEnabled(packageName: String, enabled: Boolean, userId: Int): Int? =
        runCatching { connectedRemote()?.setPackageEnabled(packageName, enabled, userId) }.getOrNull()

    fun setComponentEnabledState(packageName: String, className: String, state: String, userId: Int): Int? =
        runCatching { connectedRemote()?.setComponentEnabledState(packageName, className, state, userId) }.getOrNull()

    fun installExistingPackage(packageName: String, userId: Int): Int? =
        runCatching { connectedRemote()?.installExistingPackage(packageName, userId) }.getOrNull()

    fun listPackagesForUser(userId: Int): String? =
        runCatching { connectedRemote()?.listPackagesForUser(userId) }.getOrNull()

    fun uninstallPackageForUser(packageName: String, userId: Int): Int? =
        runCatching { connectedRemote()?.uninstallPackageForUser(packageName, userId) }.getOrNull()

    fun listUsers(): String? = runCatching { connectedRemote()?.listUsers() }.getOrNull()

    fun createProfile(name: String, cloneProfile: Boolean): String? =
        runCatching { connectedRemote()?.createProfile(name, cloneProfile) }.getOrNull()

    fun removeProfile(userId: Int): Int? = runCatching { connectedRemote()?.removeProfile(userId) }.getOrNull()

    fun startProfile(userId: Int): Int? = runCatching { connectedRemote()?.startProfile(userId) }.getOrNull()

    fun stopProfile(userId: Int): Int? = runCatching { connectedRemote()?.stopProfile(userId) }.getOrNull()

    fun readSetting(namespaceName: String, key: String, userId: Int): String? =
        runCatching { connectedRemote()?.readSetting(namespaceName, key, userId) }.getOrNull()

    fun writeSetting(namespaceName: String, key: String, value: String, userId: Int): Int? =
        runCatching { connectedRemote()?.writeSetting(namespaceName, key, value, userId) }.getOrNull()

    fun deleteSetting(namespaceName: String, key: String, userId: Int): Int? =
        runCatching { connectedRemote()?.deleteSetting(namespaceName, key, userId) }.getOrNull()

    fun listPermissionConfigFiles(): String? = runCatching { connectedRemote()?.listPermissionConfigFiles() }.getOrNull()

    fun readPermissionConfig(partitionName: String, fileName: String): String? =
        runCatching { connectedRemote()?.readPermissionConfig(partitionName, fileName) }.getOrNull()

    fun listSysconfigFiles(): String? = runCatching { connectedRemote()?.listSysconfigFiles() }.getOrNull()

    fun readSysconfigFile(partitionName: String, fileName: String): String? =
        runCatching { connectedRemote()?.readSysconfigFile(partitionName, fileName) }.getOrNull()
}
