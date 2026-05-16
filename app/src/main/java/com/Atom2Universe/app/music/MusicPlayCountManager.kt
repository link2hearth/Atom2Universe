package com.Atom2Universe.app.music

import android.content.Context
import android.os.Environment
import android.util.Log
import com.Atom2Universe.app.music.data.ListenEvent
import com.Atom2Universe.app.music.data.ListenEventDao
import com.Atom2Universe.app.music.data.MusicDatabase
import com.Atom2Universe.app.music.data.PlayCountDao
import com.Atom2Universe.app.music.data.PlayCountEntry
import com.Atom2Universe.app.music.model.MusicTrack
import com.Atom2Universe.app.music.sync.DeviceIdentity
import com.Atom2Universe.app.music.sync.SyncthingManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import androidx.core.content.edit

/**
 * Gestionnaire des compteurs d'écoutes persistants.
 *
 * Utilise Room (SQLite) pour stocker les compteurs d'écoutes.
 * C'est la SOURCE DE VÉRITÉ pour les compteurs - les tags POPM sont synchronisés à partir de ces données.
 *
 * Avantages de Room vs JSON:
 * - Requêtes indexées ultra-rapides (même avec 100k+ morceaux)
 * - Pas besoin de charger toutes les données en mémoire
 * - Transactions ACID (pas de corruption)
 * - Mises à jour atomiques
 *
 * Règle fondamentale: NE JAMAIS décrémenter un compteur - toujours prendre le MAX.
 */
object MusicPlayCountManager {

    private const val TAG = "MusicPlayCountManager"
    private const val JSON_FILENAME = ".a2u_play_counts.json"

    // Limite de validation pour les valeurs POPM
    // Au-delà de ce seuil, les valeurs sont considérées corrompues
    private const val POPM_MAX_REASONABLE = 100_000L

    private lateinit var dao: PlayCountDao
    private lateinit var listenEventDao: ListenEventDao
    private lateinit var appContext: Context
    @Volatile
    private var isInitialized = false

    // Mutex pour protéger l'initialisation
    private val initMutex = Mutex()

    // Cache en mémoire thread-safe pour accès rapide (lecture seule, mis à jour après chaque écriture)
    private val memoryCache = ConcurrentHashMap<String, Long>()
    private val cacheMutex = Mutex()

    /**
     * Initialise le manager avec le contexte de l'application.
     * Doit être appelé au démarrage de l'app AVANT toute utilisation.
     * Thread-safe: utilise un mutex pour éviter les race conditions.
     */
    suspend fun init(context: Context) {
        // Double-checked locking pattern avec mutex
        if (isInitialized) return

        initMutex.withLock {
            // Re-vérifier après acquisition du mutex
            if (isInitialized) return

            appContext = context.applicationContext
            val db = MusicDatabase.getInstance(appContext)
            dao = db.playCountDao()
            listenEventDao = db.listenEventDao()
            isInitialized = true

            Log.d(TAG, "MusicPlayCountManager initialized with Room")

            // Migration depuis le JSON si présent
            migrateFromJsonIfNeeded()

            // Migration des earnedPlayCount historiques vers listen_events
            migrateHistoricalListenEvents()

            // Pré-charger le cache en mémoire
            preloadCache()
        }
    }

    /**
     * Génère une clé unique basée sur les métadonnées du track.
     * Format: "artiste|titre|album" en minuscules pour comparaison insensible à la casse.
     */
    fun generateMetadataKey(track: MusicTrack): String {
        return generateMetadataKey(track.title, track.artist, track.album)
    }

    fun generateMetadataKey(title: String, artist: String, album: String): String {
        return "${artist.lowercase().trim()}|${title.lowercase().trim()}|${album.lowercase().trim()}"
    }

    /**
     * Retourne le play count d'un track.
     * Utilise le cache mémoire pour un accès ultra-rapide.
     * Retourne 0 si le manager n'est pas initialisé.
     */
    fun getPlayCount(track: MusicTrack): Long {
        if (!isInitialized) return 0
        val key = generateMetadataKey(track)
        return memoryCache[key] ?: 0
    }

