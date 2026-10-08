package com.Atom2Universe.app.sf2creator.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2IndexEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for managing the SF2 index (lightweight navigation entries).
 *
 * The index provides fast navigation without parsing the full SF2 file.
 * It stores only display information (names, program numbers, etc.)
 * and is created at import time by scanning the SF2 headers.
 *
 * Full details are read from the SF2 file on demand (lazy loading).
 */
@Dao
interface Sf2IndexDao {

    // ==================== Basic CRUD ====================

    @Query("SELECT * FROM sf2_index WHERE projectId = :projectId ORDER BY elementType, originalIndex")
    suspend fun getIndexForProject(projectId: Long): List<Sf2IndexEntity>

    @Query("SELECT * FROM sf2_index WHERE projectId = :projectId ORDER BY elementType, originalIndex")
    fun getIndexForProjectFlow(projectId: Long): Flow<List<Sf2IndexEntity>>

    @Query("SELECT * FROM sf2_index WHERE id = :indexId")
    suspend fun getIndexEntryById(indexId: Long): Sf2IndexEntity?

    @Insert
    suspend fun insertIndexEntry(entry: Sf2IndexEntity): Long

    @Insert
    suspend fun insertIndexEntries(entries: List<Sf2IndexEntity>): List<Long>

    @Update
    suspend fun updateIndexEntry(entry: Sf2IndexEntity)

    @Delete
    suspend fun deleteIndexEntry(entry: Sf2IndexEntity)

    @Query("DELETE FROM sf2_index WHERE id = :indexId")
    suspend fun deleteIndexEntryById(indexId: Long)

    @Query("DELETE FROM sf2_index WHERE projectId = :projectId")
    suspend fun deleteIndexForProject(projectId: Long)

    // ==================== Filter by Type ====================

    /**
     * Get all presets in the index for a project.
     */
    @Query("""
        SELECT * FROM sf2_index
        WHERE projectId = :projectId
        AND elementType = 'PRESET'
        AND isDeleted = 0
        ORDER BY bankNumber, programNumber
    """)
    suspend fun getPresetIndex(projectId: Long): List<Sf2IndexEntity>

    @Query("""
        SELECT * FROM sf2_index
        WHERE projectId = :projectId
        AND elementType = 'PRESET'
        AND isDeleted = 0
        ORDER BY bankNumber, programNumber
    """)
    fun getPresetIndexFlow(projectId: Long): Flow<List<Sf2IndexEntity>>

    /**
     * Get all instruments in the index for a project.
     */
    @Query("""
        SELECT * FROM sf2_index
        WHERE projectId = :projectId
        AND elementType = 'INSTRUMENT'
        AND isDeleted = 0
        ORDER BY name
    """)
    suspend fun getInstrumentIndex(projectId: Long): List<Sf2IndexEntity>

    @Query("""
        SELECT * FROM sf2_index
        WHERE projectId = :projectId
        AND elementType = 'INSTRUMENT'
        AND isDeleted = 0
        ORDER BY name
    """)
    fun getInstrumentIndexFlow(projectId: Long): Flow<List<Sf2IndexEntity>>

    /**
     * Get all samples in the index for a project.
     */
    @Query("""
        SELECT * FROM sf2_index
        WHERE projectId = :projectId
        AND elementType = 'SAMPLE'
        AND isDeleted = 0
        ORDER BY rootNote, name
    """)
    suspend fun getSampleIndex(projectId: Long): List<Sf2IndexEntity>

    @Query("""
        SELECT * FROM sf2_index
        WHERE projectId = :projectId
        AND elementType = 'SAMPLE'
        AND isDeleted = 0
        ORDER BY rootNote, name
    """)
    fun getSampleIndexFlow(projectId: Long): Flow<List<Sf2IndexEntity>>

    // ==================== Lookup by Original Index ====================

    /**
     * Find an index entry by its original SF2 index.
     */
    @Query("""
        SELECT * FROM sf2_index
        WHERE projectId = :projectId
        AND elementType = :elementType
        AND originalIndex = :originalIndex
        LIMIT 1
    """)
    suspend fun findByOriginalIndex(
        projectId: Long,
        elementType: String,
        originalIndex: Int
    ): Sf2IndexEntity?

