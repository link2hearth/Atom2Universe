package com.Atom2Universe.app.music

import android.util.Log
import com.Atom2Universe.app.music.model.MusicTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import kotlinx.coroutines.withTimeout
import org.jaudiotagger.tag.id3.AbstractID3v2Frame
import org.jaudiotagger.tag.id3.AbstractID3v2Tag
import org.jaudiotagger.tag.id3.ID3v24Frames
import org.jaudiotagger.tag.id3.framebody.FrameBodyPOPM
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Gestionnaire des statistiques de lecture (play count et rating).
 *
 * Architecture de persistance à 3 niveaux:
 * 1. Cache mémoire (statsCache) - accès rapide
 * 2. Fichier JSON (.a2u_play_counts.json) - sauvegarde immédiate, source de vérité
 * 3. Tags POPM ID3v2 dans les fichiers MP3 - persistance long terme dans les fichiers
 *
 * Règle fondamentale: JAMAIS décrémenter un compteur - toujours prendre le MAX
 * entre toutes les sources disponibles.
 *
 * POPM frame format:
 * - Email: identifiant de l'application
 * - Rating: 0-255 (255 = favori)
 * - Counter: nombre de lectures
 */
object MusicStatsManager {

    private const val TAG = "MusicStatsManager"

    // Limites de validation pour les valeurs POPM
    // Au-delà de ces seuils, les valeurs sont considérées suspectes ou corrompues
    private const val POPM_WARNING_THRESHOLD = 10_000L    // Log un warning si > 10k
    private const val POPM_MAX_REASONABLE = 100_000L      // Refuse si > 100k (probablement corrompu)

    // Cache en mémoire : trackId -> TrackStats
    // Ce cache combine les données du JSON et des POPM en prenant toujours le MAX
    // Bug fix: Utilisation de ConcurrentHashMap pour thread-safety
    private val statsCache = java.util.concurrent.ConcurrentHashMap<Long, TrackStats>()

    // Map trackId -> metadataKey pour synchronisation avec MusicPlayCountManager
    // Bug fix: Utilisation de ConcurrentHashMap pour thread-safety
    private val trackIdToMetadataKey = java.util.concurrent.ConcurrentHashMap<Long, String>()

    // Tracks dont les stats ont été modifiées et doivent être écrites sur disque
    // Bug fix: Utilisation de ConcurrentHashMap.newKeySet() pour thread-safety
    private val pendingWrites = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

    data class TrackStats(
        var playCount: Long = 0,
        var rating: Long = 0,  // 0-255, 255 = favori
        var lastPlayed: Long = 0  // timestamp
    )

