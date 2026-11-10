package com.Atom2Universe.app.studio

import com.Atom2Universe.app.studio.data.MySounds
import com.Atom2Universe.app.studio.data.StudioProject
import com.Atom2Universe.app.studio.data.StudioProjectCodec
import com.Atom2Universe.app.studio.data.StudioSession
import com.Atom2Universe.app.studio.dsp.SynthPresets
import com.Atom2Universe.app.studio.dsp.SynthSample
import com.Atom2Universe.app.studio.engine.Arp
import com.Atom2Universe.app.studio.engine.Inst
import com.Atom2Universe.app.studio.engine.Lane
import com.Atom2Universe.app.studio.engine.Note
import com.Atom2Universe.app.studio.engine.StepCond
import com.Atom2Universe.app.studio.engine.StepPattern
import com.Atom2Universe.app.studio.engine.StudioEngine
import com.Atom2Universe.app.studio.export.SampleTools
import com.Atom2Universe.app.studio.music.Styles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/** Jeu et mise en forme : pompe, pas « parfois », décompte et métronome, roulements, glissé, son enregistré, sons gardés. */
class StudioPlayTest {

    private val rate = 48_000

    private fun quiet(): StudioEngine = StudioEngine(rate).also {
        it.spaceAmount = 0f
        it.echoAmount = 0f
        for (inst in it.allInstruments) {
            inst.reverbSend = 0f
            inst.delaySend = 0f
        }
    }

    private fun render(engine: StudioEngine, frames: Int, block: Int = 256): FloatArray {
        val out = FloatArray(frames)
        val l = FloatArray(block)
        val r = FloatArray(block)
        var done = 0
        while (done < frames) {
            val n = minOf(block, frames - done)
            engine.render(l, r, n)
            for (i in 0 until n) out[done + i] = l[i] + r[i]
            done += n
        }
        return out
    }

    private fun rms(a: FloatArray, from: Int, to: Int): Double {
        var s = 0.0
        for (i in from until to) s += a[i].toDouble() * a[i]
        return sqrt(s / (to - from))
    }

    private fun lane(track: Int, steps: Int, vararg on: Int, cond: Int = StepCond.ALWAYS): Lane {
        val v = FloatArray(steps)
        val c = IntArray(steps)
        for (s in on) { v[s] = 1f; c[s] = cond }
        return Lane(track, v, cond = c)
    }

    @Test
    fun aKickMakesThePumpingTrackDuck() {
        // Un synthé tenu, une grosse caisse muette… puis audible : avec la pompe, le synthé se creuse juste après le coup
        fun synthLevelAfterKick(pump: Float): Double {
            val e = quiet()
            e.synth.patch = SynthPresets.PAD.copy(attack = 0.002f)
            e.instrument(Inst.SYNTH)!!.pump = pump
            e.trigger(Inst.SYNTH, 57, 1f)
            render(e, rate / 2)
            // On n'écoute que le synthé : la grosse caisse a son volume à zéro (pas muette : une piste muette ne fait pas pomper)
            e.instrument(Inst.KICK)!!.level = 0f
            e.trigger(Inst.KICK)
            val out = render(e, rate / 10)
            return rms(out, rate / 50, rate / 20)
        }
        val flat = synthLevelAfterKick(0f)
        val pumped = synthLevelAfterKick(1f)
        assertTrue("la pompe creuse le synthé ($pumped contre $flat)", pumped < flat * 0.45)
    }

    @Test
    fun theNeedleComesBackBeforeTheNextBeat() {
        val e = quiet()
        e.bpm = 120f
        e.synth.patch = SynthPresets.PAD.copy(attack = 0.002f)
        e.instrument(Inst.SYNTH)!!.pump = 1f
        e.instrument(Inst.KICK)!!.level = 0f
        e.trigger(Inst.SYNTH, 57, 1f)
        render(e, rate / 2)
        val before = rms(render(e, rate / 20), 0, rate / 20)
        e.trigger(Inst.KICK)
        val beat = rate / 2 // un temps à 120 bpm
        val out = render(e, beat)
        val late = rms(out, beat * 9 / 10, beat)
        assertTrue("le synthé est revenu avant le temps suivant ($late contre $before)", late > before * 0.85)
    }

