package io.dpcaio.app

import org.json.JSONObject
import java.net.URI
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class EnrollmentHttpClient(
    endpoint: String,
    private val connectTimeoutMs: Int = 5_000,
    private val readTimeoutMs: Int = 8_000,
) {
    private val baseUri: URI = URI(endpoint).also {
        require(it.scheme.equals("https", ignoreCase = true)) { "Enrollment endpoint must use HTTPS" }
        require(it.host?.isNotBlank() == true) { "Enrollment endpoint host missing" }
        require(it.userInfo == null) { "Enrollment endpoint must not contain user-info credentials" }
    }

    fun postJson(path: String, body: JSONObject): HttpJsonResponse {
        require(path.startsWith('/')) { "path must be absolute" }
        val url = URL(baseUri.toString().trimEnd('/') + path)
        val connection = url.openConnection() as HttpsURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.doOutput = true
        connection.setRequestProperty("content-type", "application/json; charset=utf-8")
        connection.setRequestProperty("accept", "application/json")
        connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        connection.disconnect()
        return HttpJsonResponse(status, if (text.isBlank()) JSONObject() else JSONObject(text))
    }
}

data class HttpJsonResponse(val statusCode: Int, val body: JSONObject)
