package com.example.atom2univers

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bridge exposing Storage Access Framework helpers to the WebView.
 */
class AndroidBridge(
    private val activity: AppCompatActivity,
    private val webView: WebView,
    private val openFolderLauncher: ActivityResultLauncher<Intent>,
    private val importDocumentLauncher: ActivityResultLauncher<Intent>
) {

    private val preferences: SharedPreferences =
        activity.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE)
    private val contentResolver = activity.contentResolver
    private var persistedTreeUri: Uri? = loadPersistedTreeUri()
    private var pendingExport: PendingExport? = null
    private var pendingImportRequest = false
    private var initialFolderStatusDispatched = false

    init {
        persistedTreeUri?.let { uri ->
            // Ensure the persisted permission is still valid; otherwise clear it.
            if (!isPersistedPermissionValid(uri)) {
                persistedTreeUri = null
                preferences.edit { remove(KEY_TREE_URI) }
            }
        }
    }

    @JavascriptInterface
    fun requestFolder() {
        activity.runOnUiThread {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val initial = persistedTreeUri
                    if (initial != null) {
                        val docId = DocumentsContract.getTreeDocumentId(initial)
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(initial, docId)
                        putExtra(DocumentsContract.EXTRA_INITIAL_URI, docUri)
                    }
                }
            }
            openFolderLauncher.launch(intent)
        }
    }

    @JavascriptInterface
    fun exportSave(payload: String?, suggestedName: String?) {
        if (payload.isNullOrEmpty()) {
            notifySaveExported(false, "Empty payload")
            return
        }

        val directory = getPersistedDirectory()
        if (directory == null) {
            pendingExport = PendingExport(payload, suggestedName)
            requestFolder()
            return
        }

        try {
            val fileName = createFileName(suggestedName)
            val uniqueName = resolveUniqueFileName(directory, fileName)
            val created = directory.createFile(MIME_JSON, uniqueName)
            if (created == null) {
                notifySaveExported(false, "Unable to create file")
                return
            }
            contentResolver.openOutputStream(created.uri)?.use { outputStream ->
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(payload)
                    writer.flush()
                }
            } ?: run {
                notifySaveExported(false, "Unable to open stream")
                return
            }
            val reportedName = created.name ?: uniqueName
            notifySaveExported(true, reportedName)
        } catch (error: IOException) {
            notifySaveExported(false, error.localizedMessage ?: "IO error")
        }
    }

    @JavascriptInterface
    fun importSave() {
        val directory = getPersistedDirectory()
        if (directory == null) {
            pendingImportRequest = true
            requestFolder()
            return
        }
        launchImportPicker()
    }

    fun handleFolderPickerResult(result: ActivityResult) {
        if (result.resultCode != Activity.RESULT_OK) {
            notifyFolderReady(false)
            flushPendingActionsOnFolderFailure()
            return
        }
        val uri = result.data?.data
        if (uri == null) {
            notifyFolderReady(false)
            flushPendingActionsOnFolderFailure()
            return
        }
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            persistedTreeUri?.let { previous ->
                if (previous != uri) {
                    try {
                        contentResolver.releasePersistableUriPermission(previous, flags)
                    } catch (_: SecurityException) {
                        // Ignore and override preference.
                    }
                }
            }
            contentResolver.takePersistableUriPermission(uri, flags)
            persistedTreeUri = uri
            preferences.edit { putString(KEY_TREE_URI, uri.toString()) }
            notifyFolderReady(true)
            val export = pendingExport
            val shouldImport = pendingImportRequest
            pendingExport = null
            pendingImportRequest = false
            if (export != null) {
                exportSave(export.payload, export.suggestedName)
            } else if (shouldImport) {
                if (getPersistedDirectory() != null) {
                    launchImportPicker()
                }
            }
        } catch (error: SecurityException) {
            notifyFolderReady(false)
            flushPendingActionsOnFolderFailure(error.localizedMessage ?: "Permission error")
        }
    }

    fun handleImportPickerResult(result: ActivityResult) {
        if (result.resultCode != Activity.RESULT_OK) {
            notifySaveImported(null)
            return
        }
        val uri = result.data?.data ?: run {
            notifySaveImported(null)
            return
        }
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val text = inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    reader.readText()
                }
                notifySaveImported(text)
            } ?: notifySaveImported(null)
        } catch (error: IOException) {
            notifySaveImported(null)
        }
    }

    fun onPageLoaded(url: String) {
        if (!url.startsWith(MainActivity.ASSET_URL_PREFIX)) {
            return
        }
        if (!initialFolderStatusDispatched) {
            initialFolderStatusDispatched = true
            notifyFolderReady(getPersistedDirectory() != null)
        }
    }

    private fun launchImportPicker() {
        activity.runOnUiThread {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = MIME_JSON
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val treeUri = persistedTreeUri
                    if (treeUri != null) {
                        val docId = DocumentsContract.getTreeDocumentId(treeUri)
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        putExtra(DocumentsContract.EXTRA_INITIAL_URI, docUri)
                    }
                }
            }
            importDocumentLauncher.launch(intent)
        }
    }

    private fun flushPendingActionsOnFolderFailure(errorMessage: String? = null) {
        val export = pendingExport
        pendingExport = null
        val shouldImport = pendingImportRequest
        pendingImportRequest = false
        if (export != null) {
            notifySaveExported(false, errorMessage ?: "Folder not selected")
        }
        if (shouldImport) {
            notifySaveImported(null)
        }
    }

    private fun notifyFolderReady(success: Boolean) {
        val script = """
            (function() {
                const detail = { ok: ${if (success) "true" else "false"}, time: Date.now() };
                window.__atom2uFolderStatus = detail;
                if (typeof window.onFolderReady === 'function') {
                    try { window.onFolderReady(detail.ok); } catch (error) { console.error(error); }
                }
            })();
        """.trimIndent()
        webView.post { webView.evaluateJavascript(script, null) }
    }

    private fun notifySaveExported(success: Boolean, message: String?) {
        val encodedMessage = message?.let { JSONObject.quote(it) } ?: "null"
        val script = """
            (function() {
                const detail = { ok: ${if (success) "true" else "false"}, message: $encodedMessage, time: Date.now() };
                window.__atom2uLastExport = detail;
                if (typeof window.onSaveExported === 'function') {
                    try { window.onSaveExported(detail.ok, detail.message); } catch (error) { console.error(error); }
                }
            })();
        """.trimIndent()
        webView.post { webView.evaluateJavascript(script, null) }
    }

    private fun notifySaveImported(text: String?) {
        val encoded = text?.let { JSONObject.quote(it) } ?: "null"
        val script = """
            (function() {
                const detail = { text: $encoded, time: Date.now() };
                window.__atom2uLastImport = detail;
                if (typeof window.onSaveImported === 'function') {
                    try { window.onSaveImported(detail.text); } catch (error) { console.error(error); }
                }
            })();
        """.trimIndent()
        webView.post { webView.evaluateJavascript(script, null) }
    }

    private fun getPersistedDirectory(): DocumentFile? {
        val uri = persistedTreeUri ?: return null
        return DocumentFile.fromTreeUri(activity, uri)?.takeIf { it.canWrite() || it.canRead() }
    }

    private fun loadPersistedTreeUri(): Uri? {
        val raw = preferences.getString(KEY_TREE_URI, null)?.takeIf { it.isNotBlank() } ?: return null
        return try {
            Uri.parse(raw)
        } catch (_: Exception) {
            null
        }
    }

    private fun isPersistedPermissionValid(uri: Uri): Boolean {
        val permissions = contentResolver.persistedUriPermissions
        return permissions.any { perm ->
            perm.uri == uri && perm.isReadPermission && perm.isWritePermission
        }
    }

    private fun createFileName(suggested: String?): String {
        val normalized = suggested?.trim()?.takeIf { it.isNotEmpty() }
        val baseName = normalized?.let { sanitizeFileName(it) }
            ?: "savegame-${TIMESTAMP_FORMAT.format(Date())}.json"
        return ensureJsonExtension(baseName)
    }

    private fun sanitizeFileName(raw: String): String {
        val cleaned = raw.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return cleaned.ifEmpty { "savegame" }
    }

    private fun ensureJsonExtension(name: String): String {
        return if (name.lowercase(Locale.US).endsWith(".json")) name else "$name.json"
    }

    private fun resolveUniqueFileName(directory: DocumentFile, desiredName: String): String {
        var candidate = desiredName
        val dotIndex = candidate.lastIndexOf('.')
        val base = if (dotIndex > 0) candidate.substring(0, dotIndex) else candidate
        val extension = if (dotIndex > 0) candidate.substring(dotIndex) else ""
        var index = 1
        while (directory.findFile(candidate) != null) {
            candidate = "$base-$index$extension"
            index += 1
        }
        return candidate
    }

    data class PendingExport(val payload: String, val suggestedName: String?)

    private companion object {
        private const val PREFS_NAME = "atom2univers_saf"
        private const val KEY_TREE_URI = "tree_uri"
        private const val MIME_JSON = "application/json"
        private val TIMESTAMP_FORMAT = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
    }
}
