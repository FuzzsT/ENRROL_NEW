package io.dpcaio.offline

private fun sAssert(value: Boolean, message: String) { if (!value) error(message) }

fun main() {
    val pkg = OfflinePackageEntry(
        packageName = "com.example.agent",
        versionCode = 42,
        signingCertificateSha256 = "abcd",
        files = listOf(
            OfflinePackageFile("packages/agent/base.apk", "1111", required = true),
            OfflinePackageFile("packages/agent/split_config.pl.apk", "2222", required = true)
        )
    )
    val manifest = OfflineBundleManifest(
        schemaVersion = 1,
        bundleId = "enterprise-offline-v3",
        organizationId = "example-pl-001",
        minimumDpcVersion = "1.0.0",
        minimumAndroidApi = 33,
        allowedModes = setOf("FULLY_MANAGED"),
        packages = listOf(pkg),
        requiredCapabilities = setOf(OfflineCapability.PACKAGE_INSTALL),
        policyPath = "policies/enterprise.json"
    )
    sAssert(pkg.files.size == 2, "canonical file list must be available")
    sAssert(pkg.baseFile == "packages/agent/base.apk", "base compatibility view")
    sAssert(pkg.requiredSplits == listOf("packages/agent/split_config.pl.apk"), "split compatibility view")
    sAssert(manifest.organizationId == "example-pl-001", "organization id")
    sAssert(manifest.policyPath == "policies/enterprise.json", "policy path")
    println("OfflineSchemaCompatibilityTest: PASS")
}
