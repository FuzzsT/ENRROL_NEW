package io.dpcaio.selfupdate.android

import io.dpcaio.selfupdate.SelfUpdateManifest
import io.dpcaio.selfupdate.UpdateChannel
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class SelfUpdateClient(
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 30_000,
) {
    fun fetchManifest(url: String): SelfUpdateManifest {
        val connection = openHttps(url)
        return try {
            ensureSuccess(connection, url, ResourceKind.MANIFEST)
            val expectedLength = connection.contentLengthLong.takeIf { it >= 0L }
            if (expectedLength != null && expectedLength > MAX_MANIFEST_BYTES) {
                throw IOException("Manifest exceeds size limit")
            }
            val body = connection.inputStream.use { input ->
                String(readLimited(input, MAX_MANIFEST_BYTES), Charsets.UTF_8)
            }
            parseManifest(JSONObject(body))
        } finally {
            connection.disconnect()
        }
    }

    fun downloadApk(
        url: String,
        destination: File,
        maxBytes: Long = MAX_APK_BYTES,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> },
    ): Long {
        require(maxBytes in 1..MAX_APK_BYTES) { "APK download limit out of range" }
        val connection = openHttps(url)
        try {
            ensureSuccess(connection, url, ResourceKind.APK)
            destination.parentFile?.mkdirs()
            val expectedLength = connection.contentLengthLong.takeIf { it >= 0L }
            if (expectedLength != null && expectedLength > maxBytes) {
                throw IOException("APK exceeds download limit")
            }
            var downloaded = 0L
            connection.inputStream.use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        if (downloaded + count > maxBytes) {
                            throw IOException("APK exceeds download limit")
                        }
                        output.write(buffer, 0, count)
                        downloaded += count
                        onProgress(downloaded, expectedLength)
                    }
                    output.flush()
                }
            }
            return downloaded
        } catch (error: Exception) {
            destination.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun parseManifest(json: JSONObject): SelfUpdateManifest = SelfUpdateManifest(
        schemaVersion = json.getInt("schemaVersion"),
        channel = when (json.getString("channel").trim().lowercase(Locale.ROOT)) {
            "stable" -> UpdateChannel.STABLE
            "continuous" -> UpdateChannel.CONTINUOUS
            else -> throw IllegalArgumentException("Unsupported update channel")
        },
        packageName = json.getString("packageName"),
        versionName = json.getString("versionName"),
        versionCode = json.getLong("versionCode"),
        apkUrl = json.getString("apkUrl"),
        apkSha256 = json.getString("apkSha256"),
        apkSize = json.optLong("apkSize", -1L).takeIf { it >= 0L },
        publishedAt = json.optString("publishedAt", ""),
        releaseNotesUrl = json.optString("releaseNotesUrl", "").takeIf { it.isNotBlank() },
    )

    private fun openHttps(url: String, redirectDepth: Int = 0): HttpURLConnection {
        require(url.startsWith(HTTPS_PREFIX, ignoreCase = true)) { "HTTPS required" }
        require(redirectDepth <= MAX_REDIRECTS) { "Too many redirects" }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMillis
            readTimeout = readTimeoutMillis
            instanceFollowRedirects = false
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json, application/vnd.android.package-archive, application/octet-stream")
            setRequestProperty("User-Agent", "DPC-AIO-SelfUpdate")
        }
        val status = connection.responseCode
        if (status in 300..399) {
            val location = connection.getHeaderField("Location") ?: throw IOException("Redirect without Location")
            val redirected = URL(URL(url), location).toString()
            connection.disconnect()
            require(redirected.startsWith(HTTPS_PREFIX, ignoreCase = true)) { "Non-HTTPS redirect rejected" }
            return openHttps(redirected, redirectDepth + 1)
        }
        return connection
    }

    private fun ensureSuccess(connection: HttpURLConnection, requestedUrl: String, kind: ResourceKind) {
        val status = connection.responseCode
        if (status in 200..299) return

        if (status == HttpURLConnection.HTTP_NOT_FOUND) {
            val channel = when {
                requestedUrl.contains("/dpc-aio-stable/", ignoreCase = true) -> "stable"
                requestedUrl.contains("/dpc-aio-continuous/", ignoreCase = true) -> "continuous"
                else -> null
            }
            val detail = when (kind) {
                ResourceKind.MANIFEST -> channel?.let {
                    "Self Update $it channel is not published yet (manifest HTTP 404)"
                } ?: "Self Update manifest was not found (HTTP 404)"
                ResourceKind.APK -> "Self Update APK asset was not found (HTTP 404)"
            }
            throw SelfUpdateHttpException(status, requestedUrl, detail)
        }

        throw SelfUpdateHttpException(
            statusCode = status,
            requestedUrl = requestedUrl,
            message = "Self Update HTTP $status",
        )
    }

    private fun readLimited(input: InputStream, maxBytes: Long): ByteArray {
        require(maxBytes > 0L) { "Read limit must be positive" }
        val output = ByteArrayOutputStream(minOf(maxBytes, 64L * 1024L).toInt())
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var downloaded = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            if (downloaded + count > maxBytes) {
                throw IOException("Response exceeds size limit")
            }
            output.write(buffer, 0, count)
            downloaded += count
        }
        return output.toByteArray()
    }

    private enum class ResourceKind {
        MANIFEST,
        APK,
    }

    class SelfUpdateHttpException(
        val statusCode: Int,
        val requestedUrl: String,
        message: String,
    ) : IOException(message)

    companion object {
        const val MAX_MANIFEST_BYTES: Long = 256L * 1024L
        const val MAX_APK_BYTES: Long = 512L * 1024L * 1024L
        private const val HTTPS_PREFIX = "https://"
        private const val MAX_REDIRECTS = 4
    }
}
