package io.dpcaio.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
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

    private data class ReceiverChoice(
        val componentName: String,
        val label: String,
        val enabled: Boolean,
        val exported: Boolean,
    ) {
        override fun toString(): String = buildString {
            append(label)
            append(" • ")
            append(componentName)
            append(if (enabled) " • enabled" else " • disabled")
            append(if (exported) " • exported" else " • private")
        }
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
            val rows = packageChoices(activity, shizuku, userId, setOfNotNull(currentPackage?.takeIf { it.isNotBlank() }))
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                if (rows.isEmpty()) {
                    showNoPackages(activity, userId)
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

    fun showPackageMultiPicker(
        activity: Activity,
        shizuku: ShizukuUserServiceClient? = null,
        userId: Int,
        currentPackages: Set<String> = emptySet(),
        title: String = "Choose packages",
        onSelected: (Set<String>) -> Unit,
    ) {
        Thread {
            val normalizedCurrent = currentPackages.map(String::trim).filter(String::isNotEmpty).toSet()
            val rows = packageChoices(activity, shizuku, userId, normalizedCurrent)
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                if (rows.isEmpty()) {
                    showNoPackages(activity, userId)
                    return@runOnUiThread
                }

                val labels = rows.map(PackageChoice::toString).toTypedArray()
                val checked = BooleanArray(rows.size) { index -> rows[index].packageName in normalizedCurrent }
                val selected = normalizedCurrent.toMutableSet()

                AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                        val packageName = rows[which].packageName
                        if (isChecked) selected += packageName else selected -= packageName
                    }
                    .setPositiveButton("Apply") { _, _ ->
                        onSelected(selected.toSortedSet(String.CASE_INSENSITIVE_ORDER))
                    }
                    .setNeutralButton("Clear") { _, _ -> onSelected(emptySet()) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }.start()
    }

    fun showReceiverPicker(
        activity: Activity,
        packageName: String,
        currentComponent: String? = null,
        onSelected: (String) -> Unit,
    ) {
        Thread {
            val rows = receiverChoices(activity.packageManager, packageName)
            val normalizedCurrent = currentComponent
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let(ComponentName::unflattenFromString)
                ?.flattenToShortString()
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                if (rows.isEmpty()) {
                    AlertDialog.Builder(activity)
                        .setTitle("Choose receiver")
                        .setMessage("No broadcast receivers are visible for $packageName.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@runOnUiThread
                }
                val labels = rows.map(ReceiverChoice::toString).toTypedArray()
                val checked = rows.indexOfFirst { it.componentName == normalizedCurrent }.coerceAtLeast(0)
                AlertDialog.Builder(activity)
                    .setTitle("Choose receiver")
                    .setSingleChoiceItems(labels, checked) { dialog, which ->
                        onSelected(rows[which].componentName)
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }.start()
    }

    private fun packageChoices(
        activity: Activity,
        shizuku: ShizukuUserServiceClient?,
        userId: Int,
        preservePackages: Set<String>,
    ): List<PackageChoice> {
        val choices = installedPackages(activity.packageManager)
            .associateBy { it.packageName }
            .toMutableMap()

        if (shizuku != null && shizuku.awaitConnected(1200L)) {
            parsePackages(shizuku.listPackagesForUser(userId).orEmpty()).forEach { packageName ->
                choices.putIfAbsent(packageName, PackageChoice(packageName, packageName))
            }
        }

        preservePackages.filter(PACKAGE_PATTERN::matches).forEach { packageName ->
            choices.putIfAbsent(packageName, PackageChoice(packageName, packageName))
        }

        return choices.values.sortedWith(Comparator { left, right ->
            val byLabel = String.CASE_INSENSITIVE_ORDER.compare(left.label, right.label)
            if (byLabel != 0) byLabel else String.CASE_INSENSITIVE_ORDER.compare(left.packageName, right.packageName)
        })
    }

    private fun receiverChoices(pm: PackageManager, packageName: String): List<ReceiverChoice> {
        val flags = PackageManager.GET_RECEIVERS or PackageManager.MATCH_DISABLED_COMPONENTS
        val packageInfo = runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, flags)
            }
        }.getOrNull() ?: return emptyList()

        return packageInfo.receivers.orEmpty().map { info ->
            val componentName = ComponentName(info.packageName, info.name).flattenToShortString()
            val label = runCatching { info.loadLabel(pm).toString() }
                .getOrDefault(info.name)
                .ifBlank { info.name.substringAfterLast('.') }
            ReceiverChoice(
                componentName = componentName,
                label = label,
                enabled = info.enabled,
                exported = info.exported,
            )
        }.sortedWith(Comparator { left, right ->
            val byLabel = String.CASE_INSENSITIVE_ORDER.compare(left.label, right.label)
            if (byLabel != 0) byLabel else String.CASE_INSENSITIVE_ORDER.compare(left.componentName, right.componentName)
        })
    }

    private fun showNoPackages(activity: Activity, userId: Int) {
        AlertDialog.Builder(activity)
            .setTitle("Choose package")
            .setMessage("No packages are visible for user $userId in the current runtime.")
            .setPositiveButton("OK", null)
            .show()
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
