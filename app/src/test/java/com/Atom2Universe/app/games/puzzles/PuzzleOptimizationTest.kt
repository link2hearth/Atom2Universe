package com.Atom2Universe.app.games.puzzles

import com.Atom2Universe.app.games.kit.GridSearch
import com.Atom2Universe.app.games.kit.LatinBoard
import com.Atom2Universe.app.games.kit.PuzzleStateCache
import com.Atom2Universe.app.games.puzzles.lightup.LightUpState
import com.Atom2Universe.app.games.puzzles.pegs.PegsState
import com.Atom2Universe.app.games.puzzles.samegame.SameGameState
import com.Atom2Universe.app.games.puzzles.flood.FloodState
import com.Atom2Universe.app.games.puzzles.untangle.UntangleDragAnalysis
import com.Atom2Universe.app.games.puzzles.untangle.UntangleState
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class PuzzleOptimizationTest {
    @Test fun sameGameGardeLaGraviteLeResserrementEtLeScore() {
        val initial = SameGameState(4, 4, 3, intArrayOf(
            0, 1, 2, 0,
            0, 2, 1, 0,
            0, 1, 2, 1,
            0, 2, 1, 1,
        ), 10)
        val saved = initial.encode()
        val shifted = initial.remove(0)!!
        assertArrayEquals(intArrayOf(1, 2, 0, -1, 2, 1, 0, -1, 1, 2, 1, -1, 2, 1, 1, -1), shifted.cells)
        assertEquals(14, shifted.score)
        assertEquals(saved, initial.encode())
        val fallen = shifted.remove(2)!!
        assertArrayEquals(intArrayOf(1, 2, -1, -1, 2, 1, -1, -1, 1, 2, 1, -1, 2, 1, 1, -1), fallen.cells)
        assertEquals(14, fallen.score)
        val random = Random(57)
        repeat(100) {
            val s = SameGameState(6, 5, 4, IntArray(30) { random.nextInt(5) - 1 }, 0)
            assertEquals(s.cells.indices.any { s.groupOf(it).size >= 2 }, s.hasMoves)
        }
    }

    @Test fun lesArriveesDesFichesRestentLesMemesSansCreerDEtatsProvisoires() {
        val random = Random(502)
        for (s in listOf(PegsState.cross(7), PegsState.random(9, 9, random), PegsState(1, 3, intArrayOf(2, 2, 1), 0))) {
            val saved = s.encode()
            var moves = false
            for (from in s.cells.indices) {
                val expected = s.cells.indices.filter { to ->
                    val fx = from % s.w; val fy = from / s.w
                    val tx = to % s.w; val ty = to / s.w
                    s.cells[from] == PegsState.PEG && s.cells[to] == PegsState.HOLE &&
                        ((fx == tx && kotlin.math.abs(fy - ty) == 2) || (fy == ty && kotlin.math.abs(fx - tx) == 2)) &&
                        s.cells[((fy + ty) / 2) * s.w + (fx + tx) / 2] == PegsState.PEG
                }
                assertEquals(expected.sorted(), s.targets(from).sorted())
                moves = moves || expected.isNotEmpty()
            }
            assertEquals(moves, s.hasMoves)
            assertEquals(saved, s.encode())
        }
    }

    @Test fun lesBalayagesAkariDonnentLeMemeEclairageQueLesRayons() {
        val random = Random(863)
        for ((w, h) in listOf(1 to 1, 1 to 12, 12 to 1, 7 to 10, 12 to 12)) repeat(30) {
            val walls = IntArray(w * h) { if (random.nextInt(5) == 0) LightUpState.WALL else LightUpState.OPEN }
            val cells = IntArray(w * h) { if (walls[it] == LightUpState.OPEN) random.nextInt(3) else 0 }
            val s = LightUpState(w, h, walls, cells, IntArray(w * h))
            val expected = BooleanArray(w * h)
            val clashes = HashSet<Int>()
            for (i in cells.indices) if (cells[i] == 1) {
                val ray = s.sight(i)
                for (j in ray) expected[j] = true
                if (ray.any { it != i && cells[it] == 1 }) clashes.add(i)
            }
            val actual = s.lighting()
            assertArrayEquals(expected, actual.lit)
            assertEquals(clashes, actual.clashes)
            assertEquals(expected.indices.all { !s.isOpen(it) || expected[it] } && clashes.isEmpty(), s.isSolved)
        }
    }

    @Test fun leGlisseIncrementalConserveTousLesCroisementsEtLEtatInitial() {
        val random = Random(324)
        for (n in listOf(6, 15, 25)) {
            val s = UntangleState.generate(n, random)
            val saved = s.encode()
            for (point in 0 until n) {
                val preview = UntangleDragAnalysis(s, point)
                repeat(20) {
                    val x = random.nextFloat() * 1.4f - .2f
                    val y = random.nextFloat() * 1.4f - .2f
                    val expected = s.moved(point, x, y).crossingEdges()
                    val actual = preview.at(x, y)
                    assertEquals(expected, actual.indices.filter { actual[it] }.toSet())
                    assertSame(actual, preview.at(x, y))
                }
            }
            assertEquals(saved, s.encode())
        }
    }

    @Test fun leCacheSuitLIdentiteDeLEtatEtNeGardeQueLeDernier() {
        data class State(val value: Int)
        var calculations = 0
        val cache = PuzzleStateCache<State, IntArray> { calculations++; intArrayOf(it.value) }
        val first = State(1)
        val value = cache[first]
        repeat(100) { assertSame(value, cache[first]) }
        assertEquals(1, calculations)
        assertNotSame(value, cache[State(1)])
        assertNotSame(value, cache[first])
        assertEquals(3, calculations)
    }

    @Test fun lesSolveursAbandonnentQuandLeTravailEstAnnule() {
        fun interrupted(work: () -> Unit) {
            try {
                Thread.currentThread().interrupt()
                try {
                    work()
                    fail("La recherche annulee doit s'arreter")
                } catch (_: InterruptedException) {
                    assertTrue(Thread.currentThread().isInterrupted)
                }
            } finally { Thread.interrupted() }
        }
        interrupted { GridSearch.count(9, 2, IntArray(9) { -1 }) { _, _ -> true } }
        interrupted { LatinBoard.randomSquare(7, Random(1)) }
        interrupted { LatinBoard.countSolutions(7, IntArray(49)) { _, _ -> true } }
        interrupted { UntangleState.generate(25, Random(1)) }
        interrupted { SameGameState.bestPath(SameGameState(2, 2, 2, intArrayOf(0, 0, 1, 1), 0), 10) }
        interrupted { FloodState.greedyColour(2, 2, intArrayOf(0, 0, 1, 1)) }
    }
}
