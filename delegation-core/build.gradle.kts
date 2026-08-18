plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.dpcaio.delegation"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":core-model"))
    implementation(project(":core-execution"))
    implementation(project(":platform-compat"))
}

dependencies {
    implementation(project(":policy-core"))
    implementation(project(":permission-manager"))
}
