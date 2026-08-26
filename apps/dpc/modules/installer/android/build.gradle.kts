plugins { alias(libs.plugins.android.library) }
android {
    namespace = "io.dpcaio.installer.android"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
}
dependencies {
    implementation(project(":installer-core"))
    implementation(project(":core-model"))
    api(project(":policy-core"))
}
