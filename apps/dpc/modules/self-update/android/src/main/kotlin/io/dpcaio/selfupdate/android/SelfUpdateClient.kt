package io.dpcaio.selfupdate.android

import io.dpcaio.selfupdate.SelfUpdateManifest
import io.dpcaio.selfupdate.UpdateChannel
import org.json.JSONObject
import java.io.File
import java.io.IOException
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
            ensureSuccess(connection)
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            parseManifest(JSONObject(body))
        } finally {
            connection.disconnect()
        }
    }

    fun downloadApk(
        url: String,
        destination: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> },
    ): Long {
        val connection = openHttps(url)
        try {
            ensureSuccess(connection)
            destination.parentFile?.mkdirs()
            val expectedLength = connection.contentLengthLong.takeIf { it >= 0L }
            var downloaded = 0L
            connection.inputStream.use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
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

    private fun ensureSuccess(connection: HttpURLConnection) {
        if (connection.responseCode !in 200..299) {
            throw IOException("HTTP ${connection.responseCode}")
        }
    }

    private companion object {
        const val HTTPS_PREFIX = "https://"
        const val MAX_REDIRECTS = 4
    }
}
