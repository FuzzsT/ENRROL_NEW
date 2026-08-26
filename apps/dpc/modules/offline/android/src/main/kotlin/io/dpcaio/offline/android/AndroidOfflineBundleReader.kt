package io.dpcaio.offline.android

import android.content.Context
import android.content.pm.PackageManager
import io.dpcaio.offline.OfflineBundleManifest
import io.dpcaio.offline.OfflineBundleVerifier
import io.dpcaio.offline.OfflineCapability
import io.dpcaio.offline.OfflinePackageEntry
import io.dpcaio.offline.OfflinePackageFile
import io.dpcaio.offline.OfflinePackageIssue
import io.dpcaio.offline.OfflinePackageIssueCode
import io.dpcaio.offline.OfflinePackagePlan
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.ZipFile

data class AndroidOfflineBundleInspection(
    val manifest: OfflineBundleManifest?,
    val signatureVerified: Boolean,
    val packagePlan: OfflinePackagePlan,
    val signingIdentityIssues: List<String>,
    val detail: String
) {
    val ready: Boolean get() = signatureVerified && manifest != null && packagePlan.ready && signingIdentityIssues.isEmpty()
}

class AndroidOfflineBundleReader(context: Context) {
    private val appContext = context.applicationContext
    private val pm = appContext.packageManager
    private val verifier = OfflineBundleVerifier()

    fun inspect(bundleFile: File, publicKeyX509Base64: String): AndroidOfflineBundleInspection {
        if (!bundleFile.isFile) return failed("BUNDLE_NOT_FOUND")
        return runCatching {
            ZipFile(bundleFile).use { zip ->
                val manifestEntry = zip.getEntry("manifest.json") ?: return failed("MANIFEST_MISSING")
                val signatureEntry = zip.getEntry("manifest.sig") ?: return failed("SIGNATURE_MISSING")
                val manifestBytes = zip.getInputStream(manifestEntry).use { it.readBytes() }
                val signatureRaw = zip.getInputStream(signatureEntry).use { it.readBytes() }
                val signature = decodeSignature(signatureRaw)
                val signatureResult = verifier.verifyManifest(manifestBytes, signature, publicKeyX509Base64)
                if (!signatureResult.verified) return AndroidOfflineBundleInspection(null, false, OfflinePackagePlan(false, emptyList(), emptyList()), emptyList(), signatureResult.detail)

                val manifest = parseManifest(JSONObject(manifestBytes.toString(Charsets.UTF_8)))
                val packageIssues = mutableListOf<OfflinePackageIssue>()
                val verifiedFiles = mutableListOf<String>()
                for (pkg in manifest.packages) {
                    for (file in pkg.files) {
                        val entry = zip.getEntry(file.path)
                        if (entry == null) {
                            if (file.required) packageIssues += OfflinePackageIssue(OfflinePackageIssueCode.MISSING_FILE, pkg.packageName, file.path, "required file missing")
                            continue
                        }
                        val actual = zip.getInputStream(entry).use(::sha256Stream)
                        if (!actual.equals(file.sha256, ignoreCase = true)) {
                            packageIssues += OfflinePackageIssue(OfflinePackageIssueCode.HASH_MISMATCH, pkg.packageName, file.path, "expected=${file.sha256} actual=$actual")
                        } else verifiedFiles += file.path
                    }
                }
                val plan = OfflinePackagePlan(packageIssues.isEmpty(), verifiedFiles.sorted(), packageIssues)
                val identityIssues = if (plan.ready) verifySigningIdentities(zip, manifest) else emptyList()
                AndroidOfflineBundleInspection(manifest, true, plan, identityIssues, if (plan.ready && identityIssues.isEmpty()) "BUNDLE_VERIFIED" else "PACKAGE_VERIFICATION_FAILED")
            }
        }.getOrElse { failed("BUNDLE_PARSE_ERROR:${it.javaClass.simpleName}:${it.message}") }
    }

