package com.Atom2Universe.app.readingprogress.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ReadingProgressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReadingProgressEntity)

    @Query("SELECT * FROM reading_progress WHERE bookKey = :bookKey")
    suspend fun getByKey(bookKey: String): ReadingProgressEntity?

    @Query("SELECT * FROM reading_progress")
    suspend fun getAll(): List<ReadingProgressEntity>
}
