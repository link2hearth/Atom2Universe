package com.Atom2Universe.app.games.particules.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ParticulesMetaDao {
    @Query("SELECT * FROM particules_meta WHERE id = 1 LIMIT 1")
    suspend fun getMeta(): ParticulesMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meta: ParticulesMetaEntity)
}
