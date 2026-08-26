package io.dpcaio.app

import android.app.Activity
import io.dpcaio.account.AccountPriorityPlanner
import io.dpcaio.account.android.AndroidGoogleAccountRepository
import io.dpcaio.activity.ActivityAccessPlanner
import io.dpcaio.activity.android.AndroidActivityInventory
import io.dpcaio.appmanager.AppInventoryFilterEngine
import io.dpcaio.appmanager.android.AndroidAppInventory
import io.dpcaio.core.model.BuildTrack
import io.dpcaio.core.model.CapabilityRequirements
import io.dpcaio.core.model.OwnershipRequirement
import io.dpcaio.core.model.RiskClass
import io.dpcaio.core.model.VisibilityClass
import io.dpcaio.delegation.DelegationAuthorizer
import io.dpcaio.delegation.dhizuku.DhizukuCompatRuntime
import io.dpcaio.execution.ExecutionPlanner
import io.dpcaio.protection.ProtectionPlanner
import io.dpcaio.installer.InstallPlanner
import io.dpcaio.installer.android.AndroidPackageInstallerAdapter
import io.dpcaio.knox.license.KnoxStartupGate
import io.dpcaio.knox.official.KnoxCapabilityReducer
import io.dpcaio.knox.official.android.KnoxSdkInventory
import io.dpcaio.knox.mock.KnoxMockLicenseState
import io.dpcaio.knox.mock.android.KnoxMockService
import io.dpcaio.knoxzt.KnoxZtRecoveryPlanner
import io.dpcaio.knoxzt.android.KnoxZtActivitySupport
import io.dpcaio.lab.LabHookActivityRouteExecutor
import io.dpcaio.nativebridge.NativeTraceBridge
import io.dpcaio.network.DnsRule
import io.dpcaio.network.android.DeviceOwnerPrivateDnsController
import io.dpcaio.offline.OfflineReadinessPlanner
import io.dpcaio.oem.OemCircuitBreaker
import io.dpcaio.oem.android.OemLabProbeService
import io.dpcaio.offline.android.AndroidOfflineBundleReader
import io.dpcaio.nfc.NfcTraceOwnership
import io.dpcaio.nfc.android.NfcTagInspector
import io.dpcaio.permission.EffectiveCapabilityResolver
import io.dpcaio.permission.android.AndroidPermissionCatalog
import io.dpcaio.platform.CompatibilityGate
import io.dpcaio.policy.PolicyStatus
import io.dpcaio.policy.android.AndroidDevicePolicyGateway
import io.dpcaio.samsung.settings.SettingNamespace
import io.dpcaio.samsung.settings.android.AndroidSamsungSettingGateway
import io.dpcaio.scenario.ScenarioEventType
import io.dpcaio.scenario.android.AndroidScenarioBridge
import io.dpcaio.shizuku.AndroidShizukuRuntime

enum class DpcModuleGroup(val label: String) {
    CORE("Core"),
    ANDROID("Android"),
    INTEGRATION("Integration"),
    LAB("Lab")
}

data class DpcModuleDescriptor(
    val id: String,
    val title: String,
    val group: DpcModuleGroup,
    val representative: Class<*>? = null,
    val entryActivity: Class<out Activity>? = null,
    val requirements: CapabilityRequirements = CapabilityRequirements(),
    val tags: Set<String> = emptySet(),
) {
    val status: String get() = "Integrated"
    val surface: String get() = if (entryActivity != null) "UI" else "API"
}

/**
 * Compile-time registry of every Gradle module owned by :app-dpc.
 *
 * Direct class references deliberately make module ownership explicit: if a module is removed,
 * renamed or no longer available to the application compile classpath, :app-dpc cannot compile.
 */
