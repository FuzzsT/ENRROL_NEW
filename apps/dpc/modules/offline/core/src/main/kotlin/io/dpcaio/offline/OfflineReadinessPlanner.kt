package io.dpcaio.offline

class OfflineReadinessPlanner {

    fun evaluate(manifest: OfflineBundleManifest, input: OfflineReadinessInput): OfflineReadiness {
        if (!input.signatureVerified) return blocked("OFFLINE_BUNDLE_SIGNATURE_INVALID")
        if (!input.schemaSupported) return blocked("OFFLINE_SCHEMA_UNSUPPORTED")
        if (compareVersions(input.currentDpcVersion, manifest.minimumDpcVersion) < 0) {
            return blocked("OFFLINE_DPC_VERSION_TOO_OLD:${manifest.minimumDpcVersion}")
        }
        if (input.currentAndroidApi < manifest.minimumAndroidApi) return blocked("OFFLINE_ANDROID_API_UNSUPPORTED")
        if (input.provisioningMode !in manifest.allowedModes) return blocked("OFFLINE_MODE_NOT_ALLOWED:${input.provisioningMode}")
        for (pkg in manifest.packages.sortedBy { it.packageName }) {
            for (file in pkg.files.filter { it.required }) {
                if (file.path !in input.availablePackageFiles) {
                    val code = if (file.path == pkg.baseFile) "OFFLINE_PACKAGE_MISSING:${pkg.packageName}" else "OFFLINE_SPLIT_MISSING:${file.path}"
                    return blocked(code)
                }
            }
        }
        manifest.requiredCapabilities.sortedBy { it.name }.firstOrNull { it.name !in input.availableCapabilities }?.let {
            return blocked("OFFLINE_CAPABILITY_MISSING:${it.name}")
        }
        return OfflineReadiness(OfflineReadinessStatus.FULL_OFFLINE_READY)
    }
    fun evaluate(manifest: OfflineBundleManifest, context: OfflineReadinessContext): OfflineReadiness {
        if (!context.signatureVerified) return blocked("OFFLINE_BUNDLE_SIGNATURE_INVALID")
        if (!context.manifestHashVerified) return blocked("OFFLINE_MANIFEST_HASH_INVALID")
        if (!context.schemaSupported) return blocked("OFFLINE_SCHEMA_UNSUPPORTED")
        if (compareVersions(context.currentDpcVersion, manifest.minimumDpcVersion) < 0) {
            return blocked("OFFLINE_DPC_VERSION_TOO_OLD:${manifest.minimumDpcVersion}")
        }
        if (context.androidApi < manifest.minimumAndroidApi) return blocked("OFFLINE_ANDROID_API_UNSUPPORTED")
        if (context.provisioningMode !in manifest.allowedModes) return blocked("OFFLINE_MODE_NOT_ALLOWED:${context.provisioningMode}")
        manifest.packages.firstOrNull { it.packageName !in context.availablePackages }?.let {
            return blocked("OFFLINE_PACKAGE_MISSING:${it.packageName}")
        }
        manifest.requiredCapabilities.sortedBy { it.name }.firstOrNull { it !in context.availableCapabilities }?.let {
            return blocked("OFFLINE_CAPABILITY_MISSING:${it.name}")
        }
        return OfflineReadiness(OfflineReadinessStatus.FULL_OFFLINE_READY)
    }

    private fun blocked(code: String) = OfflineReadiness(OfflineReadinessStatus.OFFLINE_PROFILE_INCOMPATIBLE, code)

    private fun compareVersions(left: String, right: String): Int {
        val a = left.split('.').map { it.toIntOrNull() ?: 0 }
        val b = right.split('.').map { it.toIntOrNull() ?: 0 }
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val av = a.getOrElse(i) { 0 }
            val bv = b.getOrElse(i) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }
}
