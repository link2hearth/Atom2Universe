package com.Atom2Universe.app.music

import android.content.Context
import android.media.MediaScannerConnection
import android.util.Log
import com.Atom2Universe.app.music.data.MusicDatabase
import com.Atom2Universe.app.music.data.PendingPopmUpdate
import com.Atom2Universe.app.music.data.PendingPopmUpdateDao
import com.Atom2Universe.app.music.model.MusicTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.id3.AbstractID3v2Frame
import org.jaudiotagger.tag.id3.AbstractID3v2Tag
import org.jaudiotagger.tag.id3.ID3v22Tag
import org.jaudiotagger.tag.id3.ID3v23Frame
import org.jaudiotagger.tag.id3.ID3v23Tag
import org.jaudiotagger.tag.id3.ID3v24Frames
import org.jaudiotagger.tag.id3.AbstractTag
import org.jaudiotagger.tag.id3.framebody.FrameBodyPOPM
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Gestionnaire de synchronisation des tags POPM.
 *
 * Ce manager gère un cache persistant (Room) des modifications à appliquer aux fichiers MP3.
 * Les modifications sont appliquées dès que le fichier n'est plus en cours de lecture.
 *
 * Workflow:
 * 1. Quand on veut modifier un tag POPM (play count ou rating), on appelle queueUpdate()
 * 2. La modification est stockée dans Room
 * 3. Si le fichier n'est pas en cours de lecture, on essaie de l'appliquer immédiatement
 * 4. Sinon, on attend que le fichier change (via onTrackChanged)
 * 5. Au démarrage de l'app, on traite toutes les modifications en attente
 */
object MusicPopmSyncManager {

    private const val TAG = "MusicPopmSyncManager"
    private const val POPM_EMAIL = "free@app"
    private const val MAX_RETRY_ATTEMPTS = 5

    // Limites de validation pour les valeurs POPM
    private const val POPM_MAX_REASONABLE = 100_000L  // Refuse si > 100k (probablement corrompu)

    private lateinit var dao: PendingPopmUpdateDao
    private lateinit var appContext: Context
    @Volatile
    private var isInitialized = false

    // CoroutineScope avec SupervisorJob pour permettre la cancellation
    private var supervisorJob = SupervisorJob()
    private var scope = CoroutineScope(supervisorJob + Dispatchers.IO)

    // Chemin du fichier actuellement en lecture (ne pas modifier ce fichier)
    @Volatile
    private var currentlyPlayingFilePath: String? = null

    // Bug 5.17: Per-file mutexes to serialize updates to the same file
    // This prevents race conditions when multiple updates are queued for the same file
    private val fileMutexes = java.util.concurrent.ConcurrentHashMap<String, Mutex>()

    /**
     * Gets or creates a mutex for the given file path.
     * Bug 5.17: Ensures serialized access to each file.
     */
    private fun getMutexForFile(filePath: String): Mutex {
        return fileMutexes.getOrPut(filePath) { Mutex() }
    }

    /**
     * Cleans up unused mutexes to prevent memory leaks.
     * Called after processing updates.
     * Bug fix: Utilise compute() pour une suppression atomique thread-safe.
     */
    private fun cleanupFileMutex(filePath: String) {
        // Suppression atomique: ne retire le mutex que s'il n'est pas verrouillé
        // compute() est thread-safe et garantit l'atomicité de l'opération
        fileMutexes.compute(filePath) { _, mutex ->
            if (mutex != null && !mutex.isLocked) {
                null  // Supprime l'entrée
            } else {
                mutex  // Garde l'entrée
            }
        }
    }

    init {
        // Désactive les logs verbeux de JAudioTagger
        try {
            Logger.getLogger("org.jaudiotagger").level = Level.OFF
        } catch (_: Exception) {
            // Ignore
        }
    }

