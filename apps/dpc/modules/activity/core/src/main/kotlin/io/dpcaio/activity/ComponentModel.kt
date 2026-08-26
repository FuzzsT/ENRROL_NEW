package io.dpcaio.activity

enum class ActivityRoute {
    LAUNCHER_APPS,
    FRAMEWORK_EXPLICIT,
    DEEP_LINK,
    SAME_UID,
    DPC_PREPARE_RETRY,
    COMPANION_RELAY,
    SHIZUKU,
    SYSTEM_PRIVILEGED,
    LAB_JAVA_HOOK,
    LAB_ART_HOOK
}

data class ActivityAccessInput(
    val packageName: String,
    val className: String,
    val enabled: Boolean,
    val exported: Boolean,
    val launcherVisible: Boolean,
    val sameUid: Boolean,
    val userAccessible: Boolean,
    val packageHiddenByDpc: Boolean = false,
    val suspendedByDpc: Boolean = false,
    val dpcCanPrepare: Boolean = false,
    val deepLinkAvailable: Boolean = false,
    val companionRelayAvailable: Boolean = false,
    val shizukuAccessible: Boolean = false,
    val systemPrivilegedAccessible: Boolean = false,
    val labBuild: Boolean = false,
    val targetOwnedDebuggable: Boolean = false,
    val labJavaHookAvailable: Boolean = false,
    val labArtHookAvailable: Boolean = false
)

data class ActivityAccessPlan(
    val routes: List<ActivityRoute>,
    val blockers: List<String>
) {
    val selected: ActivityRoute? get() = routes.firstOrNull()
}

data class DiscoveredActivity(
    val packageName: String,
    val className: String,
    val enabled: Boolean,
    val exported: Boolean,
    val launcherVisible: Boolean,
    val requiredPermission: String?,
    val sameUid: Boolean,
    val userAccessible: Boolean,
    val manifestEnabled: Boolean = enabled,
    val overrideState: ComponentOverrideState = ComponentOverrideState.DEFAULT,
    val effectiveEnabled: Boolean = enabled
)
