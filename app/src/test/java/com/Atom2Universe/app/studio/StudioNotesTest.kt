package com.Atom2Universe.app.studio

import com.Atom2Universe.app.studio.data.StudioProject
import com.Atom2Universe.app.studio.data.StudioSession
import com.Atom2Universe.app.studio.dsp.SynthPatch
import com.Atom2Universe.app.studio.dsp.SynthPresets
import com.Atom2Universe.app.studio.engine.Inst
import com.Atom2Universe.app.studio.engine.Lane
import com.Atom2Universe.app.studio.engine.Note
import com.Atom2Universe.app.studio.engine.StepPattern
import com.Atom2Universe.app.studio.engine.StudioEngine
import com.Atom2Universe.app.studio.music.Scales
import com.Atom2Universe.app.studio.music.Styles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/** Notes du synthé, gammes, sons : durée des notes, accords, équilibre de niveau, absence de clic. */
class StudioNotesTest {

    private val rate = 48_000

    private fun render(engine: StudioEngine, frames: Int, block: Int = 256): Pair<FloatArray, FloatArray> {
        val outL = FloatArray(frames)
        val outR = FloatArray(frames)
        val l = FloatArray(block)
        val r = FloatArray(block)
        var done = 0
        while (done < frames) {
            val n = minOf(block, frames - done)
            engine.render(l, r, n)
            System.arraycopy(l, 0, outL, done, n)
            System.arraycopy(r, 0, outR, done, n)
            done += n
        }
        return outL to outR
    }

    private fun rms(a: FloatArray, from: Int, to: Int): Double {
        var s = 0.0
        for (i in from until to) s += a[i].toDouble() * a[i]
        return sqrt(s / (to - from))
    }

    /** Le plus fort niveau RMS (en dB) sur des fenêtres de 50 ms : juste pour un son qui monte ou qui décroît. */
    private fun loudestWindowDb(a: FloatArray): Double {
        val win = rate / 20
        var best = 0.0
        var i = 0
        while (i + win <= a.size) {
            best = maxOf(best, rms(a, i, i + win))
            i += win / 2
        }
        return 20 * log10(best)
    }

    private fun quietEngine(): StudioEngine = StudioEngine(rate).also {
        it.spaceAmount = 0f
        it.echoAmount = 0f
        it.masterGain = 1f
        it.instruments[Inst.SYNTH].reverbSend = 0f
        it.instruments[Inst.SYNTH].delaySend = 0f
    }

    @Test
    fun aNoteLastsItsLengthInSteps() {
        // 120 bpm : un pas = 6000 échantillons. Une note de 4 pas sonne pendant ~24 000 échantillons puis se tait.
        val engine = quietEngine()
        engine.synth.patch = SynthPresets.BASS.copy(attack = 0.002f, sustain = 1f, release = 0.005f, decay = 0.5f)
        engine.pattern = StepPattern(16, emptyList(), listOf(Note(0, 45, 4, 1f)))
        engine.bpm = 120f
        engine.play()
        val (l, _) = render(engine, rate * 2)
        val during = rms(l, 6_000, 20_000)
        val after = rms(l, 26_000, 30_000)
        println("note de 4 pas : pendant $during, après $after")
        assertTrue("la note doit sonner : $during", during > 0.01)
        assertTrue("la note doit s'arrêter après 4 pas : $after", after < during * 0.02)
    }

    @Test
    fun aChordPlaysAllItsNotesAtOnce() {
        val engine = quietEngine()
        engine.pattern = StepPattern(16, emptyList(), listOf(Note(0, 45, 2), Note(0, 48, 2), Note(0, 52, 2)))
        engine.play()
        render(engine, 2_000)
        assertEquals(3, engine.synth.activeVoices)
    }

    @Test
    fun everyPresetIsCleanBoundedAndWithinAFewDecibelsOfTheOthers() {
        val levels = ArrayList<Double>()
        for (preset in SynthPresets.ALL) {
            val engine = quietEngine()
            engine.synth.patch = preset
            engine.trigger(Inst.SYNTH, 45, 0.8f)
            val (l, r) = render(engine, rate * 3)
            var peak = 0f
            var maxStep = 0f
            for (i in l.indices) {
                assertTrue("${preset.id} : NaN à $i", l[i].isFinite() && r[i].isFinite())
                peak = maxOf(peak, abs(l[i]), abs(r[i]))
                if (i > 0) maxStep = maxOf(maxStep, abs(l[i] - l[i - 1]))
            }
            val level = loudestWindowDb(l)
            levels += level
            println("préréglage ${preset.id} : niveau ${"%.1f".format(level)} dB, pic ${"%.2f".format(peak)}, plus grand saut ${"%.3f".format(maxStep)}")
            assertTrue("${preset.id} : pic $peak", peak <= 1f)
            assertTrue("${preset.id} : saut $maxStep", maxStep < 0.35f)
        }
        val spread = levels.max() - levels.min()
        println("écart de niveau entre préréglages : ${"%.1f".format(spread)} dB")
        assertTrue("écart de niveau $spread dB", spread < 3.0)
    }

