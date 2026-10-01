package com.Atom2Universe.app.audioeditor.dsp

import com.Atom2Universe.app.audioeditor.core.Resampler
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Effets qui changent la durée : vitesse (la hauteur suit), tempo (la hauteur reste), hauteur
 * (la durée reste), Paulstretch (étirement extrême) et suppression des silences. Tous travaillent
 * en flux et déclarent `changesLength` : ce qui suit la plage se décale de la différence.
 */

/** Pousse des blocs dans un [Resampler] et les rend à un [FrameWriter]. */
internal class ResamplePipe(private val channels: Int, factor: Double) {
    // `factor` = vitesse de lecture : > 1 raccourcit et élève la hauteur.
    private val r = Resampler((factor * 10000).roundToInt().coerceAtLeast(1), 10000, channels)
    private var out = Array(channels) { FloatArray(0) }

    private fun ensure(cap: Int) {
        if (out[0].size < cap) out = Array(channels) { FloatArray(cap) }
    }

    fun push(input: Array<FloatArray>, n: Int, sink: FrameWriter) {
        ensure(r.maxOutputFor(n))
        val m = r.process(input, n, out)
        if (m > 0) sink.write(out, m)
    }

    fun finish(sink: FrameWriter) {
        ensure(r.maxOutputFor(0) + 4)
        val m = r.finish(out)
        if (m > 0) sink.write(out, m)
    }
}

/** Joue plus vite ([factor] > 1) ou plus lentement : durée et hauteur changent ensemble, comme une bande qu'on accélère. */
class ChangeSpeed(factor: Float) : Effect() {
    private val factor = factor.coerceIn(0.1f, 10f).toDouble()
    override val changesLength get() = true

    override fun run(openInput: () -> FrameReader, out: FrameWriter, ctx: RunContext) {
        val input = openInput()
        val buf = Array(input.channels) { FloatArray(BlockEffect.BLOCK) }
        val pipe = ResamplePipe(input.channels, factor)
        val total = maxOf(1L, input.frames)
        var done = 0L
        while (true) {
            ctx.checkCancelled()
            val n = input.read(buf, BlockEffect.BLOCK)
            if (n <= 0) break
            pipe.push(buf, n, out)
            done += n
            ctx.report(done.toFloat() / total)
        }
        pipe.finish(out)
    }
}

/**
 * WSOLA (Waveform-Similarity Overlap-Add) : change la durée sans toucher à la hauteur.
 *
 * Des fenêtres de Hann de ~22 ms, recouvertes à moitié en sortie, sont prises dans l'entrée à un pas
 * `hop · tempo` ; chacune est décalée de ±8 ms vers l'endroit où elle ressemble le plus à la
 * continuation naturelle de la précédente (corrélation normalisée, cherchée d'abord sur un signal
 * décimé par 4 puis affinée), si bien que les périodes se recollent sans à-coup. Le décalage est
 * choisi sur le mélange mono et appliqué à tous les canaux (l'image stéréo ne se déphase pas).
 *
 * Alimenté par [push], vidé par [finish] ; la sortie dure environ `entrée / tempo` à une fenêtre près.
 */
internal class Wsola(private val ch: Int, sampleRate: Int, private val tempo: Double) {

    private val hs: Int = (sampleRate * 0.011).toInt().let { maxOf(64, it - it % 4) }
    private val n = hs * 2
    private val delta = maxOf(16, (sampleRate * 0.008).toInt())
    private val win = FloatArray(n) { (0.5 - 0.5 * cos(2.0 * PI * it / n)).toFloat() }

    private var buf = Array(ch) { FloatArray(0) }
    private var base = 0L
    private var len = 0
    private var totalIn = 0L
    private var finished = false
    private var started = false
    private var k = 1L
    private var prevPos = 0L
    private val pending = Array(ch) { FloatArray(hs) }
    private val block = Array(ch) { FloatArray(hs) }

    private val tpl = FloatArray(hs)
    private val reg = FloatArray(hs + 2 * delta + 8)
    private val tplD = FloatArray(hs / D + 1)
    private val regD = FloatArray((hs + 2 * delta + 8) / D + 1)
    private val cum = DoubleArray(regD.size + 1)

    private fun sample(c: Int, idx: Long): Float =
        if (idx < base || idx >= base + len) 0f else buf[c][(idx - base).toInt()]

    private fun append(input: Array<FloatArray>, count: Int) {
        if (count <= 0) return
        if (buf[0].size < len + count) buf = Array(ch) { c -> buf[c].copyOf(maxOf(len + count, buf[c].size * 2, 4 * n)) }
        for (c in 0 until ch) System.arraycopy(input[c], 0, buf[c], len, count)
        len += count
    }

