package com.Atom2Universe.app.pixelart.data

import android.content.Context
import com.Atom2Universe.app.pixelart.canvas.CanvasHistoryManager
import com.Atom2Universe.app.pixelart.canvas.CanvasObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Repository pour gérer la persistance des projets Canvas (Toile Infinie)
 * Implémente une sauvegarde automatique débounced pour éviter les écritures excessives
 */
class CanvasRepository(context: Context) {

    private val database = PixelArtDatabase.getInstance(context)
    private val dao = database.canvasDao()

    // HistoryManager pour la sérialisation des objets
    private val historyManager = CanvasHistoryManager(context)

    // Coroutine scope pour les opérations asynchrones
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Job pour la sauvegarde débounced
    private var autoSaveJob: Job? = null

    // ID du projet actuellement ouvert
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
     * Appelée à chaque modification du canvas
     *
     * @param objects Liste des objets du canvas
     * @param viewportX Position X du viewport
     * @param viewportY Position Y du viewport
     * @param viewportZoom Niveau de zoom du viewport
     */
    fun triggerAutoSave(
        objects: List<CanvasObject>,
        viewportX: Float,
        viewportY: Float,
        viewportZoom: Float
    ) {
        // Annuler le job précédent si en cours
        autoSaveJob?.cancel()

        // Lancer un nouveau job avec délai
        autoSaveJob = repositoryScope.launch {
            delay(AUTO_SAVE_DELAY_MS)
            try {
                saveProjectInternal(objects, viewportX, viewportY, viewportZoom)
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
        objects: List<CanvasObject>,
        viewportX: Float,
        viewportY: Float,
        viewportZoom: Float
    ) {
        // Annuler tout job en cours
        autoSaveJob?.cancel()

        withContext(Dispatchers.IO) {
            saveProjectInternal(objects, viewportX, viewportY, viewportZoom)
        }
    }

    /**
     * Logique interne de sauvegarde
     */
    private suspend fun saveProjectInternal(
        objects: List<CanvasObject>,
        viewportX: Float,
        viewportY: Float,
        viewportZoom: Float
    ) {
        val now = System.currentTimeMillis()
        val objectsJson = historyManager.serializeAllObjects(objects)

        if (currentProjectId == 0L) {
            // Créer un nouveau projet
            val project = CanvasProject(
                viewportX = viewportX,
                viewportY = viewportY,
                viewportZoom = viewportZoom,
                objectsJson = objectsJson,
                dateCreated = now,
                dateModified = now
            )
            currentProjectId = dao.insertProject(project)
        } else {
            // Mettre à jour le projet existant
            dao.updateProjectData(
                projectId = currentProjectId,
                viewportX = viewportX,
                viewportY = viewportY,
                viewportZoom = viewportZoom,
                objectsJson = objectsJson,
                timestamp = now
            )
        }
    }

    // ===== CHARGEMENT =====

    /**
     * Charge le dernier projet modifié (pour auto-load au démarrage)
     */
    suspend fun loadLastProject(): LoadedCanvasProject? {
        return withContext(Dispatchers.IO) {
            val project = dao.getLastModifiedProject() ?: return@withContext null
            currentProjectId = project.id

            val objects = historyManager.deserializeAllObjects(project.objectsJson)

            LoadedCanvasProject(
                projectId = project.id,
                viewportX = project.viewportX,
                viewportY = project.viewportY,
                viewportZoom = project.viewportZoom,
                objects = objects
            )
        }
    }

    /**
     * Charge un projet spécifique par ID
     */
    suspend fun loadProject(projectId: Long): LoadedCanvasProject? {
        return withContext(Dispatchers.IO) {
            val project = dao.getProjectById(projectId) ?: return@withContext null
            currentProjectId = projectId

            val objects = historyManager.deserializeAllObjects(project.objectsJson)

            LoadedCanvasProject(
                projectId = project.id,
                viewportX = project.viewportX,
                viewportY = project.viewportY,
                viewportZoom = project.viewportZoom,
                objects = objects
            )
        }
    }

    /**
     * Crée un nouveau projet vide
     */
    suspend fun createNewProject(): Long {
        return withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val project = CanvasProject(
                viewportX = 0f,
                viewportY = 0f,
                viewportZoom = 1f,
                objectsJson = "[]",
                dateCreated = now,
                dateModified = now
            )

            currentProjectId = dao.insertProject(project)
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

    // ===== NETTOYAGE =====

    /**
     * Annule tous les jobs en cours
     */
    fun cancel() {
        autoSaveJob?.cancel()
    }
}

/**
 * Données d'un projet Canvas chargé
 */
data class LoadedCanvasProject(
    val projectId: Long,
    val viewportX: Float,
    val viewportY: Float,
    val viewportZoom: Float,
    val objects: List<CanvasObject>
)
