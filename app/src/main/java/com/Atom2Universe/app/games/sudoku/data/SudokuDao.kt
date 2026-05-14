package com.Atom2Universe.app.games.sudoku.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO for Sudoku save operations.
 */
@Dao
interface SudokuDao {

    @Query("SELECT * FROM sudoku_saves WHERE id = 1 LIMIT 1")
    suspend fun getSave(): SudokuSaveEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSave(save: SudokuSaveEntity)

    @Query("DELETE FROM sudoku_saves WHERE id = 1")
    suspend fun deleteSave()

    @Query("SELECT EXISTS(SELECT 1 FROM sudoku_saves WHERE id = 1 AND isSolved = 0)")
    suspend fun hasActiveSave(): Boolean
}
