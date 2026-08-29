package io.dpcaio.samsung.firmware

data class SamsungFirmwarePackageProbe(
    val packageName: String,
    val role: String,
    val installed: Boolean,
    val enabled: Boolean?,
    val systemApp: Boolean?,
)

data class SamsungFirmwareProfile(
    val samsungDevice: Boolean,
    val salesCode: String?,
    val multiCsc: String?,
    val countryIso: String?,
    val omcPath: String?,
    val omcEtcPath: String?,
    val omcBuildVersion: String?,
    val buildPda: String?,
    val buildIncremental: String?,
    val propertyAccessAvailable: Boolean,
    val packages: List<SamsungFirmwarePackageProbe>,
) {
    val observedPackageCount: Int
        get() = packages.count { it.installed }
}

object SamsungFirmwareEvidenceCatalog {
    /**
     * Package identities observed in the supplied SM-S901B/OXM firmware material.
     * Presence is treated as evidence only; no package is assumed to exist on every Samsung SKU/CSC.
     */
    val packageRoles: Map<String, String> = linkedMapOf(
        "com.sec.android.usermanual" to "Samsung User Manual",
        "com.swiftkey.swiftkeyconfigurator" to "SwiftKey factory configurator",
        "com.touchtype.swiftkey" to "SwiftKey IME",
        "com.amazon.appmanager" to "Amazon preload/app manager",
    )

    val salesCodePropertyKeys: List<String> = listOf(
        "ro.csc.sales_code",
        "ro.boot.sales_code",
    )

    val countryIsoPropertyKeys: List<String> = listOf(
        "ro.csc.countryiso_code",
        "ro.csc.country_code",
    )

    val omcPathPropertyKeys: List<String> = listOf(
        "persist.sys.omc_path",
        "persist.sys.omc_etcpath",
    )

    const val omcBuildVersionProperty: String = "ro.omc.build.version"
    const val buildPdaProperty: String = "ro.build.PDA"
    const val buildIncrementalProperty: String = "ro.build.version.incremental"
    const val multiCscProperty: String = "ro.boot.omc"
}
