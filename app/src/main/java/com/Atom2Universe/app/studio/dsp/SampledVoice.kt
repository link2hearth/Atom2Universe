package com.Atom2Universe.app.studio.dsp

import com.Atom2Universe.app.midi.sf2.OrchestralSamples
import com.Atom2Universe.app.midi.sf2.Sf2Region
import kotlin.math.pow
import kotlin.math.roundToInt

/** Une note des échantillons de la banque Orchestral (piano, violon, flûte), lue comme le fait le moteur SF2. */
internal class SampledVoice(private val rate: Int) {
    private var data: ShortArray? = null
    private var position = 0.0
    private var step = 0.0
    private var baseStep = 0.0
    private var nominal = 1f
    private var end = 0.0
    private var loopStart = 0.0
    private var loopLength = 0.0
    private var gain = 0f
    private var envelope = 1f
    private var holdLeft = 0
    private var decay = 1f
    private var sustain = 0f
    private val filter = Svf(rate)
    private var filtered = false
    private var previous = 0f
    private var carry = 0f
    private val carryDecay = Fast.decayCoef(0.012f, rate)
    var finished = true
        private set

    /** Extinction de la note en cours une fois relâchée, en secondes jusqu'à −60 dB. */
    var release = 0f
        private set

    fun strike(program: Int, key: Int, velocity: Float, retrigger: Boolean) {
        carry = if (retrigger) previous else 0f
        finished = true
        val file = OrchestralSamples.ready() ?: return
        val regions = file.getPreset(0, program)?.regions ?: return
        var first = 127
        var last = 0
        for (i in regions.indices) {
            first = minOf(first, regions[i].keyRange.first)
            last = maxOf(last, regions[i].keyRange.last)
        }
        if (first > last) return
        val nearest = key.coerceIn(first, last)
        val force = (velocity * 127f).roundToInt().coerceIn(1, 127)
        var region: Sf2Region? = null
        for (i in regions.indices) if (regions[i].matches(nearest, force)) { region = regions[i]; break }
        if (region == null) return
        data = file.sampleData
        position = region.sampleStart.toDouble()
        end = (region.sampleEnd - 2).toDouble()
        loopStart = region.loopStart.toDouble()
        loopLength = if (region.hasLoop) (region.loopEnd - region.loopStart).toDouble() else 0.0
        if (loopLength > 0) end = region.loopEnd.toDouble()
        baseStep = region.calculatePlaybackRate(key, rate)
        step = baseStep
        nominal = 440f * 2f.pow((key - 69) / 12f) / rate
        // PolySynth applique déjà un facteur de vélocité ; avec celui-ci la réponse suit la courbe du SF2.
        gain = STUDIO_GAIN * velocity * region.calculateGain() / 32768f
        envelope = 1f
        val shape = region.volumeEnvelope
        // L'extinction SF2 descend de 100 dB dans sa durée : −60 dB en arrive aux six dixièmes.
        release = (shape?.release ?: 0.1f) * 0.6f
        if (shape == null || shape.sustain >= 1f) {
            holdLeft = Int.MAX_VALUE
        } else {
            val delta = 60 - key
            holdLeft = (shape.hold * 2f.pow(delta * shape.keynumToHold / 1200f) * rate).toInt()
            val time = (shape.decay * 2f.pow(delta * shape.keynumToDecay / 1200f)).coerceIn(0.001f, 100f)
            decay = 10.0.pow(-5.0 / (time * rate)).toFloat()
            sustain = if (shape.sustain <= 0f) 0f else 10.0.pow(-5.0 * (1.0 - shape.sustain)).toFloat()
        }
        val cutoff = region.filterFc
        filtered = cutoff != null && cutoff < 13000
        if (filtered) {
            filter.reset()
            filter.set(8.176f * 2f.pow(cutoff!! / 1200f), 0.707f)
        }
        finished = false
    }

    /** [fundamental] : cycles par échantillon de la note, glissé et molette compris. */
    fun tune(fundamental: Float) {
        step = baseStep * (fundamental / nominal)
    }

    fun render(out: FloatArray, count: Int) {
        val samples = data
        for (j in 0 until count) {
            var value = 0f
            if (!finished && samples != null) {
                val k = position.toInt()
                val t = (position - k).toFloat()
                val a = samples[if (k > 0) k - 1 else k].toFloat()
                val b = samples[k].toFloat()
                val c = samples[k + 1].toFloat()
                val d = samples[k + 2].toFloat()
                // Interpolation d'Hermite sur quatre points.
                val c1 = 0.5f * (c - a)
                val c2 = a - 2.5f * b + 2f * c - 0.5f * d
                val c3 = 0.5f * (d - a) + 1.5f * (b - c)
                value = (((c3 * t + c2) * t + c1) * t + b) * gain * envelope
                if (filtered) {
                    filter.tick(value)
                    value = filter.lp
                }
                position += step
                if (position >= end) {
                    if (loopLength > 0) position -= loopLength else finished = true
                }
                if (holdLeft > 0) holdLeft-- else {
                    envelope *= decay
                    if (envelope <= sustain) envelope = sustain
                    if (envelope < 1e-5f) finished = true
                }
            }
            out[j] = value + carry
            carry *= carryDecay
        }
        previous = out[count - 1]
    }

    private companion object {
        // Niveau dans Studio, à côté des autres préréglages (mesuré égal à l'ancien piano calculé).
        const val STUDIO_GAIN = 1f
    }
}
