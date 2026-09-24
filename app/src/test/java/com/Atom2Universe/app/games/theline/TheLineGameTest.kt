package com.Atom2Universe.app.games.theline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TheLineGameTest {

    private fun generated(mode: TheLineMode, diff: TheLineDifficulty): TheLineGame {
        val p = TheLineGenerator.generate(mode, diff)!!
        return TheLineGame().apply { this.mode = mode; difficulty = diff; loadPuzzle(p) }
    }

    /** Rejoue un tracé case par case ; chaque pas doit avancer. */
    private fun play(g: TheLineGame, id: Int, cells: List<TLCoord>) {
        assertEquals(id, g.begin(cells.first()))
        for (c in cells.drop(1)) assertEquals("pas vers $c", TheLineGame.Step.ADVANCED, g.extend(id, c))
    }

    /** Le chemin du générateur passe par les bornes dans l'ordre, et le rejouer gagne. */
    @Test
    fun generatorTrackWins() {
        for (diff in TheLineDifficulty.entries) repeat(15) {
            val g = generated(TheLineMode.SINGLE, diff)
            val p = g.puzzle!!
            assertTrue(p.checkpoints.size in diff.checkpointsMin..diff.checkpointsMax)
            assertEquals(p.path.first(), p.checkpoints.first())
            assertEquals(p.path.last(), p.checkpoints.last())
            val order = p.checkpoints.map { p.path.indexOf(it) }
            assertEquals("bornes hors d'ordre", order.sorted(), order)
            play(g, 0, p.path)
            assertTrue(g.isComplete())
        }
    }

    @Test
    fun generatorWiringWins() {
        for (diff in TheLineDifficulty.entries) repeat(15) {
            val g = generated(TheLineMode.MULTI, diff)
            g.puzzle!!.segments.forEachIndexed { i, seg -> play(g, i, seg.cells) }
            assertTrue(g.isComplete())
        }
    }

    @Test
    fun checkpointsAreStrictlyIncreasing() {
        for (len in 2..60) for (wanted in 2..9) {
            val idx = TheLineGenerator.pickCheckpoints(len, wanted)
            assertEquals(0, idx.first()); assertEquals(len - 1, idx.last())
            assertTrue(idx.zipWithNext().all { (a, b) -> b > a })
        }
    }

    /** Une borne atteinte trop tôt est refusée ; on ne peut pas non plus partir de la dernière. */
    @Test
    fun checkpointOrderIsEnforced() {
        // Grille 4 × 1 : bornes 1 en (0,0), 2 en (1,0), 3 en (3,0).
        val path = (0 until 4).map { TLCoord(it, 0) }
        val g = TheLineGame()
        g.loadPuzzle(TheLinePuzzle(TheLineMode.SINGLE, 4, 1, emptySet(), path,
            endpoints = path.first() to path.last(), checkpoints = listOf(path[0], path[2], path[3])))
        assertEquals(-1, g.begin(path[3]))
        assertEquals(0, g.begin(path[0]))
        assertEquals(TheLineGame.Step.ADVANCED, g.extend(0, path[1]))
        assertEquals(TheLineGame.Step.ADVANCED, g.extend(0, path[2]))
        assertEquals(TheLineGame.Step.ADVANCED, g.extend(0, path[3]))
        assertTrue(g.isComplete())

        // Même grille, borne 2 et 3 inversées : la 3 barre le chemin de la 2.
        val g2 = TheLineGame()
        g2.loadPuzzle(TheLinePuzzle(TheLineMode.SINGLE, 4, 1, emptySet(), path,
            endpoints = path.first() to path.last(), checkpoints = listOf(path[0], path[3], path[1])))
        g2.begin(path[0])
        assertEquals(TheLineGame.Step.REFUSED, g2.extend(0, path[1]))
    }

    @Test
    fun retractingShortensTheTrack() {
        val path = (0 until 5).map { TLCoord(it, 0) }
        val g = TheLineGame()
        g.loadPuzzle(TheLinePuzzle(TheLineMode.SINGLE, 5, 1, emptySet(), path,
            endpoints = path.first() to path.last(), checkpoints = listOf(path[0], path[4])))
        play(g, 0, path.take(4))
        assertEquals(TheLineGame.Step.RETRACTED, g.extend(0, path[2]))
        assertEquals(3, g.paths[0].sequence.size)
        // Reprendre du doigt au milieu coupe la piste après ce point.
        assertEquals(0, g.begin(path[1]))
        assertEquals(2, g.paths[0].sequence.size)
    }

    /** Un fil tiré à travers un autre le coupe ; il ne traverse jamais un connecteur étranger. */
    @Test
    fun wireCutsAnotherWire() {
        // Grille 3 × 3 : fil 0 de (0,0) à (2,0) par la ligne du haut, fil 1 de (0,1) à (0,2).
        val a = listOf(TLCoord(0, 0), TLCoord(1, 0), TLCoord(2, 0))
        val b = listOf(TLCoord(0, 1), TLCoord(0, 2))
        val path = a + b
        val g = TheLineGame()
        g.loadPuzzle(TheLinePuzzle(TheLineMode.MULTI, 3, 3, emptySet(), path, segments = listOf(
            TLSegment(1, a, a.first(), a.last()), TLSegment(2, b, b.first(), b.last()))))
        play(g, 0, a)
        assertTrue(g.paths[0].complete)
        g.begin(TLCoord(0, 1))
        assertEquals(TheLineGame.Step.ADVANCED, g.extend(1, TLCoord(1, 1)))
        assertEquals(TheLineGame.Step.ADVANCED, g.extend(1, TLCoord(2, 1)))
        assertEquals(TheLineGame.Step.REFUSED, g.extend(1, TLCoord(2, 0)))
        // Remonter sur (1,0) coupe le fil 0 juste avant.
        g.begin(TLCoord(1, 1))
        assertEquals(TheLineGame.Step.ADVANCED, g.extend(1, TLCoord(1, 0)))
        assertEquals(listOf(TLCoord(0, 0)), g.paths[0].sequence)
        assertFalse(g.paths[0].complete)
    }

    @Test
    fun saveRoundTrip() {
        for (mode in TheLineMode.entries) {
            val g = generated(mode, TheLineDifficulty.MEDIUM)
            val p = g.puzzle!!
            val first = if (mode == TheLineMode.SINGLE) p.path else p.segments[0].cells
            play(g, 0, first.take(first.size / 2 + 1))
            g.rewardClaimed = true
            val line = g.serialize()
            val back = TheLineGame()
            assertTrue(back.deserialize(line))
            assertEquals(mode, back.mode)
            assertEquals(TheLineDifficulty.MEDIUM, back.difficulty)
            assertEquals(p.blockedIndices, back.puzzle!!.blockedIndices)
            assertEquals(p.checkpoints, back.puzzle!!.checkpoints)
            assertEquals(p.segments.map { it.cells }, back.puzzle!!.segments.map { it.cells })
            assertEquals(g.paths.map { it.sequence }, back.paths.map { it.sequence })
            assertTrue(back.rewardClaimed)
        }
    }
}
