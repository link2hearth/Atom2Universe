package com.Atom2Universe.app.sf2creator.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2PatchEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for managing SF2 patches (modifications to the original SF2 file).
 *
 * Patches store only the changes made by the user, not the full data.
 * This allows for efficient storage and the ability to reconstruct
 * the modified SF2 by applying patches to the original file.
 */
@Dao
interface Sf2PatchDao {

    // ==================== Basic CRUD ====================

    @Query("SELECT * FROM sf2_patches WHERE projectId = :projectId ORDER BY createdAt")
    suspend fun getPatchesForProject(projectId: Long): List<Sf2PatchEntity>

    @Query("SELECT * FROM sf2_patches WHERE projectId = :projectId ORDER BY createdAt")
    fun getPatchesForProjectFlow(projectId: Long): Flow<List<Sf2PatchEntity>>

    @Query("SELECT * FROM sf2_patches WHERE id = :patchId")
    suspend fun getPatchById(patchId: Long): Sf2PatchEntity?

    @Insert
    suspend fun insertPatch(patch: Sf2PatchEntity): Long

    @Insert
    suspend fun insertPatches(patches: List<Sf2PatchEntity>): List<Long>

    @Update
    suspend fun updatePatch(patch: Sf2PatchEntity)

    @Delete
    suspend fun deletePatch(patch: Sf2PatchEntity)

    @Query("DELETE FROM sf2_patches WHERE id = :patchId")
    suspend fun deletePatchById(patchId: Long)

    @Query("DELETE FROM sf2_patches WHERE projectId = :projectId")
    suspend fun deletePatchesForProject(projectId: Long)

    // ==================== Filter by Target ====================

    /**
     * Get all patches for a specific element (preset, instrument, or sample).
     */
    @Query("""
        SELECT * FROM sf2_patches
        WHERE projectId = :projectId
        AND targetType = :targetType
        AND targetIndex = :targetIndex
        ORDER BY createdAt
    """)
    suspend fun getPatchesForTarget(
        projectId: Long,
        targetType: String,
        targetIndex: Int
    ): List<Sf2PatchEntity>

    /**
     * Get all patches of a specific type for a project.
     */
    @Query("""
        SELECT * FROM sf2_patches
        WHERE projectId = :projectId
        AND patchType = :patchType
        ORDER BY createdAt
    """)
    suspend fun getPatchesByType(
        projectId: Long,
        patchType: String
    ): List<Sf2PatchEntity>

    /**
     * Get all patches for presets in a project.
     */
    @Query("""
        SELECT * FROM sf2_patches
        WHERE projectId = :projectId
        AND targetType = 'PRESET'
        ORDER BY targetIndex, createdAt
    """)
    suspend fun getPresetPatches(projectId: Long): List<Sf2PatchEntity>

    /**
     * Get all patches for instruments in a project.
     */
    @Query("""
        SELECT * FROM sf2_patches
        WHERE projectId = :projectId
        AND targetType = 'INSTRUMENT'
        ORDER BY targetIndex, createdAt
    """)
    suspend fun getInstrumentPatches(projectId: Long): List<Sf2PatchEntity>

    /**
     * Get all patches for samples in a project.
     */
    @Query("""
        SELECT * FROM sf2_patches
        WHERE projectId = :projectId
        AND targetType = 'SAMPLE'
        ORDER BY targetIndex, createdAt
    """)
    suspend fun getSamplePatches(projectId: Long): List<Sf2PatchEntity>

    // ==================== Deletion Patches ====================

    /**
     * Get all deletion patches for a project.
     */
    @Query("""
        SELECT * FROM sf2_patches
        WHERE projectId = :projectId
        AND patchType = 'DELETE'
        ORDER BY targetType, targetIndex
    """)
    suspend fun getDeletionPatches(projectId: Long): List<Sf2PatchEntity>

    /**
     * Check if an element has been marked as deleted.
     */
    @Query("""
        SELECT COUNT(*) > 0 FROM sf2_patches
        WHERE projectId = :projectId
        AND targetType = :targetType
        AND targetIndex = :targetIndex
        AND patchType = 'DELETE'
    """)
    suspend fun isElementDeleted(
        projectId: Long,
        targetType: String,
        targetIndex: Int
    ): Boolean

    // ==================== Addition Patches ====================

    /**
     * Get all addition patches for a project.
     */
    @Query("""
        SELECT * FROM sf2_patches
        WHERE projectId = :projectId
        AND patchType = 'ADD'
        ORDER BY targetType, createdAt
    """)
    suspend fun getAdditionPatches(projectId: Long): List<Sf2PatchEntity>

    /**
     * Get all added samples for a project.
     */
    @Query("""
        SELECT * FROM sf2_patches
        WHERE projectId = :projectId
        AND targetType = 'SAMPLE'
        AND patchType = 'ADD'
        ORDER BY createdAt
    """)
    suspend fun getAddedSamples(projectId: Long): List<Sf2PatchEntity>

    // ==================== Counts and Statistics ====================

    @Query("SELECT COUNT(*) FROM sf2_patches WHERE projectId = :projectId")
    suspend fun getPatchCountForProject(projectId: Long): Int

    @Query("""
        SELECT COUNT(*) FROM sf2_patches
        WHERE projectId = :projectId
        AND patchType = :patchType
    """)
    suspend fun getPatchCountByType(projectId: Long, patchType: String): Int

    /**
     * Check if a project has any patches (has been modified).
     */
    @Query("SELECT COUNT(*) > 0 FROM sf2_patches WHERE projectId = :projectId")
    suspend fun hasPatches(projectId: Long): Boolean

    /**
     * Check if a project has any structural changes (additions or deletions).
     */
    @Query("""
        SELECT COUNT(*) > 0 FROM sf2_patches
        WHERE projectId = :projectId
        AND (patchType = 'ADD' OR patchType = 'DELETE')
    """)
    suspend fun hasStructuralChanges(projectId: Long): Boolean

    // ==================== Cleanup ====================

    /**
     * Delete all patches for a specific element.
     * Useful when an element is deleted or when reverting changes.
     */
    @Query("""
        DELETE FROM sf2_patches
        WHERE projectId = :projectId
        AND targetType = :targetType
        AND targetIndex = :targetIndex
    """)
    suspend fun deletePatchesForTarget(
        projectId: Long,
        targetType: String,
        targetIndex: Int
    )

    /**
     * Delete the most recent patch for a target (undo last change).
     */
    @Query("""
        DELETE FROM sf2_patches
        WHERE id = (
            SELECT id FROM sf2_patches
            WHERE projectId = :projectId
            AND targetType = :targetType
            AND targetIndex = :targetIndex
            ORDER BY createdAt DESC
            LIMIT 1
        )
    """)
    suspend fun deleteLastPatchForTarget(
        projectId: Long,
        targetType: String,
        targetIndex: Int
    )
}