    fun push(input: Array<FloatArray>, count: Int, out: FrameWriter) {
        append(input, count)
        totalIn += count
        produce(out)
    }

    fun finish(out: FrameWriter) {
        finished = true
        produce(out)
        if (started) out.write(pending, hs) // la demi-fenêtre descendante de la dernière trame
    }

    private fun produce(out: FrameWriter) {
        while (true) {
            if (!started) {
                if (!finished && base + len < n) return
                if (totalIn == 0L) return
                for (c in 0 until ch) {
                    for (i in 0 until hs) block[c][i] = sample(c, i.toLong())
                    for (i in 0 until hs) pending[c][i] = sample(c, (hs + i).toLong()) * win[hs + i]
                }
                out.write(block, hs)
                started = true
                k = 1
                prevPos = 0
                drop()
                continue
            }
            val nominal = (k * hs * tempo).roundToInt().toLong()
            val need = maxOf(nominal + delta + n, prevPos + 2 * hs)
            if (!finished && base + len < need) return
            if (finished && nominal >= totalIn) return
            val pos = choose(nominal)
            for (c in 0 until ch) {
                for (i in 0 until hs) block[c][i] = pending[c][i] + sample(c, pos + i) * win[i]
                for (i in 0 until hs) pending[c][i] = sample(c, pos + hs + i) * win[hs + i]
            }
            out.write(block, hs)
            prevPos = pos
            k++
            drop()
        }
    }

    /** Oublie l'entrée dont plus aucune recherche n'aura besoin. */
    private fun drop() {
        val nextNominal = (k * hs * tempo).roundToInt().toLong()
        val keepFrom = minOf(prevPos + hs, nextNominal - delta)
        val shift = (keepFrom - base).coerceIn(0L, len.toLong()).toInt()
        if (shift > 0) {
            for (c in 0 until ch) System.arraycopy(buf[c], shift, buf[c], 0, len - shift)
            len -= shift
            base += shift
        }
    }

    /** Position d'entrée de la prochaine fenêtre : la plus ressemblante à la suite naturelle de la précédente. */
    private fun choose(nominal: Long): Long {
        val lo = maxOf(base, nominal - delta)
        val hi = maxOf(lo, nominal + delta)
        val ts = prevPos + hs
        val span = (hi - lo).toInt() + hs
        for (j in 0 until hs) tpl[j] = monoAt(ts + j)
        for (j in 0 until span) reg[j] = monoAt(lo + j)

        val tn = hs / D
        for (j in 0 until tn) {
            var s = 0f
            for (q in 0 until D) s += tpl[j * D + q]
            tplD[j] = s / D
        }
        val rn = span / D
        for (m in 0 until rn) {
            var s = 0f
            for (q in 0 until D) s += reg[m * D + q]
            regD[m] = s / D
        }
        cum[0] = 0.0
        for (m in 0 until rn) cum[m + 1] = cum[m] + regD[m].toDouble() * regD[m]

        // Recherche grossière sur le signal décimé.
        var bestC = 0
        var bestScore = -Double.MAX_VALUE
        val candidates = ((hi - lo).toInt() / D) + 1
        for (cd in 0 until candidates) {
            if (cd + tn > rn) break
            var corr = 0.0
            for (j in 0 until tn) corr += tplD[j].toDouble() * regD[cd + j]
            val e = cum[cd + tn] - cum[cd]
            val score = corr / sqrt(e + 1e-9)
            if (score > bestScore) { bestScore = score; bestC = cd }
        }

        // Affinage à pleine résolution autour du meilleur candidat.
        val center = bestC * D
        var best = center
        bestScore = -Double.MAX_VALUE
        for (cand in maxOf(0, center - D)..minOf((hi - lo).toInt(), center + D)) {
            var corr = 0.0
            var e = 0.0
            for (j in 0 until hs) {
                val v = reg[cand + j]
                corr += tpl[j].toDouble() * v
                e += v.toDouble() * v
            }
            val score = corr / sqrt(e + 1e-9)
            if (score > bestScore) { bestScore = score; best = cand }
        }
        return lo + best
    }

    private fun monoAt(idx: Long): Float {
        var s = 0f
        for (c in 0 until ch) s += sample(c, idx)
        return s / ch
    }

    companion object {
        private const val D = 4
    }
}

