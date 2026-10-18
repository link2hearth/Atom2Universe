package com.Atom2Universe.app.music.equalizer.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EqPresetDao {

    // ========== Queries ==========

    @Query("SELECT * FROM eq_presets ORDER BY isSystemPreset DESC, name ASC")
    fun getAllPresetsFlow(): Flow<List<EqPreset>>

    @Query("SELECT * FROM eq_presets ORDER BY isSystemPreset DESC, name ASC")
    suspend fun getAllPresets(): List<EqPreset>

    @Query("SELECT * FROM eq_presets WHERE isSystemPreset = 1 ORDER BY id ASC")
    suspend fun getSystemPresets(): List<EqPreset>

    @Query("SELECT * FROM eq_presets WHERE isSystemPreset = 0 ORDER BY name ASC")
    suspend fun getUserPresets(): List<EqPreset>

    @Query("SELECT * FROM eq_presets WHERE id = :id")
    suspend fun getPresetById(id: Long): EqPreset?

    @Query("SELECT * FROM eq_presets WHERE id = :id")
    fun getPresetByIdFlow(id: Long): Flow<EqPreset?>

    @Query("SELECT * FROM eq_presets WHERE name = :name LIMIT 1")
    suspend fun getPresetByName(name: String): EqPreset?

    @Query("SELECT COUNT(*) FROM eq_presets")
    suspend fun getPresetCount(): Int

    @Query("SELECT COUNT(*) FROM eq_presets WHERE isSystemPreset = 0")
    suspend fun getUserPresetCount(): Int

    // ========== Inserts ==========

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: EqPreset): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPresets(presets: List<EqPreset>)

    // ========== Updates ==========

    @Update
    suspend fun updatePreset(preset: EqPreset)

    @Query("UPDATE eq_presets SET name = :name WHERE id = :id AND isSystemPreset = 0")
    suspend fun renamePreset(id: Long, name: String)

    // ========== Deletes ==========

    @Delete
    suspend fun deletePreset(preset: EqPreset)

    @Query("DELETE FROM eq_presets WHERE id = :id AND isSystemPreset = 0")
    suspend fun deletePresetById(id: Long): Int

    @Query("DELETE FROM eq_presets WHERE isSystemPreset = 0")
    suspend fun deleteAllUserPresets()

    // ========== Utility ==========

    @Query("SELECT EXISTS(SELECT 1 FROM eq_presets WHERE name = :name AND id != :excludeId)")
    suspend fun isNameTaken(name: String, excludeId: Long = 0): Boolean
}
