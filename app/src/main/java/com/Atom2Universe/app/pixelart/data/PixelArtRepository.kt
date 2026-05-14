package com.Atom2Universe.app.pixelart.data

import android.content.Context
import android.graphics.Color
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

/**
 * Repository pour gérer la persistance des projets pixel art
 * Implémente une sauvegarde automatique débounced pour éviter les écritures excessives
 */
class PixelArtRepository(context: Context) {

    private val database = PixelArtDatabase.getInstance(context)
    private val dao = database.pixelArtDao()

    // Coroutine scope pour les opérations asynchrones
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Job pour la sauvegarde débounced
    private var autoSaveJob: Job? = null

    // ID du projet actuellement ouvert (pour sauvegarde auto)
    private var currentProjectId: Long = 0L

    // Délai de debounce pour la sauvegarde auto (500ms)
    private val AUTO_SAVE_DELAY_MS = 500L

    // Callback pour notifier les erreurs
    var onSaveError: ((Exception) -> Unit)? = null

    // Callback pour notifier les sauvegardes réussies
    var onSaveSuccess: (() -> Unit)? = null

    // ===== GESTION DU PROJET COURANT =====

    /**
     * Définit le projet actuellement ouvert
     */
    fun setCurrentProjectId(projectId: Long) {
        currentProjectId = projectId
    }

    /**
     * Récupère l'ID du projet courant
     */
    fun getCurrentProjectId(): Long = currentProjectId

    // ===== SAUVEGARDE AUTOMATIQUE DÉBOUNCED =====

    /**
     * Déclenche une sauvegarde automatique débounced
     * Appelée à chaque action utilisateur (dessin, effacement, etc.)
     *
     * @param projectData Données du projet à sauvegarder (métadonnées + frames)
     */
    fun triggerAutoSave(
        canvasWidth: Int,
        canvasHeight: Int,
        fps: Int,
        primaryColor: Int,
        secondaryColor: Int,
        currentFrameIndex: Int,
        frames: List<FrameData>
    ) {
        // Annuler le job précédent si en cours
        autoSaveJob?.cancel()

        // Lancer un nouveau job avec délai
        autoSaveJob = repositoryScope.launch {
            delay(AUTO_SAVE_DELAY_MS)
            try {
                saveProjectInternal(
                    canvasWidth = canvasWidth,
                    canvasHeight = canvasHeight,
                    fps = fps,
                    primaryColor = primaryColor,
                    secondaryColor = secondaryColor,
                    currentFrameIndex = currentFrameIndex,
                    frames = frames
                )
                withContext(Dispatchers.Main) {
                    onSaveSuccess?.invoke()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onSaveError?.invoke(e)
                }
            }
        }
    }

    /**
     * Force une sauvegarde immédiate (sans débounce)
     * Utilisée lors du onPause() ou sortie de l'app
     */
    suspend fun saveImmediately(
        canvasWidth: Int,
        canvasHeight: Int,
        fps: Int,
        primaryColor: Int,
        secondaryColor: Int,
        currentFrameIndex: Int,
        frames: List<FrameData>
    ) {
        // Annuler tout job en cours
        autoSaveJob?.cancel()

        withContext(Dispatchers.IO) {
            saveProjectInternal(
                canvasWidth = canvasWidth,
                canvasHeight = canvasHeight,
                fps = fps,
                primaryColor = primaryColor,
                secondaryColor = secondaryColor,
                currentFrameIndex = currentFrameIndex,
                frames = frames
            )
        }
    }

    /**
     * Logique interne de sauvegarde
     */
    private suspend fun saveProjectInternal(
        canvasWidth: Int,
        canvasHeight: Int,
        fps: Int,
        primaryColor: Int,
        secondaryColor: Int,
        currentFrameIndex: Int,
        frames: List<FrameData>
    ) {
        val now = System.currentTimeMillis()

        // Créer ou mettre à jour le projet
        val project = if (currentProjectId == 0L) {
            PixelArtProject(
                canvasWidth = canvasWidth,
                canvasHeight = canvasHeight,
                fps = fps,
                primaryColor = primaryColor,
                secondaryColor = secondaryColor,
                currentFrameIndex = currentFrameIndex,
                dateCreated = now,
                dateModified = now
            )
        } else {
            PixelArtProject(
                id = currentProjectId,
                canvasWidth = canvasWidth,
                canvasHeight = canvasHeight,
                fps = fps,
                primaryColor = primaryColor,
                secondaryColor = secondaryColor,
                currentFrameIndex = currentFrameIndex,
                dateModified = now
            )
        }

        // Convertir les frames en entités
        val frameEntities = frames.mapIndexed { index, frameData ->
            PixelArtFrame(
                projectId = currentProjectId,
                frameIndex = index,
                pixelDataBase64 = encodePixelData(frameData.pixelData),
                duration = frameData.duration,
                dateModified = now
            )
        }

        // Sauvegarder avec transaction
        currentProjectId = dao.saveProjectWithFrames(project, frameEntities)
    }

    // ===== CHARGEMENT =====

    /**
     * Charge le dernier projet modifié (pour auto-load au démarrage)
     */
    suspend fun loadLastProject(): LoadedProject? {
        return withContext(Dispatchers.IO) {
            val project = dao.getLastModifiedProject() ?: return@withContext null
            currentProjectId = project.id
            val frames = dao.getFramesByProjectId(project.id)

            LoadedProject(
                projectId = project.id,
                canvasWidth = project.canvasWidth,
                canvasHeight = project.canvasHeight,
                fps = project.fps,
                primaryColor = project.primaryColor,
                secondaryColor = project.secondaryColor,
                currentFrameIndex = project.currentFrameIndex,
                frames = frames.map { frame ->
                    FrameData(
                        pixelData = decodePixelData(frame.pixelDataBase64),
                        duration = frame.duration
                    )
                }
            )
        }
    }

