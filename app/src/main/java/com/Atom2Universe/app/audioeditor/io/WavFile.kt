package com.Atom2Universe.app.audioeditor.io

import com.Atom2Universe.app.audioeditor.core.SampleProvider
import com.Atom2Universe.app.audioeditor.core.Source
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Ce qu'il faut savoir d'un fichier WAV pour en lire les échantillons. */
class WavInfo(
    val channels: Int,
    val sampleRate: Int,
    val bitsPerSample: Int,
    /** Vrai pour des flottants 32 bits (format 3), faux pour des entiers (format 1). */
    val float: Boolean,
    val frames: Long,
    /** Position dans le fichier du premier octet de données. */
    val dataOffset: Long,
) {
    val frameBytes get() = channels * (bitsPerSample / 8)
}

object WavFile {

    private const val FORMAT_PCM = 1
    private const val FORMAT_FLOAT = 3
    private const val FORMAT_EXTENSIBLE = 0xFFFE

    /** Lit l'en-tête d'un WAV. Tolère les chunks inconnus (`LIST`, `fact`…) et une taille de données absente. */
    fun readInfo(file: File): WavInfo = RandomAccessFile(file, "r").use { raf ->
        val head = ByteArray(12)
        raf.readFully(head)
        if (String(head, 0, 4, Charsets.US_ASCII) != "RIFF" || String(head, 8, 4, Charsets.US_ASCII) != "WAVE") {
            throw IOException("Pas un fichier WAV : ${file.name}")
        }
        var tag = 0
        var channels = 0
        var rate = 0
        var bits = 0
        var pos = 12L
        val len = raf.length()
        val chunk = ByteArray(8)
        while (pos + 8 <= len) {
            raf.seek(pos)
            raf.readFully(chunk)
            val id = String(chunk, 0, 4, Charsets.US_ASCII)
            val size = ByteBuffer.wrap(chunk, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
            val body = pos + 8
            when (id) {
                "fmt " -> {
                    val b = ByteArray(minOf(size, 40L).toInt())
                    raf.readFully(b)
                    val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
                    tag = bb.short.toInt() and 0xFFFF
                    channels = bb.short.toInt() and 0xFFFF
                    rate = bb.int
                    bb.position(14)
                    bits = bb.short.toInt() and 0xFFFF
                    if (tag == FORMAT_EXTENSIBLE && b.size >= 26) tag = bb.getShort(24).toInt() and 0xFFFF
                }
                "data" -> {
                    if (channels <= 0 || bits <= 0 || rate <= 0) throw IOException("En-tête WAV incomplet : ${file.name}")
                    val isFloat = tag == FORMAT_FLOAT
                    if (tag != FORMAT_PCM && !isFloat) throw IOException("Format WAV non pris en charge ($tag)")
                    if (isFloat && bits != 32) throw IOException("Flottant WAV non pris en charge ($bits bits)")
                    if (!isFloat && bits !in intArrayOf(8, 16, 24, 32)) throw IOException("PCM WAV non pris en charge ($bits bits)")
                    // Un flux écrit sans retour en arrière annonce 0 ou 0xFFFFFFFF : la vraie taille est ce qui reste du fichier.
                    val avail = len - body
                    val dataSize = if (size == 0L || size == 0xFFFFFFFFL || size > avail) avail else size
                    val frameBytes = channels * (bits / 8)
                    return WavInfo(channels, rate, bits, isFloat, dataSize / frameBytes, body)
                }
            }
            pos = body + size + (size and 1L)
        }
        throw IOException("Pas de données dans ${file.name}")
    }
}

/**
 * Écrit un WAV en flux depuis des tampons planaires. L'en-tête est réservé au début puis corrigé à
 * la fermeture : un plantage en route laisse un fichier que [WavFile.readInfo] sait quand même relire
 * (taille déduite de la longueur du fichier).
 */
class WavWriter(file: File, val channels: Int, val sampleRate: Int, val float: Boolean) : Closeable {

    private val raf = RandomAccessFile(file, "rw")
    private var buf = ByteArray(0)
    private var closed = false

    var frames = 0L
        private set

    init {
        raf.setLength(0)
        raf.write(header(0))
    }

    /** Ajoute [n] trames. [src] a un tableau par canal. Les valeurs hors de ±1 sont écrêtées en 16 bits, conservées en flottant. */
    fun write(src: Array<FloatArray>, n: Int) {
        if (n <= 0) return
        val bytesPer = if (float) 4 else 2
        val need = n * channels * bytesPer
        if (buf.size < need) buf = ByteArray(need)
        val bb = ByteBuffer.wrap(buf, 0, need).order(ByteOrder.LITTLE_ENDIAN)
        if (float) {
            for (i in 0 until n) for (c in 0 until channels) bb.putFloat(src[c][i])
        } else {
            for (i in 0 until n) for (c in 0 until channels) {
                val v = Math.round(src[c][i] * 32768f).coerceIn(-32768, 32767)
                bb.putShort(v.toShort())
            }
        }
        raf.write(buf, 0, need)
        frames += n
    }

