package io.dpcaio.installer

fun main() {
    val planner = ApkPlusArchivePlanner()
    val ok = planner.plan(listOf(
        ApkPlusEntry("base.apk", 100, 200),
        ApkPlusEntry("split_config.en.apk", 50, 80),
        ApkPlusEntry("apk+.json", 20, 30),
    ))
    check(ok.accepted)
    check(ok.baseApk == "base.apk")
    check(ok.splitApks == listOf("split_config.en.apk"))

    check(ApkPlusReject.PATH_TRAVERSAL in planner.plan(listOf(ApkPlusEntry("../base.apk", 1, 1))).rejections)
    check(ApkPlusReject.DUPLICATE_CANONICAL_PATH in planner.plan(listOf(ApkPlusEntry("base.apk",1,1), ApkPlusEntry("./base.apk",1,1))).rejections)
    check(ApkPlusReject.SYMLINK_ENTRY in planner.plan(listOf(ApkPlusEntry("base.apk",1,1, unixMode = 0xA000))).rejections)
    check(ApkPlusReject.COMPRESSION_RATIO_EXCEEDED in planner.plan(listOf(ApkPlusEntry("base.apk",1,1000))).rejections)
    check(ApkPlusReject.BASE_APK_MISSING in planner.plan(listOf(ApkPlusEntry("split_x.apk",1,1))).rejections)

    val meta = planner.verifyApkMetadata(
        listOf(
            ApkArtifactMetadata("base.apk", "pkg", 3, setOf("AA"), base = true),
            ApkArtifactMetadata("split_x.apk", "pkg2", 3, setOf("AA"), base = false),
        )
    )
    check(ApkPlusReject.PACKAGE_NAME_MISMATCH in meta)
    println("ApkPlusArchivePlannerTest: PASS")
}
