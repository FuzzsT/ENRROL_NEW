package io.dpcaio.activity

data class InstalledAppDescriptor(
    val packageName: String,
    val label: String,
    val systemApp: Boolean,
    val enabled: Boolean,
    val activityCount: Int,
)

enum class AppScope { ALL, USER, SYSTEM }
enum class EnabledStateFilter { ALL, ENABLED, DISABLED }
enum class ExportedStateFilter { ALL, EXPORTED, NOT_EXPORTED }
enum class LauncherStateFilter { ALL, LAUNCHER, HIDDEN }
enum class PermissionStateFilter { ALL, HAS_PERMISSION, NO_PERMISSION }
enum class AppSortMode { LABEL, PACKAGE, ACTIVITY_COUNT }

data class ActivityBrowserFilter(
    val query: String = "",
    val appScope: AppScope = AppScope.ALL,
    val enabledState: EnabledStateFilter = EnabledStateFilter.ALL,
    val exportedState: ExportedStateFilter = ExportedStateFilter.ALL,
    val launcherState: LauncherStateFilter = LauncherStateFilter.ALL,
    val permissionState: PermissionStateFilter = PermissionStateFilter.ALL,
    val favoritesOnly: Boolean = false,
    val favoriteGroup: String? = null,
    val sortMode: AppSortMode = AppSortMode.LABEL,
)

object ActivityBrowserMatcher {
    fun matchesApp(
        app: InstalledAppDescriptor,
        filter: ActivityBrowserFilter,
        favorite: Boolean,
        groups: Set<String>,
    ): Boolean {
        if (filter.appScope == AppScope.USER && app.systemApp) return false
        if (filter.appScope == AppScope.SYSTEM && !app.systemApp) return false
        if (filter.enabledState == EnabledStateFilter.ENABLED && !app.enabled) return false
        if (filter.enabledState == EnabledStateFilter.DISABLED && app.enabled) return false
        if (filter.favoritesOnly && !favorite) return false
        if (filter.favoriteGroup != null && filter.favoriteGroup !in groups) return false
        val q = filter.query.trim().lowercase()
        return q.isBlank() || app.label.lowercase().contains(q) || app.packageName.lowercase().contains(q)
    }

    fun matchesActivity(
        activity: DiscoveredActivity,
        filter: ActivityBrowserFilter,
        favorite: Boolean,
        groups: Set<String>,
    ): Boolean {
        if (filter.enabledState == EnabledStateFilter.ENABLED && !activity.effectiveEnabled) return false
        if (filter.enabledState == EnabledStateFilter.DISABLED && activity.effectiveEnabled) return false
        if (filter.exportedState == ExportedStateFilter.EXPORTED && !activity.exported) return false
        if (filter.exportedState == ExportedStateFilter.NOT_EXPORTED && activity.exported) return false
        if (filter.launcherState == LauncherStateFilter.LAUNCHER && !activity.launcherVisible) return false
        if (filter.launcherState == LauncherStateFilter.HIDDEN && activity.launcherVisible) return false
        if (filter.permissionState == PermissionStateFilter.HAS_PERMISSION && activity.requiredPermission.isNullOrBlank()) return false
        if (filter.permissionState == PermissionStateFilter.NO_PERMISSION && !activity.requiredPermission.isNullOrBlank()) return false
        if (filter.favoritesOnly && !favorite) return false
        if (filter.favoriteGroup != null && filter.favoriteGroup !in groups) return false
        val q = filter.query.trim().lowercase()
        return q.isBlank() || activity.packageName.lowercase().contains(q) || activity.className.lowercase().contains(q) ||
            (activity.requiredPermission?.lowercase()?.contains(q) == true)
    }

    fun sortApps(apps: List<InstalledAppDescriptor>, mode: AppSortMode): List<InstalledAppDescriptor> = when (mode) {
        AppSortMode.LABEL -> apps.sortedWith(compareBy<InstalledAppDescriptor> { it.label.lowercase() }.thenBy { it.packageName })
        AppSortMode.PACKAGE -> apps.sortedBy { it.packageName.lowercase() }
        AppSortMode.ACTIVITY_COUNT -> apps.sortedWith(compareByDescending<InstalledAppDescriptor> { it.activityCount }.thenBy { it.label.lowercase() })
    }
}