    @Test
    fun stepConditionsFollowTheLoops() {
        val e = quiet()
        e.bpm = 120f
        val steps = 16
        e.pattern = StepPattern(
            steps,
            listOf(
                lane(Inst.SNARE, steps, 0, cond = StepCond.EVERY_OTHER),
                lane(Inst.CLAP, steps, 0, cond = StepCond.EVERY_FOURTH),
                lane(Inst.HAT, steps, 0, cond = StepCond.RANDOM),
            ),
        )
        val loops = 8
        val hits = HashMap<Int, MutableList<Int>>()
        e.onStep = { _, _ -> }
        // On compte les coups en regardant les voix actives juste après chaque début de tour
        val stepFrames = rate * 60 / 120 / 4
        render(e, 256)
        e.play()
        for (loop in 0 until loops) {
            render(e, 64)
            for (inst in listOf(Inst.SNARE, Inst.CLAP, Inst.HAT)) {
                if (e.instrument(inst)!!.activeVoices > 0) hits.getOrPut(inst) { ArrayList() } += loop
            }
            render(e, steps * stepFrames - 64)
            // Les sons s'éteignent avant le tour suivant (16 pas = 2 s) : chaque tour se lit seul
        }
        assertEquals(listOf(1, 3, 5, 7), hits[Inst.SNARE])
        assertEquals(listOf(3, 7), hits[Inst.CLAP])
        val random = hits[Inst.HAT].orEmpty().size
        assertTrue("au hasard : ni jamais ni toujours ($random sur $loops)", random in 1 until loops)
    }

    @Test
    fun stepConditionsSurviveSaveAndCopy() {
        val s = StudioSession(StudioEngine(rate), StudioProject.fromStyle(Styles.HOUSE))
        s.editStep(Inst.KICK, 0, cond = StepCond.EVERY_FOURTH)
        assertEquals(StepCond.EVERY_FOURTH, s.stepInfo(Inst.KICK, 0)!!.cond)
        val back = StudioProjectCodec.decode(StudioProjectCodec.encode(s.project))!!
        assertEquals(StepCond.EVERY_FOURTH, back.pattern.lane(Inst.KICK)!!.cond[0])
        // Copier-coller garde la condition ; rallumer un pas le remet à « toujours »
        val clip = s.copyRange(setOf(Inst.KICK), 0, 0)
        s.paste(clip, 2)
        assertEquals(StepCond.EVERY_FOURTH, s.stepInfo(Inst.KICK, 2)!!.cond)
        s.setStep(Inst.KICK, 2, false)
        s.setStep(Inst.KICK, 2, true)
        assertEquals(StepCond.ALWAYS, s.stepInfo(Inst.KICK, 2)!!.cond)
    }

    @Test
    fun countInClicksOneBarThenPlays() {
        val e = quiet()
        e.bpm = 120f
        e.pattern = StepPattern(16, listOf(lane(Inst.KICK, 16, 0)))
        val starts = ArrayList<Long>()
        e.onStep = { step, f -> if (step == 0) starts += f }
        render(e, 256)
        e.play(countIn = true)
        val stepFrames = rate * 60 / 120 / 4
        val out = render(e, stepFrames * 18)
        assertEquals("le pas 0 joue une fois, après une mesure", 1, starts.size)
        assertEquals(256L + 16L * stepFrames, starts[0])
        // Pendant le décompte : quatre clics (du son au début de chaque temps), rien entre eux
        for (beat in 0 until 4) {
            val at = beat * 4 * stepFrames
            assertTrue("clic du temps $beat", rms(out, at, at + 400) > 0.01)
            assertTrue("silence après le clic $beat", rms(out, at + stepFrames, at + 2 * stepFrames) < 1e-4)
        }
        assertEquals(0, e.countInBeats)
    }

