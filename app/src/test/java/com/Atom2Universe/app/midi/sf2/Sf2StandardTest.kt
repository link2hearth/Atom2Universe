package com.Atom2Universe.app.midi.sf2

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comportements imposés par la norme SoundFont 2.01 (enveloppes, modulateurs par défaut,
 * modes de boucle, LFO).
 */
class Sf2StandardTest {

    private val sampleRate = 48000
    private val block = 512

    private fun db(level: Float) = 20f * log10(level)

    private fun EnvelopeGenerator.advance(seconds: Float): Float {
        repeat((seconds * sampleRate).toInt()) { process() }
        return getLevel()
    }

    @Test
    fun volumeAttackIsLinearInAmplitude() {
        val env = EnvelopeGenerator(sampleRate, isVolume = true)
        env.configure(VolumeEnvelope(attack = 0.1f, decay = 1f))
        env.trigger()
        assertEquals(0.5f, env.advance(0.05f), 0.01f)
    }

    @Test
    fun volumeDecayIsLinearInDecibelsOverHundredDecibels() {
        val env = EnvelopeGenerator(sampleRate, isVolume = true)
        // Sustain 500 cB = -50 dB, décroissance complète (100 dB) en 1 s
        env.configure(VolumeEnvelope(attack = VolumeEnvelope.DEFAULT_TIME, decay = 1f, sustain = 0.5f))
        env.trigger()
        env.advance(0.001f)
        assertEquals(-25f, db(env.advance(0.25f)), 0.5f)
        // -50 dB atteint à mi-temps, puis maintenu
        assertEquals(-50f, db(env.advance(0.5f)), 0.5f)
        assertEquals(EnvelopeGenerator.Stage.SUSTAIN, env.stage)
    }

    @Test
    fun volumeReleaseFallsHundredDecibelsInReleaseTime() {
        val env = EnvelopeGenerator(sampleRate, isVolume = true)
        env.configure(VolumeEnvelope(release = 2f))
        env.trigger()
        env.advance(0.01f)
        env.release()
        assertEquals(-50f, db(env.advance(1f)), 0.5f)
        env.advance(1.01f)
        assertTrue(env.isFinished())
    }

    @Test
    fun silentSustainEndsTheNote() {
        val env = EnvelopeGenerator(sampleRate, isVolume = true)
        // sustainVolEnv = 1000 cB : la note s'éteint d'elle-même (piano, guitare...)
        env.configure(VolumeEnvelope(decay = 0.5f, sustain = 0f))
        env.trigger()
        env.advance(0.6f)
        assertTrue(env.isFinished())
    }

    @Test
    fun modulationEnvelopeIsLinearWithPermilleSustain() {
        val env = EnvelopeGenerator(sampleRate, isVolume = false)
        // sustainModEnv = 500 (‰) : la valeur se stabilise à 0,5
        env.configure(VolumeEnvelope(decay = 1f, sustain = 0.5f))
        env.trigger()
        env.advance(0.001f)
        assertEquals(0.75f, env.advance(0.25f), 0.01f)
        assertEquals(0.5f, env.advance(1f), 1e-4f)
    }

    @Test
    fun positiveKeynumToDecayShortensHighNotes() {
        fun decayLevelAt(key: Int): Float {
            val env = EnvelopeGenerator(sampleRate, isVolume = true)
            env.configure(VolumeEnvelope(decay = 1f, sustain = 0f, keynumToDecay = 100), key)
            env.trigger()
            return env.advance(0.3f)
        }
        // +100 timecents par touche : une octave plus haut, décroissance deux fois plus rapide
        assertEquals(db(decayLevelAt(60)) * 2f, db(decayLevelAt(72)), 1f)
        assertTrue(decayLevelAt(48) > decayLevelAt(60))
    }

    private fun region(
        length: Int,
        loopStart: Int = 0,
        loopEnd: Int = length,
        envelope: VolumeEnvelope? = VolumeEnvelope(),
        loopUntilRelease: Boolean = false
    ) = Sf2Region(
        keyRange = 0..127, velRange = 0..127, sampleId = 0, sampleRate = sampleRate,
        sampleStart = 0, sampleEnd = length.toLong(), loopStart = loopStart.toLong(), loopEnd = loopEnd.toLong(),
        hasLoop = true, rootKey = 60, coarseTune = 0, fineTune = 0, scaleTuning = 100, pitchCorrection = 0,
        attenuation = 0, pan = 0f, exclusiveClass = 0, reverbSend = null, chorusSend = null,
        volumeEnvelope = envelope, sampleName = "test", filterFc = null, filterQ = null, vibLfo = null,
        modLfo = null, modEnvelope = null, modEnvToPitch = null, modEnvToFilterFc = null,
        forcedKeyNum = null, forcedVelocity = null, loopUntilRelease = loopUntilRelease
    )

