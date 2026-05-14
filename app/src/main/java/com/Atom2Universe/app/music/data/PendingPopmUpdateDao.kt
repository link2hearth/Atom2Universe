package com.Atom2Universe.app.music.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO pour les opérations sur les modifications POPM en attente.
 */
@Dao
interface PendingPopmUpdateDao {

    /**
     * Insère ou met à jour une modification en attente.
     * Si le fichier existe déjà, on cumule les playCountDelta et on garde le dernier rating.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(update: PendingPopmUpdate)

    /**
     * Récupère une modification en attente par chemin de fichier.
     */
    @Query("SELECT * FROM pending_popm_updates WHERE filePath = :filePath")
    suspend fun getByFilePath(filePath: String): PendingPopmUpdate?

    /**
     * Récupère une modification en attente par ID de track.
     */
    @Query("SELECT * FROM pending_popm_updates WHERE trackId = :trackId")
    suspend fun getByTrackId(trackId: Long): PendingPopmUpdate?

    /**
     * Récupère toutes les modifications en attente.
     */
    @Query("SELECT * FROM pending_popm_updates ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingPopmUpdate>

    /**
     * Récupère toutes les modifications en attente (Flow pour observer les changements).
     */
    @Query("SELECT * FROM pending_popm_updates ORDER BY createdAt ASC")
    fun getAllFlow(): Flow<List<PendingPopmUpdate>>

    /**
     * Récupère les modifications en attente pour un fichier spécifique (exclu).
     * Utile pour traiter les modifications des fichiers qui ne sont pas en cours de lecture.
     */
    @Query("SELECT * FROM pending_popm_updates WHERE filePath != :excludeFilePath ORDER BY createdAt ASC")
    suspend fun getAllExcept(excludeFilePath: String): List<PendingPopmUpdate>

    /**
     * Supprime une modification après qu'elle a été appliquée avec succès.
     */
    @Query("DELETE FROM pending_popm_updates WHERE filePath = :filePath")
    suspend fun deleteByFilePath(filePath: String)

    /**
     * Supprime toutes les modifications.
     */
    @Query("DELETE FROM pending_popm_updates")
    suspend fun deleteAll()

    /**
     * Met à jour les infos de tentative après un échec.
     */
    @Query("UPDATE pending_popm_updates SET lastAttempt = :timestamp, attemptCount = attemptCount + 1 WHERE filePath = :filePath")
    suspend fun updateAttemptInfo(filePath: String, timestamp: Long)

    /**
     * Compte le nombre de modifications en attente.
     */
    @Query("SELECT COUNT(*) FROM pending_popm_updates")
    suspend fun count(): Int

    /**
     * Incrémente le playCountDelta pour un fichier existant.
     */
    @Query("UPDATE pending_popm_updates SET playCountDelta = playCountDelta + :delta WHERE filePath = :filePath")
    suspend fun incrementPlayCountDelta(filePath: String, delta: Int)

    /**
     * Met à jour le rating pour un fichier existant.
     */
    @Query("UPDATE pending_popm_updates SET newRating = :rating WHERE filePath = :filePath")
    suspend fun updateRating(filePath: String, rating: Long)
}
