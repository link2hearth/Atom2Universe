package com.Atom2Universe.app.audioeditor.dsp

import com.Atom2Universe.app.audioeditor.core.Mixer
import com.Atom2Universe.app.audioeditor.core.Project
import com.Atom2Universe.app.audioeditor.core.SampleProvider
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Le mixage de `[from, to)` d'un projet, lu comme un flux stéréo (ce qu'on entend, sourdine et solo compris, sans écrêtage). */
class MixReader(private val project: Project, provider: SampleProvider, private val from: Long, private val to: Long) : FrameReader {
    private val mixer = Mixer(provider)
    private var pos = from
    override val channels = 2
    override val frames: Long = max(0L, to - from)

    override fun read(dst: Array<FloatArray>, n: Int): Int {
        val m = min(n.toLong(), to - pos).toInt()
        if (m <= 0) return 0
        mixer.render(project, pos, m, dst[0], dst[1])
        pos += m
        return m
    }
}

/** Les mesures d'une plage : crêtes et niveau efficace (linéaires, 1 = 0 dB), saturations, décalage continu. */
class SignalStats(
    val frames: Long,
    val peakL: Float,
    val peakR: Float,
    val rms: Float,
    /** Nombre d'échantillons à pleine échelle (|x| ≥ 0,999) : un son écrêté en aligne des dizaines de suite. */
    val clipped: Long,
    /** Début (en trames, depuis le début de la plage) de chaque série de saturations, au plus [Analysis.MAX_CLIP_RUNS]. */
    val clipRuns: List<Long>,
    /** Moyenne du signal : 0 si le son est centré. */
    val dc: Float,
) {
    val peak: Float get() = max(peakL, peakR)
}

object Analysis {

    const val MAX_CLIP_RUNS = 200
    private const val CLIP_LEVEL = 0.999f

    /** Une seule passe sur [reader] (une ou deux voies ; au-delà, seules les deux premières comptent pour les crêtes). */
    fun stats(reader: FrameReader, ctx: RunContext): SignalStats {
        val ch = reader.channels
        val buf = Array(ch) { FloatArray(BlockEffect.BLOCK) }
        var peakL = 0f
        var peakR = 0f
        var sumSq = 0.0
        var sum = 0.0
        var count = 0L
        var clipped = 0L
        val runs = ArrayList<Long>()
        var lastClipFrame = -1_000_000_000_000L
        var pos = 0L
        while (true) {
            ctx.checkCancelled()
            val n = reader.read(buf, BlockEffect.BLOCK)
            if (n <= 0) break
            for (c in 0 until ch) {
                val x = buf[c]
                var pk = 0f
                for (i in 0 until n) {
                    val v = x[i]
                    val a = abs(v)
                    if (a > pk) pk = a
                    sumSq += v.toDouble() * v
                    sum += v
                    if (a >= CLIP_LEVEL) {
                        clipped++
                        val f = pos + i
                        // Les saturations à moins d'un dixième de seconde l'une de l'autre forment une seule série.
                        if (f - lastClipFrame > ctx.sampleRate / 10 && runs.size < MAX_CLIP_RUNS) runs += f
                        lastClipFrame = f
                    }
                }
                if (c == 0) peakL = max(peakL, pk) else if (c == 1) peakR = max(peakR, pk)
            }
            count += n.toLong() * ch
            pos += n
            ctx.report(if (reader.frames > 0) pos.toFloat() / reader.frames else 1f)
        }
        if (ch == 1) peakR = peakL
        val c = max(1L, count)
        return SignalStats(pos, peakL, peakR, sqrt(sumSq / c).toFloat(), clipped, runs, (sum / c).toFloat())
    }

    /**
     * Le spectre moyen de [reader] (voies mélangées en une) : des fenêtres de Hann de [fftSize] échantillons, réparties
     * sur toute la plage (au plus [maxWindows]), dont on moyenne la puissance. Rend `fftSize / 2 + 1` valeurs en dB, la
     * pleine échelle d'un sinus valant 0 dB, et jamais en dessous de −120.
     */
    fun spectrum(reader: FrameReader, ctx: RunContext, fftSize: Int = 4096, maxWindows: Int = 200): FloatArray {
        val ch = reader.channels
        val buf = Array(ch) { FloatArray(BlockEffect.BLOCK) }
        val fft = Fft(fftSize)
        val hann = DoubleArray(fftSize) { 0.5 - 0.5 * cos(2.0 * PI * it / fftSize) }
        val ring = FloatArray(fftSize)
        val re = DoubleArray(fftSize)
        val im = DoubleArray(fftSize)
        val acc = DoubleArray(fftSize / 2 + 1)
        val hop = max(fftSize / 2L, (reader.frames - fftSize) / maxWindows)
        var windows = 0
        var pos = 0L

        fun analyseWindow() {
            // L'anneau contient les derniers fftSize échantillons ; le plus ancien est à l'index pos % fftSize.
            val start = (pos % fftSize).toInt()
            for (i in 0 until fftSize) {
                re[i] = ring[(start + i) % fftSize] * hann[i]
                im[i] = 0.0
            }
            fft.transform(re, im)
            for (k in acc.indices) acc[k] += re[k] * re[k] + im[k] * im[k]
            windows++
        }

        while (true) {
            ctx.checkCancelled()
            val n = reader.read(buf, BlockEffect.BLOCK)
            if (n <= 0) break
            for (i in 0 until n) {
                var s = 0f
                for (c in 0 until ch) s += buf[c][i]
                ring[(pos % fftSize).toInt()] = s / ch
                pos++
                if (pos >= fftSize && (pos - fftSize) % hop == 0L) analyseWindow()
            }
            ctx.report(if (reader.frames > 0) pos.toFloat() / reader.frames else 1f)
        }
        // Une plage plus courte qu'une fenêtre : on la complète de silence (l'anneau n'a reçu que ses premiers échantillons).
        if (windows == 0 && pos > 0) {
            for (i in 0 until fftSize) { re[i] = ring[i] * hann[i]; im[i] = 0.0 }
            fft.transform(re, im)
            for (k in acc.indices) acc[k] += re[k] * re[k] + im[k] * im[k]
            windows = 1
        }
        val norm = fftSize / 4.0 // un sinus d'amplitude 1 donne un pic de N/4 avec une fenêtre de Hann
        return FloatArray(acc.size) { k ->
            if (windows == 0) -120f else {
                val mag = sqrt(acc[k] / windows) / norm
                max(-120.0, 20 * log10(mag + 1e-9)).toFloat()
            }
        }
    }
}