    @Test
    fun aRollHitsOnEverySixteenthWhileHeldAndIsRecorded() {
        val e = quiet()
        e.bpm = 120f
        e.pattern = StepPattern(16, listOf(lane(Inst.SNARE, 16)))
        e.recordHits = true
        render(e, 256)
        e.play()
        val stepFrames = rate * 60 / 120 / 4
        render(e, stepFrames * 2 + 100) // un peu après le pas 2
        e.rollOn(Inst.SNARE, 0.8f)
        render(e, stepFrames * 4)
        e.rollOff(Inst.SNARE)
        render(e, stepFrames * 4)
        val positions = ArrayList<Double>()
        e.takeLiveHits { track, pos, vel ->
            assertEquals(Inst.SNARE, track)
            assertEquals(0.8f, vel, 1e-6f)
            positions += pos
        }
        // Le premier coup tout de suite (vers le pas 2), puis sur les pas 4, 5, 6 : le roulement attend qu'on reste au moins
        // une double-croche (un simple toucher ne frappe qu'une fois)
        assertEquals(4, positions.size)
        assertEquals(2.0, positions[0], 0.1)
        for (k in 1 until positions.size) assertEquals((k + 3).toDouble(), positions[k], 1e-3)
        // Écrits dans le motif, sur les pas les plus proches
        val s = StudioSession(StudioEngine(rate), StudioProject.blank())
        for (p in positions) s.recordHit(Inst.SNARE, p, 0.8f)
        for (step in listOf(2, 4, 5, 6)) assertTrue("pas $step", s.isOn(Inst.SNARE, step))
        assertTrue(!s.isOn(Inst.SNARE, 3))
        assertTrue(!s.isOn(Inst.SNARE, 7))

        // Un toucher bref : un seul coup
        val e2 = quiet()
        e2.recordHits = true
        render(e2, 256)
        e2.play()
        e2.rollOn(Inst.SNARE)
        render(e2, rate / 10) // 100 ms
        e2.rollOff(Inst.SNARE)
        render(e2, rate)
        var count = 0
        e2.takeLiveHits { _, _, _ -> count++ }
        assertEquals(1, count)
    }

    @Test
    fun padRollDivisionsAreWrittenOnWholeGridSteps() {
        for (division in listOf(2, 4)) {
            val e = quiet()
            e.bpm = 120f
            e.recordHits = true
            e.play()
            val stepFrames = rate / 8
            render(e, 128)
            e.rollOn(Inst.SNARE, stepsPerHit = division)
            render(e, stepFrames * 13)
            e.rollOff(Inst.SNARE)
            render(e, stepFrames * 2)
            val steps = mutableListOf<Int>()
            e.takeLiveHits { _, p, _ -> steps += Math.round(p).toInt() }
            assertEquals(listOf(0) + (2 * division..12 step division), steps)
        }
    }

    @Test
    fun arpFollowsTheSwing() {
        // Régression : avec du swing, une note posée sur un pas impair (joué en retard) voyait son coup d'arpège reporté au pas suivant
        val e = quiet()
        e.bpm = 120f
        e.swing = 0.3f
        e.setArp(Inst.SYNTH, Arp.UP, 1f, 1)
        val steps = listOf(1, 3, 6, 9)
        e.pattern = StepPattern(16, emptyList(), steps.map { Note(it, 45, 1) })
        val notes = ArrayList<Long>()
        val starts = ArrayList<Long>()
        e.onSynthNote = { _, f -> notes += f }
        e.onStep = { step, f -> if (step % 16 in steps) starts += f }
        render(e, 256)
        e.play()
        render(e, rate * 60 / 120 * 4 * 2)
        assertEquals(starts.size, notes.size)
        for (k in starts.indices) assertTrue("coup $k à ${notes[k]} pour un pas à ${starts[k]}", abs(notes[k] - starts[k]) <= 1)
    }

    @Test
    fun glideChangesPitchWithoutANewNote() {
        val e = quiet()
        e.synth.patch = SynthPresets.LEAD
        e.trigger(Inst.SYNTH, 57, 1f)
        render(e, rate / 10)
        e.glide(Inst.SYNTH, 57, 64)
        render(e, rate / 10)
        assertEquals("une seule voix : la même note a changé de hauteur", 1, e.synth.activeVoices)
        e.release(Inst.SYNTH, 64)
        render(e, rate)
        assertEquals("relâchée par sa nouvelle hauteur", 0, e.synth.activeVoices)
    }

