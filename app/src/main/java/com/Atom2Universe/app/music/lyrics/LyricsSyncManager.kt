package com.Atom2Universe.app.music.lyrics

import android.content.Context
import android.util.Log
import com.Atom2Universe.app.music.MusicLibrary
import com.Atom2Universe.app.music.MusicTagEditor
import com.Atom2Universe.app.music.data.MusicDatabase
import com.Atom2Universe.app.music.lyrics.data.PendingLyricsUpdate
import com.Atom2Universe.app.music.lyrics.data.PendingLyricsUpdateDao
import com.Atom2Universe.app.music.model.MusicTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Gestionnaire de synchronisation des paroles vers les tags USLT.
 *
 * Ce manager gère un cache persistant (Room) des paroles à écrire dans les fichiers MP3.
 * Les écritures sont appliquées dès que le fichier n'est plus en cours de lecture.
 *
 * Workflow:
 * 1. Quand on veut écrire des paroles, on appelle queueLyricsUpdate()
 * 2. La modification est stockée dans Room
 * 3. Si le fichier n'est pas en lecture, on essaie de l'appliquer immédiatement
 * 4. Sinon, on attend que le fichier change (via setCurrentlyPlayingFile)
 * 5. Au démarrage de l'app, on traite toutes les modifications en attente
 */
object LyricsSyncManager {

    private const val TAG = "LyricsSyncManager"
    private const val MAX_RETRY_ATTEMPTS = 3

    private lateinit var dao: PendingLyricsUpdateDao
    private lateinit var appContext: Context
    private var isInitialized = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Chemin du fichier actuellement en lecture (ne pas modifier ce fichier)
    @Volatile
    private var currentlyPlayingFilePath: String? = null

    /**
     * Initialise le manager avec le contexte de l'application.
     * Doit être appelé au démarrage de l'app.
     */
    fun init(context: Context) {
        if (isInitialized) return

        appContext = context.applicationContext
        dao = MusicDatabase.getInstance(appContext).pendingLyricsUpdateDao()
        isInitialized = true

        Log.d(TAG, "LyricsSyncManager initialized")

        // Traiter les modifications en attente au démarrage
        scope.launch {
            processPendingUpdates()
        }
    }

    /**
     * Met à jour le chemin du fichier actuellement en lecture.
     * Appelé par MusicPlaybackHolder quand le track change.
     */
    fun setCurrentlyPlayingFile(filePath: String?) {
        val previousPath = currentlyPlayingFilePath
        currentlyPlayingFilePath = filePath

        Log.d(TAG, "Currently playing file changed: $previousPath -> $filePath")

        // Si un fichier précédent n'est plus en lecture, essayer d'appliquer ses modifications
        if (previousPath != null && previousPath != filePath) {
            scope.launch {
                processUpdateForFile(previousPath)
            }
        }
    }

    /**
     * Enregistre une écriture de paroles à appliquer.
     */
    suspend fun queueLyricsUpdate(
        track: MusicTrack,
        lyrics: String,
        language: String = "eng",
        description: String = ""
    ) {
        if (!isInitialized) {
            Log.w(TAG, "Manager not initialized, skipping lyrics update")
            return
        }

        val filePath = track.filePath ?: return

        Log.d(TAG, "Queueing lyrics update for: ${track.title}")

        withContext(Dispatchers.IO) {
            dao.insert(PendingLyricsUpdate(
                filePath = filePath,
                trackId = track.id,
                lyrics = lyrics,
                language = language,
                description = description
            ))

            // Essayer d'appliquer si le fichier n'est pas en lecture
            if (filePath != currentlyPlayingFilePath) {
                processUpdateForFile(filePath)
            }
        }
    }

    /**
     * Traite toutes les modifications en attente.
     */
    private suspend fun processPendingUpdates() {
        if (!isInitialized) return

        withContext(Dispatchers.IO) {
            val currentFile = currentlyPlayingFilePath
            val pending = if (currentFile != null) {
                dao.getAllExcept(currentFile)
            } else {
                dao.getAll()
            }

            Log.d(TAG, "Processing ${pending.size} pending lyrics updates")

            for (update in pending) {
                processUpdateForFile(update.filePath)
            }
        }
    }

    /**
     * Traite une modification pour un fichier spécifique.
     * Note: Le mutex est géré par MusicTagEditor.writeLyrics(), pas besoin de le prendre ici.
     */
    private suspend fun processUpdateForFile(filePath: String) {
        val update = dao.getByFilePath(filePath) ?: return

        if (filePath == currentlyPlayingFilePath) {
            Log.d(TAG, "Skipping update for currently playing file: $filePath")
            return
        }

        if (update.attemptCount >= MAX_RETRY_ATTEMPTS) {
            Log.w(TAG, "Max retry attempts reached for: $filePath")
            dao.deleteByFilePath(filePath)
            return
        }

        try {
            val track = MusicLibrary.getTrackById(update.trackId)
                ?: MusicLibrary.getTrackByFilePath(filePath)

            if (track == null) {
                Log.w(TAG, "Track not found, removing from queue: $filePath")
                dao.deleteByFilePath(filePath)
                return
            }

            val success = MusicTagEditor.writeLyrics(
                appContext,
                track,
                update.lyrics,
                update.language,
                update.description
            )

            if (success) {
                Log.d(TAG, "Successfully wrote lyrics to: $filePath")
                dao.deleteByFilePath(filePath)

                // Mettre à jour le statut de sync dans le cache
                val lyricsDao = MusicDatabase.getInstance(appContext).lyricsDao()
                val key = generateMetadataKey(track)
                lyricsDao.updateSyncStatus(key, true)
            } else {
                Log.w(TAG, "Failed to write lyrics to: $filePath")
                dao.updateAttemptInfo(filePath, System.currentTimeMillis())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing lyrics to: $filePath", e)
            dao.updateAttemptInfo(filePath, System.currentTimeMillis())
        }
    }

    /**
     * Génère la clé de métadonnées pour un track.
     */
    private fun generateMetadataKey(track: MusicTrack): String {
        return "${track.title.lowercase()}-${track.artist.lowercase()}-${track.album.lowercase()}"
    }
}
