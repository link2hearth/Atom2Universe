package com.Atom2Universe.app.dictaphone

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.Atom2Universe.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class DictaphoneRecorder(private val context: Context) {

    enum class Quality { M4A, WAV }

    sealed class State {
        object Idle : State()
        object Recording : State()
        object Saving : State()
        /** [warning] : message à montrer quand même (enregistrement arrêté par un stockage plein), 0 sinon. */
        data class Completed(val uri: Uri, val displayName: String, @StringRes val warning: Int = 0) : State()
        data class Error(@StringRes val message: Int) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private var mediaRecorder: MediaRecorder? = null
    private var pendingUri: Uri? = null
    private var pendingPfd: ParcelFileDescriptor? = null

    private var audioRecord: AudioRecord? = null
    private var wavThread: Thread? = null
    private val isWavRecording = AtomicBoolean(false)
    private val wavCancelled = AtomicBoolean(false)

    @Volatile private var wavAmplitude = 0f
    private var currentQuality = Quality.M4A

    fun getAmplitude(): Float = when (currentQuality) {
        Quality.M4A -> (mediaRecorder?.maxAmplitude ?: 0) / 32767f
        Quality.WAV -> wavAmplitude
    }

    /** L'écran a déjà obtenu la permission du micro ; sans elle, rien ne se passe. */
    fun startRecording(quality: Quality): Boolean {
        if (_state.value is State.Recording || _state.value is State.Saving) return false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
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
        val (uri, pfd) = createMediaStoreEntry(displayName, "audio/mp4", "w")
        if (uri == null || pfd == null) {
            _state.value = State.Error(R.string.dictaphone_error_start)
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
            _state.value = State.Error(R.string.dictaphone_error_start)
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
            _state.value = State.Error(R.string.dictaphone_error_save)
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

    /**
     * Le PCM part directement dans le fichier final, derrière un en-tête provisoire corrigé à l'arrêt :
     * ni copie ni fichier temporaire, et un enregistrement interrompu reste récupérable ([recoverPending]).
     */
    @android.annotation.SuppressLint("MissingPermission")
    private fun startWav(): Boolean {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, ENCODING)
        if (minBuf <= 0) { _state.value = State.Error(R.string.dictaphone_error_start); return false }
        val (uri, pfd) = createMediaStoreEntry(buildDisplayName(Quality.WAV), "audio/wav", "rw")
        if (uri == null || pfd == null) { _state.value = State.Error(R.string.dictaphone_error_start); return false }
        val ar = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, ENCODING, minBuf * 2)
        } catch (_: Exception) { null }
        if (ar == null || ar.state != AudioRecord.STATE_INITIALIZED) {
            ar?.release()
            try { pfd.close() } catch (_: Exception) {}
            deletePending(uri)
            _state.value = State.Error(R.string.dictaphone_error_start)
            return false
        }
        try {
            ar.startRecording()
        } catch (_: Exception) {
            ar.release()
            try { pfd.close() } catch (_: Exception) {}
            deletePending(uri)
            _state.value = State.Error(R.string.dictaphone_error_start)
            return false
        }
        audioRecord = ar
        isWavRecording.set(true)
        wavCancelled.set(false)
        wavAmplitude = 0f
        _state.value = State.Recording
        wavThread = Thread { recordWavThread(ar, minBuf * 2, uri, pfd) }
            .also { it.priority = Thread.MAX_PRIORITY; it.start() }
        return true
    }

    private fun stopWav() {
        if (!isWavRecording.getAndSet(false)) return
        _state.value = State.Saving
    }

    private fun cancelWav() {
        wavCancelled.set(true)
        isWavRecording.set(false)
        try { wavThread?.join(3000) } catch (_: Exception) {}
        wavThread = null
        _state.value = State.Idle
    }

    private fun recordWavThread(ar: AudioRecord, bufSize: Int, uri: Uri, pfd: ParcelFileDescriptor) {
        var dataBytes = 0L
        var storageFull = false
        var failed = false
        val buf = ShortArray(bufSize / 2)
        val bytes = ByteArray(buf.size * 2)
        try {
            ParcelFileDescriptor.AutoCloseOutputStream(pfd).use { out ->
                out.write(wavHeader(0L))
                while (isWavRecording.get()) {
                    val read = ar.read(buf, 0, buf.size)
                    if (read <= 0) break
                    val n = read * 2
                    // Un WAV ne dépasse pas 4 Go (environ 13 h ici) : au-delà, on arrête et on garde.
                    if (dataBytes + n > MAX_DATA_BYTES) break
                    var maxAmp = 0
                    for (i in 0 until read) {
                        val s = buf[i].toInt()
                        bytes[i * 2] = (s and 0xFF).toByte()
                        bytes[i * 2 + 1] = (s shr 8 and 0xFF).toByte()
                        val a = if (s < 0) -s else s
                        if (a > maxAmp) maxAmp = a
                    }
                    try {
                        out.write(bytes, 0, n)
                    } catch (_: IOException) {
                        storageFull = true
                        break
                    }
                    dataBytes += n
                    wavAmplitude = maxAmp / 32767f
                }
                // En-tête définitif, au début du fichier ; un dernier bloc à moitié écrit est retiré.
                val channel = out.channel
                runCatching { channel.truncate(HEADER_BYTES + dataBytes) }
                channel.write(ByteBuffer.wrap(wavHeader(dataBytes)), 0)
            }
        } catch (_: Exception) {
            failed = true
        } finally {
            wavAmplitude = 0f
            try { ar.stop() } catch (_: Exception) {}
            try { ar.release() } catch (_: Exception) {}
            audioRecord = null
            isWavRecording.set(false)
        }

        when {
            wavCancelled.get() -> {
                deletePending(uri)
                _state.value = State.Idle
            }
            dataBytes == 0L -> {
                deletePending(uri)
                _state.value = if (failed || storageFull) State.Error(R.string.dictaphone_error_save) else State.Idle
            }
            else -> {
                finalizePending(uri)
                val warning = if (storageFull) R.string.dictaphone_error_storage else 0
                _state.value = State.Completed(uri, queryDisplayName(uri), warning)
            }
        }
    }

    // ──────────────── Récupération ────────────────

    /**
     * Les enregistrements interrompus (appli tuée en route) restent « en attente » dans MediaStore, invisibles,
     * et Android finit par les effacer : on les termine au lancement. Un WAV reçoit l'en-tête qui lui manque ;
     * un M4A sans son index (écrit seulement à l'arrêt) est illisible et retiré.
     */
    fun recoverPending() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.MIME_TYPE)
        val selection = "${MediaStore.Audio.Media.IS_PENDING} = 1 AND ${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
        val args = arrayOf("%$FOLDER_NAME%")
        val found = ArrayList<Pair<Uri, String>>()
        try {
            val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val query = Bundle().apply {
                    putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                    putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
                    putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
                }
                resolver.query(collection, projection, query, null)
            } else {
                @Suppress("DEPRECATION")
                resolver.query(MediaStore.setIncludePending(collection), projection, selection, args, null)
            }
            cursor?.use { c ->
                while (c.moveToNext()) {
                    found += ContentUris.withAppendedId(collection, c.getLong(0)) to (c.getString(1) ?: "")
                }
            }
        } catch (_: Exception) { return }

        for ((uri, mime) in found) {
            if (uri in busy) continue
            val keep = try {
                resolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                    val size = pfd.statSize
                    when {
                        mime == "audio/wav" -> size > HEADER_BYTES && repairWavHeader(pfd, size)
                        else -> size > 0 && hasMp4Index(pfd)
                    }
                } ?: false
            } catch (_: Exception) { false }
            if (keep) finalizePending(uri) else deletePending(uri)
        }
    }

    private fun repairWavHeader(pfd: ParcelFileDescriptor, size: Long): Boolean {
        val dataBytes = ((size - HEADER_BYTES) and 1L.inv()).coerceAtMost(MAX_DATA_BYTES)
        if (dataBytes <= 0L) return false
        ParcelFileDescriptor.AutoCloseOutputStream(ParcelFileDescriptor.dup(pfd.fileDescriptor)).use { out ->
            out.channel.write(ByteBuffer.wrap(wavHeader(dataBytes)), 0)
        }
        return true
    }

    /** Un MP4 n'est lisible qu'avec sa boîte `moov`, que MediaRecorder n'écrit qu'à l'arrêt. */
    private fun hasMp4Index(pfd: ParcelFileDescriptor): Boolean {
        ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(pfd.fileDescriptor)).use { input ->
            val channel = input.channel
            val size = channel.size()
            val head = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
            var pos = 0L
            while (pos + 8 <= size) {
                head.clear()
                head.limit(16)
                if (channel.read(head, pos) < 8) return false
                var boxSize = head.getInt(0).toLong() and 0xFFFFFFFFL
                val type = String(byteArrayOf(head.get(4), head.get(5), head.get(6), head.get(7)), Charsets.US_ASCII)
                if (type == "moov") return true
                if (boxSize == 1L) boxSize = if (head.position() >= 16) head.getLong(8) else return false
                if (boxSize < 8L) return false
                pos += boxSize
            }
        }
        return false
    }

    // ──────────────── Helpers ────────────────

    private fun createMediaStoreEntry(displayName: String, mimeType: String, mode: String): Pair<Uri?, ParcelFileDescriptor?> {
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
        val inserted = try { resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cv) } catch (_: Exception) { null }
        val uri = inserted ?: return null to null
        val pfd = try { resolver.openFileDescriptor(uri, mode) } catch (_: Exception) { null }
        if (pfd == null) { deletePending(uri); return null to null }
        busy += uri
        return uri to pfd
    }

    private fun finalizePending(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cv = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
            try { context.contentResolver.update(uri, cv, null, null) } catch (_: Exception) {}
        }
        busy -= uri
    }

    private fun deletePending(uri: Uri) {
        try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
        busy -= uri
    }

    private fun queryDisplayName(uri: Uri): String {
        return try {
            context.contentResolver.query(
                uri, arrayOf(MediaStore.Audio.Media.DISPLAY_NAME), null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else "" } ?: ""
        } catch (_: Exception) { "" }
    }

    /** En-tête WAV 16 bits mono ; les tailles sont des entiers non signés sur 32 bits. */
    private fun wavHeader(dataBytes: Long): ByteArray {
        val bb = ByteBuffer.allocate(HEADER_BYTES.toInt()).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray(Charsets.US_ASCII))
        bb.putInt((dataBytes + 36).toInt())
        bb.put("WAVE".toByteArray(Charsets.US_ASCII))
        bb.put("fmt ".toByteArray(Charsets.US_ASCII))
        bb.putInt(16)
        bb.putShort(1)
        bb.putShort(1)
        bb.putInt(SAMPLE_RATE)
        bb.putInt(SAMPLE_RATE * 2)
        bb.putShort(2)
        bb.putShort(16)
        bb.put("data".toByteArray(Charsets.US_ASCII))
        bb.putInt(dataBytes.toInt())
        return bb.array()
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

    companion object {
        const val FOLDER_NAME = "A2U_Dictaphone"
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val HEADER_BYTES = 44L
        /** Plus grande taille de données qu'un en-tête WAV sait annoncer, arrondie à un échantillon. */
        private const val MAX_DATA_BYTES = 0xFFFFFFFFL - 36L - 1L

        /** Les fichiers en cours d'écriture, que [recoverPending] ne doit pas toucher. */
        private val busy: MutableSet<Uri> = Collections.synchronizedSet(HashSet())
    }
}
