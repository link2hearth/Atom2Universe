package com.Atom2Universe.app.audioeditor.dsp

import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan
import kotlin.math.tanh

/*
 * Espace et modulation : écho, réverbération, chorus, flanger, phaser, trémolo, vibrato,
 * distorsion et bit-crusher. Tous gardent la durée de l'entrée ; ceux qui ont une queue
 * (écho, réverbération) la déclarent et elle se superpose à la suite au lieu de la repousser.
 */

/** Temps maximal d'une queue d'écho ou de réverbération : au-delà on coupe (le reste est inaudible). */
private const val MAX_TAIL_SECONDS = 20

/**
 * Écho : le signal revient après [delayMs] à [decay] de son niveau, puis encore, etc. (`y[n] = x[n] +
 * decay · y[n − D]`). La queue dure jusqu'à ce que les répétitions passent sous −60 dB.
 */
class Echo(private val delayMs: Float = 300f, decay: Float = 0.5f) : BlockEffect() {

    private val decay = decay.coerceIn(0f, 0.99f)
    private var ring: Array<FloatArray> = emptyArray()
    private var pos = 0

    private fun delayFrames(sampleRate: Int) = maxOf(1, (delayMs * 0.001f * sampleRate).roundToInt())

    override fun tailFrames(sampleRate: Int): Int {
        if (decay < 0.001f) return 0
        val repeats = kotlin.math.ceil(ln(0.001) / ln(decay.toDouble())).toInt()
        return (repeats.toLong() * delayFrames(sampleRate)).coerceAtMost(MAX_TAIL_SECONDS.toLong() * sampleRate).toInt()
    }

    override fun start(channels: Int, sampleRate: Int) {
        ring = Array(channels) { FloatArray(delayFrames(sampleRate)) }
        pos = 0
    }

    override fun process(buf: Array<FloatArray>, n: Int) {
        val d = ring[0].size
        for (c in buf.indices) {
            val x = buf[c]; val r = ring[c]
            var p = pos
            for (i in 0 until n) {
                val y = x[i] + decay * r[p]
                r[p] = y
                x[i] = y
                if (++p == d) p = 0
            }
        }
        pos = (pos + n) % d
    }
}

/**
 * Réverbération de Freeverb (Jezar) : huit filtres en peigne à amortissement en parallèle puis quatre
 * passe-tout en série, par voie, la voie droite décalée de 23 échantillons pour l'image stéréo.
 *
 * [roomSize] 0…1 allonge la queue, [damping] 0…1 étouffe les aigus qui y reviennent, [wet] et [dry]
 * (0…1) règlent le mélange, [width] 0…1 l'ouverture stéréo.
 */