    @Test
    fun aHeldNoteSurvivesTheLoop() {
        // Régression : un doigt qui tenait une note que la boucle jouait aussi la voyait se couper à la fin de la note du motif
        val e = quiet()
        e.bpm = 120f
        e.synth.patch = SynthPresets.PAD
        e.pattern = StepPattern(16, emptyList(), listOf(Note(0, 57, 1)))
        render(e, 256)
        e.trigger(Inst.SYNTH, 57, 0.9f)
        e.play()
        render(e, rate) // la note du motif commence et finit pendant ce temps
        assertEquals("le doigt tient toujours la note", 1, e.synth.activeVoices)
        e.release(Inst.SYNTH, 57)
        e.stop()
        render(e, rate * 3)
        assertEquals(0, e.synth.activeVoices)
    }

    @Test
    fun endingAPanelPreviewDoesNotReleaseTheSameKeyHeldByThePlayer() {
        val e = quiet()
        e.synth.patch = SynthPresets.PAD.copy(release = 0.005f)
        e.trigger(Inst.SYNTH, 57)
        render(e, 256)
        e.trigger(Inst.SYNTH, 57)
        render(e, 256)
        e.release(Inst.SYNTH, 57)
        render(e, rate)
        assertEquals(1, e.synth.activeVoices)
        e.release(Inst.SYNTH, 57)
        render(e, rate)
        assertEquals(0, e.synth.activeVoices)
    }

    @Test
    fun theRecorderWritesOnEachTrack() {
        val s = StudioSession(StudioEngine(rate), StudioProject.fromStyle(Styles.HOUSE))
        val second = s.addTrack(com.Atom2Universe.app.studio.engine.TrackKind.SYNTH)
        var pos = 0.0
        val rec = com.Atom2Universe.app.studio.data.LiveRecorder(s, { true }, { pos }, { Inst.SYNTH })
        rec.armed = true
        rec.press(60, 2.0, Inst.SYNTH)
        rec.press(64, 2.0, second)
        pos = 4.0
        rec.finishAll()
        assertTrue(s.project.pattern.notes.any { it.track == Inst.SYNTH && it.pitch == 60 && it.step == 2 && it.length == 2 })
        assertTrue(s.project.pattern.notes.any { it.track == second && it.pitch == 64 && it.step == 2 && it.length == 2 })
    }

    @Test
    fun liveDriveDistortsWithoutChangingTheVolume() {
        // La touche des pads qu'on fait bouger distord le son, mais ne doit pas le rendre plus fort ni plus faible
        fun take(drive: Float): FloatArray {
            val e = quiet()
            e.synth.patch = SynthPresets.PAD.copy(attack = 0.002f, lfoFilter = 0f)
            e.setLiveDrive(Inst.SYNTH, drive)
            e.trigger(Inst.SYNTH, 57, 0.9f)
            return render(e, rate / 2)
        }
        val clean = take(0f)
        val dirty = take(1f)
        val a = rms(clean, rate / 4, rate / 2)
        val b = rms(dirty, rate / 4, rate / 2)
        assertTrue("même volume ($b contre $a)", b in a * 0.75..a * 1.33)
        // … mais plus d'harmoniques : le signal varie plus vite
        fun roughness(x: FloatArray): Double {
            var d = 0.0
            for (i in rate / 4 + 1 until rate / 2) d += (x[i] - x[i - 1]).toDouble().let { it * it }
            return sqrt(d) / rms(x, rate / 4, rate / 2)
        }
        assertTrue("plus rugueux (${roughness(dirty)} contre ${roughness(clean)})", roughness(dirty) > roughness(clean) * 1.3)
    }

