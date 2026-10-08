package com.Atom2Universe.app.midi.sf2

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comportement audio du moteur sur des échantillons synthétiques : continuité du signal
 * (pas de marche = pas de clic), timing et polyphonie.
 */
class Sf2RenderingTest {

    private val sampleRate = 48000
    private val block = 512

    private fun region(
        length: Int,
        loopStart: Int = 0,
        loopEnd: Int = length,
        envelope: VolumeEnvelope? = VolumeEnvelope(),
        pan: Float = 0f,
        exclusiveClass: Int = 0
    ) = Sf2Region(
        keyRange = 0..127, velRange = 0..127, sampleId = 0, sampleRate = sampleRate,
        sampleStart = 0, sampleEnd = length.toLong(), loopStart = loopStart.toLong(), loopEnd = loopEnd.toLong(),
        hasLoop = true, rootKey = 60, coarseTune = 0, fineTune = 0, scaleTuning = 100, pitchCorrection = 0,
        attenuation = 0, pan = pan, exclusiveClass = exclusiveClass, reverbSend = null, chorusSend = null,
        volumeEnvelope = envelope, sampleName = "test", filterFc = null, filterQ = null, vibLfo = null,
        modLfo = null, modEnvelope = null, modEnvToPitch = null, modEnvToFilterFc = null,
        forcedKeyNum = null, forcedVelocity = null
    )

    private fun synth(data: ShortArray, regions: List<Sf2Region>, maxVoices: Int = 128): Sf2Synthesizer {
        val file = Sf2File("test", data, mapOf("0:0" to Sf2Preset("p", 0, 0, regions)), emptyList())
        return Sf2Synthesizer(file, sampleRate, maxVoices)
    }

    /** Échantillon constant : la sortie suit directement l'enveloppe et les gains. */
    private fun dc(length: Int = 2000) = ShortArray(length) { 16384 }

    private class Recording(val left: FloatArray, val right: FloatArray)

    private fun Sf2Synthesizer.record(buffers: Int): Recording {
        val left = FloatArray(buffers * block)
        val right = FloatArray(buffers * block)
        val l = FloatArray(block)
        val r = FloatArray(block)
        repeat(buffers) { b ->
            l.fill(0f); r.fill(0f)
            render(l, r, block)
            l.copyInto(left, b * block)
            r.copyInto(right, b * block)
        }
        return Recording(left, right)
    }

    private fun maxStep(signal: FloatArray, from: Int = 1, to: Int = signal.size): Float {
        var max = 0f
        for (i in maxOf(1, from) until to) max = maxOf(max, abs(signal[i] - signal[i - 1]))
        return max
    }

    @Test
    fun releaseFadesToSilenceWithoutFinalStep() {
        val s = synth(dc(), listOf(region(2000, envelope = VolumeEnvelope(release = 0.5f))))
        s.noteOn(0, 60, 127)
        val held = s.record(20)
        val level = held.left.last()
        assertTrue(level > 0.05f)

        s.noteOff(0, 60)
        val tail = s.record(80).left
        assertEquals("la voix doit finir par s'éteindre", 0f, tail.last(), 0f)
        // Avant : la release s'arrêtait net à ~0,7 % du niveau (marche de -43 dB)
        assertTrue("marche en fin de release : ${maxStep(tail) / level}", maxStep(tail) < 0.001f * level)
    }

    @Test
    fun regionWithoutVolumeEnvelopeSustains() {
        val s = synth(dc(), listOf(region(2000, envelope = null)))
        s.noteOn(0, 60, 127)
        val out = s.record(100).left   // ~1 s
        val early = out[5 * block]
        // Avant : l'enveloppe par défaut de modulation (retombée à 0 en 100 ms) était utilisée
        assertTrue("note tenue éteinte : ${out.last()} vs $early", out.last() > 0.5f * early)
    }

    @Test
    fun noteStartsAtItsFrameOffset() {
        val s = synth(dc(), listOf(region(2000)))
        s.noteOn(0, 60, 127, frameOffset = 300)
        val out = s.record(1).left
        for (i in 0 until 300) assertEquals("rien avant la position de la note", 0f, out[i], 0f)
        assertTrue(out[310] > 0f)
    }

    @Test
    fun noteReleasesAtItsFrameOffset() {
        val s = synth(dc(), listOf(region(2000, envelope = VolumeEnvelope(release = 0.05f))))
        s.noteOn(0, 60, 127)
        s.record(150)   // laisse le gain selon le nombre de voix se stabiliser
        s.noteOff(0, 60, frameOffset = 400)
        val out = s.record(1).left
        // Niveau tenu jusqu'à 400, puis décroissance
        assertEquals(out[100], out[399], out[100] * 0.01f)
        assertTrue(out[511] < out[399] * 0.95f)
    }

