package io.dpcaio.appmanager

data class ManagedAppRecord(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val uid: Int,
    val userId: Int,
    val systemApp: Boolean,
    val enabled: Boolean,
    val hidden: Boolean?,
    val suspended: Boolean?
)

enum class AppInventoryFilter {
    ALL,
    SYSTEM,
    USER,
    DISABLED,
    HIDDEN,
    SUSPENDED
}

class AppInventoryFilterEngine {
    fun apply(apps: List<ManagedAppRecord>, filter: AppInventoryFilter): List<ManagedAppRecord> = when (filter) {
        AppInventoryFilter.ALL -> apps
        AppInventoryFilter.SYSTEM -> apps.filter { it.systemApp }
        AppInventoryFilter.USER -> apps.filterNot { it.systemApp }
        AppInventoryFilter.DISABLED -> apps.filterNot { it.enabled }
        AppInventoryFilter.HIDDEN -> apps.filter { it.hidden == true }
        AppInventoryFilter.SUSPENDED -> apps.filter { it.suspended == true }
    }
}