    /**
     * Charge un projet spécifique par ID
     */
    suspend fun loadProject(projectId: Long): LoadedProject? {
        return withContext(Dispatchers.IO) {
            val projectWithFrames = dao.getProjectWithFrames(projectId) ?: return@withContext null
            currentProjectId = projectId

            LoadedProject(
                projectId = projectWithFrames.project.id,
                canvasWidth = projectWithFrames.project.canvasWidth,
                canvasHeight = projectWithFrames.project.canvasHeight,
                fps = projectWithFrames.project.fps,
                primaryColor = projectWithFrames.project.primaryColor,
                secondaryColor = projectWithFrames.project.secondaryColor,
                currentFrameIndex = projectWithFrames.project.currentFrameIndex,
                frames = projectWithFrames.frames.map { frame ->
                    FrameData(
                        pixelData = decodePixelData(frame.pixelDataBase64),
                        duration = frame.duration
                    )
                }
            )
        }
    }

    /**
     * Récupère tous les projets (pour une future liste de projets)
     */
    fun getAllProjectsFlow(): Flow<List<PixelArtProject>> = dao.getAllProjectsFlow()

    /**
     * Crée un nouveau projet vide
     */
    suspend fun createNewProject(width: Int, height: Int): Long {
        return withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val project = PixelArtProject(
                canvasWidth = width,
                canvasHeight = height,
                fps = 10,
                primaryColor = Color.BLACK,
                secondaryColor = Color.WHITE,
                currentFrameIndex = 0,
                dateCreated = now,
                dateModified = now
            )

            // Créer une frame vide (transparente)
            val emptyPixelData = IntArray(width * height) { Color.TRANSPARENT }
            val frame = PixelArtFrame(
                projectId = 0,
                frameIndex = 0,
                pixelDataBase64 = encodePixelData(emptyPixelData),
                duration = 100,
                dateModified = now
            )

            currentProjectId = dao.saveProjectWithFrames(project, listOf(frame))
            currentProjectId
        }
    }

    /**
     * Supprime un projet
     */
    suspend fun deleteProject(projectId: Long) {
        withContext(Dispatchers.IO) {
            dao.deleteProjectById(projectId)
            if (currentProjectId == projectId) {
                currentProjectId = 0L
            }
        }
    }

    // ===== SAUVEGARDE D'UNE SEULE FRAME (optimisation) =====

    /**
     * Sauvegarde uniquement les données de pixels d'une frame
     * Plus efficace que de tout sauvegarder à chaque trait
     */
    fun saveFramePixelData(frameIndex: Int, pixelData: IntArray) {
        if (currentProjectId == 0L) return

        autoSaveJob?.cancel()
        autoSaveJob = repositoryScope.launch {
            delay(AUTO_SAVE_DELAY_MS)
            try {
                val base64 = encodePixelData(pixelData)
                dao.updateFramePixelData(currentProjectId, frameIndex, base64)
                dao.updateProjectTimestamp(currentProjectId)
                withContext(Dispatchers.Main) {
                    onSaveSuccess?.invoke()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onSaveError?.invoke(e)
                }
            }
        }
    }

    /**
     * Force la sauvegarde immédiate d'une frame
     */
    suspend fun saveFramePixelDataImmediately(frameIndex: Int, pixelData: IntArray) {
        if (currentProjectId == 0L) return

        autoSaveJob?.cancel()
        withContext(Dispatchers.IO) {
            val base64 = encodePixelData(pixelData)
            dao.updateFramePixelData(currentProjectId, frameIndex, base64)
            dao.updateProjectTimestamp(currentProjectId)
        }
    }

    // ===== ENCODAGE/DÉCODAGE =====

    /**
     * Encode un IntArray de pixels en Base64
     */
    private fun encodePixelData(data: IntArray): String {
        val buffer = ByteBuffer.allocate(data.size * 4)
        for (pixel in data) {
            buffer.putInt(pixel)
        }
        return Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
    }

    /**
     * Décode une chaîne Base64 en IntArray de pixels
     */
    private fun decodePixelData(encoded: String): IntArray {
        return try {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            val buffer = ByteBuffer.wrap(bytes)
            val data = IntArray(bytes.size / 4)
            for (i in data.indices) {
                data[i] = buffer.getInt()
            }
            data
        } catch (e: Exception) {
            // Retourner un tableau vide en cas d'erreur
            IntArray(0)
        }
    }

    // ===== NETTOYAGE =====

    /**
     * Annule tous les jobs en cours
     */
    fun cancel() {
        autoSaveJob?.cancel()
    }
}

/**
 * Données d'une frame pour la sauvegarde
 */
data class FrameData(
    val pixelData: IntArray,
    val duration: Int = 100
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FrameData) return false
        return pixelData.contentEquals(other.pixelData) && duration == other.duration
    }

    override fun hashCode(): Int {
        var result = pixelData.contentHashCode()
        result = 31 * result + duration
        return result
    }
}

/**
 * Données d'un projet chargé
 */
data class LoadedProject(
    val projectId: Long,
    val canvasWidth: Int,
    val canvasHeight: Int,
    val fps: Int,
    val primaryColor: Int,
    val secondaryColor: Int,
    val currentFrameIndex: Int,
    val frames: List<FrameData>
)
