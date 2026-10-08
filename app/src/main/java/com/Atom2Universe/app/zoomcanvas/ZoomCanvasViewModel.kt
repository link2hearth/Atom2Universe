package com.Atom2Universe.app.zoomcanvas

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Atom2Universe.app.zoomcanvas.core.LayerItem
import com.Atom2Universe.app.zoomcanvas.core.ZoomScene
import com.Atom2Universe.app.zoomcanvas.data.LoadedZoomProject
import com.Atom2Universe.app.zoomcanvas.data.ZoomDatabase
import com.Atom2Universe.app.zoomcanvas.data.ZoomStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

object ZoomCanvasStorage {
    @Volatile
    private var store: ZoomStore? = null

    fun store(context: Context): ZoomStore = store ?: synchronized(this) {
        store ?: ZoomStore(File(context.applicationContext.filesDir, "zoomcanvas/projects"), ZoomDatabase.get(context)).also { store = it }
    }

    /** Les écritures survivent à l'écran qui les a lancées (on quitte souvent juste après un trait). */
    val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val saveMutex = Mutex()

    /** Écritures lancées et pas finies : la galerie attend qu'il retombe à 0 avant de relire. */
    val writesInFlight = MutableStateFlow(0)
}

/**
 * Garde le projet ouvert à travers les rotations d'écran, et l'enregistre souvent : peu après
 * chaque trait, un peu plus tard après un simple déplacement de la vue, et tout de suite quand on
 * quitte l'écran.
 */
class ZoomCanvasViewModel(app: Application) : AndroidViewModel(app) {

    enum class State { LOADING, READY, FAILED }

    val store: ZoomStore = ZoomCanvasStorage.store(app)

    private val _state = MutableStateFlow(State.LOADING)
    val state: StateFlow<State> = _state

    var project: LoadedZoomProject? = null
        private set

    /** Fabrique la vignette (sur le fil principal, depuis la vue) au moment d'enregistrer. */
    var thumbnailProvider: (() -> Bitmap?)? = null

    private var saveJob: Job? = null
    private var savedContent = -1L
    private var savedCamera = -1L
    private var lastThumbAt = 0L

    fun open(id: String) {
        if (project?.meta?.id == id) { _state.value = State.READY; return }
        _state.value = State.LOADING
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                // À l'ouverture l'historique est vide : les images que plus rien n'utilise peuvent partir
                // (on les cherche dans la base : toutes les couches ne sont pas en mémoire).
                store.load(id)?.also { store.deleteUnusedImages(id, store.usedImageKeys(it.meta.pid)) }
            }
            if (loaded == null) { _state.value = State.FAILED; return@launch }
            project = loaded
            savedContent = loaded.scene.contentVersion
            savedCamera = loaded.scene.cameraVersion
            loaded.scene.pager = pager(loaded)
            loaded.scene.refreshResidency()
            _state.value = State.READY
        }
    }

    /** Appelé quand une couche vient d'arriver de la base : l'écran peut avoir quelque chose de plus à tracer. */
    var onLayersLoaded: (() -> Unit)? = null

    private val loadingLayers = HashSet<Long>()

    /**
     * Les couches qu'on ne voit pas ne sont pas en mémoire : la scène dit celles qu'elle veut bientôt, on les
     * lit hors du fil de l'écran et on les lui rend. Elle ne décharge jamais une couche pas encore écrite,
     * donc ce qu'on relit est toujours à jour.
     */
    private fun pager(p: LoadedZoomProject) = object : ZoomScene.LayerPager {
        override fun wantLoad(depth: Long) {
            if (!loadingLayers.add(depth)) return
            ZoomCanvasStorage.ioScope.launch {
                val items = try { store.loadLayerItems(p.meta.pid, depth) } catch (e: Exception) { null }
                withContext(Dispatchers.Main) {
                    loadingLayers.remove(depth)
                    if (items != null && project === p && p.scene.installLayer(depth, items)) onLayersLoaded?.invoke()
                }
            }
        }

        override fun loadNow(depth: Long): List<Pair<LayerItem, Long>>? =
            try { runBlocking(Dispatchers.IO) { store.loadLayerItems(p.meta.pid, depth) } } catch (e: Exception) { null }
    }

    /** Après un trait : on enregistre vite. Après un déplacement de la vue : un peu plus tard. */
    fun scheduleSave(contentChanged: Boolean) {
        if (contentChanged) {
            saveJob?.cancel()
            saveJob = viewModelScope.launch { delay(CONTENT_DELAY_MS); flush() }
        } else if (saveJob?.isActive != true) {
            saveJob = viewModelScope.launch { delay(CAMERA_DELAY_MS); flush() }
        }
    }

    /**
     * Écrit tout de suite ce qui a changé. Sur le fil de l'écran, on ne fait que prendre la liste des
     * changements (un trait posé = un trait) ; l'écriture elle-même se fait hors du fil principal.
     * La vignette (qui redessine la vue) est espacée, sauf si [force] : on quitte l'écran.
     */
    fun flush(force: Boolean = false) {
        val p = project ?: return
        saveJob?.cancel()
        val scene = p.scene
        val contentDirty = scene.contentVersion != savedContent || scene.hasUnsavedItems
        if (!contentDirty && scene.cameraVersion == savedCamera) return
        if (contentDirty) p.meta.modified = System.currentTimeMillis()
        val delta = scene.drainChanges()
        savedContent = scene.contentVersion
        savedCamera = scene.cameraVersion
        val now = System.currentTimeMillis()
        val wantThumb = contentDirty && (force || now - lastThumbAt >= THUMB_EVERY_MS)
        val thumb = if (wantThumb) thumbnailProvider?.invoke() else null
        if (thumb != null) lastThumbAt = now
        ZoomCanvasStorage.writesInFlight.update { it + 1 }
        ZoomCanvasStorage.ioScope.launch {
            try {
                ZoomCanvasStorage.saveMutex.withLock {
                    try {
                        store.save(p.meta, delta)
                        // Écrit : les couches qu'il touchait peuvent de nouveau être déchargées de la mémoire.
                        withContext(Dispatchers.Main) { scene.markSaved(delta) }
                    } catch (e: Exception) {
                        // L'écriture a échoué (disque plein…) : ses changements repartent dans le journal, on retentera.
                        withContext(Dispatchers.Main) {
                            scene.requeueChanges(delta)
                            if (savedContent == delta.contentVersion) savedContent = -1L
                        }
                        return@withLock
                    }
                    if (thumb != null) {
                        val bytes = ByteArrayOutputStream().also { thumb.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                        thumb.recycle()
                        store.saveThumb(p.meta.id, bytes)
                    }
                }
            } finally {
                ZoomCanvasStorage.writesInFlight.update { it - 1 }
            }
        }
    }

    fun rename(name: String) {
        val p = project ?: return
        p.meta.name = name
        ZoomCanvasStorage.writesInFlight.update { it + 1 }
        ZoomCanvasStorage.ioScope.launch {
            try {
                ZoomCanvasStorage.saveMutex.withLock { store.rename(p.meta.id, name) }
            } finally {
                ZoomCanvasStorage.writesInFlight.update { it - 1 }
            }
        }
    }

    override fun onCleared() {
        flush(force = true)
        onLayersLoaded = null
        thumbnailProvider = null
        super.onCleared()
    }

    private companion object {
        const val CONTENT_DELAY_MS = 700L
        const val CAMERA_DELAY_MS = 2500L
        /** La vignette redessine la vue : au plus une fois par là, sauf en quittant l'écran. */
        const val THUMB_EVERY_MS = 10_000L
    }
}
