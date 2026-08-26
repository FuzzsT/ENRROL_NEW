package io.dpcaio.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import rikka.shizuku.Shizuku

class ShizukuUserServiceClient(context: Context) {
    private val appContext = context.applicationContext
    @Volatile private var remote: IAioShizukuUserService? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            remote = IAioShizukuUserService.Stub.asInterface(service)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            remote = null
        }
    }

    private val args = Shizuku.UserServiceArgs(
        ComponentName(appContext.packageName, AioShizukuUserService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("aio")
        .debuggable(false)
        .version(1)

    fun bind(): Boolean = runCatching {
        Shizuku.bindUserService(args, connection)
        true
    }.getOrDefault(false)

    fun unbind(): Boolean = runCatching {
        Shizuku.unbindUserService(args, connection, true)
        remote = null
        true
    }.getOrDefault(false)

    fun identity(): String? = runCatching { remote?.identity() }.getOrNull()

    fun startActivity(packageName: String, className: String, userId: Int): Int? =
        runCatching { remote?.startActivity(packageName, className, userId) }.getOrNull()

    fun sendBroadcast(action: String, packageName: String, userId: Int): Int? =
        runCatching { remote?.sendBroadcast(action, packageName, userId) }.getOrNull()

    fun listPermissions(): String? = runCatching { remote?.listPermissions() }.getOrNull()

    fun grantRuntimePermission(packageName: String, permissionName: String, userId: Int): Int? =
        runCatching { remote?.grantRuntimePermission(packageName, permissionName, userId) }.getOrNull()

    fun revokeRuntimePermission(packageName: String, permissionName: String, userId: Int): Int? =
        runCatching { remote?.revokeRuntimePermission(packageName, permissionName, userId) }.getOrNull()

    fun setAppOp(packageName: String, op: String, mode: String, userId: Int): Int? =
        runCatching { remote?.setAppOp(packageName, op, mode, userId) }.getOrNull()

    fun getAppOps(packageName: String, userId: Int): String? =
        runCatching { remote?.getAppOps(packageName, userId) }.getOrNull()


    fun setPackageEnabled(packageName: String, enabled: Boolean, userId: Int): Int? =
        runCatching { remote?.setPackageEnabled(packageName, enabled, userId) }.getOrNull()

    fun setComponentEnabledState(packageName: String, className: String, state: String, userId: Int): Int? =
        runCatching { remote?.setComponentEnabledState(packageName, className, state, userId) }.getOrNull()

    fun installExistingPackage(packageName: String, userId: Int): Int? =
        runCatching { remote?.installExistingPackage(packageName, userId) }.getOrNull()

    fun readSetting(namespaceName: String, key: String, userId: Int): String? =
        runCatching { remote?.readSetting(namespaceName, key, userId) }.getOrNull()

    fun writeSetting(namespaceName: String, key: String, value: String, userId: Int): Int? =
        runCatching { remote?.writeSetting(namespaceName, key, value, userId) }.getOrNull()

    fun deleteSetting(namespaceName: String, key: String, userId: Int): Int? =
        runCatching { remote?.deleteSetting(namespaceName, key, userId) }.getOrNull()

    fun listPermissionConfigFiles(): String? = runCatching { remote?.listPermissionConfigFiles() }.getOrNull()

    fun readPermissionConfig(partitionName: String, fileName: String): String? =
        runCatching { remote?.readPermissionConfig(partitionName, fileName) }.getOrNull()

    fun listSysconfigFiles(): String? = runCatching { remote?.listSysconfigFiles() }.getOrNull()

    fun readSysconfigFile(partitionName: String, fileName: String): String? =
        runCatching { remote?.readSysconfigFile(partitionName, fileName) }.getOrNull()
}
