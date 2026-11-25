plugins {
    alias(libs.plugins.android.application)
}

val enrollmentSigningPublicKey = providers.gradleProperty("dpcAioEnrollmentSigningPublicKey")
    .orElse(providers.environmentVariable("DPC_AIO_ENROLLMENT_SIGNING_PUBLIC_KEY"))
    .orElse("")
val enrollmentEndpoint = providers.gradleProperty("dpcAioEnrollmentEndpoint")
    .orElse(providers.environmentVariable("DPC_AIO_ENROLLMENT_ENDPOINT"))
    .orElse("")
val offlineSigningPublicKey = providers.gradleProperty("dpcAioOfflineSigningPublicKey")
    .orElse(providers.environmentVariable("DPC_AIO_OFFLINE_SIGNING_PUBLIC_KEY"))
    .orElse(enrollmentSigningPublicKey)

val releaseKeystorePath = providers.environmentVariable("DPC_AIO_RELEASE_KEYSTORE_PATH")
val releaseStorePassword = providers.environmentVariable("DPC_AIO_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = providers.environmentVariable("DPC_AIO_RELEASE_KEY_ALIAS")
val releaseKeyPassword = providers.environmentVariable("DPC_AIO_RELEASE_KEY_PASSWORD")
// CI receives DPC_AIO_RELEASE_KEYSTORE_B64 and decodes it only into runner-temporary storage.
fun buildConfigString(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "io.dpcaio.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.dpcaio.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 26
        providers.environmentVariable("DPC_AIO_CI_VERSION_CODE").orNull?.let { raw ->
            val parsed = raw.toIntOrNull()
                ?: throw GradleException("Invalid DPC_AIO_CI_VERSION_CODE")
            require(parsed in 26..2100000000) { "CI version code outside allowed range" }
            versionCode = parsed
        }
        versionName = "1.1.4"
        buildConfigField("String", "ENROLLMENT_SIGNING_PUBLIC_KEY", buildConfigString(enrollmentSigningPublicKey.get()))
        buildConfigField("String", "OFFLINE_SIGNING_PUBLIC_KEY", buildConfigString(offlineSigningPublicKey.get()))
        val v2Build = providers.environmentVariable("DPC_AIO_CONTINUOUS_V2_BUILD").orNull == "true"
        buildConfigField("boolean", "CONTINUOUS_V2_BUILD", v2Build.toString())
    }

    buildFeatures {
        buildConfig = true
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

    signingConfigs {
        create("enterpriseRelease") {
            val path = releaseKeystorePath.orNull
            if (!path.isNullOrBlank() && releaseStorePassword.isPresent && releaseKeyAlias.isPresent && releaseKeyPassword.isPresent) {
                storeFile = file(path)
                storePassword = releaseStorePassword.get()
                keyAlias = releaseKeyAlias.get()
                keyPassword = releaseKeyPassword.get()
            }
        }
    }

    buildTypes {
        getByName("debug") { isMinifyEnabled = false }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("enterpriseRelease")
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
    implementation(project(":enterprise-protection"))
    implementation(project(":platform-compat"))
    implementation(project(":policy-core"))
    implementation(project(":policy-android"))
    implementation(project(":permission-manager"))
    implementation(project(":offline-core"))
    implementation(project(":offline-android"))
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
    implementation(project(":self-update-core"))
    implementation(project(":self-update-android"))
    implementation(project(":delegation-core"))
    implementation(project(":dhizuku-compat"))
    implementation(project(":shizuku-adapter"))
    implementation(project(":file-manager-android"))
    implementation(project(":native-diagnostics"))
    implementation(project(":network-android"))
    implementation(project(":network-control"))
    implementation(project(":knox-license-core"))
    implementation(project(":knox-official-core"))
    implementation(project(":knox-official-android"))
    implementation(project(":knox-license-lab"))
    implementation(project(":knox-mock-core"))
    implementation(project(":knox-mock-android"))
    implementation(project(":knox-zt-core"))
    implementation(project(":knox-zt-android"))
    implementation(project(":lab-tools"))
    implementation(project(":nfc-lab-android"))
    implementation(project(":oem-internals-core"))
    implementation(project(":oem-internals-android"))
    implementation(project(":nfc-lab-core"))
    implementation(project(":scenario-android"))
    implementation(project(":scenario-core"))
    implementation(project(":offline-core"))
    implementation(project(":offline-android"))
    implementation(project(":engineering-core"))
}

// Build-time Android Enterprise enrollment QR, modeled after Google TestDPC provisioning.
// Configure with -PdpcAioProvisioningApkUrl=https://... or DPC_AIO_PROVISIONING_APK_URL.
val provisioningApkUrl = providers.gradleProperty("dpcAioProvisioningApkUrl")
    .orElse(providers.environmentVariable("DPC_AIO_PROVISIONING_APK_URL"))
val provisioningGithubRepository = providers.environmentVariable("DPC_AIO_GITHUB_REPOSITORY")
    .orElse(providers.environmentVariable("GITHUB_REPOSITORY"))
val provisioningContinuousReleaseTag = providers.gradleProperty("dpcAioContinuousReleaseTag")
    .orElse(providers.environmentVariable("DPC_AIO_CONTINUOUS_RELEASE_TAG"))
    .orElse("dpc-aio-continuous")
val provisioningEnrollmentToken = providers.gradleProperty("dpcAioEnrollmentToken")
    .orElse(providers.environmentVariable("DPC_AIO_ENROLLMENT_TOKEN"))
val provisioningPolicyProfile = providers.gradleProperty("dpcAioPolicyProfile")
    .orElse(providers.environmentVariable("DPC_AIO_POLICY_PROFILE"))
    .orElse("default")
val provisioningMode = providers.gradleProperty("dpcAioProvisioningMode")
    .orElse(providers.environmentVariable("DPC_AIO_PROVISIONING_MODE"))
    .orElse("work-profile")
val provisioningOfflineMode = providers.gradleProperty("dpcAioOfflineMode")
    .orElse(providers.environmentVariable("DPC_AIO_ENROLLMENT_OFFLINE_MODE"))
    .orElse("ONLINE")
val provisioningOfflineBundleId = providers.gradleProperty("dpcAioOfflineBundleId")
    .orElse(providers.environmentVariable("DPC_AIO_OFFLINE_BUNDLE_ID"))
    .orElse("")
val provisioningAllowOffline = providers.gradleProperty("dpcAioAllowOffline")
    .orElse(providers.environmentVariable("DPC_AIO_ALLOW_OFFLINE"))
    .map { it.equals("true", ignoreCase = true) || it == "1" || it.equals("yes", ignoreCase = true) }
    .orElse(false)

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
                val repositoryFallbackUrl = if (flavor == "enterprise" && buildType == "debug") {
                    repository?.let {
                        "https://github.com/$it/releases/download/${provisioningContinuousReleaseTag.get()}/DPC-AIO-enterprise-debug.apk"
                    }
                } else {
                    null
                }
                val apkUrl = configuredUrl ?: repositoryFallbackUrl
                if (apkUrl == null) {
                    logger.lifecycle(
                        "Provisioning QR skipped for $variant: set DPC_AIO_PROVISIONING_APK_URL " +
                            "or DPC_AIO_GITHUB_REPOSITORY/GITHUB_REPOSITORY"
                    )
                    return@doLast
                }

                val outDir = layout.buildDirectory.dir("outputs/provisioning/$flavor/$buildType").get().asFile
                outDir.mkdirs()
                val configuredMode = provisioningMode.get().trim().ifEmpty { "work-profile" }
                val offlineMode = provisioningOfflineMode.get().trim().ifEmpty { "ONLINE" }
                val offlineBundleId = provisioningOfflineBundleId.get().takeIf { it.isNotBlank() }
                val endpoint = enrollmentEndpoint.orNull?.trim()?.takeIf { it.isNotEmpty() }
                fun invalidConfig(reason: String): Nothing =
                    throw GradleException("PROVISIONING_QR_CONFIG_INVALID: $reason")
                if (configuredMode !in setOf("auto", "work-profile", "fully-managed")) {
                    invalidConfig("DPC_AIO_PROVISIONING_MODE must be auto, work-profile or fully-managed")
                }
                if (offlineMode !in setOf("ONLINE", "ONLINE_PREFERRED", "FULL_OFFLINE", "OFFLINE_THEN_SYNC")) {
                    invalidConfig("DPC_AIO_ENROLLMENT_OFFLINE_MODE is not supported")
                }
                if (offlineMode in setOf("FULL_OFFLINE", "OFFLINE_THEN_SYNC") && offlineBundleId == null) {
                    invalidConfig("DPC_AIO_OFFLINE_BUNDLE_ID is required for FULL_OFFLINE/OFFLINE_THEN_SYNC")
                }
                if (apkUrl != apkUrl.trim() || !apkUrl.startsWith("https://", ignoreCase = true)) {
                    invalidConfig("DPC_AIO_PROVISIONING_APK_URL must use HTTPS without surrounding whitespace")
                }
                if (endpoint != null && !endpoint.startsWith("https://", ignoreCase = true)) {
                    invalidConfig("DPC_AIO_ENROLLMENT_ENDPOINT must use HTTPS")
                }

                fun generate(mode: String, targetDir: java.io.File) {
                    targetDir.mkdirs()
                    val args = mutableListOf(
                        "python3",
                        rootProject.file("tools/provisioning/generate_provisioning.py").absolutePath,
                        "--apk", apk.absolutePath,
                        "--apk-url", apkUrl,
                        "--out-dir", targetDir.absolutePath,
                        "--checksum-mode", "auto",
                        "--policy-profile=${provisioningPolicyProfile.get().ifBlank { "default" }}",
                        "--provisioning-mode", mode,
                        "--offline-mode", offlineMode,
                    )
                    provisioningEnrollmentToken.orNull?.takeIf { it.isNotBlank() }?.let {
                        args += "--enrollment-token=$it"
                    }
                    endpoint?.let {
                        args += listOf("--enrollment-endpoint=$it", "--enrollment-source", "qr")
                    }
                    offlineBundleId?.let { args += "--offline-bundle-id=$it" }
                    if (provisioningAllowOffline.get()) {
                        args += "--allow-offline"
                    }
                    val execution = providers.exec {
                        commandLine(args)
                        isIgnoreExitValue = true
                    }
                    val exitCode = execution.result.get().exitValue
                    if (exitCode != 0) {
                        // Do not echo raw subprocess output: argparse can include token values.
                        val stderr = execution.standardError.asText.get()
                        val reason = when {
                            stderr.contains("APK not found:") -> "APK file unavailable"
                            stderr.contains("qrcode[pil] is required") -> "Missing Python QR dependencies"
                            stderr.contains("DataOverflowError") -> "QR payload exceeds capacity"
                            stderr.contains("expected one argument") -> "Missing CLI option value"
                            stderr.contains("invalid choice:") -> "Unsupported CLI option value"
                            stderr.contains("--offline-bundle-id is required") -> "Missing offline bundle ID"
                            stderr.contains("must use https://") -> "HTTPS URL required"
                            else -> "Generator rejected input or could not write outputs"
                        }
                        throw GradleException("PROVISIONING_QR_GENERATOR_FAILED: mode=$mode exit=$exitCode; $reason")
                    }
                }

                generate(configuredMode, outDir)

                fun publishExplicitMode(
                    mode: String,
                    prefix: String,
                    modeQrName: String,
                    tempName: String,
                ) {
                    val sourceDir = if (configuredMode == mode) outDir else outDir.resolve(tempName).also { generate(mode, it) }
                    mapOf(
                        "provisioning.json" to "$prefix-provisioning.json",
                        "provisioning-payload.txt" to "$prefix-provisioning-payload.txt",
                        "provisioning-metadata.json" to "$prefix-provisioning-metadata.json",
                        modeQrName to "$prefix-qr.png",
                    ).forEach { (sourceName, targetName) ->
                        val source = sourceDir.resolve(sourceName)
                        if (!source.isFile) throw GradleException("$mode provisioning generator did not create $source")
                        if (source.absolutePath != outDir.resolve(targetName).absolutePath) {
                            source.copyTo(outDir.resolve(targetName), overwrite = true)
                        }
                    }
                    if (sourceDir != outDir) sourceDir.deleteRecursively()
                }

                publishExplicitMode("work-profile", "work-profile", "work-profile-qr.png", "_work-profile")
                publishExplicitMode("fully-managed", "device-owner", "device-owner-qr.png", "_device-owner")

                val qr = outDir.resolve("provisioning-qr.png")
                val workProfileQr = outDir.resolve("work-profile-qr.png")
                val deviceOwnerQr = outDir.resolve("device-owner-qr.png")
                if (!qr.isFile) throw GradleException("Provisioning generator did not create $qr")
                if (!workProfileQr.isFile) throw GradleException("Provisioning generator did not create $workProfileQr")
                if (!deviceOwnerQr.isFile) throw GradleException("Provisioning generator did not create $deviceOwnerQr")
                logger.lifecycle("Provisioning QR: ${qr.absolutePath}")
                logger.lifecycle("Work-profile QR: ${workProfileQr.absolutePath}")
                logger.lifecycle("Device-owner QR: ${deviceOwnerQr.absolutePath}")
            }
        }
        tasks.matching { it.name == "assemble$variant" }.configureEach {
            finalizedBy(generateProvisioningQr)
        }
    }
}