    /**
     * Find a preset by its bank and program number.
     */
    @Query("""
        SELECT * FROM sf2_index
        WHERE projectId = :projectId
        AND elementType = 'PRESET'
        AND bankNumber = :bankNumber
        AND programNumber = :programNumber
        AND isDeleted = 0
        LIMIT 1
    """)
    suspend fun findPresetByBankProgram(
        projectId: Long,
        bankNumber: Int,
        programNumber: Int
    ): Sf2IndexEntity?

    // ==================== Deletion Status ====================

    /**
     * Mark an element as deleted (soft delete).
     */
    @Query("""
        UPDATE sf2_index
        SET isDeleted = 1
        WHERE projectId = :projectId
        AND elementType = :elementType
        AND originalIndex = :originalIndex
    """)
    suspend fun markAsDeleted(
        projectId: Long,
        elementType: String,
        originalIndex: Int
    )

    /**
     * Restore a deleted element.
     */
    @Query("""
        UPDATE sf2_index
        SET isDeleted = 0
        WHERE projectId = :projectId
        AND elementType = :elementType
        AND originalIndex = :originalIndex
    """)
    suspend fun restoreDeleted(
        projectId: Long,
        elementType: String,
        originalIndex: Int
    )

    /**
     * Get all deleted elements for a project.
     */
    @Query("""
        SELECT * FROM sf2_index
        WHERE projectId = :projectId
        AND isDeleted = 1
        ORDER BY elementType, originalIndex
    """)
    suspend fun getDeletedElements(projectId: Long): List<Sf2IndexEntity>

    // ==================== Added Elements ====================

    /**
     * Get all elements that were added (not in original SF2).
     */
    @Query("""
        SELECT * FROM sf2_index
        WHERE projectId = :projectId
        AND isAdded = 1
        AND isDeleted = 0
        ORDER BY elementType, name
    """)
    suspend fun getAddedElements(projectId: Long): List<Sf2IndexEntity>

    /**
     * Get added samples for a project.
     */
    @Query("""
        SELECT * FROM sf2_index
        WHERE projectId = :projectId
        AND elementType = 'SAMPLE'
        AND isAdded = 1
        AND isDeleted = 0
        ORDER BY rootNote, name
    """)
    suspend fun getAddedSamples(projectId: Long): List<Sf2IndexEntity>

    // ==================== Counts ====================

    @Query("SELECT COUNT(*) FROM sf2_index WHERE projectId = :projectId AND isDeleted = 0")
    suspend fun getIndexCountForProject(projectId: Long): Int

    @Query("""
        SELECT COUNT(*) FROM sf2_index
        WHERE projectId = :projectId
        AND elementType = :elementType
        AND isDeleted = 0
    """)
    suspend fun getCountByType(projectId: Long, elementType: String): Int

    @Query("""
        SELECT COUNT(*) FROM sf2_index
        WHERE projectId = :projectId
        AND elementType = 'PRESET'
        AND isDeleted = 0
    """)
    suspend fun getPresetCount(projectId: Long): Int

    @Query("""
        SELECT COUNT(*) FROM sf2_index
        WHERE projectId = :projectId
        AND elementType = 'INSTRUMENT'
        AND isDeleted = 0
    """)
    suspend fun getInstrumentCount(projectId: Long): Int

    @Query("""
        SELECT COUNT(*) FROM sf2_index
        WHERE projectId = :projectId
        AND elementType = 'SAMPLE'
        AND isDeleted = 0
    """)
    suspend fun getSampleCount(projectId: Long): Int

    // ==================== Name Updates ====================

    /**
     * Update the name of an index entry.
     */
    @Query("UPDATE sf2_index SET name = :newName WHERE id = :indexId")
    suspend fun updateName(indexId: Long, newName: String)

    /**
     * Update the name by original index.
     */
    @Query("""
        UPDATE sf2_index
        SET name = :newName
        WHERE projectId = :projectId
        AND elementType = :elementType
        AND originalIndex = :originalIndex
    """)
    suspend fun updateNameByOriginalIndex(
        projectId: Long,
        elementType: String,
        originalIndex: Int,
        newName: String
    )
}
