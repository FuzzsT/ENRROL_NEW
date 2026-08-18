package io.dpcaio.knoxzt.android

import android.content.Context

data class KnoxZtInstallSource(
    val url: String,
    val apkSha256: String,
    val signerSha256: Set<String>
) {
    val configured: Boolean
        get() = url.startsWith("https://") && apkSha256.isNotBlank() && signerSha256.isNotEmpty()
}

class KnoxZtInstallSourceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("knoxzt_install_source", Context.MODE_PRIVATE)

    fun load(): KnoxZtInstallSource? {
        val url = prefs.getString("url", null) ?: return null
        val sha = prefs.getString("apk_sha256", null) ?: return null
        val signers = prefs.getStringSet("signer_sha256", emptySet()).orEmpty()
        return KnoxZtInstallSource(url, sha, signers).takeIf { it.configured }
    }

    fun save(source: KnoxZtInstallSource) {
        require(source.configured) { "KnoxZT source must use HTTPS and include APK/signature digests" }
        prefs.edit()
            .putString("url", source.url)
            .putString("apk_sha256", source.apkSha256)
            .putStringSet("signer_sha256", source.signerSha256)
            .apply()
    }
}
