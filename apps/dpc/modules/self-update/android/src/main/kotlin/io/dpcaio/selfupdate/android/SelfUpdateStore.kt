package io.dpcaio.selfupdate.android

import android.content.Context
import io.dpcaio.selfupdate.SelfUpdateManifest
import io.dpcaio.selfupdate.UpdateChannel
import org.json.JSONObject
import java.io.File

data class StoredSelfUpdateCandidate(
    val manifest: SelfUpdateManifest,
    val apkFile: File,
    val verified: Boolean,
    val savedAtMillis: Long,
)

class SelfUpdateStore(context: Context) {
    private val rootDir = File(context.applicationContext.filesDir, "self-update")
    private val metadataFile = File(rootDir, "candidate.json")
    val candidateApkFile: File get() = File(rootDir, "candidate.apk")

    fun newDownloadFile(): File {
        rootDir.mkdirs()
        return File(rootDir, "candidate.apk.part").also { it.delete() }
    }

    fun promoteDownload(downloadedFile: File): File {
        require(downloadedFile.isFile) { "Downloaded update missing" }
        rootDir.mkdirs()
        candidateApkFile.delete()
        if (!downloadedFile.renameTo(candidateApkFile)) {
            downloadedFile.copyTo(candidateApkFile, overwrite = true)
            downloadedFile.delete()
        }
        return candidateApkFile
    }

    fun saveCandidate(manifest: SelfUpdateManifest, verified: Boolean) {
        rootDir.mkdirs()
        val savedAt = System.currentTimeMillis()
        val json = JSONObject()
            .put("schemaVersion", manifest.schemaVersion)
            .put("channel", manifest.channel.name.lowercase())
            .put("packageName", manifest.packageName)
            .put("versionName", manifest.versionName)
            .put("versionCode", manifest.versionCode)
            .put("apkUrl", manifest.apkUrl)
            .put("apkSha256", manifest.apkSha256)
            .put("apkSize", manifest.apkSize ?: JSONObject.NULL)
            .put("publishedAt", manifest.publishedAt)
            .put("releaseNotesUrl", manifest.releaseNotesUrl ?: JSONObject.NULL)
            .put("verified", verified)
            .put("savedAtMillis", savedAt)
        metadataFile.writeText(json.toString(), Charsets.UTF_8)
    }

    fun loadCandidate(): StoredSelfUpdateCandidate? = runCatching {
        if (!metadataFile.isFile || !candidateApkFile.isFile) return@runCatching null
        val json = JSONObject(metadataFile.readText(Charsets.UTF_8))
        val manifest = SelfUpdateManifest(
            schemaVersion = json.getInt("schemaVersion"),
            channel = UpdateChannel.valueOf(json.getString("channel").uppercase()),
            packageName = json.getString("packageName"),
            versionName = json.getString("versionName"),
            versionCode = json.getLong("versionCode"),
            apkUrl = json.getString("apkUrl"),
            apkSha256 = json.getString("apkSha256"),
            apkSize = json.optLong("apkSize", -1L).takeIf { it >= 0L },
            publishedAt = json.optString("publishedAt", ""),
            releaseNotesUrl = json.optString("releaseNotesUrl", "").takeIf { it.isNotBlank() },
        )
        StoredSelfUpdateCandidate(
            manifest = manifest,
            apkFile = candidateApkFile,
            verified = json.optBoolean("verified", false),
            savedAtMillis = json.optLong("savedAtMillis", 0L),
        )
    }.getOrNull()

    fun clearCandidate() {
        if (rootDir.exists()) rootDir.deleteRecursively()
    }
}
