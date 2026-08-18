package io.dpcaio.installer

class InstallSourceClassifier(
    private val playPackage: String = "com.android.vending"
) {
    fun classify(source: InstallSourceSnapshot): InstallSourceClass {
        if (source.initiatingPackageName == playPackage && source.installingPackageName == playPackage) {
            return InstallSourceClass.REAL_PLAY
        }
        if (source.installingPackageName == playPackage) {
            return InstallSourceClass.INSTALLER_RECORD_PLAY
        }
        if (source.packageSource == PackageSource.STORE) {
            return InstallSourceClass.STORE_METADATA_ONLY
        }
        return InstallSourceClass.OTHER
    }
}