/** Accélère ([factor] > 1) ou ralentit sans changer la hauteur (WSOLA). */
class ChangeTempo(factor: Float) : Effect() {
    private val factor = factor.coerceIn(0.25f, 4f).toDouble()
    override val changesLength get() = true

    override fun run(openInput: () -> FrameReader, out: FrameWriter, ctx: RunContext) {
        val input = openInput()
        val buf = Array(input.channels) { FloatArray(BlockEffect.BLOCK) }
        val w = Wsola(input.channels, ctx.sampleRate, factor)
        val total = maxOf(1L, input.frames)
        var done = 0L
        while (true) {
            ctx.checkCancelled()
            val n = input.read(buf, BlockEffect.BLOCK)
            if (n <= 0) break
            w.push(buf, n, out)
            done += n
            ctx.report(done.toFloat() / total)
        }
        w.finish(out)
    }
}

/**
 * Change la hauteur de [semitones] demi-tons (±24) sans changer la durée : étire d'abord de `r = 2^(demi-tons/12)`
 * par WSOLA, puis lit `r` fois plus vite pour revenir à la durée d'origine (la hauteur est alors multipliée par `r`).
 */
class ChangePitch(semitones: Float) : Effect() {
    private val ratio = 2.0.pow(semitones.coerceIn(-24f, 24f) / 12.0)
    // La durée sort à quelques millisecondes près : on laisse la suite se recoller.
    override val changesLength get() = true

    override fun run(openInput: () -> FrameReader, out: FrameWriter, ctx: RunContext) {
        val input = openInput()
        val ch = input.channels
        val buf = Array(ch) { FloatArray(BlockEffect.BLOCK) }
        val pipe = ResamplePipe(ch, ratio)
        val toPipe = object : FrameWriter {
            override fun write(src: Array<FloatArray>, n: Int) = pipe.push(src, n, out)
        }
        val w = Wsola(ch, ctx.sampleRate, 1.0 / ratio)
        val total = maxOf(1L, input.frames)
        var done = 0L
        while (true) {
            ctx.checkCancelled()
            val n = input.read(buf, BlockEffect.BLOCK)
            if (n <= 0) break
            w.push(buf, n, toPipe)
            done += n
            ctx.report(done.toFloat() / total)
        }
        w.finish(toPipe)
        pipe.finish(out)
    }
}

/**
 * Paulstretch : étire le son de [stretch] fois (jusqu'à plusieurs centaines) en gardant son spectre.
 *
 * Chaque fenêtre de [windowSec] secondes (sinus) est passée en FFT, ses phases sont remplacées par des
 * phases aléatoires (son spectre d'amplitude est conservé), puis elle est recollée avec un
 * recouvrement de 50 % à un pas de sortie plus grand que le pas d'entrée. Le résultat est un nuage
 * sonore continu — idéal pour des textures. La graine rend le rendu reproductible.
 */
class PaulStretch(stretch: Float = 8f, private val windowSec: Float = 0.25f, private val seed: Long = 1L) : Effect() {
    private val stretch = stretch.coerceIn(1f, 500f).toDouble()
    override val changesLength get() = true

