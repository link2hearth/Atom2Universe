package com.Atom2Universe.app.audioeditor.io

import com.Atom2Universe.app.audioeditor.core.PeakBuilder
import com.Atom2Universe.app.audioeditor.core.Resampler
import com.Atom2Universe.app.audioeditor.core.Source
import com.Atom2Universe.app.audioeditor.dsp.RunContext
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * De l'audio décodé, à sa fréquence d'origine, lu par blocs. C'est le point de rencontre entre les
 * décodeurs (qui dépendent d'Android : `MediaCodec`, FFmpeg) et [SourceImporter] (qui n'en dépend pas
 * et se teste en JVM).
 */
interface PcmStream : Closeable {
    val channels: Int
    val sampleRate: Int

    /** Longueur annoncée, pour la barre de progression seulement ; −1 si inconnue. Peut être approximative. */
    val estimatedFrames: Long

    /** Remplit `dst[c][0 until n]` pour chaque canal ; rend le nombre de trames lues, 0 à la fin. */
    fun read(dst: Array<FloatArray>, n: Int): Int
}

/** Lit un WAV de bout en bout (fichier venu de l'enregistreur, de FFmpeg, ou d'un ancien projet). */
class WavPcmStream(file: File) : PcmStream {
    private val reader = PcmReader(file)
    private var pos = 0L

    override val channels get() = reader.info.channels
    override val sampleRate get() = reader.info.sampleRate
    override val estimatedFrames get() = reader.info.frames

    override fun read(dst: Array<FloatArray>, n: Int): Int {
        val m = minOf(n.toLong(), reader.info.frames - pos).toInt()
        if (m <= 0) return 0
        reader.read(pos, m, dst, 0)
        pos += m
        return m
    }

    override fun close() = reader.close()
}

object SourceImporter {

    const val BLOCK = 16384

    fun newSourceId() = "s" + UUID.randomUUID().toString().replace("-", "").take(12)

    /**
     * Écrit [stream] dans `<projectDir>/sources/<id>.wav` (16 bits) avec ses crêtes, à la fréquence
     * [targetRate] (rééchantillonnage par sinc fenêtré si elle diffère), et rend la [Source].
     *
     * Le résultat a un ou deux canaux : le mono reste mono, le reste devient stéréo. Au-delà de deux
     * canaux (5.1…), les canaux de rang pair vont à gauche et ceux de rang impair à droite, moyennés.
     *
     * Une annulation ou une erreur efface le fichier en cours d'écriture et relance l'exception.
     * @throws IOException si le flux est vide
     */
    fun import(
        stream: PcmStream,
        projectDir: File,
        targetRate: Int,
        ctx: RunContext,
        newId: () -> String = ::newSourceId,
    ): Source {
        val inCh = stream.channels
        require(inCh >= 1) { "aucun canal" }
        val outCh = if (inCh == 1) 1 else 2
        val id = newId()
        val file = File(projectDir, "sources/$id.wav")
        file.parentFile?.mkdirs()

        val resampler = if (stream.sampleRate != targetRate) Resampler(stream.sampleRate, targetRate, outCh) else null
        val inBuf = Array(inCh) { FloatArray(BLOCK) }
        val mixed = if (inCh == outCh) inBuf else Array(outCh) { FloatArray(BLOCK) }
        val rsBuf = resampler?.let { r -> Array(outCh) { FloatArray(r.maxOutputFor(BLOCK)) } }
        val tail = resampler?.let { r -> Array(outCh) { FloatArray(r.maxOutputFor(0) + 4) } }
        val peaks = PeakBuilder(outCh)
        var consumed = 0L
        val total = stream.estimatedFrames

        try {
            WavWriter(file, outCh, targetRate, float = false).use { w ->
                fun emit(src: Array<FloatArray>, n: Int) {
                    if (n <= 0) return
                    w.write(src, n)
                    peaks.add(src, n)
                }
                while (true) {
                    ctx.checkCancelled()
                    val n = stream.read(inBuf, BLOCK)
                    if (n <= 0) break
                    if (inCh > 2) downmix(inBuf, inCh, mixed, n)
                    if (resampler != null) {
                        val m = resampler.process(mixed, n, rsBuf!!)
                        emit(rsBuf, m)
                    } else {
                        emit(mixed, n)
                    }
                    consumed += n
                    if (total > 0) ctx.report(consumed.toFloat() / total)
                }
                if (resampler != null) emit(tail!!, resampler.finish(tail))
            }
        } catch (e: Throwable) {
            file.delete()
            throw e
        }

        val frames = peaks.frames
        if (frames <= 0L) {
            file.delete()
            throw IOException("Aucun échantillon audio")
        }
        PeakFiles.save(peaks.finish(), File(projectDir, "sources/$id.peaks"))
        ctx.report(1f)
        return Source(id, "sources/$id.wav", outCh, frames, targetRate, float = false)
    }

    /** Ramène [inCh] canaux à deux : rang pair à gauche, rang impair à droite, moyennés. */
    private fun downmix(src: Array<FloatArray>, inCh: Int, dst: Array<FloatArray>, n: Int) {
        val nl = (inCh + 1) / 2
        val nr = inCh / 2
        for (i in 0 until n) {
            var l = 0f
            var r = 0f
            for (c in 0 until inCh) if (c % 2 == 0) l += src[c][i] else r += src[c][i]
            dst[0][i] = l / nl
            dst[1][i] = r / nr
        }
    }
}
