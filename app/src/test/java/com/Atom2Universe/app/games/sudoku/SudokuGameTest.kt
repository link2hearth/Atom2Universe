package com.Atom2Universe.app.games.sudoku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Garde-fou du Sudoku : une grille generee doit avoir une solution valide ET unique, sinon
 * le joueur peut remplir correctement une case que l'app juge fausse. La detection des
 * conflits alimente l'affichage en rouge en temps reel.
 */
class SudokuGameTest {

    private fun assertValidSolution(solution: Array<IntArray>) {
        val full = (1..9).toSet()
        for (i in 0 until 9) {
            assertEquals("ligne $i", full, solution[i].toSet())
            assertEquals("colonne $i", full, (0 until 9).map { solution[it][i] }.toSet())
            val r = i / 3 * 3
            val c = i % 3 * 3
            assertEquals("bloc $i", full, (0 until 9).map { solution[r + it / 3][c + it % 3] }.toSet())
        }
    }

    /** Compte les solutions (plafonne a 2) avec un solveur independant de celui de l'app. */
    private fun countSolutions(grid: Array<IntArray>): Int {
        fun candidates(r: Int, c: Int): List<Int> = (1..9).filter { d ->
            (0 until 9).none { grid[r][it] == d || grid[it][c] == d } &&
                (0 until 9).none { grid[r / 3 * 3 + it / 3][c / 3 * 3 + it % 3] == d }
        }
        fun solve(): Int {
            var bestR = -1
            var bestC = -1
            var best: List<Int>? = null
            for (r in 0 until 9) for (c in 0 until 9) if (grid[r][c] == 0) {
                val cand = candidates(r, c)
                if (best == null || cand.size < best.size) { bestR = r; bestC = c; best = cand }
            }
            if (best == null) return 1
            var found = 0
            for (d in best) {
                grid[bestR][bestC] = d
                found += solve()
                grid[bestR][bestC] = 0
                if (found >= 2) break
            }
            return found
        }
        return solve()
    }

    @Test fun chaqueDifficulteDonneUneGrilleValideEtUnique() {
        for (difficulty in SudokuDifficulty.values()) {
            repeat(3) {
                val board = SudokuGame.generatePuzzle(difficulty)
                assertValidSolution(board.solution)

                var clues = 0
                for (r in 0 until 9) for (c in 0 until 9) {
                    assertEquals("un indice doit etre fixe", board.getValue(r, c) != 0, board.isFixed(r, c))
                    if (board.isFixed(r, c)) {
                        clues++
                        assertEquals("indice faux en ($r,$c)", board.getSolutionValue(r, c), board.getValue(r, c))
                    }
                }
                assertTrue("$difficulty : $clues indices", clues >= difficulty.minClues)
                val grid = Array(9) { board.cells[it].copyOf() }
                assertEquals("$difficulty doit avoir une seule solution", 1, countSolutions(grid))
            }
        }
    }

    @Test fun uneGrilleResolueEstReconnue() {
        val board = SudokuGame.generatePuzzle(SudokuDifficulty.EASY)
        assertFalse(board.isSolved())
        for (r in 0 until 9) for (c in 0 until 9) board.setValue(r, c, board.getSolutionValue(r, c))
        assertTrue(board.isComplete())
        assertTrue(board.isSolved())
        assertTrue(SudokuGame.findConflicts(board).isEmpty())
        assertTrue(SudokuGame.findMistakes(board).isEmpty())
    }

    @Test fun uneErreurEstSignalee() {
        val board = SudokuGame.generatePuzzle(SudokuDifficulty.HARD)
        val (r, c) = (0 until 81).map { it / 9 to it % 9 }.first { (r, c) -> !board.isFixed(r, c) }
        val wrong = (1..9).first { it != board.getSolutionValue(r, c) }
        board.setValue(r, c, wrong)
        assertTrue(SudokuGame.findMistakes(board).contains(CellPosition(r, c)))
    }

    @Test fun conflitsDeLigneDeColonneEtDeBloc() {
        val row = SudokuBoard.empty()
        row.setValue(4, 1, 7); row.setValue(4, 8, 7)
        assertEquals(setOf(CellPosition(4, 1), CellPosition(4, 8)), SudokuGame.findConflicts(row))

        val col = SudokuBoard.empty()
        col.setValue(0, 2, 3); col.setValue(7, 2, 3)
        assertEquals(setOf(CellPosition(0, 2), CellPosition(7, 2)), SudokuGame.findConflicts(col))

        val box = SudokuBoard.empty()
        box.setValue(3, 3, 5); box.setValue(5, 5, 5)
        assertEquals(setOf(CellPosition(3, 3), CellPosition(5, 5)), SudokuGame.findConflicts(box))

        assertTrue(SudokuGame.findConflicts(SudokuBoard.empty()).isEmpty())
    }

    @Test fun casesLieesDUnePosition() {
        val related = SudokuGame.getRelatedCells(CellPosition(4, 4))
        assertEquals(20, related.size)                  // 8 en ligne + 8 en colonne + 4 restantes du bloc
        assertFalse(related.contains(CellPosition(4, 4)))
        assertTrue(related.contains(CellPosition(3, 3)))
        assertTrue(related.contains(CellPosition(4, 0)))
        assertTrue(related.contains(CellPosition(0, 4)))
    }

    /**
     * La difficulté se lit au raisonnement : une grille facile se finit aux seuls singletons, une
     * moyenne ne se finit pas sans eux mais avec les paires et pointages, une difficile demande
     * davantage. Une grille d'exemple connue (« Easy » des recueils) sert de témoin.
     */
    @Test fun leCorrecteurClasseLesGrillesParTechnique() {
        val easy = ("530070000600195000098000060800060003400803001700020006060000280000419005000080079")
            .map { it - '0' }.toIntArray()
        assertEquals(1, SudokuGrader.grade(easy))
        for (difficulty in SudokuDifficulty.values()) {
            val board = SudokuGame.generatePuzzle(difficulty)
            val flat = IntArray(81) { board.cells[it / 9][it % 9] }
            val level = SudokuGrader.grade(flat)
            assertTrue("$difficulty : niveau $level", level in 1..difficulty.level)
            assertEquals("$difficulty : les techniques du niveau doivent suffire",
                level, SudokuGrader.grade(flat, difficulty.level))
        }
        val empty = IntArray(81)
        assertEquals("une grille vide n'a pas de solution unique", -1, SudokuGrader.grade(empty))
    }
}
