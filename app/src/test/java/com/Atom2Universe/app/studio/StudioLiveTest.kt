package com.Atom2Universe.app.studio

import com.Atom2Universe.app.studio.data.LiveRecorder
import com.Atom2Universe.app.studio.data.MidiNotes
import com.Atom2Universe.app.studio.data.StudioProject
import com.Atom2Universe.app.studio.data.StudioProjectCodec
import com.Atom2Universe.app.studio.data.StudioSession
import com.Atom2Universe.app.studio.engine.Arp
import com.Atom2Universe.app.studio.engine.Inst
import com.Atom2Universe.app.studio.engine.Note
import com.Atom2Universe.app.studio.engine.StepPattern
import com.Atom2Universe.app.studio.engine.StudioEngine
import com.Atom2Universe.app.studio.music.Styles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Arpégiateur, jeu en direct (clavier, MIDI) et enregistreur. */
class StudioLiveTest {

    private val rate = 48_000

    private fun render(engine: StudioEngine, frames: Int, block: Int = 256) {
        val l = FloatArray(block)
        val r = FloatArray(block)
        var done = 0
        while (done < frames) {
            val n = minOf(block, frames - done)
            engine.render(l, r, n)
            done += n
        }
    }

    private fun quiet(): StudioEngine = StudioEngine(rate).also {
        it.spaceAmount = 0f
        it.echoAmount = 0f
    }

    /** Joue les notes tenues [pitches] avec l'arpège et rend les notes réellement parties vers le synthé : (hauteur, échantillon). */
    private fun arpRun(mode: Int, pitches: List<Int>, octaves: Int = 1, division: Float = 1f, seconds: Int = 2): List<Pair<Int, Long>> {
        val e = quiet()
        e.setArp(Inst.SYNTH, mode, division, octaves)
        val out = ArrayList<Pair<Int, Long>>()
        e.onSynthNote = { p, f -> out += p to f }
        render(e, 256) // le mode est pris en compte au premier bloc
        for (p in pitches) e.trigger(Inst.SYNTH, p, 0.8f)
        render(e, rate * seconds)
        return out
    }

    @Test
    fun arpUpWalksTheHeldNotesInOrderOnTheTempoGrid() {
        val notes = arpRun(Arp.UP, listOf(52, 45, 48)) // tenues dans le désordre : jouées de la plus grave à la plus aiguë
        assertTrue(notes.size >= 12)
        assertEquals(listOf(45, 48, 52, 45, 48, 52, 45, 48), notes.take(8).map { it.first })
        // 120 bpm, double-croche : un coup tous les 6000 échantillons, exactement
        for (k in 1 until notes.size) assertEquals(6000L, notes[k].second - notes[k - 1].second)
    }

    @Test
    fun arpDownAndUpDownAndOctaves() {
        assertEquals(listOf(52, 48, 45, 52, 48, 45), arpRun(Arp.DOWN, listOf(45, 48, 52)).take(6).map { it.first })
        assertEquals(listOf(45, 48, 52, 48, 45, 48, 52, 48), arpRun(Arp.UP_DOWN, listOf(45, 48, 52)).take(8).map { it.first })
        assertEquals(listOf(45, 48, 57, 60, 45, 48), arpRun(Arp.UP, listOf(45, 48), octaves = 2).take(6).map { it.first })
        // une seule note : elle se répète
        assertEquals(listOf(45, 45, 45), arpRun(Arp.UP_DOWN, listOf(45)).take(3).map { it.first })
    }

    @Test
    fun arpRateChangesTheSpacing() {
        val eighth = arpRun(Arp.UP, listOf(45, 48), division = 2f)
        assertEquals(12_000L, eighth[1].second - eighth[0].second)
        val thirtySecond = arpRun(Arp.UP, listOf(45, 48), division = 0.5f)
        assertEquals(3_000L, thirtySecond[1].second - thirtySecond[0].second)
    }

