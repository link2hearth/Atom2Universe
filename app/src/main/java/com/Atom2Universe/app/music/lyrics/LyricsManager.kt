package com.Atom2Universe.app.music.lyrics

import android.content.Context
import android.util.Log
import com.Atom2Universe.app.music.MusicPreferences
import com.Atom2Universe.app.music.MusicTagEditor
import com.Atom2Universe.app.music.data.MusicDatabase
import com.Atom2Universe.app.music.lyrics.api.AlternativeLyrics
import com.Atom2Universe.app.music.lyrics.api.LyricsApiConfig
import com.Atom2Universe.app.music.lyrics.api.LyricsRepository
import com.Atom2Universe.app.music.lyrics.api.LyricsResult
import com.Atom2Universe.app.music.lyrics.data.LyricsEntity
import com.Atom2Universe.app.music.model.MusicTrack
import com.Atom2Universe.app.music.sync.CloudSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gestionnaire principal pour les opérations de paroles.
 * Facade qui coordonne le cache, les tags de fichiers, les APIs en ligne, et la synchronisation.
 *
 * Supporte :
 * - Lecture depuis cache ou fichier
 * - Recherche automatique via APIs configurées par l'utilisateur
 * - Sauvegarde dans cache + queue d'écriture async
 * - Import manuel
 */
object LyricsManager {

    private const val TAG = "LyricsManager"
    private lateinit var appContext: Context
    private var isInitialized = false

    /**
     * Crée un repository avec les URLs configurées par l'utilisateur.
     * Appelé à chaque recherche pour prendre en compte les changements de configuration.
     */
    private fun createRepository(): LyricsRepository {
        val prefs = MusicPreferences.getInstance(appContext)
        val primaryConfig = LyricsApiConfig.fromUserInput(
            rawUrl = prefs.lyricsApiPrimary,
            headersText = prefs.lyricsApiPrimaryHeaders,
            lyricsPath = prefs.lyricsApiPrimaryLyricsPath,
            syncedLyricsPath = prefs.lyricsApiPrimarySyncedPath,
            isPrimary = true
        )
        val fallbackConfig = LyricsApiConfig.fromUserInput(
            rawUrl = prefs.lyricsApiFallback,
            headersText = prefs.lyricsApiFallbackHeaders,
            lyricsPath = prefs.lyricsApiFallbackLyricsPath,
            syncedLyricsPath = prefs.lyricsApiFallbackSyncedPath,
            isPrimary = false
        )
        return LyricsRepository(
            primaryConfig = primaryConfig,
            fallbackConfig = fallbackConfig,
            timeoutMs = 20000
        )
    }

    /**
     * Vérifie si des APIs de paroles sont configurées.
     */
    fun hasConfiguredApis(): Boolean {
        if (!isInitialized) return false
        val prefs = MusicPreferences.getInstance(appContext)
        return prefs.lyricsApiPrimary.isNotBlank() || prefs.lyricsApiFallback.isNotBlank()
    }

    /**
     * Initialise le manager.
     * Doit être appelé au démarrage de l'application.
     */
    fun init(context: Context) {
        if (isInitialized) return

        appContext = context.applicationContext
        LyricsSyncManager.init(appContext)
        LyricsAutoFetchManager.init(appContext)

        isInitialized = true
        Log.d(TAG, "LyricsManager initialized")
    }