class Reverb(
    private val roomSize: Float = 0.5f,
    private val damping: Float = 0.5f,
    private val wet: Float = 0.33f,
    private val dry: Float = 1f,
    private val width: Float = 1f,
) : BlockEffect() {

    private class Comb(size: Int) {
        val buf = FloatArray(size)
        var idx = 0
        var store = 0f
        fun process(input: Float, feedback: Float, damp: Float): Float {
            val out = buf[idx]
            store = out * (1f - damp) + store * damp
            buf[idx] = input + store * feedback
            if (++idx == buf.size) idx = 0
            return out
        }
    }

    private class AllPass(size: Int) {
        val buf = FloatArray(size)
        var idx = 0
        fun process(input: Float): Float {
            val b = buf[idx]
            val out = -input + b
            buf[idx] = input + b * 0.5f
            if (++idx == buf.size) idx = 0
            return out
        }
    }

    private class Chain(sampleRate: Int, spread: Int) {
        private val scale = sampleRate / 44100.0
        val combs = COMB_TUNING.map { Comb(((it + spread) * scale).roundToInt().coerceAtLeast(8)) }
        val allpass = ALLPASS_TUNING.map { AllPass(((it + spread) * scale).roundToInt().coerceAtLeast(8)) }
    }

    private var left: Chain? = null
    private var right: Chain? = null
    private val feedback = roomSize.coerceIn(0f, 1f) * 0.28f + 0.7f
    private val damp = damping.coerceIn(0f, 1f) * 0.4f

    override fun tailFrames(sampleRate: Int): Int {
        if (wet <= 0f) return 0
        // Chaque tour de peigne (~1 400 échantillons à 44,1 kHz) fait perdre une fraction du niveau : durée jusqu'à −60 dB.
        val loopsPerSecond = 44100.0 / 1400.0
        val seconds = ln(0.001) / (loopsPerSecond * ln(feedback.toDouble()))
        return (seconds * sampleRate).toLong().coerceIn(0L, MAX_TAIL_SECONDS.toLong() * sampleRate).toInt()
    }

    override fun start(channels: Int, sampleRate: Int) {
        left = Chain(sampleRate, 0)
        right = if (channels >= 2) Chain(sampleRate, 23) else null
    }

    private fun run(chain: Chain, input: Float): Float {
        var out = 0f
        for (c in chain.combs) out += c.process(input, feedback, damp)
        for (a in chain.allpass) out = a.process(out)
        return out
    }

    override fun process(buf: Array<FloatArray>, n: Int) {
        val wetGain = wet * 3f
        val w1 = wetGain * (width / 2f + 0.5f)
        val w2 = wetGain * ((1f - width) / 2f)
        val l = left!!
        val r = right
        if (r == null) {
            val x = buf[0]
            for (i in 0 until n) {
                val inp = x[i] * 2f * 0.015f
                x[i] = run(l, inp) * wetGain + x[i] * dry
            }
        } else {
            val xl = buf[0]; val xr = buf[1]
            for (i in 0 until n) {
                val inp = (xl[i] + xr[i]) * 0.015f
                val wl = run(l, inp)
                val wr = run(r, inp)
                val dl = xl[i]; val dr = xr[i]
                xl[i] = wl * w1 + wr * w2 + dl * dry
                xr[i] = wr * w1 + wl * w2 + dr * dry
            }
        }
    }

    companion object {
        private val COMB_TUNING = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        private val ALLPASS_TUNING = intArrayOf(556, 441, 341, 225)
    }
}

/** Ligne à retard à lecture fractionnaire (interpolation linéaire), un anneau par canal. */
internal class ModDelay(channels: Int, maxDelaySamples: Int) {
    private val size = maxDelaySamples + 4
    private val ring = Array(channels) { FloatArray(size) }
    private var w = 0

    /** Écrit [x] + [feedback]·(sortie) et rend l'échantillon retardé de [delay] échantillons (fractionnaire). */
    fun tick(ch: Int, x: Float, delay: Float, feedback: Float): Float {
        val r = ring[ch]
        val d = delay.coerceIn(1f, (size - 3).toFloat())
        var rp = w - d
        if (rp < 0f) rp += size
        val i0 = rp.toInt()
        val frac = rp - i0
        val a = r[i0 % size]
        val b = r[(i0 + 1) % size]
        val y = a + (b - a) * frac
        r[w] = x + feedback * y
        return y
    }

    /** À appeler une fois par trame, après avoir traité tous les canaux. */
    fun advance() { w++; if (w == size) w = 0 }
}

/**
 * Chorus : une copie du signal, retardée d'environ [delayMs] avec une oscillation de ± [depthMs] à
 * [rateHz], est mêlée à l'original ([mix] 0…1). La voie droite oscille en quadrature : l'effet élargit le stéréo.
 */
class Chorus(
    private val rateHz: Float = 1.2f,
    private val depthMs: Float = 3f,
    private val delayMs: Float = 20f,
    private val mix: Float = 0.5f,
) : BlockEffect() {
    private var md: ModDelay? = null
    private var phase = 0.0
    private var sr = 44100

    override fun start(channels: Int, sampleRate: Int) {
        sr = sampleRate
        md = ModDelay(channels, ((delayMs + depthMs) * 0.001f * sampleRate).toInt() + 4)
        phase = 0.0
    }

    override fun process(buf: Array<FloatArray>, n: Int) {
        val m = md!!
        val inc = 2.0 * PI * rateHz / sr
        val dryG = 1f - 0.5f * mix
        val wetG = 0.5f * mix
        for (i in 0 until n) {
            for (c in buf.indices) {
                val ph = phase + if (c == 1) PI / 2 else 0.0
                val d = (delayMs + depthMs * sin(ph).toFloat()) * 0.001f * sr
                val x = buf[c][i]
                val wet = m.tick(c, x, d, 0f)
                buf[c][i] = x * dryG + wet * wetG
            }
            m.advance()
            phase += inc
        }
    }
}

