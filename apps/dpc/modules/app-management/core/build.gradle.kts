plugins { alias(libs.plugins.kotlin.jvm) }
kotlin { jvmToolchain(21) }
dependencies {
    implementation(project(":enterprise-protection"))
    implementation(project(":policy-core"))
}
