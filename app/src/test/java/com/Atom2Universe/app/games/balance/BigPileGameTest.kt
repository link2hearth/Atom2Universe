package com.Atom2Universe.app.games.balance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * Garde-fous du mode « Gros tas » : une balance chargée bas et large tient, un tas trop haut ne
 * tient pas, une pièce ne remonte jamais de plus d'une case, deux pièces d'un même rang se
 * posent ensemble, et une réserve pleine lâche la pièce suivante.
 */
class BigPileGameTest {

    private val dt = 1f / 120f
    private val cell = BigPileRules.CELL

    private fun newGame(auto: Boolean = false) = BigPileGame(Random(3)).apply {
        autoSpawn = auto
        setViewport(6.6f)
        newGame()
    }

    private fun BigPileGame.run(seconds: Float) {
        repeat((seconds / dt).toInt()) { step(dt) }
    }

    /** Remplit toute la planche de carrés, deux rangs à la fois, sur [doubleRows] étages. */
    private fun BigPileGame.fill(doubleRows: Int) {
        for (layer in 0 until doubleRows) {
            val bottom = BigPileRules.PLANK_TOP + (layer * 2 + 0.4f) * cell
            for (col in 1..13 step 2) dropForTest(BigPiece.Kind.SQUARE, col, bottom)
            run(1.5f)
        }
    }

    @Test
    fun aLowWidePileStaysLevel() {
        val g = newGame()
        g.fill(3)
        g.run(20f)
        println("tas bas : pente ${g.leanDeg}°, ${g.score} pièces, vies ${g.lives}")
        assertEquals(BigPileGame.Phase.PLAYING, g.phase)
        assertEquals(BigPileRules.LIVES, g.lives)
        assertTrue(abs(g.leanDeg) < 3f)
    }

    @Test
    fun aTallPileCannotStandLong() {
        val g = newGame()
        val t0 = System.nanoTime()
        g.fill(6)
        // Un rien de travers suffit à lancer un tas trop haut.
        g.dropForTest(BigPiece.Kind.MONO, 13, BigPileRules.PLANK_TOP + 13 * cell)
        var t = 0f
        while (g.phase == BigPileGame.Phase.PLAYING && t < 30f) {
            g.step(dt)
            t += dt
        }
        val steps = (6 * 1.5f + t) / dt
        println("tas haut : fin ${g.ending} après ${t}s, ${(System.nanoTime() - t0) / 1e6 / steps} ms par pas")
        assertEquals(BigPileGame.Phase.OVER, g.phase)
    }

    @Test
    fun aPieceCannotClimbMoreThanOneRow() {
        val g = newGame()
        val start = 4f
        val p = g.dropForTest(BigPiece.Kind.MONO, 7, start)
        val ceiling = g.rowBottom(p.row) + cell + 1e-3f
        repeat(12) {
            val x = g.colLeft(p.col) + cell / 2f
            var y = g.rowBottom(p.row) + cell / 2f
            assertTrue(g.touchDown(x, y, -1))
            repeat(6) {
                y += cell
                g.touchMove(x, y)
                g.step(dt)
            }
            g.touchUp(tap = false)
            assertTrue(p.row <= p.floorRow + BigPileRules.SUB)
            assertTrue(g.rowBottom(p.row) <= ceiling)
            g.run(0.05f)
        }
    }

    @Test
    fun aPieceSlidesByTenthsOfACell() {
        val g = newGame()
        val p = g.dropForTest(BigPiece.Kind.MONO, 7, 4f)
        val col0 = p.col
        val x = g.colLeft(p.col) + cell / 2f
        val y = g.rowBottom(p.row) + cell / 2f
        assertTrue(g.touchDown(x, y, -1))
        g.touchMove(x + 0.4f * cell, y)
        assertEquals(col0 + 4, p.col)
        g.touchUp(tap = false)
    }

    @Test
    fun aPieceDroppedOverItsNeighbourSnapsAlongside() {
        val g = newGame(auto = true)
        g.autoSpawn = false
        val mono = g.dropForTest(BigPiece.Kind.MONO, 7, 4f)
        val p = g.reserve[0]!!
        // Lâchée en mordant de trois dixièmes sur la case voisine, au même rang.
        val left = g.colLeft(mono.col) + 0.7f * cell
        val bottom = g.rowBottom(mono.row)
        assertTrue(g.touchDown(-2f, g.gridTop + 0.5f, g.slotAt(-2f)))
        g.touchMove(left + p.w * cell / 2f, bottom - 0.8f * cell)
        g.touchUp(tap = false)
        assertEquals(BigPiece.State.FALLING, p.state)
        for (c in p.cells) {
            val dx = abs(g.cellLeft(p.col, c) - g.cellLeft(mono.col, mono.cells[0]))
            val dy = abs(g.cellBottom(p.row, c) - g.cellBottom(mono.row, mono.cells[0]))
            assertTrue(dx >= cell - 1e-3f || dy >= cell - 1e-3f)
        }
    }

    @Test
    fun twoPiecesOnOneRowLandTogether() {
        val g = newGame()
        val a = g.dropForTest(BigPiece.Kind.MONO, 3, 3f)
        val b = g.dropForTest(BigPiece.Kind.MONO, 12, 3f)
        var guard = 0
        while (a.state == BigPiece.State.FALLING && guard++ < 2000) {
            g.step(dt)
            assertEquals(a.state, b.state)
        }
        assertEquals(BigPiece.State.LANDED, a.state)
        assertEquals(2, g.score)
    }

    @Test
    fun aFullReserveDropsTheNextPiece() {
        val g = newGame(auto = true)
        g.run(15f)
        assertTrue(g.reserve.all { it != null })
        assertTrue(g.pieces.any { it.state != BigPiece.State.RESERVE })
    }
}