    @Test
    fun arpRandomStaysInTheHeldNotesAndIsReproducible() {
        val a = arpRun(Arp.RANDOM, listOf(45, 48, 52)).map { it.first }
        val b = arpRun(Arp.RANDOM, listOf(45, 48, 52)).map { it.first }
        assertEquals(a, b)
        assertTrue(a.all { it in listOf(45, 48, 52) })
        assertTrue("le hasard doit varier : $a", a.toSet().size > 1)
    }

    @Test
    fun releasingTheKeysStopsTheArp() {
        val e = quiet()
        e.setArp(Inst.SYNTH, Arp.UP, 1f, 1)
        val notes = ArrayList<Pair<Int, Long>>()
        e.onSynthNote = { p, f -> notes += p to f }
        render(e, 256)
        e.trigger(Inst.SYNTH, 45, 0.8f)
        render(e, rate / 2)
        e.release(Inst.SYNTH, 45)
        render(e, 2_000)
        val count = notes.size
        render(e, rate)
        assertEquals("plus aucun coup après le relâchement", count, notes.size)
        render(e, rate)
        assertEquals(0, e.synth.activeVoices)
    }

    @Test
    fun arpNoteIsReleasedBeforeTheNextOneStarts() {
        val e = quiet()
        e.setArp(Inst.SYNTH, Arp.UP, 1f, 1)
        render(e, 256)
        e.trigger(Inst.SYNTH, 45, 0.8f)
        e.trigger(Inst.SYNTH, 48, 0.8f)
        render(e, 6_000 * 4)
        assertTrue("jamais plus d'une note d'arpège à la fois sans traîne de relâchement : ${e.synth.activeVoices}", e.synth.activeVoices <= 3)
    }

    @Test
    fun patternChordsFeedTheArp() {
        val e = quiet()
        e.setArp(Inst.SYNTH, Arp.UP, 1f, 1)
        e.pattern = StepPattern(16, emptyList(), listOf(Note(0, 45, 8), Note(0, 48, 8), Note(0, 52, 8)))
        val notes = ArrayList<Pair<Int, Long>>()
        e.onSynthNote = { p, f -> notes += p to f }
        render(e, 256)
        e.play()
        render(e, rate * 3)
        // L'accord dure 8 pas (48 000 échantillons) : 8 coups, puis plus rien jusqu'au retour du motif.
        val first = notes.filter { it.second < 48_000 }
        assertEquals(8, first.size)
        assertEquals(listOf(45, 48, 52, 45, 48, 52, 45, 48), first.map { it.first })
        // calé sur la grille du motif : le premier coup tombe avec le premier pas
        assertEquals(256L, first[0].second)
    }

    @Test
    fun arpPlaysEveryPatternNoteAtAnyTempo() {
        // Régression : à 124 bpm un pas dure 5 806,45 échantillons (pas entier). L'arpège sautait alors au pas suivant et
        // le synthé se taisait ; changer le tempo (120, 125 : pas entiers) « réparait » tout.
        val steps = listOf(0, 1, 3, 5, 9, 10, 11, 13)
        for (bpm in listOf(120f, 124f, 97f, 133.3f, 151.7f, 68f)) {
            val e = quiet()
            e.bpm = bpm
            e.setArp(Inst.SYNTH, Arp.UP, 1f, 1)
            e.pattern = StepPattern(16, emptyList(), steps.map { Note(it, 45, 1) })
            val notes = ArrayList<Long>()
            e.onSynthNote = { _, f -> notes += f }
            val starts = ArrayList<Long>()
            e.onStep = { step, f -> if (step % 16 in steps) starts += f }
            render(e, 256)
            e.play()
            render(e, (rate * 60.0 / bpm * 4 * 32).toInt()) // deux motifs
            assertEquals("bpm $bpm : un coup d'arpège par note du motif", starts.size, notes.size)
            for (k in starts.indices) {
                assertTrue("bpm $bpm : coup $k à ${notes[k]} pour un pas à ${starts[k]}", abs(notes[k] - starts[k]) <= 1)
            }
        }
    }