    private fun parseManifest(json: JSONObject): OfflineBundleManifest {
        val packages = buildList {
            val array = json.optJSONArray("packages")
            if (array != null) for (i in 0 until array.length()) {
                val p = array.getJSONObject(i)
                val files = buildList {
                    val fa = p.optJSONArray("files")
                    if (fa != null) for (j in 0 until fa.length()) {
                        val f = fa.getJSONObject(j)
                        add(OfflinePackageFile(f.getString("path"), f.getString("sha256"), f.optBoolean("required", true)))
                    }
                }
                add(OfflinePackageEntry(
                    packageName = p.getString("packageName"),
                    versionCode = p.getLong("versionCode"),
                    signingCertificateSha256 = p.getString("signingCertificateSha256"),
                    files = files
                ))
            }
        }
        fun stringSet(name: String): Set<String> = buildSet {
            val a = json.optJSONArray(name) ?: return@buildSet
            for (i in 0 until a.length()) add(a.getString(i))
        }
        return OfflineBundleManifest(
            schemaVersion = json.getInt("schemaVersion"),
            bundleId = json.getString("bundleId"),
            organizationId = json.optString("organizationId", ""),
            minimumDpcVersion = json.optString("minimumDpcVersion", "1.0.0"),
            minimumAndroidApi = json.optInt("minimumAndroidApi", 33),
            allowedModes = stringSet("allowedModes"),
            packages = packages,
            requiredCapabilities = stringSet("requiredCapabilities").mapNotNull { runCatching { OfflineCapability.valueOf(it) }.getOrNull() }.toSet(),
            policyPath = json.optString("policy", "").takeIf { it.isNotBlank() }
        )
    }

    private fun verifySigningIdentities(zip: ZipFile, manifest: OfflineBundleManifest): List<String> {
        val issues = mutableListOf<String>()
        val verifyDir = File(appContext.cacheDir, "offline-apk-verify").apply { mkdirs() }
        for (pkg in manifest.packages) {
            val base = pkg.files.firstOrNull { it.path.endsWith("/base.apk") || it.path == "base.apk" } ?: run {
                issues += "${pkg.packageName}:BASE_APK_MISSING"
                continue
            }
            val entry = zip.getEntry(base.path) ?: continue
            val temp = File(verifyDir, "${pkg.packageName.replace('.', '_')}-${pkg.versionCode}.apk")
            try {
                zip.getInputStream(entry).use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
                @Suppress("DEPRECATION")
                val info = pm.getPackageArchiveInfo(temp.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
                if (info == null) {
                    issues += "${pkg.packageName}:APK_PARSE_FAILED"
                    continue
                }
                if (info.packageName != pkg.packageName) issues += "${pkg.packageName}:PACKAGE_NAME_MISMATCH:${info.packageName}"
                if (info.longVersionCode != pkg.versionCode) issues += "${pkg.packageName}:VERSION_MISMATCH:${info.longVersionCode}"
                val signerDigests = info.signingInfo?.apkContentsSigners.orEmpty().map { cert -> sha256(cert.toByteArray()) }.toSet()
                if (pkg.signingCertificateSha256.isNotBlank() && signerDigests.none { it.equals(pkg.signingCertificateSha256, ignoreCase = true) }) {
                    issues += "${pkg.packageName}:SIGNING_CERTIFICATE_MISMATCH"
                }
            } finally {
                temp.delete()
            }
        }
        return issues
    }

    private fun decodeSignature(bytes: ByteArray): ByteArray {
        val text = bytes.toString(Charsets.US_ASCII).trim()
        return runCatching { Base64.getDecoder().decode(text) }.getOrElse { bytes }
    }

    private fun sha256Stream(input: java.io.InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n <= 0) break
            md.update(buffer, 0, n)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun failed(detail: String) = AndroidOfflineBundleInspection(null, false, OfflinePackagePlan(false, emptyList(), emptyList()), emptyList(), detail)
}
