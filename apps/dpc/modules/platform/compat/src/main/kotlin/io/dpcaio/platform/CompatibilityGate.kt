package io.dpcaio.platform

class CompatibilityGate {
    private val supportedAbis = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
    private val supportedPageSizes = setOf(4096, 16384)

    fun evaluate(profile: PlatformProfile): CompatibilityResult {
        val findings = listOf(
            CompatibilityFinding(
                code = "SUPPORTED_API",
                passed = profile.apiLevel in 29..37,
                detail = "api=${profile.apiLevel}; supported=29..37"
            ),
            CompatibilityFinding(
                code = if (profile.abi in supportedAbis) "SUPPORTED_ABI" else "UNSUPPORTED_ABI",
                passed = profile.abi in supportedAbis,
                detail = "abi=${profile.abi}"
            ),
            CompatibilityFinding(
                code = if (profile.pageSize in supportedPageSizes) "SUPPORTED_PAGE_SIZE" else "UNSUPPORTED_PAGE_SIZE",
                passed = profile.pageSize in supportedPageSizes,
                detail = "pageSize=${profile.pageSize}"
            )
        )
        return CompatibilityResult(findings.all { it.passed }, findings)
    }
}