    /**
     * Retourne le play count par clé métadonnées.
     * Retourne 0 si le manager n'est pas initialisé.
     */
    fun getPlayCountByKey(key: String): Long {
        if (!isInitialized) return 0
        return memoryCache[key] ?: 0
    }

    /**
     * Incrémente le play count d'un track.
     * Met à jour play_counts ET insère un ListenEvent dans le journal immuable.
     *
     * @param positionMs position de lecture au moment du déclenchement (-1 si inconnue)
     * @param durationMs durée totale du track (-1 si inconnue)
     */
    suspend fun incrementPlayCount(track: MusicTrack, positionMs: Long = -1L, durationMs: Long = -1L): Long {
        if (!isInitialized) {
            Log.w(TAG, "Manager not initialized")
            return 0
        }

        val key = generateMetadataKey(track)
        val now = System.currentTimeMillis()

        val newCount = withContext(Dispatchers.IO) {
            cacheMutex.withLock {
                val existing = dao.getByKey(key)

                if (existing != null) {
                    val count = existing.playCount + 1
                    val earnedCount = existing.earnedPlayCount + 1
                    dao.update(existing.copy(
                        playCount = count,
                        earnedPlayCount = earnedCount,
                        lastPlayed = now,
                        updatedAt = now
                    ))
                    memoryCache[key] = count
                    Log.d(TAG, "Incremented play count for '${track.title}': $count")
                    count
                } else {
                    dao.insert(PlayCountEntry(
                        metadataKey = key,
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        playCount = 1,
                        earnedPlayCount = 1,
                        lastPlayed = now,
                        createdAt = now,
                        updatedAt = now
                    ))
                    memoryCache[key] = 1
                    Log.d(TAG, "Created play count for '${track.title}': 1")
                    1L
                }
            }
        }

        // Insérer l'événement dans le journal immuable
        withContext(Dispatchers.IO) {
            try {
                val effectiveDuration = if (durationMs > 0) durationMs else track.duration
                val effectivePosition = if (positionMs > 0) positionMs else effectiveDuration / 2
                listenEventDao.insert(
                    ListenEvent(
                        uuid = UUID.randomUUID().toString(),
                        trackKey = key,
                        deviceId = DeviceIdentity.getDeviceId(appContext),
                        listenedAt = now,
                        durationListenedMs = effectivePosition,
                        trackDurationMs = effectiveDuration,
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        isMigrated = false
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Could not insert listen event for '${track.title}'", e)
            }
        }

        // Planifier un export Syncthing (débounce 2 min)
        SyncthingManager.scheduleExport()

        return newCount
    }

    /**
     * Synchronise un play count depuis une source externe (POPM).
     * Prend toujours le MAX entre la valeur existante et la nouvelle.
     *
     * IMPORTANT: Cette méthode ne met PAS à jour earnedPlayCount car ce sont
     * des écoutes importées (pas jouées sur cet appareil). Cela évite le
     * doublement quand un MP3 avec POPM est copié sur un nouvel appareil.
     *
     * @return true si la valeur a été mise à jour
     */
    suspend fun syncFromPopm(track: MusicTrack, popmPlayCount: Long): Boolean {
        if (!isInitialized || popmPlayCount <= 0) return false

        // Validation: rejeter les valeurs clairement corrompues
        if (popmPlayCount > POPM_MAX_REASONABLE) {
            Log.w(TAG, "Rejecting corrupted POPM value ($popmPlayCount) for '${track.title}' - value exceeds reasonable threshold")
            return false
        }

        val key = generateMetadataKey(track)
        val currentCount = memoryCache[key] ?: 0

        // Règle MAX: ne jamais décrémenter
        if (popmPlayCount <= currentCount) {
            return false
        }

        return withContext(Dispatchers.IO) {
            cacheMutex.withLock {
                val now = System.currentTimeMillis()
                val existing = dao.getByKey(key)

                if (existing != null) {
                    // Mettre à jour seulement si POPM est supérieur
                    if (popmPlayCount > existing.playCount) {
                        dao.update(existing.copy(
                            playCount = popmPlayCount,
                            updatedAt = now
                        ))
                        memoryCache[key] = popmPlayCount
                        Log.d(TAG, "Synced from POPM for '${track.title}': ${existing.playCount} -> $popmPlayCount")
                        true
                    } else {
                        false
                    }
                } else {
                    // Créer une nouvelle entrée avec la valeur POPM
                    // earnedPlayCount reste à 0 car c'est un import, pas une écoute locale
                    dao.insert(PlayCountEntry(
                        metadataKey = key,
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        playCount = popmPlayCount,
                        earnedPlayCount = 0,  // Import POPM, pas une écoute locale
                        lastPlayed = 0,
                        createdAt = now,
                        updatedAt = now
                    ))
                    memoryCache[key] = popmPlayCount
                    Log.d(TAG, "Created from POPM for '${track.title}': $popmPlayCount (earned: 0)")
                    true
                }
            }
        }
    }

    /**
     * Réinitialise le play count d'un track (utilisé pour corriger les valeurs corrompues).
     * Bug 5.16: Now properly updates the memory cache when resetPlayCount() is called.
     */
    suspend fun resetPlayCount(track: MusicTrack) {
        if (!isInitialized) return

        val key = generateMetadataKey(track)

        withContext(Dispatchers.IO) {
            cacheMutex.withLock {
                val existing = dao.getByKey(key)
                if (existing != null) {
                    dao.update(existing.copy(
                        playCount = 0,
                        updatedAt = System.currentTimeMillis()
                    ))
                    Log.i(TAG, "Reset corrupted play count for '${track.title}'")
                }
                // Bug 5.16: Ensure cache is invalidated inside the mutex lock
                memoryCache[key] = 0L
            }
        }
    }

    /**
     * Réinitialise le play count par clé de métadonnées.
     * Utilisé par MusicPopmSyncManager quand MusicLibrary n'est pas disponible.
     * Bug 5.16: Now properly updates the memory cache when resetPlayCount() is called.
     */
    suspend fun resetPlayCountByKey(metadataKey: String) {
        if (!isInitialized) return

        withContext(Dispatchers.IO) {
            cacheMutex.withLock {
                val existing = dao.getByKey(metadataKey)
                if (existing != null) {
                    dao.update(existing.copy(
                        playCount = 0,
                        updatedAt = System.currentTimeMillis()
                    ))
                    Log.i(TAG, "Reset corrupted play count for key: $metadataKey")
                }
                // Bug 5.16: Ensure cache is invalidated inside the mutex lock
                memoryCache[metadataKey] = 0L
            }
        }
    }

    /**
     * Synchronise le play count depuis POPM par clé de métadonnées.
     * Utilisé par MusicPopmSyncManager quand MusicLibrary n'est pas disponible.
     */
    suspend fun syncFromPopmByKey(metadataKey: String, popmPlayCount: Long): Boolean {
        if (!isInitialized || popmPlayCount <= 0) return false

        // Validation: rejeter les valeurs clairement corrompues
        if (popmPlayCount > POPM_MAX_REASONABLE) {
            Log.w(TAG, "Rejecting corrupted POPM value ($popmPlayCount) for key '$metadataKey'")
            return false
        }

        val currentCount = memoryCache[metadataKey] ?: 0

        // Règle MAX: ne jamais décrémenter
        if (popmPlayCount <= currentCount) {
            return false
        }

        return withContext(Dispatchers.IO) {
            cacheMutex.withLock {
                val now = System.currentTimeMillis()
                val existing = dao.getByKey(metadataKey)

                if (existing != null) {
                    // Mettre à jour seulement si POPM est supérieur
                    if (popmPlayCount > existing.playCount) {
                        dao.update(existing.copy(
                            playCount = popmPlayCount,
                            updatedAt = now
                        ))
                        memoryCache[metadataKey] = popmPlayCount
                        Log.d(TAG, "Synced from POPM for key '$metadataKey': ${existing.playCount} -> $popmPlayCount")
                        true
                    } else {
                        false
                    }
                } else {
                    // Extraire title/artist/album depuis la clé (format: artist|title|album)
                    val parts = metadataKey.split("|")
                    if (parts.size < 3) {
                        Log.w(TAG, "Malformed metadataKey (expected 3 parts, got ${parts.size}): $metadataKey")
                    }
                    val artist = parts.getOrElse(0) { "" }
                    val title = parts.getOrElse(1) { "" }
                    val album = parts.getOrElse(2) { "" }

                    // Skip entries with empty title (likely corrupted)
                    if (title.isBlank()) {
                        Log.w(TAG, "Skipping POPM sync for entry with empty title: $metadataKey")
                        return@withLock false
                    }

                    dao.insert(PlayCountEntry(
                        metadataKey = metadataKey,
                        title = title,
                        artist = artist,
                        album = album,
                        playCount = popmPlayCount,
                        earnedPlayCount = 0,
                        lastPlayed = 0,
                        createdAt = now,
                        updatedAt = now
                    ))
                    memoryCache[metadataKey] = popmPlayCount
                    Log.d(TAG, "Created from POPM for key '$metadataKey': $popmPlayCount")
                    true
                }
            }
        }
    }

    /**
     * Retourne les tracks triés par play count (du plus écouté au moins écouté).
     * Optimisé pour éviter les appels multiples à getPlayCount().
     */
    @Suppress("unused")
    fun getTopPlayedTracks(allTracks: List<MusicTrack>, limit: Int = 50): List<MusicTrack> {
        // Cache les play counts pour éviter les lookups répétés
        val playCountMap = allTracks.associateWith { getPlayCount(it) }
        return allTracks
            .filter { playCountMap[it]!! > 0 }
            .sortedByDescending { playCountMap[it]!! }
            .take(limit)
    }

    /**
     * Retourne les tracks les moins écoutés.
     * Optimisé pour éviter les appels multiples à getPlayCount().
     */
    fun getLeastPlayedTracks(allTracks: List<MusicTrack>): List<MusicTrack> {
        if (allTracks.isEmpty()) return emptyList()

        // Cache les play counts pour éviter les lookups répétés
        val playCountMap = allTracks.associateWith { getPlayCount(it) }
        val minPlayCount = playCountMap.values.minOrNull() ?: 0
        return allTracks.filter { playCountMap[it] == minPlayCount }
    }

    /**
     * Retourne un track aléatoire parmi les moins écoutés.
     */
    @Suppress("unused")
    fun getRandomLeastPlayedTrack(allTracks: List<MusicTrack>): MusicTrack? {
        val leastPlayed = getLeastPlayedTracks(allTracks)
        return if (leastPlayed.isNotEmpty()) leastPlayed.random() else null
    }

    /**
     * Retourne le nombre de tracks avec au moins une écoute.
     */
    @Suppress("unused")
    suspend fun getTracksWithPlayCountCount(): Int {
        if (!isInitialized) return 0
        return withContext(Dispatchers.IO) {
            dao.countWithPlayCount()
        }
    }

    /**
     * Vérifie si le manager est initialisé.
     */
    @Suppress("unused")
    fun isInitialized(): Boolean = isInitialized

    /**
     * Pré-charge le cache mémoire depuis Room.
     * Optimisé pour ne charger que les clés et valeurs.
     */
    private suspend fun preloadCache() = withContext(Dispatchers.IO) {
        try {
            val entries = dao.getAllWithPlayCount()
            cacheMutex.withLock {
                memoryCache.clear()
                entries.forEach { entry ->
                    memoryCache[entry.metadataKey] = entry.playCount
                }
            }
            Log.i(TAG, "Preloaded ${entries.size} play counts into memory cache")
        } catch (e: Exception) {
            Log.e(TAG, "Error preloading cache", e)
        }
    }

    /**
     * Force le rechargement du cache depuis Room.
     */
    @Suppress("unused")
    suspend fun refreshCache() {
        if (!isInitialized) return
        preloadCache()
    }

    /**
     * Force le rechargement du cache depuis Room (version avec context).
     * Utilisée par le merger cloud sync.
     */
    suspend fun refreshCache(context: Context) {
        if (!isInitialized) {
            init(context)
        }
        preloadCache()
    }

    /**
     * Met à jour directement un play count (utilisé par le merge cloud).
     * Respecte la règle MAX: ne met à jour que si la nouvelle valeur est supérieure.
     *
     * @return true si la mise à jour a été effectuée
     */
    @Suppress("unused")
    suspend fun updatePlayCountIfHigher(metadataKey: String, newPlayCount: Long): Boolean {
        if (!isInitialized || newPlayCount <= 0) return false

        val currentCount = memoryCache[metadataKey] ?: 0
        if (newPlayCount <= currentCount) {
            return false
        }

        return withContext(Dispatchers.IO) {
            cacheMutex.withLock {
                val existing = dao.getByKey(metadataKey)
                if (existing != null && newPlayCount > existing.playCount) {
                    dao.update(existing.copy(
                        playCount = newPlayCount,
                        updatedAt = System.currentTimeMillis()
                    ))
                    memoryCache[metadataKey] = newPlayCount
                    Log.d(TAG, "Updated play count from cloud sync: $metadataKey -> $newPlayCount")
                    true
                } else {
                    false
                }
            }
        }
    }

    // ========== Accès au journal d'écoutes ==========

    /**
     * Historique récent (écoutes temps réel uniquement, pas les migrées).
     */
    suspend fun getListenHistory(limit: Int = 200): List<ListenEvent> {
        if (!isInitialized) return emptyList()
        return withContext(Dispatchers.IO) { listenEventDao.getRecentHistory(limit) }
    }

    /**
     * Tous les événements d'un track spécifique.
     */
    suspend fun getEventsForTrack(track: MusicTrack): List<ListenEvent> {
        if (!isInitialized) return emptyList()
        val key = generateMetadataKey(track)
        return withContext(Dispatchers.IO) { listenEventDao.getEventsForTrack(key) }
    }

    /**
     * Événements locaux depuis un timestamp — utilisé pour la sync P2P.
     */
    suspend fun getLocalEventsSince(sinceTimestamp: Long): List<ListenEvent> {
        if (!isInitialized) return emptyList()
        val deviceId = DeviceIdentity.getDeviceId(appContext)
        return withContext(Dispatchers.IO) {
            listenEventDao.getLocalEventsSince(deviceId, sinceTimestamp)
        }
    }

    /**
     * Insère des événements reçus d'un autre nœud (déduplication par UUID automatique).
     */
    suspend fun insertRemoteEvents(events: List<ListenEvent>) {
        if (!isInitialized || events.isEmpty()) return
        withContext(Dispatchers.IO) {
            listenEventDao.insertAll(events)
            // Recalculer les play counts depuis le journal pour les tracks concernés
            val affectedKeys = events.map { it.trackKey }.toSet()
            cacheMutex.withLock {
                for (key in affectedKeys) {
                    val count = listenEventDao.getPlayCount(key)
                    if (count > 0) {
                        val current = dao.getByKey(key)
                        if (current != null && count > current.playCount) {
                            dao.update(current.copy(playCount = count, updatedAt = System.currentTimeMillis()))
                            memoryCache[key] = count
                        }
                    }
                }
            }
        }
    }

    // ========== Migration historique ==========

    /**
     * Migration one-shot : convertit les earnedPlayCount existants en ListenEvents.
     * Crée N événements par track (N = earnedPlayCount), timestamps interpolés entre
     * createdAt et lastPlayed. Marqués isMigrated=true pour les distinguer des vraies écoutes.
     */
    private suspend fun migrateHistoricalListenEvents() = withContext(Dispatchers.IO) {
        val prefs = appContext.getSharedPreferences("a2u_music_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("listen_events_migrated", false)) return@withContext

        val deviceId = DeviceIdentity.getDeviceId(appContext)
        val entries = dao.getAllWithEarnedPlayCount()

        if (entries.isEmpty()) {
            prefs.edit { putBoolean("listen_events_migrated", true) }
            return@withContext
        }

        val events = mutableListOf<ListenEvent>()

        for (entry in entries) {
            val count = entry.earnedPlayCount.coerceAtLeast(0)
            if (count <= 0) continue

            val start = entry.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis()
            val end = if (entry.lastPlayed > start) entry.lastPlayed else start
            val step = if (count > 1 && end > start) (end - start) / count else 0L

            for (i in 0 until count) {
                events.add(
                    ListenEvent(
                        uuid = UUID.randomUUID().toString(),
                        trackKey = entry.metadataKey,
                        deviceId = deviceId,
                        listenedAt = start + (i * step),
                        durationListenedMs = -1L,
                        trackDurationMs = -1L,
                        title = entry.title,
                        artist = entry.artist,
                        album = entry.album,
                        isMigrated = true
                    )
                )
            }
        }

        // Insérer par lots pour éviter des transactions trop grandes
        events.chunked(500).forEach { batch -> listenEventDao.insertAll(batch) }

        prefs.edit { putBoolean("listen_events_migrated", true) }
        Log.i(TAG, "Migrated ${events.size} historical listen events from earnedPlayCount (${entries.size} tracks)")
    }

    /**
     * Migration depuis l'ancien fichier JSON vers Room.
     * Cette opération est effectuée une seule fois au premier lancement après la mise à jour.
     */
    private suspend fun migrateFromJsonIfNeeded() = withContext(Dispatchers.IO) {
        try {
            val jsonFile = getJsonFile()
            if (!jsonFile.exists()) {
                Log.d(TAG, "No JSON file to migrate")
                return@withContext
            }

            // Vérifier si Room a déjà des données (migration déjà faite)
            val existingCount = dao.count()
            if (existingCount > 0) {
                Log.d(TAG, "Room already has $existingCount entries, skipping JSON migration")
                // Supprimer le JSON car Room est maintenant la source de vérité
                deleteJsonFile()
                return@withContext
            }

            Log.i(TAG, "Starting migration from JSON to Room...")

            val jsonString = jsonFile.readText()
            val json = JSONObject(jsonString)
            val countsObject = json.optJSONObject("playCounts") ?: JSONObject()

            var migratedCount = 0
            val now = System.currentTimeMillis()

            val keys = countsObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val entry = countsObject.getJSONObject(key)

                val playCount = entry.optLong("playCount", 0)
                if (playCount > 0) {
                    dao.insert(PlayCountEntry(
                        metadataKey = key,
                        title = entry.optString("title", ""),
                        artist = entry.optString("artist", ""),
                        album = entry.optString("album", ""),
                        playCount = playCount,
                        lastPlayed = entry.optLong("lastPlayed", 0),
                        createdAt = now,
                        updatedAt = now
                    ))
                    migratedCount++
                }
            }

            Log.i(TAG, "Migration complete: $migratedCount entries migrated from JSON to Room")

            // Supprimer le fichier JSON après migration réussie
            deleteJsonFile()

        } catch (e: Exception) {
            Log.e(TAG, "Error during JSON to Room migration", e)
        }
    }

    /**
     * Obtient le fichier JSON (pour migration uniquement)
     */
    private fun getJsonFile(): File {
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        return File(musicDir, JSON_FILENAME)
    }

    /**
     * Supprime le fichier JSON après migration
     */
    private fun deleteJsonFile() {
        try {
            val file = getJsonFile()
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "Deleted JSON file after migration to Room")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not delete JSON file", e)
        }
    }

    // ========== Méthodes de compatibilité (appelées par l'ancien code) ==========

    /**
     * Charge les play counts - maintenant juste un alias pour refreshCache.
     * Gardé pour compatibilité avec le code existant.
     */
    fun loadPlayCounts() {
        // L'init est maintenant gérée séparément
        // Cette méthode ne fait rien car le cache est pré-chargé dans init()
        if (!isInitialized) {
            Log.w(TAG, "loadPlayCounts called but manager not initialized")
        }
    }

    /**
     * Vérifie si les play counts sont chargés.
     * Gardé pour compatibilité - retourne true si initialisé.
     */
    fun isLoaded(): Boolean = isInitialized

    /**
     * Sauvegarde asynchrone - maintenant no-op car Room sauvegarde automatiquement.
     */
    @Suppress("unused")
    fun savePlayCountsAsync() {
        // Room sauvegarde automatiquement, cette méthode est gardée pour compatibilité
    }
}