    @Test
    fun expressionSoftensTheTrack() {
        fun level(expr: Float): Double {
            val e = quiet()
            e.synth.patch = SynthPresets.PAD.copy(attack = 0.002f)
            e.setExpression(Inst.SYNTH, expr)
            e.trigger(Inst.SYNTH, 57, 1f)
            val out = render(e, rate / 2)
            return rms(out, rate / 4, rate / 2)
        }
        assertTrue(level(0.2f) < level(1f) * 0.4)
    }

    /** Un sinus de [freq] Hz, comme un « ouuu » chanté. */
    private fun sine(freq: Double, seconds: Double = 1.0): FloatArray =
        FloatArray((rate * seconds).toInt()) { (0.6 * sin(2 * PI * freq * it / rate)).toFloat() }

    @Test
    fun theRootOfARecordedSoundIsFound() {
        assertEquals(57f, SampleTools.detectRoot(sine(220.0), rate), 0.1f) // La 2
        assertEquals(64f, SampleTools.detectRoot(sine(329.63), rate), 0.1f) // Mi 3
        assertEquals(45f, SampleTools.detectRoot(sine(110.0), rate), 0.1f)
        // Du bruit n'a pas de hauteur : il se joue tel quel sur le do du milieu
        val rnd = java.util.Random(1)
        assertEquals(60f, SampleTools.detectRoot(FloatArray(rate) { (rnd.nextFloat() - 0.5f) }, rate), 0f)
    }

    @Test
    fun theSynthPlaysTheRecordedSoundInTune() {
        val e = quiet()
        e.setSynthSample(SynthSample(sine(220.0), rate, 57f))
        e.synth.patch = SynthPresets.MY_VOICE
        e.trigger(Inst.SYNTH, 69, 1f) // une octave au-dessus : 440 Hz
        val out = render(e, rate / 2)
        val period = rate / SampleTools.detectRoot(out.copyOfRange(rate / 10, rate / 2), rate).let { 440.0 * Math.pow(2.0, (it - 69) / 12.0) }
        assertEquals(rate / 440.0, period, 0.5)
        // Sans son enregistré, ce patch se tait (jamais de bruit inattendu)
        val e2 = quiet()
        e2.synth.patch = SynthPresets.MY_VOICE
        e2.trigger(Inst.SYNTH, 60, 1f)
        val silent = render(e2, rate / 10)
        assertTrue(rms(silent, 0, silent.size) < 1e-6)
    }

    @Test
    fun keptSoundsComeBack() {
        val dir = Files.createTempDirectory("mysounds").toFile()
        try {
            val store = MySounds(File(dir, "my_sounds.json"))
            val kept = store.keep(SynthPresets.ACID.copy(resonance = 9f), "Ma basse")
            assertEquals("mine:Ma basse", kept.id)
            assertEquals(listOf(kept), store.all())
            store.keep(SynthPresets.LEAD, "Lead")
            store.keep(SynthPresets.PAD, "Ma basse") // même nom : remplacé
            assertEquals(listOf("mine:Lead", "mine:Ma basse"), store.all().map { it.id })
            assertEquals(SynthPresets.PAD.cutoffHz, store.all()[1].cutoffHz, 1e-3f)
            store.forget("mine:Lead")
            assertEquals(listOf("Ma basse"), store.all().map { MySounds.nameOf(it.id) })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun songBlocksMoveAndGo() {
        val s = StudioSession(StudioEngine(rate), StudioProject.fromStyle(Styles.HOUSE))
        for (k in listOf(0, 1, 2, 3)) s.chainAdd(k)
        s.chainMove(0, 3)
        assertEquals(listOf(1, 2, 3, 0), s.project.chain)
        s.chainRemoveAt(1)
        assertEquals(listOf(1, 3, 0), s.project.chain)
    }

    @Test
    fun pumpIsSaved() {
        val s = StudioSession(StudioEngine(rate), StudioProject.fromStyle(Styles.HOUSE))
        s.updateTrack(Inst.SYNTH) { it.copy(pump = 0.7f) }
        val back = StudioProjectCodec.decode(StudioProjectCodec.encode(s.project))!!
        assertEquals(0.7f, back.track(Inst.SYNTH).mix.pump, 1e-6f)
    }
}
