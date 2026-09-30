package com.Atom2Universe.app.zoomcanvas

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Atom2Universe.app.zoomcanvas.core.LoadedZoomProject
import com.Atom2Universe.app.zoomcanvas.core.ZoomSnapshot
import com.Atom2Universe.app.zoomcanvas.core.ZoomStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

object ZoomCanvasStorage {
    @Volatile
    private var store: ZoomStore? = null

    fun store(context: Context): ZoomStore = store ?: synchronized(this) {
        store ?: ZoomStore(File(context.applicationContext.filesDir, "zoomcanvas/projects")).also { store = it }
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

    fun open(id: String) {
        if (project?.meta?.id == id) { _state.value = State.READY; return }
        _state.value = State.LOADING
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { store.load(id) }
            if (loaded == null) { _state.value = State.FAILED; return@launch }
            project = loaded
            savedContent = loaded.scene.contentVersion
            savedCamera = loaded.scene.cameraVersion
            _state.value = State.READY
        }
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

    /** Écrit tout de suite ce qui a changé (l'écriture elle-même se fait hors du fil principal). */
    fun flush() {
        val p = project ?: return
        saveJob?.cancel()
        val scene = p.scene
        val contentDirty = scene.contentVersion != savedContent
        if (!contentDirty && scene.cameraVersion == savedCamera) return
        if (contentDirty) p.meta.modified = System.currentTimeMillis()
        val snap = ZoomSnapshot.of(p.meta, scene)
        savedContent = scene.contentVersion
        savedCamera = scene.cameraVersion
        val thumb = if (contentDirty) thumbnailProvider?.invoke() else null
        ZoomCanvasStorage.writesInFlight.update { it + 1 }
        ZoomCanvasStorage.ioScope.launch {
            try {
                ZoomCanvasStorage.saveMutex.withLock {
                    try {
                        store.save(snap)
                    } catch (e: Exception) {
                        // L'écriture a échoué (disque plein…) : on retentera au prochain changement.
                        withContext(Dispatchers.Main) { if (savedContent == snap.contentVersion) savedContent = -1L }
                        return@withLock
                    }
                    if (thumb != null) {
                        val bytes = ByteArrayOutputStream().also { thumb.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                        thumb.recycle()
                        store.saveThumb(snap.meta.id, bytes)
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
        p.meta.modified = System.currentTimeMillis()
        savedContent = -1L
        flush()
    }

    override fun onCleared() {
        flush()
        thumbnailProvider = null
        super.onCleared()
    }

    private companion object {
        const val CONTENT_DELAY_MS = 700L
        const val CAMERA_DELAY_MS = 2500L
    }
}
