package com.Atom2Universe.app.dictaphone

import android.content.ContentValues
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class DictaphoneRecorder(private val context: Context) {

    enum class Quality { M4A, WAV }

    sealed class State {
        object Idle : State()
        object Recording : State()
        object Saving : State()
        data class Completed(val uri: Uri, val displayName: String) : State()
        data class Error(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private var mediaRecorder: MediaRecorder? = null
    private var pendingUri: Uri? = null
    private var pendingPfd: ParcelFileDescriptor? = null

    private var audioRecord: AudioRecord? = null
    private var wavThread: Thread? = null
    private val isWavRecording = AtomicBoolean(false)

    @Volatile private var wavAmplitude = 0f
    private var currentQuality = Quality.M4A

    fun getAmplitude(): Float = when (currentQuality) {
        Quality.M4A -> (mediaRecorder?.maxAmplitude ?: 0) / 32767f
        Quality.WAV -> wavAmplitude
    }

    fun startRecording(quality: Quality): Boolean {
        if (_state.value is State.Recording) return false
        currentQuality = quality
        return when (quality) {
            Quality.M4A -> startM4A()
            Quality.WAV -> startWav()
        }
    }

    fun stopRecording() {
        when (currentQuality) {
            Quality.M4A -> stopM4A()
            Quality.WAV -> stopWav()
        }
    }

    fun cancelRecording() {
        when (currentQuality) {
            Quality.M4A -> cancelM4A()
            Quality.WAV -> cancelWav()
        }
        wavAmplitude = 0f
    }

    // ──────────────── M4A ────────────────

    private fun startM4A(): Boolean {
        val displayName = buildDisplayName(Quality.M4A)
        val (uri, pfd) = createMediaStoreEntry(displayName, "audio/mp4")
        if (uri == null || pfd == null) {
            _state.value = State.Error("Cannot create output file")
            return false
        }
        pendingUri = uri
        pendingPfd = pfd
        return try {
            val mr = newMediaRecorder()
            mr.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44100)
                setOutputFile(pfd.fileDescriptor)
                prepare()
                start()
            }
            mediaRecorder = mr
            _state.value = State.Recording
            true
        } catch (e: Exception) {
            pfd.close()
            deletePending(uri)
            pendingUri = null
            pendingPfd = null
            _state.value = State.Error(e.message ?: "Failed to start recording")
            false
        }
    }

    private fun stopM4A() {
        val mr = mediaRecorder ?: return
        val uri = pendingUri ?: return
        val pfd = pendingPfd
        try {
            mr.stop()
        } catch (e: Exception) {
            mr.release()
            mediaRecorder = null
            pfd?.close(); pendingPfd = null
            deletePending(uri); pendingUri = null
            _state.value = State.Error(e.message ?: "Failed to stop")
            return
        }
        mr.release(); mediaRecorder = null
        pfd?.close(); pendingPfd = null
        finalizePending(uri)
        val name = queryDisplayName(uri)
        pendingUri = null
        _state.value = State.Completed(uri, name)
    }

    private fun cancelM4A() {
        mediaRecorder?.let { try { it.stop() } catch (_: Exception) {}; it.release() }
        mediaRecorder = null
        pendingPfd?.close(); pendingPfd = null
        pendingUri?.let { deletePending(it) }; pendingUri = null
        _state.value = State.Idle
    }

    // ──────────────── WAV ────────────────

    private fun startWav(): Boolean {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, ENCODING)
        if (minBuf <= 0) { _state.value = State.Error("AudioRecord not supported"); return false }
        val ar = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, ENCODING, minBuf * 2)
        if (ar.state != AudioRecord.STATE_INITIALIZED) {
            ar.release(); _state.value = State.Error("AudioRecord init failed"); return false
        }
        audioRecord = ar
        isWavRecording.set(true)
        wavAmplitude = 0f
        ar.startRecording()
        _state.value = State.Recording
        val displayName = buildDisplayName(Quality.WAV)
        wavThread = Thread { recordWavThread(ar, minBuf * 2, displayName) }
            .also { it.priority = Thread.MAX_PRIORITY; it.start() }
        return true
    }

    private fun stopWav() {
        isWavRecording.set(false)
        _state.value = State.Saving
    }

    private fun cancelWav() {
        isWavRecording.set(false)
        try { wavThread?.join(3000) } catch (_: Exception) {}
        wavThread = null
        _state.value = State.Idle
    }

    private fun recordWavThread(ar: AudioRecord, bufSize: Int, displayName: String) {
        val tempPcm = File(context.cacheDir, "dict_pcm_${System.currentTimeMillis()}.tmp")
        var totalBytes = 0L
        var hadError = false
        try {
            val buf = ShortArray(bufSize / 2)
            FileOutputStream(tempPcm).use { fos ->
                while (isWavRecording.get()) {
                    val read = ar.read(buf, 0, buf.size)
                    if (read <= 0) break
                    var maxAmp = 0L
                    val bytes = ByteArray(read * 2)
                    for (i in 0 until read) {
                        val s = buf[i]
                        bytes[i * 2] = (s.toInt() and 0xFF).toByte()
                        bytes[i * 2 + 1] = (s.toInt() shr 8 and 0xFF).toByte()
                        val a = kotlin.math.abs(s.toLong())
                        if (a > maxAmp) maxAmp = a
                    }
                    fos.write(bytes)
                    totalBytes += bytes.size
                    wavAmplitude = maxAmp / 32767f
                }
            }
        } catch (_: Exception) { hadError = true }
        finally {
            wavAmplitude = 0f
            try { ar.stop(); ar.release() } catch (_: Exception) {}
            audioRecord = null
        }

        if (hadError || totalBytes == 0L) {
            tempPcm.delete()
            _state.value = if (hadError) State.Error("Recording failed") else State.Idle
            return
        }

        val tempWav = File(context.cacheDir, "dict_wav_${System.currentTimeMillis()}.tmp")
        try {
            pcmToWav(tempPcm, tempWav, totalBytes)
        } catch (_: Exception) {
            tempPcm.delete(); tempWav.delete()
            _state.value = State.Error("WAV conversion failed")
            return
        } finally { tempPcm.delete() }

        val (uri, pfd) = createMediaStoreEntry(displayName, "audio/wav")
        if (uri == null || pfd == null) {
            tempWav.delete(); _state.value = State.Error("Cannot save to storage"); return
        }
        try {
            pfd.use { p ->
                FileOutputStream(p.fileDescriptor).use { out ->
                    tempWav.inputStream().use { it.copyTo(out) }
                }
            }
            finalizePending(uri)
            _state.value = State.Completed(uri, queryDisplayName(uri))
        } catch (_: Exception) {
            deletePending(uri); _state.value = State.Error("Failed to save file")
        } finally { tempWav.delete() }
    }

    // ──────────────── Helpers ────────────────

    private fun createMediaStoreEntry(displayName: String, mimeType: String): Pair<Uri?, ParcelFileDescriptor?> {
        val resolver = context.contentResolver
        val cv = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/$FOLDER_NAME")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            } else {
                val dir = legacyDir()
                if (!dir.exists()) dir.mkdirs()
                put(MediaStore.Audio.Media.DATA, File(dir, displayName).absolutePath)
            }
        }
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cv) ?: return null to null
        val pfd = try { resolver.openFileDescriptor(uri, "w") } catch (_: Exception) { null }
        if (pfd == null) { deletePending(uri); return null to null }
        return uri to pfd
    }

    private fun finalizePending(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cv = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
            try { context.contentResolver.update(uri, cv, null, null) } catch (_: Exception) {}
        }
    }

    private fun deletePending(uri: Uri) {
        try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
    }

    private fun queryDisplayName(uri: Uri): String {
        return try {
            context.contentResolver.query(
                uri, arrayOf(MediaStore.Audio.Media.DISPLAY_NAME), null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else "" } ?: ""
        } catch (_: Exception) { "" }
    }

    private fun pcmToWav(pcmFile: File, wavFile: File, pcmBytes: Long) {
        RandomAccessFile(wavFile, "rw").use { raf ->
            val byteRate = SAMPLE_RATE * 2
            raf.writeBytes("RIFF"); raf.write(intToBytes((pcmBytes + 36).toInt()))
            raf.writeBytes("WAVE")
            raf.writeBytes("fmt "); raf.write(intToBytes(16))
            raf.write(shortToBytes(1)); raf.write(shortToBytes(1))
            raf.write(intToBytes(SAMPLE_RATE)); raf.write(intToBytes(byteRate))
            raf.write(shortToBytes(2)); raf.write(shortToBytes(16))
            raf.writeBytes("data"); raf.write(intToBytes(pcmBytes.toInt()))
            pcmFile.inputStream().use { it.copyTo(FileOutputStream(raf.fd)) }
        }
    }

    private fun legacyDir() = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), FOLDER_NAME
    )

    private fun buildDisplayName(quality: Quality): String {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "dictaphone_$ts.${if (quality == Quality.M4A) "m4a" else "wav"}"
    }

    private fun newMediaRecorder() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(context)
    } else {
        @Suppress("DEPRECATION") MediaRecorder()
    }

    private fun intToBytes(v: Int) = byteArrayOf(
        (v and 0xFF).toByte(), (v shr 8 and 0xFF).toByte(),
        (v shr 16 and 0xFF).toByte(), (v shr 24 and 0xFF).toByte()
    )
    private fun shortToBytes(v: Short) = byteArrayOf(
        (v.toInt() and 0xFF).toByte(), (v.toInt() shr 8 and 0xFF).toByte()
    )

    companion object {
        const val FOLDER_NAME = "A2U_Dictaphone"
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }
}
