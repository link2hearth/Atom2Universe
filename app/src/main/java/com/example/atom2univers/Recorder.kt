package com.example.atom2univers

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
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

data class TrackMetadata(val artist: String?, val title: String?)

class Recorder(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    private val client = OkHttpClient()
    private var recordJob: Job? = null
    private var onRecordingFinished: (() -> Unit)? = null
    @Volatile
    private var latestMetadata: TrackMetadata? = null

    @Volatile
    private var metadataVersion: Long = 0

    @Volatile
    private var keepRecording = false

    fun startRecording(streamUrl: String, metadata: TrackMetadata?, onFinished: (() -> Unit)? = null): Boolean {
        if (recordJob?.isActive == true) {
            Log.d(TAG, "Recording already in progress, ignoring start request")
            return false
        }

        keepRecording = true
        latestMetadata = normalizeMetadata(metadata)
        onRecordingFinished = onFinished

        recordJob = scope.launch {
            recordStream(streamUrl)
            withContext(NonCancellable) {
                onRecordingFinished?.invoke()
                onRecordingFinished = null
            }
            recordJob = null
        }
        return true
    }

    fun updateMetadata(metadata: TrackMetadata?) {
        val normalized = normalizeMetadata(metadata)
        if (normalized == latestMetadata) {
            return
        }
        latestMetadata = normalized
        metadataVersion += 1
    }

    fun stopRecording() {
        keepRecording = false
        recordJob?.cancel()
        recordJob = null
    }

    fun release() {
        keepRecording = false
        recordJob?.cancel()
        recordJob = null
        scope.cancel()
    }

    private suspend fun recordStream(streamUrl: String) {
        val request = Request.Builder()
            .get()
            .url(streamUrl)
            .build()

        var segmentMetadataVersion = metadataVersion
        var metadataSnapshot = latestMetadata
        var segment = createNewSegment(segmentMetadataVersion, metadataSnapshot)
        if (segment == null) {
            keepRecording = false
            return
        }

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Recording HTTP error: ${response.code}")
                    return@use
                }

                val body = response.body ?: return@use
                val input = body.byteStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

                while (currentCoroutineContext().isActive && keepRecording) {
                    if (metadataVersion != segmentMetadataVersion) {
                        finalizeSegment(segment, wasSuccessful = true)
                        segmentMetadataVersion = metadataVersion
                        metadataSnapshot = latestMetadata
                        segment = createNewSegment(segmentMetadataVersion, metadataSnapshot)
                        if (segment == null) {
                            keepRecording = false
                            return
                        }
                    }

                    val read = input.read(buffer)
                    if (read <= 0) break
                    segment.outputStream.write(buffer, 0, read)
                    segment.bytesWritten += read
                }

                segment.wasSuccessful = true
            }
        } catch (cancel: CancellationException) {
            segment.wasSuccessful = segment.bytesWritten > 0
        } catch (error: Exception) {
            segment.wasSuccessful = false
            if (error !is IOException) {
                Log.w(TAG, "Recording failed", error)
            } else {
                Log.w(TAG, "Recording IO issue", error)
            }
        } finally {
            finalizeSegment(segment, wasSuccessful = segment.wasSuccessful)
        }
    }

    private fun createNewSegment(versionSnapshot: Long, metadataSnapshot: TrackMetadata?): RecordingSegment? {
        val resolver = context.contentResolver
        val displayName = buildDisplayName(metadataSnapshot)
        val contentValues = buildContentValues(displayName)
        val targetUri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (targetUri == null) {
            Log.w(TAG, "Unable to create MediaStore entry for recording")
            return null
        }

        val outputStream = resolver.openOutputStream(targetUri)
        if (outputStream == null) {
            Log.w(TAG, "Unable to open output stream for recording")
            try {
                resolver.delete(targetUri, null)
            } catch (_: Exception) {
            }
            return null
        }

        return RecordingSegment(
            targetUri = targetUri,
            outputStream = outputStream,
            startTimeMs = System.currentTimeMillis(),
            metadataVersion = versionSnapshot,
            bytesWritten = 0,
            wasSuccessful = false
        )
    }

    private fun finalizeSegment(segment: RecordingSegment?, wasSuccessful: Boolean) {
        if (segment == null) return
        val resolver = context.contentResolver
        val durationMs = System.currentTimeMillis() - segment.startTimeMs
        val keepEntry = wasSuccessful && segment.bytesWritten > 0 && durationMs >= MIN_DURATION_MS

        try {
            segment.outputStream.flush()
            segment.outputStream.close()
        } catch (_: Exception) {
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val doneValues = ContentValues().apply {
                put(MediaStore.Audio.Media.IS_PENDING, 0)
            }
            resolver.update(segment.targetUri, doneValues, null, null)
        }

        if (!keepEntry) {
            try {
                resolver.delete(segment.targetUri, null)
            } catch (_: Exception) {
            }
        }
    }

    private fun buildDisplayName(metadata: TrackMetadata?): String {
        val base = buildBaseName(metadata)
        val safeBase = base.replace(ILLEGAL_FILENAME_CHARS.toRegex(), "_").trim('_').ifEmpty { "radio_record" }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "${safeBase}_$timestamp.mp3"
    }

    private fun buildBaseName(metadata: TrackMetadata?): String {
        val normalized = normalizeMetadata(metadata)
        val artist = normalized?.artist
        val title = normalized?.title
        if (artist != null && title != null) {
            return "$artist - $title"
        }
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    }

    private fun normalizeMetadata(metadata: TrackMetadata?): TrackMetadata? {
        val artist = metadata?.artist?.trim()?.takeIf { it.isNotEmpty() }
        val title = metadata?.title?.trim()?.takeIf { it.isNotEmpty() }
        if (artist == null && title == null) {
            return null
        }
        return TrackMetadata(artist, title)
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

    private data class RecordingSegment(
        val targetUri: Uri,
        val outputStream: OutputStream,
        val startTimeMs: Long,
        val metadataVersion: Long,
        var bytesWritten: Long,
        var wasSuccessful: Boolean
    )

    companion object {
        private const val TAG = "Recorder"
        private const val ILLEGAL_FILENAME_CHARS = "[^a-zA-Z0-9._-]"
        private const val MIN_DURATION_MS = 60_000
    }
}
