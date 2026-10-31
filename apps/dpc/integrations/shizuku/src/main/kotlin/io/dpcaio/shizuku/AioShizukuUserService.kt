package io.dpcaio.shizuku

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import java.util.concurrent.TimeUnit

class AioShizukuUserService : IAioShizukuUserService.Stub {
    constructor() : super()
    constructor(@Suppress("UNUSED_PARAMETER") context: Context) : super()

    override fun identity(): String = "uid=${Process.myUid()},pid=${Process.myPid()}"

    override fun startActivity(packageName: String, className: String, userId: Int): Int {
        if (!validPackage(packageName) || !validClass(className) || userId < 0) return RESULT_INVALID_ARGUMENT
        val component = "$packageName/$className"
        val primary = runTyped(listOf("/system/bin/am", "start", "-W", "--user", userId.toString(), "-n", component))
        if (primary == 0) return primary
        return runTyped(
            listOf(
                "/system/bin/cmd",
                "activity",
                "start-activity",
                "-W",
                "--user",
                userId.toString(),
                "-n",
                component,
            )
        )
    }

    override fun sendBroadcast(action: String, packageName: String, userId: Int): Int {
        if (!validAction(action) || !validPackage(packageName) || userId < 0) return RESULT_INVALID_ARGUMENT
        return runTyped(listOf("/system/bin/am", "broadcast", "--user", userId.toString(), "-a", action, "-p", packageName))
    }

    override fun queryBroadcastReceivers(action: String, userId: Int): String {
        if (!validAction(action) || userId < 0) return "INVALID_ARGUMENT"
        return runTypedCaptureChecked(
            listOf(
                "/system/bin/pm",
                "query-receivers",
                "--brief",
                "--components",
                "--user",
                userId.toString(),
                "--query-flags",
                "0x00000200",
                "-a",
                action,
            )
        )
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
        return runTypedCaptureChecked(listOf("/system/bin/cmd", "appops", "get", "--user", userId.toString(), packageName))
    }

    override fun getAppOp(packageName: String, op: String, userId: Int): String {
        if (!validPackage(packageName) || !validOp(op) || userId < 0) return "INVALID_ARGUMENT"
        return runTypedCaptureChecked(listOf("/system/bin/cmd", "appops", "get", "--user", userId.toString(), packageName, op))
    }

    override fun resolveAppOp(op: String): String {
        if (!validOp(op)) return "INVALID_ARGUMENT"
        return try {
            val appOpsClass = AppOpsManager::class.java
            val intClass = Int::class.javaPrimitiveType!!
            val requestedCode = op.toIntOrNull() ?: runCatching {
                appOpsClass.getDeclaredMethod("strOpToOp", String::class.java).invoke(null, op) as Int
            }.getOrElse {
                appOpsClass.getDeclaredMethod("strDebugOpToOp", String::class.java).invoke(null, op) as Int
            }
            val switchCode = appOpsClass.getDeclaredMethod("opToSwitch", intClass).invoke(null, requestedCode) as Int
            val opToName = appOpsClass.getDeclaredMethod("opToName", intClass)
            val requestedName = opToName.invoke(null, requestedCode) as String
            val switchName = opToName.invoke(null, switchCode) as String
            buildString {
                appendLine("requested=$requestedName")
                appendLine("requestedCode=$requestedCode")
                appendLine("switch=$switchName")
                append("switchCode=$switchCode")
            }
        } catch (_: Exception) {
            "EXECUTION_ERROR"
        }
    }

    override fun setPackageEnabled(packageName: String, enabled: Boolean, userId: Int): Int {
        if (!validPackage(packageName) || userId < 0) return RESULT_INVALID_ARGUMENT
        return runTyped(listOf("/system/bin/pm", if (enabled) "enable" else "disable-user", "--user", userId.toString(), packageName))
    }

    override fun setComponentEnabledState(packageName: String, className: String, state: String, userId: Int): Int {
        if (!validPackage(packageName) || !validClass(className) || userId < 0) return RESULT_INVALID_ARGUMENT
        val component = "$packageName/$className"
        return when (state) {
            "ENABLED" -> runTyped(listOf("/system/bin/pm", "enable", "--user", userId.toString(), component))
            "DISABLED" -> runTyped(listOf("/system/bin/pm", "disable", "--user", userId.toString(), component))
            "DEFAULT" -> runTyped(listOf("/system/bin/pm", "default-state", "--user", userId.toString(), component))
            else -> RESULT_INVALID_ARGUMENT
        }
    }

