package com.example.atom2univers

import android.content.ContentUris
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
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
    fun sendBackupData(base64Data: String?) {
        activityRef.get()?.writeBackupFromJs(base64Data)
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
    fun saveImageToDevice(imageUrl: String?, imageId: String?) {
        val activity = activityRef.get() ?: return
        val safeUrl = imageUrl?.takeIf { it.isNotBlank() } ?: return

        Thread {
            val savedUri = try {
                downloadAndPersistImage(activity, safeUrl, imageId)
            } catch (error: Exception) {
                Log.e(TAG, "Unable to download image from $safeUrl", error)
                null
            }

            val safeId = imageId?.takeIf { it.isNotBlank() } ?: safeUrl
            val callback = if (!savedUri.isNullOrBlank()) {
                "window.onImageSaved && window.onImageSaved(${JSONObject.quote(safeId)});"
            } else {
                "window.onImageSaveFailed && window.onImageSaveFailed(${JSONObject.quote(safeId)});"
            }
            activity.postJavascript(callback)
        }.start()
    }

    @JavascriptInterface
    fun cacheImageToDevice(imageUrl: String?, imageId: String?) {
        val activity = activityRef.get() ?: return
        val safeUrl = imageUrl?.takeIf { it.isNotBlank() } ?: return

        Thread {
            val savedUri = try {
                downloadAndPersistImage(activity, safeUrl, imageId)
            } catch (error: Exception) {
                Log.e(TAG, "Unable to cache image from $safeUrl", error)
                null
            }

            val safeId = imageId?.takeIf { it.isNotBlank() } ?: safeUrl
            val callback = if (!savedUri.isNullOrBlank()) {
                "window.onImageCached && window.onImageCached(${JSONObject.quote(safeId)}, ${JSONObject.quote(savedUri)});"
            } else {
                "window.onImageCacheFailed && window.onImageCacheFailed(${JSONObject.quote(safeId)});"
            }
            activity.postJavascript(callback)
        }.start()
    }

    @JavascriptInterface
    fun listCachedImages(): String {
        val activity = activityRef.get() ?: return "[]"
        val resolver = activity.contentResolver
        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.RELATIVE_PATH
        } else {
            MediaStore.Images.Media.DATA
        }
        val titleColumn = MediaStore.Images.Media.TITLE

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            pathColumn,
            titleColumn
        )

        val (selection, selectionArgs) = buildAlbumSelection(pathColumn)
        var cursor = resolver.query(collectionUri, projection, selection, selectionArgs, null)
        val results = JSONArray()

        return try {
            if (cursor != null) {
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val pathIdx = cursor.getColumnIndexOrThrow(pathColumn)
                val titleIdx = cursor.getColumnIndexOrThrow(titleColumn)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val displayName = cursor.getString(nameColumn) ?: ""
                    val path = cursor.getString(pathIdx) ?: ""
                    val title = cursor.getString(titleIdx) ?: ""
                    val uri = ContentUris.withAppendedId(collectionUri, id).toString()
                    val payload = JSONObject()
                    payload.put("uri", uri)
                    payload.put("displayName", displayName)
                    payload.put("path", path)
                    payload.put("title", title)
                    results.put(payload)
                }
                Log.d(
                    TAG,
                    "Found ${results.length()} cached images in album using selection ${selectionArgs?.firstOrNull()}"
                )
            }
            results.toString()
        } catch (error: Exception) {
            Log.e(TAG, "Unable to list cached Atom2Univers images", error)
            "[]"
        } finally {
            cursor?.close()
        }
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

    private fun downloadImage(url: String): DownloadedImage? {
        var connection: HttpURLConnection? = null
        var input: BufferedInputStream? = null
        return try {
            val endpoint = URL(url)
            connection = endpoint.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                Log.w(TAG, "Unexpected response code $responseCode when downloading $url")
                return null
            }
            val mimeType = connection.contentType
                ?.substringBefore(';')
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            input = BufferedInputStream(connection.inputStream)
            DownloadedImage(input.readBytes(), mimeType)
        } catch (error: IOException) {
            Log.e(TAG, "Unable to download image bytes from $url", error)
            null
        } finally {
            try {
                input?.close()
            } catch (_: IOException) {
            }
            connection?.disconnect()
        }
    }

    private fun persistImage(
        activity: MainActivity,
        download: DownloadedImage,
        imageUrl: String,
        imageId: String?
    ): String? {
        val mimeType = download.mimeType
            ?: extractMimeType(imageUrl)
            ?: "image/jpeg"
        val fileName = sanitizeFileName(URLUtil.guessFileName(imageUrl, null, mimeType))
        val resolver = activity.contentResolver
        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val safeId = imageId?.takeIf { it.isNotBlank() }
        val sanitizedId = safeId?.let { sanitizeFileName(it) }
        val displayName = if (!sanitizedId.isNullOrBlank()) {
            "${sanitizedId}_${fileName}"
        } else {
            fileName
        }

        val targetRelativePath = buildAlbumRelativePath()
        var legacyTarget: java.io.File? = null

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (!safeId.isNullOrBlank()) {
                put(MediaStore.MediaColumns.TITLE, safeId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, targetRelativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            } else {
                val directory = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES
                )
                val targetFolder = directory?.let { parent ->
                    java.io.File(parent, "Atom2Univers")
                }
                if (targetFolder != null && !targetFolder.exists() && !targetFolder.mkdirs()) {
                    Log.w(TAG, "Unable to create target directory: ${targetFolder.absolutePath}")
                }
                legacyTarget = targetFolder?.let { java.io.File(it, fileName) }
                if (legacyTarget != null) {
                    put(MediaStore.MediaColumns.DATA, legacyTarget!!.absolutePath)
                }
            }
        }

        return try {
            val imageUri = resolver.insert(collectionUri, values) ?: return null
            val success = resolver.openOutputStream(imageUri)?.use { stream ->
                stream.write(download.data)
                stream.flush()
                true
            } ?: false
            if (!success) {
                resolver.delete(imageUri, null, null)
                return null
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val finalize = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(imageUri, finalize, null, null)
                Log.d(
                    TAG,
                    "Image saved: uri=$imageUri, relativePath=$targetRelativePath, name=$displayName"
                )
            } else {
                val readable = legacyTarget?.canRead()
                val exists = legacyTarget?.exists()
                Log.d(
                    TAG,
                    "Image saved: uri=$imageUri, file=${legacyTarget?.absolutePath}, exists=$exists, canRead=$readable"
                )
            }

            imageUri.toString()
        } catch (error: Exception) {
            Log.e(TAG, "Unable to persist image to device storage", error)
            null
        }
    }

    private fun downloadAndPersistImage(
        activity: MainActivity,
        imageUrl: String,
        imageId: String?
    ): String? {
        val image = downloadImage(imageUrl) ?: return null
        return persistImage(activity, image, imageUrl, imageId)
    }

    private fun extractMimeType(imageUrl: String): String? {
        return try {
            val guess = URLConnection.guessContentTypeFromName(imageUrl)
            guess?.substringBefore(';')?.trim()?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    private fun sanitizeFileName(rawName: String?): String {
        val cleaned = rawName?.map { char ->
            when {
                char.isLetterOrDigit() -> char
                char == '.' || char == '-' || char == '_' -> char
                else -> '_'
            }
        }?.joinToString("")?.takeIf { it.isNotBlank() }

        return cleaned ?: "atom2univers_image"
    }

    private fun buildAlbumRelativePath(): String {
        val base = Environment.DIRECTORY_PICTURES
        val suffix = if (base.endsWith("/")) "Atom2Univers/" else "/Atom2Univers/"
        return base + suffix
    }

    private fun buildAlbumSelection(pathColumn: String): Pair<String, Array<String>> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relativePath = buildAlbumRelativePath()
            "$pathColumn LIKE ?" to arrayOf("$relativePath%")
        } else {
            val legacyRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val legacyPath = legacyRoot?.let { java.io.File(it, "Atom2Univers") }
            val absolutePrefix = legacyPath?.absolutePath ?: ""
            "$pathColumn LIKE ?" to arrayOf("$absolutePrefix%")
        }
    }

    private companion object {
        private const val TAG = "Atom2Univers"
    }

    private data class DownloadedImage(val data: ByteArray, val mimeType: String?)
}
