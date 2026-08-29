package io.dpcaio.samsung.firmware

enum class SamsungFirmwarePackageClass {
    REFERENCE_FIRMWARE_APP,
    CARRIER_PROVISIONING_AGENT,
    CARRIER_STOREFRONT,
    SAMSUNG_CONNECTIVITY_OVERLAY,
    OEM_PRELOAD_MANAGER,
}

data class SamsungFirmwarePackageEvidence(
    val role: String,
    val packageClass: SamsungFirmwarePackageClass,
)

data class SamsungFirmwarePackageProbe(
    val packageName: String,
    val role: String,
    val packageClass: SamsungFirmwarePackageClass,
    val installed: Boolean,
    val enabled: Boolean?,
    val systemApp: Boolean?,
)

data class SamsungFirmwareProfile(
    val samsungDevice: Boolean,
    val salesCode: String?,
    val carrierId: String?,
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

    val carrierProvisioningPresent: Boolean
        get() = packages.any {
            it.installed && it.packageClass == SamsungFirmwarePackageClass.CARRIER_PROVISIONING_AGENT
        }

    val carrierPackageCount: Int
        get() = packages.count {
            it.installed && it.packageClass in setOf(
                SamsungFirmwarePackageClass.CARRIER_PROVISIONING_AGENT,
                SamsungFirmwarePackageClass.CARRIER_STOREFRONT,
            )
        }

    val connectivityOverlayPresent: Boolean
        get() = packages.any {
            it.installed && it.packageClass == SamsungFirmwarePackageClass.SAMSUNG_CONNECTIVITY_OVERLAY
        }
}

object SamsungFirmwareEvidenceCatalog {
    /**
     * Package identities observed in supplied Samsung/OXM and carrier APK material.
     * Presence is evidence only; no package is assumed to exist on every Samsung SKU/CSC.
     * Google platform packages such as Trichrome/Mainline AdServices are intentionally excluded.
     */
    val packageEvidence: Map<String, SamsungFirmwarePackageEvidence> = linkedMapOf(
        "com.sec.android.usermanual" to SamsungFirmwarePackageEvidence(
            role = "Samsung User Manual",
            packageClass = SamsungFirmwarePackageClass.REFERENCE_FIRMWARE_APP,
        ),
        "com.swiftkey.swiftkeyconfigurator" to SamsungFirmwarePackageEvidence(
            role = "SwiftKey factory configurator",
            packageClass = SamsungFirmwarePackageClass.REFERENCE_FIRMWARE_APP,
        ),
        "com.touchtype.swiftkey" to SamsungFirmwarePackageEvidence(
            role = "SwiftKey IME",
            packageClass = SamsungFirmwarePackageClass.REFERENCE_FIRMWARE_APP,
        ),
        "com.amazon.appmanager" to SamsungFirmwarePackageEvidence(
            role = "Amazon preload/app manager",
            packageClass = SamsungFirmwarePackageClass.OEM_PRELOAD_MANAGER,
        ),
        "com.dti.tim" to SamsungFirmwarePackageEvidence(
            role = "Digital Turbine Ignite — TIM Samsung agent",
            packageClass = SamsungFirmwarePackageClass.CARRIER_PROVISIONING_AGENT,
        ),
        "com.dti.telefonica" to SamsungFirmwarePackageEvidence(
            role = "Digital Turbine Ignite — Telefonica Samsung agent",
            packageClass = SamsungFirmwarePackageClass.CARRIER_PROVISIONING_AGENT,
        ),
        "com.dti.bouyguestelecom" to SamsungFirmwarePackageEvidence(
            role = "Digital Turbine Ignite — Bouygues Samsung agent",
            packageClass = SamsungFirmwarePackageClass.CARRIER_PROVISIONING_AGENT,
        ),
        "com.dti.aone" to SamsungFirmwarePackageEvidence(
            role = "Digital Turbine Ignite — A1 Samsung agent",
            packageClass = SamsungFirmwarePackageClass.CARRIER_PROVISIONING_AGENT,
        ),
        "de.telekom.tsc" to SamsungFirmwarePackageEvidence(
            role = "Telekom AppEnabler / carrier preload manager",
            packageClass = SamsungFirmwarePackageClass.CARRIER_PROVISIONING_AGENT,
        ),
        "com.sfr.android.sfrjeux.samsung" to SamsungFirmwarePackageEvidence(
            role = "SFR Jeux Samsung carrier storefront",
            packageClass = SamsungFirmwarePackageClass.CARRIER_STOREFRONT,
        ),
        "com.altice.android.myapps.samsung" to SamsungFirmwarePackageEvidence(
            role = "Altice MyApps Samsung carrier storefront",
            packageClass = SamsungFirmwarePackageClass.CARRIER_STOREFRONT,
        ),
        "com.samsung.android.ConnectivityUxOverlay" to SamsungFirmwarePackageEvidence(
            role = "Samsung connectivity resources overlay",
            packageClass = SamsungFirmwarePackageClass.SAMSUNG_CONNECTIVITY_OVERLAY,
        ),
    )

    val salesCodePropertyKeys: List<String> = listOf(
        "ro.csc.sales_code",
        "ro.boot.sales_code",
    )

    val carrierIdPropertyKeys: List<String> = listOf(
        "ro.boot.carrierid",
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
