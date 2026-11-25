package io.dpcaio.selfupdate

fun main() {
    val v2 = UpdateChannel.values().firstOrNull { it.name == "CONTINUOUS_V2" }
    checkNotNull(v2) { "Continuous v2 channel is missing" }
    val url = "https://github.com/FuzzsT/ENRROL_NEW/releases/download/dpc-aio-continuous-v2/DPC-AIO-enterprise-release.apk"
    val planner = SelfUpdatePlanner()
    val manifest = SelfUpdateManifest(1, v2, "io.dpcaio.app", "1.4.0", 100L, url, "a".repeat(64), 100L, "2026-11-25", null)
    fun evaluate(candidate: SelfUpdateManifest, selected: UpdateChannel = v2, installed: Long = 99L) =
        planner.evaluate("io.dpcaio.app", installed, selected, candidate)
    check(evaluate(manifest).accepted)
    check(evaluate(manifest.copy(apkUrl = url.replace("continuous-v2", "continuous"))).reason == "CONTINUOUS_V2_APK_URL_MISMATCH")
    check(evaluate(manifest.copy(apkUrl = url.replace("FuzzsT/ENRROL_NEW", "other/repo"))).reason == "CONTINUOUS_V2_APK_URL_MISMATCH")
    check(evaluate(manifest, UpdateChannel.CONTINUOUS).reason == "CHANNEL_MISMATCH")
    check(evaluate(manifest.copy(channel = UpdateChannel.CONTINUOUS), UpdateChannel.CONTINUOUS).reason == "CONTINUOUS_V2_APK_URL_MISMATCH")
    check(evaluate(manifest, installed = 100L).reason == "NO_NEWER_VERSION")
    check(evaluate(manifest.copy(apkSha256 = "bad")).reason == "INVALID_APK_SHA256")
    check(evaluate(manifest.copy(packageName = "other.app")).reason == "PACKAGE_MISMATCH")
    val legacy = manifest.copy(channel = UpdateChannel.CONTINUOUS, apkUrl = url.replace("continuous-v2", "continuous"))
    check(evaluate(legacy, UpdateChannel.CONTINUOUS).accepted)
    println("ContinuousV2PlannerTest: PASS (9 checks)")
}
