package com.Atom2Universe.app.games.sudoku

import com.Atom2Universe.app.R

/**
 * Difficulty levels for Sudoku puzzles.
 *
 * La difficulté se mesure au raisonnement demandé ([SudokuGrader]) : [level] est le niveau de
 * techniques qu'il faut pour finir la grille. [minClues] n'est qu'un plancher — on retire des
 * indices tant que la grille reste faisable à ce niveau, sans descendre en dessous.
 */
enum class SudokuDifficulty(
    val labelResId: Int,
    val level: Int,
    val minClues: Int,
    val showErrorsRealtime: Boolean,
    val showConflictsRealtime: Boolean
) {
    EASY(
        labelResId = R.string.sudoku_difficulty_easy,
        level = 1,
        minClues = 32,
        showErrorsRealtime = true,
        showConflictsRealtime = true
    ),
    MEDIUM(
        labelResId = R.string.sudoku_difficulty_medium,
        level = 2,
        minClues = 24,
        showErrorsRealtime = false,
        showConflictsRealtime = false
    ),
    HARD(
        labelResId = R.string.sudoku_difficulty_hard,
        level = 3,
        minClues = 20,
        showErrorsRealtime = false,
        showConflictsRealtime = false
    );
}
