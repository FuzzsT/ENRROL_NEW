plugins { alias(libs.plugins.android.library) }
android {
    namespace = "io.dpcaio.appmanager.android"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
}
dependencies {
    implementation(project(":app-manager"))
    implementation(project(":policy-core"))
}
