package com.Atom2Universe.app.stats.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO pour les opérations sur les sessions d'utilisation.
 */
@Dao
interface UsageSessionDao {

    /**
     * Insère une nouvelle session d'utilisation (session locale).
     * Lève une exception en cas de conflit (ne devrait pas arriver pour des sessions locales).
     */
    @Insert
    suspend fun insertSession(session: UsageSessionEntity): Long

    /**
     * Insère une session en ignorant les doublons (utilisé pour l'import depuis la sync).
     * Retourne -1 si la session existe déjà (via la contrainte unique moduleType+startTimestamp+endTimestamp).
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSessionIgnoreConflict(session: UsageSessionEntity): Long

    /**
     * Récupère la durée totale d'utilisation pour un module donné dans une période.
     *
     * @param moduleType Type de module ("music", "midi", "radio")
     * @param startDate Timestamp de début de période
     * @param endDate Timestamp de fin de période
     * @return Durée totale en millisecondes
     */
    @Query("""
        SELECT COALESCE(SUM(durationMs), 0)
        FROM usage_sessions
        WHERE moduleType = :moduleType
        AND startTimestamp >= :startDate
        AND endTimestamp <= :endDate
    """)
    suspend fun getTotalDurationByModule(
        moduleType: String,
        startDate: Long,
        endDate: Long
    ): Long

    /**
     * Récupère le top des artistes les plus écoutés avec leur durée d'écoute.
     *
     * @param startDate Timestamp de début de période
     * @param endDate Timestamp de fin de période
     * @param limit Nombre maximum de résultats
     * @return Liste de paires (artiste, durée en ms)
     */
    @Query("""
        SELECT trackArtist, SUM(durationMs) as totalDuration
        FROM usage_sessions
        WHERE moduleType = 'music'
        AND trackArtist IS NOT NULL
        AND trackArtist != ''
        AND startTimestamp >= :startDate
        AND endTimestamp <= :endDate
        GROUP BY trackArtist
        ORDER BY totalDuration DESC
        LIMIT :limit
    """)
    suspend fun getTopArtists(
        startDate: Long,
        endDate: Long,
        limit: Int
    ): List<ArtistStats>

    /**
     * Récupère le top des albums les plus écoutés avec leur durée d'écoute.
     *
     * @param startDate Timestamp de début de période
     * @param endDate Timestamp de fin de période
     * @param limit Nombre maximum de résultats
     * @return Liste de paires (album, artiste, durée en ms)
     */
    @Query("""
        SELECT trackAlbum, trackAlbumArtist, SUM(durationMs) as totalDuration
        FROM usage_sessions
        WHERE moduleType = 'music'
        AND trackAlbum IS NOT NULL
        AND trackAlbum != ''
        AND startTimestamp >= :startDate
        AND endTimestamp <= :endDate
        GROUP BY trackAlbum, trackAlbumArtist
        ORDER BY totalDuration DESC
        LIMIT :limit
    """)
    suspend fun getTopAlbums(
        startDate: Long,
        endDate: Long,
        limit: Int
    ): List<AlbumStats>

    /**
     * Récupère le top des fichiers MIDI les plus travaillés avec leur durée de pratique.
     *
     * @param startDate Timestamp de début de période
     * @param endDate Timestamp de fin de période
     * @param limit Nombre maximum de résultats
     * @return Liste de paires (nom fichier, durée en ms)
     */
    @Query("""
        SELECT midiFileName, SUM(durationMs) as totalDuration
        FROM usage_sessions
        WHERE moduleType IN ('midi', 'midi_practice')
        AND midiFileName IS NOT NULL
        AND midiFileName != ''
        AND startTimestamp >= :startDate
        AND endTimestamp <= :endDate
        GROUP BY midiFileName
        ORDER BY totalDuration DESC
        LIMIT :limit
    """)
    suspend fun getTopMidiFiles(
        startDate: Long,
        endDate: Long,
        limit: Int
    ): List<MidiFileStats>

    /**
     * Récupère le score moyen de practice pour une période donnée.
     *
     * @param startDate Timestamp de début de période
     * @param endDate Timestamp de fin de période
     * @return Score moyen (null si aucune session)
     */
    @Query("""
        SELECT AVG(practiceScore)
        FROM usage_sessions
        WHERE moduleType = 'midi_practice'
        AND practiceScore IS NOT NULL
        AND startTimestamp >= :startDate
        AND endTimestamp <= :endDate
    """)
    suspend fun getAveragePracticeScore(
        startDate: Long,
        endDate: Long
    ): Float?

    /**
     * Récupère le nombre de sessions pour un module dans une période.
     */
    @Query("""
        SELECT COUNT(*)
        FROM usage_sessions
        WHERE moduleType = :moduleType
        AND startTimestamp >= :startDate
        AND endTimestamp <= :endDate
    """)
    suspend fun getSessionCount(
        moduleType: String,
        startDate: Long,
        endDate: Long
    ): Int

