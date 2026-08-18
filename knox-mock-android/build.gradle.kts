plugins { alias(libs.plugins.android.library) }
android {
    namespace = "io.dpcaio.knox.mock.android"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    buildFeatures { aidl = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
}
dependencies {
    implementation(project(":knox-mock-core"))
    implementation(project(":policy-core"))
    implementation(project(":policy-android"))
}
