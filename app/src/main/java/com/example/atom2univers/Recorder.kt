package com.example.atom2univers

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Recorder(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    private val client = OkHttpClient()
    private var recordJob: Job? = null
    private var onRecordingFinished: (() -> Unit)? = null

    fun startRecording(streamUrl: String, fileBaseName: String, onFinished: (() -> Unit)? = null): Boolean {
        if (recordJob?.isActive == true) {
            Log.d(TAG, "Recording already in progress, ignoring start request")
            return false
        }

        onRecordingFinished = onFinished
        recordJob = scope.launch {
            var targetUri: Uri? = null
            var outputStream: OutputStream? = null
            var success = false
            try {
                val resolver = context.contentResolver
                val displayName = buildDisplayName(fileBaseName)
                val contentValues = buildContentValues(displayName)
                targetUri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (targetUri == null) {
                    Log.w(TAG, "Unable to create MediaStore entry for recording")
                    return@launch
                }

                outputStream = resolver.openOutputStream(targetUri)
                if (outputStream == null) {
                    Log.w(TAG, "Unable to open output stream for recording")
                    return@launch
                }

                val request = Request.Builder()
                    .get()
                    .url(streamUrl)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Recording HTTP error: ${response.code}")
                        return@use
                    }
                    val body = response.body ?: return@use
                    val input = body.byteStream()
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (isActive) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        outputStream.write(buffer, 0, read)
                    }
                    success = true
                }
            } catch (error: Exception) {
                if (error !is IOException) {
                    Log.w(TAG, "Recording failed", error)
                } else {
                    Log.w(TAG, "Recording IO issue", error)
                }
            } finally {
                withContext(NonCancellable) {
                    try {
                        outputStream?.flush()
                        outputStream?.close()
                    } catch (_: Exception) {
                    }
                    finalizePendingEntry(targetUri, success)
                    onRecordingFinished?.invoke()
                    onRecordingFinished = null
                }
                recordJob = null
            }
        }
        return true
    }

    fun stopRecording() {
        recordJob?.cancel()
        recordJob = null
    }

    fun release() {
        recordJob?.cancel()
        recordJob = null
        scope.cancel()
    }

    private fun finalizePendingEntry(targetUri: Uri?, success: Boolean) {
        if (targetUri == null) return
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val doneValues = ContentValues().apply {
                put(MediaStore.Audio.Media.IS_PENDING, 0)
            }
            resolver.update(targetUri, doneValues, null, null)
        }
        if (!success && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                resolver.delete(targetUri, null)
            } catch (_: Exception) {
            }
        }
    }

    private fun buildDisplayName(base: String): String {
        val safeBase = base.replace(ILLEGAL_FILENAME_CHARS.toRegex(), "_").trim('_').ifEmpty { "radio_record" }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "${safeBase}_$timestamp.mp3"
    }

    private fun buildContentValues(displayName: String): ContentValues {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Atom2Univers")
            values.put(MediaStore.Audio.Media.IS_PENDING, 1)
        } else {
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val targetDir = File(musicDir, "Atom2Univers")
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            val legacyPath = File(targetDir, displayName).absolutePath
            values.put(MediaStore.Audio.Media.DATA, legacyPath)
        }
        return values
    }

    companion object {
        private const val TAG = "Recorder"
        private const val ILLEGAL_FILENAME_CHARS = "[^a-zA-Z0-9._-]"
    }
}