    @Test
    fun arpStaysOnTheStepsAfterATempoChange() {
        // Régression : la grille de l'arpège partait du début de la lecture au tempo du moment. Changer le tempo pendant la
        // lecture (boutons − / + maintenus) la décalait des pas : l'arpège jouait à côté, ou pas du tout.
        val steps = listOf(0, 1, 3, 5, 9, 10, 11, 13)
        val e = quiet()
        e.bpm = 120f
        e.setArp(Inst.SYNTH, Arp.UP, 1f, 1)
        e.pattern = StepPattern(16, emptyList(), steps.map { Note(it, 45, 1) })
        val notes = ArrayList<Long>()
        val starts = ArrayList<Long>()
        e.onSynthNote = { _, f -> notes += f }
        e.onStep = { step, f -> if (step % 16 in steps) starts += f }
        render(e, 256)
        e.play()
        render(e, (rate * 60.0 / 120 * 4).toInt()) // un motif à 120 bpm
        for (bpm in listOf(124f, 97f, 133.3f)) {
            e.bpm = bpm
            render(e, (rate * 60.0 / bpm).toInt()) // un temps, le temps de quitter le pas commencé à l'ancien tempo
            notes.clear()
            starts.clear()
            render(e, (rate * 60.0 / bpm * 4 * 2).toInt()) // deux motifs
            assertEquals("bpm $bpm : un coup d'arpège par note du motif", starts.size, notes.size)
            for (k in starts.indices) {
                assertTrue("bpm $bpm : coup $k à ${notes[k]} pour un pas à ${starts[k]}", abs(notes[k] - starts[k]) <= 1)
            }
        }
    }

    @Test
    fun turningTheArpOffReleasesEverything() {
        val e = quiet()
        e.setArp(Inst.SYNTH, Arp.UP, 1f, 1)
        render(e, 256)
        e.trigger(Inst.SYNTH, 45, 0.8f)
        render(e, rate / 4)
        e.setArp(Inst.SYNTH, Arp.OFF, 1f, 1)
        render(e, rate * 2)
        assertEquals(0, e.synth.activeVoices)
        // et le synthé rejoue directement
        val notes = ArrayList<Int>()
        e.onSynthNote = { p, _ -> notes += p }
        e.trigger(Inst.SYNTH, 60, 0.8f)
        render(e, 512)
        assertEquals(listOf(60), notes)
    }

    @Test
    fun playPositionAdvancesOneStepPerStep() {
        val e = quiet()
        e.pattern = Styles.generate(Styles.HOUSE, 16, 1L)
        e.bpm = 120f
        assertEquals(0.0, e.playPosition, 0.0)
        e.play()
        render(e, 256)
        render(e, 6_000 * 3)
        // 3 pas + 256 échantillons ≈ 3,04 pas, à un bloc près
        assertTrue("position ${e.playPosition}", abs(e.playPosition - 3.04) < 0.1)
        e.stop()
        render(e, 256)
        assertEquals(0.0, e.playPosition, 0.0)
    }

    @Test
    fun severalThreadsCanSendCommandsWithoutLosingAny() {
        val e = quiet()
        val got = java.util.concurrent.atomic.AtomicInteger()
        e.onSynthNote = { _, _ -> got.incrementAndGet() }
        render(e, 256)
        val threads = (0 until 4).map { t ->
            Thread { for (i in 0 until 30) e.trigger(Inst.SYNTH, 40 + t, 0.5f) }
        }
        // l'audio consomme pendant que les fils écrivent
        val consumer = Thread { repeat(400) { render(e, 64, 64) } }
        consumer.start()
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        consumer.join()
        render(e, 1024)
        // la file garde 255 commandes : 120 envoyées ne doivent pas se perdre
        assertEquals(120, got.get())
    }

    // ---- Enregistreur et MIDI --------------------------------------------------------------------------

    private fun session(): StudioSession =
        StudioSession(StudioEngine(rate), StudioProject.fromStyle(Styles.HOUSE).copy(pattern = Styles.emptyPattern(16)))

