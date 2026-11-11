package com.Atom2Universe.app.studio.music

import com.Atom2Universe.app.studio.engine.Note
import com.Atom2Universe.app.studio.data.Track
import com.Atom2Universe.app.studio.engine.SynthBank
import com.Atom2Universe.app.studio.engine.TrackKind
import kotlin.math.abs

/** Une même fenêtre MIDI pour les deux claviers, toujours bordée de touches blanches. */
internal class StudioRegister(widthDp: Int, heightDp: Int, chosenOctaves: Int? = null) {
    val octaves = chosenOctaves ?: if (widthDp >= 680 && heightDp >= 720) 2 else 1
    val whiteCount = 7 * octaves + 1
    private val lastStart = whites.size - whiteCount
    private var start = whites.indexOf(MIDDLE_C)
    val low get() = whites[start]
    val high get() = whites[start + whiteCount - 1]

    fun setLow(whitePitch: Int) {
        start = whites.binarySearch(whitePitch).coerceIn(0, lastStart)
    }

    fun reveal(pitch: Int) {
        while (pitch < low && start > 0) start--
        while (pitch > high && start < lastStart) start++
    }

    fun shift(whiteKeys: Int) { start = (start + whiteKeys).coerceIn(0, lastStart) }

    fun focus(notes: List<Note>, emptyOctave: Int? = null) {
        if (notes.isEmpty()) {
            if (emptyOctave != null) setLow(emptyOctave)
            return
        }
        val pitches = IntArray(128)
        for (n in notes) if (n.pitch in pitches.indices) pitches[n.pitch]++
        for (p in 1..127) pitches[p] += pitches[p - 1]
        val previous = start
        start = (0..lastStart).maxWith(compareBy<Int> {
            pitches[whites[it + whiteCount - 1]] - (pitches.getOrNull(whites[it] - 1) ?: 0)
        }.thenBy { -abs(it - previous) })
    }

    companion object {
        const val MIDDLE_C = 60
        val whites = (0..127).filter { it % 12 in listOf(0, 2, 4, 5, 7, 9, 11) }.toIntArray()

        /** Le do de référence de la préécoute, commun aux pistes vides et au jeu au doigt. */
        fun chipOctave(track: Track): Int? = when {
            track.kind != TrackKind.SYNTH || track.synthBank == SynthBank.STUDIO -> null
            track.synthBank == SynthBank.FM -> if (track.fmProgram in 13..14) 36 else 60
            track.program in 32..39 -> 36
            else -> 60
        }

        fun padPitches(notes: List<Note>, scale: Scale, keyPc: Int, emptyOctave: Int?): IntArray {
            val low = notes.minOfOrNull { it.pitch } ?: ((emptyOctave ?: MIDDLE_C) + keyPc)
            val tonic = (low - Math.floorMod(low - keyPc, 12)).coerceAtLeast(keyPc)
            return Scales.rows(scale, keyPc, tonic, 1, true)
        }
    }
}