    override fun run(openInput: () -> FrameReader, out: FrameWriter, ctx: RunContext) {
        val input = openInput()
        val ch = input.channels
        var size = 1024
        while (size < ctx.sampleRate * windowSec && size < 65536) size *= 2
        val half = size / 2
        val hopIn = half / stretch
        val fft = Fft(size)
        val win = DoubleArray(size) { sin(PI * (it + 0.5) / size) }
        val rnd = Random(seed)
        val re = DoubleArray(size)
        val im = DoubleArray(size)
        val acc = Array(ch) { FloatArray(half) }
        val block = Array(ch) { FloatArray(half) }

        // Entrée conservée en mémoire par morceaux : on lit au fil des besoins.
        var buf = Array(ch) { FloatArray(0) }
        var base = 0L
        var len = 0
        var eof = false
        val chunk = Array(ch) { FloatArray(BlockEffect.BLOCK) }
        fun fill(upTo: Long) {
            while (!eof && base + len < upTo) {
                ctx.checkCancelled()
                val n = input.read(chunk, BlockEffect.BLOCK)
                if (n <= 0) { eof = true; break }
                if (buf[0].size < len + n) buf = Array(ch) { c -> buf[c].copyOf(maxOf(len + n, buf[c].size * 2)) }
                for (c in 0 until ch) System.arraycopy(chunk[c], 0, buf[c], len, n)
                len += n
            }
        }

        val totalIn = input.frames
        val scale = sqrt(2.0) // fenêtre sinus à l'analyse et à la synthèse : la puissance moyenne tombe à ½
        var k = 0L
        while (true) {
            ctx.checkCancelled()
            val pos = (k * hopIn).toLong()
            if (pos >= totalIn && k > 0) break
            if (totalIn == 0L) break
            fill(pos + size)
            for (c in 0 until ch) {
                for (i in 0 until size) {
                    val idx = pos + i - base
                    val v = if (idx < 0 || idx >= len) 0.0 else buf[c][idx.toInt()].toDouble()
                    re[i] = v * win[i]
                    im[i] = 0.0
                }
                fft.transform(re, im)
                // Même amplitude, phase aléatoire ; symétrie hermitienne pour une sortie réelle.
                for (b in 1 until half) {
                    val mag = hypot(re[b], im[b])
                    val ph = rnd.nextDouble() * 2.0 * PI
                    re[b] = mag * cos(ph); im[b] = mag * sin(ph)
                    re[size - b] = re[b]; im[size - b] = -im[b]
                }
                im[0] = 0.0; im[half] = 0.0 // continu et Nyquist restent réels
                fft.transform(re, im, inverse = true)
                for (i in 0 until half) block[c][i] = acc[c][i] + (re[i] * win[i] * scale).toFloat()
                for (i in 0 until half) acc[c][i] = (re[half + i] * win[half + i] * scale).toFloat()
            }
            out.write(block, half)
            k++
            // On oublie l'entrée derrière la prochaine fenêtre.
            val keep = (k * hopIn).toLong()
            val shift = (keep - base).coerceIn(0L, len.toLong()).toInt()
            if (shift > 0) {
                for (c in 0 until ch) System.arraycopy(buf[c], shift, buf[c], 0, len - shift)
                len -= shift
                base += shift
            }
            ctx.report((pos.toFloat() / maxOf(1L, totalIn)).coerceAtMost(1f))
        }
        if (k > 0) out.write(acc, half)
    }
}

/**
 * Raccourcit les silences : toute plage plus longue que [minSilenceMs] où tous les canaux restent sous
 * [thresholdDb] est ramenée à [keepMs] (le début du silence est gardé, pour ne pas couper une
 * décroissance). Les silences plus courts ne sont pas touchés.
 */
class TruncateSilence(
    private val thresholdDb: Float = -50f,
    private val minSilenceMs: Float = 200f,
    private val keepMs: Float = 50f,
) : Effect() {
    override val changesLength get() = true

    override fun run(openInput: () -> FrameReader, out: FrameWriter, ctx: RunContext) {
        val input = openInput()
        val ch = input.channels
        val rate = ctx.sampleRate
        val thr = dbToLinear(thresholdDb)
        val minFrames = maxOf(1, (minSilenceMs * 0.001f * rate).roundToInt())
        val keepFrames = (keepMs * 0.001f * rate).roundToInt().coerceIn(0, minFrames)
        val runBuf = Array(ch) { FloatArray(minFrames) }
        var run = 0
        val outBuf = Array(ch) { FloatArray(BlockEffect.BLOCK) }
        var outN = 0
        fun flush() { if (outN > 0) { out.write(outBuf, outN); outN = 0 } }
        fun emit(src: Array<FloatArray>, idx: Int) {
            for (c in 0 until ch) outBuf[c][outN] = src[c][idx]
            if (++outN == BlockEffect.BLOCK) flush()
        }

        val buf = Array(ch) { FloatArray(BlockEffect.BLOCK) }
        val total = maxOf(1L, input.frames)
        var done = 0L
        while (true) {
            ctx.checkCancelled()
            val n = input.read(buf, BlockEffect.BLOCK)
            if (n <= 0) break
            for (i in 0 until n) {
                var silent = true
                for (c in 0 until ch) if (abs(buf[c][i]) > thr) { silent = false; break }
                if (silent) {
                    run++
                    if (run <= minFrames) {
                        for (c in 0 until ch) runBuf[c][run - 1] = buf[c][i]
                    } else if (run == minFrames + 1) {
                        // Le silence dépasse le minimum : on ne garde que le début du tampon.
                        for (j in 0 until keepFrames) emit(runBuf, j)
                    }
                } else {
                    if (run in 1..minFrames) for (j in 0 until run) emit(runBuf, j)
                    run = 0
                    emit(buf, i)
                }
            }
            done += n
            ctx.report(done.toFloat() / total)
        }
        if (run in 1..minFrames) for (j in 0 until run) emit(runBuf, j)
        flush()
    }
}