    /**
     * Charge les stats pour une liste de tracks.
     *
     * Processus en 3 étapes:
     * 1. Charger d'abord depuis le JSON (rapide, source de vérité)
     * 2. Lire les POPM des fichiers MP3
     * 3. Fusionner en prenant toujours le MAX (ne jamais décrémenter)
     *
     * Si un POPM contient une valeur supérieure au JSON, le JSON est mis à jour.
     */
    suspend fun loadStatsForTracks(tracks: List<MusicTrack>) = withContext(Dispatchers.IO) {
        // Désactive les logs verbeux de jaudiotagger
        Logger.getLogger("org.jaudiotagger").level = Level.OFF

        // Étape 1: Vérifier que MusicPlayCountManager est initialisé (Room = source de vérité)
        if (!MusicPlayCountManager.isLoaded()) {
            MusicPlayCountManager.loadPlayCounts()
        }

        // Étape 2: Pré-remplir le cache avec les données de Room
        var jsonLoadedCount = 0
        var corruptedCount = 0
        for (track in tracks) {
            val metadataKey = MusicPlayCountManager.generateMetadataKey(track)
            trackIdToMetadataKey[track.id] = metadataKey

            val jsonPlayCount = MusicPlayCountManager.getPlayCount(track)
            if (jsonPlayCount > 0) {
                // Validation: ignorer et corriger les valeurs corrompues
                if (jsonPlayCount > POPM_MAX_REASONABLE) {
                    Log.w(TAG, "Corrupted play count in Room ($jsonPlayCount) for ${track.title} - resetting")
                    MusicPlayCountManager.resetPlayCount(track)
                    corruptedCount++
                    continue
                }

                statsCache[track.id] = TrackStats(
                    playCount = jsonPlayCount,
                    rating = 0
                )
                jsonLoadedCount++
            }
        }
        Log.i(TAG, "Pre-loaded $jsonLoadedCount play counts from JSON" +
                if (corruptedCount > 0) " ($corruptedCount corrupted values reset)" else "")

        // Étape 3: Lire les POPM et fusionner (prendre le MAX)
        var popmLoadedCount = 0
        var popmUpdatedJsonCount = 0
        var favoriteCount = 0

        for (track in tracks) {
            try {
                val filePath = track.filePath ?: continue
                val file = File(filePath)
                if (!file.exists() || !file.canRead()) continue

                // Ne lit que les MP3
                if (!filePath.lowercase().endsWith(".mp3")) continue

                // Bug 5.14: Add timeout to prevent hanging on slow/corrupted files
                val audioFile = withTimeout(5000L) {
                    AudioFileIO.read(file)
                }
                val tag = audioFile.tag as? AbstractID3v2Tag ?: continue

                // Cherche un frame POPM
                val frameId = ID3v24Frames.FRAME_ID_POPULARIMETER
                val frames = tag.getFields(frameId)

                var popmPlayCount = 0L
                var popmRating = 0L

                for (f in frames) {
                    if (f is AbstractID3v2Frame) {
                        val body = f.body as? FrameBodyPOPM ?: continue

                        // Accepte les tags POPM de n'importe quelle application
                        // Prend le plus grand playCount trouvé parmi les POPM
                        val rawCounter = body.counter

                        // Validation: rejeter les valeurs clairement corrompues
                        if (rawCounter > POPM_MAX_REASONABLE) {
                            Log.w(TAG, "POPM counter suspiciously high ($rawCounter) for ${track.title} - ignoring (probably corrupted)")
                            continue
                        }

                        // Warning pour les valeurs élevées mais plausibles
                        if (rawCounter > POPM_WARNING_THRESHOLD) {
                            Log.w(TAG, "POPM counter unusually high ($rawCounter) for ${track.title} by ${track.artist}")
                        }

                        if (rawCounter > popmPlayCount) {
                            popmPlayCount = rawCounter
                        }
                        // Prend le plus grand rating trouvé
                        if (body.rating > popmRating) {
                            popmRating = body.rating
                        }
                    }
                }

                // Fusionner avec le cache (prendre le MAX, ne jamais décrémenter)
                val currentStats = statsCache[track.id]
                val currentPlayCount = currentStats?.playCount ?: 0
                val currentRating = currentStats?.rating ?: 0

                // Le nouveau playCount est le MAX entre JSON (dans cache) et POPM
                val finalPlayCount = maxOf(currentPlayCount, popmPlayCount)
                val finalRating = maxOf(currentRating, popmRating)

                // Toujours créer une entrée dans le cache si on a des données
                if (finalPlayCount > 0 || finalRating > 0) {
                    statsCache[track.id] = TrackStats(
                        playCount = finalPlayCount,
                        rating = finalRating
                    )

                    if (popmPlayCount > 0 || popmRating > 0) {
                        popmLoadedCount++
                    }

                    if (finalRating >= 1) {
                        favoriteCount++
                    }
                }

                // Si le POPM a une valeur supérieure au JSON, synchroniser le JSON
                if (popmPlayCount > currentPlayCount) {
                    MusicPlayCountManager.syncFromPopm(track, popmPlayCount)
                    popmUpdatedJsonCount++
                }
            } catch (_: Exception) {
                // Ignore les erreurs individuelles, continue avec les autres fichiers
            }
        }

        Log.i(TAG, "Loaded POPM stats for $popmLoadedCount tracks ($favoriteCount with rating/favorites)")
        if (popmUpdatedJsonCount > 0) {
            Log.i(TAG, "Synced $popmUpdatedJsonCount play counts from POPM to Room (POPM had higher values)")
            // Room sauvegarde automatiquement, pas besoin de savePlayCountsAsync()
        }
    }

