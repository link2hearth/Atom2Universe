package com.example.atom2univers

import android.app.Activity
import android.content.ContentResolver
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.lang.ref.WeakReference
import kotlin.text.Charsets

class WebAppBridge(activity: MainActivity) {

    private val activityRef = WeakReference(activity)
    private val defaultNewsFeedUrl = "https://news.google.com/rss?hl=fr&gl=FR&ceid=FR:fr"
    private val notesRelativePath = "${Environment.DIRECTORY_DOCUMENTS}/Atom2Univers/"
    private val legacyNotesFolderName = "Atom2Univers"

    @JavascriptInterface
    fun saveBackup() {
        // Get the activity from the WeakReference
        val activity = activityRef.get()
        // Run the code on the UI thread, using the 'activity' variable
        activity?.runOnUiThread {
            activity.startCreateBackup()
        }
    }

    @JavascriptInterface
    fun loadBackup() {
        // Get the activity from the WeakReference
        val activity = activityRef.get()
        // Run the code on the UI thread, using the 'activity' variable
        activity?.runOnUiThread {
            activity.startOpenBackup()
        }
    }

    @JavascriptInterface
    fun pickBackgroundImageBank() {
        val activity = activityRef.get()
        activity?.runOnUiThread {
            activity.startBackgroundBankPicker()
        }
    }

    @JavascriptInterface
    fun selectSoundFont() {
        val activity = activityRef.get()
        activity?.runOnUiThread {
            activity.startSoundFontPicker()
        }
    }

    @JavascriptInterface
    fun loadCachedSoundFont() {
        val activity = activityRef.get()
        activity?.runOnUiThread {
            activity.loadCachedSoundFont()
        }
    }

    @JavascriptInterface
    fun pickMidiFolder() {
        val activity = activityRef.get()
        activity?.runOnUiThread {
            activity.startMidiFolderPicker()
        }
    }

    @JavascriptInterface
    fun loadMidiFolder() {
        val activity = activityRef.get()
        activity?.runOnUiThread {
            activity.loadPersistedMidiLibrary()
        }
    }

    @JavascriptInterface
    fun rescanMidiFolder() {
        val activity = activityRef.get()
        activity?.runOnUiThread {
            activity.loadPersistedMidiLibrary(true)
        }
    }

    @JavascriptInterface
    fun startForegroundAudio(title: String?, artist: String?, playing: Boolean) {
        val activity = activityRef.get()
        activity?.runOnUiThread {
            activity.setForegroundAudioPlayback(playing)
            AudioPlaybackService.startForegroundPlayback(activity, title, artist, playing)
        }
    }

    @JavascriptInterface
    fun updateForegroundAudio(title: String?, artist: String?, playing: Boolean) {
        val activity = activityRef.get()
        activity?.runOnUiThread {
            activity.setForegroundAudioPlayback(playing)
            AudioPlaybackService.updateMetadata(activity, title, artist, playing)
        }
    }

    @JavascriptInterface
    fun stopForegroundAudio() {
        val activity = activityRef.get()
        activity?.runOnUiThread {
            val wasPlaying = activity.isForegroundAudioPlaybackRunning()
            activity.setForegroundAudioPlayback(false)
            if (wasPlaying) {
                AudioPlaybackService.stopPlayback(activity)
            }
        }
    }

    @JavascriptInterface
    fun loadBackgroundImageBank() {
        val activity = activityRef.get()
        activity?.runOnUiThread {
            activity.loadPersistedBackgroundBank()
        }
    }

    @JavascriptInterface
    fun sendBackupData(base64Data: String?) {
        activityRef.get()?.writeBackupFromJs(base64Data)
    }

    @JavascriptInterface
    fun saveImageToDevice(url: String?, itemId: String?) {
        val activity = activityRef.get() ?: return
        val source = url?.takeIf { it.isNotBlank() } ?: return
        Thread {
            val mimeType = guessMimeType(source)
            val displayName = buildImageDisplayName(itemId, mimeType)
            val imageBytes = decodeDataUrl(source) ?: downloadBinary(source)
            val success = if (imageBytes != null && imageBytes.isNotEmpty()) {
                saveImageBytes(activity, imageBytes, mimeType, displayName)
            } else {
                false
            }
            if (success) {
                showImageSavedToast(activity)
            }
            val script = "window.onImageSaved && window.onImageSaved(${if (success) "true" else "false"});"
            activity.postJavascript(script)
        }.start()
    }

