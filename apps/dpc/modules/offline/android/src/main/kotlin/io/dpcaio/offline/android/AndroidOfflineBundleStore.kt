package io.dpcaio.offline.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File

class AndroidOfflineBundleStore(context: Context) {
    private val appContext = context.applicationContext

    fun createImportIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "application/zip"
        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/zip", "application/octet-stream"))
    }

    fun copyToPrivateStorage(uri: Uri, fileName: String = "offline-bundle.zip"): File {
        val root = File(appContext.filesDir, "offline-vault").apply { mkdirs() }
        val target = File(root, fileName.replace(Regex("[^A-Za-z0-9._-]"), "_"))
        appContext.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open offline bundle URI" }
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }
}