    override fun installExistingPackage(packageName: String, userId: Int): Int {
        if (!validPackage(packageName) || userId <= 0) return RESULT_INVALID_ARGUMENT
        return runTyped(listOf("/system/bin/cmd", "package", "install-existing", "--user", userId.toString(), packageName))
    }

    override fun listPackagesForUser(userId: Int): String {
        if (userId < 0) return "INVALID_ARGUMENT"
        return runTypedCapture(listOf("/system/bin/pm", "list", "packages", "--user", userId.toString()))
    }

    override fun uninstallPackageForUser(packageName: String, userId: Int): Int {
        if (!validPackage(packageName) || userId <= 0) return RESULT_INVALID_ARGUMENT
        return runTyped(listOf("/system/bin/pm", "uninstall", "--user", userId.toString(), packageName))
    }

    override fun listUsers(): String = runTypedCapture(listOf("/system/bin/pm", "list", "users"))

    override fun createProfile(name: String, cloneProfile: Boolean): String {
        val safeName = name.trim()
        if (!validProfileName(safeName)) return "INVALID_ARGUMENT"
        val args = mutableListOf("/system/bin/pm", "create-user", "--profileOf", "0")
        if (cloneProfile) {
            args += listOf("--user-type", "android.os.usertype.profile.CLONE")
        } else {
            args += "--managed"
        }
        args += safeName
        return runTypedCapture(args).trim()
    }

    override fun removeProfile(userId: Int): Int {
        if (userId <= 0) return RESULT_INVALID_ARGUMENT
        return runTyped(listOf("/system/bin/pm", "remove-user", userId.toString()))
    }

    override fun startProfile(userId: Int): Int {
        if (userId <= 0) return RESULT_INVALID_ARGUMENT
        return runTyped(listOf("/system/bin/am", "start-user", userId.toString()))
    }

    override fun stopProfile(userId: Int): Int {
        if (userId <= 0) return RESULT_INVALID_ARGUMENT
        return runTyped(listOf("/system/bin/am", "stop-user", "-f", userId.toString()))
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

    private fun runTypedCaptureChecked(args: List<String>): String = try {
        val process = ProcessBuilder(args).redirectErrorStream(true).start()
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroy(); "TIMEOUT"
        } else {
            val output = process.inputStream.bufferedReader().use { it.readText() }
            if (process.exitValue() == 0) output else "EXECUTION_ERROR"
        }
    } catch (_: Exception) { "EXECUTION_ERROR" }

    private fun validPackage(value: String) = PACKAGE_PATTERN.matches(value)
    private fun validClass(value: String) = CLASS_PATTERN.matches(value)
    private fun validAction(value: String) = ACTION_PATTERN.matches(value)
    private fun validPermission(value: String) = PERMISSION_PATTERN.matches(value)
    private fun validOp(value: String) = OP_PATTERN.matches(value)
    private fun validNamespace(value: String) = value in SETTINGS_NAMESPACES
    private fun validSettingKey(value: String) = value.length in 1..160 && SETTING_KEY_PATTERN.matches(value)
    private fun validSettingValue(value: String) = value.length <= 4096 && value.none { it == '\u0000' || it == '\n' || it == '\r' }
    private fun validProfileName(value: String) = value.length in 1..48 && PROFILE_NAME_PATTERN.matches(value)

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
        private val PROFILE_NAME_PATTERN = Regex("[A-Za-z0-9 _.-]+")
        private val SETTINGS_NAMESPACES = setOf("system", "secure", "global")
        private val APP_OP_MODES = setOf("allow", "ignore", "deny", "default", "foreground")
        private val CONFIG_PARTITIONS = listOf("system", "system_ext", "product", "vendor", "odm")
        private val CONFIG_FILE_PATTERN = Regex("[A-Za-z0-9_.-]+\\.xml")
    }
}
