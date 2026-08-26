package io.dpcaio.samsung.settings

data class SettingRouteContext(
    val namespace: SettingNamespace,
    val isSamsung: Boolean,
    val publicWriteSettings: Boolean,
    val shizukuAvailable: Boolean,
    val systemPrivileged: Boolean,
    val knoxDeepSettingsAvailable: Boolean
)

class SamsungSettingRoutePlanner {
    fun plan(context: SettingRouteContext): List<SettingWriteRoute> {
        if (context.namespace == SettingNamespace.KNOX_DEEP_SETTING) {
            return if (context.isSamsung && context.knoxDeepSettingsAvailable) listOf(SettingWriteRoute.KNOX_DEEP_SETTINGS) else emptyList()
        }
        if (context.namespace == SettingNamespace.SAMSUNG_CUSTOM && context.isSamsung && context.knoxDeepSettingsAvailable) {
            return listOf(SettingWriteRoute.KNOX_DEEP_SETTINGS)
        }
        val routes = mutableListOf<SettingWriteRoute>()
        if (context.namespace == SettingNamespace.SYSTEM && context.publicWriteSettings) routes += SettingWriteRoute.PUBLIC_SETTINGS
        if (context.shizukuAvailable) routes += SettingWriteRoute.SHIZUKU_SETTINGS
        if (context.systemPrivileged) routes += SettingWriteRoute.SYSTEM_PRIVILEGED
        return routes.distinct()
    }
}
