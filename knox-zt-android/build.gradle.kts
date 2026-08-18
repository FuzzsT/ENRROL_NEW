plugins { alias(libs.plugins.android.library) }
android {
    namespace = "io.dpcaio.knoxzt.android"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
}
dependencies {
    implementation(project(":knox-zt-core"))
    implementation(project(":installer-core"))
    implementation(project(":installer-android"))
    implementation(project(":activity-launcher"))
    implementation(project(":activity-android"))
    implementation(project(":shizuku-adapter"))
}
