plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.dpcaio.permission.android"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":permission-manager"))
    implementation(project(":core-model"))
    implementation(project(":policy-core"))
    implementation(project(":platform-compat"))
    implementation(project(":shizuku-adapter"))
    implementation(libs.androidx.core)
}