    /**
     * Initialise le manager avec le contexte de l'application.
     * Doit être appelé au démarrage de l'app.
     * Thread-safe: utilise synchronized pour éviter les race conditions.
     */
    @Synchronized
    fun init(context: Context) {
        // Double-checked locking pattern
        if (isInitialized) return

        appContext = context.applicationContext
        dao = MusicDatabase.getInstance(appContext).pendingPopmUpdateDao()
        isInitialized = true

        Log.d(TAG, "MusicPopmSyncManager initialized")

        // Traiter les modifications en attente au démarrage
        scope.launch {
            processPendingUpdates()
        }
    }

    /**
     * Libère les ressources et annule les jobs en cours.
     * Appelé lors de la fermeture de l'app.
     */
    fun release() {
        supervisorJob.cancel()
        supervisorJob = SupervisorJob()
        scope = CoroutineScope(supervisorJob + Dispatchers.IO)
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
        // Vérifier isInitialized avant d'accéder au dao via processUpdateForFile
        if (isInitialized && previousPath != null && previousPath != filePath) {
            scope.launch {
                processUpdateForFile(previousPath)
            }
        }
    }

    /**
     * Enregistre une modification de play count à appliquer.
     */
    suspend fun queuePlayCountIncrement(track: MusicTrack) {
        if (!isInitialized) {
            Log.w(TAG, "Manager not initialized, skipping play count increment")
            return
        }

        val filePath = track.filePath ?: return

        Log.d(TAG, "Queueing play count increment for: ${track.title}")

        withContext(Dispatchers.IO) {
            val existing = dao.getByFilePath(filePath)
            if (existing != null) {
                // Incrémenter le delta existant
                dao.incrementPlayCountDelta(filePath, 1)
            } else {
                // Créer une nouvelle entrée
                dao.insert(PendingPopmUpdate(
                    filePath = filePath,
                    trackId = track.id,
                    playCountDelta = 1
                ))
            }

            // Essayer d'appliquer si le fichier n'est pas en lecture
            if (filePath != currentlyPlayingFilePath) {
                processUpdateForFile(filePath)
            }
        }
    }

    /**
     * Enregistre une modification de rating à appliquer.
     */
    suspend fun queueRatingUpdate(track: MusicTrack, rating: Long) {
        if (!isInitialized) {
            Log.w(TAG, "Manager not initialized, skipping rating update")
            return
        }

        val filePath = track.filePath ?: return

        Log.d(TAG, "Queueing rating update for: ${track.title} -> $rating")

        withContext(Dispatchers.IO) {
            val existing = dao.getByFilePath(filePath)
            if (existing != null) {
                // Mettre à jour le rating
                dao.updateRating(filePath, rating)
            } else {
                // Créer une nouvelle entrée
                dao.insert(PendingPopmUpdate(
                    filePath = filePath,
                    trackId = track.id,
                    newRating = rating
                ))
            }

            // Essayer d'appliquer si le fichier n'est pas en lecture
            if (filePath != currentlyPlayingFilePath) {
                processUpdateForFile(filePath)
            }
        }
    }

    /**
     * Traite toutes les modifications en attente (sauf le fichier en cours de lecture).
     */
    suspend fun processPendingUpdates() {
        if (!isInitialized) return

        withContext(Dispatchers.IO) {
            val currentFile = currentlyPlayingFilePath
            val pending = if (currentFile != null) {
                dao.getAllExcept(currentFile)
            } else {
                dao.getAll()
            }

            Log.d(TAG, "Processing ${pending.size} pending updates")

            for (update in pending) {
                processUpdateForFile(update.filePath)
            }
        }
    }

