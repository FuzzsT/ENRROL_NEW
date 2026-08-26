package io.dpcaio.knoxzt.android

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class KnoxZtDownloader(private val context: Context) {
    fun download(source: KnoxZtInstallSource, timeoutMs: Int = 20_000): File {
        require(source.configured)
        val target = File(context.cacheDir, "knoxzt-framework-download.apk")
        val connection = URL(source.url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.requestMethod = "GET"
        if (connection.responseCode !in 200..299) error("KnoxZT download HTTP ${connection.responseCode}")
        connection.inputStream.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }
}