    /**
     * Récupère les paroles pour un track.
     * Vérifie d'abord le cache, puis lit depuis le tag du fichier.
     * Ne fait pas de requête réseau.
     */
    suspend fun getLyrics(track: MusicTrack): String? = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            Log.w(TAG, "Manager not initialized")
            return@withContext null
        }

        val database = MusicDatabase.getInstance(appContext)
        val dao = database.lyricsDao()

        // Vérifier d'abord le cache
        val key = generateMetadataKey(track)
        val cached = dao.getByKey(key)
        if (cached != null && !cached.noLyricsFound) {
            val lyricsContent = cached.lyrics
            val isHtml = lyricsContent.trimStart().startsWith("<!DOCTYPE", ignoreCase = true) ||
                lyricsContent.trimStart().startsWith("<html", ignoreCase = true)
            if (isHtml) {
                Log.w(TAG, "HTML détecté dans le cache pour: ${track.title}, entrée ignorée")
                // On passe à la suite (tags fichier) plutôt que de retourner du HTML
            } else {
                Log.d(TAG, "Found lyrics in cache for: ${track.title}")
                return@withContext lyricsContent
            }
        }
        // Si noLyricsFound == true, on ignore l'entrée et on essaie quand même
        // les tags du fichier (au cas où les paroles auraient été ajoutées manuellement).

        // Essayer de lire depuis le tag du fichier
        val fromFile = MusicTagEditor.readLyrics(track)
        if (fromFile != null) {
            Log.d(TAG, "Found lyrics in file tag for: ${track.title}")

            // Cacher le résultat (remplace aussi un éventuel marqueur noLyricsFound)
            dao.insert(LyricsEntity(
                metadataKey = key,
                trackId = track.id,
                lyrics = fromFile,
                source = "file",
                isSynced = false,
                isSyncedToFile = true
            ))

            return@withContext fromFile
        }

        Log.d(TAG, "No lyrics found for: ${track.title}")
        null
    }

    /**
     * Recherche des paroles en ligne via les APIs configurées.
     * Si succès, cache automatiquement le résultat.
     * Retourne NotFound si aucune API n'est configurée.
     */
    suspend fun fetchLyricsOnline(track: MusicTrack, forceRefresh: Boolean = false): LyricsResult = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            Log.w(TAG, "Manager not initialized")
            return@withContext LyricsResult.Error("Manager not initialized", "manager")
        }

        // Vérifier si des APIs sont configurées
        if (!hasConfiguredApis()) {
            Log.d(TAG, "No lyrics APIs configured - cannot search online")
            return@withContext LyricsResult.NotFound
        }

        val database = MusicDatabase.getInstance(appContext)
        val dao = database.lyricsDao()
        val key = generateMetadataKey(track)

        // Vérifier le marqueur "pas de paroles" : si les APIs n'ont déjà rien trouvé
        // pour ce titre, inutile de refaire un appel réseau — mais seulement pendant 7 jours.
        // forceRefresh = true bypasse ce marqueur (recherche manuelle de l'utilisateur).
        if (!forceRefresh) {
            val cached = dao.getByKey(key)
            if (cached?.noLyricsFound == true) {
                val sevenDaysMs = 7 * 24 * 60 * 60 * 1000L
                val isStale = System.currentTimeMillis() - cached.fetchedAt > sevenDaysMs
                if (!isStale) {
                    Log.d(TAG, "Titre marqué sans paroles (skip, marqueur < 7j): ${track.title}")
                    return@withContext LyricsResult.NotFound
                }
                Log.d(TAG, "Marqueur noLyricsFound expiré (>7j), on réessaie: ${track.title}")
            }
        } else {
            Log.d(TAG, "forceRefresh=true, bypass du marqueur noLyricsFound: ${track.title}")
        }

        Log.d(TAG, "Fetching lyrics online for: ${track.title}")

        // Créer le repository avec les URLs actuelles
        val repository = createRepository()
        val result = repository.fetchLyrics(track)

        when {
            result is LyricsResult.Success -> {
                // Conserver la liste complète en mémoire (10 min) pour la navigation dans LyricsBottomSheet.
                // On stocke [meilleur résultat] + [alternatives] pour permettre la restauration de l'index
                // correct lors de la réouverture du panneau dans la fenêtre de 10 minutes.
                if (result.alternatives.isNotEmpty()) {
                    val fullList = buildList {
                        add(AlternativeLyrics(result.lyrics, result.source, result.isSynced))
                        addAll(result.alternatives)
                    }
                    LyricsAlternativesCache.put(track.id, fullList)
                }
                // Succès : mettre en cache et planifier l'écriture dans le tag
                try {
                    dao.insert(LyricsEntity(
                        metadataKey = key,
                        trackId = track.id,
                        lyrics = result.lyrics,
                        source = result.source,
                        isSynced = result.isSynced,
                        isSyncedToFile = false
                    ))
                    LyricsSyncManager.queueLyricsUpdate(track, result.lyrics)
                    // Déclenche la sync cloud pour envoyer les nouvelles paroles sur Drive
                    CloudSyncManager.triggerDebouncedSync()
                    Log.d(TAG, "Cached lyrics from ${result.source}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error caching lyrics: ${e.message}", e)
                }
            }
            result is LyricsResult.NotFound -> {
                // Aucune API n'a trouvé de paroles → stocker le marqueur pour éviter
                // de refaire des appels inutiles. Ce marqueur est interne à l'appli
                // et est effacé si l'utilisateur sauvegarde des paroles manuellement.
                try {
                    dao.insert(LyricsEntity(
                        metadataKey = key,
                        trackId = track.id,
                        lyrics = "",
                        source = "no_lyrics",
                        noLyricsFound = true
                    ))
                    Log.d(TAG, "Marqué sans paroles (no_lyrics): ${track.title}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error storing no_lyrics marker: ${e.message}", e)
                }
            }
            // RateLimited et Error sont temporaires → pas de marqueur, on réessaiera
        }

        result
    }

    /**
     * Sauvegarde des paroles pour un track.
     * Les paroles sont mises en cache et ajoutées à la queue d'écriture asynchrone.
     */
    suspend fun saveLyrics(
        track: MusicTrack,
        lyrics: String,
        source: String = "manual"
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            Log.w(TAG, "Manager not initialized")
            return@withContext false
        }

        try {
            val database = MusicDatabase.getInstance(appContext)
            val dao = database.lyricsDao()

            // Sauvegarder dans le cache
            val entity = LyricsEntity(
                metadataKey = generateMetadataKey(track),
                trackId = track.id,
                lyrics = lyrics,
                source = source,
                isSynced = false,
                isSyncedToFile = false
            )
            dao.insert(entity)

            // Mettre en queue pour écriture asynchrone dans le tag
            LyricsSyncManager.queueLyricsUpdate(track, lyrics)

            // Trigger debounced cloud sync
            CloudSyncManager.triggerDebouncedSync()

            Log.d(TAG, "Saved lyrics for: ${track.title}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving lyrics: ${e.message}", e)
            false
        }
    }

    /**
     * Notifie le manager du fichier actuellement en lecture.
     * Utilisé pour éviter d'écrire dans un fichier verrouillé par le lecteur.
     */
    fun setCurrentlyPlayingFile(filePath: String?) {
        if (isInitialized) {
            LyricsSyncManager.setCurrentlyPlayingFile(filePath)
        }
    }

    /**
     * Génère la clé de métadonnées unique pour un track.
     * Format : "title-artist-album" en minuscules.
     */
    private fun generateMetadataKey(track: MusicTrack): String {
        return "${track.title.lowercase()}-${track.artist.lowercase()}-${track.album.lowercase()}"
    }
}
