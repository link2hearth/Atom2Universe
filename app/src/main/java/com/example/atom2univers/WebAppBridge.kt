package com.example.atom2univers

import android.app.Activity
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

class WebAppBridge(activity: MainActivity) {

    private val activityRef = WeakReference(activity)
    private val defaultNewsFeedUrl = "https://news.google.com/rss?hl=fr&gl=FR&ceid=FR:fr"

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
            activity.setForegroundAudioPlayback(false)
            AudioPlaybackService.stopPlayback(activity)
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
