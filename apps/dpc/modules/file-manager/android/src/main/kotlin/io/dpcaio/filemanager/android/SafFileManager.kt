package io.dpcaio.filemanager.android

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

data class SafFileEntry(
    val uri: Uri,
    val documentId: String,
    val name: String,
    val mimeType: String,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
)

class SafFileManager(context: Context) {
    private val contentResolver: ContentResolver = context.applicationContext.contentResolver

    fun listChildren(treeUri: Uri, directoryUri: Uri = treeUri): List<SafFileEntry> {
        val documentId = runCatching { DocumentsContract.getDocumentId(directoryUri) }
            .getOrElse { DocumentsContract.getTreeDocumentId(treeUri) }
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        return runCatching {
            contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                buildList {
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(idIndex)
                        val mime = cursor.getString(mimeIndex).orEmpty()
                        add(
                            SafFileEntry(
                                uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                                documentId = id,
                                name = cursor.getString(nameIndex).orEmpty(),
                                mimeType = mime,
                                size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L,
                                lastModified = if (modifiedIndex >= 0 && !cursor.isNull(modifiedIndex)) cursor.getLong(modifiedIndex) else 0L,
                                isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR,
                            )
                        )
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
            .sortedWith(compareByDescending<SafFileEntry> { it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    fun createDirectory(parentUri: Uri, name: String): Uri? =
        DocumentsContract.createDocument(
            contentResolver,
            parentUri,
            DocumentsContract.Document.MIME_TYPE_DIR,
            name,
        )

    fun createFile(parentUri: Uri, mimeType: String, name: String): Uri? =
        DocumentsContract.createDocument(contentResolver, parentUri, mimeType, name)

    fun deleteDocument(uri: Uri): Boolean =
        runCatching { DocumentsContract.deleteDocument(contentResolver, uri) }.getOrDefault(false)

    fun renameDocument(uri: Uri, name: String): Uri? =
        runCatching { DocumentsContract.renameDocument(contentResolver, uri, name) }.getOrNull()
}
