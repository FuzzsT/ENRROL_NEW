plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.dpcaio.shizuku"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    buildFeatures { aidl = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":core-model"))
    implementation(project(":core-execution"))
    implementation(project(":platform-compat"))
    implementation(project(":delegation-core"))
    implementation(project(":activity-launcher"))
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
}
