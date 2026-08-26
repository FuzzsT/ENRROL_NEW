package io.dpcaio.network.android

import java.net.URL
import javax.net.ssl.HttpsURLConnection

class DohDiagnosticClient {
    fun query(endpoint: String, wireQuery: ByteArray, timeoutMs: Int = 5000): ByteArray {
        require(endpoint.startsWith("https://")) { "DoH endpoint must use HTTPS" }
        val connection = URL(endpoint).openConnection() as HttpsURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/dns-message")
        connection.setRequestProperty("Accept", "application/dns-message")
        connection.outputStream.use { it.write(wireQuery) }
        if (connection.responseCode !in 200..299) error("DoH HTTP ${connection.responseCode}")
        return connection.inputStream.use { it.readBytes() }
    }
}
