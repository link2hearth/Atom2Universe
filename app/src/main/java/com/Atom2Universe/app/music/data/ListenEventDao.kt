package com.Atom2Universe.app.music.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ListenEventDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: ListenEvent)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(events: List<ListenEvent>)

    /** Play count d'un track = nombre d'événements dans le journal. */
    @Query("SELECT COUNT(*) FROM listen_events WHERE trackKey = :trackKey")
    suspend fun getPlayCount(trackKey: String): Long

    /** Historique récent, toutes pistes confondues. */
    @Query("SELECT * FROM listen_events WHERE isMigrated = 0 ORDER BY listenedAt DESC LIMIT :limit")
    suspend fun getRecentHistory(limit: Int = 200): List<ListenEvent>

    /** Tous les événements d'un track, du plus récent au plus ancien. */
    @Query("SELECT * FROM listen_events WHERE trackKey = :trackKey ORDER BY listenedAt DESC")
    suspend fun getEventsForTrack(trackKey: String): List<ListenEvent>

    /** Événements produits par cet appareil, depuis un timestamp (pour la sync). */
    @Query("SELECT * FROM listen_events WHERE deviceId = :deviceId AND listenedAt > :sinceTimestamp ORDER BY listenedAt ASC")
    suspend fun getLocalEventsSince(deviceId: String, sinceTimestamp: Long): List<ListenEvent>

    /**
     * Dernière écoute produite par un appareil donné.
     * Sert de curseur "depuis quand" lors d'une sync, sans charger les events en mémoire.
     */
    @Query("SELECT MAX(listenedAt) FROM listen_events WHERE deviceId = :deviceId")
    suspend fun getLatestTimestampForDevice(deviceId: String): Long?

    /**
     * Nombre d'écoutes détenues pour un couple (appareil, morceau).
     *
     * Sert à réconcilier le résumé agrégé qu'un appareil envoie pour ses écoutes
     * anciennes : on ne complète que ce qui manque, donc ré-appliquer le même
     * résumé n'ajoute rien.
     */
    @Query("SELECT COUNT(*) FROM listen_events WHERE deviceId = :deviceId AND trackKey = :trackKey")
    suspend fun countForDeviceAndTrack(deviceId: String, trackKey: String): Long

    /**
     * Résumé par morceau des écoutes de cet appareil ANTÉRIEURES à :cutoff.
     *
     * C'est ce qu'on envoie à la place des écoutes une par une : quelques dizaines
     * d'octets par morceau au lieu de ~90 par écoute.
     */
    @Query("""
        SELECT trackKey,
               COUNT(*)        AS eventCount,
               MIN(listenedAt) AS firstAt,
               MAX(listenedAt) AS lastAt,
               MAX(title)      AS title,
               MAX(artist)     AS artist,
               MAX(album)      AS album
        FROM listen_events
        WHERE deviceId = :deviceId AND listenedAt <= :cutoff
        GROUP BY trackKey
    """)
    suspend fun getArchiveSummary(deviceId: String, cutoff: Long): List<ListenArchiveRow>

    /** Tous les appareils ayant produit des écoutes présentes dans ce journal. */
    @Query("SELECT DISTINCT deviceId FROM listen_events")
    suspend fun getKnownDeviceIds(): List<String>

    /** Toutes les écoutes d'un appareil, sans borne de date (sauvegarde). */
    @Query("SELECT * FROM listen_events WHERE deviceId = :deviceId ORDER BY listenedAt ASC")
    suspend fun getAllForDevice(deviceId: String): List<ListenEvent>

    /** Filtre les UUID déjà présents (déduplication à la réception d'une sync). */
    @Query("SELECT uuid FROM listen_events WHERE uuid IN (:uuids)")
    suspend fun getExistingUuids(uuids: List<String>): List<String>

    @Query("SELECT COUNT(*) FROM listen_events")
    suspend fun count(): Long

    @Query("SELECT COUNT(*) FROM listen_events WHERE isMigrated = 0")
    suspend fun countRealEvents(): Long

    /** Dernier listenedAt connu, pour offrir un point de départ aux autres nœuds. */
    @Query("SELECT MAX(listenedAt) FROM listen_events")
    suspend fun getLatestTimestamp(): Long?
}

/**
 * Ligne du résumé agrégé : toutes les écoutes d'un morceau sur un appareil,
 * avant la fenêtre d'échange détaillée.
 */
data class ListenArchiveRow(
    val trackKey: String,
    val eventCount: Long,
    val firstAt: Long,
    val lastAt: Long,
    val title: String,
    val artist: String,
    val album: String
)
