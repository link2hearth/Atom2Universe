package com.Atom2Universe.app.audioeditor.core

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Les crêtes d'une source, pour dessiner sa forme d'onde sans relire l'audio : pour chaque bloc de
 * [blockSize] trames et chaque canal, le plus petit et le plus grand échantillon, sur un octet
 * (±127 = ±1,0). Une heure de stéréo tient dans environ 2,5 Mo.
 *
 * Le minimum est arrondi vers le bas et le maximum vers le haut : la silhouette dessinée englobe
 * toujours le vrai signal, elle ne le rabote jamais.
 */
class PeakData(val channels: Int, val frames: Long, val blockSize: Int, private val data: ByteArray) {

    val blocks: Int get() = ((frames + blockSize - 1) / blockSize).toInt()

    fun min(block: Int, channel: Int): Float = data[(block * channels + channel) * 2] / 127f
    fun max(block: Int, channel: Int): Float = data[(block * channels + channel) * 2 + 1] / 127f

    /**
     * Remplit, pour [pixels] colonnes, le min et le max du [channel] : la colonne `p` couvre les
     * trames `[startFrame + p·framesPerPixel, startFrame + (p+1)·framesPerPixel)`. Hors de la source,
     * une colonne vaut 0. À utiliser tant que [framesPerPixel] ≥ [blockSize] ; en dessous, la
     * précision d'un bloc ne suffit plus et il faut lire l'audio ([columnsFromSamples]).
     */
    fun columns(channel: Int, startFrame: Long, framesPerPixel: Double, pixels: Int, outMin: FloatArray, outMax: FloatArray) {
        val total = blocks
        for (p in 0 until pixels) {
            val f0 = startFrame + p * framesPerPixel
            val f1 = f0 + framesPerPixel
            var b0 = floor(f0 / blockSize).toInt()
            var b1 = ceil(f1 / blockSize).toInt()
            if (b1 <= b0) b1 = b0 + 1
            b0 = b0.coerceAtLeast(0)
            b1 = b1.coerceAtMost(total)
            if (b0 >= b1 || f1 <= 0.0 || f0 >= frames) {
                outMin[p] = 0f; outMax[p] = 0f
                continue
            }
            var lo = 127
            var hi = -127
            for (b in b0 until b1) {
                val i = (b * channels + channel) * 2
                if (data[i] < lo) lo = data[i].toInt()
                if (data[i + 1] > hi) hi = data[i + 1].toInt()
            }
            outMin[p] = lo / 127f
            outMax[p] = hi / 127f
        }
    }

    fun write(out: OutputStream) {
        val d = DataOutputStream(out)
        d.writeInt(MAGIC)
        d.writeInt(VERSION)
        d.writeInt(channels)
        d.writeInt(blockSize)
        d.writeLong(frames)
        d.writeInt(data.size)
        d.write(data)
        d.flush()
    }

    companion object {
        private const val MAGIC = 0x41325042 // « A2PB »
        private const val VERSION = 1

        fun read(input: InputStream): PeakData {
            val d = DataInputStream(input)
            if (d.readInt() != MAGIC) throw IOException("Fichier de crêtes invalide")
            if (d.readInt() != VERSION) throw IOException("Version de crêtes inconnue")
            val channels = d.readInt()
            val blockSize = d.readInt()
            val frames = d.readLong()
            val size = d.readInt()
            if (channels !in 1..8 || blockSize <= 0 || size < 0) throw IOException("En-tête de crêtes invalide")
            val data = ByteArray(size)
            d.readFully(data)
            val expected = ((frames + blockSize - 1) / blockSize) * channels * 2
            if (expected != size.toLong()) throw IOException("Crêtes tronquées")
            return PeakData(channels, frames, blockSize, data)
        }

        /**
         * Les colonnes calculées directement sur des échantillons (zoom serré, moins d'un bloc par
         * pixel). [samples] contient les trames à partir de [firstFrame] ; le reste est du silence.
         */
        fun columnsFromSamples(
            samples: FloatArray,
            firstFrame: Long,
            startFrame: Long,
            framesPerPixel: Double,
            pixels: Int,
            outMin: FloatArray,
            outMax: FloatArray,
        ) {
            for (p in 0 until pixels) {
                val f0 = startFrame + p * framesPerPixel
                val a = floor(f0).toLong()
                val b = maxOf(a + 1, floor(f0 + framesPerPixel).toLong())
                var lo = Float.MAX_VALUE
                var hi = -Float.MAX_VALUE
                for (f in a until b) {
                    val i = f - firstFrame
                    val v = if (i < 0 || i >= samples.size) 0f else samples[i.toInt()]
                    if (v < lo) lo = v
                    if (v > hi) hi = v
                }
                outMin[p] = lo.coerceIn(-1f, 1f)
                outMax[p] = hi.coerceIn(-1f, 1f)
            }
        }
    }
}

/** Accumule les crêtes au fil de l'eau : un seul passage sur l'audio, à l'import ou pendant un rendu. */
class PeakBuilder(val channels: Int, val blockSize: Int = DEFAULT_BLOCK) {

    private val out = ByteArrayOutputStream()
    private val lo = FloatArray(channels)
    private val hi = FloatArray(channels)
    private var inBlock = 0

    var frames = 0L
        private set

    init {
        resetBlock()
    }

    private fun resetBlock() {
        java.util.Arrays.fill(lo, Float.MAX_VALUE)
        java.util.Arrays.fill(hi, -Float.MAX_VALUE)
        inBlock = 0
    }

    private fun flushBlock() {
        for (c in 0 until channels) {
            out.write(floor(lo[c].coerceIn(-1f, 1f) * 127f).toInt().coerceIn(-127, 127))
            out.write(ceil(hi[c].coerceIn(-1f, 1f) * 127f).toInt().coerceIn(-127, 127))
        }
        resetBlock()
    }

    /** Ajoute [n] trames de [src] (un tableau par canal) à partir de l'indice [off]. */
    fun add(src: Array<FloatArray>, n: Int, off: Int = 0) {
        for (i in 0 until n) {
            for (c in 0 until channels) {
                val v = src[c][off + i]
                if (v < lo[c]) lo[c] = v
                if (v > hi[c]) hi[c] = v
            }
            if (++inBlock == blockSize) flushBlock()
        }
        frames += n
    }

    fun finish(): PeakData {
        if (inBlock > 0) flushBlock()
        return PeakData(channels, frames, blockSize, out.toByteArray())
    }

    companion object {
        const val DEFAULT_BLOCK = 256
    }
}