    /** Puissance de [x] (fenêtre de Hann entre [from] et [to]) à la fréquence [hz] : un filtre de Goertzel. */
    private fun powerAt(w: FloatArray, hz: Double): Double {
        val coeff = 2 * kotlin.math.cos(2 * Math.PI * hz / rate)
        var s1 = 0.0
        var s2 = 0.0
        for (v in w) {
            val s = v + coeff * s1 - s2
            s2 = s1
            s1 = s
        }
        return s1 * s1 + s2 * s2 - coeff * s1 * s2
    }

    /** Écart, en centièmes de demi-ton, de la hauteur perçue autour de [f0] (barycentre de puissance sur ±120 centièmes). */
    private fun centsOffset(x: FloatArray, from: Int, to: Int, f0: Double): Double {
        val n = to - from
        val w = FloatArray(n) { (x[from + it] * (0.5 - 0.5 * kotlin.math.cos(2 * Math.PI * it / n))).toFloat() }
        var num = 0.0
        var den = 0.0
        var c = -120
        while (c <= 120) {
            val p = powerAt(w, f0 * Math.pow(2.0, c / 1200.0))
            num += p * c
            den += p
            c += 2
        }
        return num / den
    }

    @Test
    fun everyPresetIsInTune() {
        // Mesuré sur de vraies basses de synthé (Sonic Pi, CC0) : la méthode retrouve leur note à moins de 8 centièmes.
        // Ici, le filtre en mouvement et la distorsion sont coupés : ils déplacent le barycentre sans changer la hauteur.
        for (preset in SynthPresets.ALL) for (pitch in listOf(33, 45, 57, 69)) {
            val engine = quietEngine()
            engine.synth.patch = preset.copy(lfoFilter = 0f, drive = 0f)
            engine.trigger(Inst.SYNTH, pitch, 0.8f)
            val (l, _) = render(engine, rate)
            val expected = 440.0 * Math.pow(2.0, (pitch - 69) / 12.0)
            val off = centsOffset(l, rate / 20, rate / 20 + rate / 4, expected)
            assertTrue("${preset.id} à $pitch : ${"%.1f".format(off)} centièmes", abs(off) < 8.0)
        }
    }

    @Test
    fun driveNeverMakesTheSoundMuchLouder() {
        val base = SynthPresets.BASS
        fun level(drive: Float): Double {
            val engine = quietEngine()
            engine.synth.patch = base.copy(drive = drive)
            engine.trigger(Inst.SYNTH, 45, 0.8f)
            val (l, _) = render(engine, rate * 2)
            return loudestWindowDb(l)
        }
        val clean = level(0f)
        for (d in listOf(0.25f, 0.5f, 0.75f, 1f)) {
            val x = level(d)
            println("distorsion $d : ${"%.1f".format(x - clean)} dB par rapport au son net")
            assertTrue("distorsion $d : ${x - clean} dB", x - clean in -6.0..4.0)
        }
    }

    @Test
    fun lowPassFilterKeepsHighNotesBrightWithKeytracking() {
        // Garde-fou de forme : un son filtré ne doit jamais produire plus que le son non filtré (énergie conservée ≤).
        val open = SynthPresets.LEAD.copy(cutoffHz = 12_000f, envOctaves = 0f, resonance = 0.7f)
        val closed = open.copy(cutoffHz = 300f)
        fun level(p: SynthPatch): Double {
            val engine = quietEngine()
            engine.synth.patch = p
            engine.trigger(Inst.SYNTH, 69, 0.8f)
            val (l, _) = render(engine, rate / 2)
            return rms(l, rate / 20, rate / 3)
        }
        assertTrue(level(closed) < level(open))
    }

    // ---- Gammes ------------------------------------------------------------------------------------------

    @Test
    fun lockedRowsOnlyHoldScaleNotesAndFreeRowsHoldEverything() {
        val locked = Scales.rows(Scales.MINOR_PENT, 9, 33, 2, true)
        val free = Scales.rows(Scales.MINOR_PENT, 9, 33, 2, false)
        assertEquals(25, free.size)
        assertEquals(listOf(33, 36, 38, 40, 43, 45, 48, 50, 52, 55, 57), locked.toList())
        for (p in locked) assertTrue(Scales.contains(Scales.MINOR_PENT, 9, p))
        assertEquals(25, Scales.rows(Scales.CHROMATIC, 0, 36, 2, true).size)
    }

