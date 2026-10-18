package com.Atom2Universe.app.midi.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO (Data Access Object) pour les paramètres de l'application
 * Système key-value pour stocker : SoundFont path, dossier MIDI URI, préférences
 */
@Dao
interface SettingsDao {

    /**
     * Récupère un paramètre par sa clé
     */
    @Query("SELECT value FROM app_settings WHERE key = :key")
    suspend fun getSetting(key: String): String?

    /**
     * Récupère un paramètre par sa clé (Flow pour observer les changements)
     */
    @Query("SELECT value FROM app_settings WHERE key = :key")
    fun getSettingFlow(key: String): Flow<String?>

    /**
     * Récupère tous les paramètres
     */
    @Query("SELECT * FROM app_settings")
    fun getAllSettings(): Flow<List<AppSettings>>

    /**
     * Sauvegarde un paramètre (ou le met à jour si existe déjà)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSetting(setting: AppSettings)

    /**
     * Sauvegarde plusieurs paramètres d'un coup
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: List<AppSettings>)

    /**
     * Supprime un paramètre par sa clé
     */
    @Query("DELETE FROM app_settings WHERE key = :key")
    suspend fun deleteSetting(key: String)

    /**
     * Supprime tous les paramètres
     */
    @Query("DELETE FROM app_settings")
    suspend fun deleteAllSettings()

    /**
     * Vérifie si un paramètre existe
     */
    @Query("SELECT EXISTS(SELECT 1 FROM app_settings WHERE key = :key)")
    suspend fun settingExists(key: String): Boolean

    /**
     * Compte le nombre total de paramètres
     */
    @Query("SELECT COUNT(*) FROM app_settings")
    suspend fun getSettingsCount(): Int
}