    /**
     * Retourne le play count d'un track (version avec MusicTrack).
     *
     * Cette version est plus fiable car elle peut toujours consulter
     * le JSON même si le track n'était pas connu au démarrage.
     */
    fun getPlayCount(track: MusicTrack): Long {
        // D'abord vérifier le cache mémoire
        val cachedCount = statsCache[track.id]?.playCount ?: 0
        if (cachedCount > 0) {
            return cachedCount
        }

        // Consulter le JSON via les métadonnées du track
        val jsonCount = MusicPlayCountManager.getPlayCount(track)
        if (jsonCount > 0) {
            // Mettre à jour le cache et la map pour les prochains appels
            statsCache[track.id] = TrackStats(playCount = jsonCount)
            trackIdToMetadataKey[track.id] = MusicPlayCountManager.generateMetadataKey(track)
            return jsonCount
        }

        return 0
    }

    /**
     * Retourne le play count d'un track par son ID.
     */
    @Suppress("unused")
    fun getPlayCount(trackId: Long): Long {
        val cachedCount = statsCache[trackId]?.playCount ?: 0
        if (cachedCount > 0) {
            return cachedCount
        }

        val metadataKey = trackIdToMetadataKey[trackId]
        if (metadataKey != null) {
            val jsonCount = MusicPlayCountManager.getPlayCountByKey(metadataKey)
            if (jsonCount > 0) {
                statsCache[trackId] = TrackStats(playCount = jsonCount)
                return jsonCount
            }
        }

        return 0
    }

    /**
     * Retourne le rating d'un track (0-255)
     */
    fun getRating(trackId: Long): Long {
        return statsCache[trackId]?.rating ?: 0
    }

    /**
     * Retourne les stats d'un track
     */
    @Suppress("unused")
    fun getStats(trackId: Long): TrackStats? {
        return statsCache[trackId]
    }

    /**
     * Vérifie si un track est favori basé sur le rating POPM
     */
    @Suppress("unused")
    fun isFavoriteByRating(trackId: Long): Boolean {
        return (statsCache[trackId]?.rating ?: 0) >= 1
    }

    /**
     * Incrémente le play count d'un track.
     *
     * Cette méthode met à jour:
     * 1. Le cache mémoire (immédiat)
     * 2. Le fichier JSON (immédiat, via MusicPlayCountManager)
     * 3. Le tag POPM dans le MP3 (différé, via MusicPopmSyncManager)
     *
     * Le JSON est mis à jour immédiatement pour garantir la persistance
     * même si l'app est tuée avant l'écriture POPM.
     */
    suspend fun incrementPlayCount(track: MusicTrack, positionMs: Long = -1L, durationMs: Long = -1L) {
        // Stocker la clé métadonnées si pas déjà fait
        if (!trackIdToMetadataKey.containsKey(track.id)) {
            trackIdToMetadataKey[track.id] = MusicPlayCountManager.generateMetadataKey(track)
        }

        // Mise à jour IMMÉDIATE dans Room (source de vérité) - retourne le nouveau total authoritative.
        // On utilise cette valeur pour sync statsCache, car statsCache peut être vide pour ce track
        // si le titre n'a pas encore été affiché (ex: lecture avec écran éteint).
        // Sans ça, getOrPut créerait TrackStats(playCount=0) et ++ donnerait 1 au lieu de N+1.
        val newCount = MusicPlayCountManager.incrementPlayCount(track, positionMs, durationMs)

        // Synchroniser statsCache avec la valeur authoritative de Room
        val stats = statsCache.getOrPut(track.id) { TrackStats() }
        stats.playCount = newCount
        stats.lastPlayed = System.currentTimeMillis()

        Log.d(TAG, "Play count for '${track.title}': $newCount")

        // Enregistre la modification pour écriture différée dans le fichier MP3
        MusicPopmSyncManager.queuePlayCountIncrement(track)
    }

