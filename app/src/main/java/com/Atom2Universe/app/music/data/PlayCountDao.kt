package com.Atom2Universe.app.music.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * DAO pour accéder aux compteurs d'écoutes dans la base Room.
 */
@Dao
interface PlayCountDao {

    /**
     * Récupère une entrée par sa clé métadonnées
     */
    @Query("SELECT * FROM play_counts WHERE metadataKey = :key LIMIT 1")
    suspend fun getByKey(key: String): PlayCountEntry?

    /**
     * Récupère le play count d'un morceau par sa clé métadonnées
     * Retourne 0 si non trouvé
     */
    @Query("SELECT playCount FROM play_counts WHERE metadataKey = :key LIMIT 1")
    suspend fun getPlayCount(key: String): Long?

    /**
     * Récupère toutes les entrées avec au moins une écoute
     * Trié par nombre d'écoutes décroissant
     */
    @Query("SELECT * FROM play_counts WHERE playCount > 0 ORDER BY playCount DESC")
    suspend fun getAllWithPlayCount(): List<PlayCountEntry>

    /**
     * Récupère les N morceaux les plus écoutés
     */
    @Query("SELECT * FROM play_counts WHERE playCount > 0 ORDER BY playCount DESC LIMIT :limit")
    suspend fun getTopPlayed(limit: Int): List<PlayCountEntry>

    /**
     * Récupère toutes les entrées (pour export/debug)
     */
    @Query("SELECT * FROM play_counts ORDER BY playCount DESC")
    suspend fun getAll(): List<PlayCountEntry>

    /**
     * Compte le nombre total d'entrées
     */
    @Query("SELECT COUNT(*) FROM play_counts")
    suspend fun count(): Int

    /**
     * Compte le nombre d'entrées avec au moins une écoute (total)
     */
    @Query("SELECT COUNT(*) FROM play_counts WHERE playCount > 0")
    suspend fun countWithPlayCount(): Int

    /**
     * Compte le nombre d'entrées avec au moins une écoute LOCALE (earnedPlayCount > 0).
     * Utilisé pour savoir si on doit créer des baseline deltas pour la sync.
     */
    @Query("SELECT COUNT(*) FROM play_counts WHERE earnedPlayCount > 0")
    suspend fun countWithEarnedPlayCount(): Int

    /**
     * Insère une nouvelle entrée (ignore si existe déjà)
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: PlayCountEntry): Long

    /**
     * Met à jour une entrée existante
     */
    @Update
    suspend fun update(entry: PlayCountEntry)

    /**
     * Insère ou met à jour une entrée
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: PlayCountEntry)

    /**
     * Incrémente le play count d'une entrée existante (écoute sur cet appareil).
     * Incrémente AUSSI earnedPlayCount car c'est une vraie écoute locale.
     * IMPORTANT: Utilise MAX pour ne jamais décrémenter
     */
    @Query("""
        UPDATE play_counts
        SET playCount = MAX(playCount, playCount + 1),
            earnedPlayCount = earnedPlayCount + 1,
            lastPlayed = :timestamp,
            updatedAt = :timestamp
        WHERE metadataKey = :key
    """)
    suspend fun incrementPlayCount(key: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Met à jour le play count en prenant le MAX avec la valeur actuelle.
     * Utilisé pour synchroniser avec les POPM sans risque de décrémentation.
     * NOTE: N'incrémente PAS earnedPlayCount car ce sont des écoutes importées (pas locales).
     */
    @Query("""
        UPDATE play_counts
        SET playCount = MAX(playCount, :newPlayCount),
            updatedAt = :timestamp
        WHERE metadataKey = :key
    """)
    suspend fun updatePlayCountMax(key: String, newPlayCount: Long, timestamp: Long = System.currentTimeMillis())

    /**
     * Récupère toutes les entrées avec au moins une écoute LOCALE (earnedPlayCount > 0).
     * Utilisé pour créer les baseline deltas lors de l'activation de la sync.
     * Exclut les imports POPM pour éviter le doublement lors de la copie de MP3.
     */
    @Query("SELECT * FROM play_counts WHERE earnedPlayCount > 0 ORDER BY earnedPlayCount DESC")
    suspend fun getAllWithEarnedPlayCount(): List<PlayCountEntry>

    /**
     * Supprime une entrée par sa clé
     */
    @Query("DELETE FROM play_counts WHERE metadataKey = :key")
    suspend fun deleteByKey(key: String)

    /**
     * Supprime toutes les entrées (reset complet)
     */
    @Query("DELETE FROM play_counts")
    suspend fun deleteAll()

    /**
     * Vérifie si une entrée existe
     */
    @Query("SELECT EXISTS(SELECT 1 FROM play_counts WHERE metadataKey = :key)")
    suspend fun exists(key: String): Boolean

    /**
     * Reset tous les playCount à earnedPlayCount.
     * Utilisé pour corriger les compteurs après un bug de multiplication.
     * earnedPlayCount contient les vraies écoutes locales non affectées par le bug.
     */
    @Query("""
        UPDATE play_counts
        SET playCount = earnedPlayCount,
            updatedAt = :timestamp
        WHERE playCount > earnedPlayCount
    """)
    suspend fun resetPlayCountsToEarned(timestamp: Long = System.currentTimeMillis())
}
