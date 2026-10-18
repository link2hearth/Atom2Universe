package com.Atom2Universe.app.games.solitaire.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SolitaireDao {

    @Query("SELECT * FROM solitaire_saves WHERE id = 1 LIMIT 1")
    suspend fun getSave(): SolitaireSaveEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSave(save: SolitaireSaveEntity)

    @Query("DELETE FROM solitaire_saves WHERE id = 1")
    suspend fun deleteSave()

    @Query("SELECT EXISTS(SELECT 1 FROM solitaire_saves WHERE id = 1 AND isWon = 0)")
    suspend fun hasActiveSave(): Boolean
}
