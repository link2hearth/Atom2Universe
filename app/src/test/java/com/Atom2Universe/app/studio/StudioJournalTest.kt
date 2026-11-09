package com.Atom2Universe.app.studio

import com.Atom2Universe.app.studio.data.StudioHistory
import com.Atom2Universe.app.studio.data.StudioLibrary
import com.Atom2Universe.app.studio.data.StudioProject
import com.Atom2Universe.app.studio.data.StudioProjectCodec
import com.Atom2Universe.app.studio.data.StudioSession
import com.Atom2Universe.app.studio.engine.Inst
import com.Atom2Universe.app.studio.engine.StudioEngine
import com.Atom2Universe.app.studio.engine.TrackKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Le journal des actions : on ferme, on rouvre, on retrouve le morceau et tout l'historique, sans réécrire le morceau à chaque action. */
class StudioJournalTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun library() = StudioLibrary(File(tmp.root, "songs"))

    private fun open(lib: StudioLibrary, id: String): StudioSession {
        val e = lib.load(id)!!
        return StudioSession(StudioEngine(48_000), e.project, e.history, lib.journal(id)).apply { coalesceMs = 0 }
    }

    private fun same(a: StudioProject, b: StudioProject) = StudioProjectCodec.encode(a) == StudioProjectCodec.encode(b)

    @Test
    fun reopeningGivesTheSongAndItsHistoryBack() {
        val lib = library()
        val id = lib.create("t", StudioProject.blank()).id
        val s = open(lib, id)
        s.setStep(Inst.KICK, 0, true)
        s.setStep(Inst.KICK, 4, true)
        s.addNote(2, 62, 3)
        s.setBpm(133)
        s.flush()
        val before = s.project

        val again = open(lib, id)
        assertTrue(same(before, again.project))
        assertEquals(133, again.project.bpm)
        // tout l'historique est là : trois actions à annuler, une par une
        again.undo()
        assertTrue(again.project.pattern.notes.isEmpty())
        again.undo()
        assertFalse(again.isOn(Inst.KICK, 4))
        assertTrue(again.isOn(Inst.KICK, 0))
        again.undo()
        assertFalse(again.isOn(Inst.KICK, 0))
        assertFalse(again.canUndo)
        // le tempo n'est pas annulable : il est resté
        assertEquals(133, again.project.bpm)
    }

    @Test
    fun undoneActionsCanStillBeRedoneAfterReopening() {
        val lib = library()
        val id = lib.create("t", StudioProject.blank()).id
        val s = open(lib, id)
        s.setStep(Inst.SNARE, 4, true)
        s.setStep(Inst.SNARE, 12, true)
        s.undo()
        s.flush()

        val again = open(lib, id)
        assertTrue(again.isOn(Inst.SNARE, 4))
        assertFalse(again.isOn(Inst.SNARE, 12))
        assertTrue(again.canRedo)
        again.redo()
        assertTrue(again.isOn(Inst.SNARE, 12))
    }

    @Test
    fun anAddedTrackAndItsSoundComeBackAndCanBeUndone() {
        val lib = library()
        val id = lib.create("t", StudioProject.blank()).id
        val s = open(lib, id)
        val t = s.addTrack(TrackKind.SYNTH)
        s.addNote(0, 60, 2, track = t)
        s.setDrumVoice(Inst.KICK, 1, 3, 120)
        s.flush()

        val again = open(lib, id)
        assertNotNull(again.project.trackOrNull(t))
        assertEquals(1, again.project.track(Inst.KICK).kit)
        again.undo()
        assertEquals(0, again.project.track(Inst.KICK).kit)
        again.undo()
        assertTrue(again.project.pattern.notes.none { it.track == t })
        again.undo()
        assertEquals(null, again.project.trackOrNull(t))
    }

    @Test
    fun theSongFileIsNotRewrittenForEachAction() {
        val lib = library()
        val id = lib.create("t", StudioProject.blank()).id
        val main = File(tmp.root, "songs/$id.json")
        val stamp = main.readText()
        val s = open(lib, id)
        for (i in 0 until 20) {
            s.setStep(Inst.KICK, i % 16, i < 16)
            s.flush()
        }
        assertEquals(stamp, main.readText())
        assertTrue(File(tmp.root, "songs/$id.journal").length() > 0)
    }

    @Test
    fun aLongJournalIsFoldedAndTheHistoryKeepsItsLimit() {
        val lib = library()
        val id = lib.create("t", StudioProject.blank()).id
        val s = open(lib, id)
        for (i in 0 until 500) {
            s.setStep(Inst.HAT, i % 16, (i / 16) % 2 == 0)
            s.flush()
        }
        val before = s.project
        val again = open(lib, id)
        assertTrue(same(before, again.project))
        var n = 0
        while (again.canUndo) {
            again.undo()
            n++
        }
        assertEquals(StudioHistory.MAX, n)
        // le journal a été réécrit : bien plus court que 500 actions
        assertTrue(File(tmp.root, "songs/$id.journal").readLines().size < 400)
    }

    @Test
    fun aJournalCutInTheMiddleOfALineIsRepaired() {
        val lib = library()
        val id = lib.create("t", StudioProject.blank()).id
        val s = open(lib, id)
        s.setStep(Inst.KICK, 0, true)
        s.flush()
        File(tmp.root, "songs/$id.journal").appendText("{\"t\":\"a\",\"h\":1,\"c\":[{\"k\":\"p0\",\"b\":\"")
        val again = open(lib, id)
        assertTrue(again.isOn(Inst.KICK, 0))
        again.setStep(Inst.KICK, 8, true)
        again.flush()
        val third = open(lib, id)
        assertTrue(third.isOn(Inst.KICK, 0))
        assertTrue(third.isOn(Inst.KICK, 8))
    }

    @Test
    fun aJournalOfAnotherVersionIsIgnored() {
        val lib = library()
        val id = lib.create("t", StudioProject.blank()).id
        val s = open(lib, id)
        s.setStep(Inst.KICK, 0, true)
        s.flush()
        val journal = File(tmp.root, "songs/$id.journal")
        journal.writeText(journal.readText().replaceFirst("\"rev\":0", "\"rev\":7"))
        val again = open(lib, id)
        assertFalse(again.isOn(Inst.KICK, 0))
    }

    @Test
    fun theSixtyFourStepSongSurvivesTheCodec() {
        val s = StudioSession(StudioEngine(48_000), StudioProject.blank()).apply { coalesceMs = 0 }
        s.setStepCount(64)
        val back = StudioProjectCodec.decode(StudioProjectCodec.encode(s.project))
        assertNotNull(back)
        assertEquals(64, back!!.baseSteps)
    }
}