    @Test
    fun stereoLayersWithSameExclusiveClassBothPlay() {
        val s = synth(dc(), listOf(
            region(2000, pan = -1f, exclusiveClass = 1),
            region(2000, pan = 1f, exclusiveClass = 1)
        ))
        s.noteOn(9, 42, 127)
        val out = s.record(4)
        // Avant : la région droite coupait la gauche déclenchée juste avant
        assertTrue("canal gauche coupé", out.left.last() > 0.05f)
        assertTrue(out.right.last() > 0.05f)
    }

    @Test
    fun exclusiveClassStillChokesPreviousNote() {
        val s = synth(dc(), listOf(region(2000, pan = -1f, exclusiveClass = 1)))
        s.noteOn(9, 42, 127)
        s.record(4)
        s.noteOn(9, 46, 127)
        s.record(4)
        assertEquals(1, s.getActiveVoiceCount())
    }

    @Test
    fun stolenVoiceFadesInsteadOfBeingCut() {
        val s = synth(dc(), listOf(region(2000)), maxVoices = 1)
        s.noteOn(0, 60, 127)
        val before = s.record(10).left
        val level = before.last()

        s.noteOn(0, 64, 127)
        val after = s.record(1).left
        assertEquals("la voix volée finit son fondu dans un slot libre", 2, s.getActiveVoiceCount())
        val firstStep = abs(after[0] - level)
        // Avant : l'ancienne note était remplacée sur place, le signal tombait à 0 d'un coup
        assertTrue("saut au vol de voix : ${firstStep / level}", firstStep < 0.3f * level)
        assertTrue(maxStep(after) < 0.3f * level)

        s.record(5)
        assertEquals(1, s.getActiveVoiceCount())
    }

    @Test
    fun newNoteStartsAtCurrentPitchBend() {
        val period = 48
        val sine = ShortArray(period * 20) { (sin(2 * PI * it / period) * 16000).toInt().toShort() }
        val s = synth(sine, listOf(region(sine.size)))
        // Plage de pitch bend à 12 demi-tons (RPN 0,0), bend au maximum
        s.controlChange(0, 101, 0)
        s.controlChange(0, 100, 0)
        s.controlChange(0, 6, 12)
        s.pitchBend(0, 16383)
        s.noteOn(0, 60, 127)
        val out = s.record(1).left

        var crossings = 0
        var previous = 0f
        for (v in out) {
            if (v != 0f) {
                if (previous != 0f && (v > 0f) != (previous > 0f)) crossings++
                previous = v
            }
        }
        // 2000 Hz pendant 10,7 ms ≈ 42 passages par zéro. Avant : la note démarrait à 1000 Hz
        // et glissait vers 2000 Hz pendant ~20 ms.
        assertTrue("passages par zéro : $crossings", crossings >= 38)
    }

    @Test
    fun shortLoopWrapsWithoutDiscontinuity() {
        val period = 50
        val sine = ShortArray(period * 3) { (sin(2 * PI * it / period) * 16000).toInt().toShort() }
        val s = synth(sine, listOf(region(sine.size)))
        s.noteOn(0, 60, 127)
        s.record(5)
        val out = s.record(4).left
        val amplitude = out.maxOf { abs(it) }
        val expectedMaxStep = amplitude * 2 * PI.toFloat() / period
        // Avant : le fondu enchaîné sautait de 32 échantillons de phase à chaque tour de boucle
        assertTrue("discontinuité de boucle : ${maxStep(out) / expectedMaxStep}", maxStep(out) < expectedMaxStep * 1.1f)
    }

    @Test
    fun limiterGainChangeIsRamped() {
        val limiter = AudioLimiter().apply { autoGainEnabled = false }
        val l1 = FloatArray(block) { 0.5f }
        val r1 = FloatArray(block) { 0.5f }
        limiter.process(l1, r1, block)

        val l2 = FloatArray(block) { 0.5f }.also { it[block - 1] = 2f }
        val r2 = FloatArray(block) { 0.5f }
        limiter.process(l2, r2, block)
        // Avant : le gain tombait à 0,45 dès le premier échantillon du buffer (clic)
        assertTrue(abs(l2[0] - l1.last()) < 0.01f)
        assertTrue(maxStep(l2, to = block - 1) < 0.01f)
        assertTrue(l2[block - 1] <= 0.98f)
    }
}
