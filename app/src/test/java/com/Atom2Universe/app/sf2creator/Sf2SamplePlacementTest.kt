package com.Atom2Universe.app.sf2creator

import com.Atom2Universe.app.sf2creator.data.Sf2SamplePlacement
import org.junit.Assert.assertEquals
import org.junit.Test

/** Où vont les sons ajoutés depuis des fichiers audio. */
class Sf2SamplePlacementTest {

    private fun place(pitches: List<Int?>, occupied: Set<Int> = emptySet()) =
        Sf2SamplePlacement.place(pitches, occupied).map { listOf(it.rootNote, it.low, it.high) }

    @Test
    fun oneSoundInAnEmptyInstrumentPlaysEverywhere() {
        assertEquals(listOf(listOf(57, 0, 127)), place(listOf(57)))
        assertEquals(listOf(listOf(60, 0, 127)), place(listOf(null)))
    }

    @Test
    fun pitchedSoundsShareTheKeyboard() {
        assertEquals(
            listOf(listOf(48, 0, 54), listOf(60, 55, 66), listOf(72, 67, 127)),
            place(listOf(48, 60, 72))
        )
        // Deux sons sur la même note partagent la même plage
        assertEquals(listOf(listOf(60, 0, 127), listOf(60, 0, 127)), place(listOf(60, 60)))
    }

    @Test
    fun soundsAddedToAnInstrumentKeepTheirOwnNote() {
        assertEquals(listOf(listOf(64, 64, 64)), place(listOf(64), occupied = setOf(60)))
    }

    @Test
    fun soundsWithoutPitchTakeFreeKeysLikeADrumKit() {
        assertEquals(
            listOf(listOf(36, 36, 36), listOf(38, 38, 38), listOf(39, 39, 39)),
            place(listOf(null, null, null), occupied = setOf(37))
        )
        assertEquals(
            listOf(listOf(36, 36, 36), listOf(62, 62, 62)),
            place(listOf(null, 62))
        )
    }
}
