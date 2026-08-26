package io.dpcaio.shizuku

import android.content.Context
import android.os.Process
import java.util.concurrent.TimeUnit

class AioShizukuUserService : IAioShizukuUserService.Stub {
    constructor() : super()
    constructor(@Suppress("UNUSED_PARAMETER") context: Context) : super()

    override fun identity(): String = "uid=${Process.myUid()},pid=${Process.myPid()}"

    override fun startActivity(packageName: String, className: String, userId: Int): Int {
        if (!validPackage(packageName) || !validClass(className) || userId < 0) return RESULT_INVALID_ARGUMENT
        return runTyped(listOf("/system/bin/am", "start", "--user", userId.toString(), "-n", "$packageName/$className"))
    }

    override fun sendBroadcast(action: String, packageName: String, userId: Int): Int {
        if (!validAction(action) || !validPackage(packageName) || userId < 0) return RESULT_INVALID_ARGUMENT
        return runTyped(listOf("/system/bin/am", "broadcast", "--user", userId.toString(), "-a", action, "-p", packageName))
    }

    override fun listPermissions(): String = runTypedCapture(listOf("/system/bin/pm", "list", "permissions", "-f", "-g", "-d", "-u"))

    override fun grantRuntimePermission(packageName: String, permissionName: String, userId: Int): Int {
        if (!validPackage(packageName) || !validPermission(permissionName) || userId < 0) return RESULT_INVALID_ARGUMENT
        return runTyped(listOf("/system/bin/pm", "grant", "--user", userId.toString(), packageName, permissionName))
    }

    override fun revokeRuntimePermission(packageName: String, permissionName: String, userId: Int): Int {
        if (!validPackage(packageName) || !validPermission(permissionName) || userId < 0) return RESULT_INVALID_ARGUMENT
        return runTyped(listOf("/system/bin/pm", "revoke", "--user", userId.toString(), packageName, permissionName))
    }

    override fun setAppOp(packageName: String, op: String, mode: String, userId: Int): Int {
        if (!validPackage(packageName) || !validOp(op) || mode !in APP_OP_MODES || userId < 0) return RESULT_INVALID_ARGUMENT
        return runTyped(listOf("/system/bin/cmd", "appops", "set", "--user", userId.toString(), packageName, op, mode))
    }

    override fun getAppOps(packageName: String, userId: Int): String {
        if (!validPackage(packageName) || userId < 0) return "INVALID_ARGUMENT"
        return runTypedCapture(listOf("/system/bin/cmd", "appops", "get", "--user", userId.toString(), packageName))
    }


    override fun setPackageEnabled(packageName: String, enabled: Boolean, userId: Int): Int {
        if (!validPackage(packageName) || userId < 0) return RESULT_INVALID_ARGUMENT
        return runTyped(listOf("/system/bin/pm", if (enabled) "enable" else "disable-user", "--user", userId.toString(), packageName))
    }

    override fun setComponentEnabledState(packageName: String, className: String, state: String, userId: Int): Int {
        if (!validPackage(packageName) || !validClass(className) || userId < 0) return RESULT_INVALID_ARGUMENT
        val command = when (state) {
            "ENABLED" -> "enable"
            "DISABLED" -> "disable"
            "DEFAULT" -> "default-state"
            else -> return RESULT_INVALID_ARGUMENT
        }
        val component = "$packageName/$className"
        return when (command) {
            "enable" -> runTyped(listOf("/system/bin/pm", "enable", "--user", userId.toString(), component))
            "disable" -> runTyped(listOf("/system/bin/pm", "disable", "--user", userId.toString(), component))
            else -> runTyped(listOf("/system/bin/pm", "default-state", "--user", userId.toString(), component))
        }
    }

    override fun installExistingPackage(packageName: String, userId: Int): Int {
        if (!validPackage(packageName) || userId < 0) return RESULT_INVALID_ARGUMENT
        return runTyped(listOf("/system/bin/cmd", "package", "install-existing", "--user", userId.toString(), packageName))
    }

    override fun readSetting(namespaceName: String, key: String, userId: Int): String {
        if (!validNamespace(namespaceName) || !validSettingKey(key) || userId < 0) return "INVALID_ARGUMENT"
        return runTypedCapture(listOf("/system/bin/settings", "--user", userId.toString(), "get", namespaceName, key)).trim()
    }

