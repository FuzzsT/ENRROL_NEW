package io.dpcaio.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import rikka.shizuku.Shizuku

class ShizukuUserServiceClient(context: Context) {
    private val appContext = context.applicationContext
    private val connectionLock = Any()

    @Volatile private var remote: IAioShizukuUserService? = null
    @Volatile private var serviceBinder: IBinder? = null

    private val deathRecipient = IBinder.DeathRecipient {
        clearRemote()
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            synchronized(connectionLock) {
                clearRemoteLocked()
                if (!service.isBinderAlive) return
                if (runCatching { service.linkToDeath(deathRecipient, 0) }.isFailure) return
                serviceBinder = service
                remote = IAioShizukuUserService.Stub.asInterface(service)
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            clearRemote()
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
        if (isConnected()) return true
        return runCatching {
            Shizuku.bindUserService(args, connection)
            true
        }.getOrDefault(false)
    }

    fun unbind(): Boolean {
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

    private fun clearRemote() {
        synchronized(connectionLock) {
            clearRemoteLocked()
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
