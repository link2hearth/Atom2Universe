package com.Atom2Universe.app.games.sudoku.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.Atom2Universe.app.games.sudoku.SudokuBoard
import com.Atom2Universe.app.games.sudoku.SudokuDifficulty

/**
 * Room entity for persisting Sudoku game state.
 */
@Entity(tableName = "sudoku_saves")
data class SudokuSaveEntity(
    @PrimaryKey
    val id: Int = 1, // Single save slot
    val difficulty: String,
    val cellsJson: String,
    val solutionJson: String,
    val fixedJson: String,
    val notesJson: String,
    val elapsedTimeMs: Long,
    val isSolved: Boolean,
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun fromBoard(
            board: SudokuBoard,
            difficulty: SudokuDifficulty,
            elapsedTimeMs: Long,
            isSolved: Boolean,
            notes: Array<IntArray> = Array(9) { IntArray(9) }
        ): SudokuSaveEntity {
            return SudokuSaveEntity(
                difficulty = difficulty.name,
                cellsJson = boardToJson(board.cells),
                solutionJson = boardToJson(board.solution),
                fixedJson = fixedToJson(board.fixed),
                notesJson = boardToJson(notes),
                elapsedTimeMs = elapsedTimeMs,
                isSolved = isSolved
            )
        }

        private fun boardToJson(board: Array<IntArray>): String {
            return board.joinToString(";") { row ->
                row.joinToString(",")
            }
        }

        private fun fixedToJson(fixed: Array<BooleanArray>): String {
            return fixed.joinToString(";") { row ->
                row.joinToString(",") { if (it) "1" else "0" }
            }
        }
    }

    fun toBoard(): SudokuBoard {
        return SudokuBoard(
            cells = jsonToBoard(cellsJson),
            solution = jsonToBoard(solutionJson),
            fixed = jsonToFixed(fixedJson)
        )
    }

    fun toNotes(): Array<IntArray> {
        return jsonToBoard(notesJson)
    }

    fun toDifficulty(): SudokuDifficulty {
        return try {
            SudokuDifficulty.valueOf(difficulty)
        } catch (e: Exception) {
            SudokuDifficulty.MEDIUM
        }
    }

    private fun jsonToBoard(json: String): Array<IntArray> {
        return try {
            json.split(";").map { row ->
                row.split(",").map { it.trim().toIntOrNull() ?: 0 }.toIntArray()
            }.toTypedArray()
        } catch (e: Exception) {
            Array(9) { IntArray(9) { 0 } }
        }
    }

    private fun jsonToFixed(json: String): Array<BooleanArray> {
        return try {
            json.split(";").map { row ->
                row.split(",").map { it.trim() == "1" }.toBooleanArray()
            }.toTypedArray()
        } catch (e: Exception) {
            Array(9) { BooleanArray(9) { false } }
        }
    }
}