    override fun writeSetting(namespaceName: String, key: String, value: String, userId: Int): Int {
        if (!validNamespace(namespaceName) || !validSettingKey(key) || !validSettingValue(value) || userId < 0) return RESULT_INVALID_ARGUMENT
        return runTyped(listOf("/system/bin/settings", "--user", userId.toString(), "put", namespaceName, key, value))
    }

    override fun deleteSetting(namespaceName: String, key: String, userId: Int): Int {
        if (!validNamespace(namespaceName) || !validSettingKey(key) || userId < 0) return RESULT_INVALID_ARGUMENT
        return runTyped(listOf("/system/bin/settings", "--user", userId.toString(), "delete", namespaceName, key))
    }


    override fun listPermissionConfigFiles(): String = listConfigDir("etc/permissions")

    override fun readPermissionConfig(partitionName: String, fileName: String): String = readConfigFile(partitionName, "etc/permissions", fileName)

    override fun listSysconfigFiles(): String = listConfigDir("etc/sysconfig")

    override fun readSysconfigFile(partitionName: String, fileName: String): String = readConfigFile(partitionName, "etc/sysconfig", fileName)

    private fun listConfigDir(relativeDir: String): String = buildString {
        for (partition in CONFIG_PARTITIONS) {
            val path = "/$partition/$relativeDir"
            val listing = runTypedCapture(listOf("/system/bin/ls", "-1", path))
            appendLine("[$partition]")
            appendLine(listing.trim())
        }
    }

    private fun readConfigFile(partitionName: String, relativeDir: String, fileName: String): String {
        if (partitionName !in CONFIG_PARTITIONS || !CONFIG_FILE_PATTERN.matches(fileName)) return "INVALID_ARGUMENT"
        return runTypedCapture(listOf("/system/bin/cat", "/$partitionName/$relativeDir/$fileName"))
    }

    private fun runTyped(args: List<String>): Int = try {
        val process = ProcessBuilder(args).redirectErrorStream(true).start()
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroy(); RESULT_TIMEOUT
        } else process.exitValue()
    } catch (_: Exception) { RESULT_EXECUTION_ERROR }

    private fun runTypedCapture(args: List<String>): String = try {
        val process = ProcessBuilder(args).redirectErrorStream(true).start()
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroy(); "TIMEOUT"
        } else process.inputStream.bufferedReader().use { it.readText() }
    } catch (_: Exception) { "EXECUTION_ERROR" }

    private fun validPackage(value: String) = PACKAGE_PATTERN.matches(value)
    private fun validClass(value: String) = CLASS_PATTERN.matches(value)
    private fun validAction(value: String) = ACTION_PATTERN.matches(value)
    private fun validPermission(value: String) = PERMISSION_PATTERN.matches(value)
    private fun validOp(value: String) = OP_PATTERN.matches(value)
    private fun validNamespace(value: String) = value in SETTINGS_NAMESPACES
    private fun validSettingKey(value: String) = value.length in 1..160 && SETTING_KEY_PATTERN.matches(value)
    private fun validSettingValue(value: String) = value.length <= 4096 && value.none { it == '\u0000' || it == '\n' || it == '\r' }

    companion object {
        const val RESULT_INVALID_ARGUMENT = -2
        const val RESULT_TIMEOUT = -3
        const val RESULT_EXECUTION_ERROR = -4
        private val PACKAGE_PATTERN = Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")
        private val CLASS_PATTERN = Regex("[A-Za-z0-9_.$]+")
        private val ACTION_PATTERN = Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")
        private val PERMISSION_PATTERN = Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")
        private val OP_PATTERN = Regex("[A-Za-z0-9_.:-]+")
        private val SETTING_KEY_PATTERN = Regex("[A-Za-z0-9_.:-]+")
        private val SETTINGS_NAMESPACES = setOf("system", "secure", "global")
        private val APP_OP_MODES = setOf("allow", "ignore", "deny", "default", "foreground")
        private val CONFIG_PARTITIONS = listOf("system", "system_ext", "product", "vendor", "odm")
        private val CONFIG_FILE_PATTERN = Regex("[A-Za-z0-9_.-]+\\.xml")
    }
}