/**
 * Flanger : un très court retard ([depthMs] au plus) balayé à [rateHz], avec retour ([feedback] −0,95…0,95)
 * qui creuse des résonances en peigne mobiles.
 */
class Flanger(
    private val rateHz: Float = 0.25f,
    private val depthMs: Float = 2f,
    feedback: Float = 0.5f,
    private val mix: Float = 0.8f,
) : BlockEffect() {
    private val feedback = feedback.coerceIn(-0.95f, 0.95f)
    private var md: ModDelay? = null
    private var phase = 0.0
    private var sr = 44100

    override fun start(channels: Int, sampleRate: Int) {
        sr = sampleRate
        md = ModDelay(channels, (depthMs * 0.001f * sampleRate).toInt() + 8)
        phase = 0.0
    }

    override fun process(buf: Array<FloatArray>, n: Int) {
        val m = md!!
        val inc = 2.0 * PI * rateHz / sr
        val dryG = 1f - 0.5f * mix
        val wetG = 0.5f * mix
        for (i in 0 until n) {
            val d = (0.0002f + depthMs * 0.001f * 0.5f * (1f + sin(phase).toFloat())) * sr
            for (c in buf.indices) {
                val x = buf[c][i]
                val wet = m.tick(c, x, d, feedback)
                buf[c][i] = x * dryG + wet * wetG
            }
            m.advance()
            phase += inc
        }
    }
}

/** Vibrato : uniquement le signal retardé, dont la hauteur oscille avec la vitesse de variation du retard. */
class Vibrato(private val rateHz: Float = 5f, private val depthMs: Float = 1f) : BlockEffect() {
    private var md: ModDelay? = null
    private var phase = 0.0
    private var sr = 44100

    override fun start(channels: Int, sampleRate: Int) {
        sr = sampleRate
        md = ModDelay(channels, (depthMs * 0.001f * sampleRate * 2).toInt() + 8)
        phase = 0.0
    }

    override fun process(buf: Array<FloatArray>, n: Int) {
        val m = md!!
        val inc = 2.0 * PI * rateHz / sr
        for (i in 0 until n) {
            val d = (depthMs * 0.001f * (1f + sin(phase).toFloat()) + 0.0002f) * sr
            for (c in buf.indices) buf[c][i] = m.tick(c, buf[c][i], d, 0f)
            m.advance()
            phase += inc
        }
    }
}

/** Trémolo : le volume oscille à [rateHz] ; à [depth] 1 il tombe jusqu'au silence, à 0 il ne bouge pas. */
class Tremolo(private val rateHz: Float = 5f, private val depth: Float = 0.7f) : BlockEffect() {
    private var phase = 0.0
    private var sr = 44100

    override fun start(channels: Int, sampleRate: Int) { sr = sampleRate; phase = 0.0 }

    override fun process(buf: Array<FloatArray>, n: Int) {
        val inc = 2.0 * PI * rateHz / sr
        val d = depth.coerceIn(0f, 1f)
        for (i in 0 until n) {
            val g = 1f - d * 0.5f * (1f - kotlin.math.cos(phase).toFloat())
            for (c in buf.indices) buf[c][i] *= g
            phase += inc
        }
    }
}

/**
 * Phaser : une chaîne de [stages] passe-tout du premier ordre dont la fréquence est balayée à [rateHz]
 * entre 200 Hz et 200 Hz × `2^(depth·4)` ; mêlée à l'original, elle creuse des creux mobiles.
 */
