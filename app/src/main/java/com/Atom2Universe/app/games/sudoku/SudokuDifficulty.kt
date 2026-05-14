package com.Atom2Universe.app.games.sudoku

import com.Atom2Universe.app.R

/**
 * Difficulty levels for Sudoku puzzles.
 * Each level defines the range of clues (pre-filled cells) in the puzzle.
 */
enum class SudokuDifficulty(
    val labelResId: Int,
    val minClues: Int,
    val maxClues: Int,
    val showErrorsRealtime: Boolean,
    val showConflictsRealtime: Boolean
) {
    EASY(
        labelResId = R.string.sudoku_difficulty_easy,
        minClues = 32,
        maxClues = 36,
        showErrorsRealtime = true,
        showConflictsRealtime = true
    ),
    MEDIUM(
        labelResId = R.string.sudoku_difficulty_medium,
        minClues = 24,
        maxClues = 28,
        showErrorsRealtime = false,
        showConflictsRealtime = false
    ),
    HARD(
        labelResId = R.string.sudoku_difficulty_hard,
        minClues = 18,
        maxClues = 22,
        showErrorsRealtime = false,
        showConflictsRealtime = false
    );

    fun pickClueCount(): Int {
        if (minClues == maxClues) return minClues
        return minClues + (Math.random() * (maxClues - minClues + 1)).toInt()
    }
}