object DpcModuleRegistry {
    val modules: List<DpcModuleDescriptor> = listOf(
        DpcModuleDescriptor(":core-model", "Core model", DpcModuleGroup.CORE, BuildTrack::class.java),
        DpcModuleDescriptor(":core-execution", "Execution planner", DpcModuleGroup.CORE, ExecutionPlanner::class.java),
        DpcModuleDescriptor(":enterprise-protection", "Enterprise protection guard", DpcModuleGroup.CORE, ProtectionPlanner::class.java, tags = setOf("enterprise", "protection")),
        DpcModuleDescriptor(":platform-compat", "Platform compatibility", DpcModuleGroup.CORE, CompatibilityGate::class.java),

        DpcModuleDescriptor(":policy-core", "Device policy core", DpcModuleGroup.CORE, PolicyStatus::class.java),
        DpcModuleDescriptor(":policy-android", "Device policy Android", DpcModuleGroup.ANDROID, AndroidDevicePolicyGateway::class.java),

        DpcModuleDescriptor(":permission-manager", "Permission manager core", DpcModuleGroup.CORE, EffectiveCapabilityResolver::class.java, PermissionManagerActivity::class.java),
        DpcModuleDescriptor(":permission-android", "Permission manager Android", DpcModuleGroup.ANDROID, AndroidPermissionCatalog::class.java, PermissionManagerActivity::class.java),
        DpcModuleDescriptor(":offline-core", "Full offline core", DpcModuleGroup.CORE, OfflineReadinessPlanner::class.java, OfflineSetupActivity::class.java),
        DpcModuleDescriptor(":offline-android", "Full offline Android", DpcModuleGroup.ANDROID, AndroidOfflineBundleReader::class.java, OfflineSetupActivity::class.java),

        DpcModuleDescriptor(":samsung-settings", "Samsung settings core", DpcModuleGroup.CORE, SettingNamespace::class.java, SamsungSettingsEditorActivity::class.java, requirements = CapabilityRequirements(requiresSamsung = true), tags = setOf("samsung_knox")),
        DpcModuleDescriptor(":samsung-settings-android", "Samsung settings Android", DpcModuleGroup.ANDROID, AndroidSamsungSettingGateway::class.java, SamsungSettingsEditorActivity::class.java, requirements = CapabilityRequirements(requiresSamsung = true), tags = setOf("samsung_knox")),

        DpcModuleDescriptor(":account-manager", "Account manager core", DpcModuleGroup.CORE, AccountPriorityPlanner::class.java, GoogleAccountManagerActivity::class.java),
        DpcModuleDescriptor(":account-android", "Account manager Android", DpcModuleGroup.ANDROID, AndroidGoogleAccountRepository::class.java, GoogleAccountManagerActivity::class.java),

        DpcModuleDescriptor(":app-manager", "App management core", DpcModuleGroup.CORE, AppInventoryFilterEngine::class.java),
        DpcModuleDescriptor(":app-android", "App management Android", DpcModuleGroup.ANDROID, AndroidAppInventory::class.java),

        DpcModuleDescriptor(":activity-launcher", "Activity launcher core", DpcModuleGroup.CORE, ActivityAccessPlanner::class.java, ActivityExplorerActivity::class.java),
        DpcModuleDescriptor(":activity-android", "Activity launcher Android", DpcModuleGroup.ANDROID, AndroidActivityInventory::class.java, ActivityExplorerActivity::class.java),

        DpcModuleDescriptor(":installer-core", "Installer core", DpcModuleGroup.CORE, InstallPlanner::class.java),
        DpcModuleDescriptor(":installer-android", "Installer Android", DpcModuleGroup.ANDROID, AndroidPackageInstallerAdapter::class.java),

        DpcModuleDescriptor(":delegation-core", "Delegation broker", DpcModuleGroup.CORE, DelegationAuthorizer::class.java),
        DpcModuleDescriptor(":dhizuku-compat", "Dhizuku compatibility", DpcModuleGroup.INTEGRATION, DhizukuCompatRuntime::class.java, requirements = CapabilityRequirements(visibility = VisibilityClass.HIDDEN), tags = setOf("advanced")),
        DpcModuleDescriptor(":shizuku-adapter", "Shizuku adapter", DpcModuleGroup.INTEGRATION, AndroidShizukuRuntime::class.java, requirements = CapabilityRequirements(visibility = VisibilityClass.HIDDEN), tags = setOf("advanced")),
        DpcModuleDescriptor(":native-diagnostics", "Native diagnostics", DpcModuleGroup.INTEGRATION, NativeTraceBridge::class.java, requirements = CapabilityRequirements(visibility = VisibilityClass.HIDDEN), tags = setOf("advanced", "diagnostics")),

        DpcModuleDescriptor(":network-control", "Network policy core", DpcModuleGroup.CORE, DnsRule::class.java, NetworkControlActivity::class.java),
        DpcModuleDescriptor(":network-android", "Network policy Android", DpcModuleGroup.ANDROID, DeviceOwnerPrivateDnsController::class.java, NetworkControlActivity::class.java),

        DpcModuleDescriptor(":knox-license-core", "Knox license core", DpcModuleGroup.CORE, KnoxStartupGate::class.java, requirements = CapabilityRequirements(requiresSamsung = true), tags = setOf("samsung_knox")),
        DpcModuleDescriptor(":knox-official-core", "Knox official policy core", DpcModuleGroup.CORE, KnoxCapabilityReducer::class.java, requirements = CapabilityRequirements(requiresSamsung = true, requiresKnox = true), tags = setOf("samsung_knox", "enterprise")),
        DpcModuleDescriptor(":knox-official-android", "Knox official Android adapter", DpcModuleGroup.ANDROID, KnoxSdkInventory::class.java, requirements = CapabilityRequirements(requiresSamsung = true, requiresKnox = true), tags = setOf("samsung_knox", "enterprise")),
        DpcModuleDescriptor(":knox-license-lab", "Knox license lab", DpcModuleGroup.LAB, requirements = CapabilityRequirements(visibility = VisibilityClass.LAB), tags = setOf("lab", "samsung_knox")),
        DpcModuleDescriptor(":knox-mock-core", "Knox mock core", DpcModuleGroup.LAB, KnoxMockLicenseState::class.java, requirements = CapabilityRequirements(visibility = VisibilityClass.LAB), tags = setOf("lab", "samsung_knox")),
        DpcModuleDescriptor(":knox-mock-android", "Knox mock Android", DpcModuleGroup.LAB, KnoxMockService::class.java, requirements = CapabilityRequirements(visibility = VisibilityClass.LAB), tags = setOf("lab", "samsung_knox")),
        DpcModuleDescriptor(":knox-zt-core", "Knox Zero Trust core", DpcModuleGroup.CORE, KnoxZtRecoveryPlanner::class.java, KnoxZtManagerActivity::class.java, requirements = CapabilityRequirements(requiresSamsung = true, requiresKnox = true), tags = setOf("samsung_knox")),
        DpcModuleDescriptor(":knox-zt-android", "Knox Zero Trust Android", DpcModuleGroup.ANDROID, KnoxZtActivitySupport::class.java, KnoxZtManagerActivity::class.java, requirements = CapabilityRequirements(requiresSamsung = true, requiresKnox = true), tags = setOf("samsung_knox")),

        DpcModuleDescriptor(":scenario-core", "Scenario recorder core", DpcModuleGroup.CORE, ScenarioEventType::class.java, ScenarioLabActivity::class.java, requirements = CapabilityRequirements(visibility = VisibilityClass.LAB), tags = setOf("lab")),
        DpcModuleDescriptor(":scenario-android", "Scenario recorder Android", DpcModuleGroup.ANDROID, AndroidScenarioBridge::class.java, ScenarioLabActivity::class.java, requirements = CapabilityRequirements(visibility = VisibilityClass.LAB), tags = setOf("lab")),

        DpcModuleDescriptor(":nfc-lab-core", "NFC lab core", DpcModuleGroup.LAB, NfcTraceOwnership::class.java, NfcLabActivity::class.java, requirements = CapabilityRequirements(visibility = VisibilityClass.LAB), tags = setOf("lab")),
        DpcModuleDescriptor(":nfc-lab-android", "NFC lab Android", DpcModuleGroup.LAB, NfcTagInspector::class.java, NfcLabActivity::class.java, requirements = CapabilityRequirements(visibility = VisibilityClass.LAB), tags = setOf("lab")),
        DpcModuleDescriptor(":oem-internals-core", "OEM Internals catalog core", DpcModuleGroup.LAB, OemCircuitBreaker::class.java, requirements = CapabilityRequirements(visibility = VisibilityClass.LAB), tags = setOf("lab", "oem_internal")),
        DpcModuleDescriptor(":oem-internals-android", "OEM Internals Lab Android", DpcModuleGroup.LAB, OemLabProbeService::class.java, requirements = CapabilityRequirements(visibility = VisibilityClass.LAB), tags = setOf("lab", "oem_internal")),
        DpcModuleDescriptor(":lab-tools", "Lab route tools", DpcModuleGroup.LAB, LabHookActivityRouteExecutor::class.java, requirements = CapabilityRequirements(visibility = VisibilityClass.LAB), tags = setOf("lab"))
    )

    init {
        require(modules.map { it.id }.distinct().size == modules.size) { "Duplicate DPC module id" }
    }
}
