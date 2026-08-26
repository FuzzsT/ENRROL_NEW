package io.dpcaio.platform

data class PlatformProfile(
    val apiLevel: Int,
    val abi: String,
    val is64Bit: Boolean,
    val pageSize: Int
)

data class CompatibilityFinding(
    val code: String,
    val passed: Boolean,
    val detail: String
)

data class CompatibilityResult(
    val supported: Boolean,
    val findings: List<CompatibilityFinding>
)
