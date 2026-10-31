package io.dpcaio.app

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import io.dpcaio.shizuku.ShizukuUserServiceClient

/**
 * Shared runtime target chooser for screens that previously required a raw
 * Android user id or package name. The picker is advisory UI only: the target
 * operation still performs its own permission/capability checks.
 */
object RuntimeTargetPicker {
    private data class UserChoice(val id: Int, val label: String) {
        override fun toString(): String = "$label • user $id"
    }

    private data class PackageChoice(val packageName: String, val label: String) {
        override fun toString(): String = if (label == packageName) packageName else "$label • $packageName"
    }

    fun showUserPicker(
        activity: Activity,
        shizuku: ShizukuUserServiceClient?,
        currentUserId: Int,
        selectedUserId: Int = currentUserId,
        onSelected: (Int) -> Unit,
    ) {
        Thread {
            val choices = linkedMapOf<Int, UserChoice>()
            choices[currentUserId] = UserChoice(currentUserId, "Current user")

            if (shizuku != null && shizuku.awaitConnected(1200L)) {
                parseUsers(shizuku.listUsers().orEmpty()).forEach { choice ->
                    choices[choice.id] = if (choice.id == currentUserId) {
                        UserChoice(choice.id, "${choice.label} (current)")
                    } else {
                        choice
                    }
                }
            }

            val rows = choices.values.sortedBy { it.id }
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                val labels = rows.map(UserChoice::toString).toTypedArray()
                val checked = rows.indexOfFirst { it.id == selectedUserId }.coerceAtLeast(0)
                AlertDialog.Builder(activity)
                    .setTitle("Choose user")
                    .setSingleChoiceItems(labels, checked) { dialog, which ->
                        onSelected(rows[which].id)
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }.start()
    }

    fun showPackagePicker(
        activity: Activity,
        shizuku: ShizukuUserServiceClient? = null,
        userId: Int,
        currentPackage: String? = null,
        onSelected: (String) -> Unit,
    ) {
        Thread {
            val local = installedPackages(activity.packageManager)
                .associateBy { it.packageName }
                .toMutableMap()

            if (shizuku != null && shizuku.awaitConnected(1200L)) {
                parsePackages(shizuku.listPackagesForUser(userId).orEmpty()).forEach { packageName ->
                    local.putIfAbsent(packageName, PackageChoice(packageName, packageName))
                }
            }

            val rows = local.values.sortedWith(
                compareBy<PackageChoice>(String.CASE_INSENSITIVE_ORDER) { it.label }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.packageName },
            )
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                if (rows.isEmpty()) {
                    AlertDialog.Builder(activity)
                        .setTitle("Choose package")
                        .setMessage("No packages are visible for user $userId in the current runtime.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@runOnUiThread
                }
                val labels = rows.map(PackageChoice::toString).toTypedArray()
                val checked = rows.indexOfFirst { it.packageName == currentPackage }.coerceAtLeast(0)
                AlertDialog.Builder(activity)
                    .setTitle("Choose package")
                    .setSingleChoiceItems(labels, checked) { dialog, which ->
                        onSelected(rows[which].packageName)
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }.start()
    }

    @Suppress("DEPRECATION")
    private fun installedPackages(pm: PackageManager): List<PackageChoice> {
        val apps: List<ApplicationInfo> = pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)
        return apps.map { info ->
            val label = runCatching { pm.getApplicationLabel(info).toString() }
                .getOrDefault(info.packageName)
                .ifBlank { info.packageName }
            PackageChoice(info.packageName, label)
        }
    }

    private fun parseUsers(raw: String): List<UserChoice> {
        val pattern = Regex("UserInfo\\{(\\d+):([^:}]*)")
        return pattern.findAll(raw).mapNotNull { match ->
            val id = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val name = match.groupValues[2].trim().ifBlank { "User $id" }
            UserChoice(id, name)
        }.distinctBy { it.id }.toList()
    }

    private fun parsePackages(raw: String): Set<String> = raw.lineSequence()
        .map { it.trim() }
        .filter { it.startsWith("package:") }
        .map { it.removePrefix("package:").substringBefore('=').trim() }
        .filter { it.isNotEmpty() && PACKAGE_PATTERN.matches(it) }
        .toCollection(linkedSetOf())

    private val PACKAGE_PATTERN = Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")
}
