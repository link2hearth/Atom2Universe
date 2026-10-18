package com.Atom2Universe.app.games.minesweeper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Garde-fou du Demineur : le premier clic ne doit jamais perdre, le nombre de mines doit
 * etre exact, et le compte des voisines doit correspondre aux mines reelles.
 */
class MinesweeperGameTest {

    private fun allCells(g: MinesweeperGame) =
        (0 until g.rows).flatMap { r -> (0 until g.cols).map { c -> r to c } }

    @Test fun lePremierClicEstToujoursSansDangerEtOuvreUneZone() {
        repeat(200) {
            val g = MinesweeperGame(9, 9, 10)
            g.reveal(4, 4)
            assertEquals(GameState.PLAYING, g.gameState)
            // Le clic et ses huit voisines sont sans mine : le premier clic ouvre donc toujours une zone.
            for (dr in -1..1) for (dc in -1..1) assertFalse(g.grid[4 + dr][4 + dc].isMine)
            assertTrue(g.revealedCount > 1)
        }
    }

    @Test fun lePremierClicDansUnCoinFonctionneAussi() {
        repeat(100) {
            val g = MinesweeperGame(9, 9, 10)
            g.reveal(0, 0)
            assertEquals(GameState.PLAYING, g.gameState)
            assertFalse(g.grid[0][0].isMine)
        }
    }

    @Test fun lesMinesSontExactementCellesDemandeesEtLesChiffresSontJustes() {
        for (difficulty in MinesweeperDifficulty.values()) {
            val g = MinesweeperGame(difficulty.cols, difficulty.cols, difficulty.mines)
            g.reveal(g.rows / 2, g.cols / 2)
            assertEquals(difficulty.mines, allCells(g).count { (r, c) -> g.grid[r][c].isMine })
            for ((r, c) in allCells(g)) {
                if (g.grid[r][c].isMine) continue
                val around = (-1..1).sumOf { dr -> (-1..1).count { dc ->
                    (dr != 0 || dc != 0) && r + dr in 0 until g.rows && c + dc in 0 until g.cols && g.grid[r + dr][c + dc].isMine
                } }
                assertEquals("voisines de ($r,$c) en $difficulty", around, g.grid[r][c].adjacentMines)
            }
        }
    }

    @Test fun uneZoneSansMineVoisineOuvreSesBordsSansJamaisToucherUneMine() {
        val g = MinesweeperGame(16, 16, 30)
        g.reveal(8, 8)
        for ((r, c) in allCells(g)) {
            val cell = g.grid[r][c]
            assertFalse("une mine ne doit pas etre revelee par la zone", cell.isMine && cell.state == CellState.REVEALED)
            if (cell.state == CellState.REVEALED && cell.adjacentMines == 0) {
                for (dr in -1..1) for (dc in -1..1) {
                    val nr = r + dr; val nc = c + dc
                    if (nr in 0 until g.rows && nc in 0 until g.cols) assertEquals("voisine de ($r,$c)", CellState.REVEALED, g.grid[nr][nc].state)
                }
            }
        }
        assertEquals(allCells(g).count { (r, c) -> g.grid[r][c].state == CellState.REVEALED }, g.revealedCount)
    }

    @Test fun toucherUneMinePerdEtRevelePlusRien() {
        val g = MinesweeperGame(9, 9, 10)
        g.reveal(4, 4)
        val (r, c) = allCells(g).first { (r, c) -> g.grid[r][c].isMine }
        g.reveal(r, c)
        assertEquals(GameState.LOST, g.gameState)
        assertEquals(r to c, g.detonatedCell)
        assertTrue(allCells(g).filter { (r, c) -> g.grid[r][c].isMine }.none { (r, c) -> g.grid[r][c].state == CellState.HIDDEN })
        val revealedBefore = g.revealedCount
        val (r2, c2) = allCells(g).first { (r, c) -> g.grid[r][c].state == CellState.HIDDEN && !g.grid[r][c].isMine }
        g.reveal(r2, c2)
        assertEquals("rien ne se passe une fois la partie perdue", revealedBefore, g.revealedCount)
    }