    private fun synth(data: ShortArray, region: Sf2Region): Sf2Synthesizer {
        val file = Sf2File("test", data, mapOf("0:0" to Sf2Preset("p", 0, 0, listOf(region))), emptyList())
        return Sf2Synthesizer(file, sampleRate)
    }

    private fun Sf2Synthesizer.renderLeft(buffers: Int): FloatArray {
        val out = FloatArray(buffers * block)
        val l = FloatArray(block)
        val r = FloatArray(block)
        repeat(buffers) { b ->
            l.fill(0f); r.fill(0f)
            render(l, r, block)
            l.copyInto(out, b * block)
        }
        return out
    }

    private fun rms(signal: FloatArray, from: Int): Float {
        var sum = 0.0
        for (i in from until signal.size) sum += signal[i] * signal[i]
        return sqrt(sum / (signal.size - from)).toFloat()
    }

    private fun sine(periodSamples: Int, periods: Int) =
        ShortArray(periodSamples * periods) { (sin(2 * PI * it / periodSamples) * 16000).toInt().toShort() }

    @Test
    fun velocityFollowsTheStandardConcaveCurve() {
        val dc = ShortArray(2000) { 16384 }
        fun levelAt(velocity: Int): Float {
            val s = synth(dc, region(2000))
            s.noteOn(0, 60, velocity)
            return s.renderLeft(4).last()
        }
        // Modulateur par défaut : 960 cB concave -> amplitude (v / 127)²
        val expected = (64f / 127f) * (64f / 127f)
        assertEquals(expected, levelAt(64) / levelAt(127), 0.005f)
    }

    @Test
    fun lowVelocityDarkensTheSound() {
        // Sinus à 12 kHz : au-dessus de la coupure à vélocité faible (13500 - 1838 cents ≈ 6,9 kHz)
        val tone = sine(4, 500)
        fun level(velocity: Int): Float {
            val s = synth(tone, region(tone.size))
            s.noteOn(0, 60, velocity)
            return rms(s.renderLeft(4), block)
        }
        val ratio = level(30) / level(127)
        val velocityOnly = (30f / 127f) * (30f / 127f)
        assertTrue("le filtre devrait atténuer en plus du volume : $ratio vs $velocityOnly", ratio < velocityOnly * 0.5f)
    }

    @Test
    fun volumeControllerUsesSquareLaw() {
        val dc = ShortArray(2000) { 16384 }
        fun levelAt(cc7: Int): Float {
            val s = synth(dc, region(2000))
            s.controlChange(0, 7, cc7)
            s.noteOn(0, 60, 127)
            return s.renderLeft(4).last()
        }
        // Modulateur par défaut CC7 -> atténuation, 960 cB concave : amplitude (cc / 127)²
        val expected = (64f / 127f) * (64f / 127f)
        assertEquals(expected, levelAt(64) / levelAt(127), 0.005f)
    }

    @Test
    fun loopUntilReleasePlaysTheTailAfterNoteOff() {
        val dc = ShortArray(3000) { 16384 }
        // Release très longue : seule la fin de l'échantillon peut arrêter la voix
        val s = synth(dc, region(3000, loopStart = 100, loopEnd = 200, envelope = VolumeEnvelope(release = 5f), loopUntilRelease = true))
        s.noteOn(0, 60, 127)
        s.renderLeft(10)
        s.noteOff(0, 60)
        s.renderLeft(8)   // 4096 échantillons > 2800 restants
        assertEquals(0, s.getActiveVoiceCount())

        val looping = synth(dc, region(3000, loopStart = 100, loopEnd = 200, envelope = VolumeEnvelope(release = 5f)))
        looping.noteOn(0, 60, 127)
        looping.renderLeft(10)
        looping.noteOff(0, 60)
        looping.renderLeft(8)
        assertEquals("une boucle continue tourne pendant la release", 1, looping.getActiveVoiceCount())
    }

    @Test
    fun lfoStartsAtZeroAndRises() {
        val lfo = Lfo(sampleRate)
        lfo.configure(null, 0)
        lfo.trigger()
        val first = lfo.processBlock(64)
        assertTrue("le LFO doit d'abord monter : $first", first > 0f)
    }

    @Test
    fun modWheelUsesTheVibratoLfoOnly() {
        val period = 48
        val tone = sine(period, 20)
        val s = synth(tone, region(tone.size))
        s.controlChange(0, 1, 127)   // molette au maximum : ±50 cents sur le LFO de vibrato
        s.noteOn(0, 60, 127)
        val out = s.renderLeft(20)
        // Le vibrato doit faire varier la période : on compare deux fenêtres décalées
        fun crossings(from: Int, to: Int): Int {
            var count = 0
            for (i in from + 1 until to) if ((out[i] > 0f) != (out[i - 1] > 0f)) count++
            return count
        }
        val a = crossings(block, block + 2400)
        val b = crossings(block + 2400, block + 4800)
        assertTrue("vibrato absent : $a / $b", abs(a - b) >= 1)
    }
}
