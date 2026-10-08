package com.Atom2Universe.app.music.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * DAO pour la gestion du cache des pistes musicales.
 */
@Dao
interface CachedTrackDao {

    /**
     * Récupère toutes les pistes en cache, triées par titre.
     */
    @Query("SELECT * FROM cached_tracks ORDER BY title COLLATE NOCASE ASC")
    suspend fun getAllTracks(): List<CachedTrackEntity>

    /**
     * Récupère le nombre de pistes en cache.
     */
    @Query("SELECT COUNT(*) FROM cached_tracks")
    suspend fun getTrackCount(): Int

    /**
     * Vérifie si le cache contient des pistes.
     */
    @Query("SELECT COUNT(*) > 0 FROM cached_tracks")
    suspend fun hasCache(): Boolean

    /**
     * Récupère une piste par son ID.
     */
    @Query("SELECT * FROM cached_tracks WHERE id = :trackId")
    suspend fun getTrackById(trackId: Long): CachedTrackEntity?

    /**
     * Récupère une piste par son chemin de fichier.
     */
    @Query("SELECT * FROM cached_tracks WHERE filePath = :filePath")
    suspend fun getTrackByFilePath(filePath: String): CachedTrackEntity?

    /**
     * Insère ou met à jour une piste.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: CachedTrackEntity)

    /**
     * Insère ou met à jour plusieurs pistes.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<CachedTrackEntity>)

    /**
     * Supprime toutes les pistes du cache.
     */
    @Query("DELETE FROM cached_tracks")
    suspend fun clearCache()

    /**
     * Supprime une piste par son ID.
     */
    @Query("DELETE FROM cached_tracks WHERE id = :trackId")
    suspend fun deleteTrackById(trackId: Long)

    /**
     * Supprime une piste par son chemin de fichier.
     */
    @Query("DELETE FROM cached_tracks WHERE filePath = :filePath")
    suspend fun deleteTrackByFilePath(filePath: String)

    /**
     * Remplace tout le cache par une nouvelle liste de pistes.
     * Opération atomique via transaction.
     */
    @Transaction
    suspend fun replaceAllTracks(tracks: List<CachedTrackEntity>) {
        clearCache()
        insertTracks(tracks)
    }

    /**
     * Récupère tous les IDs des pistes en cache.
     * Utile pour comparer avec MediaStore et détecter les changements.
     */
    @Query("SELECT id FROM cached_tracks")
    suspend fun getAllTrackIds(): List<Long>

    /**
     * Récupère le timestamp de la dernière modification du cache.
     * Basé sur le dernier ID inséré (approximation).
     */
    @Query("SELECT MAX(id) FROM cached_tracks")
    suspend fun getMaxTrackId(): Long?
}