    /**
     * Met à jour le rating d'un track.
     * La modification est mise en cache et sera appliquée au fichier quand il ne sera plus en lecture.
     */
    suspend fun setRating(track: MusicTrack, rating: Long) {
        val stats = statsCache.getOrPut(track.id) { TrackStats() }
        stats.rating = rating.coerceIn(0, 255)

        Log.d(TAG, "Rating for '${track.title}': ${stats.rating}")

        // Enregistre la modification pour écriture différée
        MusicPopmSyncManager.queueRatingUpdate(track, stats.rating)
    }

    /**
     * Met à jour directement le cache en mémoire (appelé par MusicPopmSyncManager après une écriture réussie).
     */
    fun updateCacheDirectly(trackId: Long, playCount: Long, rating: Long) {
        // Validation: ne pas accepter de valeurs corrompues dans le cache
        val safePlayCount = if (playCount > POPM_MAX_REASONABLE) {
            Log.w(TAG, "Rejecting corrupted play count ($playCount) for track $trackId")
            0L
        } else {
            playCount
        }

        val stats = statsCache.getOrPut(trackId) { TrackStats() }
        stats.playCount = safePlayCount
        stats.rating = rating
        pendingWrites.remove(trackId)
        Log.d(TAG, "Cache updated directly for track $trackId: playCount=$safePlayCount, rating=$rating")
    }

    /**
     * Retourne les tracks triés par play count (du plus écouté au moins écouté).
     * Optimisé pour éviter les appels multiples à getPlayCount().
     */
    fun getTopPlayedTracks(allTracks: List<MusicTrack>, limit: Int = 50): List<MusicTrack> {
        // Cache les play counts pour éviter les lookups répétés
        val playCountMap = allTracks.associateWith { getPlayCount(it) }
        return allTracks
            .filter { playCountMap[it]!! > 0 }
            .sortedByDescending { playCountMap[it]!! }
            .take(limit)
    }

    /**
     * Retourne les tracks les moins écoutés (pour lecture aléatoire discovery).
     * Optimisé pour éviter les appels multiples à getPlayCount().
     */
    fun getLeastPlayedTracks(allTracks: List<MusicTrack>): List<MusicTrack> {
        if (allTracks.isEmpty()) return emptyList()

        // Cache les play counts pour éviter les lookups répétés
        val playCountMap = allTracks.associateWith { getPlayCount(it) }

        // Trouve le minimum de play count
        val minPlayCount = playCountMap.values.minOrNull() ?: 0

        // Retourne tous les tracks avec ce minimum
        return allTracks.filter { playCountMap[it] == minPlayCount }
    }

    /**
     * Retourne un track aléatoire parmi les moins écoutés
     */
    @Suppress("unused")
    fun getRandomLeastPlayedTrack(allTracks: List<MusicTrack>): MusicTrack? {
        val leastPlayed = getLeastPlayedTracks(allTracks)
        return if (leastPlayed.isNotEmpty()) leastPlayed.random() else null
    }

    /**
     * Synchronise les favoris depuis les ratings POPM
     */
    suspend fun syncFavoritesFromRatings(tracks: List<MusicTrack>): Int {
        var importedCount = 0

        for (track in tracks) {
            val rating = getRating(track.id)
            if (rating >= 1 && !MusicFavoritesManager.isFavorite(track)) {
                MusicFavoritesManager.addFavorite(track)
                importedCount++
                Log.d(TAG, "Imported favorite from POPM rating: ${track.title}")
            }
        }

        if (importedCount > 0) {
            Log.i(TAG, "Imported $importedCount favorites from POPM ratings")
        }

        return importedCount
    }

    /**
     * Vide le cache (utile lors du rescan)
     */
    @Suppress("unused")
    fun clearCache() {
        statsCache.clear()
        pendingWrites.clear()
    }
}
