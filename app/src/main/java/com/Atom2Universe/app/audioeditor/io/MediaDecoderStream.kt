package com.Atom2Universe.app.audioeditor.io

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Décode un fichier audio (ou la bande son d'une vidéo) avec `MediaExtractor` + `MediaCodec` et le
 * livre en flottants planaires par blocs, à la fréquence et au nombre de canaux **réellement produits**
 * par le décodeur (un AAC avec SBR sort au double de la fréquence annoncée par le conteneur : on lit
 * donc le format de sortie, pas celui d'entrée).
 *
 * Tout se passe sur le fil appelant, sans file d'attente ni fil à part : `MediaCodec` n'est pas fait
 * pour être partagé. Ouvrir avec [open], lire avec [read], toujours fermer.
 */
class MediaDecoderStream private constructor(
    private val extractor: MediaExtractor,
    private val codec: MediaCodec,
    inputFormat: MediaFormat,
) : PcmStream {

    override var channels: Int = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        private set
    override var sampleRate: Int = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        private set
    override var estimatedFrames: Long = -1
        private set

    private val info = MediaCodec.BufferInfo()
    private var inputDone = false
    private var outputDone = false
    private var formatKnown = false
    private var floatOut = false

    // Les octets PCM du dernier tampon de sortie, recopiés pour pouvoir rendre le tampon au décodeur tout de suite.
    private var pending = ByteArray(0)
    private var pendingPos = 0
    private var pendingLen = 0

    init {
        if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
            estimatedFrames = inputFormat.getLong(MediaFormat.KEY_DURATION) * sampleRate / 1_000_000L
        }
    }

    private fun adoptOutputFormat(f: MediaFormat) {
        val ch = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        if (formatKnown && (ch != channels || rate != sampleRate)) {
            throw IOException("Le décodeur a changé de format en cours de fichier")
        }
        if (rate != sampleRate && estimatedFrames > 0) estimatedFrames = estimatedFrames * rate / sampleRate
        channels = ch
        sampleRate = rate
        floatOut = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
            f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
        formatKnown = true
    }

    /** Fait avancer le décodeur d'un pas : un peu d'entrée, un peu de sortie. */
    private fun pump() {
        if (!inputDone) {
            val i = codec.dequeueInputBuffer(0)
            if (i >= 0) {
                val buf = codec.getInputBuffer(i) ?: throw IOException("Tampon d'entrée indisponible")
                val size = extractor.readSampleData(buf, 0)
                if (size < 0) {
                    codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    inputDone = true
                } else {
                    codec.queueInputBuffer(i, 0, size, extractor.sampleTime, 0)
                    extractor.advance()
                }
            }
        }
        when (val o = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
            MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> adoptOutputFormat(codec.outputFormat)
            MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
            else -> if (o >= 0) {
                // Un tampon sans changement de format annoncé : le format d'entrée était le bon.
                if (!formatKnown) formatKnown = true
                if (info.size > 0) {
                    val out = codec.getOutputBuffer(o)
                    if (out != null) {
                        if (pending.size < info.size) pending = ByteArray(info.size)
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        out.get(pending, 0, info.size)
                        pendingPos = 0
                        pendingLen = info.size
                    }
                }
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                codec.releaseOutputBuffer(o, false)
            }
        }
    }

    private fun prime() {
        val deadline = System.nanoTime() + PRIME_TIMEOUT_MS * 1_000_000L
        while (!formatKnown && !outputDone) {
            pump()
            if (System.nanoTime() > deadline) throw IOException("Le décodeur ne répond pas")
        }
    }

    override fun read(dst: Array<FloatArray>, n: Int): Int {
        val frameBytes = channels * (if (floatOut) 4 else 2)
        var done = 0
        var idleSince = 0L
        while (done < n) {
            if (pendingPos >= pendingLen) {
                if (outputDone) break
                pump()
                if (pendingPos >= pendingLen) {
                    // Rien de neuf : si le décodeur n'avance plus du tout, on abandonne plutôt que de tourner sans fin.
                    if (idleSince == 0L) idleSince = System.nanoTime()
                    else if (System.nanoTime() - idleSince > STALL_TIMEOUT_MS * 1_000_000L) throw IOException("Le décodeur est bloqué")
                    continue
                }
                idleSince = 0L
            }
            val frames = minOf(n - done, (pendingLen - pendingPos) / frameBytes)
            if (frames <= 0) { pendingPos = pendingLen; continue }
            val bb = ByteBuffer.wrap(pending, pendingPos, frames * frameBytes).order(ByteOrder.nativeOrder())
            if (floatOut) {
                val fb = bb.asFloatBuffer()
                for (i in 0 until frames) for (c in 0 until channels) dst[c][done + i] = fb.get()
            } else {
                val sb = bb.asShortBuffer()
                for (i in 0 until frames) for (c in 0 until channels) dst[c][done + i] = sb.get() / 32768f
            }
            pendingPos += frames * frameBytes
            done += frames
        }
        return done
    }

    override fun close() {
        runCatching { codec.stop() }
        runCatching { codec.release() }
        runCatching { extractor.release() }
    }

    companion object {
        private const val TIMEOUT_US = 10_000L
        private const val PRIME_TIMEOUT_MS = 10_000L
        private const val STALL_TIMEOUT_MS = 10_000L

        /** @throws IOException si le fichier n'a pas de piste audio ou si aucun décodeur ne la prend en charge */
        fun open(context: Context, uri: Uri): MediaDecoderStream {
            val extractor = MediaExtractor()
            var codec: MediaCodec? = null
            try {
                extractor.setDataSource(context, uri, null)
                var index = -1
                for (i in 0 until extractor.trackCount) {
                    if (extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { index = i; break }
                }
                if (index < 0) throw IOException("Pas de piste audio")
                extractor.selectTrack(index)
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: throw IOException("Type de fichier inconnu")
                if (!format.containsKey(MediaFormat.KEY_SAMPLE_RATE) || !format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    throw IOException("Format audio incomplet")
                }
                codec = MediaCodec.createDecoderByType(mime)
                codec.configure(format, null, null, 0)
                codec.start()
                return MediaDecoderStream(extractor, codec, format).also { it.prime() }
            } catch (e: Throwable) {
                runCatching { codec?.release() }
                runCatching { extractor.release() }
                throw if (e is IOException) e else IOException("Décodage impossible : ${e.message}", e)
            }
        }
    }
}
