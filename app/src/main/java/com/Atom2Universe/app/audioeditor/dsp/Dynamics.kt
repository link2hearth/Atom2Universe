package com.Atom2Universe.app.audioeditor.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

private fun timeCoeff(ms: Float, sampleRate: Int): Float =
    if (ms <= 0f) 0f else exp(-1.0 / (ms * 0.001 * sampleRate)).toFloat()

/**
 * Compresseur à action directe. Le détecteur suit la crête la plus haute des canaux (les deux voies
 * baissent ensemble, l'image stéréo ne bouge pas) avec une [attackMs] et un [releaseMs] ; au-dessus du
 * [thresholdDb] le niveau est ramené selon le [ratio] (`4` = 4 dB d'entrée pour 1 dB de sortie), avec
 * un coude progressif de [kneeDb]. [makeupDb] rattrape le niveau perdu.
 */
class Compressor(
    private val thresholdDb: Float = -18f,
    private val ratio: Float = 3f,
    private val attackMs: Float = 10f,
    private val releaseMs: Float = 150f,
    private val kneeDb: Float = 6f,
    private val makeupDb: Float = 0f,
) : BlockEffect() {

    private var env = 0f
    private var attack = 0f
    private var release = 0f
    private val makeup = dbToLinear(makeupDb)

    override fun start(channels: Int, sampleRate: Int) {
        env = 0f
        attack = timeCoeff(attackMs, sampleRate)
        release = timeCoeff(releaseMs, sampleRate)
    }

    /** Réduction de gain, en dB (≤ 0), pour un niveau d'entrée de [levelDb]. */
    internal fun gainReductionDb(levelDb: Float): Float {
        val over = levelDb - thresholdDb
        val r = ratio.coerceAtLeast(1f)
        val outDb = when {
            2f * over < -kneeDb -> levelDb
            kneeDb > 0f && 2f * abs(over) <= kneeDb -> levelDb + (1f / r - 1f) * (over + kneeDb / 2f).pow(2) / (2f * kneeDb)
            else -> thresholdDb + over / r
        }
        return outDb - levelDb
    }

    override fun process(buf: Array<FloatArray>, n: Int) {
        val ch = buf.size
        for (i in 0 until n) {
            var level = 0f
            for (c in 0 until ch) { val a = abs(buf[c][i]); if (a > level) level = a }
            env = if (level > env) attack * env + (1f - attack) * level else release * env + (1f - release) * level
            val g = if (env < 1e-6f) makeup else dbToLinear(gainReductionDb(linearToDb(env))) * makeup
            for (c in 0 until ch) buf[c][i] *= g
        }
    }
}

/**
 * Limiteur « à mur de briques » avec anticipation : aucune crête ne dépasse [ceilingDb].
 *
 * Le gain est calculé sur une fenêtre qui regarde [lookaheadMs] en avant puis lissé sur la même
 * durée, si bien qu'il descend avant la crête au lieu de la couper net (pas de distorsion de
 * coupure). Le retard d'anticipation est compensé : la sortie reste alignée sur l'entrée.
 */