class Phaser(
    private val rateHz: Float = 0.4f,
    private val stages: Int = 4,
    private val depth: Float = 0.8f,
    feedback: Float = 0.3f,
    private val mix: Float = 0.5f,
) : BlockEffect() {
    private val feedback = feedback.coerceIn(-0.9f, 0.9f)
    private var xPrev = Array(0) { FloatArray(0) }
    private var yPrev = Array(0) { FloatArray(0) }
    private var last = FloatArray(0)
    private var phase = 0.0
    private var sr = 44100
    private var a = 0f
    private var counter = 0

    override fun start(channels: Int, sampleRate: Int) {
        sr = sampleRate
        val s = stages.coerceIn(2, 12)
        xPrev = Array(channels) { FloatArray(s) }
        yPrev = Array(channels) { FloatArray(s) }
        last = FloatArray(channels)
        phase = 0.0
        counter = 0
    }

    override fun process(buf: Array<FloatArray>, n: Int) {
        val s = stages.coerceIn(2, 12)
        val inc = 2.0 * PI * rateHz / sr
        val dryG = 1f - 0.5f * mix
        val wetG = 0.5f * mix
        val octaves = depth.coerceIn(0f, 1f) * 4.0
        for (i in 0 until n) {
            if (counter == 0) {
                val f = 200.0 * 2.0.pow(octaves * 0.5 * (1.0 + sin(phase))) // coefficient mis à jour par petits pas
                val t = tan(PI * f.coerceAtMost(sr * 0.45) / sr)
                a = ((t - 1.0) / (t + 1.0)).toFloat()
            }
            counter = (counter + 1) and 15
            for (c in buf.indices) {
                val x = buf[c][i]
                var v = x + feedback * last[c]
                for (k in 0 until s) {
                    val y = a * (v - yPrev[c][k]) + xPrev[c][k]
                    xPrev[c][k] = v
                    yPrev[c][k] = y
                    v = y
                }
                last[c] = v
                buf[c][i] = x * dryG + v * wetG
            }
            phase += inc
        }
    }
}

/** Forme de la saturation de [Distortion]. */
enum class DistortionKind { SOFT, HARD, TUBE }

/**
 * Saturation : le signal, amplifié de [driveDb], est écrêté en douceur ([DistortionKind.SOFT], tangente
 * hyperbolique), net ([HARD]) ou de façon asymétrique ([TUBE], qui ajoute des harmoniques paires) ;
 * [outputDb] rattrape le niveau. Pas de suréchantillonnage : l'effet repliera un peu aux fortes saturations.
 */
class Distortion(
    private val driveDb: Float = 12f,
    private val kind: DistortionKind = DistortionKind.SOFT,
    private val outputDb: Float = -6f,
) : BlockEffect() {
    private val g = dbToLinear(driveDb)
    private val out = dbToLinear(outputDb)
    private val norm = 1f / tanh(maxOf(g, 1f))

    override fun start(channels: Int, sampleRate: Int) {}

    override fun process(buf: Array<FloatArray>, n: Int) {
        for (c in buf.indices) {
            val x = buf[c]
            for (i in 0 until n) {
                val v = x[i] * g
                x[i] = out * when (kind) {
                    DistortionKind.SOFT -> tanh(v) * norm
                    DistortionKind.HARD -> v.coerceIn(-1f, 1f)
                    DistortionKind.TUBE -> if (v >= 0f) tanh(v) else tanh(0.5f * v)
                }
            }
        }
    }
}

/** Dégrade la résolution à [bits] bits et la fréquence d'échantillonnage d'un facteur [downsample] (chaque valeur est tenue). */
class BitCrusher(bits: Int = 8, private val downsample: Int = 1) : BlockEffect() {
    // Entier signé sur `bits` bits : de −levels à levels − 1 (3 bits → 8 valeurs).
    private val levels = 2.0f.pow(bits.coerceIn(1, 16) - 1)
    private var held = FloatArray(0)
    private var count = 0

    override fun start(channels: Int, sampleRate: Int) { held = FloatArray(channels); count = 0 }

    override fun process(buf: Array<FloatArray>, n: Int) {
        val hold = maxOf(1, downsample)
        for (i in 0 until n) {
            if (count == 0) for (c in buf.indices) held[c] = Math.round(buf[c][i] * levels).coerceIn(-levels.toInt(), levels.toInt() - 1) / levels
            for (c in buf.indices) buf[c][i] = held[c]
            count = (count + 1) % hold
        }
    }
}
