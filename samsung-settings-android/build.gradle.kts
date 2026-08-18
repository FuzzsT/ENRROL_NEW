plugins { alias(libs.plugins.android.library) }
android {
    namespace = "io.dpcaio.samsung.settings.android"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
dependencies {
    implementation(project(":samsung-settings"))
    implementation(project(":shizuku-adapter"))
}
