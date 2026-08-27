package io.dpcaio.activity

private fun ab(v: Boolean, m: String) { if (!v) error(m) }

fun main() {
    val userApp = InstalledAppDescriptor("com.alpha", "Alpha Tool", systemApp = false, enabled = true, activityCount = 3)
    val systemApp = InstalledAppDescriptor("com.android.beta", "Beta System", systemApp = true, enabled = true, activityCount = 8)
    val activity = DiscoveredActivity(
        packageName = "com.alpha",
        className = "com.alpha.HiddenSettingsActivity",
        enabled = false,
        exported = false,
        launcherVisible = false,
        requiredPermission = "android.permission.MANAGE_USERS",
        sameUid = false,
        userAccessible = true,
        manifestEnabled = true,
        overrideState = ComponentOverrideState.DISABLED,
        effectiveEnabled = false,
    )

    ab(ActivityBrowserMatcher.matchesApp(userApp, ActivityBrowserFilter(query = "alpha"), false, emptySet()), "query label/package")
    ab(ActivityBrowserMatcher.matchesApp(userApp, ActivityBrowserFilter(appScope = AppScope.USER), false, emptySet()), "user scope")
    ab(!ActivityBrowserMatcher.matchesApp(systemApp, ActivityBrowserFilter(appScope = AppScope.USER), false, emptySet()), "system excluded")
    ab(ActivityBrowserMatcher.matchesApp(systemApp, ActivityBrowserFilter(appScope = AppScope.SYSTEM), false, emptySet()), "system scope")
    ab(ActivityBrowserMatcher.matchesActivity(activity, ActivityBrowserFilter(query = "hidden", enabledState = EnabledStateFilter.DISABLED), false, emptySet()), "activity query + disabled")
    ab(ActivityBrowserMatcher.matchesActivity(activity, ActivityBrowserFilter(exportedState = ExportedStateFilter.NOT_EXPORTED), false, emptySet()), "not exported")
    ab(ActivityBrowserMatcher.matchesActivity(activity, ActivityBrowserFilter(launcherState = LauncherStateFilter.HIDDEN), false, emptySet()), "hidden")
    ab(ActivityBrowserMatcher.matchesActivity(activity, ActivityBrowserFilter(permissionState = PermissionStateFilter.HAS_PERMISSION), false, emptySet()), "permission")
    ab(!ActivityBrowserMatcher.matchesActivity(activity, ActivityBrowserFilter(favoritesOnly = true), false, emptySet()), "favorites only excludes")
    ab(ActivityBrowserMatcher.matchesActivity(activity, ActivityBrowserFilter(favoritesOnly = true), true, emptySet()), "favorites only includes")
    ab(ActivityBrowserMatcher.matchesActivity(activity, ActivityBrowserFilter(favoriteGroup = "ops"), false, setOf("ops")), "group includes")
    ab(!ActivityBrowserMatcher.matchesActivity(activity, ActivityBrowserFilter(favoriteGroup = "ops"), true, setOf("other")), "group excludes")

    val byLabel = ActivityBrowserMatcher.sortApps(listOf(systemApp, userApp), AppSortMode.LABEL)
    ab(byLabel.first().packageName == "com.alpha", "label sort")
    val byPackage = ActivityBrowserMatcher.sortApps(listOf(systemApp, userApp), AppSortMode.PACKAGE)
    ab(byPackage.first().packageName == "com.alpha", "package sort")
    val byCount = ActivityBrowserMatcher.sortApps(listOf(userApp, systemApp), AppSortMode.ACTIVITY_COUNT)
    ab(byCount.first().packageName == "com.android.beta", "activity count descending")
    println("ActivityBrowserModelTest: PASS")
}