    @JavascriptInterface
    fun resolveContentUri(uri: String?): String? {
        val activity = activityRef.get() ?: return null
        val source = uri?.takeIf { it.isNotBlank() } ?: return null
        return try {
            val target = Uri.parse(source)
            val resolver = activity.contentResolver
            val mimeType = resolver.getType(target) ?: guessMimeType(source)
            val bytes = resolver.openInputStream(target)?.use { readFully(it) } ?: return null
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val safeMime = mimeType?.takeIf { it.isNotBlank() } ?: "image/*"
            "data:$safeMime;base64,$base64"
        } catch (error: Exception) {
            Log.w(TAG, "Unable to resolve content URI", error)
            null
        }
    }

    @JavascriptInterface
    fun loadNews(feedUrl: String?) {
        val activity = activityRef.get() ?: return
        val targetUrl = feedUrl?.takeIf { it.isNotBlank() } ?: defaultNewsFeedUrl
        Thread {
            val xml = fetchRss(targetUrl)
            val payload = JSONObject.quote(xml)
            val script = "window.onNewsLoaded && window.onNewsLoaded($payload);"
            activity.postJavascript(script)
        }.start()
    }

    @JavascriptInterface
    fun listUserNotes(): String {
        val activity = activityRef.get() ?: return JSONArray().toString()
        val result = JSONArray()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = activity.contentResolver
                val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val projection = arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    MediaStore.Files.FileColumns.MIME_TYPE,
                    MediaStore.Files.FileColumns.DATE_MODIFIED
                )
                val selection = "${MediaStore.Files.FileColumns.RELATIVE_PATH} = ?"
                val selectionArgs = arrayOf(notesRelativePath)
                val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
                resolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                    val idIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                    val nameIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val mimeIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
                    val dateIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
                    while (cursor.moveToNext()) {
                        val displayName = cursor.getString(nameIndex) ?: continue
                        val id = cursor.getLong(idIndex)
                        val mimeType = cursor.getString(mimeIndex) ?: guessNoteMimeType(displayName)
                        val updatedAtSeconds = cursor.getLong(dateIndex)
                        val entry = JSONObject()
                        entry.put("name", displayName)
                        entry.put("mimeType", mimeType)
                        entry.put("uri", Uri.withAppendedPath(collection, id.toString()).toString())
                        entry.put("updatedAt", updatedAtSeconds * 1000)
                        result.put(entry)
                    }
                }
            } else {
                val directory = ensureLegacyNotesDir(activity) ?: return result.toString()
                directory.listFiles { file -> file.isFile && isNoteFile(file.name) }
                    ?.sortedByDescending { it.lastModified() }
                    ?.forEach { file ->
                        val entry = JSONObject()
                        entry.put("name", file.name)
                        entry.put("mimeType", guessNoteMimeType(file.name))
                        entry.put("uri", Uri.fromFile(file).toString())
                        entry.put("updatedAt", file.lastModified())
                        result.put(entry)
                    }
            }
            result.toString()
        } catch (error: Exception) {
            Log.w(TAG, "Unable to list notes", error)
            result.toString()
        }
    }

    @JavascriptInterface
    fun readUserNote(displayName: String?): String? {
        val activity = activityRef.get() ?: return null
        val safeName = sanitizeNoteFileName(displayName, "txt") ?: return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = activity.contentResolver
                val uri = findNoteUri(resolver, safeName) ?: return null
                return resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                    reader.readText()
                }
            }
            val directory = ensureLegacyNotesDir(activity) ?: return null
            val target = File(directory, safeName)
            if (!target.exists()) {
                return null
            }
            target.inputStream().bufferedReader(Charsets.UTF_8).use { reader ->
                reader.readText()
            }
        } catch (error: Exception) {
            Log.w(TAG, "Unable to read note $displayName", error)
            null
        }
    }

    @JavascriptInterface
    fun saveUserNote(displayName: String?, content: String?, mimeType: String?): String? {
        val activity = activityRef.get() ?: return null
        val fallbackExt = if (mimeType?.contains("markdown", ignoreCase = true) == true) "md" else "txt"
        val safeName = sanitizeNoteFileName(displayName, fallbackExt) ?: return null
        val payload = content ?: ""
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = activity.contentResolver
                val existingUri = findNoteUri(resolver, safeName)
                val targetUri = existingUri ?: run {
                    val values = ContentValues().apply {
                        put(MediaStore.Files.FileColumns.DISPLAY_NAME, safeName)
                        put(MediaStore.Files.FileColumns.MIME_TYPE, mimeType ?: guessNoteMimeType(safeName))
                        put(MediaStore.Files.FileColumns.RELATIVE_PATH, notesRelativePath)
                    }
                    resolver.insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                }
                targetUri ?: return null
                resolver.openOutputStream(targetUri, "wt")?.use { stream ->
                    stream.write(payload.toByteArray(Charsets.UTF_8))
                } ?: return null
                return targetUri.toString()
            }

            val directory = ensureLegacyNotesDir(activity) ?: return null
            val target = File(directory, safeName)
            target.outputStream().buffered().use { output ->
                output.write(payload.toByteArray(Charsets.UTF_8))
            }
            Uri.fromFile(target).toString()
        } catch (error: Exception) {
            Log.w(TAG, "Unable to save note $displayName", error)
            null
        }
    }

    @JavascriptInterface
    fun deleteUserNote(displayName: String?): Boolean {
        val activity = activityRef.get() ?: return false
        val safeName = sanitizeNoteFileName(displayName, "txt") ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = activity.contentResolver
                val uri = findNoteUri(resolver, safeName) ?: return false
                resolver.delete(uri, null, null) > 0
            } else {
                val directory = ensureLegacyNotesDir(activity) ?: return false
                val target = File(directory, safeName)
                !target.exists() || target.delete()
            }
        } catch (error: Exception) {
            Log.w(TAG, "Unable to delete note $displayName", error)
            false
        }
    }

    private fun sanitizeNoteFileName(raw: String?, defaultExt: String): String? {
        val cleaned = raw?.trim()
            ?.replace("[\\\\/:*?\"<>|]".toRegex(), "")
            ?.replace("\\s+".toRegex(), "-")
            ?.takeIf { it.isNotBlank() }
        val basePart = cleaned?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
            ?: cleaned?.takeIf { it.isNotBlank() }
            ?: "note-${System.currentTimeMillis()}"
        val extPart = cleaned?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.isNotBlank() }
        val normalizedExt = when (extPart) {
            "md", "markdown" -> "md"
            "txt" -> "txt"
            else -> defaultExt.ifBlank { "txt" }
        }
        return "$basePart.$normalizedExt"
    }

    private fun guessNoteMimeType(displayName: String): String {
        val normalized = displayName.lowercase()
        return if (normalized.endsWith(".md") || normalized.endsWith(".markdown")) {
            "text/markdown"
        } else {
            "text/plain"
        }
    }

    private fun isNoteFile(name: String?): Boolean {
        if (name.isNullOrBlank()) {
            return false
        }
        val normalized = name.lowercase()
        return normalized.endsWith(".txt") || normalized.endsWith(".md") || normalized.endsWith(".markdown")
    }

    private fun ensureLegacyNotesDir(activity: Activity): File? {
        return try {
            val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val target = File(baseDir, legacyNotesFolderName)
            if (!target.exists() && !target.mkdirs()) {
                val fallback = activity.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                    ?.let { File(it, legacyNotesFolderName) }
                if (fallback != null && (fallback.exists() || fallback.mkdirs())) {
                    fallback
                } else {
                    null
                }
            } else {
                target
            }
        } catch (error: Exception) {
            Log.w(TAG, "Unable to access legacy notes directory", error)
            null
        }
    }

    private fun findNoteUri(resolver: ContentResolver, displayName: String): Uri? {
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(MediaStore.Files.FileColumns._ID)
        val selection = "${MediaStore.Files.FileColumns.RELATIVE_PATH} = ? AND ${MediaStore.Files.FileColumns.DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf(notesRelativePath, displayName)
        resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
            if (idIndex >= 0 && cursor.moveToFirst()) {
                val id = cursor.getLong(idIndex)
                return Uri.withAppendedPath(collection, id.toString())
            }
        }
        return null
    }

    private fun fetchRss(url: String): String {
        var connection: HttpURLConnection? = null
        var reader: BufferedReader? = null
        return try {
            val endpoint = URL(url)
            connection = endpoint.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.requestMethod = "GET"
            val stream = connection.inputStream
            reader = BufferedReader(InputStreamReader(stream))
            val builder = StringBuilder()
            var line: String?
            while (reader?.readLine().also { line = it } != null) {
                builder.append(line).append('\n')
            }
            builder.toString()
        } catch (error: Exception) {
            ""
        } finally {
            try {
                reader?.close()
            } catch (_: Exception) {
            }
            connection?.disconnect()
        }
    }

    private fun showImageSavedToast(activity: Activity) {
        val targetPath = Environment.DIRECTORY_PICTURES + "/Atom2Univers"
        activity.runOnUiThread {
            Toast.makeText(
                activity,
                activity.getString(R.string.image_saved_toast, targetPath),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun decodeDataUrl(source: String): ByteArray? {
        if (!source.startsWith("data:", ignoreCase = true)) {
            return null
        }
        val commaIndex = source.indexOf(',')
        if (commaIndex <= 4 || commaIndex >= source.lastIndex) {
            return null
        }
        val metadata = source.substring(5, commaIndex)
        val payload = source.substring(commaIndex + 1)
        return try {
            if (metadata.contains(";base64", ignoreCase = true)) {
                Base64.decode(payload, Base64.DEFAULT)
            } else {
                payload.toByteArray()
            }
        } catch (error: Exception) {
            Log.w(TAG, "Unable to decode data URL", error)
            null
        }
    }

    private fun downloadBinary(url: String): ByteArray? {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 12000
            connection.readTimeout = 12000
            connection.requestMethod = "GET"
            connection.inputStream.use { readFully(it) }
        } catch (error: Exception) {
            Log.w(TAG, "Unable to download image", error)
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun readFully(stream: InputStream): ByteArray {
        val buffer = ByteArrayOutputStream()
        stream.copyTo(buffer)
        return buffer.toByteArray()
    }

    private fun guessMimeType(source: String): String {
        if (source.startsWith("data:", ignoreCase = true)) {
            val endOfMeta = source.indexOf(';').takeIf { it > 5 }
            val endOfType = if (endOfMeta != null && endOfMeta > 5) endOfMeta else source.indexOf(',')
            if (endOfType > 5) {
                val type = source.substring(5, endOfType)
                if (type.contains('/')) {
                    return type
                }
            }
        }
        val extension = source.substringAfterLast('.', "")
            .substringBefore('?')
            .substringBefore('#')
            .lowercase()
        val mapped = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        if (!mapped.isNullOrBlank()) {
            return mapped
        }
        return "image/jpeg"
    }

    private fun buildImageDisplayName(itemId: String?, mimeType: String): String {
        val base = itemId?.takeIf { it.isNotBlank() } ?: "image_${System.currentTimeMillis()}"
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            ?.takeIf { it.isNotBlank() }
            ?: "jpg"
        return "$base.$extension"
    }

    private fun saveImageBytes(activity: Activity, data: ByteArray, mimeType: String, displayName: String): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveWithMediaStore(activity, data, mimeType, displayName)
        } else {
            saveLegacyImage(activity, data, mimeType, displayName)
        }
    }

    private fun saveWithMediaStore(activity: Activity, data: ByteArray, mimeType: String, displayName: String): Boolean {
        val resolver = activity.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Atom2Univers")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: return false
        return try {
            resolver.openOutputStream(uri)?.use { output ->
                output.write(data)
            } ?: return false
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (error: Exception) {
            Log.w(TAG, "Unable to persist image with MediaStore", error)
            resolver.delete(uri, null, null)
            false
        }
    }

    private fun saveLegacyImage(activity: Activity, data: ByteArray, mimeType: String, displayName: String): Boolean {
        return try {
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val targetDir = File(picturesDir, "Atom2Univers")
            if (!targetDir.exists() && !targetDir.mkdirs()) {
                return false
            }
            val targetFile = File(targetDir, displayName)
            FileOutputStream(targetFile).use { it.write(data) }
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DATA, targetFile.absolutePath)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            }
            activity.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            true
        } catch (error: Exception) {
            Log.w(TAG, "Unable to persist image on legacy storage", error)
            false
        }
    }

    companion object {
        private const val TAG = "WebAppBridge"
    }
}
