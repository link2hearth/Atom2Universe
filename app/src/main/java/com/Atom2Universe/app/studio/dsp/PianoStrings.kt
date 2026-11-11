package com.Atom2Universe.app.studio.dsp

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Cordes frappées. Chaque partiel suit sa propre décroissance mesurée après le coup de marteau. */
internal class PianoStrings(private val rate: Int) {
    private class Partial {
        var ratio = 1f
        var phase = 0f
        var secondPhase = 0f
        var step = 0f
        var secondStep = 0f
        var bandGain = 1f
        var amplitude = 0f
        val slopes = FloatArray(4)
    }

    private val partials = Array(24) { Partial() }
    private val boundaries = intArrayOf((0.035f * rate).toInt(), (0.28f * rate).toInt(),
        (1.20f * rate).toInt(), (2.42f * rate).toInt())
    private var used = 0
    private var frames = 0
    private var endFrame = 0
    val finished: Boolean get() = frames >= endFrame
    private var segment = -1
    private var unison = 1f
    private var previous = 0f
    private var carry = 0f
    private val carryDecay = Fast.decayCoef(0.012f, rate)

    fun strike(key: Int, velocity: Float, retrigger: Boolean) {
        val registers = PianoSpectrum.registers
        val k = key.coerceIn(registers.first().key, registers.last().key)
        val upper = registers.indexOfFirst { it.key >= k }
        val a = registers[(upper - 1).coerceAtLeast(0)]
        val b = registers[upper]
        val blend = (k - a.key).toFloat() / (b.key - a.key).coerceAtLeast(1)
        val soft = velocity * 127f < 80.5f
        val lo = if (soft) a.soft else a.loud
        val hi = if (soft) b.soft else b.loud
        val stiffness = lo.stiffness + (hi.stiffness - lo.stiffness) * blend
        val reference = (if (soft) 64f else 104f) / 127f
        // PolySynth applique déjà un facteur de vélocité ; la pression sonore varie environ avec son carré.
        val gain = STUDIO_GAIN * velocity / (reference * reference)
        unison = if (k < 32) 1f else 1.00065f
        carry = if (retrigger) previous else 0f
        used = maxOf(lo.attack.size, hi.attack.size)
        frames = 0
        endFrame = boundaries.last()
        segment = -1
        for (i in 0 until used) {
            val p = partials[i]
            val n = i + 1
            p.ratio = n * sqrt(1f + stiffness * (n * n - 1))
            p.phase = (n * n * 0.193f) % 1f
            p.secondPhase = p.phase
            fun db(left: IntArray, right: IntArray): Float {
                val x = left.getOrElse(i) { -86 }
                val y = right.getOrElse(i) { -86 }
                return x + blend * (y - x)
            }
            val attack = db(lo.attack, hi.attack)
            val body = db(lo.body, hi.body)
            val ring = db(lo.ring, hi.ring)
            val tail = db(lo.tail, hi.tail)
            p.amplitude = 10f.pow(attack / 20f) * gain
            fun slope(deltaDb: Float, samples: Int) = 10.0.pow(deltaDb / (20.0 * samples)).toFloat()
            p.slopes[0] = slope(body - attack, boundaries[1] - boundaries[0])
            p.slopes[1] = slope(ring - body, boundaries[2] - boundaries[1])
            p.slopes[2] = slope(tail - ring, boundaries[3] - boundaries[2])
            val tailDrop = minOf(tail - ring, -4f)
            val tailFrames = boundaries[3] - boundaries[2]
            p.slopes[3] = slope(tailDrop, tailFrames)
            val remaining = ((-88f - tail) / tailDrop * tailFrames).toInt().coerceAtLeast(0)
            endFrame = maxOf(endFrame, boundaries[3] + remaining)
        }
    }

    fun tune(fundamental: Float, upper: Float) {
        for (i in 0 until used) {
            val p = partials[i]
            p.step = (if (i == 0) fundamental else upper) * p.ratio
            p.secondStep = p.step * unison
            p.bandGain = ((0.5f - p.secondStep) / 0.05f).coerceIn(0f, 1f)
        }
    }

    fun next(): Float {
        if (segment < 3 && frames == boundaries[segment + 1]) segment++
        frames++
        var sum = 0f
        for (i in 0 until used) {
            val p = partials[i]
            if (p.bandGain > 0f) {
                p.phase += p.step
                if (p.phase >= 1f) p.phase -= 1f
                p.secondPhase += p.secondStep
                if (p.secondPhase >= 1f) p.secondPhase -= 1f
                sum += (sine(p.phase) * 0.78f + sine(p.secondPhase) * 0.22f) * p.amplitude * p.bandGain
            }
            if (segment >= 0) p.amplitude *= p.slopes[segment]
        }
        previous = sum + carry
        carry *= carryDecay
        return previous
    }

    private companion object {
        // Gain du rendu SF2 de référence vers le niveau des préréglages Studio.
        const val STUDIO_GAIN = 1.5f
        private val table = FloatArray(2049) { sin(it * 2.0 * PI / 2048).toFloat() }
        fun sine(phase: Float): Float {
            val index = phase * 2048
            val i = index.toInt()
            return table[i] + (index - i) * (table[i + 1] - table[i])
        }
    }
}