    /**
     * Traite une modification pour un fichier spécifique.
     * Utilise le mutex global de MusicTagEditor pour éviter les conflits
     * avec LyricsSyncManager qui écrit les paroles.
     *
     * Bug 5.17: Uses per-file mutex to serialize updates to the same file.
     * This prevents race conditions when multiple updates are queued for the same file.
     *
     * IMPORTANT: Pour éviter les deadlocks, les accès à la base de données
     * sont effectués AVANT l'acquisition du mutex fichier. L'ordre est:
     * 1. Acquisition per-file mutex (Bug 5.17)
     * 2. Lecture DB
     * 3. Acquisition fileWriteMutex (MusicTagEditor global)
     * 4. Écriture fichier
     * 5. Libération fileWriteMutex
     * 6. Mise à jour DB
     * 7. Libération per-file mutex
     */
    private suspend fun processUpdateForFile(filePath: String) {
        Log.d(TAG, "processUpdateForFile called for: $filePath")

        // Bug 5.17: Acquire per-file mutex to serialize updates to the same file
        val fileMutex = getMutexForFile(filePath)

        fileMutex.withLock {
            // ÉTAPE 1: Lecture DB (inside per-file mutex to ensure consistency)
            val update = dao.getByFilePath(filePath)
            if (update == null) {
                Log.d(TAG, "No pending update found in DB for: $filePath")
                return@withLock
            }

            Log.d(TAG, "Found pending update: delta=${update.playCountDelta}, attempts=${update.attemptCount}")

            // Ne pas traiter si le fichier est en cours de lecture
            if (filePath == currentlyPlayingFilePath) {
                Log.d(TAG, "Skipping update for currently playing file: $filePath")
                return@withLock
            }

            // Vérifier le nombre de tentatives
            if (update.attemptCount >= MAX_RETRY_ATTEMPTS) {
                Log.w(TAG, "Max retry attempts reached for: $filePath, removing from queue")
                dao.deleteByFilePath(filePath)
                return@withLock
            }

            // ÉTAPE 2: Écriture fichier avec mutex global (pas d'accès DB à l'intérieur)
            val success: Boolean
            try {
                success = MusicTagEditor.fileWriteMutex.withLock {
                    applyPopmUpdate(update)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error applying POPM update to: $filePath", e)
                // ÉTAPE 3: Mise à jour DB APRÈS libération du mutex global
                dao.updateAttemptInfo(filePath, System.currentTimeMillis())
                return@withLock
            }

            // ÉTAPE 3: Mise à jour DB APRÈS libération du mutex global
            if (success) {
                Log.d(TAG, "Successfully applied POPM update to: $filePath")
                dao.deleteByFilePath(filePath)
            } else {
                Log.w(TAG, "Failed to apply POPM update to: $filePath")
                dao.updateAttemptInfo(filePath, System.currentTimeMillis())
            }
        }

        // Bug 5.17: Clean up unused mutex
        cleanupFileMutex(filePath)
    }

    /**
     * Applique une modification POPM à un fichier.
     *
     * RÈGLE FONDAMENTALE: Ne jamais décrémenter un compteur.
     * La valeur finale est le MAX entre:
     * 1. La valeur actuelle du POPM dans le fichier
     * 2. La valeur du JSON (MusicPlayCountManager)
     * 3. La valeur calculée (POPM + delta)
     *
     * IMPORTANT: Cette méthode ne touche qu'à notre frame POPM (email = "free@app").
     * Les frames POPM d'autres applications (Windows Media Player, iTunes, etc.)
     * sont préservés intacts.
     *
     * Retourne true si la modification a été appliquée avec succès.
     */
    private fun applyPopmUpdate(update: PendingPopmUpdate): Boolean {
        // Vérifier si l'écriture des tags est activée
        val preferences = MusicPreferences.getInstance(appContext)
        val shouldWriteToFile = preferences.writeTagsToFiles

        if (!shouldWriteToFile) {
            Log.d(TAG, "Tag writing disabled, skipping file write but updating cache/Room for: ${update.filePath}")
        }

        val file = File(update.filePath)
        if (!file.exists() || !file.canWrite()) {
            Log.w(TAG, "File does not exist or is not writable: ${update.filePath}")
            return false
        }

        return try {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault

            // Convertir en ID3v2.3 si nécessaire
            // ID3v2.2 utilise des frame IDs de 3 bytes, incompatible avec POPM (4 bytes)
            val id3v2Tag: AbstractID3v2Tag = when (tag) {
                is ID3v22Tag -> {
                    // Upgrader ID3v2.2 vers ID3v2.3
                    Log.d(TAG, "Upgrading ID3v2.2 to ID3v2.3 for: ${update.filePath}")
                    val newTag = ID3v23Tag(tag as AbstractTag)
                    audioFile.tag = newTag
                    newTag
                }
                is AbstractID3v2Tag -> tag
                else -> {
                    // Créer un nouveau tag ID3v2.3 (pour ID3v1 ou pas de tag)
                    val newTag = ID3v23Tag()
                    audioFile.tag = newTag
                    newTag
                }
            }

            // Lire les valeurs actuelles de NOTRE frame POPM uniquement
            // Les frames des autres apps (WMP, iTunes, etc.) sont préservés mais ignorés
            // L'import initial depuis ces autres frames se fait lors du scan complet
            // (MusicStatsManager.loadStatsForTracks)
            var ourPopmPlayCount = 0L
            var ourPopmRating = 0L
            val framesToPreserve = mutableListOf<AbstractID3v2Frame>()

            val existingFrames = id3v2Tag.getFrame(ID3v24Frames.FRAME_ID_POPULARIMETER)
            if (existingFrames != null) {
                val frameList = when (existingFrames) {
                    is List<*> -> existingFrames.filterIsInstance<AbstractID3v2Frame>()
                    is AbstractID3v2Frame -> listOf(existingFrames)
                    else -> emptyList()
                }

                for (frame in frameList) {
                    val body = frame.body
                    if (body is FrameBodyPOPM) {
                        val email = body.emailToUser ?: ""
                        val rawCounter = body.counter

                        if (email == POPM_EMAIL) {
                            // C'est notre frame : lire ses valeurs pour la mise à jour
                            if (rawCounter <= POPM_MAX_REASONABLE) {
                                ourPopmPlayCount = rawCounter
                                ourPopmRating = body.rating
                            } else {
                                Log.w(TAG, "Our POPM counter corrupted ($rawCounter) in ${update.filePath} - resetting")
                            }
                        } else {
                            // Frame d'une autre application : préserver tel quel, ignorer ses valeurs
                            framesToPreserve.add(frame)
                            Log.d(TAG, "Preserving POPM frame from '$email' (counter=$rawCounter) - not used for calculation")
                        }
                    }
                }
            }

            // Récupérer les métadonnées directement depuis les tags ID3 du fichier
            // Cela évite de dépendre de MusicLibrary qui peut ne pas être chargé
            val title = audioFile.tag?.getFirst(org.jaudiotagger.tag.FieldKey.TITLE)?.trim() ?: ""
            val artist = audioFile.tag?.getFirst(org.jaudiotagger.tag.FieldKey.ARTIST)?.trim() ?: ""
            val album = audioFile.tag?.getFirst(org.jaudiotagger.tag.FieldKey.ALBUM)?.trim() ?: ""

            // Générer la clé de métadonnées pour accéder au play count dans Room
            val metadataKey = MusicPlayCountManager.generateMetadataKey(title, artist, album)

            var jsonPlayCount = MusicPlayCountManager.getPlayCountByKey(metadataKey)

            // Validation: si la valeur JSON est corrompue, la réinitialiser
            if (jsonPlayCount > POPM_MAX_REASONABLE) {
                Log.w(TAG, "JSON play count corrupted ($jsonPlayCount) for $title - resetting to 0")
                jsonPlayCount = 0L
                // Corriger aussi dans Room via la clé
                scope.launch {
                    MusicPlayCountManager.resetPlayCountByKey(metadataKey)
                }
            }

            // Calculer la valeur avec delta (basé uniquement sur NOTRE frame)
            val calculatedPlayCount = ourPopmPlayCount + update.playCountDelta

            // RÈGLE MAX: Prendre la plus grande valeur entre notre POPM et le JSON
            // Ne JAMAIS décrémenter
            // Note: les frames WMP/iTunes sont ignorés ici, leur import se fait au scan complet
            val newPlayCount = maxOf(ourPopmPlayCount, jsonPlayCount, calculatedPlayCount)
            val newRating = update.newRating ?: ourPopmRating

            Log.d(TAG, "Updating POPM: ourPopm=$ourPopmPlayCount, json=$jsonPlayCount, calc=$calculatedPlayCount -> final=$newPlayCount, rating $ourPopmRating -> $newRating")

            // Créer notre nouveau frame POPM
            val popmBody = FrameBodyPOPM(POPM_EMAIL, newRating, newPlayCount)
            val popmFrame = ID3v23Frame(ID3v24Frames.FRAME_ID_POPULARIMETER)
            popmFrame.body = popmBody

            // Supprimer tous les frames POPM existants
            id3v2Tag.removeFrame(ID3v24Frames.FRAME_ID_POPULARIMETER)

            // Reconstruire la liste complète : frames préservés + notre frame
            if (framesToPreserve.isEmpty()) {
                // Cas simple : un seul frame (le nôtre)
                id3v2Tag.setFrame(popmFrame)
            } else {
                // Cas multiple : préserver les autres + ajouter le nôtre
                val allFrames = framesToPreserve.toMutableList()
                allFrames.add(popmFrame)
                id3v2Tag.setFrame(ID3v24Frames.FRAME_ID_POPULARIMETER, allFrames)
            }

            Log.d(TAG, "Saved ${framesToPreserve.size} other POPM frames + our frame")

            // Sauvegarder dans le fichier seulement si l'option est activée
            if (shouldWriteToFile) {
                audioFile.commit()

                // Forcer la mise à jour du timestamp
                file.setLastModified(System.currentTimeMillis())

                // Notifier le MediaStore
                MediaScannerConnection.scanFile(
                    appContext,
                    arrayOf(update.filePath),
                    arrayOf("audio/mpeg"),
                    null
                )
            } else {
                Log.d(TAG, "Skipped writing to file, tag writing is disabled")
            }

            // Mettre à jour le cache en mémoire de MusicStatsManager
            MusicStatsManager.updateCacheDirectly(update.trackId, newPlayCount, newRating)

            // Synchroniser Room si le POPM final est supérieur
            if (newPlayCount > jsonPlayCount) {
                scope.launch {
                    MusicPlayCountManager.syncFromPopmByKey(metadataKey, newPlayCount)
                }
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error writing POPM tags to file: ${update.filePath}", e)
            false
        }
    }

    // Lock pour protéger les modifications de currentlyPlayingFilePath dans forceProcessAll
    private val forceProcessLock = Any()

    /**
     * Force le traitement de toutes les modifications en attente.
     * Utile quand l'app se met en pause ou se ferme.
     * Bug fix: Utilisation d'un lock pour éviter les race conditions sur currentlyPlayingFilePath.
     */
    fun forceProcessAll() {
        // Vérifier isInitialized avant d'accéder au dao
        if (!isInitialized) return

        scope.launch {
            synchronized(forceProcessLock) {
                // Temporairement ignorer le fichier en cours de lecture
                val savedPath = currentlyPlayingFilePath
                currentlyPlayingFilePath = null

                // processPendingUpdates est suspendu, on doit l'appeler hors du synchronized
                // pour éviter de bloquer trop longtemps
                kotlinx.coroutines.runBlocking {
                    processPendingUpdates()
                }

                // Restaurer seulement si aucun autre thread n'a changé la valeur entre temps
                // On vérifie que c'est toujours null (pas modifié par setCurrentlyPlayingFile)
                if (currentlyPlayingFilePath == null) {
                    currentlyPlayingFilePath = savedPath
                }
            }
        }
    }

    /**
     * Retourne le nombre de modifications en attente.
     */
    @Suppress("unused")
    suspend fun getPendingCount(): Int {
        if (!isInitialized) return 0
        return withContext(Dispatchers.IO) {
            dao.count()
        }
    }
}
