package io.dpcaio.offline

private fun oAssert(value: Boolean, message: String) { if (!value) error(message) }

fun main() {
    val manifest = OfflineBundleManifest(
        schemaVersion = 1,
        bundleId = "enterprise-offline-v1",
        minimumDpcVersion = "1.0.0",
        minimumAndroidApi = 33,
        allowedModes = setOf("FULLY_MANAGED"),
        packages = listOf(
            OfflinePackageEntry(
                packageName = "com.example.agent",
                versionCode = 42,
                baseFile = "packages/agent/base.apk",
                requiredSplits = listOf("packages/agent/split_config.arm64_v8a.apk"),
                sha256 = "abc",
                signingCertificateSha256 = "def"
            )
        ),
        requiredCapabilities = setOf(OfflineCapability.PACKAGE_INSTALL, OfflineCapability.PERMISSION_CONTROL)
    )

    val ready = OfflineReadinessPlanner().evaluate(
        manifest,
        OfflineReadinessContext(
            signatureVerified = true,
            manifestHashVerified = true,
            schemaSupported = true,
            currentDpcVersion = "1.0.0",
            androidApi = 36,
            provisioningMode = "FULLY_MANAGED",
            availablePackages = setOf("com.example.agent"),
            availableCapabilities = setOf(OfflineCapability.PACKAGE_INSTALL, OfflineCapability.PERMISSION_CONTROL)
        )
    )
    oAssert(ready.status == OfflineReadinessStatus.FULL_OFFLINE_READY, "complete offline profile should be ready")

    val badSignature = OfflineReadinessPlanner().evaluate(
        manifest,
        OfflineReadinessContext(
            signatureVerified = false,
            manifestHashVerified = true,
            schemaSupported = true,
            currentDpcVersion = "1.0.0",
            androidApi = 36,
            provisioningMode = "FULLY_MANAGED",
            availablePackages = setOf("com.example.agent"),
            availableCapabilities = setOf(OfflineCapability.PACKAGE_INSTALL, OfflineCapability.PERMISSION_CONTROL)
        )
    )
    oAssert(badSignature.status == OfflineReadinessStatus.OFFLINE_PROFILE_INCOMPATIBLE, "bad signature must block")
    oAssert(badSignature.blockingCode == "OFFLINE_BUNDLE_SIGNATURE_INVALID", "signature failure should have deterministic code")


    val badSchema = OfflineReadinessPlanner().evaluate(
        manifest,
        OfflineReadinessContext(
            signatureVerified = true, manifestHashVerified = true, schemaSupported = false,
            currentDpcVersion = "1.0.0", androidApi = 36, provisioningMode = "FULLY_MANAGED",
            availablePackages = setOf("com.example.agent"),
            availableCapabilities = setOf(OfflineCapability.PACKAGE_INSTALL, OfflineCapability.PERMISSION_CONTROL)
        )
    )
    oAssert(badSchema.blockingCode == "OFFLINE_SCHEMA_UNSUPPORTED", "schema failure ordering")

    val oldDpc = OfflineReadinessPlanner().evaluate(
        manifest,
        OfflineReadinessContext(
            signatureVerified = true, manifestHashVerified = true, schemaSupported = true,
            currentDpcVersion = "0.9.0", androidApi = 36, provisioningMode = "FULLY_MANAGED",
            availablePackages = setOf("com.example.agent"),
            availableCapabilities = setOf(OfflineCapability.PACKAGE_INSTALL, OfflineCapability.PERMISSION_CONTROL)
        )
    )
    oAssert(oldDpc.blockingCode == "OFFLINE_DPC_VERSION_TOO_OLD:1.0.0", "DPC version gate")

    val oldApi = OfflineReadinessPlanner().evaluate(
        manifest,
        OfflineReadinessContext(
            signatureVerified = true, manifestHashVerified = true, schemaSupported = true,
            currentDpcVersion = "1.0.0", androidApi = 32, provisioningMode = "FULLY_MANAGED",
            availablePackages = setOf("com.example.agent"),
            availableCapabilities = setOf(OfflineCapability.PACKAGE_INSTALL, OfflineCapability.PERMISSION_CONTROL)
        )
    )
    oAssert(oldApi.blockingCode == "OFFLINE_ANDROID_API_UNSUPPORTED", "api gate")

    val wrongMode = OfflineReadinessPlanner().evaluate(
        manifest,
        OfflineReadinessContext(
            signatureVerified = true, manifestHashVerified = true, schemaSupported = true,
            currentDpcVersion = "1.0.0", androidApi = 36, provisioningMode = "MANAGED_PROFILE",
            availablePackages = setOf("com.example.agent"),
            availableCapabilities = setOf(OfflineCapability.PACKAGE_INSTALL, OfflineCapability.PERMISSION_CONTROL)
        )
    )
    oAssert(wrongMode.blockingCode == "OFFLINE_MODE_NOT_ALLOWED:MANAGED_PROFILE", "mode gate")

    val missingCapability = OfflineReadinessPlanner().evaluate(
        manifest,
        OfflineReadinessContext(
            signatureVerified = true, manifestHashVerified = true, schemaSupported = true,
            currentDpcVersion = "1.0.0", androidApi = 36, provisioningMode = "FULLY_MANAGED",
            availablePackages = setOf("com.example.agent"),
            availableCapabilities = setOf(OfflineCapability.PACKAGE_INSTALL)
        )
    )
    oAssert(missingCapability.blockingCode == "OFFLINE_CAPABILITY_MISSING:PERMISSION_CONTROL", "capability gate")

    val missingPackage = OfflineReadinessPlanner().evaluate(
        manifest,
        OfflineReadinessContext(
            signatureVerified = true,
            manifestHashVerified = true,
            schemaSupported = true,
            currentDpcVersion = "1.0.0",
            androidApi = 36,
            provisioningMode = "FULLY_MANAGED",
            availablePackages = emptySet(),
            availableCapabilities = setOf(OfflineCapability.PACKAGE_INSTALL, OfflineCapability.PERMISSION_CONTROL)
        )
    )
    oAssert(missingPackage.status == OfflineReadinessStatus.OFFLINE_PROFILE_INCOMPATIBLE, "missing package must block")
    oAssert(missingPackage.blockingCode == "OFFLINE_PACKAGE_MISSING:com.example.agent", "missing package code")

    println("OfflineReadinessPlannerTest: PASS")
}
