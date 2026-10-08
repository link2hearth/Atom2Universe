package com.Atom2Universe.app.pixelart.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Atom2Universe.app.pixelart.core.EditorSession
import com.Atom2Universe.app.pixelart.io.LoadedProject
import com.Atom2Universe.app.pixelart.io.PaletteStore
import com.Atom2Universe.app.pixelart.io.ProjectStore
import com.Atom2Universe.app.pixelart.io.SourceLink
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
import java.io.File

/** Le dossier des projets et le fil d'écriture partagés par la galerie et l'éditeur. */
object PixelArtStorage {
    @Volatile
    private var store: ProjectStore? = null

    fun store(context: Context): ProjectStore = store ?: synchronized(this) {
        store ?: ProjectStore(File(context.applicationContext.filesDir, "pixelart/projects")).also { store = it }
    }

    /** Les écritures survivent à l'écran qui les a lancées (on quitte l'éditeur juste après un trait). */
    val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val saveMutex = Mutex()

    /** Nombre d'écritures lancées et pas finies : la galerie attend qu'il retombe à 0 avant de relire les projets. */
    val writesInFlight = MutableStateFlow(0)
}

/**
 * Garde le projet ouvert et sa [EditorSession] à travers les rotations d'écran (l'activité est
 * recréée, le dessin et l'historique ne le sont pas), et s'occupe de l'enregistrement automatique.
 */
class EditorViewModel(app: Application) : AndroidViewModel(app) {

    enum class State { LOADING, READY, FAILED }

    val store: ProjectStore = PixelArtStorage.store(app)
    val palettes = PaletteStore(app)

    private val _state = MutableStateFlow(State.LOADING)
    val state: StateFlow<State> = _state

    var project: LoadedProject? = null
        private set
    var session: EditorSession? = null
        private set

    private var autosaveJob: Job? = null

    fun open(id: String) {
        if (project?.meta?.id == id) { _state.value = State.READY; return }
        _state.value = State.LOADING
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { store.load(id) }
            if (loaded == null) { _state.value = State.FAILED; return@launch }
            project = loaded
            val s = EditorSession(loaded.doc)
            s.primary = loaded.meta.primary
            s.secondary = loaded.meta.secondary
            loaded.doc.layerById(loaded.meta.activeLayerId)?.let { s.selectLayer(loaded.doc.layerIndexOf(it.id)) }
            loaded.doc.frameById(loaded.meta.activeFrameId)?.let { s.selectFrame(loaded.doc.frameIndexOf(it.id)) }
            session = s
            _state.value = State.READY
        }
    }

    /** Enregistrement différé : on attend la fin du geste pour ne pas écrire à chaque trait. */
    fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(AUTOSAVE_DELAY_MS)
            flush()
        }
    }

    /** Écrit tout de suite ce qui a changé (l'écriture se fait hors du fil principal). */
    fun flush() {
        val p = project ?: return
        val s = session ?: return
        autosaveJob?.cancel()
        s.settle()
        p.meta.primary = s.primary
        p.meta.secondary = s.secondary
        p.meta.activeLayerId = s.activeLayerId
        p.meta.activeFrameId = s.activeFrameId
        val plan = store.prepareSave(p)
        PixelArtStorage.writesInFlight.update { it + 1 }
        PixelArtStorage.ioScope.launch {
            try {
                PixelArtStorage.saveMutex.withLock { store.save(plan) }
                withContext(Dispatchers.Main) { p.doc.markSaved(plan.dirty) }
            } catch (e: Exception) {
                // Disque plein ou fichier verrouillé : rien n'est acquitté, les cels restent « sales »
                // et la prochaine écriture les reprendra. Sans cela l'exception tuerait l'appli.
                Log.w("PixelArtStorage", "Enregistrement impossible", e)
            } finally {
                PixelArtStorage.writesInFlight.update { it - 1 }
            }
        }
    }

    fun setLink(link: SourceLink?) {
        val p = project ?: return
        p.meta.link = link
        p.doc.markMetaDirty()
        scheduleAutosave()
    }

    fun rename(name: String) {
        val p = project ?: return
        p.meta.name = name
        p.doc.markMetaDirty()
        scheduleAutosave()
    }

    override fun onCleared() {
        flush()
        session?.listener = null
        super.onCleared()
    }

    private companion object {
        const val AUTOSAVE_DELAY_MS = 1200L
    }
}