    @Test
    fun recorderPlacesNotesOnTheNearestStepAndRoundsTheirLength() {
        val s = session()
        var pos = 0.0
        val rec = LiveRecorder(s, { true }, { pos })
        rec.armed = true
        pos = 3.1; rec.press(45)
        pos = 5.4; rec.release(45) // 2,3 pas tenus : 2
        pos = 3.6; rec.press(48)
        pos = 3.8; rec.release(48) // très court : au moins 1 pas
        val n = s.project.pattern.notes.sortedBy { it.pitch }
        assertEquals(2, n.size)
        assertEquals(3, n[0].step)
        assertEquals(2, n[0].length)
        assertEquals(4, n[1].step) // 3,6 arrondi au pas 4
        assertEquals(1, n[1].length)
    }

    @Test
    fun recorderWrapsAroundTheLoopAndIgnoresWhenNotArmedOrStopped() {
        val s = session()
        var pos = 0.0
        var playing = true
        val rec = LiveRecorder(s, { playing }, { pos })
        pos = 15.7; rec.press(45) // pas armé : rien
        pos = 16.5; rec.release(45)
        assertTrue(s.project.pattern.notes.isEmpty())
        rec.armed = true
        pos = 31.8; rec.press(45) // deuxième tour : 31,8 -> pas 32 -> 0
        pos = 32.6; rec.release(45)
        assertEquals(0, s.project.pattern.notes[0].step)
        playing = false
        rec.press(48)
        rec.release(48)
        assertEquals(1, s.project.pattern.notes.size)
    }

    @Test
    fun recorderEndsHeldNotesWhenStopped() {
        val s = session()
        var pos = 2.0
        val rec = LiveRecorder(s, { true }, { pos })
        rec.armed = true
        rec.press(45)
        pos = 6.0
        rec.finishAll()
        assertEquals(4, s.project.pattern.notes[0].length)
    }

    @Test
    fun midiMessagesBecomeNotes() {
        val on = ArrayList<Pair<Int, Float>>()
        val off = ArrayList<Int>()
        fun feed(vararg b: Int) = MidiNotes.handle(ByteArray(b.size) { b[it].toByte() }, { n, v -> on += n to v }, { n -> off += n })
        feed(0x90, 60, 127)
        feed(0x90, 62, 64)
        feed(0x90, 60, 0) // note on de vélocité 0 = relâchement
        feed(0x80, 62, 40)
        feed(0x95, 64, 100) // autre canal : accepté
        feed(0x99, 36, 100) // canal 10 (batterie) : ignoré
        feed(0xB0, 1, 50) // contrôleur : ignoré
        feed(0x90, 60) // trop court : ignoré
        assertEquals(listOf(60 to 1f, 62 to 64 / 127f, 64 to 100 / 127f), on)
        assertEquals(listOf(60, 62), off)
    }

    @Test
    fun arpSettingsAreSavedAndSurviveAStyleChange() {
        val engine = StudioEngine(rate)
        val s = StudioSession(engine, StudioProject.fromStyle(Styles.HOUSE))
        s.setArpMode(Arp.UP_DOWN)
        s.setArpRate(2)
        s.setArpOctaves(3)
        assertEquals(Arp.UP_DOWN, s.project.track(Inst.SYNTH).arpMode)
        assertEquals(0.5f, StudioSession.ARP_DIVISIONS[s.project.track(Inst.SYNTH).arpRate], 0f)
        assertEquals(3, s.project.track(Inst.SYNTH).arpOctaves)
        val back = StudioProjectCodec.decode(StudioProjectCodec.encode(s.project))
        assertNotNull(back)
        assertEquals(Arp.UP_DOWN, back!!.track(Inst.SYNTH).arpMode)
        assertEquals(2, back.track(Inst.SYNTH).arpRate)
        assertEquals(3, back.track(Inst.SYNTH).arpOctaves)
        s.applyStyle(Styles.TECHNO)
        assertEquals(Arp.UP_DOWN, s.project.track(Inst.SYNTH).arpMode)
        assertEquals(3, s.project.track(Inst.SYNTH).arpOctaves)
        assertFalse(s.project.styleId == "house")
    }
}
