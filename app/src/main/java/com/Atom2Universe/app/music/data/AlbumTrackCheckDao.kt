package com.Atom2Universe.app.music.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO pour les albums déjà traités par la proposition de numéros de piste.
 */
@Dao
interface AlbumTrackCheckDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: AlbumTrackCheckEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<AlbumTrackCheckEntity>)

    @Query("SELECT albumKey FROM album_track_number_checks")
    suspend fun getAllKeys(): List<String>

    @Query("DELETE FROM album_track_number_checks")
    suspend fun clear()
}
