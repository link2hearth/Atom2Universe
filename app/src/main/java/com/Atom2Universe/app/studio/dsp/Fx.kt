package com.Atom2Universe.app.studio.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp

/*
 * Les effets de bus du Studio : une réverbération et un écho en ping-pong. Ils lisent un bus d'envoi mono et
 * AJOUTENT leur retour stéréo au mixage. Réglages modifiables en direct (champs @Volatile), pas d'allocation,
 * état purement par échantillon (le résultat ne dépend pas du découpage en blocs).
 */

/** Réverbération de type Freeverb : 8 filtres en peigne amortis en parallèle puis 4 filtres passe-tout, par voie. */
class StereoReverb(sampleRate: Int) {

    /** 0 = petite pièce, 1 = très longue (environ 6 s). */
    @Volatile var roomSize = 0.84f

    /** 0 = brillante, 1 = sourde. */
    @Volatile var damping = 0.5f

    private class Comb(size: Int) {
        val buf = FloatArray(size)
        var idx = 0
        var store = 0f

        fun process(input: Float, feedback: Float, damp: Float): Float {
            val out = buf[idx]
            store = out * (1f - damp) + store * damp
            if (abs(store) < 1e-20f) store = 0f
            buf[idx] = input + store * feedback
            if (++idx == buf.size) idx = 0
            return out
        }
    }

    private class AllPass(size: Int) {
        val buf = FloatArray(size)
        var idx = 0

        fun process(input: Float): Float {
            val held = buf[idx]
            val out = held - input
            buf[idx] = input + held * 0.5f
            if (++idx == buf.size) idx = 0
            return out
        }
    }

    private val scale = sampleRate / 44100f
    private val combsL = Array(8) { Comb(maxOf(8, (COMB[it] * scale).toInt())) }
    private val combsR = Array(8) { Comb(maxOf(8, ((COMB[it] + SPREAD) * scale).toInt())) }
    private val allL = Array(4) { AllPass(maxOf(8, (ALLPASS[it] * scale).toInt())) }
    private val allR = Array(4) { AllPass(maxOf(8, ((ALLPASS[it] + SPREAD) * scale).toInt())) }

    /** Ajoute le retour de la réverbération (niveau [wet]) à [outL] et [outR] pour [from] à [to]. */
    fun process(input: FloatArray, outL: FloatArray, outR: FloatArray, from: Int, to: Int, wet: Float) {
        val feedback = roomSize.coerceIn(0f, 1f) * 0.28f + 0.7f
        val damp = damping.coerceIn(0f, 1f) * 0.4f
        for (i in from until to) {
            val x = input[i] * 0.03f
            var l = 0f
            var r = 0f
            for (c in combsL) l += c.process(x, feedback, damp)
            for (c in combsR) r += c.process(x, feedback, damp)
            for (a in allL) l = a.process(l)
            for (a in allR) r = a.process(r)
            outL[i] += l * wet
            outR[i] += r * wet
        }
    }

    private companion object {
        val COMB = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        val ALLPASS = intArrayOf(556, 441, 341, 225)
        const val SPREAD = 23
    }
}

/**
 * Écho en ping-pong : les répétitions rebondissent de gauche à droite et s'assombrissent à chaque tour (filtre
 * passe-bas dans la boucle). Le temps suit le tempo ; ses changements sont glissés pour ne jamais faire de « zip ».
 */
class PingPongDelay(private val sampleRate: Int, maxSeconds: Float = 2.5f) {

    @Volatile var delaySeconds = 0.375f
    @Volatile var feedback = 0.45f

    private val size = (maxSeconds * sampleRate).toInt() + 4
    private val bufL = FloatArray(size)
    private val bufR = FloatArray(size)
    private var write = 0
    private var current = delaySeconds * sampleRate
    private var lpL = 0f
    private var lpR = 0f
    private val lpCoef = (1.0 - exp(-2.0 * PI * 3500.0 / sampleRate)).toFloat()

    /** Ajoute le retour de l'écho (niveau [wet]) à [outL] et [outR] pour [from] à [to]. */
    fun process(input: FloatArray, outL: FloatArray, outR: FloatArray, from: Int, to: Int, wet: Float) {
        val target = (delaySeconds * sampleRate).coerceIn(32f, (size - 4).toFloat())
        val fb = feedback.coerceIn(0f, 0.85f)
        for (i in from until to) {
            current += (target - current) * 0.0004f
            var rp = write - current
            if (rp < 0f) rp += size
            val i0 = rp.toInt()
            val frac = rp - i0
            val i1 = if (i0 + 1 == size) 0 else i0 + 1
            val dl = bufL[i0] * (1f - frac) + bufL[i1] * frac
            val dr = bufR[i0] * (1f - frac) + bufR[i1] * frac

            lpL += (dl - lpL) * lpCoef
            lpR += (dr - lpR) * lpCoef
            if (abs(lpL) < 1e-20f) lpL = 0f
            if (abs(lpR) < 1e-20f) lpR = 0f
            bufL[write] = input[i] + lpR * fb
            bufR[write] = lpL * fb
            if (++write == size) write = 0

            outL[i] += dl * wet
            outR[i] += dr * wet
        }
    }
}
