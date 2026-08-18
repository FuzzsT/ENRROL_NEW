plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":policy-core"))
    implementation(project(":core-model"))
    implementation(project(":core-execution"))
}