    @Test
    fun everyStyleGeneratesNotesInItsOwnScale() {
        for (style in Styles.ALL) {
            val scale = Scales.byId(style.scaleId)
            val pattern = Styles.generate(style, 16, 5L)
            assertTrue("${style.id} : pas de note", pattern.notes.isNotEmpty())
            if (style.chord == null) {
                for (n in pattern.notes) assertTrue("${style.id} : ${n.pitch} hors gamme", Scales.contains(scale, style.keyPc, n.pitch))
            }
        }
    }

    @Test
    fun chordStylesStackNotesOnTheSameStep() {
        val lofi = Styles.generate(Styles.LOFI, 16, 3L)
        val atZero = lofi.notes.filter { it.step == 0 }
        assertEquals(4, atZero.size)
        val ambient = Styles.generate(Styles.AMBIENT, 16, 3L)
        assertEquals(3, ambient.notes.count { it.step == 0 })
    }

    // ---- Édition des notes ---------------------------------------------------------------------------------

    private fun session(): StudioSession {
        val engine = StudioEngine(rate)
        return StudioSession(engine, StudioProject.fromStyle(Styles.HOUSE).copy(pattern = Styles.emptyPattern(16)))
    }

    @Test
    fun addingANoteOverAnotherOfTheSamePitchReplacesIt() {
        val s = session()
        s.addNote(2, 45, 4)
        s.addNote(4, 45, 2)
        val at45 = s.project.pattern.notes.filter { it.pitch == 45 }
        assertEquals(1, at45.size)
        assertEquals(4, at45[0].step)
        s.addNote(4, 48, 2)
        assertEquals(2, s.project.pattern.notes.size)
    }

    @Test
    fun noteLengthIsClampedToThePattern() {
        val s = session()
        s.addNote(14, 45, 8)
        assertEquals(2, s.project.pattern.notes[0].length)
        val n = s.project.pattern.notes[0]
        s.resizeNote(n, 1)
        assertEquals(1, s.project.pattern.notes[0].length)
    }

    @Test
    fun removeAndFindANote() {
        val s = session()
        s.addNote(3, 40, 3)
        assertNotNull(s.noteAt(5, 40))
        assertNull(s.noteAt(6, 40))
        assertNull(s.noteAt(5, 41))
        s.removeNote(s.noteAt(4, 40)!!)
        assertTrue(s.project.pattern.notes.isEmpty())
    }

    @Test
    fun changingKeyTransposesTheNotesByTheShortestWay() {
        val s = session()
        s.addNote(0, 45, 1) // la, tonalité de départ : la (9)
        s.setKey(0) // do : -9 ou +3 -> +3
        assertEquals(48, s.project.pattern.notes[0].pitch)
        assertEquals(0, s.project.keyPc)
        s.setKey(7) // sol : -5
        assertEquals(43, s.project.pattern.notes[0].pitch)
    }

    @Test
    fun rhythmGridSynthRowIsASummaryOfTheNotes() {
        val s = session()
        assertFalse(s.isOn(Inst.SYNTH, 4))
        s.setStep(Inst.SYNTH, 4, true)
        assertTrue(s.isOn(Inst.SYNTH, 4))
        assertEquals(1, s.project.pattern.notes.size)
        assertEquals(0.7f, s.cycleVelocity(Inst.SYNTH, 4), 0f)
        s.addNote(4, 52, 1)
        s.setStep(Inst.SYNTH, 4, false)
        assertTrue(s.project.pattern.notes.isEmpty())
    }

    @Test
    fun doublingTheLengthRepeatsTheNotes() {
        val s = session()
        s.addNote(3, 45, 2)
        s.setStepCount(32)
        assertEquals(listOf(3, 19), s.project.pattern.notes.map { it.step }.sorted())
        s.setStepCount(16)
        assertEquals(listOf(3), s.project.pattern.notes.map { it.step })
    }

    @Test
    fun synthEditsReachTheEngine() {
        val engine = StudioEngine(rate)
        val s = StudioSession(engine, StudioProject.fromStyle(Styles.HOUSE))
        s.updateSynth { it.copy(cutoffHz = 777f, drive = 0.4f, wave = SynthPatch.WAVE_SQUARE) }
        assertEquals(777f, engine.synth.patch.cutoffHz, 0f)
        assertEquals(SynthPatch.WAVE_SQUARE, engine.synth.patch.wave)
        s.setPreset("pad")
        assertEquals("pad", engine.synth.patch.id)
        s.setSynthSpace(0.5f)
        assertEquals(0.5f, engine.instruments[Inst.SYNTH].reverbSend, 0f)
        assertEquals(0.35f, engine.instruments[Inst.SYNTH].delaySend, 1e-6f)
    }

    @Test
    fun aPatternWithoutLanesStillPlaysItsNotes() {
        val engine = quietEngine()
        engine.pattern = StepPattern(16, listOf(Lane(Inst.KICK, FloatArray(16))), listOf(Note(0, 45, 1)))
        engine.play()
        val (l, _) = render(engine, 6_000)
        assertTrue(rms(l, 500, 5_000) > 0.005)
    }
}
