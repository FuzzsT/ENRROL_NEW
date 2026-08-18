plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.dpcaio.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.dpcaio.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 9
        versionName = "0.6.1-dev"
    }

    flavorDimensions += "track"
    productFlavors {
        create("enterprise") { dimension = "track" }
        create("systemPrivileged") { dimension = "track" }
        create("lab") { dimension = "track" }
        create("tst") { dimension = "track" }
        create("eng") { dimension = "track" }
    }

    sourceSets {
        getByName("tst").assets.srcDir("src/lab/assets")
        getByName("tst").java.srcDir("src/lab/java")
        getByName("eng").assets.srcDir("src/lab/assets")
        getByName("eng").java.srcDir("src/lab/java")
    }

    buildTypes {
        getByName("debug") { isMinifyEnabled = false }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
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
    implementation(project(":policy-core"))
    implementation(project(":policy-android"))
    implementation(project(":permission-manager"))
    implementation(project(":samsung-settings"))
    implementation(project(":samsung-settings-android"))
    implementation(project(":account-manager"))
    implementation(project(":account-android"))
    implementation(project(":permission-android"))
    implementation(project(":app-manager"))
    implementation(project(":app-android"))
    implementation(project(":activity-launcher"))
    implementation(project(":activity-android"))
    implementation(project(":installer-core"))
    implementation(project(":installer-android"))
    implementation(project(":delegation-core"))
    implementation(project(":dhizuku-compat"))
    implementation(project(":shizuku-adapter"))
    implementation(project(":native-diagnostics"))
    implementation(project(":network-android"))
    implementation(project(":network-control"))
    implementation(project(":knox-license-core"))
    implementation(project(":knox-license-lab"))
    implementation(project(":knox-mock-android"))
    implementation(project(":knox-zt-core"))
    implementation(project(":knox-zt-android"))
    implementation(project(":lab-tools"))
    implementation(project(":nfc-lab-android"))
    implementation(project(":nfc-lab-core"))
    implementation(project(":scenario-android"))
    implementation(project(":scenario-core"))
}

// Build-time Android Enterprise enrollment QR, modeled after Google TestDPC provisioning.
// Configure with -PdpcAioProvisioningApkUrl=https://... or DPC_AIO_PROVISIONING_APK_URL.
val provisioningApkUrl = providers.gradleProperty("dpcAioProvisioningApkUrl")
    .orElse(providers.environmentVariable("DPC_AIO_PROVISIONING_APK_URL"))
val provisioningGithubRepository = providers.environmentVariable("DPC_AIO_GITHUB_REPOSITORY")
    .orElse(providers.environmentVariable("GITHUB_REPOSITORY"))
val provisioningEnrollmentToken = providers.gradleProperty("dpcAioEnrollmentToken")
    .orElse(providers.environmentVariable("DPC_AIO_ENROLLMENT_TOKEN"))
val provisioningPolicyProfile = providers.gradleProperty("dpcAioPolicyProfile")
    .orElse(providers.environmentVariable("DPC_AIO_POLICY_PROFILE"))
    .orElse("default")

fun String.capVariant(): String = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

listOf("enterprise", "systemPrivileged", "lab", "tst", "eng").forEach { flavor ->
    listOf("debug", "release").forEach { buildType ->
        val variant = flavor.capVariant() + buildType.capVariant()
        val generateProvisioningQr = tasks.register("generate${variant}ProvisioningQr") {
            group = "provisioning"
            description = "Generate provisioning.json and provisioning-qr.png for $flavor/$buildType"
            doLast {
                val apkDir = layout.buildDirectory.dir("outputs/apk/$flavor/$buildType").get().asFile
                val apk = apkDir.walkTopDown().firstOrNull { it.isFile && it.extension == "apk" }
                    ?: throw GradleException("No APK found under $apkDir; assemble $variant first")

                val configuredUrl = provisioningApkUrl.orNull
                val repository = provisioningGithubRepository.orNull
                val apkUrl = configuredUrl ?: repository?.let {
                    "https://github.com/$it/releases/latest/download/DPC-AIO-${flavor}-${buildType}.apk"
                }
                if (apkUrl == null) {
                    logger.lifecycle(
                        "Provisioning QR skipped for $variant: set DPC_AIO_PROVISIONING_APK_URL " +
                            "or DPC_AIO_GITHUB_REPOSITORY/GITHUB_REPOSITORY"
                    )
                    return@doLast
                }

                val outDir = layout.buildDirectory.dir("outputs/provisioning/$flavor/$buildType").get().asFile
                outDir.mkdirs()
                val args = mutableListOf(
                    "python3",
                    rootProject.file("tools/provisioning/generate_provisioning.py").absolutePath,
                    "--apk", apk.absolutePath,
                    "--apk-url", apkUrl,
                    "--out-dir", outDir.absolutePath,
                    "--checksum-mode", "auto",
                    "--policy-profile", provisioningPolicyProfile.get(),
                )
                provisioningEnrollmentToken.orNull?.takeIf { it.isNotBlank() }?.let {
                    args += listOf("--enrollment-token", it)
                }
                providers.exec { commandLine(args) }.result.get().assertNormalExitValue()
                val qr = outDir.resolve("provisioning-qr.png")
                if (!qr.isFile) throw GradleException("Provisioning generator did not create $qr")
                logger.lifecycle("Provisioning QR: ${qr.absolutePath}")
            }
        }
        tasks.matching { it.name == "assemble$variant" }.configureEach {
            finalizedBy(generateProvisioningQr)
        }
    }
}