    private fun header(frames: Long): ByteArray {
        val bytesPer = if (float) 4 else 2
        val dataBytes = frames * channels * bytesPer
        // Le format flottant demande un chunk `fact` et un `fmt ` de 18 octets pour les lecteurs stricts.
        val fmtSize = if (float) 18 else 16
        val extra = if (float) 12 else 0
        val bb = ByteBuffer.allocate(44 + (fmtSize - 16) + extra).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray(Charsets.US_ASCII))
        bb.putInt((36 + (fmtSize - 16) + extra + dataBytes).coerceAtMost(0xFFFFFFFFL).toInt())
        bb.put("WAVE".toByteArray(Charsets.US_ASCII))
        bb.put("fmt ".toByteArray(Charsets.US_ASCII))
        bb.putInt(fmtSize)
        bb.putShort((if (float) 3 else 1).toShort())
        bb.putShort(channels.toShort())
        bb.putInt(sampleRate)
        bb.putInt(sampleRate * channels * bytesPer)
        bb.putShort((channels * bytesPer).toShort())
        bb.putShort((bytesPer * 8).toShort())
        if (float) {
            bb.putShort(0)
            bb.put("fact".toByteArray(Charsets.US_ASCII))
            bb.putInt(4)
            bb.putInt(frames.toInt())
        }
        bb.put("data".toByteArray(Charsets.US_ASCII))
        bb.putInt(dataBytes.coerceAtMost(0xFFFFFFFFL).toInt())
        return bb.array()
    }

    override fun close() {
        if (closed) return
        closed = true
        try {
            raf.seek(0)
            raf.write(header(frames))
        } finally {
            raf.close()
        }
    }
}

/** Lecture aléatoire d'un WAV en flottants planaires. Hors des bornes du fichier, on lit du silence. */
class PcmReader(file: File) : Closeable {

    val info: WavInfo = WavFile.readInfo(file)
    private val raf = RandomAccessFile(file, "r")
    private var buf = ByteArray(0)

    /** Remplit `dst[c][off until off + count]` pour chaque canal du fichier. */
    @Synchronized
    fun read(frame: Long, count: Int, dst: Array<FloatArray>, off: Int) {
        val ch = info.channels
        val usable = minOf(ch, dst.size)
        // Partie du bloc qui tombe dans le fichier.
        val from = maxOf(frame, 0L)
        val to = minOf(frame + count, info.frames)
        val lead = (from - frame).toInt().coerceIn(0, count)
        val n = (to - from).toInt().coerceAtLeast(0)
        for (c in 0 until usable) {
            java.util.Arrays.fill(dst[c], off, off + lead, 0f)
            java.util.Arrays.fill(dst[c], off + lead + n, off + count, 0f)
        }
        if (n == 0) return
        val bytesPer = info.bitsPerSample / 8
        val need = n * ch * bytesPer
        if (buf.size < need) buf = ByteArray(need)
        raf.seek(info.dataOffset + from * info.frameBytes)
        raf.readFully(buf, 0, need)
        val bb = ByteBuffer.wrap(buf, 0, need).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until n) {
            for (c in 0 until ch) {
                val v = when {
                    info.float -> bb.getFloat()
                    info.bitsPerSample == 16 -> bb.getShort() / 32768f
                    info.bitsPerSample == 8 -> ((bb.get().toInt() and 0xFF) - 128) / 128f
                    info.bitsPerSample == 24 -> {
                        val b0 = bb.get().toInt() and 0xFF
                        val b1 = bb.get().toInt() and 0xFF
                        val b2 = bb.get().toInt()
                        ((b2 shl 16) or (b1 shl 8) or b0) / 8388608f
                    }
                    else -> bb.getInt() / 2147483648f
                }
                if (c < usable) dst[c][off + lead + i] = v
            }
        }
    }

    override fun close() = raf.close()
}

/**
 * Fournit au mixeur les échantillons des sources d'un projet, lues dans `<dossier>/<source.file>`.
 * Les fichiers sont ouverts à la demande et gardés ouverts jusqu'à [close].
 */
class FileSampleProvider(private val projectDir: File) : SampleProvider, Closeable {

    private val readers = HashMap<String, PcmReader>()

    @Synchronized
    private fun reader(source: Source): PcmReader =
        readers.getOrPut(source.id) { PcmReader(File(projectDir, source.file)) }

    override fun read(source: Source, frame: Long, count: Int, dst: Array<FloatArray>, off: Int) {
        reader(source).read(frame, count, dst, off)
    }

    @Synchronized
    override fun close() {
        readers.values.forEach { runCatching { it.close() } }
        readers.clear()
    }
}
