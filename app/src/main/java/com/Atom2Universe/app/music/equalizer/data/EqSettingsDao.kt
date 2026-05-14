package com.Atom2Universe.app.music.equalizer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EqSettingsDao {

    @Query("SELECT * FROM eq_settings WHERE id = 1")
    suspend fun getSettings(): EqSettings?

    @Query("SELECT * FROM eq_settings WHERE id = 1")
    fun getSettingsFlow(): Flow<EqSettings?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: EqSettings)

    @Query("UPDATE eq_settings SET isEnabled = :enabled WHERE id = 1")
    suspend fun setEnabled(enabled: Boolean)

    @Query("UPDATE eq_settings SET globalPresetId = :presetId WHERE id = 1")
    suspend fun setGlobalPresetId(presetId: Long)

    @Query("SELECT globalPresetId FROM eq_settings WHERE id = 1")
    suspend fun getGlobalPresetId(): Long?

    @Query("SELECT isEnabled FROM eq_settings WHERE id = 1")
    suspend fun isEnabled(): Boolean?
}
