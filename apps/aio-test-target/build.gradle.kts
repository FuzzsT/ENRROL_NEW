plugins { alias(libs.plugins.android.application) }

android {
    namespace = "io.dpcaio.testtarget"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        applicationId = "io.dpcaio.testtarget"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
