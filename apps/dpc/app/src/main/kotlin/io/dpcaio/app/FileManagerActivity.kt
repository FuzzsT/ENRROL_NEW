package io.dpcaio.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import io.dpcaio.filemanager.android.SafFileEntry
import io.dpcaio.filemanager.android.SafFileManager

class FileManagerActivity : Activity() {
    companion object {
        private const val REQUEST_TREE = 4101
        private const val PREFS = "file_manager"
        private const val KEY_TREE_URI = "tree_uri"
    }

    private val manager by lazy { SafFileManager(this) }
    private lateinit var output: TextView
    private lateinit var listRoot: LinearLayout
    private var treeUri: Uri? = null
    private var currentDirectory: Uri? = null
    private var selected: SafFileEntry? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "DPC-AIO File Manager"

        treeUri = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_TREE_URI, null)?.let(Uri::parse)
        currentDirectory = treeUri

        val body = LinearLayout(this)
        body.addView(ModernUi.hero(
            this,
            title = "File Manager",
            subtitle = "Storage Access Framework • only user-approved locations",
            eyebrow = "Safe file operations",
        ))

        body.addView(ModernUi.section(this, "Location"))
        body.addView(ModernUi.card(this, emphasized = true).apply {
            addView(ModernUi.body(this@FileManagerActivity, "Choose a folder with the Android system picker. Access persists only for the granted tree."))
            addView(ModernUi.spacer(this@FileManagerActivity, 8))
            addView(ModernUi.action(this@FileManagerActivity, "Choose folder") { chooseTree() })
            addView(ModernUi.action(this@FileManagerActivity, "Root") {
                currentDirectory = treeUri
                selected = null
                refresh()
            })
            addView(ModernUi.action(this@FileManagerActivity, "Refresh") { refresh() })
        })

        body.addView(ModernUi.section(this, "Actions"))
        body.addView(ModernUi.card(this).apply {
            addView(ModernUi.action(this@FileManagerActivity, "Create folder") { promptCreateFolder() })
            addView(ModernUi.action(this@FileManagerActivity, "Rename selected") { promptRenameSelected() })
            addView(ModernUi.action(this@FileManagerActivity, "Delete selected", destructive = true) { confirmDeleteSelected() })
        })

        body.addView(ModernUi.section(this, "Files"))
        listRoot = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(listRoot)

        body.addView(ModernUi.section(this, "Status"))
        output = TextView(this).apply { text = "Select a folder." }
        body.addView(ModernUi.outputCard(this, output))

        setContentView(ModernUi.page(this, body))
        refresh()
    }

    private fun chooseTree() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
            treeUri?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_TREE)
    }

    @Deprecated("Legacy Activity result is retained for plain android.app.Activity compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_TREE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val takeFlags = (data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
        runCatching { contentResolver.takePersistableUriPermission(uri, takeFlags) }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_TREE_URI, uri.toString()).apply()
        treeUri = uri
        currentDirectory = uri
        selected = null
        refresh()
    }

    private fun refresh() {
        val root = treeUri
        val directory = currentDirectory
        listRoot.removeAllViews()
        if (root == null || directory == null) {
            output.text = "No SAF tree selected."
            return
        }
        val entries = manager.listChildren(root, directory)
        output.text = "${entries.size} entries • selected=${selected?.name ?: "none"}"
        if (entries.isEmpty()) {
            listRoot.addView(ModernUi.card(this).apply {
                addView(ModernUi.body(this@FileManagerActivity, "Folder is empty or cannot be read."))
            })
            return
        }
        entries.forEach { entry -> listRoot.addView(entryCard(entry)) }
    }

    private fun entryCard(entry: SafFileEntry): View = ModernUi.card(this, emphasized = selected?.uri == entry.uri).apply {
        addView(ModernUi.title(this@FileManagerActivity, if (entry.isDirectory) "📁 ${entry.name}" else entry.name))
        addView(ModernUi.body(
            this@FileManagerActivity,
            if (entry.isDirectory) "Directory" else "${entry.mimeType} • ${entry.size} B",
        ))
        addView(ModernUi.action(this@FileManagerActivity, if (entry.isDirectory) "Open / select" else "Select") {
            selected = entry
            if (entry.isDirectory) currentDirectory = entry.uri
            refresh()
        })
    }

    private fun promptCreateFolder() {
        val parent = currentDirectory ?: run { output.text = "Choose a folder first."; return }
        promptText("Create folder", "Folder name") { name ->
            val created = runCatching { manager.createDirectory(parent, name) }.getOrNull()
            output.text = if (created != null) "Created folder: $name" else "Create folder failed"
            refresh()
        }
    }

    private fun promptRenameSelected() {
        val item = selected ?: run { output.text = "Select an entry first."; return }
        promptText("Rename selected", item.name) { name ->
            val renamed = manager.renameDocument(item.uri, name)
            selected = renamed?.let { item.copy(uri = it, name = name) }
            output.text = if (renamed != null) "Renamed to: $name" else "Rename failed"
            refresh()
        }
    }

    private fun confirmDeleteSelected() {
        val item = selected ?: run { output.text = "Select an entry first."; return }
        AlertDialog.Builder(this)
            .setTitle("Delete selected")
            .setMessage("Delete ${item.name}?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                val deleted = manager.deleteDocument(item.uri)
                if (deleted) selected = null
                output.text = if (deleted) "Deleted ${item.name}" else "Delete failed"
                refresh()
            }
            .show()
    }

    private fun promptText(title: String, initial: String, action: (String) -> Unit) {
        val input = EditText(this).apply { setText(initial); selectAll() }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("OK") { _, _ ->
                input.text.toString().trim().takeIf { it.isNotEmpty() }?.let(action)
            }
            .show()
    }
}
