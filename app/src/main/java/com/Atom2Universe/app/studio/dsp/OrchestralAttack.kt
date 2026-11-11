package com.Atom2Universe.app.studio.dsp

import kotlin.math.pow
import kotlin.math.roundToInt

/** Somme des oscillateurs à hauteur fixe, calculée au chargement du MIDI à partir des paramètres. */
internal class OrchestralAttack(
    val rate: Int, key: Int, right: Boolean, spectrum: OrchestralBank.Spectrum, frames: Int,
) {
    val cycles = 440f * 2f.pow((key - 69) / 12f) / rate
    val samples = FloatArray(frames)
    val phases = FloatArray((frames / STRIDE + 1) * spectrum.tones)

    init {
        require(spectrum.movingRatios == null)
        val times = spectrum.times.map { (it * rate).roundToInt() }
        val sine = OrchestralVoice.sine
        val phaseFactor = if (right) 0.317f else 0.193f
        for (i in 0 until spectrum.tones) {
            var phase = ((i + 1) * (i + 1) * phaseFactor) % 1f
            val step = cycles * spectrum.ratios[i]
            val weight = ((0.5f - step) / .05f).coerceIn(0f, 1f)
            val levels = spectrum.levels[i]
            var index = 0
            var amplitude = levels[0]
            var delta = 0f
            for (section in 0..times.size) {
                val stop = if (section < times.size) minOf(times[section], frames) else frames
                while (index < stop) {
                    if (index % STRIDE == 0) phases[index / STRIDE * spectrum.tones + i] = phase
                    if (weight > 0f) {
                        phase += step
                        if (phase >= 1f) phase -= 1f
                        val x = phase * 2048
                        val k = x.toInt()
                        samples[index] += (sine[k] + (x - k) * (sine[k + 1] - sine[k])) * amplitude * weight
                    }
                    amplitude += delta
                    index++
                }
                if (index == frames) break
                amplitude = levels[section]
                delta = if (section < times.lastIndex)
                    (levels[section + 1] - amplitude) / (times[section + 1] - times[section]) else 0f
            }
            if (frames % STRIDE == 0) phases[frames / STRIDE * spectrum.tones + i] = phase
        }
    }

    companion object { const val STRIDE = 512 }
}