    @Test fun revelertoutesLesCasesSuresGagne() {
        val g = MinesweeperGame(9, 9, 10)
        g.reveal(4, 4)
        for ((r, c) in allCells(g)) if (!g.grid[r][c].isMine) g.reveal(r, c)
        assertEquals(GameState.WON, g.gameState)
    }

    @Test fun drapeauxEtCaseProtegee() {
        val g = MinesweeperGame(9, 9, 10)
        g.toggleFlag(0, 0)
        assertEquals(1, g.flagsPlaced)
        g.reveal(0, 0)
        assertEquals("une case portant un drapeau ne se revele pas", CellState.FLAGGED, g.grid[0][0].state)
        g.toggleFlag(0, 0)
        assertEquals(0, g.flagsPlaced)
        assertEquals(CellState.HIDDEN, g.grid[0][0].state)
    }

    @Test fun accordAvecLesBonsDrapeauxOuvreLesVoisines() {
        val g = MinesweeperGame(9, 9, 10)
        g.reveal(4, 4)
        // Une case revelee qui touche au moins une mine : on pose les drapeaux justes puis on « accorde ».
        val (r, c) = allCells(g).first { (r, c) -> g.grid[r][c].state == CellState.REVEALED && g.grid[r][c].adjacentMines > 0 }
        for (dr in -1..1) for (dc in -1..1) {
            val nr = r + dr; val nc = c + dc
            if ((dr != 0 || dc != 0) && nr in 0 until g.rows && nc in 0 until g.cols && g.grid[nr][nc].isMine) g.toggleFlag(nr, nc)
        }
        g.chordReveal(r, c)
        assertTrue("des drapeaux justes ne font jamais perdre", g.gameState != GameState.LOST)
        for (dr in -1..1) for (dc in -1..1) {
            val nr = r + dr; val nc = c + dc
            if (nr in 0 until g.rows && nc in 0 until g.cols && !g.grid[nr][nc].isMine) assertEquals(CellState.REVEALED, g.grid[nr][nc].state)
        }
    }

    @Test fun sauvegardeEtRelecture() {
        val g = MinesweeperGame(9, 9, 10)
        g.reveal(4, 4)
        val (fr, fc) = allCells(g).first { (r, c) -> g.grid[r][c].state == CellState.HIDDEN }
        g.toggleFlag(fr, fc)
        val back = MinesweeperGame.deserialize(g.serialize())
        assertNotNull(back)
        assertEquals(g.gameState, back!!.gameState)
        assertEquals(g.revealedCount, back.revealedCount)
        assertEquals(g.flagsPlaced, back.flagsPlaced)
        for ((r, c) in allCells(g)) {
            assertEquals(g.grid[r][c].isMine, back.grid[r][c].isMine)
            assertEquals(g.grid[r][c].state, back.grid[r][c].state)
            assertEquals(g.grid[r][c].adjacentMines, back.grid[r][c].adjacentMines)
        }
    }

    /**
     * Les grilles se finissent sans deviner : le solveur prudent doit venir à bout de toutes les
     * grilles faciles, et de la grande majorité des difficiles malgré le budget de temps.
     */
    @Test fun lesGrillesSeFinissentSansDeviner() {
        val random = kotlin.random.Random(42)
        repeat(20) {
            val mines = MinesweeperGame.layoutMines(9, 13, 10, 6 * 9 + 4, random)
            assertEquals(10, mines.count { it })
            assertTrue(MinesweeperSolver.solvable(9, 13, mines, 6 * 9 + 4))
        }
        var solved = 0
        val start = System.nanoTime()
        repeat(10) {
            val mines = MinesweeperGame.layoutMines(16, 23, 80, 11 * 16 + 8, random)
            assertEquals(80, mines.count { it })
            assertFalse("pas de mine sous le premier appui", mines[11 * 16 + 8])
            if (MinesweeperSolver.solvable(16, 23, mines, 11 * 16 + 8)) solved++
        }
        println("Démineur difficile : $solved/10 sans hasard, ${(System.nanoTime() - start) / 10_000_000} ms par grille")
        assertTrue("$solved/10 grilles difficiles résolubles", solved >= 8)
    }
}
