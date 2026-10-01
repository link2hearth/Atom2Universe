package com.Atom2Universe.app.midi

import com.Atom2Universe.app.midi.visualizer.MidiLiveState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Garde-fou : MidiLiveState est la source unique des claviers animés. Si elle ment, les claviers
 * se désynchronisent, donc ces règles ne doivent pas bouger.
 */
class MidiLiveStateTest {

    @After
    fun tearDown() = MidiLiveState.reset()

    @Test
    fun noteOnThenOffIsReadBack() {
        MidiLiveState.noteOn(2, 60, 100)
        assertEquals(100, MidiLiveState.velocity(2, 60))
        assertEquals(0, MidiLiveState.velocity(3, 60))

        MidiLiveState.noteOff(2, 60)
        assertEquals(0, MidiLiveState.velocity(2, 60))
    }

    @Test
    fun clearNotesKeepsInstrumentsButResetDropsThem() {
        MidiLiveState.setProgram(1, 24)
        MidiLiveState.noteOn(1, 50, 80)

        MidiLiveState.clearNotes()
        assertEquals(0, MidiLiveState.velocity(1, 50))
        assertEquals(24, MidiLiveState.program(1))

        MidiLiveState.reset()
        assertEquals(-1, MidiLiveState.program(1))
    }

    @Test
    fun versionMovesOnlyWhenSomethingChanged() {
        val before = MidiLiveState.notesVersion()
        MidiLiveState.noteOff(0, 10)            // rien ne sonnait : pas de changement
        MidiLiveState.clearNotes()              // idem
        assertEquals(before, MidiLiveState.notesVersion())

        MidiLiveState.noteOn(0, 10, 90)
        assertNotEquals(before, MidiLiveState.notesVersion())
    }

    @Test
    fun outOfRangeEventsAreIgnored() {
        MidiLiveState.noteOn(16, 60, 100)
        MidiLiveState.noteOn(0, 128, 100)
        MidiLiveState.noteOn(-1, 60, 100)
        assertEquals(0, MidiLiveState.velocity(16, 60))
        assertEquals(0, MidiLiveState.velocity(0, 128))
        assertEquals(emptyList<Int>(), MidiLiveState.activeNotes(0))
    }

    @Test
    fun activeNotesListsSoundingNotesOfOneChannel() {
        MidiLiveState.noteOn(4, 60, 90)
        MidiLiveState.noteOn(4, 64, 90)
        MidiLiveState.noteOn(5, 67, 90)
        assertEquals(listOf(60, 64), MidiLiveState.activeNotes(4))
    }
}
