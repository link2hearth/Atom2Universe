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