class Limiter(
    private val ceilingDb: Float = -1f,
    private val releaseMs: Float = 100f,
    private val lookaheadMs: Float = 5f,
) : BlockEffect() {

    private val ceiling = dbToLinear(ceilingDb)
    private var look = 1
    private var delay: Array<FloatArray> = emptyArray()
    private var pos = 0

    // Fenêtre glissante des gains minimaux (file monotone, temps absolu en Long) et moyenne glissante du minimum.
    private var dqIdx = LongArray(0)
    private var dqVal = FloatArray(0)
    private var head = 0
    private var tail = 0
    private var t = 0L
    private var minRing = FloatArray(0)
    private var minSum = 0.0
    private var releaseCoeff = 0f
    private var prevGain = 1f

    override fun latencyFrames(sampleRate: Int): Int = lookahead(sampleRate)

    private fun lookahead(sampleRate: Int) = maxOf(1, (lookaheadMs * 0.001f * sampleRate).toInt())

    override fun start(channels: Int, sampleRate: Int) {
        look = lookahead(sampleRate)
        // Retard d'exactement `look` trames : l'échantillon lu à l'instant t est entré à t − look.
        delay = Array(channels) { FloatArray(look) }
        pos = 0
        val cap = look + 3
        dqIdx = LongArray(cap); dqVal = FloatArray(cap); head = 0; tail = 0
        t = 0
        minRing = FloatArray(look + 1) { 1f }
        minSum = (look + 1).toDouble()
        releaseCoeff = timeCoeff(releaseMs, sampleRate)
        prevGain = 1f
    }

    override fun process(buf: Array<FloatArray>, n: Int) {
        val ch = buf.size
        val cap = dqIdx.size
        for (i in 0 until n) {
            var peak = 0f
            for (c in 0 until ch) { val a = abs(buf[c][i]); if (a > peak) peak = a }
            val g = if (peak > ceiling) ceiling / peak else 1f

            // Minimum glissant des look+1 derniers gains : on garde une file de gains croissants.
            while (tail != head && dqVal[(tail - 1 + cap) % cap] >= g) tail = (tail - 1 + cap) % cap
            dqIdx[tail] = t; dqVal[tail] = g; tail = (tail + 1) % cap
            while (dqIdx[head] < t - look) head = (head + 1) % cap
            val windowMin = dqVal[head]

            // Moyenne glissante de ce minimum sur look+1 valeurs : elle reste ≤ au gain que réclame chaque crête de la fenêtre.
            val slot = (t % (look + 1)).toInt()
            minSum += windowMin - minRing[slot]
            minRing[slot] = windowMin
            var gain = (minSum / (look + 1)).toFloat()
            // Le gain descend tout de suite mais remonte progressivement (relâchement).
            if (gain > prevGain) gain = minOf(gain, prevGain + (1f - prevGain) * (1f - releaseCoeff))
            prevGain = gain

            for (c in 0 until ch) {
                val d = delay[c]
                val out = d[pos]
                d[pos] = buf[c][i]
                buf[c][i] = out * gain
            }
            pos = (pos + 1) % look
            t++
        }
    }
}

/**
 * Porte de bruit : sous [thresholdDb] le signal est atténué de [reductionDb] (au plus), avec une
 * [attackMs] d'ouverture, un [holdMs] pendant lequel elle reste ouverte après la dernière crête, puis
 * un [releaseMs] de fermeture.
 */
class NoiseGate(
    private val thresholdDb: Float = -45f,
    private val reductionDb: Float = -80f,
    private val attackMs: Float = 5f,
    private val holdMs: Float = 50f,
    private val releaseMs: Float = 120f,
) : BlockEffect() {

    private val threshold = dbToLinear(thresholdDb)
    private val floor = dbToLinear(reductionDb)
    private var gain = 1f
    private var holdLeft = 0
    private var holdFrames = 0
    private var attack = 0f
    private var release = 0f
    private var detector = 0f
    private var detRelease = 0f

    override fun start(channels: Int, sampleRate: Int) {
        gain = floor
        holdLeft = 0
        holdFrames = (holdMs * 0.001f * sampleRate).toInt()
        attack = timeCoeff(attackMs, sampleRate)
        release = timeCoeff(releaseMs, sampleRate)
        detector = 0f
        detRelease = timeCoeff(10f, sampleRate)
    }

    override fun process(buf: Array<FloatArray>, n: Int) {
        val ch = buf.size
        for (i in 0 until n) {
            var level = 0f
            for (c in 0 until ch) { val a = abs(buf[c][i]); if (a > level) level = a }
            detector = if (level > detector) level else detRelease * detector + (1f - detRelease) * level
            if (detector >= threshold) holdLeft = holdFrames
            val open = detector >= threshold || holdLeft > 0
            if (holdLeft > 0 && detector < threshold) holdLeft--
            val target = if (open) 1f else floor
            gain = if (target > gain) attack * gain + (1f - attack) * target else release * gain + (1f - release) * target
            for (c in 0 until ch) buf[c][i] *= gain
        }
    }
}
