package io.dpcaio.installer

private fun assertEquals(expected: Any?, actual: Any?, message: String) {
    if (expected != actual) error("$message: expected=$expected actual=$actual")
}

fun main() {
    val classifier = InstallSourceClassifier(playPackage = "com.android.vending")

    val realPlay = classifier.classify(
        InstallSourceSnapshot("com.android.vending", "com.android.vending", PackageSource.STORE, "com.android.vending")
    )
    assertEquals(InstallSourceClass.REAL_PLAY, realPlay, "Play must be real only when initiating and installing package are Play")

    val recordOnly = classifier.classify(
        InstallSourceSnapshot("io.dpcaio.app", "com.android.vending", PackageSource.STORE, null)
    )
    assertEquals(InstallSourceClass.INSTALLER_RECORD_PLAY, recordOnly,
        "installer-of-record Play with another initiator must not be reported as genuine Play")

    val metadataOnly = classifier.classify(
        InstallSourceSnapshot("io.dpcaio.app", "io.dpcaio.app", PackageSource.STORE, null)
    )
    assertEquals(InstallSourceClass.STORE_METADATA_ONLY, metadataOnly,
        "store source metadata alone is not a genuine Play install")

    val local = classifier.classify(
        InstallSourceSnapshot("io.dpcaio.app", "io.dpcaio.app", PackageSource.LOCAL_FILE, null)
    )
    assertEquals(InstallSourceClass.OTHER, local, "local AIO install should remain OTHER")

    println("InstallSourceClassifierTest: PASS")
}