    /**
     * Supprime toutes les sessions plus anciennes qu'un timestamp donné.
     * Utile pour le nettoyage automatique des anciennes données.
     */
    @Query("DELETE FROM usage_sessions WHERE endTimestamp < :timestamp")
    suspend fun deleteSessionsOlderThan(timestamp: Long): Int

    /**
     * Récupère toutes les sessions depuis un timestamp donné (pour les résumés journaliers).
     */
    @Query("SELECT * FROM usage_sessions WHERE endTimestamp >= :sinceTimestamp ORDER BY endTimestamp DESC")
    suspend fun getAllSessionsSince(sinceTimestamp: Long): List<UsageSessionEntity>

    /**
     * Récupère uniquement les sessions créées localement (sourceDeviceId IS NULL), pour la sync.
     * Exclut les sessions importées d'autres appareils afin d'éviter de les réexporter sous le mauvais deviceId.
     */
    @Query("SELECT * FROM usage_sessions WHERE endTimestamp >= :sinceTimestamp AND sourceDeviceId IS NULL ORDER BY endTimestamp DESC")
    suspend fun getLocalSessionsSince(sinceTimestamp: Long): List<UsageSessionEntity>

    /**
     * Récupère les sessions dans une plage de timestamps.
     */
    @Query("SELECT * FROM usage_sessions WHERE startTimestamp >= :startMs AND startTimestamp < :endMs ORDER BY startTimestamp ASC")
    suspend fun getSessionsBetween(startMs: Long, endMs: Long): List<UsageSessionEntity>

    /**
     * Récupère toutes les sessions pour une liste de modules (ex: lecture livres+BD),
     * triées de la plus récente à la plus ancienne.
     */
    @Query("SELECT * FROM usage_sessions WHERE moduleType IN (:moduleTypes) ORDER BY startTimestamp DESC")
    suspend fun getSessionsByModules(moduleTypes: List<String>): List<UsageSessionEntity>

    /**
     * Récupère le temps de lecture total par titre pour un module (book ou comic),
     * pour l'affichage sur les tuiles des bibliothèques.
     */
    @Query("""
        SELECT readingTitle, SUM(durationMs) as totalDuration
        FROM usage_sessions
        WHERE moduleType = :moduleType
        AND readingTitle IS NOT NULL
        AND readingTitle != ''
        GROUP BY readingTitle
    """)
    suspend fun getReadingTimeByTitle(moduleType: String): List<TitleDurationStats>

    /**
     * Récupère les sessions d'une liste de modules dans une plage de timestamps
     * (ex: lecture livres+BD sur une période ou un mois pour le calendrier).
     */
    @Query("SELECT * FROM usage_sessions WHERE moduleType IN (:moduleTypes) AND startTimestamp >= :startMs AND startTimestamp < :endMs ORDER BY startTimestamp ASC")
    suspend fun getSessionsByModulesBetween(moduleTypes: List<String>, startMs: Long, endMs: Long): List<UsageSessionEntity>

    /**
     * Récupère le top des titres (livres/BD) les plus lus sur une période, tous modules confondus.
     */
    @Query("""
        SELECT readingTitle, moduleType, SUM(durationMs) as totalDuration
        FROM usage_sessions
        WHERE moduleType IN (:moduleTypes)
        AND readingTitle IS NOT NULL
        AND readingTitle != ''
        AND startTimestamp >= :startDate
        AND endTimestamp <= :endDate
        GROUP BY readingTitle, moduleType
        ORDER BY totalDuration DESC
        LIMIT :limit
    """)
    suspend fun getTopReadingTitles(moduleTypes: List<String>, startDate: Long, endDate: Long, limit: Int): List<ReadingTitleStats>
}

/**
 * Classe de données pour le top des titres de lecture (livre/BD) sur une période.
 */
data class ReadingTitleStats(
    val readingTitle: String,
    val moduleType: String,
    val totalDuration: Long
)

/**
 * Classe de données pour le temps de lecture cumulé d'un titre (livre/BD).
 */
data class TitleDurationStats(
    val readingTitle: String,
    val totalDuration: Long
)

/**
 * Classe de données pour les statistiques d'artiste.
 */
data class ArtistStats(
    val trackArtist: String,
    val totalDuration: Long
)

/**
 * Classe de données pour les statistiques d'album.
 */
data class AlbumStats(
    val trackAlbum: String,
    val trackAlbumArtist: String?,
    val totalDuration: Long
)

/**
 * Classe de données pour les statistiques de fichiers MIDI.
 */
data class MidiFileStats(
    val midiFileName: String,
    val totalDuration: Long
)
