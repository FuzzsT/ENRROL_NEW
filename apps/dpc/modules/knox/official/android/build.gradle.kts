plugins { alias(libs.plugins.android.library) }

val knoxSdkJar = providers.environmentVariable("KNOX_SDK_JAR")

android {
    namespace = "io.dpcaio.knox.official.android"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":knox-official-core"))
    implementation(project(":core-model"))
    implementation(project(":knox-license-core"))
    knoxSdkJar.orNull?.takeIf { it.isNotBlank() }?.let { path ->
        compileOnly(files(path))
    }
}
