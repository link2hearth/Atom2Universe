package com.Atom2Universe.app.radio.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO pour accéder aux filtres radio en cache
 */
@Dao
interface RadioFilterDao {

    @Query("SELECT * FROM radio_filters WHERE filterType = :type ORDER BY value ASC")
    suspend fun getFiltersByType(type: String): List<RadioFilterEntity>

    @Query("SELECT cachedAt FROM radio_filters WHERE filterType = :type LIMIT 1")
    suspend fun getCacheTimestamp(type: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFilters(filters: List<RadioFilterEntity>)

    @Query("DELETE FROM radio_filters WHERE filterType = :type")
    suspend fun deleteFiltersByType(type: String)

    @Query("DELETE FROM radio_filters")
    suspend fun clearAllFilters()
}
